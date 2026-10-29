#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde::Deserialize;
use serde_json::{json, Value};
use std::{collections::HashMap, io::{BufRead, BufReader, Write}, path::{Path, PathBuf}, process::{Child, ChildStdin, Command, Stdio}, sync::{mpsc, Arc, Mutex}, time::Duration};
use tauri::{Manager, Emitter};
use std::sync::atomic::{AtomicU64,Ordering};
mod attachments;
mod compose;
mod vault;
mod calendars;
mod contact_book;

#[derive(Deserialize)]
struct Request { id: String, method: String, args: Value }
type Reply = Result<Value, String>;
type Pending = Arc<Mutex<HashMap<String, mpsc::Sender<Reply>>>>;
struct Engine { child: Mutex<Option<Child>>, stdin: Mutex<ChildStdin>, pending: Pending, saved: Mutex<HashMap<String,PathBuf>>, epoch:AtomicU64, owner:Mutex<Option<String>>,calendar_serial:AtomicU64,calendar_jobs:Mutex<HashMap<String,Arc<std::sync::atomic::AtomicBool>>>,session_gate:Mutex<()>,drafts:Mutex<HashMap<String,Arc<Mutex<compose::Draft>>>> }
// Older Windows Java launchers may resolve their DLLs through the active ANSI code page.
// Use an existing filesystem short alias when available; never change system locale or 8.3 policy.
#[cfg(windows)]
fn java_root(root: &Path) -> PathBuf {
    let root=dunce::simplified(root);
    use std::{ffi::OsString, os::windows::ffi::{OsStrExt, OsStringExt}};
    #[link(name = "kernel32")]
    extern "system" { fn GetShortPathNameW(long: *const u16, short: *mut u16, count: u32) -> u32; }
    let wide: Vec<u16> = root.as_os_str().encode_wide().chain(std::iter::once(0)).collect();
    let needed = unsafe { GetShortPathNameW(wide.as_ptr(), std::ptr::null_mut(), 0) };
    if needed == 0 || needed > 32768 { return root.to_path_buf(); }
    let mut buffer = vec![0u16; needed as usize + 1];
    let used = unsafe { GetShortPathNameW(wide.as_ptr(), buffer.as_mut_ptr(), buffer.len() as u32) };
    if used == 0 || used as usize >= buffer.len() { return root.to_path_buf(); }
    PathBuf::from(OsString::from_wide(&buffer[..used as usize]))
}
#[cfg(not(windows))]
fn java_root(root: &Path) -> PathBuf { root.to_path_buf() }

impl Engine {
    fn start(root: &Path, events: Option<tauri::AppHandle>) -> Result<Arc<Self>, String> {
        let root = java_root(root);
        let java = root.join("runtime/bin").join(if cfg!(windows) { "java.exe" } else { "java" });
        let separator = if cfg!(windows) { ";" } else { ":" };
        let cp = format!("{}{}{}", root.join("engine/classes").display(), separator, root.join("engine/lib/*").display());
        let mut command = Command::new(java);
        command.args(["-Djava.awt.headless=true", "-Dfile.encoding=UTF-8", "-cp", &cp, "ru.local.spbumailpilot.DesktopEngine"]).stdin(Stdio::piped()).stdout(Stdio::piped()).stderr(Stdio::null());
        #[cfg(windows)] { use std::os::windows::process::CommandExt; command.creation_flags(0x08000000); }
        let mut child = command.spawn().map_err(|_| "ENGINE_UNAVAILABLE")?;
        let stdin = child.stdin.take().ok_or("ENGINE_UNAVAILABLE")?;
        let stdout = child.stdout.take().ok_or("ENGINE_UNAVAILABLE")?;
        let pending: Pending = Arc::new(Mutex::new(HashMap::new()));
        let waiting = pending.clone();
        std::thread::spawn(move || {
            for line in BufReader::new(stdout).lines() {
                let Ok(line) = line else { break };
                if line.len() > 8 * 1024 * 1024 { break; }
                let Ok(value) = serde_json::from_str::<Value>(&line) else { break };
                let Some(id) = value.get("id").and_then(Value::as_str) else { continue };
                if value["event"] == "attachmentProgress" || value["event"] == "sendProgress" {
                    if waiting.lock().map(|map| map.contains_key(id)).unwrap_or(false) {
                        if let (Some(app),Some(bytes),Some(total)) = (&events,value["bytes"].as_u64(),value["total"].as_i64()) {
                            if bytes <= 209715200 && (-1..=209715200).contains(&total) { let sending=value["event"]=="sendProgress";
                                let stage=value["stage"].as_str().filter(|s|["connecting","uploading","confirming","complete","accepted"].contains(s)).unwrap_or("connecting");
                                let _ = app.emit(if sending{"send-progress"}else{"attachment-progress"}, json!({"id":id,"bytes":bytes,"total":total,"stage":stage})); }
                        }
                    }
                    continue;
                }
                if let Ok(mut map) = waiting.lock() { if let Some(sender) = map.remove(id) { let _ = sender.send(Ok(value)); } }
            }
            if let Ok(mut map) = waiting.lock() { for (_, sender) in map.drain() { let _ = sender.send(Err("ENGINE_UNAVAILABLE".into())); } }
        });
        Ok(Arc::new(Self { child: Mutex::new(Some(child)), stdin: Mutex::new(stdin), pending, saved:Mutex::new(HashMap::new()),epoch:AtomicU64::new(0),owner:Mutex::new(None),calendar_serial:AtomicU64::new(0),calendar_jobs:Mutex::new(HashMap::new()),session_gate:Mutex::new(()),drafts:Mutex::new(HashMap::new()) }))
    }
    fn request(&self, request: Request) -> Reply {
        const METHODS: &[&str] = &["capabilities", "connect", "disconnect", "folders", "page", "body", "cancel", "attachment", "send", "calendarDiscover", "calendarMonth", "contactsPage"];
        if request.id.is_empty() || request.id.len() > 64 || !request.id.bytes().all(|c| c.is_ascii_alphanumeric() || b"._-".contains(&c)) || !METHODS.contains(&request.method.as_str()) || !request.args.is_object() { return Err("BAD_REQUEST".into()); }
        let id = request.id.clone();
        let line = json!({"id": request.id, "method": request.method, "args": request.args}).to_string();
        if line.len() > 1048576 { return Err("BAD_REQUEST".into()); }
        let (sender, receiver) = mpsc::channel();
        { let mut map = self.pending.lock().map_err(|_| "ENGINE_UNAVAILABLE")?;
          if map.len() >= 16 || map.contains_key(&id) { return Err("BUSY".into()); }
          map.insert(id.clone(), sender); }
        let written = self.stdin.lock().map_err(|_| "ENGINE_UNAVAILABLE").and_then(|mut stream| writeln!(stream, "{}", line).and_then(|_| stream.flush()).map_err(|_| "ENGINE_UNAVAILABLE"));
        if written.is_err() { if let Ok(mut map) = self.pending.lock() { map.remove(&id); } return Err("ENGINE_UNAVAILABLE".into()); }
        let result = receiver.recv_timeout(Duration::from_secs(if matches!(request.method.as_str(),"attachment"|"send"|"calendarDiscover"|"calendarMonth"|"contactsPage") {600} else {90})).unwrap_or_else(|_| Err("ENGINE_UNAVAILABLE".into()));
        if let Ok(mut map) = self.pending.lock() { map.remove(&id); }
        result
    }
    fn stop(&self) { if let Ok(mut slot) = self.child.lock() { if let Some(mut child) = slot.take() { let _ = child.kill(); let _ = child.wait(); } } }
}
impl Drop for Engine { fn drop(&mut self) { self.stop(); } }

fn connect(mut request:Request,app:&tauri::AppHandle,engine:&Arc<Engine>,remember:bool)->Reply{
    let _gate=engine.session_gate.lock().map_err(|_|"NO_SESSION")?;
    compose::clear(engine)?;
    *engine.owner.lock().map_err(|_|"NO_SESSION")?=None;
    if let Ok(mut saved)=engine.saved.lock(){engine.epoch.fetch_add(1,Ordering::SeqCst);saved.clear();}
    request.method="connect".into();let auth=request.args.clone();
    let mut reply=engine.request(request)?;
    if reply["result"]["connected"]==true{
        *engine.owner.lock().map_err(|_|"NO_SESSION")?=Some(vault::owner(&auth)?);
        reply["result"]["epoch"]=json!(engine.epoch.load(Ordering::SeqCst));reply["result"]["host"]=auth["host"].clone();reply["result"]["port"]=auth["port"].clone();
        if remember{reply["result"]["remembered"]=json!(vault::remember(app,&auth).is_ok());}
    }Ok(reply)
}
#[tauri::command]
async fn mail_request(mut request: Request, app:tauri::AppHandle, state: tauri::State<'_, Arc<Engine>>) -> Reply {
    let engine = state.inner().clone();
    if request.method=="cancel"{if let Some(target)=request.args["requestId"].as_str(){calendars::cancel(&engine,target);}}
    if ["calendarStatus","calendarConnect","calendarSelect","calendarForget","calendarMonth"].contains(&request.method.as_str()){return calendars::run(request,app,engine).await;}
    tauri::async_runtime::spawn_blocking(move || {
        if ["localContactsRead","localContactsSave","localContactsDelete"].contains(&request.method.as_str()){return contact_book::execute(request,&app,&engine);}
        if request.method == "contactsPage" {
            let epoch=request.args["epoch"].as_u64().ok_or("BAD_REQUEST")?;
            {let _gate=engine.session_gate.lock().map_err(|_|"NO_SESSION")?;
                if engine.epoch.load(Ordering::SeqCst)!=epoch{return Err("NO_SESSION".into());}
                request.args["_owner"]=json!(engine.owner.lock().map_err(|_|"NO_SESSION")?.clone().ok_or("NO_SESSION")?);}
            let reply=engine.request(request)?;
            let _gate=engine.session_gate.lock().map_err(|_|"NO_SESSION")?;
            if engine.epoch.load(Ordering::SeqCst)!=epoch{return Err("CANCELLED".into());}return Ok(reply);
        }
        if request.method == "attachment" { return attachments::save(request,&app,&engine); }
        if request.method == "send" {return compose::send(request,&app,&engine);}
        if request.method == "connectSaved" {return vault::connect_saved(request,&app,&engine);}
        if request.method == "connect" {let remember=request.args["remember"].as_bool()==Some(true);return connect(request,&app,&engine,remember);}
        if request.method == "disconnect" {
            let _gate=engine.session_gate.lock().map_err(|_|"NO_SESSION")?;
            compose::clear(&engine)?;*engine.owner.lock().map_err(|_|"NO_SESSION")?=None;
            if let Ok(mut saved)=engine.saved.lock(){engine.epoch.fetch_add(1,Ordering::SeqCst);saved.clear();}
        }
        engine.request(request)
    }).await.map_err(|_| "ENGINE_UNAVAILABLE".to_string())?
}
#[tauri::command]
async fn open_saved(token:String,app:tauri::AppHandle,state:tauri::State<'_,Arc<Engine>>)->Result<(),String>{
    let path=state.saved.lock().map_err(|_| "FILE_UNAVAILABLE")?.get(&token).cloned().ok_or("FILE_UNAVAILABLE")?;
    if !attachments::openable(&path)||!path.is_file(){return Err("FILE_UNAVAILABLE".into());}
    use tauri_plugin_opener::OpenerExt;
    app.opener().open_path(path.to_string_lossy().into_owned(),None::<&str>).map_err(|_| "OPEN_FAILED".into())
}
fn root_for(app: &tauri::App) -> Result<PathBuf, Box<dyn std::error::Error>> {
    let resources = app.path().resource_dir()?;
    if resources.join("engine/classes").is_dir() { return Ok(resources); }
    if cfg!(debug_assertions) { return Ok(PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../build")); }
    Err("Desktop resources missing".into())
}
fn main() {
    if std::env::args().any(|arg| arg == "--verify-engine") {
        let root = std::env::current_exe().ok().and_then(|p| p.parent().map(Path::to_path_buf)).expect("Executable location missing");
        let root=root.canonicalize().expect("Canonical executable location missing");
        let engine = Engine::start(&root,None).expect("Bundled engine unavailable");
        let reply = engine.request(Request { id: "probe".into(), method: "capabilities".into(), args: json!({}) }).expect("Engine probe failed");
        assert_eq!(reply["result"]["sending"], true);engine.stop();return;
    }
    tauri::Builder::default().plugin(tauri_plugin_single_instance::init(|app,_,_|{if let Some(window)=app.get_webview_window("main"){let _=window.show();let _=window.set_focus();}})).plugin(tauri_plugin_dialog::init()).plugin(tauri_plugin_opener::init()).setup(|app| { let root = root_for(app)?;let engine = Engine::start(&root,Some(app.handle().clone())).map_err(std::io::Error::other)?;let ready=engine.request(Request{id:"startup".into(),method:"capabilities".into(),args:json!({})}).map_err(std::io::Error::other)?;
            if ready["result"]["sending"]!=true {return Err(std::io::Error::other("Engine handshake failed").into());}
            app.manage(engine);Ok(()) })
        .invoke_handler(tauri::generate_handler![mail_request,open_saved,compose::draft_begin,compose::draft_attach,compose::draft_remove,compose::draft_discard,compose::draft_save,compose::draft_restore,compose::draft_forget,vault::account_status,vault::account_forget])
        .on_window_event(|window, event| { if matches!(event, tauri::WindowEvent::Destroyed) { if let Some(engine) = window.try_state::<Arc<Engine>>() { engine.stop(); } } })
        .run(tauri::generate_context!()).expect("Desktop window failed to start");
}

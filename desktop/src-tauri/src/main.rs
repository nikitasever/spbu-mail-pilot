#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde::Deserialize;
use serde_json::{json, Value};
use std::{collections::HashMap, io::{BufRead, BufReader, Write}, path::{Path, PathBuf}, process::{Child, ChildStdin, Command, Stdio}, sync::{mpsc, Arc, Mutex}, time::Duration};
use tauri::Manager;

#[derive(Deserialize)]
struct Request { id: String, method: String, args: Value }
type Reply = Result<Value, String>;
type Pending = Arc<Mutex<HashMap<String, mpsc::Sender<Reply>>>>;
struct Engine { child: Mutex<Option<Child>>, stdin: Mutex<ChildStdin>, pending: Pending }
impl Engine {
    fn start(root: &Path) -> Result<Arc<Self>, String> {
        let java = root.join("runtime/bin").join(if cfg!(windows) { "java.exe" } else { "java" });
        let separator = if cfg!(windows) { ";" } else { ":" };
        let cp = format!("{}{}{}", root.join("engine/classes").display(), separator, root.join("engine/lib/*").display());
        let mut command = Command::new(java);
        command.args(["-Djava.awt.headless=true", "-cp", &cp, "ru.local.spbumailpilot.DesktopEngine"]).stdin(Stdio::piped()).stdout(Stdio::piped()).stderr(Stdio::null());
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
                if let Ok(mut map) = waiting.lock() { if let Some(sender) = map.remove(id) { let _ = sender.send(Ok(value)); } }
            }
            if let Ok(mut map) = waiting.lock() { for (_, sender) in map.drain() { let _ = sender.send(Err("ENGINE_UNAVAILABLE".into())); } }
        });
        Ok(Arc::new(Self { child: Mutex::new(Some(child)), stdin: Mutex::new(stdin), pending }))
    }
    fn request(&self, request: Request) -> Reply {
        const METHODS: &[&str] = &["capabilities", "connect", "disconnect", "folders", "page", "body", "cancel"];
        if request.id.is_empty() || request.id.len() > 64 || !request.id.bytes().all(|c| c.is_ascii_alphanumeric() || b"._-".contains(&c)) || !METHODS.contains(&request.method.as_str()) || !request.args.is_object() { return Err("BAD_REQUEST".into()); }
        let id = request.id.clone();
        let line = json!({"id": request.id, "method": request.method, "args": request.args}).to_string();
        if line.len() > 65536 { return Err("BAD_REQUEST".into()); }
        let (sender, receiver) = mpsc::channel();
        { let mut map = self.pending.lock().map_err(|_| "ENGINE_UNAVAILABLE")?;
          if map.len() >= 16 || map.contains_key(&id) { return Err("BUSY".into()); }
          map.insert(id.clone(), sender); }
        let written = self.stdin.lock().map_err(|_| "ENGINE_UNAVAILABLE").and_then(|mut stream| writeln!(stream, "{}", line).and_then(|_| stream.flush()).map_err(|_| "ENGINE_UNAVAILABLE"));
        if written.is_err() { if let Ok(mut map) = self.pending.lock() { map.remove(&id); } return Err("ENGINE_UNAVAILABLE".into()); }
        let result = receiver.recv_timeout(Duration::from_secs(90)).unwrap_or_else(|_| Err("ENGINE_UNAVAILABLE".into()));
        if let Ok(mut map) = self.pending.lock() { map.remove(&id); }
        result
    }
    fn stop(&self) { if let Ok(mut slot) = self.child.lock() { if let Some(mut child) = slot.take() { let _ = child.kill(); let _ = child.wait(); } } }
}
impl Drop for Engine { fn drop(&mut self) { self.stop(); } }

#[tauri::command]
async fn mail_request(request: Request, state: tauri::State<'_, Arc<Engine>>) -> Reply {
    let engine = state.inner().clone();
    tauri::async_runtime::spawn_blocking(move || engine.request(request)).await.map_err(|_| "ENGINE_UNAVAILABLE".to_string())?
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
        let engine = Engine::start(&root).expect("Bundled engine unavailable");
        let reply = engine.request(Request { id: "probe".into(), method: "capabilities".into(), args: json!({}) }).expect("Engine probe failed");
        assert_eq!(reply["result"]["readOnly"], true);engine.stop();return;
    }
    tauri::Builder::default().setup(|app| { let root = root_for(app)?;let engine = Engine::start(&root).map_err(std::io::Error::other)?;app.manage(engine);Ok(()) })
        .invoke_handler(tauri::generate_handler![mail_request])
        .on_window_event(|window, event| { if matches!(event, tauri::WindowEvent::Destroyed) { if let Some(engine) = window.try_state::<Arc<Engine>>() { engine.stop(); } } })
        .run(tauri::generate_context!()).expect("Desktop window failed to start");
}

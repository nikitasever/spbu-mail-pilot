use super::{Engine,Request,Reply,Ordering};
use serde_json::{json,Value};
use std::{sync::{Arc,Mutex},path::Path,io::{Read,Write}};
use tauri::{Manager,State};
use tauri_plugin_dialog::DialogExt;

pub struct Draft {epoch:u64,directory:tempfile::TempDir,files:Vec<Value>,busy:bool,terminal:bool}
fn token()->String{uuid::Uuid::new_v4().to_string()}
fn snapshot(path:&Path,directory:&Path,remaining:u64)->Result<Value,String>{
    let name=path.file_name().and_then(|p|p.to_str()).ok_or("FILE_UNAVAILABLE")?;
    let source=std::fs::File::open(path).map_err(|_|"FILE_UNAVAILABLE")?;
    let metadata=source.metadata().map_err(|_|"FILE_UNAVAILABLE")?;
    if !metadata.is_file(){return Err("FILE_UNAVAILABLE".into());}if metadata.len()>remaining{return Err("TOO_LARGE".into());}
    let file=token()+".bin";let target=directory.join(&file);
    let result=(||->Result<u64,String>{
        let mut output=std::fs::OpenOptions::new().create_new(true).write(true).open(&target).map_err(|_|"FILE_UNAVAILABLE")?;
        let copied=std::io::copy(&mut source.take(remaining+1),&mut output).map_err(|_|"FILE_UNAVAILABLE")?;
        output.flush().map_err(|_|"FILE_UNAVAILABLE")?;
        if copied>remaining{return Err("TOO_LARGE".into());}Ok(copied)
    })();
    match result{Ok(size)=>Ok(json!({"token":file,"name":name,"size":size})),Err(error)=>{let _=std::fs::remove_file(target);Err(error)}}
}

fn draft(engine:&Engine,id:&str)->Result<Arc<Mutex<Draft>>,String>{
    engine.drafts.lock().map_err(|_|"DRAFT_UNAVAILABLE")?.get(id).cloned().ok_or("DRAFT_UNAVAILABLE".into())
}
fn editable(value:&Draft,engine:&Engine)->Result<(),String>{
    if value.epoch!=engine.epoch.load(Ordering::SeqCst){return Err("DRAFT_UNAVAILABLE".into());}
    if value.busy||value.terminal{return Err("SEND_LOCKED".into());}Ok(())
}
pub fn clear(engine:&Engine)->Result<(),String>{
    let mut drafts=engine.drafts.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;
    for value in drafts.values(){if value.lock().map_err(|_|"DRAFT_UNAVAILABLE")?.busy{return Err("SEND_BUSY".into());}}
    drafts.clear();Ok(())
}
#[tauri::command]
pub fn draft_begin(state:State<'_,Arc<Engine>>)->Result<Value,String>{
    let mut drafts=state.drafts.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;
    if drafts.len()>=4{return Err("DRAFT_LIMIT".into());}
    let id=token();let value=Draft{epoch:state.epoch.load(Ordering::SeqCst),directory:tempfile::Builder::new().prefix("spbu-mail-draft-").tempdir().map_err(|_|"FILE_UNAVAILABLE")?,files:Vec::new(),busy:false,terminal:false};
    drafts.insert(id.clone(),Arc::new(Mutex::new(value)));Ok(json!({"id":id}))
}
#[tauri::command]
pub async fn draft_attach(id:String,app:tauri::AppHandle,state:State<'_,Arc<Engine>>)->Result<Value,String>{
    let engine=state.inner().clone();
    tauri::async_runtime::spawn_blocking(move||{
        let handle=draft(&engine,&id)?;
        // Do not hold the draft lock while the system picker is open.
        {let value=handle.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;editable(&value,&engine)?;}
        let Some(paths)=app.dialog().file().set_title("Прикрепить файлы").blocking_pick_files() else{return Ok(json!({"cancelled":true}));};
        let mut value=handle.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;editable(&value,&engine)?;
        if paths.len()+value.files.len()>10{return Err("TOO_MANY_FILES".into());}
        let mut remaining=104857600-value.files.iter().map(|v|v["size"].as_u64().unwrap_or(0)).sum::<u64>();
        let mut batch=Vec::new();let result=(||->Result<(),String>{
            for path in paths{
                let path=path.into_path().map_err(|_|"FILE_UNAVAILABLE")?;
                let file=snapshot(&path,value.directory.path(),remaining)?;remaining-=file["size"].as_u64().ok_or("FILE_UNAVAILABLE")?;batch.push(file);
            }Ok(())
        })();
        if let Err(error)=result{for file in batch{let _=std::fs::remove_file(value.directory.path().join(file["token"].as_str().unwrap_or("")));}return Err(error);}
        value.files.extend(batch);Ok(json!({"files":value.files}))
    }).await.map_err(|_|"FILE_UNAVAILABLE".to_string())?
}
#[tauri::command]
pub fn draft_remove(id:String,file:String,state:State<'_,Arc<Engine>>)->Result<Value,String>{
    let handle=draft(&state,&id)?;let mut value=handle.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;editable(&value,&state)?;
    if let Some(index)=value.files.iter().position(|v|v["token"].as_str()==Some(file.as_str())){
        std::fs::remove_file(value.directory.path().join(&file)).map_err(|_|"FILE_UNAVAILABLE")?;value.files.remove(index);
    }Ok(json!({"files":value.files}))
}
#[tauri::command]
pub fn draft_discard(id:String,state:State<'_,Arc<Engine>>)->Result<(),String>{
    let mut drafts=state.drafts.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;
    if let Some(handle)=drafts.get(&id){if handle.lock().map_err(|_|"DRAFT_UNAVAILABLE")?.busy{return Err("SEND_BUSY".into());}}
    drafts.remove(&id);Ok(())
}
pub fn send(mut request:Request,app:&tauri::AppHandle,engine:&Arc<Engine>)->Reply{
    let id=request.id.clone();let fault=|code:&str|Ok(json!({"id":id,"error":{"code":code}}));
    let Some(draft_id)=request.args["draftId"].as_str().map(String::from) else{return fault("BAD_DRAFT");};
    let handle=draft(engine,&draft_id)?;
    {
        let mut value=handle.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;editable(&value,engine)?;
        // All file paths and the durable receipt directory originate in the native shell.
        request.args["directory"]=json!(value.directory.path());request.args["attachments"]=json!(value.files);request.args["attemptId"]=json!(draft_id);
        let receipts=app.path().app_data_dir().map_err(|_|"DRAFT_UNAVAILABLE")?.join("send-receipts");request.args["receipts"]=json!(receipts);
        value.busy=true;
    }
    let result=engine.request(request);
    let mut value=handle.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;value.busy=false;
    match result{
        Ok(reply)=>{value.terminal=reply["result"]["accepted"]==true||reply["error"]["code"]=="SEND_UNKNOWN";Ok(reply)}
        Err(_)=>{value.terminal=true;fault("SEND_UNKNOWN")}
    }
}

#[cfg(test)]mod tests{
    use super::*;
    #[test]fn snapshots_do_not_follow_original_edits_and_oversize_leaves_no_partial(){
        let originals=tempfile::tempdir().unwrap();let copies=tempfile::tempdir().unwrap();let source=originals.path().join("Документ.txt");
        std::fs::write(&source,b"selected bytes").unwrap();
        let saved=snapshot(&source,copies.path(),20).unwrap();
        std::fs::write(&source,b"changed later").unwrap();
        assert_eq!(std::fs::read(copies.path().join(saved["token"].as_str().unwrap())).unwrap(),b"selected bytes");
        assert!(snapshot(&source,copies.path(),2).is_err());assert_eq!(std::fs::read_dir(copies.path()).unwrap().count(),1);
    }
    #[test]fn ids_are_safe_unique_receipt_names(){let a=token();let b=token();assert_ne!(a,b);assert!(uuid::Uuid::parse_str(&a).is_ok());assert_eq!(Path::new(&a).components().count(),1);}
}

use super::{Engine,Request,Reply,Ordering};
use serde_json::{json,Value};
use std::{sync::{Arc,Mutex},path::{Path,PathBuf},io::{Read,Write}};
use tauri::{Manager,State};
use tauri_plugin_dialog::DialogExt;

pub struct Draft {epoch:u64,directory:tempfile::TempDir,files:Vec<Value>,busy:bool,terminal:bool,deleted:bool,id:String,owner:String,root:PathBuf,content:Value,revision:u64,saved:bool,status:String}
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
    if value.deleted||value.epoch!=engine.epoch.load(Ordering::SeqCst)||engine.owner.lock().map_err(|_|"NO_SESSION")?.as_deref()!=Some(value.owner.as_str()){return Err("DRAFT_UNAVAILABLE".into());}
    if value.busy||value.terminal{return Err("SEND_LOCKED".into());}Ok(())
}
pub fn clear(engine:&Engine)->Result<(),String>{
    let mut drafts=engine.drafts.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;
    let mut guards=Vec::new();for value in drafts.values(){let guard=value.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;if guard.busy{return Err("SEND_BUSY".into());}guards.push(guard);}
    for value in &mut guards{value.deleted=true;}drop(guards);
    drafts.clear();Ok(())
}
#[tauri::command]
pub fn draft_begin(epoch:u64,app:tauri::AppHandle,state:State<'_,Arc<Engine>>)->Result<Value,String>{
    let _gate=state.session_gate.lock().map_err(|_|"NO_SESSION")?;if epoch!=state.epoch.load(Ordering::SeqCst){return Err("DRAFT_UNAVAILABLE".into());}
    let owner=active_owner(&state)?;let root=super::vault::root(&app)?;
    let mut drafts=state.drafts.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;
    if drafts.len()>=4{return Err("DRAFT_LIMIT".into());}
    if active_path(&root,&owner).try_exists().map_err(|_|"DRAFT_SAVE_FAILED")?{return Err("DRAFT_EXISTS".into());}
    let value=fresh(&root,&owner,state.epoch.load(Ordering::SeqCst))?;let id=value.id.clone();
    drafts.insert(id.clone(),Arc::new(Mutex::new(value)));Ok(json!({"id":id,"revision":0}))
}
#[tauri::command]
pub async fn draft_save(id:String,revision:u64,content:Value,state:State<'_,Arc<Engine>>)->Result<Value,String>{
    let engine=state.inner().clone();
    tauri::async_runtime::spawn_blocking(move||{
        let handle=draft(&engine,&id)?;let mut value=handle.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;editable(&value,&engine)?;
        if revision<=value.revision{return Ok(json!({"saved":true,"revision":value.revision,"stale":true}));}
        let content=validate_content(&content)?;let old=value.content.clone();let previous=value.revision;value.content=content;value.revision=revision;
        if let Err(error)=persist(&mut value){value.content=old;value.revision=previous;return Err(error);}
        Ok(json!({"saved":true,"revision":value.revision}))
    }).await.map_err(|_|"DRAFT_SAVE_FAILED".to_string())?
}
#[tauri::command]
pub fn draft_forget(epoch:u64,app:tauri::AppHandle,state:State<'_,Arc<Engine>>)->Result<(),String>{
    let _gate=state.session_gate.lock().map_err(|_|"NO_SESSION")?;if epoch!=state.epoch.load(Ordering::SeqCst){return Err("DRAFT_UNAVAILABLE".into());}
    let owner=active_owner(&state)?;let drafts=state.drafts.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;
    if drafts.values().any(|v|v.lock().map(|v|v.owner==owner).unwrap_or(true)){return Err("DRAFT_EXISTS".into());}
    let path=super::vault::root(&app)?.join("drafts").join(owner);
    match std::fs::remove_dir_all(path){Ok(())=>Ok(()),Err(error)if error.kind()==std::io::ErrorKind::NotFound=>Ok(()),Err(_)=>Err("DRAFT_SAVE_FAILED".into())}
}
#[tauri::command]
pub async fn draft_restore(epoch:u64,app:tauri::AppHandle,state:State<'_,Arc<Engine>>)->Result<Value,String>{
    let engine=state.inner().clone();
    tauri::async_runtime::spawn_blocking(move||{
        let _gate=engine.session_gate.lock().map_err(|_|"NO_SESSION")?;if epoch!=engine.epoch.load(Ordering::SeqCst){return Err("DRAFT_UNAVAILABLE".into());}
        let owner=active_owner(&engine)?;let root=super::vault::root(&app)?;let epoch=engine.epoch.load(Ordering::SeqCst);
        let mut drafts=engine.drafts.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;
        if let Some(handle)=drafts.values().find(|handle|handle.lock().map(|v|v.owner==owner).unwrap_or(false)){
            let value=handle.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;return Ok(dto(&value));
        }
        let Some(value)=restore(&root,&owner,epoch)? else{return Ok(Value::Null);};
        let result=dto(&value);drafts.insert(value.id.clone(),Arc::new(Mutex::new(value)));Ok(result)
    }).await.map_err(|_|"DRAFT_UNAVAILABLE".to_string())?
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
        let previous=value.files.clone();value.files.extend(batch.clone());
        if let Err(error)=persist(&mut value){value.files=previous;for file in batch{let name=file["token"].as_str().unwrap_or("");let _=std::fs::remove_file(value.directory.path().join(name));let _=std::fs::remove_file(blob_path(&value,name));}return Err(error);}
        Ok(json!({"files":value.files,"saved":true}))
    }).await.map_err(|_|"FILE_UNAVAILABLE".to_string())?
}
#[tauri::command]
pub fn draft_remove(id:String,file:String,state:State<'_,Arc<Engine>>)->Result<Value,String>{
    let handle=draft(&state,&id)?;let mut value=handle.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;editable(&value,&state)?;
    if let Some(index)=value.files.iter().position(|v|v["token"].as_str()==Some(file.as_str())){
        let previous=value.files.clone();value.files.remove(index);
        if let Err(error)=persist(&mut value){value.files=previous;return Err(error);}
        let _=std::fs::remove_file(value.directory.path().join(&file));
        let _=std::fs::remove_file(blob_path(&value,&file));
    }Ok(json!({"files":value.files,"saved":true}))
}
#[tauri::command]
pub fn draft_discard(id:String,state:State<'_,Arc<Engine>>)->Result<(),String>{
    let mut drafts=state.drafts.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;
    if let Some(handle)=drafts.get(&id){
        let mut value=handle.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;if value.busy{return Err("SEND_BUSY".into());}
        let active=active_path(&value.root,&value.owner);
        if active.try_exists().map_err(|_|"DRAFT_SAVE_FAILED")?{
            let saved=read_meta(&value.root,&value.owner)?;if saved["id"]!=value.id{return Err("DRAFT_CONFLICT".into());}
            std::fs::remove_file(active).map_err(|_|"DRAFT_SAVE_FAILED")?;
        }
        let _=std::fs::remove_dir_all(value.root.join("drafts").join(&value.owner).join(&value.id));value.deleted=true;
    }
    drafts.remove(&id);Ok(())
}
pub fn send(mut request:Request,app:&tauri::AppHandle,engine:&Arc<Engine>)->Reply{
    let gate=engine.session_gate.lock().map_err(|_|"NO_SESSION")?;let id=request.id.clone();let fault=|code:&str|Ok(json!({"id":id,"error":{"code":code}}));
    let Some(draft_id)=request.args["draftId"].as_str().map(String::from) else{return fault("BAD_DRAFT");};
    let handle=draft(engine,&draft_id)?;
    {
        let mut value=handle.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;editable(&value,engine)?;
        // All file paths and the durable receipt directory originate in the native shell.
        request.args["directory"]=json!(value.directory.path());request.args["attachments"]=json!(value.files);request.args["attemptId"]=json!(draft_id);
        let receipts=app.path().app_data_dir().map_err(|_|"DRAFT_UNAVAILABLE")?.join("send-receipts");request.args["receipts"]=json!(receipts);
        let mut content=request.args.clone();content["smtpPort"]=json!(request.args["smtpPort"].as_u64().unwrap_or(465).to_string());
        value.content=validate_content(&content)?;value.status="pending".into();if persist(&mut value).is_err(){value.status="editing".into();return fault("DRAFT_SAVE_FAILED");}
        value.busy=true;
    }
    drop(gate);let result=engine.request(request);
    let mut value=handle.lock().map_err(|_|"DRAFT_UNAVAILABLE")?;value.busy=false;
    let reply=match result{
        Ok(reply)=>{value.terminal=reply["result"]["accepted"]==true||reply["error"]["code"]=="SEND_UNKNOWN";reply}
        Err(_)=>{value.terminal=true;json!({"id":id,"error":{"code":"SEND_UNKNOWN"}})}
    };
    value.status=if reply["result"]["accepted"]==true{"sent"}else if value.terminal{"unknown"}else{"editing"}.into();
    // If final persistence fails, the earlier pending state still prevents retry after restart.
    let _=persist(&mut value);Ok(reply)
}

fn active_owner(engine:&Engine)->Result<String,String>{engine.owner.lock().map_err(|_|"NO_SESSION")?.clone().ok_or("NO_SESSION".into())}
fn active_path(root:&Path,owner:&str)->PathBuf{root.join("drafts").join(owner).join("active.dpapi")}
fn blob_path(value:&Draft,file:&str)->PathBuf{value.root.join("drafts").join(&value.owner).join(&value.id).join(format!("{file}.dpapi"))}
fn blob_context(value:&Draft,file:&str)->String{format!("spbu-draft-file-v1:{}:{}:{}",value.owner,value.id,file)}
fn fresh(root:&Path,owner:&str,epoch:u64)->Result<Draft,String>{Ok(Draft{epoch,directory:tempfile::Builder::new().prefix("spbu-mail-draft-").tempdir().map_err(|_|"FILE_UNAVAILABLE")?,files:Vec::new(),busy:false,terminal:false,deleted:false,id:token(),owner:owner.into(),root:root.to_path_buf(),content:json!({}),revision:0,saved:false,status:"editing".into()})}
fn validate_content(raw:&Value)->Result<Value,String>{
    let mut result=json!({});
    for (key,max) in [("to",4000),("cc",4000),("bcc",4000),("subject",1000),("body",200000),("from",1000),("smtpHost",1000),("smtpPort",32),("inReplyTo",2000)]{
        let value=raw[key].as_str().ok_or("BAD_DRAFT")?;if value.chars().count()>max{return Err("BAD_DRAFT".into());}result[key]=json!(value);
    }
    let ssl=raw["smtpSsl"].as_bool().ok_or("BAD_DRAFT")?;result["smtpSsl"]=json!(ssl);
    let refs=raw["references"].as_array().ok_or("BAD_DRAFT")?;if refs.len()>100||refs.iter().any(|v|v.as_str().map(|s|s.len()>2000).unwrap_or(true)){return Err("BAD_DRAFT".into());}result["references"]=json!(refs);
    if result.to_string().len()>1048576{return Err("BAD_DRAFT".into());}Ok(result)
}
fn persist(value:&mut Draft)->Result<(),String>{
    if value.deleted{return Err("DRAFT_UNAVAILABLE".into());}
    let path=active_path(&value.root,&value.owner);
    if path.try_exists().map_err(|_|"DRAFT_SAVE_FAILED")?{let old=read_meta(&value.root,&value.owner)?;if old["id"]!=value.id{return Err("DRAFT_CONFLICT".into());}}
    let mut total=0u64;for file in &value.files{
        let name=file["token"].as_str().ok_or("DRAFT_SAVE_FAILED")?;total+=file["size"].as_u64().ok_or("DRAFT_SAVE_FAILED")?;if total>104857600{return Err("TOO_LARGE".into());}
        let target=blob_path(value,name);if !target.try_exists().map_err(|_|"DRAFT_SAVE_FAILED")?{
            let source=std::fs::File::open(value.directory.path().join(name)).map_err(|_|"DRAFT_SAVE_FAILED")?;
            let mut plain=Vec::new();source.take(104857601).read_to_end(&mut plain).map_err(|_|"DRAFT_SAVE_FAILED")?;
            if plain.len() as u64!=file["size"].as_u64().unwrap_or(u64::MAX){return Err("DRAFT_SAVE_FAILED".into());}
            super::vault::write(&target,&blob_context(value,name),plain).map_err(|_|"DRAFT_SAVE_FAILED")?;
        }
    }
    let data=json!({"version":1,"id":value.id,"owner":value.owner,"revision":value.revision,"state":value.status,"content":value.content,"files":value.files});
    let encoded=serde_json::to_vec(&data).map_err(|_|"DRAFT_SAVE_FAILED")?;if encoded.len()>1048576{return Err("DRAFT_SAVE_FAILED".into());}
    super::vault::write(&path,&format!("spbu-draft-meta-v1:{}",value.owner),encoded).map_err(|_|"DRAFT_SAVE_FAILED")?;value.saved=true;Ok(())
}
fn read_meta(root:&Path,owner:&str)->Result<Value,String>{
    let plain=super::vault::read(&active_path(root,owner),&format!("spbu-draft-meta-v1:{owner}"),1048576).map_err(|_|"DRAFT_RESTORE_FAILED")?;
    serde_json::from_slice(&plain).map_err(|_|"DRAFT_RESTORE_FAILED".into())
}
fn restore(root:&Path,owner:&str,epoch:u64)->Result<Option<Draft>,String>{
    if !active_path(root,owner).try_exists().map_err(|_|"DRAFT_RESTORE_FAILED")?{return Ok(None);}
    let data=read_meta(root,owner)?;let id=data["id"].as_str().ok_or("DRAFT_RESTORE_FAILED")?;
    if uuid::Uuid::parse_str(id).is_err()||data["owner"]!=owner||data["version"]!=1{return Err("DRAFT_RESTORE_FAILED".into());}
    let mut value=fresh(root,owner,epoch)?;value.id=id.into();value.content=validate_content(&data["content"])?;value.revision=data["revision"].as_u64().ok_or("DRAFT_RESTORE_FAILED")?;value.status=data["state"].as_str().ok_or("DRAFT_RESTORE_FAILED")?.into();
    let files=data["files"].as_array().ok_or("DRAFT_RESTORE_FAILED")?;if files.len()>10{return Err("DRAFT_RESTORE_FAILED".into());}
    let mut total=0u64;for file in files{
        let name=file["token"].as_str().ok_or("DRAFT_RESTORE_FAILED")?;
        if name.strip_suffix(".bin").and_then(|n|uuid::Uuid::parse_str(n).ok()).is_none()||file["name"].as_str().map(|n|n.len()>2048).unwrap_or(true){return Err("DRAFT_RESTORE_FAILED".into());}
        let size=file["size"].as_u64().ok_or("DRAFT_RESTORE_FAILED")?;total=total.checked_add(size).ok_or("DRAFT_RESTORE_FAILED")?;if total>104857600{return Err("DRAFT_RESTORE_FAILED".into());}
        let plain=super::vault::read(&blob_path(&value,name),&blob_context(&value,name),size).map_err(|_|"DRAFT_RESTORE_FAILED")?;if plain.len() as u64!=size{return Err("DRAFT_RESTORE_FAILED".into());}
        std::fs::write(value.directory.path().join(name),plain).map_err(|_|"DRAFT_RESTORE_FAILED")?;value.files.push(file.clone());
    }
    let receipt=root.join("send-receipts").join(format!("{}.state",value.id));
    if receipt.try_exists().unwrap_or(true){value.status=if std::fs::read_to_string(receipt).ok().as_deref()==Some("sent"){"sent"}else{"unknown"}.into();}
    else if value.status!="editing"&&value.status!="sent"{value.status="unknown".into();}
    value.terminal=value.status!="editing";value.saved=true;Ok(Some(value))
}
fn dto(value:&Draft)->Value{json!({"id":value.id,"revision":value.revision,"content":value.content,"files":value.files,"state":value.status,"saved":value.saved})}

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
    #[cfg(windows)]
    #[test]fn encrypted_draft_restores_files_owner_and_uncertain_attempt_without_retry(){
        let root=tempfile::tempdir().unwrap();let owner="fixture-owner";let mut value=fresh(root.path(),owner,3).unwrap();
        value.content=json!({"to":"to@fixture.invalid","cc":"","bcc":"hidden@fixture.invalid","subject":"Тема","body":"fixture-private-draft","from":"from@fixture.invalid","smtpHost":"smtp.fixture.invalid","smtpPort":"465","smtpSsl":true,"inReplyTo":"","references":[]});value.revision=7;
        let original=root.path().join("source.txt");std::fs::write(&original,b"fixture-attachment").unwrap();value.files.push(snapshot(&original,value.directory.path(),100).unwrap());persist(&mut value).unwrap();
        let id=value.id.clone();let file=value.files[0]["token"].as_str().unwrap().to_owned();drop(value);std::fs::write(original,b"original changed after selection").unwrap();
        let mut loaded=restore(root.path(),owner,4).unwrap().unwrap();assert_eq!(loaded.id,id);assert_eq!(loaded.revision,7);assert_eq!(loaded.content["body"],"fixture-private-draft");assert_eq!(std::fs::read(loaded.directory.path().join(&file)).unwrap(),b"fixture-attachment");assert!(restore(root.path(),"other-owner",4).unwrap().is_none());
        let encrypted=std::fs::read(active_path(root.path(),owner)).unwrap();assert!(!encrypted.windows(21).any(|b|b==b"fixture-private-draft"));
        loaded.status="pending".into();persist(&mut loaded).unwrap();drop(loaded);let pending=restore(root.path(),owner,5).unwrap().unwrap();assert!(pending.terminal);assert_eq!(pending.status,"unknown");assert_eq!(pending.id,id);drop(pending);
        std::fs::create_dir_all(root.path().join("send-receipts")).unwrap();std::fs::write(root.path().join("send-receipts").join(format!("{id}.state")),"sent").unwrap();let sent=restore(root.path(),owner,6).unwrap().unwrap();assert!(sent.terminal);assert_eq!(sent.status,"sent");
        let blob=blob_path(&sent,&file);let mut bytes=std::fs::read(&blob).unwrap();let middle=bytes.len()/2;bytes[middle]^=1;std::fs::write(blob,bytes).unwrap();drop(sent);assert!(restore(root.path(),owner,7).is_err());
    }
    #[test]fn ids_are_safe_unique_receipt_names(){let a=token();let b=token();assert_ne!(a,b);assert!(uuid::Uuid::parse_str(&a).is_ok());assert_eq!(Path::new(&a).components().count(),1);}
}

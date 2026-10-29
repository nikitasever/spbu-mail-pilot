use super::{Engine,Reply,Request,Ordering};
use serde_json::json;
use std::{path::{Path,PathBuf},sync::Arc};
use tauri_plugin_dialog::DialogExt;

pub fn openable(path:&Path)->bool {
    let extension=path.extension().and_then(|s| s.to_str()).unwrap_or("").to_ascii_lowercase();
    ["pdf","txt","csv","png","jpg","jpeg","gif","webp","bmp","doc","docx","xls","xlsx","ppt","pptx","odt","ods","odp","rtf","eml","msg","ics","vcf","zip","7z","rar","mp3","mp4"].contains(&extension.as_str())
}
fn fault(id:&str,code:&str)->Reply{Ok(json!({"id":id,"error":{"code":code}}))}

pub fn save(mut request:Request,app:&tauri::AppHandle,engine:&Arc<Engine>)->Reply {
    let id=request.id.clone();
    let epoch=engine.epoch.load(Ordering::SeqCst);
    if !request.args.is_object()||id.is_empty()||id.len()>64||!id.bytes().all(|c|c.is_ascii_alphanumeric()||b"._-".contains(&c)) {return Err("BAD_REQUEST".into());}
    let suggested=request.args["name"].as_str().unwrap_or("Вложение");
    // A filename is display metadata, never a path supplied to the OS opener.
    let name=suggested.rsplit(['/', '\\']).next().unwrap_or("Вложение").chars().filter(|c|!c.is_control()&&!"<>:\"|?*".contains(*c)).take(160).collect::<String>();
    let name=if name.trim_matches([' ','.']).is_empty(){"Вложение"}else{name.as_str()};
    let Some(destination)=app.dialog().file().set_title("Сохранить вложение").set_file_name(name).blocking_save_file() else {
        return Ok(json!({"id":id,"result":{"cancelled":true}}));
    };
    let Ok(destination)=destination.into_path() else {return fault(&id,"SAVE_FAILED");};
    if epoch!=engine.epoch.load(Ordering::SeqCst){return fault(&id,"CANCELLED");}
    let Ok(directory)=tempfile::Builder::new().prefix("spbu-mail-transfer-").tempdir() else{return fault(&id,"SAVE_FAILED");};
    request.args["directory"]=json!(directory.path());
    let reply=engine.request(request)?;
    if reply.get("error").is_some(){return Ok(reply);}
    let Some(file)=reply["result"]["file"].as_str() else{return fault(&id,"BAD_RESPONSE");};
    // The engine may only supply a regular file from this specific private download.
    let source=match std::fs::symlink_metadata(file){
        Ok(meta) if meta.file_type().is_file()&&meta.len()<=104857600=>PathBuf::from(file),
        _=>return fault(&id,"FILE_UNAVAILABLE"),
    };
    let Ok(source)=source.canonicalize() else{return fault(&id,"FILE_UNAVAILABLE");};
    let Ok(root)=directory.path().canonicalize() else{return fault(&id,"FILE_UNAVAILABLE");};
    if source.parent()!=Some(root.as_path()){return fault(&id,"FILE_UNAVAILABLE");}
    let mut files=engine.saved.lock().map_err(|_|"FILE_UNAVAILABLE")?;
    if epoch!=engine.epoch.load(Ordering::SeqCst){return fault(&id,"CANCELLED");}
    let Some(parent)=destination.parent() else{return fault(&id,"SAVE_FAILED");};
    // Copy beside the destination then replace atomically; failed transfers preserve existing files.
    let saved=(||->std::io::Result<()>{
        let mut output=tempfile::NamedTempFile::new_in(parent)?;
        let mut input=std::fs::File::open(&source)?;
        std::io::copy(&mut input,&mut output)?;
        output.as_file().sync_all()?;
        output.persist(&destination).map_err(|error|error.error)?;
        Ok(())
    })();
    if saved.is_err(){return fault(&id,"SAVE_FAILED");}
    let can_open=openable(&destination);
    if files.len()>=64 {if let Some(key)=files.keys().next().cloned(){files.remove(&key);}}
    files.insert(id.clone(),destination);
    Ok(json!({"id":id,"result":{"saved":true,"token":id,"openable":can_open}}))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]fn only_documents_can_be_opened(){
        assert!(openable(Path::new("Пересланное письмо.eml")));
        assert!(openable(Path::new("DOC.PDF")));
        for name in ["file.exe","file.pdf.exe","script.cmd","link.url","link.lnk","page.html","image.svg","file"]{assert!(!openable(Path::new(name)));}
    }
}

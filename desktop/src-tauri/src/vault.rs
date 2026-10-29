use serde_json::{json,Value};
use std::path::{Path,PathBuf};
use sha2::{Digest,Sha256};
use tauri::Manager;
use super::{Engine,Request,Reply};
use std::sync::Arc;

// DPAPI CurrentUser with context-bound entropy. No plaintext fallback on other platforms.
#[cfg(windows)]
fn crypt(context:&[u8],mut bytes:Vec<u8>,encrypt:bool)->Result<Vec<u8>,String>{
    #[repr(C)]struct Blob{length:u32,data:*mut u8}
    #[link(name="crypt32")]extern "system"{
        fn CryptProtectData(input:*const Blob,description:*const u16,entropy:*const Blob,reserved:*mut std::ffi::c_void,prompt:*mut std::ffi::c_void,flags:u32,output:*mut Blob)->i32;
        fn CryptUnprotectData(input:*const Blob,description:*mut *mut u16,entropy:*const Blob,reserved:*mut std::ffi::c_void,prompt:*mut std::ffi::c_void,flags:u32,output:*mut Blob)->i32;
    }
    #[link(name="kernel32")]extern "system"{fn LocalFree(memory:*mut std::ffi::c_void)->*mut std::ffi::c_void;}
    if bytes.len()>110*1024*1024{return Err("VAULT_UNAVAILABLE".into());}
    let input=Blob{length:bytes.len() as u32,data:bytes.as_mut_ptr()};
    let entropy=Blob{length:context.len() as u32,data:context.as_ptr() as *mut u8};
    let mut output=Blob{length:0,data:std::ptr::null_mut()};
    let ok=unsafe{if encrypt{CryptProtectData(&input,std::ptr::null(),&entropy,std::ptr::null_mut(),std::ptr::null_mut(),1,&mut output)}else{CryptUnprotectData(&input,std::ptr::null_mut(),&entropy,std::ptr::null_mut(),std::ptr::null_mut(),1,&mut output)}};
    bytes.fill(0);drop(bytes);
    if ok==0{return Err("VAULT_UNAVAILABLE".into());}
    let result=if output.length==0{Vec::new()}else if output.data.is_null(){return Err("VAULT_UNAVAILABLE".into());}else{unsafe{std::slice::from_raw_parts(output.data,output.length as usize).to_vec()}};
    unsafe{if !encrypt&&output.length>0{std::ptr::write_bytes(output.data,0,output.length as usize);}LocalFree(output.data.cast());}
    Ok(result)
}
#[cfg(not(windows))]
fn crypt(_context:&[u8],_bytes:Vec<u8>,_encrypt:bool)->Result<Vec<u8>,String>{Err("VAULT_UNAVAILABLE".into())}
pub fn write(path:&Path,context:&str,plain:Vec<u8>)->Result<(),String>{
    use std::io::Write;
    let cipher=crypt(context.as_bytes(),plain,true)?;
    let parent=path.parent().ok_or("VAULT_UNAVAILABLE")?;
    std::fs::create_dir_all(parent).map_err(|_|"VAULT_UNAVAILABLE")?;
    let mut temporary=tempfile::NamedTempFile::new_in(parent).map_err(|_|"VAULT_UNAVAILABLE")?;
    temporary.write_all(&cipher).and_then(|_|temporary.as_file().sync_all()).map_err(|_|"VAULT_UNAVAILABLE")?;
    temporary.persist(path).map_err(|_|"VAULT_UNAVAILABLE")?;Ok(())
}
pub fn read(path:&Path,context:&str,limit:u64)->Result<Vec<u8>,String>{
    use std::io::Read;
    let file=std::fs::File::open(path).map_err(|_|"VAULT_UNAVAILABLE")?;
    if file.metadata().map_err(|_|"VAULT_UNAVAILABLE")?.len()>limit+1024*1024{return Err("VAULT_UNAVAILABLE".into());}
    let mut cipher=Vec::new();file.take(limit+1024*1024+1).read_to_end(&mut cipher).map_err(|_|"VAULT_UNAVAILABLE")?;
    if cipher.len() as u64>limit+1024*1024{return Err("VAULT_UNAVAILABLE".into());}
    let plain=crypt(context.as_bytes(),cipher,false)?;
    if plain.len() as u64>limit{return Err("VAULT_UNAVAILABLE".into());}Ok(plain)
}
pub fn root(app:&tauri::AppHandle)->Result<PathBuf,String>{app.path().app_data_dir().map_err(|_|"VAULT_UNAVAILABLE".into())}
pub fn owner(args:&Value)->Result<String,String>{
    let host=args["host"].as_str().ok_or("BAD_REQUEST")?.to_lowercase();let user=args["user"].as_str().ok_or("BAD_REQUEST")?.to_lowercase();let port=args["port"].as_u64().ok_or("BAD_REQUEST")?;
    let value=serde_json::to_vec(&json!([host,port,user])).map_err(|_|"BAD_REQUEST")?;
    Ok(format!("{:x}",Sha256::digest(value)))
}
fn load(app:&tauri::AppHandle)->Result<Value,String>{
    let bytes=read(&root(app)?.join("account.dpapi"),"spbu-account-v1",32768)?;
    let value:Value=serde_json::from_slice(&bytes).map_err(|_|"VAULT_UNAVAILABLE")?;
    for key in ["host","user","password"]{if !value[key].is_string(){return Err("VAULT_UNAVAILABLE".into());}}
    if !value["port"].is_u64(){return Err("VAULT_UNAVAILABLE".into());}Ok(value)
}
pub fn remember(app:&tauri::AppHandle,args:&Value)->Result<(),String>{
    let value=json!({"host":args["host"],"port":args["port"],"user":args["user"],"password":args["password"]});
    write(&root(app)?.join("account.dpapi"),"spbu-account-v1",serde_json::to_vec(&value).map_err(|_|"VAULT_UNAVAILABLE")?)
}
#[tauri::command]
pub fn account_status(app:tauri::AppHandle)->Result<Value,String>{
    if !root(&app)?.join("account.dpapi").try_exists().map_err(|_|"VAULT_UNAVAILABLE")?{return Ok(Value::Null);}
    let value=load(&app)?;Ok(json!({"host":value["host"],"port":value["port"],"user":value["user"]}))
}
#[tauri::command]
pub fn account_forget(app:tauri::AppHandle)->Result<(),String>{
    let file=root(&app)?.join("account.dpapi");match std::fs::remove_file(file){Ok(())=>Ok(()),Err(error) if error.kind()==std::io::ErrorKind::NotFound=>Ok(()),Err(_)=>Err("VAULT_UNAVAILABLE".into())}
}
pub fn connect_saved(mut request:Request,app:&tauri::AppHandle,engine:&Arc<Engine>)->Reply{
    request.method="connect".into();request.args=load(app)?;
    super::connect(request,app,engine,false)
}
#[cfg(all(test,windows))]mod tests{
    use super::*;
    #[test]fn account_identity_binds_server_port_user_but_not_password(){
        let a=json!({"host":"MAIL.fixture.invalid","port":993,"user":"USER@fixture.invalid","password":"one"});
        let b=json!({"host":"mail.fixture.invalid","port":993,"user":"user@fixture.invalid","password":"two"});
        assert_eq!(owner(&a).unwrap(),owner(&b).unwrap());let mut c=b.clone();c["port"]=json!(994);assert_ne!(owner(&a).unwrap(),owner(&c).unwrap());c=b;c["host"]=json!("other.fixture.invalid");assert_ne!(owner(&a).unwrap(),owner(&c).unwrap());
    }
    #[test]fn dpapi_roundtrip_context_tamper_and_no_plaintext(){
        let folder=tempfile::tempdir().unwrap();let path=folder.path().join("secret");let secret="Пароль и текст черновика fixture-secret".as_bytes().to_vec();
        write(&path,"fixture-owner",secret.clone()).unwrap();let cipher=std::fs::read(&path).unwrap();
        assert!(!cipher.windows(14).any(|bytes|bytes==b"fixture-secret"));assert_eq!(read(&path,"fixture-owner",1000).unwrap(),secret);
        assert!(read(&path,"other-owner",1000).is_err());let empty=folder.path().join("empty");write(&empty,"empty",Vec::new()).unwrap();assert!(read(&empty,"empty",1).unwrap().is_empty());
        let mut broken=cipher;let index=broken.len()/2;broken[index]^=1;std::fs::write(&path,broken).unwrap();assert!(read(&path,"fixture-owner",1000).is_err());
    }
}

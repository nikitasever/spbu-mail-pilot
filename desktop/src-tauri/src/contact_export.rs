use super::{Engine,Request,Reply,Ordering,contact_book};
use serde_json::{json,Value};
use std::{io::Write,path::{Path,PathBuf}};
use tauri_plugin_dialog::DialogExt;
fn owner(engine:&Engine,epoch:u64)->Result<String,String>{if engine.epoch.load(Ordering::SeqCst)!=epoch{return Err("CANCELLED".into());}engine.owner.lock().map_err(|_|"NO_SESSION")?.clone().ok_or("NO_SESSION".into())}
fn safe_csv(value:&str)->String{
    let danger=value.starts_with('\'')||value.trim_start().starts_with(['=','+','-','@']);
    let text=if danger{format!("'{value}")}else{value.to_string()};format!("\"{}\"",text.replace('"',"\"\""))
}
fn escape(value:&str)->String{let mut text=String::new();for c in value.chars(){match c{'\\'=>text.push_str("\\\\"),';'=>text.push_str("\\;"),','=>text.push_str("\\,"),'\r'=>{},'\n'=>text.push_str("\\n"),_=>text.push(c)}}text}
fn line(output:&mut String,value:&str){let mut length=0;for c in value.chars(){if length+c.len_utf8()>75{output.push_str("\r\n ");length=1;}output.push(c);length+=c.len_utf8();}output.push_str("\r\n");}
fn range(book:&Value,part:&Value)->Result<std::ops::Range<usize>,String>{
    let size=book["people"].as_array().ok_or("CONTACTS_STORE")?.len();if size==0{return Err("CONTACTS_EXPORT_EMPTY".into());}if size>5000{return Err("CONTACTS_LIMIT".into());}
    if part.is_null(){return Ok(0..size);}let index=part.as_u64().filter(|n|*n<10).ok_or("BAD_REQUEST")? as usize;let start=index*500;if start>=size{return Err("BAD_REQUEST".into());}Ok(start..std::cmp::min(start+500,size))
}
fn encode(book:&Value,format:&str,part:&Value)->Result<(Vec<u8>,usize),String>{
    if !["csv","vcf"].contains(&format){return Err("BAD_REQUEST".into());}let selected=range(book,part)?;let count=selected.len();let rows=&book["people"].as_array().ok_or("CONTACTS_STORE")?[selected];
    let mut output=if format=="csv"{"\u{feff}Name,Email,SPbU Safe CSV\r\n".into()}else{String::new()};
    for row in rows{let sender=row["sender"].as_str().ok_or("CONTACTS_STORE")?;let email=row["email"].as_str().ok_or("CONTACTS_STORE")?;
        if !contact_book::valid_person(sender,email){return Err("CONTACTS_STORE".into());}
        if format=="csv"{output.push_str(&format!("{},{},1\r\n",safe_csv(sender),safe_csv(email)));}
        else{let id=row["id"].as_str().filter(|id|uuid::Uuid::parse_str(id).is_ok()).ok_or("CONTACTS_STORE")?;for value in ["BEGIN:VCARD".into(),"VERSION:3.0".into(),format!("UID:urn:uuid:{id}"),format!("FN:{}",escape(sender)),format!("N:;{};;;",escape(sender)),format!("EMAIL;TYPE=INTERNET:{}",escape(email)),"END:VCARD".into()]{line(&mut output,&value)}}
    }Ok((output.into_bytes(),count))
}
fn destination(mut path:PathBuf,format:&str,root:&Path)->Result<PathBuf,String>{
    if !path.is_absolute(){return Err("CONTACTS_EXPORT_SAVE".into());}
    match path.extension().and_then(|s|s.to_str()) {None|Some("")=>{path.set_extension(format);},Some(ext)if ext.eq_ignore_ascii_case(format)=>{},_=>return Err("CONTACTS_EXPORT_TYPE".into())}
    let parent=dunce::canonicalize(path.parent().ok_or("CONTACTS_EXPORT_SAVE")?).map_err(|_|"CONTACTS_EXPORT_SAVE")?;
    let root=dunce::canonicalize(root).map_err(|_|"CONTACTS_EXPORT_SAVE")?;
    let normalized=|p:&Path|p.components().map(|c|c.as_os_str().to_string_lossy().to_lowercase()).collect::<Vec<_>>();
    if normalized(&parent).starts_with(&normalized(&root)){return Err("CONTACTS_EXPORT_SAVE".into());}
    match std::fs::symlink_metadata(&path){Ok(meta)if !meta.file_type().is_file()=>return Err("CONTACTS_EXPORT_SAVE".into()),Err(error)if error.kind()!=std::io::ErrorKind::NotFound=>return Err("CONTACTS_EXPORT_SAVE".into()),_=>{}}
    Ok(path)
}
fn write_atomic(path:&Path,bytes:&[u8])->Result<(),String>{
    let mut file=tempfile::NamedTempFile::new_in(path.parent().ok_or("CONTACTS_EXPORT_SAVE")?).map_err(|_|"CONTACTS_EXPORT_SAVE")?;
    file.write_all(bytes).and_then(|_|file.as_file().sync_all()).map_err(|_|"CONTACTS_EXPORT_SAVE")?;file.persist(path).map_err(|_|"CONTACTS_EXPORT_SAVE")?;Ok(())
}
pub fn execute(request:Request,app:&tauri::AppHandle,engine:&Engine)->Reply{
    if request.id.is_empty()||request.id.len()>64||!request.id.bytes().all(|c|c.is_ascii_alphanumeric()||b"._-".contains(&c)){return Err("BAD_REQUEST".into());}
    let epoch=request.args["epoch"].as_u64().ok_or("BAD_REQUEST")?;let gate=engine.session_gate.lock().map_err(|_|"NO_SESSION")?;let scope=owner(engine,epoch)?;
    let root=super::vault::root(app)?;let book_path=root.join("contacts").join(&scope).join("book.dpapi");let book=contact_book::snapshot(&book_path,&scope)?;
    if book["revision"]!=request.args["revision"]{return Err("CONTACTS_CONFLICT".into());}
    let format=request.args["format"].as_str().ok_or("BAD_REQUEST")?;let part=request.args.get("part").ok_or("BAD_REQUEST")?;let (bytes,count)=encode(&book,format,part)?;drop(gate);
    let name=if let Some(index)=part.as_u64(){format!("contacts-part-{}.{}",index+1,format)}else{format!("contacts.{format}")};
    let Some(picked)=app.dialog().file().set_title("Сохранить контакты").set_file_name(&name).add_filter(if format=="csv"{"CSV UTF-8"}else{"vCard 3.0 UTF-8"},&[format]).blocking_save_file()else{return Ok(json!({"id":request.id,"result":{"cancelled":true}}));};
    let path=picked.into_path().map_err(|_|"CONTACTS_EXPORT_SAVE")?;let path=destination(path,format,&root)?;
    let _gate=engine.session_gate.lock().map_err(|_|"NO_SESSION")?;owner(engine,epoch)?;let current=contact_book::snapshot(&book_path,&scope)?;if current["revision"]!=book["revision"]{return Err("CONTACTS_CONFLICT".into());}
    write_atomic(&path,&bytes)?;let name=path.file_name().and_then(|s|s.to_str()).unwrap_or("Контакты").chars().filter(|c|!c.is_control()).take(160).collect::<String>();
    Ok(json!({"id":request.id,"result":{"saved":true,"format":format,"count":count,"name":name}}))
}
#[cfg(test)]mod tests{
 use super::*;use crate::contact_import_parser as parser;
 fn book()->Value{json!({"revision":"revision","people":[{"id":"11111111-1111-4111-8111-111111111111","sender":"=1+1","email":"+tag@example.invalid"},{"id":"22222222-2222-4222-8222-222222222222","sender":"'Настоящий апостроф","email":"two@example.invalid"},{"id":"33333333-3333-4333-8333-333333333333","sender":"Иванов, Иван; \\ \"Ваня\"","email":"three@example.invalid"}]})}
 #[test]fn csv_formula_protection_and_lossless_own_roundtrip(){
  let source=book();let (bytes,count)=encode(&source,"csv",&Value::Null).unwrap();assert_eq!(count,3);let text=parser::decode(&bytes).unwrap();assert!(text.contains("\"'=1+1\""));assert!(text.contains("\"'+tag@example.invalid\""));
  let rows=parser::csv(&text).unwrap();for (n,row) in rows.iter().enumerate(){assert_eq!(row.sender,source["people"][n]["sender"]);assert_eq!(row.email,source["people"][n]["email"]);}
 }
 #[test]fn vcard_escapes_and_utf8_line_folding_roundtrip(){
  let mut source=book();source["people"][0]["sender"]=json!("Ж".repeat(170));let (bytes,count)=encode(&source,"vcf",&Value::Null).unwrap();assert_eq!(count,3);let text=std::str::from_utf8(&bytes).unwrap();assert!(text.split("\r\n").all(|s|s.len()<=75));let rows=parser::vcard(text).unwrap();for(n,row)in rows.iter().enumerate(){assert_eq!(row.sender,source["people"][n]["sender"]);assert_eq!(row.email,source["people"][n]["email"]);}
 }
 #[test]fn parts_are_bounded_explicit_and_not_frontend_supplied_records(){
  let source=json!({"people":vec![book()["people"][0].clone();501]});assert_eq!(range(&source,&json!(0)).unwrap(),0..500);assert_eq!(range(&source,&json!(1)).unwrap(),500..501);assert!(range(&source,&json!(2)).is_err());assert!(range(&json!({"people":[]}),&Value::Null).is_err());assert!(encode(&book(),"exe",&Value::Null).is_err());
 }
 #[test]fn chosen_destination_and_atomic_write_preserve_app_data(){
  let folder=tempfile::tempdir().unwrap();let root=folder.path().join("app-data");std::fs::create_dir(&root).unwrap();let path=folder.path().join("contacts");let csv=destination(path,"csv",&root).unwrap();assert_eq!(csv.extension().unwrap(),"csv");assert!(destination(root.join("contacts.csv"),"csv",&root).is_err());assert!(destination(folder.path().join("file.exe"),"csv",&root).is_err());
  std::fs::write(&csv,b"old").unwrap();write_atomic(&csv,b"new").unwrap();assert_eq!(std::fs::read(&csv).unwrap(),b"new");let directory=folder.path().join("target.csv");std::fs::create_dir(&directory).unwrap();std::fs::write(directory.join("keep"),b"original").unwrap();assert!(write_atomic(&directory,b"fail").is_err());assert_eq!(std::fs::read(directory.join("keep")).unwrap(),b"original");
 }
}

use super::{Engine,Request,Reply,Ordering};
use serde::{Deserialize,Serialize};
use serde_json::{json,Value};
use std::path::Path;
use uuid::Uuid;
const LIMIT:u64=8*1024*1024;
#[derive(Clone,Serialize,Deserialize)]
struct Person{id:String,sender:String,email:String}
#[derive(Serialize,Deserialize)]
struct Book{version:u8,owner:String,revision:String,people:Vec<Person>}
pub(super) fn valid_person(sender:&str,email:&str)->bool{
    let parts:Vec<_>=email.split('@').collect();
    !sender.trim().is_empty()&&sender.encode_utf16().count()<=180&&!sender.chars().any(char::is_control)&&email.len()<=254&&parts.len()==2&&parts.iter().all(|v|!v.is_empty())&&!email.chars().any(|c|c.is_whitespace()||c.is_control()||"<>,;\"".contains(c))
}
fn load(path:&Path,owner:&str)->Result<Book,String>{
    if !path.try_exists().map_err(|_|"CONTACTS_STORE")?{return Ok(Book{version:1,owner:owner.into(),revision:"0".into(),people:vec![]});}
    let plain=super::vault::read(path,&format!("spbu-contacts-v1:{owner}"),LIMIT).map_err(|_|"CONTACTS_STORE")?;
    let book:Book=serde_json::from_slice(&plain).map_err(|_|"CONTACTS_STORE")?;
    if book.version!=1||book.owner!=owner||Uuid::parse_str(&book.revision).is_err()||book.people.len()>5000{return Err("CONTACTS_STORE".into());}
    let mut ids=std::collections::HashSet::new();let mut emails=std::collections::HashSet::new();
    for row in &book.people{if Uuid::parse_str(&row.id).is_err()||!valid_person(&row.sender,&row.email)||!ids.insert(&row.id)||!emails.insert(row.email.to_lowercase()){return Err("CONTACTS_STORE".into());}}
    Ok(book)
}
fn public(book:&Book)->Value{json!({"revision":book.revision,"people":book.people})}
fn apply(path:&Path,owner:&str,method:&str,args:&Value)->Result<Value,String>{
    let mut book=load(path,owner)?;
    if method=="localContactsRead"{return Ok(public(&book));}
    if args["revision"].as_str()!=Some(book.revision.as_str()){return Err("CONTACTS_CONFLICT".into());}
    let id=args["id"].as_str().ok_or("BAD_REQUEST")?;
    match method{
        "localContactsSave"=>{
            let sender=args["sender"].as_str().ok_or("BAD_REQUEST")?.trim();let email=args["email"].as_str().ok_or("BAD_REQUEST")?.trim();
            if !valid_person(sender,email){return Err("BAD_CONTACT".into());}
            if book.people.iter().any(|row|row.id!=id&&row.email.to_lowercase()==email.to_lowercase()){return Err("CONTACTS_EXISTS".into());}
            if id.is_empty(){if book.people.len()>=5000{return Err("CONTACTS_LIMIT".into());}book.people.push(Person{id:Uuid::new_v4().to_string(),sender:sender.into(),email:email.into()});}
            else{let row=book.people.iter_mut().find(|row|row.id==id).ok_or("CONTACTS_CONFLICT")?;row.sender=sender.into();row.email=email.into();}
        }
        "localContactsDelete"=>{let index=book.people.iter().position(|row|row.id==id).ok_or("CONTACTS_CONFLICT")?;book.people.remove(index);}
        _=>return Err("BAD_REQUEST".into())
    }
    persist(path,owner,book)
}
fn persist(path:&Path,owner:&str,mut book:Book)->Result<Value,String>{
    book.revision=Uuid::new_v4().to_string();let bytes=serde_json::to_vec(&book).map_err(|_|"CONTACTS_STORE")?;
    if bytes.len() as u64>LIMIT{return Err("CONTACTS_LIMIT".into());}
    super::vault::write(path,&format!("spbu-contacts-v1:{owner}"),bytes).map_err(|_|"CONTACTS_STORE")?;Ok(public(&book))
}
pub(super) fn snapshot(path:&Path,owner:&str)->Result<Value,String>{Ok(public(&load(path,owner)?))}
pub(super) fn import(path:&Path,owner:&str,revision:&str,rows:&[(String,String)])->Result<Value,String>{
    let mut book=load(path,owner)?;if book.revision!=revision{return Err("CONTACTS_CONFLICT".into());}
    if rows.is_empty()||rows.len()>500{return Err("BAD_REQUEST".into());}if book.people.len()+rows.len()>5000{return Err("CONTACTS_LIMIT".into());}
    let mut emails:std::collections::HashSet<String>=book.people.iter().map(|row|row.email.to_lowercase()).collect();
    for (sender,email) in rows{if !valid_person(sender,email){return Err("BAD_CONTACT".into());}if !emails.insert(email.to_lowercase()){return Err("CONTACTS_EXISTS".into());}}
    for (sender,email) in rows{book.people.push(Person{id:Uuid::new_v4().to_string(),sender:sender.clone(),email:email.clone()});}persist(path,owner,book)
}
pub fn execute(request:Request,app:&tauri::AppHandle,engine:&Engine)->Reply{
    if request.id.is_empty()||request.id.len()>64||!request.id.bytes().all(|c|c.is_ascii_alphanumeric()||b"._-".contains(&c)){return Err("BAD_REQUEST".into());}
    let _gate=engine.session_gate.lock().map_err(|_|"NO_SESSION")?;
    let epoch=request.args["epoch"].as_u64().ok_or("BAD_REQUEST")?;
    if epoch!=engine.epoch.load(Ordering::SeqCst){return Err("CANCELLED".into());}
    let owner=engine.owner.lock().map_err(|_|"NO_SESSION")?.clone().ok_or("NO_SESSION")?;
    let path=super::vault::root(app)?.join("contacts").join(&owner).join("book.dpapi");
    Ok(json!({"id":request.id,"result":apply(&path,&owner,&request.method,&request.args)?}))
}
#[cfg(all(test,windows))]mod tests{
    use super::*;
    #[test]fn batch_import_is_atomic_and_revision_bound(){
        let folder=tempfile::tempdir().unwrap();let path=folder.path().join("book.dpapi");let rows=vec![("One".into(),"one@example.invalid".into()),("Two".into(),"two@example.invalid".into())];
        let book=import(&path,"owner","0",&rows).unwrap();assert_eq!(book["people"].as_array().unwrap().len(),2);let bytes=std::fs::read(&path).unwrap();
        assert!(import(&path,"owner","0",&rows).is_err());assert!(import(&path,"owner",book["revision"].as_str().unwrap(),&[("Three".into(),"three@example.invalid".into()),("Dup".into(),"ONE@example.invalid".into())]).is_err());assert_eq!(std::fs::read(&path).unwrap(),bytes);
    }
    #[test]fn encrypted_book_cas_identity_validation_and_failure_preserve_data(){
        let folder=tempfile::tempdir().unwrap();let path=folder.path().join("book.dpapi");
        let first=apply(&path,"owner-a","localContactsSave",&json!({"revision":"0","id":"","sender":"Имя","email":"person@example.invalid"})).unwrap();
        let id=first["people"][0]["id"].as_str().unwrap();let revision=first["revision"].as_str().unwrap();
        assert!(!std::fs::read(&path).unwrap().windows(22).any(|part|part==b"person@example.invalid"));assert!(load(&path,"owner-b").is_err());
        assert_eq!(apply(&path,"owner-a","localContactsSave",&json!({"revision":"0","id":"","sender":"Other","email":"other@example.invalid"})).unwrap_err(),"CONTACTS_CONFLICT");
        assert_eq!(apply(&path,"owner-a","localContactsSave",&json!({"revision":revision,"id":"","sender":"Duplicate","email":"PERSON@example.invalid"})).unwrap_err(),"CONTACTS_EXISTS");
        assert!(apply(&path,"owner-a","localContactsSave",&json!({"revision":revision,"id":id,"sender":"New","email":"x@example.invalid\r\nBcc: bad@x.invalid"})).is_err());
        assert_eq!(load(&path,"owner-a").unwrap().revision,revision);
        let updated=apply(&path,"owner-a","localContactsSave",&json!({"revision":revision,"id":id,"sender":"Новое имя","email":"new@example.invalid"})).unwrap();assert_eq!(updated["people"][0]["id"],id);
        assert!(apply(&path,"owner-a","localContactsDelete",&json!({"revision":revision,"id":id})).is_err());
        let deleted=apply(&path,"owner-a","localContactsDelete",&json!({"revision":updated["revision"],"id":id})).unwrap();assert!(deleted["people"].as_array().unwrap().is_empty());
        std::fs::write(&path,b"broken").unwrap();assert!(apply(&path,"owner-a","localContactsSave",&json!({"revision":"0","id":"","sender":"New","email":"new@example.invalid"})).is_err());assert_eq!(std::fs::read(&path).unwrap(),b"broken");
    }
}

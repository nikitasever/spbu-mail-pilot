use super::{Engine,Request,Reply,Ordering};
use serde_json::{json,Value};
use sha2::{Digest,Sha256};
use std::sync::{Arc,atomic::AtomicBool};
use url::Url;
fn fault(id:&str,code:&str)->Reply{Ok(json!({"id":id,"error":{"code":code}}))}
fn address(value:&str)->Result<Url,String>{
    if value.len()>8192{return Err("BAD_REQUEST".into());}let url=Url::parse(value).map_err(|_|"BAD_REQUEST")?;
    if url.scheme()!="https"||url.host_str().is_none()||!url.username().is_empty()||url.password().is_some()||url.fragment().is_some(){return Err("BAD_REQUEST".into());}Ok(url)
}
fn owner(engine:&Engine,epoch:u64)->Result<String,String>{
    if engine.epoch.load(Ordering::SeqCst)!=epoch{return Err("CANCELLED".into());}engine.owner.lock().map_err(|_|"NO_SESSION")?.clone().ok_or("NO_SESSION".into())
}
fn path(app:&tauri::AppHandle,owner:&str)->Result<std::path::PathBuf,String>{Ok(super::vault::root(app)?.join("calendars").join(owner).join("connection.dpapi"))}
fn read(app:&tauri::AppHandle,owner:&str)->Result<Option<Value>,String>{
    let file=path(app,owner)?;if !file.try_exists().map_err(|_|"CALENDAR_STORE")?{return Ok(None);}
    let plain=super::vault::read(&file,&format!("spbu-calendar-v1:{owner}"),2*1024*1024).map_err(|_|"CALENDAR_STORE")?;
    let value:Value=serde_json::from_slice(&plain).map_err(|_|"CALENDAR_STORE")?;
    if value["owner"]!=owner||value["version"]!=1||value["profileId"].as_str().and_then(|id|uuid::Uuid::parse_str(id).ok()).is_none(){return Err("CALENDAR_STORE".into());}
    let server=address(value["server"].as_str().ok_or("CALENDAR_STORE")?)?;
    let calendars=value["calendars"].as_array().ok_or("CALENDAR_STORE")?;
    if calendars.is_empty()||calendars.len()>100{return Err("CALENDAR_STORE".into());}
    for item in calendars{let url=address(item["url"].as_str().ok_or("CALENDAR_STORE")?)?;if url.origin()!=server.origin()||item["id"].as_str()!=Some(format!("{:x}",Sha256::digest(url.as_str().as_bytes())).as_str())||item["title"].as_str().map(|title|title.len()>2048).unwrap_or(true){return Err("CALENDAR_STORE".into());}}
    if value["user"].as_str().map(|v|v.is_empty()||v.len()>1280).unwrap_or(true)||value["password"].as_str().map(|v|v.is_empty()||v.len()>8192).unwrap_or(true){return Err("CALENDAR_STORE".into());}Ok(Some(value))
}
fn write(app:&tauri::AppHandle,owner:&str,value:&Value)->Result<(),String>{
    super::vault::write(&path(app,owner)?,&format!("spbu-calendar-v1:{owner}"),serde_json::to_vec(value).map_err(|_|"CALENDAR_STORE")?).map_err(|_|"CALENDAR_STORE".into())
}
fn dto(value:&Value)->Value{
    let names:Vec<Value>=value["calendars"].as_array().unwrap().iter().map(|item|json!({"id":item["id"],"title":item["title"]})).collect();
    let server=address(value["server"].as_str().unwrap_or("")).map(|url|url.origin().ascii_serialization()).unwrap_or_default();
    json!({"profileId":value["profileId"],"server":server,"user":value["user"],"selected":value["selected"],"calendars":names})
}
pub fn cancel(engine:&Engine,id:&str){if let Ok(jobs)=engine.calendar_jobs.lock(){if let Some(flag)=jobs.get(id){flag.store(true,Ordering::SeqCst);}}}
struct Ticket{engine:Arc<Engine>,id:String,flag:Arc<AtomicBool>}
impl Drop for Ticket{fn drop(&mut self){if let Ok(mut jobs)=self.engine.calendar_jobs.lock(){if jobs.get(&self.id).map(|flag|Arc::ptr_eq(flag,&self.flag)).unwrap_or(false){jobs.remove(&self.id);}}}}
pub async fn run(request:Request,app:tauri::AppHandle,engine:Arc<Engine>)->Reply{
    if request.id.is_empty()||request.id.len()>64||!request.id.bytes().all(|c|c.is_ascii_alphanumeric()||b"._-".contains(&c)){return Err("BAD_REQUEST".into());}
    let flag=Arc::new(AtomicBool::new(false));{
        let mut jobs=engine.calendar_jobs.lock().map_err(|_|"BUSY")?;if jobs.len()>=16||jobs.contains_key(&request.id){return Err("BUSY".into());}jobs.insert(request.id.clone(),flag.clone());
    }
    let ticket=Ticket{engine:engine.clone(),id:request.id.clone(),flag};
    tauri::async_runtime::spawn_blocking(move||{let ticket=ticket;execute(request,&app,&engine,&ticket.flag)}).await.map_err(|_|"CALENDAR_NETWORK".to_string())?
}
fn execute(mut request:Request,app:&tauri::AppHandle,engine:&Arc<Engine>,cancelled:&AtomicBool)->Reply{
    let id=request.id.clone();let epoch=request.args["epoch"].as_u64().ok_or("BAD_REQUEST")?;
    let gate=engine.session_gate.lock().map_err(|_|"NO_SESSION")?;let scope=owner(engine,epoch)?;
    if cancelled.load(Ordering::SeqCst){return fault(&id,"CANCELLED");}
    match request.method.as_str(){
        "calendarStatus"=>return Ok(json!({"id":id,"result":read(app,&scope)?.map(|value|dto(&value)).unwrap_or(Value::Null)})),
        "calendarForget"=>{
            let old=read(app,&scope)?;
            if let Some(value)=old{if value["profileId"]!=request.args["profileId"]{return fault(&id,"CALENDAR_CHANGED");}}
            engine.calendar_serial.fetch_add(1,Ordering::SeqCst);
            match std::fs::remove_file(path(app,&scope)?){Ok(())=>{},Err(error)if error.kind()==std::io::ErrorKind::NotFound=>{},Err(_)=>return fault(&id,"CALENDAR_STORE")};
            return Ok(json!({"id":id,"result":{"removed":true}}));
        }
        "calendarSelect"=>{
            let mut value=read(app,&scope)?.ok_or("CALENDAR_MISSING")?;
            if value["profileId"]!=request.args["profileId"]{return fault(&id,"CALENDAR_CHANGED");}
            if !value["calendars"].as_array().unwrap().iter().any(|item|item["id"]==request.args["calendarId"]){return fault(&id,"BAD_REQUEST");}
            value["selected"]=request.args["calendarId"].clone();write(app,&scope,&value)?;return Ok(json!({"id":id,"result":dto(&value)}));
        }
        "calendarConnect"=>{
            let server=address(request.args["server"].as_str().ok_or("BAD_REQUEST")?)?;
            let auth=request.args.clone();let serial=engine.calendar_serial.fetch_add(1,Ordering::SeqCst)+1;
            request.method="calendarDiscover".into();drop(gate);let reply=engine.request(request)?;
            if reply.get("error").is_some(){return Ok(reply);}
            let _gate=engine.session_gate.lock().map_err(|_|"NO_SESSION")?;owner(engine,epoch)?;
            if cancelled.load(Ordering::SeqCst)||engine.calendar_serial.load(Ordering::SeqCst)!=serial{return fault(&id,"CANCELLED");}
            let rows=reply["result"]["calendars"].as_array().ok_or("CALENDAR_DATA")?;
            if rows.is_empty()||rows.len()>100{return fault(&id,"CALENDAR_DATA");}
            let mut calendars=Vec::new();let mut seen=std::collections::HashSet::new();for row in rows{
                let url=address(row["url"].as_str().ok_or("CALENDAR_DATA")?)?;if url.origin()!=server.origin(){return fault(&id,"CALENDAR_ORIGIN");}
                let title=row["title"].as_str().ok_or("CALENDAR_DATA")?;if title.len()>2048{return fault(&id,"CALENDAR_DATA");}
                let key=format!("{:x}",Sha256::digest(url.as_str().as_bytes()));if seen.insert(key.clone()){calendars.push(json!({"id":key,"title":title,"url":url.as_str()}));}
            }
            let value=json!({"version":1,"owner":scope,"profileId":uuid::Uuid::new_v4().to_string(),"server":server.as_str(),"user":auth["user"],"password":auth["password"],"selected":calendars[0]["id"],"calendars":calendars});write(app,&scope,&value)?;
            Ok(json!({"id":id,"result":dto(&value)}))
        }
        "calendarMonth"=>{
            let value=read(app,&scope)?.ok_or("CALENDAR_MISSING")?;
            if value["profileId"]!=request.args["profileId"]{return fault(&id,"CALENDAR_CHANGED");}
            let calendar=value["calendars"].as_array().unwrap().iter().find(|item|item["id"]==request.args["calendarId"]).ok_or("BAD_REQUEST")?;
            let calendar_id=calendar["id"].clone();let month=request.args["month"].clone();let profile=value["profileId"].clone();
            request.args=json!({"server":value["server"],"url":calendar["url"],"user":value["user"],"password":value["password"],"month":month});drop(gate);let reply=engine.request(request)?;
            let _gate=engine.session_gate.lock().map_err(|_|"NO_SESSION")?;owner(engine,epoch)?;
            if cancelled.load(Ordering::SeqCst)||read(app,&scope)?.map(|now|now["profileId"]!=profile).unwrap_or(true){return fault(&id,"CANCELLED");}
            if reply.get("error").is_some(){return Ok(reply);}
            Ok(json!({"id":id,"result":{"calendarId":calendar_id,"month":month,"events":reply["result"]}}))
        }
        _=>Err("BAD_REQUEST".into())
    }
}
#[cfg(test)]mod tests{
    use super::*;
    #[test]fn calendar_urls_are_https_and_metadata_never_contains_credentials(){
        for url in ["http://calendar.invalid/","https://user:secret@calendar.invalid/","https://calendar.invalid/#fragment"]{assert!(address(url).is_err());}
        let data=json!({"profileId":"profile","server":"https://calendar.invalid/private/","user":"fixture","password":"fixture-private-password","selected":"c1","calendars":[{"id":"c1","title":"Личный","url":"https://calendar.invalid/private/calendar"}]});
        let public=dto(&data);assert!(!public.to_string().contains("password"));assert!(!public.to_string().contains("/private/"));assert_eq!(public["calendars"][0]["title"],"Личный");
    }
}

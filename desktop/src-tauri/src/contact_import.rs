use super::{Engine,Request,Reply,Ordering,contact_book,contact_import_parser as parser};
use serde_json::{json,Value};
use tauri_plugin_dialog::DialogExt;
use std::{collections::HashSet,io::Read,path::Path,sync::Arc};
pub struct Preview{token:String,owner:String,epoch:u64,revision:String,rows:Vec<parser::Row>,selectable:HashSet<usize>}
pub fn clear(engine:&Engine){if let Ok(mut state)=engine.contact_import.lock(){*state=None;}}
fn owner(engine:&Engine,epoch:u64)->Result<String,String>{if engine.epoch.load(Ordering::SeqCst)!=epoch{return Err("CANCELLED".into());}engine.owner.lock().map_err(|_|"NO_SESSION")?.clone().ok_or("NO_SESSION".into())}
fn file(path:&Path)->Result<Vec<parser::Row>,String>{
 let meta=std::fs::symlink_metadata(path).map_err(|_|"CONTACTS_IMPORT_FILE")?;if !meta.is_file(){return Err("CONTACTS_IMPORT_FILE".into());}if meta.len()>1024*1024{return Err("CONTACTS_IMPORT_LIMIT".into());}
 let mut bytes=vec![];std::fs::File::open(path).map_err(|_|"CONTACTS_IMPORT_FILE")?.take(1024*1024+1).read_to_end(&mut bytes).map_err(|_|"CONTACTS_IMPORT_FILE")?;let text=parser::decode(&bytes)?;
 match path.extension().and_then(|v|v.to_str()).unwrap_or("").to_ascii_lowercase().as_str(){"csv"=>parser::csv(&text),"vcf"=>parser::vcard(&text),_=>Err("CONTACTS_IMPORT_FORMAT".into())}
}
fn chosen(preview:&Preview,scope:&str,epoch:u64,args:&Value)->Result<Vec<(String,String)>,String>{
  if preview.owner!=scope||preview.epoch!=epoch||args["token"].as_str()!=Some(preview.token.as_str()){return Err("CONTACTS_IMPORT_EXPIRED".into());}
  let selected=args["selected"].as_array().ok_or("BAD_REQUEST")?;if selected.is_empty()||selected.len()>500{return Err("BAD_REQUEST".into());}let mut ids=HashSet::new();let mut rows=vec![];
  for value in selected{let index=value.as_u64().ok_or("BAD_REQUEST")? as usize;if !preview.selectable.contains(&index)||!ids.insert(index){return Err("BAD_REQUEST".into());}let row=&preview.rows[index];rows.push((row.sender.clone(),row.email.clone()));}
  Ok(rows)
}
pub fn execute(request:Request,app:&tauri::AppHandle,engine:&Arc<Engine>)->Reply{
 if request.id.is_empty()||request.id.len()>64||!request.id.bytes().all(|c|c.is_ascii_alphanumeric()||b"._-".contains(&c)){return Err("BAD_REQUEST".into());}
 let epoch=request.args["epoch"].as_u64().ok_or("BAD_REQUEST")?;let gate=engine.session_gate.lock().map_err(|_|"NO_SESSION")?;let scope=owner(engine,epoch)?;
 let path=super::vault::root(app)?.join("contacts").join(&scope).join("book.dpapi");
 if request.method=="localContactsDiscardImport"{
  let mut state=engine.contact_import.lock().map_err(|_|"BUSY")?;if state.as_ref().map(|p|p.token.as_str())==request.args["token"].as_str(){*state=None;}return Ok(json!({"id":request.id,"result":{"discarded":true}}));
 }
 if request.method=="localContactsImport"{
  let mut state=engine.contact_import.lock().map_err(|_|"BUSY")?;let preview=state.as_ref().ok_or("CONTACTS_IMPORT_EXPIRED")?;
  let rows=chosen(preview,&scope,epoch,&request.args)?;
  let book=contact_book::import(&path,&scope,&preview.revision,&rows)?;*state=None;return Ok(json!({"id":request.id,"result":{"book":book,"added":rows.len()}}));
 }
 if request.method!="localContactsPickImport"{return Err("BAD_REQUEST".into());}
 let book=contact_book::snapshot(&path,&scope)?;if book["revision"]!=request.args["revision"]{return Err("CONTACTS_CONFLICT".into());}let revision=book["revision"].as_str().ok_or("CONTACTS_STORE")?.to_string();clear(engine);drop(gate);
 let Some(picked)=app.dialog().file().set_title("Импорт контактов — CSV или vCard").add_filter("Контакты UTF-8 / UTF-16",&["csv","vcf"]).blocking_pick_file()else{return Ok(json!({"id":request.id,"result":{"cancelled":true}}));};
 let file_path=picked.into_path().map_err(|_|"CONTACTS_IMPORT_FILE")?;let rows=file(&file_path)?;
 let _gate=engine.session_gate.lock().map_err(|_|"NO_SESSION")?;owner(engine,epoch)?;let now=contact_book::snapshot(&path,&scope)?;if now["revision"]!=revision{return Err("CONTACTS_CONFLICT".into());}
 let existing:HashSet<String>=now["people"].as_array().ok_or("CONTACTS_STORE")?.iter().filter_map(|row|row["email"].as_str().map(str::to_lowercase)).collect();let capacity=5000-existing.len();let mut seen=HashSet::new();let mut selectable=HashSet::new();let mut output=vec![];
 for (index,row) in rows.iter().enumerate(){let email=row.email.to_lowercase();let issue=if !parser::valid(row){"invalid"}else if existing.contains(&email){"existing"}else if !seen.insert(email){"duplicate"}else{selectable.insert(index);""};output.push(json!({"index":index,"sender":row.sender,"email":row.email,"source":row.source,"issue":issue}));}
 let token=uuid::Uuid::new_v4().to_string();let name=file_path.file_name().and_then(|v|v.to_str()).unwrap_or("Контакты").chars().filter(|c|!c.is_control()).take(160).collect::<String>();
 *engine.contact_import.lock().map_err(|_|"BUSY")?=Some(Preview{token:token.clone(),owner:scope,epoch,revision,rows,selectable});Ok(json!({"id":request.id,"result":{"token":token,"name":name,"capacity":capacity,"rows":output}}))
}

#[cfg(test)]mod tests{
 use super::*;
 #[test]fn selection_cannot_forge_rows_repeat_ids_or_cross_owner_session(){
  let preview=Preview{token:"ticket".into(),owner:"owner".into(),epoch:1,revision:"revision".into(),rows:vec![parser::Row{sender:"One".into(),email:"one@example.invalid".into(),source:1},parser::Row{sender:"Existing".into(),email:"existing@example.invalid".into(),source:2}],selectable:HashSet::from([0])};
  assert_eq!(chosen(&preview,"owner",1,&json!({"token":"ticket","selected":[0]})).unwrap().len(),1);
  for args in [json!({"token":"ticket","selected":[1]}),json!({"token":"ticket","selected":[0,0]}),json!({"token":"other","selected":[0]}),json!({"token":"ticket","selected":[]})]{assert!(chosen(&preview,"owner",1,&args).is_err());}
  let args=json!({"token":"ticket","selected":[0]});assert!(chosen(&preview,"other",1,&args).is_err());assert!(chosen(&preview,"owner",2,&args).is_err());
 }
 #[test]fn only_bounded_regular_user_selected_contact_files_are_read(){
  let folder=tempfile::tempdir().unwrap();let path=folder.path().join("контакты.csv");std::fs::write(&path,"Имя;Почта\nИмя;one@example.invalid").unwrap();assert_eq!(file(&path).unwrap().len(),1);assert!(file(folder.path()).is_err());
  std::fs::write(&path,[0xff]).unwrap();assert!(file(&path).is_err());std::fs::File::create(&path).unwrap().set_len(1024*1024+1).unwrap();assert!(file(&path).is_err());
 }
}

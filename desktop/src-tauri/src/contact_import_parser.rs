use super::contact_book::valid_person;
#[derive(Clone)]
pub struct Row{pub sender:String,pub email:String,pub source:usize}
pub fn decode(bytes:&[u8])->Result<String,String>{
 if bytes.len()>1024*1024{return Err("CONTACTS_IMPORT_LIMIT".into());}
 let text=if bytes.starts_with(&[0xff,0xfe])||bytes.starts_with(&[0xfe,0xff]){
  if (bytes.len()-2)%2!=0{return Err("CONTACTS_IMPORT_ENCODING".into());}let little=bytes[0]==0xff;
  let units=bytes[2..].chunks_exact(2).map(|b|if little{u16::from_le_bytes([b[0],b[1]])}else{u16::from_be_bytes([b[0],b[1]])});
  char::decode_utf16(units).collect::<Result<String,_>>().map_err(|_|"CONTACTS_IMPORT_ENCODING")?
 }else{std::str::from_utf8(bytes.strip_prefix(&[0xef,0xbb,0xbf]).unwrap_or(bytes)).map_err(|_|"CONTACTS_IMPORT_ENCODING")?.to_string()};
 if text.contains('\0'){return Err("CONTACTS_IMPORT_ENCODING".into());}Ok(text)
}
fn format()->String{"CONTACTS_IMPORT_FORMAT".into()}
fn push(rows:&mut Vec<Row>,sender:&str,email:&str,source:usize)->Result<(),String>{
 if rows.len()>=500{return Err("CONTACTS_IMPORT_LIMIT".into());}
 let email=email.trim();let sender=if sender.trim().is_empty(){email}else{sender.trim()};
 rows.push(Row{sender:sender.chars().take(1000).collect(),email:email.chars().take(1000).collect(),source});Ok(())
}
fn csv_records(text:&str,delimiter:char)->Result<Vec<Vec<String>>,String>{
 let mut records=vec![];let mut record=vec![];let mut field=String::new();let mut quoted=false;let mut closed=false;let mut chars=text.chars().peekable();
 while let Some(c)=chars.next(){
  if quoted{if c=='"'{if chars.peek()==Some(&'"'){chars.next();field.push('"')}else{quoted=false;closed=true}}else{field.push(c)}continue}
  if c=='"'{if !field.is_empty()||closed{return Err(format());}quoted=true;continue}
  if c==delimiter{record.push(std::mem::take(&mut field));closed=false;continue}
  if c=='\n'||c=='\r'{if c=='\r'&&chars.peek()==Some(&'\n'){chars.next();}record.push(std::mem::take(&mut field));if record.iter().any(|v|!v.trim().is_empty()){records.push(std::mem::take(&mut record));}else{record.clear();}closed=false;if records.len()>501{return Err("CONTACTS_IMPORT_LIMIT".into());}continue}
  if closed{if c==' '||c=='\t'{continue}return Err(format());}field.push(c);
 }
 if quoted{return Err(format());}record.push(field);if record.iter().any(|v|!v.trim().is_empty()){records.push(record);}if records.len()>501{return Err("CONTACTS_IMPORT_LIMIT".into());}Ok(records)
}
fn header(value:&str)->String{value.chars().filter(|c|c.is_alphanumeric()).flat_map(char::to_lowercase).collect()}
pub fn csv(text:&str)->Result<Vec<Row>,String>{
 let first=text.split(['\r','\n']).next().unwrap_or("");let delimiter=[',',';','\t'].into_iter().max_by_key(|d|first.chars().filter(|c|c==d).count()).unwrap();
 let records=csv_records(text,delimiter)?;let Some(head)=records.first()else{return Err(format())};let names:Vec<_>=head.iter().map(|s|header(s)).collect();
 let emails:Vec<_>=names.iter().enumerate().filter_map(|(n,key)|if ["email","emailaddress","электроннаяпочта","адреспочты","адресэлектроннойпочты","адресэлпочты","почта"].contains(&key.as_str())||key.starts_with("email")&&(key.ends_with("value")||key.ends_with("address")){Some(n)}else{None}).collect();
 if emails.is_empty(){return Err(format());}
 let find=|keys:&[&str]|names.iter().position(|v|keys.contains(&v.as_str()));
 let full=find(&["name","displayname","fullname","фио"]);let given=find(&["firstname","givenname","имя","имяконтакта"]);let family=find(&["lastname","familyname","фамилия"]);
 let get=|record:&Vec<String>,index:Option<usize>|index.and_then(|n|record.get(n)).map(String::as_str).unwrap_or("").trim().to_string();let mut rows=vec![];
 for (index,record) in records.iter().enumerate().skip(1){
  if record.len()!=head.len(){return Err(format());}
  let mut sender=get(record,full);if sender.is_empty(){sender=[get(record,given),get(record,family)].into_iter().filter(|s|!s.is_empty()).collect::<Vec<_>>().join(" ");}
  let mut any=false;for email in &emails{let value=&record[*email];if !value.trim().is_empty(){push(&mut rows,&sender,value,index+1)?;any=true;}}
  if !any{push(&mut rows,&sender,"",index+1)?;}
 }
 if rows.is_empty(){return Err(format());}Ok(rows)
}
fn unescape(value:&str)->Result<String,String>{let mut result=String::new();let mut chars=value.chars();while let Some(c)=chars.next(){if c!='\\'{result.push(c);continue}result.push(match chars.next(){Some('n'|'N')=>'\n',Some(c@('\\'|','|';'))=>c,_=>return Err(format())});}Ok(result)}
fn name_parts(value:&str)->Result<String,String>{let mut parts=vec![];let mut part=String::new();let mut escaped=false;for c in value.chars(){if c==';'&&!escaped{parts.push(unescape(&part)?);part.clear();}else{part.push(c);}escaped=if escaped{false}else{c=='\\'};}parts.push(unescape(&part)?);let get=|i:usize|parts.get(i).map(String::as_str).unwrap_or("");Ok([get(3),get(1),get(2),get(0),get(4)].into_iter().filter(|v|!v.is_empty()).collect::<Vec<_>>().join(" "))}
pub fn vcard(text:&str)->Result<Vec<Row>,String>{
 let mut lines:Vec<String>=vec![];for line in text.split(['\r','\n']){if line.is_empty(){continue}if line.starts_with([' ','\t']){let last=lines.last_mut().ok_or_else(format)?;last.push_str(&line[1..]);}else{lines.push(line.into());}}
 let mut inside=false;let mut version=String::new();let mut full=String::new();let mut name=String::new();let mut emails:Vec<String>=vec![];let mut rows=vec![];let mut source=0;
 for line in lines{
  if line.eq_ignore_ascii_case("BEGIN:VCARD"){if inside{return Err(format());}inside=true;source+=1;if source>500{return Err("CONTACTS_IMPORT_LIMIT".into());}version.clear();full.clear();name.clear();emails.clear();continue}
  if line.eq_ignore_ascii_case("END:VCARD"){if !inside||!matches!(version.as_str(),"3.0"|"4.0"){return Err(format());}let sender=if full.is_empty(){name.as_str()}else{full.as_str()};if emails.is_empty(){push(&mut rows,sender,"",source)?;}else{for email in &emails{push(&mut rows,sender,email,source)?;}}inside=false;continue}
  if !inside{return Err(format());}let (key,value)=line.split_once(':').ok_or_else(format)?;let property=key.split(';').next().unwrap_or("").rsplit('.').next().unwrap_or("").to_ascii_uppercase();
  match property.as_str(){"VERSION"=>{if !version.is_empty(){return Err(format());}version=value.into();},"FN"|"N"|"EMAIL"=>{
   let upper=key.to_ascii_uppercase();if upper.contains("ENCODING=")||upper.split(';').any(|p|p.starts_with("CHARSET=")&&!matches!(p,"CHARSET=UTF-8"|"CHARSET=UTF8")){return Err(format());}
   match property.as_str(){"FN"=>full=unescape(value)?,"N"=>name=name_parts(value)?,_=>emails.push(unescape(value)?)}
  },_=>{}}
 }
 if inside||source==0{return Err(format());}Ok(rows)
}
pub fn valid(row:&Row)->bool{valid_person(&row.sender,&row.email)&&!row.email.contains(':')}
#[cfg(test)]mod tests{
 use super::*;
 #[test]fn csv_quotes_gmail_outlook_multiple_emails_and_strict_records(){
  let rows=csv("Name,E-mail 1 - Value,E-mail 2 - Value\r\n\"Иванов, Иван\",one@example.invalid,two@example.invalid\r\n").unwrap();assert_eq!(rows.len(),2);assert_eq!(rows[0].sender,"Иванов, Иван");assert!(valid(&rows[0]));
  let rows=csv("First Name;Last Name;E-mail Address\nИван;Иванов;x@example.invalid").unwrap();assert_eq!(rows[0].sender,"Иван Иванов");assert_eq!(csv("Имя;Фамилия;Почта\nИван;Иванов;x@example.invalid").unwrap()[0].sender,"Иван Иванов");
  assert!(csv("Имя;Почта\nБез адреса;").unwrap().iter().all(|row|!valid(row)));assert!(csv("name,email\n\"unterminated,x@x.invalid").is_err());assert!(csv("name,email\na,b,c").is_err());
  let text=format!("Name,Email\n{}",(0..501).map(|n|format!("N,{n}@x.invalid\n")).collect::<String>());assert!(csv(&text).is_err());
 }
 #[test]fn vcard_folded_names_escapes_multiple_emails_and_unsupported_encoding(){
  let rows=vcard("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Иванов\\, И\r\n ван\r\nEMAIL;TYPE=INTERNET:one@example.invalid\r\nitem1.EMAIL:two@example.invalid\r\nEND:VCARD\r\n").unwrap();assert_eq!(rows.len(),2);assert_eq!(rows[0].sender,"Иванов, Иван");
  let rows=vcard("BEGIN:VCARD\nVERSION:4.0\nN:Иванов;Иван;Иванович;;\nEMAIL:x@example.invalid\nEND:VCARD").unwrap();assert_eq!(rows[0].sender,"Иван Иванович Иванов");
  for text in ["BEGIN:VCARD\nVERSION:2.1\nEND:VCARD","BEGIN:VCARD\nVERSION:3.0\nFN;ENCODING=QUOTED-PRINTABLE:=AB\nEND:VCARD","BEGIN:VCARD\nVERSION:3.0"]{assert!(vcard(text).is_err());}
 }
 #[test]fn bounded_strict_utf8_utf16_bom(){
  assert_eq!(decode(&[0xef,0xbb,0xbf,b'a']).unwrap(),"a");let mut bytes=vec![0xff,0xfe];for unit in "Имя".encode_utf16(){bytes.extend(unit.to_le_bytes());}assert_eq!(decode(&bytes).unwrap(),"Имя");
  assert!(decode(&[0xff]).is_err());assert!(decode(&[0xff,0xfe,0,0xd8]).is_err());assert!(decode(b"a\0b").is_err());assert!(decode(&vec![b'a';1024*1024+1]).is_err());
 }
}

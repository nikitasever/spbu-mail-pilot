package ru.local.spbumailpilot;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.json.*;

final class UniversityDirectory {
    interface Transport {String post(String path,String body)throws Exception;}
    private final Transport transport;
    private String session="",owner="";
    private long expires;
    UniversityDirectory(){this(UniversityDirectory::post);}
    UniversityDirectory(Transport transport){this.transport=transport;}
    static boolean supported(SavedAccounts.Login login){return login!=null&&"mail.spbu.ru".equalsIgnoreCase(login.host);}
    static String query(String value){
        if(value==null)throw new IllegalArgumentException();
        String result=value.trim();
        if(result.length()<2||result.length()>120||result.matches("(?s).*\\p{Cntrl}.*"))throw new IllegalArgumentException();
        return result;
    }
    static String xml(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    static String escapedFilter(String value){
        StringBuilder result=new StringBuilder();
        for(char character:value.toCharArray())switch(character){case '*':result.append("\\2a");break;case '(':result.append("\\28");break;case ')':result.append("\\29");break;case '\\':result.append("\\5c");break;default:result.append(character);}
        return result.toString();
    }
    static String filter(String value){String text=escapedFilter(value);return "(|(cn=*"+text+"*)(displayName=*"+text+"*)(mail=*"+text+"*)(uid=*"+text+"*))";}
    static Document document(String xml)throws Exception{
        if(xml.length()>512*1024||java.util.regex.Pattern.compile("(?is)<\\s*!\\s*(DOCTYPE|ENTITY)\\b").matcher(xml).find())throw new IOException("Некорректный ответ справочника.");
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setExpandEntityReferences(false);
        DocumentBuilder builder=factory.newDocumentBuilder();builder.setEntityResolver((publicId,systemId)->{throw new org.xml.sax.SAXException("External XML refused");});
        Document result=builder.parse(new org.xml.sax.InputSource(new StringReader(xml)));
        if(!"XIMSS".equals(result.getDocumentElement().getTagName()))throw new IOException("Справочник СПбГУ недоступен.");
        return result;
    }
    static void completed(Document document,String id)throws IOException{
        NodeList responses=document.getElementsByTagName("response");
        for(int index=0;index<responses.getLength();index++){
            Element response=(Element)responses.item(index);
            if(id.equals(response.getAttribute("id"))){if(response.hasAttribute("errorNum")||response.hasAttribute("errorText"))throw new IOException("Сервер не разрешил поиск в справочнике СПбГУ.");return;}
        }
        throw new IOException("Неполный ответ справочника СПбГУ.");
    }
    static List<String> fields(Element record,String name){
        List<String> result=new ArrayList<>();
        for(Node node=record.getFirstChild();node!=null;node=node.getNextSibling())if(node instanceof Element){
            Element field=(Element)node;
            if("subKey".equals(field.getTagName())&&name.equalsIgnoreCase(field.getAttribute("key"))){
                NodeList values=field.getElementsByTagName("subValue");
                if(values.getLength()==0)result.add(field.getTextContent().trim());else for(int index=0;index<values.getLength();index++)result.add(values.item(index).getTextContent().trim());
            }
        }
        return result;
    }
    static String field(Element record,String name){List<String> values=fields(record,name);return values.isEmpty()?"":values.get(0);}
    static JSONArray people(Document document)throws Exception{return people(document,20);}
    static JSONArray people(Document document,int limit)throws Exception{
        completed(document,"directory");JSONArray result=new JSONArray();Set<String> seen=new HashSet<>();NodeList records=document.getElementsByTagName("directoryData");
        for(int index=0;index<records.getLength()&&result.length()<limit;index++){
            Element record=(Element)records.item(index);if(!"directory".equals(record.getAttribute("id")))continue;
            String name=field(record,"displayName");if(name.isEmpty())name=field(record,"cn");
            List<String> addresses=fields(record,"mail");
            for(int address=0;address<addresses.size()&&result.length()<limit;address++){
                String email=addresses.get(address);
                if(email.length()>254||!email.matches("[^\\s<>@]+@[^\\s<>@]+")||!seen.add(email.toLowerCase(Locale.ROOT)))continue;
                result.put(new JSONObject().put("email",email).put("sender",name.isEmpty()?email:name.substring(0,Math.min(name.length(),180))));
            }
        }
        return result;
    }
    private void connect(SavedAccounts.Login login)throws Exception{
        if(!supported(login))throw new IllegalArgumentException();
        if(!owner.equals(login.identity())||System.currentTimeMillis()>expires){close();owner=login.identity();}
            if(session.isEmpty()){
                String authentication=Base64.getEncoder().encodeToString(("\0"+login.user+"\0"+login.password).getBytes(StandardCharsets.UTF_8));
                Document response=document(transport.post("/ximsslogin/","<XIMSS><login id=\"login\" method=\"PLAIN\" version=\"6.0\" authData=\""+authentication+"\"/></XIMSS>"));completed(response,"login");
                NodeList sessions=response.getElementsByTagName("session");if(sessions.getLength()!=1)throw new IOException("Справочник СПбГУ недоступен.");
                Element entry=(Element)sessions.item(0);if(entry.getElementsByTagName("x2auth").getLength()>0)throw new IOException("Для справочника требуется дополнительный вход.");
                session=entry.getAttribute("urlID");if(!session.matches("[A-Za-z0-9_-]{1,160}")){session="";throw new IOException("Некорректный ответ справочника.");}
                expires=System.currentTimeMillis()+2*60*1000L;
            }

    }
    private Document command(String xml)throws Exception{return document(transport.post("/Session/"+session+"/sync","<XIMSS>"+xml+"</XIMSS>"));}
    synchronized JSONArray search(SavedAccounts.Login login,String value)throws Exception{
        String text=query(value);try{connect(login);return people(command(directoryCommand("",filter(text),20,null)));}catch(Exception failure){close();throw failure;}
    }
    static String directoryCommand(String base,String filter,int limit,String cookie){
        return "<listDirectory id=\"directory\" baseDN=\""+xml(base)+"\" scope=\"sub\" limit=\""+limit+"\" sortField=\"cn\""+(cookie==null?"":" cookie=\""+xml(cookie)+"\"")+(filter.isEmpty()?"":" filter=\""+xml(filter)+"\"")+"><field>mail</field><field>cn</field><field>displayName</field><field>ou</field></listDirectory>";
    }
    private Document preferences()throws Exception{Document result=command("<prefsRead id=\"books\"><name>DirectoryBooks</name><name>ContactsBox</name></prefsRead>");completed(result,"books");return result;}
    static String preference(Document doc,String name){NodeList prefs=doc.getElementsByTagName("prefs");if(prefs.getLength()!=1)return "";Element root=(Element)prefs.item(0);for(Node n=root.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element&&name.equals(((Element)n).getTagName()))return n.getTextContent().trim();return "";}
    static String companyBase(Document doc){NodeList books=doc.getElementsByTagName("DirectoryBooks");if(books.getLength()==1){String base=field((Element)books.item(0),"$domain$");if(!base.isEmpty())return base;}return "$";}
    static String pagingCookie(Document doc){String result="";NodeList records=doc.getElementsByTagName("directoryData");for(int i=0;i<records.getLength();i++){Element r=(Element)records.item(i);if("directory".equals(r.getAttribute("id"))&&r.hasAttribute("cookie"))result=r.getAttribute("cookie");}return result;}
    synchronized JSONObject page(SavedAccounts.Login login,String book,String query,String cookie)throws Exception{
        if(!Arrays.asList("personal","team","company").contains(book)||query==null||query.length()>120||query.matches("(?s).*\\p{Cntrl}.*")||cookie==null||cookie.length()>4096||cookie.matches("(?s).*\\p{Cntrl}.*"))throw new IllegalArgumentException();
        try{connect(login);Document prefs=preferences();
            if(book.equals("personal"))return personal(preference(prefs,"ContactsBox"),query,cookie);
            String base=companyBase(prefs),filter=query.trim().isEmpty()?"":filter(query.trim());
            if(book.equals("team")){
                String user=login.user.split("@",2)[0];Document own=command(directoryCommand(base,"(uid="+escapedFilter(user)+")",1,null));completed(own,"directory");NodeList records=own.getElementsByTagName("directoryData");String department=records.getLength()==1?field((Element)records.item(0),"ou"):"";
                if(department.isEmpty())throw new IOException("Сервер не указал подразделение для команды.");
                filter="(&"+filter+"(ou="+escapedFilter(department)+"))";
            }
            Document result=command(directoryCommand(base,filter,50,cookie));JSONArray people=people(result,50);String next=pagingCookie(result);
            if(!cookie.isEmpty()&&cookie.equals(next))throw new IOException("Повтор страницы справочника.");
            return new JSONObject().put("people",people).put("cookie",next).put("hasMore",!next.isEmpty());
        }catch(Exception failure){close();throw failure;}
    }
    private JSONObject personal(String mailbox,String query,String cookie)throws Exception{
        if(mailbox.isEmpty()||mailbox.length()>512)throw new IOException("Сервер не указал личную адресную книгу.");
        int offset=cookie.isEmpty()?0:Integer.parseInt(cookie);if(offset<0||offset>1000000)throw new IllegalArgumentException();
        Document opened=command("<folderOpen id=\"openContacts\" folder=\"pilotContacts\" mailbox=\""+xml(mailbox)+"\""+(query.trim().isEmpty()?"":" filter=\""+xml(query.trim())+"\"")+" sortField=\"Subject\" sortOrder=\"asc\"><field>Subject</field><field>E-To</field></folderOpen>");completed(opened,"openContacts");
        try{
            int total=-1;NodeList reports=opened.getElementsByTagName("folderReport");for(int i=0;i<reports.getLength();i++){Element r=(Element)reports.item(i);if("pilotContacts".equals(r.getAttribute("folder"))&&"init".equals(r.getAttribute("mode")))total=Integer.parseInt(r.getAttribute("messages"));}if(total<0)throw new IOException("Неполный ответ адресной книги.");
            JSONArray people=new JSONArray();int end=Math.min(total,offset+50);
            if(end>offset){Document data=command("<folderBrowse id=\"browseContacts\" folder=\"pilotContacts\"><index from=\""+offset+"\" till=\""+(end-1)+"\"/></folderBrowse>");completed(data,"browseContacts");reports=data.getElementsByTagName("folderReport");
                for(int i=0;i<reports.getLength();i++){Element r=(Element)reports.item(i);if(!"browseContacts".equals(r.getAttribute("id"))||!"pilotContacts".equals(r.getAttribute("folder")))continue;NodeList names=r.getElementsByTagName("Subject"),addresses=r.getElementsByTagName("E-To");String name=names.getLength()>0?names.item(0).getTextContent():"";
                    for(int j=0;j<addresses.getLength();j++){Element address=(Element)addresses.item(j);NodeList items=address.getElementsByTagName("To");if(items.getLength()>0){for(int k=0;k<items.getLength();k++)addPerson(people,name,items.item(k).getTextContent());}else addPerson(people,name,address.getTextContent());}
                }
            }
            return new JSONObject().put("people",people).put("cookie",end<total?String.valueOf(end):"").put("hasMore",end<total).put("total",total);
        }finally{try{command("<folderClose id=\"closeContacts\" folder=\"pilotContacts\"/>");}catch(Exception ignored){}}
    }
    static void addPerson(JSONArray people,String name,String raw)throws Exception{
        try{for(javax.mail.internet.InternetAddress address:javax.mail.internet.InternetAddress.parse(raw,false)){String email=address.getAddress();if(email!=null&&email.length()<=254&&email.matches("[^\\s<>@]+@[^\\s<>@]+"))people.put(new JSONObject().put("email",email).put("sender",name.isEmpty()?address.getPersonal()==null?email:address.getPersonal():name));}}catch(javax.mail.internet.AddressException ignored){}
    }
    synchronized void close(){String previous=session;session="";owner="";expires=0;if(!previous.isEmpty())try{transport.post("/Session/"+previous+"/sync","<XIMSS><bye id=\"bye\"/></XIMSS>");}catch(Exception ignored){}}
    private static String post(String path,String body)throws Exception{
        if(!path.equals("/ximsslogin/")&&!path.matches("/Session/[A-Za-z0-9_-]{1,160}/sync"))throw new IllegalArgumentException();
        HttpURLConnection connection=(HttpURLConnection)new URL("https://mail.spbu.ru"+path).openConnection();
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(8000);connection.setReadTimeout(8000);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","text/xml; charset=utf-8");
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);
        try{try(OutputStream output=connection.getOutputStream()){output.write(bytes);}if(connection.getResponseCode()!=200)throw new IOException("Справочник СПбГУ недоступен.");
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1){if(output.size()+count>512*1024)throw new IOException("Ответ справочника слишком большой.");output.write(buffer,0,count);}return output.toString("UTF-8");}
        }finally{connection.disconnect();}
    }
}

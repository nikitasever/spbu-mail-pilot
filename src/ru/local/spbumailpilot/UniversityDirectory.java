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
    static String filter(String value){
        StringBuilder result=new StringBuilder();
        for(char character:value.toCharArray())switch(character){case '*':result.append("\\2a");break;case '(':result.append("\\28");break;case ')':result.append("\\29");break;case '\\':result.append("\\5c");break;default:result.append(character);}
        String text=result.toString();return "(|(cn=*"+text+"*)(displayName=*"+text+"*)(mail=*"+text+"*)(uid=*"+text+"*))";
    }
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
    static JSONArray people(Document document)throws Exception{
        completed(document,"directory");JSONArray result=new JSONArray();Set<String> seen=new HashSet<>();NodeList records=document.getElementsByTagName("directoryData");
        for(int index=0;index<records.getLength()&&result.length()<20;index++){
            Element record=(Element)records.item(index);if(!"directory".equals(record.getAttribute("id")))continue;
            String name=field(record,"displayName");if(name.isEmpty())name=field(record,"cn");
            List<String> addresses=fields(record,"mail");
            for(int address=0;address<addresses.size()&&result.length()<20;address++){
                String email=addresses.get(address);
                if(email.length()>254||!email.matches("[^\\s<>@]+@[^\\s<>@]+")||!seen.add(email.toLowerCase(Locale.ROOT)))continue;
                result.put(new JSONObject().put("email",email).put("sender",name.isEmpty()?email:name.substring(0,Math.min(name.length(),180))));
            }
        }
        return result;
    }
    synchronized JSONArray search(SavedAccounts.Login login,String value)throws Exception{
        if(!supported(login))throw new IllegalArgumentException();String text=query(value);
        if(!owner.equals(login.identity())||System.currentTimeMillis()>expires){close();owner=login.identity();}
        try{
            if(session.isEmpty()){
                String authentication=Base64.getEncoder().encodeToString(("\0"+login.user+"\0"+login.password).getBytes(StandardCharsets.UTF_8));
                Document response=document(transport.post("/ximsslogin/","<XIMSS><login id=\"login\" method=\"PLAIN\" version=\"6.0\" authData=\""+authentication+"\"/></XIMSS>"));completed(response,"login");
                NodeList sessions=response.getElementsByTagName("session");if(sessions.getLength()!=1)throw new IOException("Справочник СПбГУ недоступен.");
                Element entry=(Element)sessions.item(0);if(entry.getElementsByTagName("x2auth").getLength()>0)throw new IOException("Для справочника требуется дополнительный вход.");
                session=entry.getAttribute("urlID");if(!session.matches("[A-Za-z0-9_-]{1,160}")){session="";throw new IOException("Некорректный ответ справочника.");}
                expires=System.currentTimeMillis()+2*60*1000L;
            }
            String command="<XIMSS><listDirectory id=\"directory\" baseDN=\"\" scope=\"sub\" limit=\"20\" filter=\""+xml(filter(text))+"\"><field>mail</field><field>cn</field><field>displayName</field></listDirectory></XIMSS>";
            return people(document(transport.post("/Session/"+session+"/sync",command)));
        }catch(Exception failure){close();throw failure;}
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

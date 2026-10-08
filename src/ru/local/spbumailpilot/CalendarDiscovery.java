package ru.local.spbumailpilot;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import okhttp3.*;

/** Read-only RFC 5397/4791 discovery. No credentials leave the selected origin. */
final class CalendarDiscovery {
    static final String DAV="DAV:",CAL="urn:ietf:params:xml:ns:caldav";
    static final String PROPS="<?xml version='1.0' encoding='UTF-8'?><d:propfind xmlns:d='DAV:' xmlns:c='urn:ietf:params:xml:ns:caldav'><d:prop><d:current-user-principal/><c:calendar-home-set/><d:resourcetype/><d:displayname/><c:supported-calendar-component-set/></d:prop></d:propfind>";
    static final class Calendar {final String title,url;Calendar(String title,String url){this.title=title;this.url=url;}}
    static final class Reply {final URI url;final Element root;Reply(URI u,Element r){url=u;root=r;}}
    static List<Calendar> discover(String server,String user,String password)throws Exception{
        return discover(server,user,password,new OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(40,TimeUnit.SECONDS).callTimeout(60,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build());
    }
    static List<Calendar> discover(String server,String user,String password,OkHttpClient client)throws Exception{
        if(user.isEmpty()||user.length()>320||user.contains(":")||user.contains("\r")||user.contains("\n")||password.isEmpty()||password.length()>2048)throw new IllegalArgumentException("Укажите адрес ящика и пароль внешнего приложения.");
        URI origin=CalDav.address(server.contains("://")?server:"https://"+server);
        try{
            Reply first;
            if(origin.getPath().isEmpty()||origin.getPath().equals("/")){
                first=propfind(origin,origin.resolve("/.well-known/caldav"),user,password,"0",client,true);
                if(first==null)first=propfind(origin,origin.resolve("/"),user,password,"0",client,false);
            }else first=propfind(origin,origin,user,password,"0",client,false);
            List<URI> homes=links(first,CAL,"calendar-home-set",origin);
            if(homes.isEmpty()){
                List<URI> principals=links(first,DAV,"current-user-principal",origin);
                if(principals.isEmpty())throw new CalDav.Failure("Сервер не предоставил адрес аккаунта CalDAV. Можно подключить календарь по полной ссылке вручную.");
                homes=links(propfind(origin,principals.get(0),user,password,"0",client,false),CAL,"calendar-home-set",origin);
            }
            if(homes.isEmpty()||homes.size()>10)throw new CalDav.Failure("Сервер не предоставил список календарей CalDAV.");
            LinkedHashMap<String,Calendar> calendars=new LinkedHashMap<>();
            for(URI home:homes){
                Reply listing=propfind(origin,home,user,password,"1",client,false);
                NodeList responses=listing.root.getElementsByTagNameNS(DAV,"response");
                for(int i=0;i<responses.getLength();i++){
                    Element response=(Element)responses.item(i);List<Element> props=successful(response);
                    boolean calendar=false,event=false,componentSet=false;String title="";
                    for(Element prop:props){
                        NodeList types=prop.getElementsByTagNameNS(DAV,"resourcetype");
                        for(int j=0;j<types.getLength();j++)if(((Element)types.item(j)).getElementsByTagNameNS(CAL,"calendar").getLength()>0)calendar=true;
                        NodeList sets=prop.getElementsByTagNameNS(CAL,"supported-calendar-component-set");
                        if(sets.getLength()>0)componentSet=true;
                        for(int j=0;j<sets.getLength();j++){NodeList components=((Element)sets.item(j)).getElementsByTagNameNS(CAL,"comp");for(int k=0;k<components.getLength();k++)if("VEVENT".equalsIgnoreCase(((Element)components.item(k)).getAttribute("name")))event=true;}
                        NodeList names=prop.getElementsByTagNameNS(DAV,"displayname");if(names.getLength()>0)title=names.item(0).getTextContent().trim();
                    }
                    if(!calendar||(componentSet&&!event))continue;
                    Element href=child(response,DAV,"href");if(href==null)continue;
                    URI url=safeLink(origin,listing.url,href.getTextContent());
                    if(title.isEmpty())title="Календарь "+(calendars.size()+1);
                    if(title.length()>120)title=title.substring(0,120);
                    calendars.put(url.toString(),new Calendar(title,url.toString()));
                    if(calendars.size()>100)throw new CalDav.Failure("Сервер вернул слишком много календарей.");
                }
            }
            if(calendars.isEmpty())throw new CalDav.Failure("Доступных календарей с событиями не найдено. Общие календари могут не предоставляться сервером через CalDAV.");
            return new ArrayList<>(calendars.values());
        }finally{client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}
    }
    static URI safeLink(URI origin,URI base,String href)throws Exception{
        URI next=CalDav.address(base.resolve(href.trim()).toString());
        if(!CalDav.sameOrigin(origin,next))throw new CalDav.Failure("CalDAV указал другой сервер. Введите его адрес отдельно; пароль на другой сервер не передаётся.");
        return next;
    }
    static Element child(Element parent,String ns,String name){for(Node n=parent.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element&&ns.equals(n.getNamespaceURI())&&name.equals(n.getLocalName()))return (Element)n;return null;}
    static List<Element> successful(Element response){
        List<Element> result=new ArrayList<>();
        for(Node n=response.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element&&DAV.equals(n.getNamespaceURI())&&"propstat".equals(n.getLocalName())){
            Element part=(Element)n,status=child(part,DAV,"status"),prop=child(part,DAV,"prop");
            if(status!=null&&prop!=null&&status.getTextContent().matches(".* 200(?: .*)?"))result.add(prop);
        }
        return result;
    }
    static List<URI> links(Reply reply,String ns,String name,URI origin)throws Exception{
        List<URI> urls=new ArrayList<>();NodeList responses=reply.root.getElementsByTagNameNS(DAV,"response");
        for(int i=0;i<responses.getLength();i++)for(Element prop:successful((Element)responses.item(i))){
            NodeList properties=prop.getElementsByTagNameNS(ns,name);
            for(int j=0;j<properties.getLength();j++){NodeList hrefs=((Element)properties.item(j)).getElementsByTagNameNS(DAV,"href");for(int k=0;k<hrefs.getLength();k++){URI url=safeLink(origin,reply.url,hrefs.item(k).getTextContent());if(!urls.contains(url))urls.add(url);if(urls.size()>10)throw new CalDav.Failure("Слишком много адресов CalDAV.");}}
        }
        return urls;
    }
    static Reply propfind(URI origin,URI url,String user,String password,String depth,OkHttpClient client,boolean optional)throws Exception{
        for(int redirects=0;redirects<4;redirects++){
            Request request=new Request.Builder().url(url.toString()).header("Authorization",Credentials.basic(user,password,java.nio.charset.StandardCharsets.UTF_8)).header("Depth",depth).method("PROPFIND",RequestBody.create(MediaType.parse("application/xml; charset=utf-8"),PROPS)).build();
            try(Response response=client.newCall(request).execute()){
                int code=response.code();
                if(code==301||code==302||code==307||code==308){String target=response.header("Location");if(target==null)throw new CalDav.Failure("Некорректное перенаправление CalDAV.");url=safeLink(origin,url,target);continue;}
                if(code==401||code==403)throw new CalDav.Failure("Сервер отклонил вход. Проверьте полный адрес ящика и пароль внешнего приложения с доступом к Календарю.");
                if(optional&&(code==404||code==405))return null;
                if(code!=207||response.body()==null)throw new CalDav.Failure("Поиск календарей вернул HTTP "+code+". Проверьте адрес сервера CalDAV.");
                try(InputStream in=response.body().byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){if(out.size()+count>CalDav.LIMIT)throw new CalDav.Failure("Слишком большой ответ CalDAV.");out.write(buffer,0,count);}
                    String xml=out.toString("UTF-8");if(java.util.regex.Pattern.compile("(?is)<\\s*!\\s*(DOCTYPE|ENTITY)\\b").matcher(xml).find())throw new CalDav.Failure("Небезопасный ответ CalDAV.");
                    DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setExpandEntityReferences(false);DocumentBuilder parser=factory.newDocumentBuilder();parser.setEntityResolver((a,b)->{throw new org.xml.sax.SAXException("External XML refused");});
                    Element root=parser.parse(new org.xml.sax.InputSource(new StringReader(xml))).getDocumentElement();if(!DAV.equals(root.getNamespaceURI())||!"multistatus".equals(root.getLocalName()))throw new CalDav.Failure("Сервер не предоставил ответ CalDAV.");return new Reply(url,root);
                }
            }
        }
        throw new CalDav.Failure("Слишком много перенаправлений CalDAV.");
    }
}

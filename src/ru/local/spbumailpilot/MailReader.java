package ru.local.spbumailpilot;

import android.text.Html;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Properties;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeUtility;
import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.iap.Response;
import java.util.ArrayList;

/** All folders are opened read-only. BODY.PEEK avoids setting the Seen flag. */
final class MailReader implements AutoCloseable {
    private Store store;
    private Folder inbox;
    private long validity;
    private String folderName = "INBOX";
    private String pagingFolder = "";
    private long pagingValidity;
    private long generation;
    private UidPages pages,filePages;
    private String pagingQuery="",fileFolder;private long fileValidity,fileGeneration;
    void connect(String host, int port, String user, String password) throws Exception {
        Properties props = new Properties();
        props.setProperty("mail.imaps.ssl.checkserveridentity", "true");
        props.setProperty("mail.imaps.connectiontimeout", "15000");
        props.setProperty("mail.imaps.timeout", "20000");
        props.setProperty("mail.imaps.writetimeout", "20000");
        props.setProperty("mail.imaps.peek", "true");
        props.setProperty("mail.imaps.partialfetch", "true");
        props.setProperty("mail.imaps.fetchsize", "65536");
        props.setProperty("mail.imaps.connectionpoolsize", "1");
        MailcapCommandMap handlers = (MailcapCommandMap) CommandMap.getDefaultCommandMap();
        handlers.addMailcap("text/plain;; x-java-content-handler=com.sun.mail.handlers.text_plain");
        handlers.addMailcap("text/html;; x-java-content-handler=com.sun.mail.handlers.text_html");
        handlers.addMailcap("multipart/*;; x-java-content-handler=com.sun.mail.handlers.multipart_mixed");
        handlers.addMailcap("message/rfc822;; x-java-content-handler=com.sun.mail.handlers.message_rfc822");
        Session session = Session.getInstance(props);
        session.setDebug(false);
        session.setProvider(new Provider(Provider.Type.STORE, "imaps", "com.sun.mail.imap.IMAPSSLStore", "Eclipse", "1.6.7"));
        store = session.getStore("imaps");
        try { store.connect(host, port, user, password); openFolder("INBOX"); }
        catch (Exception e) { close(); throw e; }
    }
    private void openFolder(String name) throws Exception {
        if (inbox != null && inbox.isOpen() && folderName.equals(name)) return;
        if (inbox != null && inbox.isOpen()) inbox.close(false);
        inbox = store.getFolder(name);
        inbox.open(Folder.READ_ONLY);
        folderName = name;
        validity = ((UIDFolder) inbox).getUIDValidity();
    }
    JSONArray folders() throws Exception {
        JSONArray result = new JSONArray();
        ArrayList<String> names = new ArrayList<>();
        for (Folder folder : store.getDefaultFolder().list("*")) {
            if ((folder.getType() & Folder.HOLDS_MESSAGES) != 0) names.add(folder.getFullName());
        }
        names.sort((a,b) -> a.equals(b) ? 0 : a.equalsIgnoreCase("INBOX") ? -1 : b.equalsIgnoreCase("INBOX") ? 1 : a.compareToIgnoreCase(b));
        for (String name : names) result.put(name);
        return result;
    }
    private long[] uidSnapshot() throws Exception {return uidSnapshot("");}
    private long[] uidSnapshot(String query) throws Exception {
        final com.sun.mail.iap.Argument search=query.isEmpty()?null:SearchQuery.arguments(query);
        return (long[]) ((IMAPFolder) inbox).doCommand(protocol -> {
            Response[] responses = query.isEmpty()?protocol.command("UID SEARCH ALL", null):protocol.command("UID SEARCH",search);
            protocol.handleResult(responses[responses.length - 1]);
            long[] values = UidSearch.parse(responses);
            protocol.notifyResponseHandlers(responses);
            return values;
        });
    }
    JSONObject page(String name,int offset,String snapshot)throws Exception{return page(name,offset,snapshot,"");}
    JSONObject page(String name, int offset, String snapshot,String query) throws Exception {
        openFolder(name);
        if (offset == 0) {
            pages = new UidPages(uidSnapshot(query));pagingQuery=query;
            pagingFolder = name; pagingValidity = validity; generation++;
        } else if (pages == null || !query.equals(pagingQuery) || !name.equals(pagingFolder) || pagingValidity != validity || !Long.toString(generation).equals(snapshot)) {
            throw new MessagingException("Page snapshot changed");
        }
        long[] uids = pages.page(offset);
        JSONArray result = new JSONArray();
        Message[] found = uids.length == 0 ? new Message[0] : ((UIDFolder) inbox).getMessagesByUID(uids);
        ArrayList<Message> present = new ArrayList<>();
        for (Message m : found) if (m != null && !m.isExpunged()) present.add(m);
        Message[] messages = present.toArray(new Message[0]);
        FetchProfile profile = new FetchProfile();
        profile.add(FetchProfile.Item.ENVELOPE);
        profile.add(FetchProfile.Item.FLAGS);
        profile.add(UIDFolder.FetchProfileItem.UID);
        if (messages.length > 0) inbox.fetch(messages, profile);
        for (int i = messages.length - 1; i >= 0; --i) {
            Message m = messages[i];
            if (m.isExpunged()) continue;
            result.put(header(m));
        }
        int next = pages.next(offset);
        return new JSONObject().put("messages", result).put("total", pages.size()).put("nextOffset", next)
                .put("hasMore", next < pages.size()).put("snapshot", Long.toString(generation)).put("folder", name);
    }
    JSONObject files(String name,int offset,String snapshot,String query)throws Exception{
        openFolder(name);
        if(offset==0){filePages=new UidPages(uidSnapshot());fileFolder=name;fileValidity=validity;fileGeneration++;}
        else if(filePages==null||!name.equals(fileFolder)||fileValidity!=validity||!Long.toString(fileGeneration).equals(snapshot))throw new MessagingException("File snapshot changed");
        long[] uids=filePages.page(offset);Message[] found=uids.length==0?new Message[0]:((UIDFolder)inbox).getMessagesByUID(uids);
        FetchProfile profile=new FetchProfile();profile.add(FetchProfile.Item.ENVELOPE);profile.add(FetchProfile.Item.CONTENT_INFO);profile.add(UIDFolder.FetchProfileItem.UID);if(found.length>0)inbox.fetch(found,profile);
        JSONArray result=new JSONArray();for(int i=found.length-1;i>=0;i--){Message message=found[i];if(message==null||message.isExpunged())continue;
            JSONArray entries=AttachmentIndex.list(message,query);JSONObject info=header(message);
            for(int j=0;j<entries.length();j++){JSONObject entry=entries.getJSONObject(j);entry.put("message",info);result.put(entry);}
        }
        int next=filePages.next(offset);return new JSONObject().put("files",result).put("total",filePages.size()).put("nextOffset",next).put("hasMore",next<filePages.size()).put("snapshot",Long.toString(fileGeneration));
    }
    JSONObject exportMessage(String name,String uid,String expectedValidity,java.io.File directory)throws Exception{
        openFolder(name);if(!Long.toString(validity).equals(expectedValidity))throw new MessagingException("UID validity changed");Message message=((UIDFolder)inbox).getMessageByUID(Long.parseLong(uid));if(message==null||message.isExpunged())throw new MessagingException("Missing message");
        if(message instanceof com.sun.mail.imap.IMAPMessage)((com.sun.mail.imap.IMAPMessage)message).setPeek(true);
        directory.mkdirs();java.io.File file=java.io.File.createTempFile("message-",".eml",directory);
        try(java.io.OutputStream raw=new java.io.FileOutputStream(file);java.io.OutputStream bounded=new java.io.FilterOutputStream(raw){long size;public void write(int b)throws java.io.IOException{if(++size>AttachmentFiles.MAX_BYTES)throw new java.io.IOException("Message too large");out.write(b);}public void write(byte[] b,int off,int len)throws java.io.IOException{if((size+=len)>AttachmentFiles.MAX_BYTES)throw new java.io.IOException("Message too large");out.write(b,off,len);}}){message.writeTo(bounded);}catch(Exception e){file.delete();throw e;}
        return new JSONObject().put("file",file.getAbsolutePath()).put("name",AttachmentFiles.safeName(message.getSubject())+".eml").put("mime","message/rfc822");
    }
    private JSONObject header(Message m) throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("uid", Long.toString(((UIDFolder) inbox).getUID(m)));
            obj.put("validity", Long.toString(validity));
            obj.put("folder", folderName);
            obj.put("subject", m.getSubject() == null ? "Без темы" : m.getSubject());
            Address[] from = m.getFrom();
            String name = "Неизвестный отправитель", email = "";
            if (from != null && from.length > 0) {
                if (from[0] instanceof InternetAddress) {
                    InternetAddress address = (InternetAddress) from[0];
                    email = address.getAddress();
                    name = address.getPersonal() == null ? email : address.getPersonal();
                } else name = from[0].toString();
            }
            obj.put("sender", name); obj.put("email", email);
            Address[] reply=m.getReplyTo();obj.put("replyTo",reply!=null&&reply.length>0&&reply[0] instanceof InternetAddress?((InternetAddress)reply[0]).getAddress():email);
            obj.put("recipients", recipients(m));
            Date date = m.getReceivedDate(); if (date == null) date = m.getSentDate();
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT);
            format.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
            obj.put("date", date == null ? "" : format.format(date));
            obj.put("seen", m.isSet(Flags.Flag.SEEN));
            return obj;
    }
    private JSONArray recipients(Message message) throws Exception {
        JSONArray result=new JSONArray();
        Message.RecipientType[] types={Message.RecipientType.TO,Message.RecipientType.CC,Message.RecipientType.BCC};
        for(Message.RecipientType type:types){
            Address[] values=message.getRecipients(type);if(values==null)continue;
            for(Address value:values)if(value instanceof InternetAddress){
                InternetAddress address=(InternetAddress)value;String email=address.getAddress();
                if(email==null||!email.matches("[^\\s<>@]+@[^\\s<>@]+"))continue;
                result.put(new JSONObject().put("email",email).put("sender",address.getPersonal()==null?email:address.getPersonal()));
            }
        }
        return result;
    }
    JSONObject arrivals(String oldValidity,long oldUid)throws Exception{
        openFolder("INBOX");String current=Long.toString(validity);
        long next=((IMAPFolder)inbox).getUIDNext(),high=next>0?next-1:0;
        if(next<=0){long[] all=uidSnapshot();for(long uid:all)high=Math.max(high,uid);}
        JSONObject result=new JSONObject().put("validity",current).put("uid",high).put("count",0).put("summary","");
        if(!ReminderPolicy.newMail(oldValidity,oldUid,current,high))return result;
        Message[] found=((UIDFolder)inbox).getMessagesByUID(oldUid+1,UIDFolder.LASTUID);ArrayList<Message> messages=new ArrayList<>();
        for(Message message:found)if(message!=null&&!message.isExpunged()){messages.add(message);high=Math.max(high,((UIDFolder)inbox).getUID(message));}
        int start=Math.max(0,messages.size()-3);Message[] recent=messages.subList(start,messages.size()).toArray(new Message[0]);
        FetchProfile profile=new FetchProfile();profile.add(FetchProfile.Item.ENVELOPE);if(recent.length>0)inbox.fetch(recent,profile);
        StringBuilder summary=new StringBuilder();for(int i=recent.length-1;i>=0;i--){if(summary.length()>0)summary.append('\n');String subject=recent[i].getSubject();summary.append(subject==null?"Без темы":subject.substring(0,Math.min(180,subject.length())));}
        return result.put("uid",high).put("count",messages.size()).put("summary",summary.toString());
    }
    JSONObject body(String name, String uid, String expectedValidity) throws Exception {
        openFolder(name);
        if (!Long.toString(validity).equals(expectedValidity)) throw new MessagingException("UID validity changed");
        Message m = ((UIDFolder) inbox).getMessageByUID(Long.parseLong(uid));
        if (m == null || m.isExpunged()) throw new MessagingException("Message unavailable");
        JSONArray files = new JSONArray();
        String text = extract(m, files, 0, "root");
        JSONObject info=header(m);String[] messageIds=m.getHeader("Message-ID");if(messageIds!=null&&messageIds.length>0)info.put("messageId",messageIds[0]);
        return new JSONObject().put("uid", uid).put("text", text).put("attachments", files).put("message", info).put("phones",new JSONArray(PhoneNumbers.find(text)));
    }
    JSONObject seen(String name,String uid,String expectedValidity,boolean seen)throws Exception{
        if(inbox!=null&&inbox.isOpen())inbox.close(false);
        inbox=store.getFolder(name);inbox.open(Folder.READ_WRITE);folderName=name;validity=((UIDFolder)inbox).getUIDValidity();
        try{
            if(!Long.toString(validity).equals(expectedValidity))throw new MessagingException("UID validity changed");
            Message message=((UIDFolder)inbox).getMessageByUID(Long.parseLong(uid));
            if(message==null||message.isExpunged())throw new MessagingException("Message unavailable");
            message.setFlag(Flags.Flag.SEEN,seen);
            return new JSONObject().put("seen",message.isSet(Flags.Flag.SEEN));
        }finally{if(inbox.isOpen())inbox.close(false);inbox=null;}
    }
    void saveSent(javax.mail.internet.MimeMessage message)throws Exception{
        Folder chosen=null;
        for(Folder folder:store.getDefaultFolder().list("*")){
            if((folder.getType()&Folder.HOLDS_MESSAGES)==0)continue;
            if(folder instanceof IMAPFolder)for(String attribute:((IMAPFolder)folder).getAttributes())if(attribute.equalsIgnoreCase("\\Sent"))chosen=folder;
            if(chosen!=null)break;
        }
        if(chosen==null)for(String candidate:new String[]{"Sent","Sent Items","Sent Messages","Отправленное","Отправленные"}){Folder folder=store.getFolder(candidate);if(folder.exists()){chosen=folder;break;}}
        if(chosen==null)throw new MessagingException("Sent folder not found");
        message.setFlag(Flags.Flag.SEEN,true);chosen.appendMessages(new Message[]{message});
    }
    private String extract(Part part, JSONArray files, int depth, String path) throws Exception {
        if (depth > 12) return "";
        String name = part.getFileName();
        if (name != null || Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
            files.put(new JSONObject().put("name", name == null ? "Вложение" : MimeUtility.decodeText(name)).put("size", part.getSize()).put("path",path));
            return ""; // Do not download binary attachment content.
        }
        if (part.isMimeType("text/plain")) return part.getSize() > 5 * 1024 * 1024 ? "[Текстовая часть слишком большая для тестовой версии]" : limit((String) part.getContent());
        if (part.isMimeType("text/html")) {
            if (part.getSize() > 5 * 1024 * 1024) return "[Текстовая часть слишком большая для тестовой версии]";
            String html = limit((String) part.getContent());
            html = html.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>", "");
            return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multi = (Multipart) part.getContent();
            if (part.isMimeType("multipart/alternative")) {
                for (int i = 0; i < multi.getCount(); i++) if (multi.getBodyPart(i).isMimeType("text/plain")) return extract(multi.getBodyPart(i), files, depth + 1, AttachmentParts.child(path,Integer.toString(i)));
                for (int i = multi.getCount() - 1; i >= 0; i--) if (multi.getBodyPart(i).isMimeType("text/html")) return extract(multi.getBodyPart(i), files, depth + 1, AttachmentParts.child(path,Integer.toString(i)));
            }
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < multi.getCount() && i < 80 && text.length() < 100000; i++) {
                String value = extract(multi.getBodyPart(i), files, depth + 1, AttachmentParts.child(path,Integer.toString(i)));
                if (!value.isEmpty()) { if (text.length() > 0) text.append("\n\n"); text.append(value); }
            }
            return limit(text.toString());
        }
        if (part.isMimeType("message/rfc822")) return extract((Part) part.getContent(), files, depth + 1, AttachmentParts.child(path,"m"));
        return "";
    }
    JSONObject attachment(String folder,String uid,String expectedValidity,String path,java.io.File directory)throws Exception{
        openFolder(folder);
        if(!Long.toString(validity).equals(expectedValidity))throw new MessagingException("UID validity changed");
        Message message=((UIDFolder)inbox).getMessageByUID(Long.parseLong(uid));
        if(message==null||message.isExpunged())throw new MessagingException("Message unavailable");
        Part part=AttachmentParts.resolve(message,path);
        String raw=part.getFileName();String name=AttachmentFiles.safeName(raw==null?"Вложение":MimeUtility.decodeText(raw));
        if(part.getSize()>AttachmentFiles.MAX_BYTES)throw new java.io.IOException("Attachment too large");
        String mime=AttachmentFiles.mime(name,new javax.mail.internet.ContentType(part.getContentType()).getBaseType());
        java.io.File file;
        try(java.io.InputStream input=part.getInputStream()){file=AttachmentFiles.copy(input,directory,name,AttachmentFiles.MAX_BYTES);}
        return new JSONObject().put("file",file.getAbsolutePath()).put("name",name).put("mime",mime);
    }
    private String limit(String value) { return value.length() <= 100000 ? value : value.substring(0, 100000) + "\n[Показана часть текста]"; }
    @Override public void close() {
        try { if (inbox != null && inbox.isOpen()) inbox.close(false); } catch (Exception ignored) { }
        try { if (store != null) store.close(); } catch (Exception ignored) { }
        inbox = null; store = null;
    }
}

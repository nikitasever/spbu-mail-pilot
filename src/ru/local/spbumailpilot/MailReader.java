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
    private UidPages smartPages;private String smartFolder="",smartKey="";private long smartValidity,smartGeneration;
    private String pagingQuery="",fileFolder;private long fileValidity,fileGeneration;
    private final java.util.LinkedHashMap<String,JSONObject> downloadedFiles=new java.util.LinkedHashMap<>();
    void connect(String host, int port, String user, String password) throws Exception {
        Properties props = new Properties();
        props.setProperty("mail.imaps.ssl.checkserveridentity", "true");
        props.setProperty("mail.imaps.connectiontimeout", "15000");
        props.setProperty("mail.imaps.timeout", "20000");
        props.setProperty("mail.imaps.writetimeout", "20000");
        props.setProperty("mail.imaps.peek", "true");props.setProperty("mail.imaps.appendbuffersize","0");
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
    boolean hasReply(String id,String... ownAddresses)throws Exception{
        if(!ReplyReminder.validId(id))throw new MessagingException("Invalid reply identity");
        java.util.Set<String> own=new java.util.HashSet<>();for(String value:ownAddresses)if(value!=null&&!value.isEmpty())own.add(new InternetAddress(value).getAddress().toLowerCase(Locale.ROOT));
        openFolder("INBOX");
        Message[] candidates=inbox.search(new javax.mail.search.OrTerm(new javax.mail.search.HeaderTerm("In-Reply-To",id),new javax.mail.search.HeaderTerm("References",id)));
        if(candidates.length>2000)throw new MessagingException("Too many reply candidates");
        FetchProfile profile=new FetchProfile();profile.add(FetchProfile.Item.ENVELOPE);profile.add("In-Reply-To");profile.add("References");inbox.fetch(candidates,profile);
        for(Message candidate:candidates){if(candidate==null||candidate.isExpunged())continue;
            boolean other=false;Address[] from=candidate.getFrom();if(from!=null)for(Address address:from)if(address instanceof InternetAddress&&!own.contains(((InternetAddress)address).getAddress().toLowerCase(Locale.ROOT)))other=true;
            if(!other)continue;
            for(String name:new String[]{"In-Reply-To","References"}){String[] values=candidate.getHeader(name);if(values!=null)for(String value:values)if(ReplyReminder.references(value,id))return true;}
        }return false;
    }
    JSONObject unread(JSONObject result){
        try{int count=store.getFolder("INBOX").getUnreadMessageCount();if(count>=0)result.put("unread",count);}catch(Exception unavailable){/* Never infer zero on a failed STATUS command. */}
        return result;
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
        return uidSnapshot(query.isEmpty()?null:SearchQuery.arguments(query));
    }
    private long[] uidSnapshot(com.sun.mail.iap.Argument search)throws Exception{
        return (long[]) ((IMAPFolder) inbox).doCommand(protocol -> {
            Response[] responses = search==null?protocol.command("UID SEARCH ALL", null):protocol.command("UID SEARCH",search);
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
        JSONArray result=headersForUids(pages.page(offset));
        int next = pages.next(offset);
        return new JSONObject().put("messages", result).put("total", pages.size()).put("nextOffset", next)
                .put("hasMore", next < pages.size()).put("snapshot", Long.toString(generation)).put("folder", name).put("validity",Long.toString(validity));
    }
    private JSONArray headersForUids(long[] uids)throws Exception{
        JSONArray result = new JSONArray();
        Message[] found = uids.length == 0 ? new Message[0] : ((UIDFolder) inbox).getMessagesByUID(uids);
        ArrayList<Message> present = new ArrayList<>();
        for (Message m : found) if (m != null && !m.isExpunged()) present.add(m);
        Message[] messages = present.toArray(new Message[0]);
        FetchProfile profile = new FetchProfile();
        profile.add(FetchProfile.Item.ENVELOPE);
        profile.add(FetchProfile.Item.FLAGS);
        // JavaMail fetches BODYSTRUCTURE in this batch, without downloading bodies.
        profile.add(FetchProfile.Item.CONTENT_INFO);
        profile.add(UIDFolder.FetchProfileItem.UID);MailThreadHeaders.fetch(profile);profile.add("From");
        if (messages.length > 0) inbox.fetch(messages, profile);
        for (int i = messages.length - 1; i >= 0; --i) {
            Message m = messages[i];
            if (m.isExpunged()) continue;
            result.put(header(m));
        }
        return result;
    }
    JSONObject smartPage(String name,int offset,String snapshot,SearchQuery.Filters filters)throws Exception{
        openFolder(name);String key=filters.key();
        if(offset==0){smartPages=new UidPages(uidSnapshot(filters.arguments()));smartFolder=name;smartValidity=validity;smartKey=key;smartGeneration++;}
        else if(smartPages==null||!smartFolder.equals(name)||smartValidity!=validity||!smartKey.equals(key)||!Long.toString(smartGeneration).equals(snapshot))throw new MessagingException("Search snapshot changed");
        JSONArray rows=headersForUids(smartPages.page(offset));int next=smartPages.next(offset);
        return SearchQuery.filtered(new JSONObject().put("messages",rows).put("total",smartPages.size()).put("nextOffset",next).put("hasMore",next<smartPages.size()).put("snapshot",Long.toString(smartGeneration)).put("folder",name).put("validity",Long.toString(validity)),filters);
    }
    JSONObject files(String name,int offset,String snapshot,String query)throws Exception{
        openFolder(name);
        if(offset==0){filePages=new UidPages(uidSnapshot());fileFolder=name;fileValidity=validity;fileGeneration++;}
        else if(filePages==null||!name.equals(fileFolder)||fileValidity!=validity||!Long.toString(fileGeneration).equals(snapshot))throw new MessagingException("File snapshot changed");
        long[] uids=filePages.page(offset);Message[] found=uids.length==0?new Message[0]:((UIDFolder)inbox).getMessagesByUID(uids);
        FetchProfile profile=new FetchProfile();profile.add(FetchProfile.Item.ENVELOPE);profile.add(FetchProfile.Item.CONTENT_INFO);profile.add(UIDFolder.FetchProfileItem.UID);MailThreadHeaders.fetch(profile);profile.add("From");if(found.length>0)inbox.fetch(found,profile);
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
    private String senderPersonal(Message message,InternetAddress envelope){
        try{String[] values=message.getHeader("From");if(values!=null&&values.length>0&&values[0].length()<=8192){InternetAddress[] parsed=InternetAddress.parseHeader(values[0],false);if(parsed.length>0&&envelope.getAddress()!=null&&envelope.getAddress().equalsIgnoreCase(parsed[0].getAddress()))return MimeWords.personal(parsed[0]);}}catch(Exception invalid){}return MimeWords.personal(envelope);
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
                    name = senderPersonal(m,address);
                } else name = from[0].toString();
            }
            obj.put("sender", name); obj.put("email", email);MailThreadHeaders.add(m,obj);
            Address[] reply=m.getReplyTo();obj.put("replyTo",reply!=null&&reply.length>0&&reply[0] instanceof InternetAddress?((InternetAddress)reply[0]).getAddress():email);
            obj.put("recipients", recipients(m));
            Date date = m.getReceivedDate(); if (date == null) date = m.getSentDate();
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT);
            format.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
            obj.put("date", date == null ? "" : format.format(date));
            obj.put("seen", m.isSet(Flags.Flag.SEEN));
            Boolean attachments = attachmentPresence(m);
            if (attachments != null) obj.put("hasAttachments", attachments);
            return obj;
    }
    /** null means the MIME structure could not be inspected completely. */
    static Boolean attachmentPresence(Part part) {
        try { return attachmentPresence(part, 0, new int[]{500}); }
        catch (Exception unavailable) { return null; }
    }
    private static Boolean attachmentPresence(Part part, int depth, int[] remaining) throws Exception {
        if (depth > 12 || remaining[0]-- <= 0) return null;
        String disposition = part.getDisposition();
        if (Part.ATTACHMENT.equalsIgnoreCase(disposition)) return true;
        // Embedded images decorate HTML; they should not advertise a downloadable document.
        if (Part.INLINE.equalsIgnoreCase(disposition) && part.isMimeType("image/*")) return false;
        if (part.getFileName() != null) return true;
        // A forwarded message is itself an attachment. Never decode its RFC822 body here.
        if (part.isMimeType("message/rfc822")) return true;
        if (!part.isMimeType("multipart/*")) return false;
        // On IMAP parts this creates IMAPMultipartDataSource from the prefetched BODYSTRUCTURE.
        // It visits child metadata, never getInputStream()/text or binary content.
        Object content = part.getContent();
        if (!(content instanceof Multipart)) return null;
        Multipart multipart = (Multipart) content;
        boolean unknown = false;
        for (int i = 0; i < multipart.getCount(); i++) {
            Boolean found = attachmentPresence(multipart.getBodyPart(i), depth + 1, remaining);
            if (Boolean.TRUE.equals(found)) return true;
            if (found == null) unknown = true;
            if (remaining[0] <= 0) return null;
        }
        return unknown ? null : false;
    }

    private JSONArray recipients(Message message) throws Exception {
        JSONArray result=new JSONArray();
        Message.RecipientType[] types={Message.RecipientType.TO,Message.RecipientType.CC,Message.RecipientType.BCC};
        for(Message.RecipientType type:types){
            Address[] values=message.getRecipients(type);if(values==null)continue;
            for(Address value:values)if(value instanceof InternetAddress){
                InternetAddress address=(InternetAddress)value;String email=address.getAddress();
                if(email==null||!email.matches("[^\\s<>@]+@[^\\s<>@]+"))continue;
                result.put(new JSONObject().put("email",email).put("sender",MimeWords.personal(address)));
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
        FetchProfile profile=new FetchProfile();profile.add(FetchProfile.Item.ENVELOPE);profile.add("From");if(recent.length>0)inbox.fetch(recent,profile);
        StringBuilder summary=new StringBuilder();for(int i=recent.length-1;i>=0;i--){if(summary.length()>0)summary.append('\n');Address[] from=recent[i].getFrom();String name="",email="";if(from!=null&&from.length>0){if(from[0] instanceof InternetAddress){InternetAddress address=(InternetAddress)from[0];name=senderPersonal(recent[i],address);email=address.getAddress();}else name=from[0].toString();}String sender=MailNotificationText.sender(name,email),subject=MailNotificationText.clean(recent[i].getSubject(),"Без темы",180);summary.append(sender).append(" · ").append(subject);if(i==recent.length-1)result.put("sender",sender).put("email",email).put("subject",subject).put("openUid",Long.toString(((UIDFolder)inbox).getUID(recent[i])));}

        return result.put("uid",high).put("count",messages.size()).put("summary",summary.toString());
    }
    JSONObject previews(String name,JSONArray refs)throws Exception {
        if(refs.length()>5)throw new IllegalArgumentException("Too many previews");
        openFolder(name);JSONArray items=new JSONArray();
        for(int i=0;i<refs.length();i++) {
            JSONObject ref=refs.getJSONObject(i);
            if(!Long.toString(validity).equals(ref.getString("validity")))continue;
            Message message=((UIDFolder)inbox).getMessageByUID(Long.parseLong(ref.getString("uid")));
            if(message==null||message.isExpunged())continue;
            if(message instanceof com.sun.mail.imap.IMAPMessage)((com.sun.mail.imap.IMAPMessage)message).setPeek(true);
            try {items.put(new JSONObject().put("uid",ref.getString("uid")).put("validity",ref.getString("validity")).put("text",MailPreview.read(message)));}
            catch(Exception unavailable) { /* A preview failure must not hide the mail list. */ }
        }
        return new JSONObject().put("items",items);
    }
    JSONObject body(String name,String uid,String validity)throws Exception{return body(name,uid,validity,false);}
    JSONObject cachedBody(String name,String uid,String validity)throws Exception{return body(name,uid,validity,true);}
    private JSONObject body(String name, String uid, String expectedValidity,boolean prefetch) throws Exception {
        openFolder(name);
        if (!Long.toString(validity).equals(expectedValidity)) throw new MessagingException("UID validity changed");
        Message m = ((UIDFolder) inbox).getMessageByUID(Long.parseLong(uid));
        if (m == null || m.isExpunged()) throw new MessagingException("Message unavailable");
        FetchProfile profile=new FetchProfile();profile.add(FetchProfile.Item.ENVELOPE);profile.add(FetchProfile.Item.FLAGS);profile.add(FetchProfile.Item.CONTENT_INFO);MailThreadHeaders.fetch(profile);profile.add("From");
        inbox.fetch(new Message[]{m},profile);
        JSONArray files = new JSONArray();
        if(m instanceof com.sun.mail.imap.IMAPMessage)((com.sun.mail.imap.IMAPMessage)m).setPeek(true);
        String text = extract(m, files, 0, "root",prefetch?24000:100000,prefetch);
        JSONObject info=header(m);String[] rawFrom=m.getHeader("From");if(rawFrom!=null&&rawFrom.length>0)info.put("rawFrom",rawFrom[0]);
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
    Store draftStore(){return store;}
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
    private String extract(Part part, JSONArray files, int depth, String path,int budget,boolean prefetch) throws Exception {
        if (depth > 12) return "";
        if(prefetch&&part.isMimeType("message/rfc822")){String forwardName=part.getFileName();files.put(new JSONObject().put("name",forwardName==null?"Пересланное письмо.eml":AttachmentNames.decode(forwardName)).put("size",part.getSize()).put("path",path));return "";}
        String name = part.getFileName();
        if (name != null || Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
            files.put(new JSONObject().put("name", name == null ? "Вложение" : AttachmentNames.decode(name)).put("size", part.getSize()).put("path",path));
            return ""; // Do not download binary attachment content.
        }
        if (part.isMimeType("text/plain")) return MailText.read(part,budget);
        if (part.isMimeType("text/html")) {
            String html = MailText.read(part,budget);
            html = html.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>", "");
            return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multi = (Multipart) part.getContent();
            if (part.isMimeType("multipart/alternative")) {
                for (int i = 0; i < multi.getCount(); i++) if (multi.getBodyPart(i).isMimeType("text/plain")) return extract(multi.getBodyPart(i), files, depth + 1, AttachmentParts.child(path,Integer.toString(i)),budget,prefetch);
                for (int i = multi.getCount() - 1; i >= 0; i--) if (multi.getBodyPart(i).isMimeType("text/html")) return extract(multi.getBodyPart(i), files, depth + 1, AttachmentParts.child(path,Integer.toString(i)),budget,prefetch);
            }
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < multi.getCount() && i < 80; i++) {
                String value = extract(multi.getBodyPart(i), files, depth + 1, AttachmentParts.child(path,Integer.toString(i)),Math.max(0,budget-text.length()),prefetch);
                if (!value.isEmpty()) { if (text.length() > 0) text.append("\n\n"); text.append(value); }
            }
            return limit(text.toString());
        }
        if (part.isMimeType("message/rfc822")) return extract((Part) part.getContent(), files, depth + 1, AttachmentParts.child(path,"m"),budget,prefetch);
        return "";
    }
    JSONObject attachment(String folder,String uid,String expectedValidity,String path,java.io.File directory)throws Exception{
        return attachment(folder,uid,expectedValidity,path,directory,()->false,bytes->{});
    }
    JSONObject attachment(String folder,String uid,String expectedValidity,String path,java.io.File directory,java.util.function.BooleanSupplier cancelled,java.util.function.LongConsumer progress)throws Exception{
        return attachmentMeasured(folder,uid,expectedValidity,path,directory,cancelled,(bytes,total)->progress.accept(bytes));
    }
    JSONObject attachmentMeasured(String folder,String uid,String expectedValidity,String path,java.io.File directory,java.util.function.BooleanSupplier cancelled,AttachmentTransfer.Progress progress)throws Exception{
        BulkAttachments.check(cancelled);
        openFolder(folder);
        if(!Long.toString(validity).equals(expectedValidity))throw new MessagingException("UID validity changed");
        Message message=((UIDFolder)inbox).getMessageByUID(Long.parseLong(uid));
        if(message==null||message.isExpunged())throw new MessagingException("Message unavailable");
        String cacheKey=new JSONArray().put(folder).put(uid).put(expectedValidity).put(path).put(directory.getAbsolutePath()).toString();
        JSONObject cached=downloadedFiles.get(cacheKey);if(cached!=null&&new java.io.File(cached.getString("file")).isFile())return new JSONObject(cached.toString());
        Part part=AttachmentParts.resolve(message,path);
        String raw=part.getFileName();String name=AttachmentFiles.safeName(raw==null?"Вложение":AttachmentNames.decode(raw));
        // MIME size is transfer-encoded (e.g. base64), not the saved file size.
        // AttachmentTransfer bounds both encoded bytes and the decoded 100 MiB file.
        String mime=AttachmentFiles.mime(name,new javax.mail.internet.ContentType(part.getContentType()).getBaseType());
        java.io.File file;
        file=AttachmentTransfer.copy(part,directory,name,cancelled,progress);
        JSONObject result=new JSONObject().put("file",file.getAbsolutePath()).put("name",name).put("mime",mime);
        downloadedFiles.put(cacheKey,result);if(downloadedFiles.size()>30)downloadedFiles.remove(downloadedFiles.keySet().iterator().next());return new JSONObject(result.toString());
    }
    private String limit(String value) {
        if(value.length()<=100000)return value;
        int end=100000;
        if(Character.isHighSurrogate(value.charAt(end-1))&&Character.isLowSurrogate(value.charAt(end)))end--;
        return value.substring(0,end)+"\n[Показана часть текста]";
    }
    @Override public void close() {
        try { if (inbox != null && inbox.isOpen()) inbox.close(false); } catch (Exception ignored) { }
        try { if (store != null) store.close(); } catch (Exception ignored) { }
        inbox = null; store = null;downloadedFiles.clear();
    }
}


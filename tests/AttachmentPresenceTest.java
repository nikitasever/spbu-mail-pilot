package ru.local.spbumailpilot;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** List metadata must not need the attachment or text payload. */
public class AttachmentPresenceTest {
    static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    static class MetadataPart extends MimeBodyPart {
        final String type, disposition, name;
        MetadataPart(String type, String disposition, String name) {
            this.type=type; this.disposition=disposition; this.name=name;
        }
        public String getContentType() { return type; }
        public boolean isMimeType(String pattern) throws MessagingException {
            return new ContentType(type).match(pattern);
        }
        public String getDisposition() { return disposition; }
        public String getFileName() { return name; }
        public Object getContent() throws IOException { throw new AssertionError("Payload decoded"); }
        public InputStream getInputStream() throws IOException { throw new AssertionError("Payload downloaded"); }
    }
    static MimeBodyPart container(final Part... children) throws Exception {
        final MimeMultipart multipart=new MimeMultipart();
        for (Part child:children) multipart.addBodyPart((BodyPart)child);
        return new MetadataPart("multipart/mixed",null,null) {
            public Object getContent() { return multipart; }
        };
    }
    public static void main(String[] args) throws Exception {
        MetadataPart text=new MetadataPart("text/plain; charset=UTF-8",null,null);
        MetadataPart pdf=new MetadataPart("application/pdf",Part.ATTACHMENT,"document.pdf");
        MetadataPart inline=new MetadataPart("image/png",Part.INLINE,"logo.png");
        check(Boolean.FALSE.equals(MailReader.attachmentPresence(text)));
        check(Boolean.TRUE.equals(MailReader.attachmentPresence(pdf)));
        check(Boolean.FALSE.equals(MailReader.attachmentPresence(inline)));
        check(Boolean.TRUE.equals(MailReader.attachmentPresence(new MetadataPart("image/png",Part.ATTACHMENT,"photo.png"))));
        check(Boolean.TRUE.equals(MailReader.attachmentPresence(new MetadataPart("application/pdf",null,"document.pdf"))));
        check(Boolean.TRUE.equals(MailReader.attachmentPresence(new MetadataPart("application/pdf",Part.ATTACHMENT,null))));
        check(Boolean.TRUE.equals(MailReader.attachmentPresence(new MetadataPart("message/rfc822",null,null))));
        check(Boolean.FALSE.equals(MailReader.attachmentPresence(container(text,inline))));
        check(Boolean.TRUE.equals(MailReader.attachmentPresence(container(text,container(inline,pdf)))));
        MimeBodyPart broken=new MetadataPart("multipart/mixed",null,null) {
            public Object getContent() throws IOException { throw new IOException("Structure unavailable"); }
        };
        check(MailReader.attachmentPresence(broken)==null);
        Part nested=text; for(int i=0;i<14;i++) nested=container(nested);
        check(MailReader.attachmentPresence(nested)==null);
        Part[] many=new Part[501]; Arrays.fill(many,text);
        check(MailReader.attachmentPresence(container(many))==null);

        ReadFlagTest.MemoryStore store=new ReadFlagTest.MemoryStore(Session.getInstance(new Properties()));
        final MimeMultipart multipart=new MimeMultipart(); multipart.addBodyPart(text); multipart.addBodyPart(pdf);
        store.folder.message.setContent(multipart); store.folder.message.setHeader("Content-Type",multipart.getContentType());
        MailReader reader=new MailReader();
        java.lang.reflect.Field inbox=MailReader.class.getDeclaredField("inbox"); inbox.setAccessible(true); inbox.set(reader,store.folder);
        java.lang.reflect.Method header=MailReader.class.getDeclaredMethod("header",Message.class); header.setAccessible(true);
        JSONObject result=(JSONObject)header.invoke(reader,store.folder.message);
        check(result.getBoolean("hasAttachments")); check(!result.getBoolean("seen"));
        check(!store.folder.message.isSet(Flags.Flag.SEEN));
        multipart.removeBodyPart(pdf); multipart.addBodyPart(inline);
        result=(JSONObject)header.invoke(reader,store.folder.message);
        check(result.has("hasAttachments") && !result.getBoolean("hasAttachments"));
        MimeMessage unavailable=new MimeMessage(Session.getInstance(new Properties())) {
            public Object getContent() throws IOException { throw new IOException("Structure unavailable"); }
        };
        unavailable.setHeader("Content-Type","multipart/mixed; boundary=missing");
        result=(JSONObject)header.invoke(reader,unavailable);
        check(!result.has("hasAttachments"));
        System.out.println("PASS: attachment metadata before opening, inline decoration exclusion, nested MIME, forwarded message without decoding, unknown structure bounds, header Seen preservation.");
    }
}

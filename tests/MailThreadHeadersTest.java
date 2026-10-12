package ru.local.spbumailpilot;
import javax.mail.*;
import javax.mail.internet.*;
import org.json.*;
import java.util.*;
public class MailThreadHeadersTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        check(MailThreadHeaders.ids(new String[]{"comment <root@example>\r\n <parent@example> <root@example>"}).equals(Arrays.asList("<root@example>","<parent@example>")));
        check(MailThreadHeaders.ids(new String[]{"<script>bad</script> <invalid> <a b@host> <тест@host>"}).isEmpty());
        check(!MailThreadHeaders.valid("<id@example>\r\nBcc: victim@example"));check(!MailThreadHeaders.valid("<id@example> <second@example>"));
        Session session=Session.getInstance(new Properties());MimeMessage message=new MimeMessage(session);message.setHeader("Message-ID","<child@example>");message.setHeader("References","<root@example>\r\n <parent@example>");message.setHeader("In-Reply-To","<parent@example>");
        JSONObject headers=new JSONObject();MailThreadHeaders.add(message,headers);check(headers.getString("messageId").equals("<child@example>"));check(headers.getJSONArray("references").length()==2);check(headers.getString("inReplyTo").equals("<parent@example>"));
        message.setHeader("Message-ID","<one@example> <two@example>");headers=new JSONObject();MailThreadHeaders.add(message,headers);check(!headers.has("messageId"));
        MimeMessage reply=new MimeMessage(session);reply.setText("body");MailThreadHeaders.reply(reply,"<child@example>",new JSONArray().put("<root@example>").put("<parent@example>").put("<root@example>"));check(MailThreadHeaders.ids(reply.getHeader("References")).equals(Arrays.asList("<root@example>","<parent@example>","<child@example>")));
        JSONArray many=new JSONArray();for(int i=0;i<100;i++)many.put("<id"+i+"@example>");MailThreadHeaders.reply(reply,"<latest@example>",many);List<String> ids=MailThreadHeaders.ids(reply.getHeader("References"));check(ids.size()==100);check(ids.get(0).equals("<id0@example>"));check(ids.get(99).equals("<latest@example>"));
        try{MailThreadHeaders.reply(reply,"<child@example>",new JSONArray().put("<bad@example>\r\nBcc: victim@example"));throw new AssertionError();}catch(IllegalArgumentException expected){}
        try{MailThreadHeaders.reply(reply,"",new JSONArray().put("<root@example>"));throw new AssertionError();}catch(IllegalArgumentException expected){}
        SavedAccounts.Login login=new SavedAccounts.Login("mail.example.invalid",993,"sender@example.invalid","fixture");JSONObject outgoing=new JSONObject().put("to","recipient@example.invalid").put("subject","Reply").put("body","body").put("inReplyTo","<child@example>").put("references",new JSONArray().put("<root@example>").put("<parent@example>"));
        MimeMessage sent=MailSender.message(session,login,outgoing,new java.io.File("unused-fixture"));check(MailThreadHeaders.ids(sent.getHeader("References")).equals(Arrays.asList("<root@example>","<parent@example>","<child@example>")));
        MimeMessage serverDraft=MailSender.draft(login,outgoing,new java.io.File("unused-fixture"));check(MailThreadHeaders.ids(serverDraft.getHeader("References")).equals(Arrays.asList("<root@example>","<parent@example>","<child@example>")));
        JSONObject draft=new JSONObject().put("body","body"),changed=new JSONObject(draft.toString()).put("references",new JSONArray().put("<root@example>"));check(!ServerDrafts.revision(draft).equals(ServerDrafts.revision(changed)));
        System.out.println("PASS: bounded/folded RFC thread identifiers, ambiguous/malformed ID isolation, complete reply References, header injection rejection and draft revision tracking.");
    }
}

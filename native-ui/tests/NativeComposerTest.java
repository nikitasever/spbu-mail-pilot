package ru.local.spbumailpilot;
import org.json.*;
import java.io.IOException;

public final class NativeComposerTest {
    static final String A="account-"+new String(new char[64]).replace('\0','a'), B="account-"+new String(new char[64]).replace('\0','b');
    interface Check {void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Check action)throws Exception{try{action.run();}catch(Exception expected){return;}throw new AssertionError("Expected failure");}
    static final class Store implements NativeSendTransaction.Store {
        NativeDraft current;int writes,failAt;
        Store(NativeDraft draft){current=draft;}
        public void write(NativeDraft next)throws Exception{if(++writes==failAt)throw new IOException("Disk full");NativeDraft.checkWrite(current,next);current=NativeDraft.parse(next.owner,next.json().toString());}
    }
    public static void main(String[] args)throws Exception{
        JSONObject source=new JSONObject().put("email","sender@example.invalid").put("replyTo","reply@example.invalid")
            .put("subject","Тема").put("messageId","<parent@example.invalid>").put("references",new JSONArray().put("<root@example.invalid>"));
        NativeDraft draft=NativeDraft.create(A,source,"Подпись");
        check(draft.mail.getString("to").equals("reply@example.invalid"));check(draft.mail.getString("subject").equals("Re: Тема"));
        check(draft.mail.getJSONArray("attachments").length()==0);
        NativeDraft restored=NativeDraft.parse(A,draft.json().toString());check(restored.mail.getString("body").equals("Подпись"));
        reject(()->NativeDraft.parse(B,draft.json().toString()));
        reject(()->NativeDraft.parse(A,draft.json().put("version",2).toString()));
        reject(()->new NativeDraft(A,NativeDraft.EDITING,new JSONObject(draft.mail.toString()).put("to","bad\r\nBcc: x@example.invalid")));
        NativeDraft noParent=NativeDraft.create(A,new JSONObject().put("email","sender@example.invalid").put("references",source.getJSONArray("references")),"");
        check(noParent.mail.getJSONArray("references").length()==0);
        Store success=new Store(draft);int[] sent={0};
        NativeDraft result=NativeSendTransaction.run(draft,success,()->{check(success.current.status.equals(NativeDraft.DISPATCHING));sent[0]++;});
        check(result.status.equals(NativeDraft.SENT)&&sent[0]==1&&success.current.revision==2);
        NativeDraft delivered=result;reject(()->NativeSendTransaction.run(delivered,success,()->sent[0]++));check(sent[0]==1);
        Store noDisk=new Store(draft);noDisk.failAt=1;reject(()->NativeSendTransaction.run(draft,noDisk,()->sent[0]++));check(sent[0]==1);
        Store uncertain=new Store(draft);result=NativeSendTransaction.run(draft,uncertain,()->{sent[0]++;throw new MailSender.Uncertain(new IOException("Connection lost"));});
        check(result.status.equals(NativeDraft.UNCERTAIN));NativeDraft crash=NativeDraft.parse(A,uncertain.current.json().toString());
        reject(()->NativeSendTransaction.run(crash,uncertain,()->sent[0]++));check(sent[0]==2);
        Store finalDisk=new Store(draft);finalDisk.failAt=2;reject(()->NativeSendTransaction.run(draft,finalDisk,()->sent[0]++));
        check(finalDisk.current.status.equals(NativeDraft.DISPATCHING));
        reject(()->NativeSendTransaction.run(finalDisk.current,finalDisk,()->sent[0]++));check(sent[0]==3);
        Store known=new Store(draft);reject(()->NativeSendTransaction.run(draft,known,()->{throw new IOException("Not connected");}));check(known.current.status.equals(NativeDraft.EDITING));
        // A stale Activity cannot overwrite a newer version or start a second delivery.
        reject(()->NativeDraft.checkWrite(success.current,draft.status(NativeDraft.EDITING)));
        reject(()->NativeSendTransaction.run(draft,success,()->sent[0]++));check(sent[0]==3);
        SavedAccounts.Login login=new SavedAccounts.Login("example.invalid",993,"own@example.invalid","fixture");
        javax.mail.internet.MimeMessage message=MailSender.message(javax.mail.Session.getInstance(new java.util.Properties()),login,draft.mail,new java.io.File("unused"));
        check(message.getHeader("In-Reply-To")[0].equals("<parent@example.invalid>"));
        check(message.getHeader("References")[0].contains("<root@example.invalid>"));
        check(message.getAllRecipients()[0].toString().equals("reply@example.invalid"));
        System.out.println("PASS native composer: account-isolated round trip, reply headers, bounds, durable send markers, rejected/unknown delivery, disk failures, stale editor and duplicate send; no network.");
    }
}

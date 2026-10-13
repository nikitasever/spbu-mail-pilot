package ru.local.spbumailpilot;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.security.keystore.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import javax.crypto.*;
import org.json.*;

/** Immutable encrypted snapshots, transactionally claimed before SMTP. No passwords in the queue. */
final class ScheduledMail {
    static final int JOB=2410,ALARM=2411;
    static final String ACTION="ru.local.spbumailpilot.SCHEDULED_SEND",ALIAS="spbu-mail-scheduled-v1";
    static final Object LOCK=new Object();
    private static final AtomicBoolean running=new AtomicBoolean();
    private static final Set<String> active=new HashSet<>();
    private static SQLiteDatabase db(Context c){
        SQLiteDatabase d=SQLiteDatabase.openOrCreateDatabase(new File(c.getNoBackupFilesDir(),"scheduled-mail.db"),null);
        d.execSQL("CREATE TABLE IF NOT EXISTS queue (id TEXT PRIMARY KEY, account TEXT NOT NULL, source TEXT NOT NULL, due INTEGER NOT NULL, next INTEGER NOT NULL, state TEXT NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, returned_source TEXT, payload BLOB NOT NULL, UNIQUE(account,source))");
        d.execSQL("CREATE INDEX IF NOT EXISTS queue_due ON queue(state,next)");return d;
    }
    private static SecretKey key(boolean create)throws Exception{
        KeyStore s=KeyStore.getInstance("AndroidKeyStore");s.load(null);if(s.containsAlias(ALIAS))return (SecretKey)s.getKey(ALIAS,null);if(!create)throw new IOException("Scheduled key unavailable");
        KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey();
    }
    private static byte[] encode(String account,String id,JSONObject json)throws Exception{byte[] plain=json.toString().getBytes(StandardCharsets.UTF_8);try{return OfflineMailStore.crypt(true,key(true),"scheduled/"+account+"/"+id,plain);}finally{Arrays.fill(plain,(byte)0);}}
    private static JSONObject decode(String account,String id,byte[] blob)throws Exception{if(blob.length>1024*1024)throw new IOException("Queue record too large");byte[] plain=OfflineMailStore.crypt(false,key(false),"scheduled/"+account+"/"+id,blob);try{return new JSONObject(new String(plain,StandardCharsets.UTF_8));}finally{Arrays.fill(plain,(byte)0);}}
    private static File files(Context c,String account,String id){return new File(new File(c.getNoBackupFilesDir(),"scheduled-files/"+account),id);}
    private static File uploads(Context c,SavedAccounts.Login login)throws Exception{return new File(c.getFilesDir(),"outgoing/"+NoticeCenter.scope(login));}
    private static void deleteFiles(File directory){File[] list=directory.listFiles();if(list!=null)for(File f:list)f.delete();directory.delete();}
    private static ContentValues values(String state,long next){ContentValues v=new ContentValues();v.put("state",state);v.put("next",next);return v;}
    private static boolean live(String state){return state.equals("pending")||state.equals("preparing")||state.equals("transmitting")||state.equals("failed")||state.equals("uncertain");}
    private static void recover(SQLiteDatabase d){
        try(Cursor rows=d.rawQuery("SELECT id,state FROM queue WHERE state IN ('preparing','transmitting')",null)){
            while(rows.moveToNext())if(!active.contains(rows.getString(0)))d.update("queue",values(ScheduledPolicy.recover(rows.getString(1)),System.currentTimeMillis()),"id=?",new String[]{rows.getString(0)});
        }
    }
    static boolean protects(Context c,SavedAccounts.Login login,String source)throws Exception{synchronized(LOCK){try(SQLiteDatabase d=db(c);Cursor rows=d.rawQuery("SELECT id FROM queue WHERE account=? AND (source=? OR (returned_source=? AND state!='cancelled'))",new String[]{NoticeCenter.scope(login),source,source})){return rows.moveToFirst();}}}
    static JSONObject create(Context c,SavedAccounts.Login login,JSONObject original,long at)throws Exception{
        if(at<=System.currentTimeMillis()||at>253402300799000L)throw new IllegalArgumentException("Future time required");
        String source=original.getString("id");if(!source.matches("[A-Za-z0-9_-]{1,100}"))throw new IllegalArgumentException("Draft identity");
        String account=NoticeCenter.scope(login),id=UUID.randomUUID().toString();JSONObject draft=new JSONObject(original.toString());
        MailSender.message(javax.mail.Session.getInstance(new Properties()),login,draft,uploads(c,login));
        File directory=files(c,account,id);boolean stored=false;
        synchronized(LOCK){try(SQLiteDatabase d=db(c)){
            recover(d);try(Cursor rows=d.rawQuery("SELECT count(*) FROM queue WHERE account=? AND state NOT IN ('sent','cancelled')",new String[]{account})){rows.moveToFirst();if(rows.getInt(0)>=50)throw new IllegalStateException("Queue full");}
            if(protects(c,login,source))throw new IllegalStateException("Draft already scheduled");
            JSONArray sourceFiles=draft.optJSONArray("attachments"),copied=new JSONArray();
            if(sourceFiles!=null)for(int i=0;i<sourceFiles.length();i++){JSONObject entry=new JSONObject(sourceFiles.getJSONObject(i).toString());try(InputStream input=new FileInputStream(MailSender.file(uploads(c,login),entry.getString("token")))){File copy=AttachmentFiles.copy(input,directory,entry.optString("name"),AttachmentFiles.MAX_BYTES);entry.put("token",copy.getName());copied.put(entry);}}
            draft.put("attachments",copied).put("scheduledMessageId","<schedule-"+id+"@mail-pilot.local>");
            JSONObject payload=new JSONObject().put("draft",draft).put("from",login.from).put("to",draft.optString("to")).put("subject",draft.optString("subject"));
            ContentValues v=values("pending",at);v.put("id",id);v.put("account",account);v.put("source",source);v.put("due",at);v.put("payload",encode(account,id,payload));d.insertOrThrow("queue",null,v);stored=true;
        }finally{if(!stored)deleteFiles(directory);}}
        JSONArray originalFiles=original.optJSONArray("attachments");if(originalFiles!=null)for(int i=0;i<originalFiles.length();i++)try{MailSender.file(uploads(c,login),originalFiles.getJSONObject(i).getString("token")).delete();}catch(Exception ignored){}
        boolean ready=reconcile(c);return new JSONObject().put("scheduled",true).put("id",id).put("at",at).put("warning",ready?"":"Письмо сохранено, но Android не подтвердил запуск планировщика. Откройте приложение снова перед сроком.");
    }
    static JSONObject list(Context c,SavedAccounts.Login login)throws Exception{synchronized(LOCK){try(SQLiteDatabase d=db(c)){
        recover(d);String account=NoticeCenter.scope(login);JSONArray items=new JSONArray(),protectedIds=new JSONArray();
        try(Cursor rows=d.rawQuery("SELECT source,returned_source,state FROM queue WHERE account=? ORDER BY due DESC LIMIT 5000",new String[]{account})){while(rows.moveToNext()){protectedIds.put(rows.getString(0));if(!rows.isNull(1)&&!rows.getString(2).equals("cancelled"))protectedIds.put(rows.getString(1));}}
        try(Cursor rows=d.rawQuery("SELECT id,due,next,state,payload FROM queue WHERE account=? ORDER BY CASE WHEN state IN ('sent','cancelled') THEN 1 ELSE 0 END,due DESC LIMIT 150",new String[]{account})){
            while(rows.moveToNext()){String id=rows.getString(0);JSONObject p;try{p=decode(account,id,rows.getBlob(4));}catch(Exception unreadable){p=new JSONObject().put("subject","Не удалось прочитать запись").put("warning","Сохранённая копия не удалена. При невозможности чтения отправка не выполняется.");}items.put(new JSONObject().put("id",id).put("at",rows.getLong(1)).put("next",rows.getLong(2)).put("state",rows.getString(3)).put("to",p.optString("to")).put("subject",p.optString("subject")).put("warning",p.optString("warning")).put("sentAt",p.optLong("sentAt")).put("recoverable",rows.getString(3).equals("cancelled")&&(p.has("returnedDraft")||p.has("draft"))).put("returnedId",p.optJSONObject("returnedDraft")==null?"":p.getJSONObject("returnedDraft").optString("id")));}
        }return new JSONObject().put("items",items).put("protectedDraftIds",protectedIds);
    }}}
    static JSONObject details(Context c,SavedAccounts.Login login,String id)throws Exception{synchronized(LOCK){try(SQLiteDatabase d=db(c);Cursor row=d.rawQuery("SELECT payload FROM queue WHERE account=? AND id=?",new String[]{NoticeCenter.scope(login),id})){
        if(!row.moveToFirst())throw new IllegalArgumentException();JSONObject p=decode(NoticeCenter.scope(login),id,row.getBlob(0));return new JSONObject().put("draft",p.getJSONObject("draft")).put("from",p.optString("from",login.from));
    }}}
    static JSONObject cancel(Context c,SavedAccounts.Login login,String id)throws Exception{
        if(id==null||!id.matches("[0-9a-f-]{36}"))throw new IllegalArgumentException();String account=NoticeCenter.scope(login);
        synchronized(LOCK){try(SQLiteDatabase d=db(c)){
            // Stop eligibility first. Restoring large files must not delay cancellation.
            if(d.update("queue",values("cancelled",0),"account=? AND id=? AND state IN ('pending','preparing','failed','uncertain')",new String[]{account,id})!=1)throw new IllegalStateException("Send already started");
        }}reconcile(c);
        try{return returned(c,login,id).put("cancelled",true);}catch(Exception cannotRestore){return new JSONObject().put("cancelled",true).put("warning","Отправка отменена. Копия сохранена в очереди; восстановите черновик после освобождения места.");}
    }
    static JSONObject returned(Context c,SavedAccounts.Login login,String id)throws Exception{
        if(id==null||!id.matches("[0-9a-f-]{36}"))throw new IllegalArgumentException();String account=NoticeCenter.scope(login);
        synchronized(LOCK){try(SQLiteDatabase d=db(c);Cursor row=d.rawQuery("SELECT payload FROM queue WHERE account=? AND id=? AND state='cancelled'",new String[]{account,id})){
            if(!row.moveToFirst())throw new IllegalArgumentException();JSONObject p=decode(account,id,row.getBlob(0));if(p.has("returnedDraft"))return new JSONObject().put("draft",p.getJSONObject("returnedDraft"));
            if(!p.has("draft"))throw new IllegalStateException("Draft already returned");
            JSONObject restored=new JSONObject(p.getJSONObject("draft").toString());JSONArray entries=restored.optJSONArray("attachments"),copies=new JSONArray();List<File> created=new ArrayList<>();boolean committed=false;
            try{if(entries!=null)for(int i=0;i<entries.length();i++){JSONObject entry=new JSONObject(entries.getJSONObject(i).toString());try(InputStream input=new FileInputStream(MailSender.file(files(c,account,id),entry.getString("token")))){File copy=AttachmentFiles.copy(input,uploads(c,login),entry.optString("name"),AttachmentFiles.MAX_BYTES);created.add(copy);entry.put("token",copy.getName());copies.put(entry);}}
                restored.put("id",UUID.randomUUID().toString()).put("attachments",copies);restored.remove("serverDraft");restored.remove("serverDraftUnconfirmed");restored.remove("serverDraftAttempted");restored.remove("scheduledMessageId");
                p.put("returnedDraft",restored);ContentValues v=new ContentValues();v.put("payload",encode(account,id,p));v.put("returned_source",restored.getString("id"));if(d.update("queue",v,"account=? AND id=? AND state='cancelled'",new String[]{account,id})!=1)throw new IllegalStateException();committed=true;
                return new JSONObject().put("draft",restored);
            }finally{if(!committed)for(File f:created)f.delete();}
        }}
    }
    static void forget(Context c,SavedAccounts.Login login)throws Exception{synchronized(LOCK){try(SQLiteDatabase d=db(c)){
        recover(d);String account=NoticeCenter.scope(login);try(Cursor row=d.rawQuery("SELECT id FROM queue WHERE account=? AND state='transmitting'",new String[]{account})){if(row.moveToFirst())throw new IllegalStateException("Wait for active scheduled send");}
        // Keep recoverable snapshots, but make them permanently ineligible for automatic sending.
        d.update("queue",values("failed",0),"account=? AND state IN ('pending','preparing')",new String[]{account});
    }}}
    static void acknowledgeReturned(Context c,SavedAccounts.Login login,String id)throws Exception{synchronized(LOCK){try(SQLiteDatabase d=db(c);Cursor row=d.rawQuery("SELECT payload FROM queue WHERE account=? AND id=? AND state='cancelled'",new String[]{NoticeCenter.scope(login),id})){
        if(!row.moveToFirst())return;String account=NoticeCenter.scope(login);JSONObject p=decode(account,id,row.getBlob(0)),summary=new JSONObject().put("to",p.optString("to")).put("subject",p.optString("subject"));ContentValues v=new ContentValues();v.put("payload",encode(account,id,summary));d.update("queue",v,"id=? AND state='cancelled'",new String[]{id});deleteFiles(files(c,account,id));
    }}}
    static void returnedSent(Context c,SavedAccounts.Login login,String source)throws Exception{synchronized(LOCK){try(SQLiteDatabase d=db(c)){
        String account=NoticeCenter.scope(login);try(Cursor row=d.rawQuery("SELECT id,payload FROM queue WHERE account=? AND returned_source=? AND state='cancelled'",new String[]{account,source})){
            while(row.moveToNext()){String id=row.getString(0);JSONObject p=decode(account,id,row.getBlob(1));ContentValues v=values("sent",0);v.put("payload",encode(account,id,new JSONObject().put("to",p.optString("to")).put("subject",p.optString("subject")).put("sentAt",System.currentTimeMillis())));d.update("queue",v,"id=?",new String[]{id});deleteFiles(files(c,account,id));}
        }
    }}}
    static void forgetAll(Context c)throws Exception{synchronized(LOCK){try(SQLiteDatabase d=db(c)){recover(d);try(Cursor row=d.rawQuery("SELECT id FROM queue WHERE state='transmitting'",null)){if(row.moveToFirst())throw new IllegalStateException("Wait for active scheduled send");}d.update("queue",values("failed",0),"state IN ('pending','preparing')",null);
    }}}
    private static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,ALARM,new Intent(c,NoticeAlarm.class).setAction(ACTION),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static boolean reconcile(Context c){try{synchronized(LOCK){try(SQLiteDatabase d=db(c)){
        recover(d);long next=Long.MAX_VALUE;for(SavedAccounts.Login login:new CredentialVault(c).list())try(Cursor row=d.rawQuery("SELECT min(next) FROM queue WHERE account=? AND state='pending'",new String[]{NoticeCenter.scope(login)})){if(row.moveToFirst()&&!row.isNull(0))next=Math.min(next,row.getLong(0));}
        AlarmManager alarms=c.getSystemService(AlarmManager.class);alarms.cancel(alarm(c));JobScheduler jobs=c.getSystemService(JobScheduler.class);
        if(next==Long.MAX_VALUE){if(!running.get())jobs.cancel(JOB);return true;}
        long now=System.currentTimeMillis();if(next>now)alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,alarm(c));
        if(running.get())return true;
        return jobs.schedule(new JobInfo.Builder(JOB,new ComponentName(c,ScheduledSendJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(Math.max(0,next-now)).setBackoffCriteria(300000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).setPersisted(true).build())==JobScheduler.RESULT_SUCCESS;
    }}}catch(Exception unavailable){/* Keep persisted queue; retried on resume/boot/change. */return false;}}
    static boolean run(Context c,BooleanSupplier stopped){
        if(!running.compareAndSet(false,true))return true;
        boolean retry=false;try{
            for(SavedAccounts.Login login:new CredentialVault(c).list()){
                if(stopped.getAsBoolean())break;String account=NoticeCenter.scope(login);List<String> ids=new ArrayList<>();
                synchronized(LOCK){try(SQLiteDatabase d=db(c)){recover(d);try(Cursor rows=d.rawQuery("SELECT id FROM queue WHERE account=? AND state='pending' AND next<=? ORDER BY due LIMIT 10",new String[]{account,Long.toString(System.currentTimeMillis())})){while(rows.moveToNext())ids.add(rows.getString(0));}}}
                for(String id:ids){if(stopped.getAsBoolean())break;deliver(c,login,id,stopped);}
            }
        }catch(Exception unavailable){retry=true;}finally{running.set(false);}return retry;
    }
    private static void deliver(Context c,SavedAccounts.Login login,String id,BooleanSupplier stopped)throws Exception{
        String account=NoticeCenter.scope(login);JSONObject p;int attempts;
        synchronized(LOCK){try(SQLiteDatabase d=db(c);Cursor row=d.rawQuery("SELECT state,payload,attempts,next FROM queue WHERE id=? AND account=?",new String[]{id,account})){
            if(!row.moveToFirst()||!ScheduledPolicy.ready(row.getString(0),row.getLong(3),System.currentTimeMillis()))return;try{p=decode(account,id,row.getBlob(1));}catch(Exception unreadable){d.update("queue",values("failed",0),"id=?",new String[]{id});NoticeCenter.scheduled(c,login,id,"failed","Не удалось прочитать сохранённую копию. Она не удалена.");return;}attempts=row.getInt(2)+1;
            ContentValues v=values("preparing",0);v.put("attempts",attempts);if(d.update("queue",v,"id=? AND state='pending'",new String[]{id})!=1)return;
        }active.add(id);}
        try{
            SavedAccounts.Login current=null;for(SavedAccounts.Login saved:new CredentialVault(c).list())if(saved.identity().equals(login.identity()))current=saved;
            if(current==null||stopped.getAsBoolean())throw new IOException("Account unavailable or stopped");final SavedAccounts.Login sender=new SavedAccounts.Login(current.host,current.port,current.user,current.password,current.smtpHost,current.smtpPort,current.smtpSsl,p.optString("from",current.from));
            javax.mail.internet.MimeMessage message=MailSender.send(sender,p.getJSONObject("draft"),files(c,account,id),null,()->{
                synchronized(LOCK){try(SQLiteDatabase d=db(c)){
                    if(stopped.getAsBoolean())throw new IOException("Stopped before SMTP DATA");boolean remembered=false;for(SavedAccounts.Login saved:new CredentialVault(c).list())if(saved.identity().equals(sender.identity()))remembered=true;if(!remembered)throw new IOException("Account removed");
                    if(d.update("queue",values("transmitting",0),"id=? AND state='preparing'",new String[]{id})!=1)throw new IOException("Schedule cancelled");
                }}
            });
            // SMTP ACK comes first; sent is durable before any IMAP copy or file cleanup.
            synchronized(LOCK){try(SQLiteDatabase d=db(c)){JSONObject summary=new JSONObject().put("to",p.optString("to")).put("subject",p.optString("subject")).put("sentAt",System.currentTimeMillis());ContentValues v=values("sent",0);v.put("payload",encode(account,id,summary));if(d.update("queue",v,"id=? AND state='transmitting'",new String[]{id})!=1)throw new IOException("Cannot record SMTP ACK");}}
            String warning="";try(MailReader reader=new MailReader()){reader.connect(sender.host,sender.port,sender.user,sender.password);reader.saveSent(message);String key=c.getSharedPreferences("draft-keys-"+account,Context.MODE_PRIVATE).getString(p.getJSONObject("draft").optString("id"),null);if(key!=null)ServerDrafts.remove(reader.draftStore(),key);}catch(Exception retained){warning="Письмо отправлено. Копия в «Отправленных» или удаление серверного черновика не подтверждены. Не отправляйте повторно.";}
            if(!warning.isEmpty())synchronized(LOCK){try(SQLiteDatabase d=db(c)){JSONObject summary=new JSONObject().put("to",p.optString("to")).put("subject",p.optString("subject")).put("sentAt",System.currentTimeMillis()).put("warning",warning);ContentValues v=new ContentValues();v.put("payload",encode(account,id,summary));d.update("queue",v,"id=? AND state='sent'",new String[]{id});}}
            deleteFiles(files(c,account,id));NoticeCenter.scheduled(c,sender,id,"sent",p.optString("subject"));
        }catch(Exception error){synchronized(LOCK){try(SQLiteDatabase d=db(c);Cursor row=d.rawQuery("SELECT state FROM queue WHERE id=?",new String[]{id})){
            if(row.moveToFirst()){String state=row.getString(0);if(state.equals("preparing")||state.equals("transmitting")){String next=ScheduledPolicy.failure(state,error,attempts);d.update("queue",values(next,next.equals("pending")?ScheduledPolicy.retryAt(System.currentTimeMillis(),attempts):0),"id=?",new String[]{id});if(!next.equals("pending"))NoticeCenter.scheduled(c,login,id,next,p.optString("subject"));}}
        }}}finally{synchronized(LOCK){active.remove(id);}}
    }
}

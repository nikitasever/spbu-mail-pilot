package ru.local.spbumailpilot;
import android.content.Context;
import android.security.keystore.*;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

final class OfflineMailStore {
    private static final java.util.Map<String,Long> epochs=new java.util.HashMap<>();
    private long epoch;
    private static final String ALIAS="spbu-mail-offline-v1";
    private final AtomicFile file;private final String owner;private OfflineMailbox mailbox;
    OfflineMailStore(Context context,SavedAccounts.Login login)throws Exception{
        owner=NoticeCenter.scope(login);file=new AtomicFile(path(context,owner));
        synchronized(OfflineMailStore.class){
            epoch=epochs.getOrDefault(owner,0L);epochs.put(owner,epoch);mailbox=new OfflineMailbox();
            File snapshot=committedSnapshot(file.getBaseFile());
            if(snapshot.exists())try{if(snapshot.length()>6*1024*1024)throw new IOException("Cache limit");byte[] encrypted=file.readFully();if(encrypted.length>6*1024*1024)throw new IOException("Cache limit");byte[] plain=crypt(false,key(false),owner,encrypted);try{JSONObject envelope=new JSONObject(new String(plain,StandardCharsets.UTF_8));if(!owner.equals(envelope.getString("account")))throw new IOException("Cache owner");mailbox=new OfflineMailbox(envelope.getJSONObject("mail"));}finally{Arrays.fill(plain,(byte)0);}}catch(Exception invalid){mailbox=new OfflineMailbox();}
        }
    }
    static File committedSnapshot(File base){File backup=new File(base.getPath()+".bak");return backup.exists()?backup:base;}

    private static File path(Context context,String owner){return new File(context.getNoBackupFilesDir(),"offline-"+owner+".bin");}
    static synchronized void delete(Context context,SavedAccounts.Login login)throws Exception{String owner=NoticeCenter.scope(login);epochs.put(owner,epochs.getOrDefault(owner,0L)+1);AtomicFile file=new AtomicFile(path(context,owner));file.delete();if(file.getBaseFile().exists())throw new IOException("Cannot clear cache");}
    static synchronized void deleteAll(Context context){for(String owner:new java.util.ArrayList<>(epochs.keySet()))epochs.put(owner,epochs.get(owner)+1);File[] files=context.getNoBackupFilesDir().listFiles();if(files!=null)for(File file:files)if(file.getName().startsWith("offline-account-"))new AtomicFile(file).delete();}
    private static synchronized javax.crypto.SecretKey key(boolean create)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))return (javax.crypto.SecretKey)store.getKey(ALIAS,null);if(!create)throw new IOException("Cache key");KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();
    }
    static byte[] crypt(boolean encrypt,javax.crypto.SecretKey key,String owner,byte[] value)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");byte[] aad=("ru.local.spbumailpilot/offline/v1/"+owner).getBytes(StandardCharsets.UTF_8);
        if(encrypt){cipher.init(Cipher.ENCRYPT_MODE,key);cipher.updateAAD(aad);byte[] iv=cipher.getIV(),body=cipher.doFinal(value),packet=new byte[13+body.length];if(iv.length!=12)throw new IOException("IV");packet[0]=1;System.arraycopy(iv,0,packet,1,12);System.arraycopy(body,0,packet,13,body.length);return packet;}
        if(value.length<29||value[0]!=1)throw new IOException("Cache packet");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOfRange(value,1,13)));cipher.updateAAD(aad);return cipher.doFinal(value,13,value.length-13);
    }
    private void persist()throws Exception{synchronized(OfflineMailStore.class){if(epoch!=epochs.getOrDefault(owner,0L))throw new IOException("Cache invalidated");byte[] plain=new JSONObject().put("account",owner).put("mail",mailbox.data).toString().getBytes(StandardCharsets.UTF_8);FileOutputStream output=null;try{byte[] encrypted=crypt(true,key(true),owner,plain);output=file.startWrite();output.write(encrypted);file.finishWrite(output);}catch(Exception error){if(output!=null)file.failWrite(output);throw error;}finally{Arrays.fill(plain,(byte)0);}}}
    synchronized boolean available(){return mailbox.available();}
    synchronized JSONObject folders()throws Exception{return new JSONObject().put("folders",mailbox.folders()).put("offline",true);}
    synchronized JSONObject page(String folder,int offset,String snapshot,String query)throws Exception{return mailbox.page(folder,offset,snapshot,query);}
    synchronized JSONObject body(String folder,String uid,String validity)throws Exception{return mailbox.body(folder,uid,validity);}
    synchronized void savePage(String folder,int offset,JSONObject result)throws Exception{OfflineMailbox next=new OfflineMailbox(mailbox.data);next.page(folder,offset,result);OfflineMailbox previous=mailbox;mailbox=next;try{persist();}catch(Exception failed){mailbox=previous;throw failed;}}
    synchronized long seenRevision(String folder,String uid,String validity)throws Exception{return mailbox.seenRevision(folder,uid,validity);}
    synchronized void savePrefetchedBody(String folder,String uid,String validity,JSONObject result,long revision,java.util.function.BooleanSupplier allowed)throws Exception{synchronized(OfflineMailStore.class){if(!allowed.getAsBoolean())throw new IOException("Preload cancelled");OfflineMailbox next=new OfflineMailbox(mailbox.data);next.prefetchedBody(folder,uid,validity,result,revision);OfflineMailbox previous=mailbox;mailbox=next;try{persist();}catch(Exception failed){mailbox=previous;throw failed;}}}
    synchronized void saveBody(String folder,String uid,String validity,JSONObject result)throws Exception{OfflineMailbox next=new OfflineMailbox(mailbox.data);next.body(folder,uid,validity,result);OfflineMailbox previous=mailbox;mailbox=next;try{persist();}catch(Exception failed){mailbox=previous;throw failed;}}
    synchronized void seen(String folder,String uid,String validity,boolean value)throws Exception{OfflineMailbox next=new OfflineMailbox(mailbox.data);next.seen(folder,uid,validity,value);OfflineMailbox previous=mailbox;mailbox=next;try{persist();}catch(Exception failed){mailbox=previous;throw failed;}}
    synchronized void clear()throws Exception{synchronized(OfflineMailStore.class){epochs.put(owner,epochs.getOrDefault(owner,0L)+1);file.delete();if(file.getBaseFile().exists())throw new IOException("Cannot clear cache");mailbox=new OfflineMailbox();}}
}

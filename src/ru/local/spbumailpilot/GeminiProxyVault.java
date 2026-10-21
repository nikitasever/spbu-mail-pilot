package ru.local.spbumailpilot;
import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
final class GeminiProxyVault {
 private static final String ALIAS="spbu-mail-gemini-proxy-v1";
 private final SharedPreferences prefs;
 GeminiProxyVault(Context context){prefs=context.getSharedPreferences("gemini-proxy-secret",Context.MODE_PRIVATE);}
    private SecretKey key(boolean create) throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS)) return (SecretKey)store.getKey(ALIAS,null);
        if(!create) throw new IllegalStateException("Session key unavailable");
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }

 private static long revision;
 static final class Snapshot {
    final String value;private final long version;
    Snapshot(String value,long version){this.value=value;this.version=version;}
    boolean current(){synchronized(GeminiProxyVault.class){return version==revision;}}
    @Override public String toString(){return "GeminiProxyVault.Snapshot(redacted)";}
 }
 boolean configured(){synchronized(GeminiProxyVault.class){return prefs.contains("encrypted");}}
 long version(){synchronized(GeminiProxyVault.class){return revision;}}
 Snapshot snapshot()throws Exception{synchronized(GeminiProxyVault.class){return new Snapshot(load(),revision);}}
 String load()throws Exception{synchronized(GeminiProxyVault.class){String value=prefs.getString("encrypted",null);if(value==null)return "";byte[] plain=SessionSecret.decrypt(key(false),Base64.decode(value,Base64.NO_WRAP));try{return new String(plain,StandardCharsets.UTF_8);}finally{Arrays.fill(plain,(byte)0);}}}
 void save(String value)throws Exception{save(value,-1);}
 void save(String value,long expected)throws Exception{synchronized(GeminiProxyVault.class){
    if(expected>=0&&expected!=revision)throw new IllegalStateException("AI configuration changed");
    if(!GeminiProxyTransport.validPassword(value))throw new IllegalArgumentException("Invalid proxy password");++revision;
    byte[] plain=value.getBytes(StandardCharsets.UTF_8);
    try{String packet=Base64.encodeToString(SessionSecret.encrypt(key(true),plain),Base64.NO_WRAP);if(!prefs.edit().putString("encrypted",packet).commit())throw new IllegalStateException();}
    finally{Arrays.fill(plain,(byte)0);}
 }}
 void remove()throws Exception{remove(-1);}
 void remove(long expected)throws Exception{synchronized(GeminiProxyVault.class){
    if(expected>=0&&expected!=revision)throw new IllegalStateException("AI configuration changed");
    ++revision;if(!prefs.edit().clear().commit())throw new IllegalStateException();
    KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))store.deleteEntry(ALIAS);
 }}
}

package ru.local.spbumailpilot;
import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
final class AiVault {
 private static final String ALIAS="spbu-mail-gemini-v1";
 private final SharedPreferences prefs;
 AiVault(Context context){prefs=context.getSharedPreferences("gemini-secret",Context.MODE_PRIVATE);}
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

 synchronized boolean configured(){return prefs.contains("encrypted");}
 synchronized String load()throws Exception{String value=prefs.getString("encrypted",null);if(value==null)return "";byte[] plain=SessionSecret.decrypt(key(false),Base64.decode(value,Base64.NO_WRAP));try{return new String(plain,StandardCharsets.UTF_8);}finally{Arrays.fill(plain,(byte)0);}}
 synchronized void save(String value)throws Exception{value=value.trim();if(value.isEmpty()||value.length()>512||!value.matches("[A-Za-z0-9_.-]+"))throw new IllegalArgumentException();byte[] plain=value.getBytes(StandardCharsets.UTF_8);try{String packet=Base64.encodeToString(SessionSecret.encrypt(key(true),plain),Base64.NO_WRAP);if(!prefs.edit().putString("encrypted",packet).commit())throw new IllegalStateException();}finally{Arrays.fill(plain,(byte)0);}}
 synchronized void remove()throws Exception{if(!prefs.edit().clear().commit())throw new IllegalStateException();KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))store.deleteEntry(ALIAS);}
}

package ru.local.spbumailpilot;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;

final class CredentialVault {
    private static final String ALIAS = "spbu-mail-session-v1";
    private final SharedPreferences prefs;
    CredentialVault(Context context) { prefs=context.getSharedPreferences("remembered-login",Context.MODE_PRIVATE); }
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
    static String encode(SavedAccounts accounts)throws Exception{
        JSONArray entries=new JSONArray();
        for(SavedAccounts.Login login:accounts.list())entries.put(new JSONObject().put("host",login.host).put("port",login.port).put("user",login.user).put("password",login.password).put("smtpHost",login.smtpHost).put("smtpPort",login.smtpPort).put("smtpSsl",login.smtpSsl).put("from",login.from));
        return new JSONObject().put("schema",2).put("active",accounts.activeId()).put("accounts",entries).toString();
    }
    static SavedAccounts decode(String value)throws Exception{
        JSONObject json=new JSONObject(value);SavedAccounts accounts=new SavedAccounts();
        if(!json.has("schema")){
            accounts.save(login(json)); // Migrate a single remembered login from version 0.3.
        }else{
            if(json.getInt("schema")!=2)throw new IllegalStateException("Unknown account storage schema");
            JSONArray entries=json.getJSONArray("accounts");
            for(int i=0;i<entries.length();i++)accounts.put(login(entries.getJSONObject(i)));
            accounts.setActive(json.getString("active"));
        }
        return accounts;
    }
    private void write(SavedAccounts accounts) throws Exception {
        byte[] plain=encode(accounts).getBytes(StandardCharsets.UTF_8);
        try {
            String encrypted=Base64.encodeToString(SessionSecret.encrypt(key(true),plain),Base64.NO_WRAP);
            if(!prefs.edit().putString("encrypted",encrypted).commit()) throw new IllegalStateException("Cannot persist login");
        } finally {Arrays.fill(plain,(byte)0);}
    }
    private SavedAccounts read() throws Exception {
        String encrypted=prefs.getString("encrypted",null);
        SavedAccounts accounts=new SavedAccounts();
        if(encrypted==null)return accounts;
        byte[] plain=SessionSecret.decrypt(key(false),Base64.decode(encrypted,Base64.NO_WRAP));
        try {
            return decode(new String(plain,StandardCharsets.UTF_8));
        } finally {Arrays.fill(plain,(byte)0);}
    }
    private static SavedAccounts.Login login(JSONObject json)throws Exception{SavedAccounts.Login defaults=new SavedAccounts.Login(json.getString("host"),json.getInt("port"),json.getString("user"),json.getString("password"));return new SavedAccounts.Login(defaults.host,defaults.port,defaults.user,defaults.password,json.optString("smtpHost",defaults.smtpHost),json.optInt("smtpPort",defaults.smtpPort),json.optBoolean("smtpSsl",defaults.smtpSsl),json.optString("from",defaults.from));}
    void save(SavedAccounts.Login login)throws Exception{SavedAccounts accounts=read();accounts.save(login);write(accounts);}
    SavedAccounts.Login load()throws Exception{return read().active();}
    ArrayList<SavedAccounts.Login> list()throws Exception{return read().list();}
    void deactivate()throws Exception{SavedAccounts accounts=read();accounts.deactivate();write(accounts);}
    void remove(SavedAccounts.Login login)throws Exception{SavedAccounts accounts=read();accounts.remove(login);if(accounts.list().isEmpty())clear();else write(accounts);}
    void clear() throws Exception {
        if(!prefs.edit().clear().commit())throw new IllegalStateException("Cannot remove login");
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))store.deleteEntry(ALIAS);
    }
}

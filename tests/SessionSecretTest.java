package ru.local.spbumailpilot;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class SessionSecretTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(SecretKey key,byte[] packet)throws Exception{
        try{SessionSecret.decrypt(key,packet);throw new AssertionError("Tampered packet accepted");}catch(java.security.GeneralSecurityException|IllegalArgumentException expected){}
    }
    public static void main(String[] args)throws Exception{
        KeyGenerator generator=KeyGenerator.getInstance("AES");generator.init(256);
        SecretKey key=generator.generateKey();
        byte[] plain="test-only login: пароль с символами !&< >".getBytes(StandardCharsets.UTF_8);
        byte[] first=SessionSecret.encrypt(key,plain),second=SessionSecret.encrypt(key,plain);
        check(Arrays.equals(plain,SessionSecret.decrypt(key,first)));
        check(Arrays.equals(plain,SessionSecret.decrypt(key,second)));
        check(!Arrays.equals(Arrays.copyOfRange(first,1,13),Arrays.copyOfRange(second,1,13)));
        check(!new String(first,StandardCharsets.UTF_8).contains("test-only login"));
        byte[] tampered=first.clone();tampered[20]^=1;reject(key,tampered);
        tampered=first.clone();tampered[1]^=1;reject(key,tampered);
        tampered=first.clone();tampered[tampered.length-1]^=1;reject(key,tampered);
        reject(generator.generateKey(),first);reject(key,Arrays.copyOf(first,20));
        tampered=first.clone();tampered[0]=2;reject(key,tampered);
        check(SessionSecret.decrypt(key,SessionSecret.encrypt(key,new byte[0])).length==0);
        System.out.println("PASS: encrypted credentials round-trip, unique nonces, modified ciphertext/nonce/tag rejected, wrong key/truncation/version rejected.");
    }
}

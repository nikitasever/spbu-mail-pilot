package ru.local.spbumailpilot;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Authenticated encryption; only the Android Keystore owns the persistent key. */
final class SessionSecret {
    private static final byte[] CONTEXT = "ru.local.spbumailpilot/credentials/v1".getBytes(StandardCharsets.UTF_8);
    static byte[] encrypt(SecretKey key, byte[] plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        cipher.updateAAD(CONTEXT);
        byte[] iv = cipher.getIV(), encrypted = cipher.doFinal(plain);
        if (iv.length != 12) throw new IllegalStateException("Invalid GCM nonce");
        byte[] packet = new byte[1 + iv.length + encrypted.length];
        packet[0] = 1;
        System.arraycopy(iv,0,packet,1,iv.length);
        System.arraycopy(encrypted,0,packet,13,encrypted.length);
        return packet;
    }
    static byte[] decrypt(SecretKey key, byte[] packet) throws Exception {
        if (packet.length < 29 || packet[0] != 1) throw new IllegalArgumentException("Invalid credentials packet");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOfRange(packet,1,13)));
        cipher.updateAAD(CONTEXT);
        return cipher.doFinal(packet,13,packet.length-13);
    }
}

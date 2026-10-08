package ru.local.spbumailpilot;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

public class AccountMigrationTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        SavedAccounts old=CredentialVault.decode("{\"host\":\"mail.spbu.ru\",\"port\":993,\"user\":\"first@example.org\",\"password\":\"legacy-test-password\"}");
        check(old.list().size()==1&&old.active().password.equals("legacy-test-password"));
        old.save(new SavedAccounts.Login("mail.spbu.ru",993,"second@example.org","новый тестовый пароль &<>"));
        KeyGenerator generator=KeyGenerator.getInstance("AES");generator.init(256);SecretKey key=generator.generateKey();
        byte[] encrypted=SessionSecret.encrypt(key,CredentialVault.encode(old).getBytes(StandardCharsets.UTF_8));
        SavedAccounts restored=CredentialVault.decode(new String(SessionSecret.decrypt(key,encrypted),StandardCharsets.UTF_8));
        check(restored.list().size()==2&&restored.active().user.equals("second@example.org"));
        check(restored.list().get(0).password.equals("legacy-test-password"));
        check(restored.active().password.equals("новый тестовый пароль &<>"));
        restored.deactivate();restored=CredentialVault.decode(CredentialVault.encode(restored));check(restored.active()==null&&restored.list().size()==2);
        restored.remove(restored.list().get(0));restored=CredentialVault.decode(CredentialVault.encode(restored));check(restored.list().size()==1&&restored.active()==null);
        SavedAccounts.Login previous=restored.list().get(0);restored.save(new SavedAccounts.Login(previous.host,previous.port,previous.user,previous.password,"smtp.example.invalid",587,false,"sender@example.invalid"));
        restored=CredentialVault.decode(CredentialVault.encode(restored));check(restored.active().smtpHost.equals("smtp.example.invalid")&&restored.active().smtpPort==587&&!restored.active().smtpSsl&&restored.active().from.equals("sender@example.invalid"));
        try{CredentialVault.decode("{\"schema\":99}");throw new AssertionError();}catch(IllegalStateException expected){}
        System.out.println("PASS: legacy 0.3 migration, encrypted multi-account round-trip, password preservation, logout across restart and single-account deletion.");
    }
}

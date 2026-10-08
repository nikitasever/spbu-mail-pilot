package ru.local.spbumailpilot;

import java.util.Base64;
import java.nio.charset.StandardCharsets;
import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;

public final class LoginFailureTest {
    static void check(boolean condition){if(!condition)throw new AssertionError();}
    public static void main(String[] args){
        String user="test@example.org",password="fixture-secret-123";
        String encoded=Base64.getEncoder().encodeToString(("\0"+user+"\0"+password).getBytes(StandardCharsets.UTF_8));
        AuthenticationFailedException denied=new AuthenticationFailedException("[AUTHENTICATIONFAILED] Application password required "+user+" "+password+" "+encoded);
        String result=LoginFailure.describe(denied,"imap.mail.ru",user,password);
        check(result.contains("AUTHENTICATIONFAILED")&&result.contains("Application password required"));
        check(!result.contains(user)&&!result.contains(password)&&!result.contains(encoded));
        MessagingException wrapped=new MessagingException("Connection failed",denied);
        check(LoginFailure.describe(wrapped,"imap.mail.ru",user,password).contains("Сервер отклонил авторизацию"));
        check(!LoginFailure.describe(new java.net.SocketTimeoutException("Read timed out"),"mail.spbu.ru",user,password).contains("отклонил авторизацию"));
        check(LoginFailure.describe(new Exception("x".repeat(2000)),"imap.mail.ru",user,password).length()<1000);
        System.out.println("LoginFailureTest passed");
    }
}

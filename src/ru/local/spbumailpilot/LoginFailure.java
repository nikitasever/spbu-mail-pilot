package ru.local.spbumailpilot;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.IdentityHashMap;
import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;

/** Local, bounded diagnostics. Never log an authentication exchange. */
final class LoginFailure {
    static String describe(Throwable error,String host,String user,String password){
        StringBuilder details=new StringBuilder();boolean auth=false;
        IdentityHashMap<Throwable,Boolean> visited=new IdentityHashMap<>();
        for(Throwable current=error;current!=null&&visited.size()<8&&!visited.containsKey(current);){
            visited.put(current,true);auth|=current instanceof AuthenticationFailedException;
            String message=current.getMessage();
            if(message!=null&&!message.isEmpty()){
                String safe=redact(message,user,password);
                if(details.indexOf(safe)<0){if(details.length()>0)details.append("\n");details.append(safe);}
            }
            Throwable next=current.getCause();
            if(next==null&&current instanceof MessagingException)next=((MessagingException)current).getNextException();
            current=next;
        }
        String heading=auth?"Сервер отклонил авторизацию.":"Не удалось подключить почту.";
        String advice=auth&&"imap.mail.ru".equalsIgnoreCase(host)?"\nДля Mail.ru нужны полный адрес и пароль внешнего приложения с полным доступом к Почте. Пароль только для Календаря не подходит. Если эти условия выполнены, сообщите текст ответа ниже.":"";
        String response=details.length()==0?"Сервер не сообщил подробностей.":details.substring(0,Math.min(600,details.length()));
        return heading+advice+"\n\nОтвет подключения:\n"+response;
    }
    private static String redact(String message,String user,String password){
        String safe=message;
        String[] secrets={"\0"+user+"\0"+password,user+"\0"+user+"\0"+password,password,user};
        for(String secret:secrets)if(secret!=null&&!secret.isEmpty()){
            safe=safe.replace(secret,"[скрыто]");
            safe=safe.replace(Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8)),"[скрыто]");
        }
        safe=safe.replaceAll("[A-Za-z0-9+/=_-]{32,}","[скрыто]");
        safe=safe.replaceAll("[^\\s<>@]+@[^\\s<>@]+","[адрес скрыт]");
        return safe.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]"," ");
    }
}

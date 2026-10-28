package ru.local.spbumailpilot;

import org.json.*;

/** Explicit text-only forward. Incoming recipient lists and thread headers are never copied. */
final class NativeForward {
    static final class Invalid extends Exception {
        Invalid(String message){super(message);}
    }
    static NativeDraft create(String owner,JSONObject source,String text,String signature)throws Exception {
        if(source==null||text==null||text.trim().isEmpty())
            throw new Invalid("В письме нет текста для пересылки. Для пересылки исходного файла письма используйте «Все возможности почты».");
        if(text.contains("[Показана часть текста]"))
            throw new Invalid("Загружен только фрагмент письма. Чтобы не отправить неполный текст, используйте пересылку в «Все возможности почты».");
        String original=source.optString("subject"),subject=subject(original);
        String sender=line(source.optString("sender")),email=line(source.optString("email")),date=line(source.optString("date"));
        String from=sender.isEmpty()?email:email.isEmpty()||sender.equalsIgnoreCase(email)?sender:sender+" <"+email+">";
        String header="---------- Пересланное письмо ----------\n"+
            (from.isEmpty()?"":"От: "+from+"\n")+(date.isEmpty()?"":"Дата: "+date+"\n")+"Тема: "+line(original)+"\n\n";
        if(signature==null)signature="";
        if(subject.length()>500||(long)signature.length()+2+header.length()+text.length()>100000)
            throw new Invalid("Пересылка с заголовком превышает лимит темы или текста. Для полного письма используйте «Все возможности почты».");
        NativeDraft empty=NativeDraft.create(owner,null,"");
        JSONObject mail=new JSONObject(empty.mail.toString()).put("kind","forward-text").put("subject",subject)
            .put("body",signature+"\n\n"+header+text);
        return new NativeDraft(owner,NativeDraft.EDITING,mail);
    }
    static String subject(String original)throws Invalid{
        if(original.indexOf('\r')>=0||original.indexOf('\n')>=0)throw new Invalid("В теме письма есть недопустимый перенос строки.");
        String subject=original.trim();if(subject.isEmpty())subject="Без темы";
        if(!subject.regionMatches(true,0,"Fwd:",0,4)&&!subject.regionMatches(true,0,"FW:",0,3))subject="Fwd: "+subject;
        if(subject.length()>500)throw new Invalid("Тема пересылки превышает 500 символов.");return subject;
    }
    private static String line(String value){
        // Metadata is displayed as plain text on one line; controls cannot forge another header row.
        return value.replaceAll("[\\p{Cntrl}\\u2028\\u2029]"," ").trim();
    }
}

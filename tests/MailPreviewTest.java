package ru.local.spbumailpilot;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;
public class MailPreviewTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        MimeBodyPart plain=new MimeBodyPart();plain.setText("Hello\n  world 😀","UTF-8");plain.setHeader("Content-Type","text/plain; charset=UTF-8");
        check(MailPreview.read(plain).equals("Hello world 😀"));
        MimeBodyPart attachment=new MimeBodyPart(){public InputStream getInputStream(){throw new AssertionError("Attachment downloaded");}};
        attachment.setText("secret");attachment.setDisposition(Part.ATTACHMENT);
        check(MailPreview.read(attachment).isEmpty());
        MimeBodyPart named=new MimeBodyPart(){public InputStream getInputStream(){throw new AssertionError("Named text file downloaded");}};
        named.setText("secret");named.setFileName("notes.txt");check(MailPreview.read(named).isEmpty());
        MimeMultipart multi=new MimeMultipart("mixed");multi.addBodyPart(attachment);multi.addBodyPart(plain);MimeBodyPart parent=new MimeBodyPart();parent.setContent(multi);parent.setHeader("Content-Type",multi.getContentType());
        check(MailPreview.read(parent).equals("Hello world 😀"));
        MimeBodyPart huge=new MimeBodyPart();huge.setText(new String(new char[10000]).replace('\0','x'),"UTF-8");check(MailPreview.read(huge).length()==180);
        check(MailPreview.clean(new String(new char[179]).replace('\0','x')+"😀z").length()==179);
        check(MailPreview.clean("abc\n[Показана часть текста]").equals("abc"));
        System.out.println("PASS: MIME preview skips attachments, bounded prefixes, whitespace normalization and Unicode boundaries.");
    }
}

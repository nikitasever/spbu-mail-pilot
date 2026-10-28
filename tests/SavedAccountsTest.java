package ru.local.spbumailpilot;

public class SavedAccountsTest {
    static void check(boolean condition){if(!condition)throw new AssertionError();}
    public static void main(String[] args){
        SavedAccounts.Login mail=new SavedAccounts.Login("IMAP.MAIL.RU",993,"pilot-test@mail.ru","fake-app-password");
        check(mail.smtpHost.equals("smtp.mail.ru")&&mail.smtpPort==465&&mail.smtpSsl&&mail.from.equals("pilot-test@mail.ru"));
        SavedAccounts.Login custom=new SavedAccounts.Login("imap.mail.ru",993,"pilot-test@mail.ru","fake-app-password","custom.example.org",587,false,"alias@example.org");
        check(custom.smtpHost.equals("custom.example.org")&&custom.smtpPort==587&&!custom.smtpSsl);
        SavedAccounts book=new SavedAccounts();
        SavedAccounts.Login work=new SavedAccounts.Login("mail.spbu.ru",993,"work@example.org","test-password-a");
        SavedAccounts.Login other=new SavedAccounts.Login("mail.spbu.ru",993,"other@example.org","test-password-b");
        book.save(work);book.save(other);check(book.list().size()==2&&book.active()==other);
        book.deactivate();check(book.active()==null&&book.list().size()==2);
        book.save(work);check(book.active()==work&&book.list().get(1).password.equals("test-password-b"));
        SavedAccounts.Login updated=new SavedAccounts.Login("MAIL.SPBU.RU",993,"WORK@EXAMPLE.ORG","replacement-test-password");
        book.save(updated);check(book.list().size()==2&&book.active()==updated);
        SavedAccounts.Login server=new SavedAccounts.Login("different.example.org",993,"work@example.org","server-specific-test-password");
        SavedAccounts.Login port=new SavedAccounts.Login("mail.spbu.ru",1993,"work@example.org","port-specific-test-password");
        book.save(server);book.save(port);check(book.list().size()==4);
        book.remove(other);check(book.list().size()==3&&book.active()==port);
        book.remove(port);check(book.active()==null&&book.list().size()==2);
        book.setActive(updated.identity());check(book.active()==updated);
        book.setActive("unknown account");check(book.active()==null&&book.list().size()==2);
        // Reconstruct persisted entries without selecting any after an explicit logout.
        SavedAccounts restored=new SavedAccounts();for(SavedAccounts.Login login:book.list())restored.put(login);
        restored.setActive(book.activeId());check(restored.active()==null&&restored.list().size()==2);
        restored.save(server);restored.remove(updated);check(restored.active()==server&&restored.list().size()==1);
        restored.remove(server);check(restored.active()==null&&restored.list().isEmpty());

        // Atomic password update on inactive account B while A remains active, preserving B's custom SMTP.
        SavedAccounts casBook=new SavedAccounts();
        SavedAccounts.Login activeA=new SavedAccounts.Login("mail.spbu.ru",993,"active-a@spbu.ru","pass-a-1");
        SavedAccounts.Login inactiveB=new SavedAccounts.Login("mail.spbu.ru",993,"inactive-b@spbu.ru","pass-b-1","smtp.custom.invalid",587,false,"alias-b@spbu.ru");
        casBook.save(inactiveB);casBook.save(activeA);
        check(casBook.active()==activeA&&casBook.activeId().equals(activeA.identity()));
        check(casBook.get(inactiveB.identity())==inactiveB);
        casBook.updatePassword(inactiveB,"pass-b-2");
        check(casBook.active()==activeA&&casBook.activeId().equals(activeA.identity()));
        SavedAccounts.Login updatedB=casBook.get(inactiveB.identity());
        check(updatedB!=null&&updatedB.password.equals("pass-b-2")&&updatedB.smtpHost.equals("smtp.custom.invalid")&&updatedB.smtpPort==587&&!updatedB.smtpSsl&&updatedB.from.equals("alias-b@spbu.ru"));
        // Reject stale expected login (old password or modified SMTP) and invalid new passwords.
        try{casBook.updatePassword(inactiveB,"pass-b-3");throw new AssertionError();}catch(IllegalStateException expected){}
        SavedAccounts.Login wrongSmtp=new SavedAccounts.Login("mail.spbu.ru",993,"inactive-b@spbu.ru","pass-b-2","other.smtp.invalid",587,false,"alias-b@spbu.ru");
        try{casBook.updatePassword(wrongSmtp,"pass-b-3");throw new AssertionError();}catch(IllegalStateException expected){}
        try{casBook.updatePassword(updatedB,"");throw new AssertionError();}catch(IllegalArgumentException expected){}
        try{casBook.updatePassword(updatedB,"bad\0pass");throw new AssertionError();}catch(IllegalArgumentException expected){}
        System.out.println("PASS: multiple accounts, logout preserves passwords, switching, credential replacement, atomic password CAS without changing activeId, server/port isolation, deletion and active-account restore.");
    }
}

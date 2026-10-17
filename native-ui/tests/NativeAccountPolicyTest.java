package ru.local.spbumailpilot;
public final class NativeAccountPolicyTest {
    interface Attempt {void run()throws Exception;}
    static void check(boolean b){if(!b)throw new AssertionError();}
    static void reject(Attempt a)throws Exception{try{a.run();}catch(Exception good){return;}throw new AssertionError("Expected rejection");}
    public static void main(String[] args)throws Exception {
        SavedAccounts.Login a=NativeAccountPolicy.login(" mail.spbu.ru ","993"," user ","fixture-only-password");
        check(a.host.equals("mail.spbu.ru")&&a.user.equals("user")&&a.port==993&&a.smtpSsl);
        check(a.from.equals("user@spbu.ru"));
        SavedAccounts.Login b=NativeAccountPolicy.smtp(a,"smtp.example.org","587",false,"alias@example.org");
        check(a.identity().equals(b.identity())&&a.password.equals(b.password)&&!b.smtpSsl&&b.smtpPort==587);
        check(!NativeAccountPolicy.same(a,b));
        check(NativeAccountPolicy.same(b,NativeAccountPolicy.smtp(a,"smtp.example.org","587",false,"alias@example.org")));
        check(NativeAccountPolicy.same(null,null)&&!NativeAccountPolicy.same(a,null));
        for(String host:new String[]{"https://mail.spbu.ru","mail.spbu.ru/x","mail..ru","mail.-ru","mail.ru\r\nX","bad@host","-mail.ru","mail_.ru"})reject(()->NativeAccountPolicy.login(host,"993","user","p"));
        for(String port:new String[]{"0","65536","NaN","993\nX"})reject(()->NativeAccountPolicy.login("mail.spbu.ru",port,"user","p"));
        reject(()->NativeAccountPolicy.login("mail.spbu.ru","993","user\r\nX","p"));
        reject(()->NativeAccountPolicy.login("mail.spbu.ru","993","user",""));
        reject(()->NativeAccountPolicy.login("imap.mail.ru","993","shortname","p"));
        for(String from:new String[]{"Name <a@example.org>","a@example.org,b@example.org","a@example.org\r\nBcc: b@example.org","missing-domain","a@example.org (comment)"})reject(()->NativeAccountPolicy.smtp(a,"smtp.example.org","465",true,from));
        SavedAccounts book=new SavedAccounts();book.save(a);book.save(b);check(book.list().size()==1&&book.active()==b);
        book.deactivate();check(book.list().size()==1&&book.active()==null);
        System.out.println("NativeAccountPolicyTest PASS");
    }
}

package ru.local.spbumailpilot;
public final class NativeWritingAiTest {
    interface Attempt{void run()throws Exception;}
    static void check(boolean b){if(!b)throw new AssertionError();}
    static void reject(Attempt a)throws Exception{try{a.run();}catch(Exception expected){return;}throw new AssertionError("Expected rejection");}
    public static void main(String[] args)throws Exception {
        String body="До 😀\n<script>fixture</script> после",subject="Тема";
        String checked=NativeWritingAi.prompt("check",subject,body,"original-not-in-check");
        check(checked.contains(body)&&checked.contains("Проверь орфографию")&&!checked.contains("original-not-in-check"));
        String reply=NativeWritingAi.prompt("reply",subject,body,"Исходник");
        check(reply.contains("Исходник")&&reply.contains("Не выдумывай факты")&&reply.endsWith(body));
        check(NativeWritingAi.prompt("reply","","","").contains("Исходное письмо не включено"));
        reject(()->NativeWritingAi.prompt("check",""," \n","") );reject(()->NativeWritingAi.prompt("unknown","","x","") );
        reject(()->NativeWritingAi.prompt("check","x".repeat(501),"text","") );
        String longOriginal="x".repeat(11999)+"😀"+"x".repeat(2000);String longReply=NativeWritingAi.prompt("reply","x".repeat(500),"x".repeat(100000),longOriginal);
        check(longReply.length()<=120000&&longReply.contains("текст обрезан")&&!longReply.contains("\uD83D\n"));
        String edited="  Запрос после правки\n😀  ";check(NativeWritingAi.validate(edited).equals(edited));
        reject(()->NativeWritingAi.validate(" "));reject(()->NativeWritingAi.validate("x".repeat(120001)));
        System.out.println("PASS NativeWritingAi: legacy check/reply instructions, exact editable text, bounded/marked source context, Unicode cut, no draft rewrite or hidden source in check");
    }
}

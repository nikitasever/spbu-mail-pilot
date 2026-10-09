package ru.local.spbumailpilot;

final class NoticeWork {
    interface Work {void run()throws Exception;}
    static boolean perform(Work tasks,Work mail,java.util.function.BooleanSupplier stopped){
        boolean retry=false;
        if(stopped.getAsBoolean())return false;
        try{tasks.run();}catch(Exception unavailable){retry=true;}
        if(stopped.getAsBoolean())return false;
        try{mail.run();}catch(javax.mail.AuthenticationFailedException rejected){}catch(Exception unavailable){retry=true;}
        return retry&&!stopped.getAsBoolean();
    }
}

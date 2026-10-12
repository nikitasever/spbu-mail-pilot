package ru.local.spbumailpilot;

import java.util.function.BooleanSupplier;

/** One bad account must not prevent other remembered mailboxes from being checked. */
final class AccountMailChecks {
    interface Eligible<T> { boolean get(T account)throws Exception; }
    interface Check<T> { void run(T account)throws Exception; }
    static boolean enabled(boolean mailNotifications,boolean backgroundMail,boolean continuous){
        return mailNotifications&&(!continuous||backgroundMail);
    }
    static <T> void perform(Iterable<T> accounts,Eligible<T> eligible,Check<T> check,BooleanSupplier stopped)throws Exception{
        Exception failure=null;
        for(T account:accounts){
            if(stopped.getAsBoolean())return;
            try{if(eligible.get(account))check.run(account);}
            catch(Exception error){if(failure==null)failure=error;}
        }
        if(!stopped.getAsBoolean()&&failure!=null)throw failure;
    }
}

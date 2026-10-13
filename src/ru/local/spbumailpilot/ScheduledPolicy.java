package ru.local.spbumailpilot;
/** Durable phase changes define when SMTP may run; uncertain work is never retried automatically. */
final class ScheduledPolicy {
    static boolean ready(String state,long next,long now){return "pending".equals(state)&&next<=now;}
    static String recover(String state){return "preparing".equals(state)?"pending":"transmitting".equals(state)?"uncertain":state;}
    static boolean cancellable(String state){return state.equals("pending")||state.equals("preparing")||state.equals("failed")||state.equals("uncertain");}
    static String failure(String phase,Exception error,int attempts){
        if(phase.equals("transmitting"))return "uncertain";
        if(error instanceof javax.mail.AuthenticationFailedException||error instanceof IllegalArgumentException||error instanceof javax.mail.internet.AddressException)return "failed";
        return attempts<3?"pending":"failed";
    }
    static long retryAt(long now,int attempts){return now+Math.min(30,5*Math.max(1,attempts))*60000L;}
}

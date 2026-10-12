package ru.local.spbumailpilot;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class AccountMailChecksTest {
    public static void main(String[] args)throws Exception{
        List<String> checked=new ArrayList<>();
        Exception rejected=new Exception("rejected credentials");
        try{
            AccountMailChecks.perform(Arrays.asList("disabled","broken","inactive","active"),account->!account.equals("disabled"),account->{checked.add(account);if(account.equals("broken"))throw rejected;},()->false);
            throw new AssertionError("failure lost");
        }catch(Exception failure){if(failure!=rejected)throw failure;}
        if(!checked.equals(Arrays.asList("broken","inactive","active")))throw new AssertionError("bad account blocks other mailboxes: "+checked);
        checked.clear();
        try{
            AccountMailChecks.perform(Arrays.asList("bad-settings","healthy"),account->{if(account.equals("bad-settings"))throw rejected;return true;},checked::add,()->false);
            throw new AssertionError("bad settings failure lost");
        }catch(Exception failure){if(failure!=rejected)throw failure;}
        if(!checked.equals(Arrays.asList("healthy")))throw new AssertionError("bad settings block healthy account");
        checked.clear();
        AccountMailChecks.perform(Arrays.asList("inactive","active"),account->true,checked::add,()->true);
        if(!checked.isEmpty())throw new AssertionError("check starts after cancellation");
        AtomicBoolean stopped=new AtomicBoolean();checked.clear();
        AccountMailChecks.perform(Arrays.asList("inactive","active"),account->true,account->{checked.add(account);stopped.set(true);},stopped::get);
        if(!checked.equals(Arrays.asList("inactive")))throw new AssertionError("cancelled check starts next account");
        for(boolean notifications:new boolean[]{false,true})for(boolean background:new boolean[]{false,true}){
            if(AccountMailChecks.enabled(notifications,background,false)!=notifications)throw new AssertionError("periodic notification policy");
            if(AccountMailChecks.enabled(notifications,background,true)!=(notifications&&background))throw new AssertionError("per-account foreground policy");
        }
        System.out.println("PASS: all remembered accounts checked, independent failures, cancellation and per-account notification/background settings.");
    }
}

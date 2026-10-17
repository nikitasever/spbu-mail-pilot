package ru.local.spbumailpilot;
import java.util.*;
public final class LauncherIconPolicyTest {
    static void check(boolean v){if(!v)throw new AssertionError();}
    static final class Fake implements LauncherIconPolicy.Backend {
        Map<String,Integer> states=new LinkedHashMap<>();List<String> calls=new ArrayList<>();String saved="spbu";int failAt=-1;boolean failSave;
        Fake(){states.put("SpbuLauncher",0);states.put("ClassicLauncher",0);states.put("ComposePilotLauncher",0);states.put("ComposeClassicLauncher",0);}
        public int state(String name){return states.get(name);}
        public void state(String name,int value)throws Exception{calls.add(name+":"+value);states.put(name,value);if(calls.size()==failAt)throw new Exception("Partial platform update");}
        public String saved(){return saved;}
        public boolean save(String mode){saved=mode;if(failSave){failSave=false;return false;}return true;}
    }
    public static void main(String[] args)throws Exception {
        Fake success=new Fake();LauncherIconPolicy.change(success,true,"classic");
        check(success.states.get("ClassicLauncher")==1&&success.states.get("ComposeClassicLauncher")==1&&success.states.get("SpbuLauncher")==2&&success.states.get("ComposePilotLauncher")==2&&success.saved.equals("classic"));
        check(success.calls.get(0).equals("ClassicLauncher:1")&&success.calls.get(1).equals("ComposeClassicLauncher:1"));
        for(int phase=1;phase<=5;phase++){
            Fake f=new Fake();Map<String,Integer> before=new LinkedHashMap<>(f.states);f.failAt=phase<=4?phase:-1;f.failSave=phase==5;
            try{LauncherIconPolicy.change(f,true,"classic");throw new AssertionError("Expected failure");}catch(Exception expected){}
            check(f.states.equals(before)&&f.saved.equals("spbu"));
        }
        Fake legacy=new Fake();LauncherIconPolicy.change(legacy,false,"classic");check(legacy.calls.size()==2&&legacy.states.get("ComposePilotLauncher")==0);
        Fake invalid=new Fake();try{LauncherIconPolicy.change(invalid,true,"unknown");throw new AssertionError();}catch(IllegalArgumentException expected){}check(invalid.calls.isEmpty());
        System.out.println("PASS LauncherIconPolicy: native/legacy selection, replacement-first ordering, partial component/prefs rollback, invalid-mode nonmutation");
    }
}

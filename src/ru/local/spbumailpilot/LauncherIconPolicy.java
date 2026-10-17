package ru.local.spbumailpilot;
import java.util.*;

/** Enable replacements before hiding old icons; restore component states if a step fails. */
final class LauncherIconPolicy {
    interface Backend {
        int state(String name)throws Exception;
        void state(String name,int value)throws Exception;
        String saved();
        boolean save(String mode)throws Exception;
    }
    static void change(Backend backend,boolean nativeAliases,String mode)throws Exception {
        if(!"spbu".equals(mode)&&!"classic".equals(mode))throw new IllegalArgumentException("Unknown icon");
        boolean spbu="spbu".equals(mode);
        List<String> enable=new ArrayList<>(Arrays.asList(spbu?"SpbuLauncher":"ClassicLauncher"));
        List<String> disable=new ArrayList<>(Arrays.asList(spbu?"ClassicLauncher":"SpbuLauncher"));
        if(nativeAliases){enable.add(spbu?"ComposePilotLauncher":"ComposeClassicLauncher");disable.add(spbu?"ComposeClassicLauncher":"ComposePilotLauncher");}
        List<String> order=new ArrayList<>(enable);order.addAll(disable);
        Map<String,Integer> before=new LinkedHashMap<>();for(String name:order)before.put(name,backend.state(name));
        String old=backend.saved();List<String> touched=new ArrayList<>();boolean saving=false;
        try {
            for(String name:order){touched.add(name);backend.state(name,enable.contains(name)?1:2);}
            saving=true;if(!backend.save(mode))throw new IllegalStateException("Icon preference not saved");
        }catch(Exception failure){
            if(saving)try{if(!backend.save(old))throw new IllegalStateException("Preference rollback failed");}catch(Exception rollback){failure.addSuppressed(rollback);}
            Collections.reverse(touched);for(String name:touched)try{backend.state(name,before.get(name));}catch(Exception rollback){failure.addSuppressed(rollback);}
            throw failure;
        }
    }
}

package ru.local.spbumailpilot;

import java.nio.charset.StandardCharsets;
import org.json.*;

/** Host test for the pure pasted-text import policy.
 *  Run: java ru.local.spbumailpilot.NativeContactImportTest */
public final class NativeContactImportTest {
    interface Step {void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Step step){try{step.run();}catch(Exception expected){return;}throw new AssertionError("Expected refusal");}
    static String refuse(Step step)throws Exception{try{step.run();}catch(Exception expected){return expected.getMessage()==null?"":expected.getMessage();}throw new AssertionError("Expected refusal");}
    static JSONObject person(String email,String name,String number)throws Exception{return new JSONObject().put("email",email).put("sender",name).put("number",number);}
    static JSONObject find(JSONArray contacts,String email)throws Exception{for(int i=0;i<contacts.length();i++)if(contacts.getJSONObject(i).getString("email").equalsIgnoreCase(email))return contacts.getJSONObject(i);return null;}

    public static void main(String[] args)throws Exception {
        JSONObject keep=person("keep@example.invalid","Existing","+7 000").put("custom",new JSONObject().put("kept",true));
        JSONObject state=new JSONObject()
            .put("contacts",new JSONArray().put(keep))
            .put("recipientBook",new JSONArray().put(new JSONObject(keep.toString())))
            .put("phoneContacts",new JSONArray().put(person("keep@example.invalid","Existing","+7 111")))
            .put("settings",new JSONObject().put("theme","dark"));
        String before=state.toString();

        // Successful import: three new records, in-input duplicates and existing matches skipped.
        String text=String.join("\n",
            "First Person <first@example.invalid>",
            "second@example.invalid",
            "third@example.invalid; Третий; +7 999",
            "",
            "  ",
            "FIRST@example.invalid; duplicate;",
            "third@example.invalid; duplicate;",
            "KEEP@example.invalid; existing duplicate;");
        NativeContactImport.Preview preview=NativeContactImport.preview(text,state);
        check(preview.added==3);
        check(preview.skipped==3);
        JSONObject next=preview.apply(state);
        check(next.getJSONArray("contacts").length()==4);
        check(next.getJSONArray("contacts").getJSONObject(0).getString("sender").equals("Existing"));
        check(find(next.getJSONArray("contacts"),"first@example.invalid").getString("sender").equals("First Person"));
        check(find(next.getJSONArray("contacts"),"second@example.invalid").getString("sender").equals("second@example.invalid"));
        check(find(next.getJSONArray("contacts"),"third@example.invalid").getString("number").equals("+7 999"));
        check(find(next.getJSONArray("contacts"),"first@example.invalid").optString("number").isEmpty()); // first line wins
        check(find(next.getJSONArray("contacts"),"keep@example.invalid").getJSONObject("custom").getBoolean("kept"));
        check(SharedStateMerge.same(next.get("settings"),state.get("settings")));
        check(SharedStateMerge.same(next.get("recipientBook"),state.get("recipientBook")));
        for(String key:new String[]{"tasks","notes","events"})check(next.getJSONArray(key).length()==0);
        check(state.toString().equals(before)); // pure policy never mutates input

        // Unrelated concurrent state is retained; a contact change forces a new preview.
        JSONObject concurrent=new JSONObject(state.toString()).put("settings",new JSONObject().put("theme","light").put("extra",1));
        check(preview.apply(concurrent).getJSONObject("settings").getString("theme").equals("light"));
        reject(()->preview.apply(new JSONObject(state.toString()).put("contacts",new JSONArray().put(person("other@example.invalid","Other","")))));

        // Malformed lines reject the whole batch and name the line; no partial change.
        check(refuse(()->NativeContactImport.preview("ok@example.invalid\nnot an address\n",state)).contains("Строка 2"));
        check(refuse(()->NativeContactImport.preview("a@example.invalid;name;phone;extra\n",state)).contains("Строка 1"));
        check(refuse(()->NativeContactImport.preview("Alice <a@example.invalid\n",state)).contains("Строка 1"));
        reject(()->NativeContactImport.preview("<only@example.invalid>\n",state));
        reject(()->NativeContactImport.preview(";name;+7 000\n",state));
        check(state.toString().equals(before));

        // Size and line limits.
        check(NativeContactImport.preview("",state).added==0);
        check(refuse(()->NativeContactImport.preview("x".repeat(NativeContactImport.MAX_BYTES+1),state)).contains("200"));
        check(refuse(()->NativeContactImport.preview("\u044b".repeat(NativeContactImport.MAX_BYTES/2+1),state)).contains("200"));
        StringBuilder exact=new StringBuilder();
        for(int i=0;i<NativeContactImport.MAX_LINES;i++){if(i>0)exact.append('\n');exact.append("line").append(i).append("@example.invalid");}
        check(NativeContactImport.preview(exact.toString(),state).added==NativeContactImport.MAX_LINES);
        check(refuse(()->NativeContactImport.preview(exact+"\noverflow@example.invalid",state)).contains("500"));

        // Total storage limit: 5000 existing blocks an addition; duplicates stay allowed.
        JSONArray bulk=new JSONArray();
        for(int i=0;i<5000;i++)bulk.put(person("bulk"+i+"@example.invalid","Bulk "+i,""));
        JSONObject full=new JSONObject().put("contacts",bulk);
        reject(()->NativeContactImport.preview("extra@example.invalid",full));
        check(NativeContactImport.preview("bulk0@example.invalid",full).added==0);
        JSONArray almost=new JSONArray();
        for(int i=0;i<4999;i++)almost.put(person("almost"+i+"@example.invalid","A"+i,""));
        check(NativeContactImport.preview("new@example.invalid",new JSONObject().put("contacts",almost)).added==1);
        // Stream reading safely rejects streams exceeding MAX_BYTES and reads valid streams.
        java.io.ByteArrayInputStream goodStream=new java.io.ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        NativeContactImport.Preview streamPreview=NativeContactImport.previewStream(goodStream,state);
        check(streamPreview.added==3);
        check(streamPreview.skipped==3);
        check(streamPreview.duplicateCount==2);
        check(streamPreview.existingCount==1);

        byte[] bigBytes=new byte[NativeContactImport.MAX_BYTES+1];
        java.util.Arrays.fill(bigBytes,(byte)'a');
        reject(()->NativeContactImport.previewStream(new java.io.ByteArrayInputStream(bigBytes),state));
        reject(()->NativeContactImport.previewStream(null,state));

        reject(()->NativeContactImport.previewStream(new java.io.ByteArrayInputStream(new byte[]{(byte)0xc3,0x28}),state));
        reject(()->NativeContactImport.previewStream(new java.io.ByteArrayInputStream("bad\0@example.invalid".getBytes(StandardCharsets.UTF_8)),state));
        check(NativeContactImport.previewStream(new java.io.ByteArrayInputStream(("\ufeffnew-bom@example.invalid").getBytes(StandardCharsets.UTF_8)),state).added==1);
        java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        java.io.InputStream cancels=new java.io.ByteArrayInputStream("new@example.invalid".getBytes(StandardCharsets.UTF_8)){
            public synchronized int read(byte[] b,int off,int len){int count=super.read(b,off,len);cancelled.set(true);return count;}
        };
        reject(()->NativeContactImport.readStream(cancels,cancelled::get));

        String owner="account-"+"a".repeat(64);NativeContactFilePick pick=new NativeContactFilePick(owner,5);
        check(pick.matches(owner,5)&&!pick.matches(owner,6)&&!pick.matches("account-"+"b".repeat(64),5));
        check(!pick.toString().contains(owner));
        byte[] exactFileBytes=new byte[NativeContactImport.MAX_BYTES];java.util.Arrays.fill(exactFileBytes,(byte)' ');
        byte[] address="boundary@example.invalid".getBytes(StandardCharsets.UTF_8);System.arraycopy(address,0,exactFileBytes,0,address.length);
        check(NativeContactImport.previewStream(new java.io.ByteArrayInputStream(exactFileBytes),state).added==1);

        // parseWithErrors collects errors per line without throwing
        java.util.List<String> errors=new java.util.ArrayList<>();
        java.util.List<JSONObject> parsedWithErrors=NativeContactImport.parseWithErrors("good@example.invalid\nbad line\nanother@example.invalid",errors);
        check(parsedWithErrors.size()==2);
        check(errors.size()==1);
        check(errors.get(0).contains("Строка 2"));

        java.util.List<JSONObject> systemBatch=new java.util.ArrayList<>();
        systemBatch.add(person("work@example.invalid","Иван","+7 123"));
        systemBatch.add(person("home@example.invalid","Иван","+7 123"));
        systemBatch.add(person("WORK@example.invalid","Другой",""));
        systemBatch.add(person("keep@example.invalid","Existing",""));
        systemBatch.add(person("not-an-email","Bad",""));
        NativeContactImport.Preview system=NativeContactImport.previewEntries(systemBatch,state);
        check(system.candidates.size()==4&&system.added==2&&system.skipped==2&&system.invalidLines.size()==1);
        check(system.duplicateCount==1&&system.existingCount==1&&system.selectable().size()==2);
        systemBatch.get(0).put("sender","changed after preview");
        JSONObject partial=system.apply(state,java.util.Set.of("WORK@example.invalid"));
        check(partial.getJSONArray("contacts").length()==2&&find(partial.getJSONArray("contacts"),"work@example.invalid").getString("sender").equals("Иван"));
        check(find(partial.getJSONArray("contacts"),"home@example.invalid")==null&&state.toString().equals(before));
        reject(()->system.apply(state,java.util.Set.of("unknown@example.invalid")));
        reject(()->system.apply(state,java.util.Set.of("keep@example.invalid")));
        reject(()->system.apply(state,null));reject(()->system.candidates.clear());
        NativeContactImport.Preview capacity=NativeContactImport.previewEntries(java.util.Arrays.asList(person("new-one@example.invalid","One",""),person("new-two@example.invalid","Two","")),new JSONObject().put("contacts",almost));
        check(capacity.capacity==1&&capacity.added==2);
        check(capacity.apply(new JSONObject().put("contacts",almost),java.util.Set.of("new-one@example.invalid")).getJSONArray("contacts").length()==5000);
        reject(()->capacity.apply(new JSONObject().put("contacts",almost)));

        System.out.println("NativeContactImportTest passed: format, line/byte/count limits, malformed lines, case duplicates, existing and unrelated field preservation, stale preview conflict, nonmutation, stream reading, error collection and successful import");
    }
}


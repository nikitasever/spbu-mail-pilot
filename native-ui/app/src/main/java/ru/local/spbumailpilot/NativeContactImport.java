package ru.local.spbumailpilot;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/**
 * Pure policy for pasted-text local contact import.
 * No IO, no storage, and it never mutates its inputs. The caller commits the
 * fresh shared account JSON under its existing ReplyReminderCenter lock.
 *
 * Plain text only, one record per non-blank line:
 *   email
 *   Name <email>
 *   email;name;phone
 * There is no CSV quoting promise; semicolons and angle brackets are literal.
 */
final class NativeContactImport {
    private NativeContactImport(){}

    static final int MAX_BYTES=200*1024;
    static final int MAX_LINES=500;
    static final int MAX_CONTACTS=5000;

    static final class Candidate {
        final String email,name,number;final boolean duplicateInBatch,alreadyExists;
        Candidate(JSONObject entry,boolean duplicate,boolean exists)throws Exception{email=entry.getString("email");name=entry.getString("sender");number=entry.getString("number");duplicateInBatch=duplicate;alreadyExists=exists;}
        boolean isImportable(){return !duplicateInBatch&&!alreadyExists;}
    }
    /** Immutable preview. Holds deep copies so callers can never alias the store. */
    static final class Preview {
        private final JSONArray snapshot;
        final List<Candidate> candidates;
        final int capacity;
        final int added;
        final int skipped;
        final List<String> invalidLines;
        final int duplicateCount;
        final int existingCount;

        Preview(JSONArray snapshot,List<Candidate> candidates,int added,int skipped,List<String> invalidLines,int duplicates,int existing)throws Exception{
            this.snapshot=new JSONArray(snapshot.toString());capacity=MAX_CONTACTS-snapshot.length();
            this.candidates=Collections.unmodifiableList(new ArrayList<>(candidates));this.added=added;this.skipped=skipped;
            this.invalidLines=Collections.unmodifiableList(new ArrayList<>(invalidLines));duplicateCount=duplicates;existingCount=existing;
        }
        Set<String> selectable(){Set<String> result=new HashSet<>();for(Candidate c:candidates)if(c.isImportable())result.add(key(c.email));return Collections.unmodifiableSet(result);}
        /** Rebuilds shared JSON from the current store, refusing contact changes since the preview. */
        JSONObject apply(JSONObject current)throws Exception{return apply(current,selectable());}
        JSONObject apply(JSONObject current,Set<String> selected)throws Exception{
            if(selected==null)throw new IllegalArgumentException("Selection required");
            Set<String> chosen=new HashSet<>();for(String email:selected){if(email==null)throw new IllegalArgumentException("Invalid selection");chosen.add(key(email));}
            if(!selectable().containsAll(chosen))throw new IllegalArgumentException("Selection changed");
            JSONObject base=current==null?new JSONObject():current;JSONArray now=NativeContactEdits.rows(base);
            if(!SharedStateMerge.same(snapshot,now))throw new SharedStateMerge.Conflict();
            JSONArray result=new JSONArray(snapshot.toString());
            for(Candidate c:candidates)if(c.isImportable()&&chosen.contains(key(c.email)))result.put(NativeContactEdits.fields(new JSONObject().put("email",c.email).put("sender",c.name).put("number",c.number)));
            if(result.length()>MAX_CONTACTS)throw new IllegalArgumentException("Предел 5000 контактов: уменьшите выбор.");
            JSONObject next=new JSONObject(base.toString()).put("contacts",result);return NativeSharedState.ready(next);
        }
    }

    /** Strict bounded UTF-8; never replaces invalid bytes or silently ignores binary data. */
    static Preview previewStream(java.io.InputStream input,JSONObject current)throws Exception{return preview(readStream(input,()->false),current);}
    static String readStream(java.io.InputStream input,java.util.function.BooleanSupplier cancelled)throws Exception{
        if(input==null)throw new IllegalArgumentException("Файл недоступен.");
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int read;
        while(true){
            if(cancelled.getAsBoolean())throw new java.io.InterruptedIOException("Чтение остановлено.");
            read=input.read(buffer);if(read<0)break;
            if(read>MAX_BYTES-out.size())throw new IllegalArgumentException("Файл больше 200 КиБ.");out.write(buffer,0,read);
        }
        if(cancelled.getAsBoolean())throw new java.io.InterruptedIOException("Чтение остановлено.");
        final String decoded;
        try{decoded=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(out.toByteArray())).toString();}
        catch(java.nio.charset.CharacterCodingException invalid){throw new IllegalArgumentException("Нужен текстовый файл в UTF-8. Кодировка файла не поддерживается.");}
        String text=decoded.startsWith("\ufeff")?decoded.substring(1):decoded;
        if(text.indexOf('\0')>=0)throw new IllegalArgumentException("Файл содержит бинарные данные. Выберите текст UTF-8.");
        return text;
    }

    /** Text/file import rejects invalid lines as one batch, preserving its prior contract. */
    static Preview preview(String text,JSONObject current)throws Exception{
        List<String> invalid=new ArrayList<>();List<JSONObject> parsed=parseWithErrors(text,invalid);
        if(!invalid.isEmpty())throw new IllegalArgumentException("Некорректных строк: "+invalid.size()+". "+invalid.get(0));
        return build(parsed,current,true);
    }
    /** Android rows are revalidated; invalid rows are disclosed and never selectable. */
    static Preview previewEntries(List<JSONObject> parsed,JSONObject current)throws Exception{return build(parsed,current,false);}
    private static Preview build(List<JSONObject> parsed,JSONObject current,boolean strict)throws Exception{
        if(parsed==null||parsed.size()>MAX_CONTACTS)throw new IllegalArgumentException("В системной книге больше 5000 адресов. Импортируйте выбранный список текстом или файлом.");
        JSONObject base=current==null?new JSONObject():current;JSONArray existing=NativeContactEdits.rows(base);
        Set<String> present=new HashSet<>();for(int i=0;i<existing.length();i++)present.add(key(existing.getJSONObject(i).getString("email")));
        Set<String> seen=new HashSet<>();List<Candidate> candidates=new ArrayList<>();List<String> invalid=new ArrayList<>();
        int added=0,skipped=0,duplicates=0,existingMatches=0,index=0;
        for(JSONObject input:parsed){
            index++;JSONObject entry;
            try{entry=NativeContactEdits.fields(input);}catch(Exception error){if(strict)throw error;invalid.add("Некорректная запись "+index);continue;}
            String email=key(entry.getString("email"));boolean duplicate=!seen.add(email),exists=present.contains(email);
            candidates.add(new Candidate(entry,duplicate,exists));
            if(duplicate){skipped++;duplicates++;}else if(exists){skipped++;existingMatches++;}else{added++;}
        }
        if(strict&&existing.length()+added>MAX_CONTACTS)throw new IllegalArgumentException("Предел 5000 контактов: уменьшите пакет.");
        return new Preview(existing,candidates,added,skipped,invalid,duplicates,existingMatches);
    }

    /** Validates lines and collects errors per-line instead of throwing on the first one. */
    static List<JSONObject> parseWithErrors(String text,List<String> invalidLines)throws Exception {
        String value=text==null?"":text;
        if(value.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Текст больше 200 КиБ.");
        String[] lines=value.split("\r\n|\n|\r",-1);
        List<JSONObject> entries=new ArrayList<>();
        int nonblank=0;
        for(int i=0;i<lines.length;i++) {
            String raw=lines[i].trim();
            if(raw.isEmpty())continue;
            if(++nonblank>MAX_LINES)throw new IllegalArgumentException("Больше "+MAX_LINES+" непустых строк.");
            try {
                entries.add(line(raw,i+1));
            }catch(Exception err){
                if(invalidLines!=null)invalidLines.add(err.getMessage());
                else throw err;
            }
        }
        return entries;
    }

    /** Validates every non-blank line; a single bad line rejects the whole batch. */
    static List<JSONObject> parse(String text)throws Exception {
        return parseWithErrors(text,null);
    }


    private static JSONObject line(String raw,int number) {
        try {
            String email,name,phone="";
            if(raw.indexOf(';')>=0) {
                String[] parts=raw.split(";",-1);
                if(parts.length>3)throw new IllegalArgumentException("Формат: адрес; имя; телефон.");
                email=parts[0];name=parts.length>1?parts[1]:"";phone=parts.length>2?parts[2]:"";
            }else {
                int open=raw.lastIndexOf('<'),close=raw.lastIndexOf('>');
                if(open>=0||close>=0) {
                    if(open<=0||close!=raw.length()-1||close<open)throw new IllegalArgumentException("Формат: Имя <адрес>.");
                    name=raw.substring(0,open);email=raw.substring(open+1,close);
                }else {email=raw;name="";}
            }
            return NativeContactEdits.fields(new JSONObject().put("email",email).put("sender",name).put("number",phone));
        }catch(Exception error) {
            String reason=error instanceof IllegalArgumentException&&error.getMessage()!=null?error.getMessage():"Неверный формат.";
            throw new IllegalArgumentException("Строка "+number+": "+reason);
        }
    }

    private static String key(String email){return email.toLowerCase(Locale.ROOT);}
}

package ru.local.spbumailpilot;
import java.util.*;
import org.json.*;

public class DirectoryAutocompleteTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static final String DEFAULTS="<XIMSS><prefs><DirectoryBooks><subKey key='$domain$'>dc=default</subKey><subKey key='students'>dc=students</subKey><subKey key='staff'>dc=staff</subKey><subKey key='private'>dc=private</subKey><subKey key='disabled'>dc=disabled</subKey></DirectoryBooks></prefs><response id='defaultBooks'/></XIMSS>";
    static final String EFFECTIVE="<XIMSS><prefs><DirectoryBooks><subKey key='$domain$'>dc=override</subKey></DirectoryBooks><DirectoryBooksHideForSearch><subValue>private</subValue></DirectoryBooksHideForSearch><DisabledDirectoryBooks><subValue>disabled</subValue></DisabledDirectoryBooks></prefs><response id='books'/></XIMSS>";
    public static void main(String[] args)throws Exception{
        List<String> calls=new ArrayList<>();List<JSONObject> searches=new ArrayList<>();
        UniversityDirectory directory=new UniversityDirectory((path,body)->{
            calls.add(path);
            if(path.equals("/ximsslogin/"))return "<XIMSS><session urlID='fixture' userName='fixture@spbu.ru'/></XIMSS>";
            if(body.contains("id=\"defaultBooks\""))return DEFAULTS;
            if(body.contains("prefsRead"))return EFFECTIVE;
            if(path.endsWith("/api/v1/ldap/search")){searches.add(new JSONObject(body));return "{\"records\":[{\"cn\":\"Перепенко — тестовая запись\",\"mail\":\"person@example.invalid\"},{\"cn\":\"Other test user\",\"mail\":[\"other@example.invalid\",\"bad\",\"PERSON@example.invalid\"]}]}";}
            return "<XIMSS><response id='bye'/></XIMSS>";
        });
        SavedAccounts.Login login=new SavedAccounts.Login("mail.spbu.ru",993,"fixture@spbu.ru","fixture");
        JSONArray people=directory.search(login,"Перепенко");check(people.length()==2);check(people.getJSONObject(0).getString("sender").startsWith("Перепенко"));
        JSONObject payload=searches.get(0);JSONArray bases=payload.getJSONArray("baseDNs");check(bases.length()==3);check(bases.toString().contains("dc=students")&&bases.toString().contains("dc=staff")&&bases.toString().contains("dc=override"));check(!bases.toString().contains("dc=default")&&!bases.toString().contains("dc=private")&&!bases.toString().contains("dc=disabled"));check(payload.getString("filter").contains("Перепенко"));check(payload.getString("scope").equals("sub"));
        directory.search(login,"internal-alias@spbu.ru");check(calls.size()==5);check(searches.get(1).getString("filter").contains("(uid=*internal-alias*)"));check(!searches.get(1).getString("filter").contains("(uid=*internal-alias@spbu.ru*)"));directory.close();
        check(UniversityDirectory.searchBases(UniversityDirectory.document(DEFAULTS),UniversityDirectory.document(EFFECTIVE)).size()==3);
        check(UniversityDirectory.apiPeople("{\"records\":[]}").length()==0);try{UniversityDirectory.apiPeople("{\"error\":\"hidden\"}");throw new AssertionError();}catch(JSONException expected){}
        List<String> fallback=new ArrayList<>();UniversityDirectory legacy=new UniversityDirectory((path,body)->{
            if(path.equals("/ximsslogin/"))return "<XIMSS><session urlID='legacy'/></XIMSS>";
            if(body.contains("id=\"defaultBooks\""))return DEFAULTS;if(body.contains("prefsRead"))return EFFECTIVE;
            if(path.endsWith("/ldap/search"))throw new UniversityDirectory.HttpFailure(404);
            if(body.contains("listDirectory")){fallback.add(body);return "<XIMSS><directoryData id='directory'><subKey key='cn'>Test Person</subKey><subKey key='mail'>person@example.invalid</subKey></directoryData><response id='directory'/></XIMSS>";}
            return "<XIMSS><response id='bye'/></XIMSS>";
        });
        check(legacy.search(login,"Person").length()==1&&fallback.size()==3);check(fallback.stream().noneMatch(c->c.contains("dc=private")||c.contains("dc=disabled")));legacy.close();
        UniversityDirectory forbidden=new UniversityDirectory((path,body)->{if(path.equals("/ximsslogin/"))return "<XIMSS><session urlID='blocked'/></XIMSS>";if(body.contains("id=\"defaultBooks\""))return DEFAULTS;if(body.contains("prefsRead"))return EFFECTIVE;if(path.endsWith("/ldap/search"))throw new UniversityDirectory.HttpFailure(403);if(body.contains("listDirectory"))throw new AssertionError("No fallback on denied permission");return "<XIMSS><response id='bye'/></XIMSS>";});
        try{forbidden.search(login,"Person");throw new AssertionError();}catch(UniversityDirectory.HttpFailure expected){check(expected.status==403);}
        System.out.println("PASS: web-mail LDAP search contract, default/custom book merge, all permitted bases, excluded books respected, Unicode/name and full-address UID queries, cached prefs, valid deduped records, legacy-only fallback. Test names and addresses are fixtures; no real university directory queried.");
    }
}

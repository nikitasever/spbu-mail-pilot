package ru.local.spbumailpilot;

import java.util.*;
import org.json.*;

public final class UniversityContactsTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        List<String> calls=new ArrayList<>();
        UniversityDirectory directory=new UniversityDirectory((path,body)->{
            calls.add(body);
            if(path.equals("/ximsslogin/"))return "<XIMSS><session urlID='fixture'/><response id='login'/></XIMSS>";
            if(body.contains("defaultBooks"))return "<XIMSS><prefs/><response id='defaultBooks'/></XIMSS>";
            if(body.contains("prefsRead"))return "<XIMSS><prefs><DirectoryBooks><subKey key='$domain$'>dc=university</subKey></DirectoryBooks><ContactsBox>My Contacts</ContactsBox></prefs><response id='books'/></XIMSS>";
            if(body.contains("(uid="))return "<XIMSS><directoryData id='directory'><field name='ou'>IT (support)*</field></directoryData><response id='directory'/></XIMSS>";
            if(body.contains("listDirectory"))return "<XIMSS><directoryData id='directory' cookie='next&amp;page'><field name='cn'>University Person</field><field name='mail'>person@example.invalid</field></directoryData><response id='directory'/></XIMSS>";
            if(body.contains("folderOpen"))return "<XIMSS><folderReport id='openContacts' folder='pilotContacts' mode='init' messages='51'/><response id='openContacts'/></XIMSS>";
            if(body.contains("folderBrowse"))return "<XIMSS><folderReport id='browseContacts' folder='pilotContacts'><Subject>Personal Name</Subject><E-To>personal@example.invalid</E-To></folderReport><response id='browseContacts'/></XIMSS>";
            return "<XIMSS><response id='closeContacts'/></XIMSS>";
        });
        SavedAccounts.Login login=new SavedAccounts.Login("mail.spbu.ru",993,"fixture@spbu.ru","fixture");
        JSONObject company=directory.page(login,"company","","");check(company.getJSONArray("people").length()==1);check(company.getBoolean("hasMore")&&company.getString("cookie").equals("next&page"));
        check(calls.stream().anyMatch(c->c.contains("baseDN=\"dc=university\"")&&c.contains("cookie=\"\"")&&c.contains("limit=\"50\"")));
        directory.page(login,"team","Person","");check(calls.stream().anyMatch(c->c.contains("IT \\28support\\29\\2a")&&c.contains("(ou=")));
        JSONObject personal=directory.page(login,"personal","Name","");check(personal.getJSONArray("people").getJSONObject(0).getString("sender").equals("Personal Name"));
        check(personal.getString("cookie").equals("50")&&personal.getBoolean("hasMore"));
        check(calls.stream().anyMatch(c->c.contains("mailbox=\"My Contacts\"")&&c.contains("filter=\"Name\"")&&!c.contains("mailboxClass")));
        JSONObject last=directory.page(login,"personal","Name","50");check(!last.getBoolean("hasMore"));
        check(calls.stream().anyMatch(c->c.contains("from=\"50\" till=\"50\"")));
        check(calls.stream().filter(c->c.contains("folderClose")).count()==2);
        check(calls.stream().noneMatch(c->c.contains("messageMark")||c.contains("messageRemove")||c.contains("contactAppend")));
        try{directory.page(login,"company","","next&page");throw new AssertionError();}catch(java.io.IOException expected){}
        UniversityDirectory missingTeam=new UniversityDirectory((path,body)->path.equals("/ximsslogin/")?"<XIMSS><session urlID='fixture'/><response id='login'/></XIMSS>":body.contains("defaultBooks")?"<XIMSS><prefs/><response id='defaultBooks'/></XIMSS>":body.contains("prefsRead")?"<XIMSS><prefs/><response id='books'/></XIMSS>":"<XIMSS><response id='directory'/></XIMSS>");
        check(missingTeam.page(login,"team","","").getString("notice").contains("подразделение"));
        List<String> defaultsCalls=new ArrayList<>();
        UniversityDirectory inherited=new UniversityDirectory((path,body)->{
            defaultsCalls.add(body);
            if(path.equals("/ximsslogin/"))return "<XIMSS><session urlID='fixture' userName='me@spbu.ru'/></XIMSS>";
            if(body.contains("defaultBooks"))return "<XIMSS><prefs><DirectoryBooks><subKey key='$domain$'>dc=inherited</subKey></DirectoryBooks></prefs><response id='defaultBooks'/></XIMSS>";
            if(body.contains("prefsRead"))return "<XIMSS><prefs/><response id='books'/></XIMSS>";
            return "<XIMSS><directoryData id='directory'><attribute name='cn'>Inherited Person</attribute><attribute name='mail'>inherited@example.invalid</attribute></directoryData><response id='directory'/></XIMSS>";
        });
        check(inherited.page(login,"company","","").getJSONArray("people").getJSONObject(0).getString("sender").equals("Inherited Person"));
        check(defaultsCalls.stream().anyMatch(c->c.contains("baseDN=\"dc=inherited\"")));
        check(UniversityDirectory.companyBase(UniversityDirectory.document("<XIMSS><prefs/></XIMSS>")).equals("$domain$"));
        check(UniversityDirectory.people(UniversityDirectory.document("<XIMSS><directoryData id='other'><field name='mail'>wrong@example.invalid</field></directoryData><directoryData id='directory'><cn>Named Person</cn><mail><subValue>array@example.invalid</subValue><subValue>bad</subValue></mail></directoryData><response id='directory'/></XIMSS>")).length()==1);
        System.out.println("PASS: company server base, team department filter escaping, directory paging cookie, personal address-book header search/paging/close, no write commands, missing department and repeated page refused. No real contacts accessed.");
    }
}

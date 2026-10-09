package ru.local.spbumailpilot;

import java.util.*;
import org.json.*;

public final class UniversityDirectoryTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    interface Operation {void run()throws Exception;}
    static void rejected(Operation operation)throws Exception{try{operation.run();throw new AssertionError();}catch(IllegalArgumentException|java.io.IOException expected){}}
    public static void main(String[] args)throws Exception{
        List<String> calls=new ArrayList<>();
        String found="<XIMSS><directoryData id='directory'><subKey key='cn'>Иван Петров</subKey><subKey key='mail'><subValue>ivan@spbu.ru</subValue><subValue>invalid</subValue></subKey></directoryData><directoryData id='directory'><subKey key='mail'>IVAN@SPBU.RU</subKey></directoryData><response id='directory'/></XIMSS>";
        UniversityDirectory directory=new UniversityDirectory((path,body)->{calls.add(path+" "+body);if(path.equals("/ximsslogin/"))return "<XIMSS><session urlID='123-test' userName='fixture@spbu.ru'/></XIMSS>";if(path.endsWith("/ldap/search"))return "{\"records\":[{\"cn\":\"Иван Петров\",\"mail\":\"ivan@spbu.ru\"},{\"cn\":\"Duplicate\",\"mail\":\"IVAN@SPBU.RU\"}]}";if(body.contains("id=\"defaultBooks\""))return "<XIMSS><prefs/><response id='defaultBooks'/></XIMSS>";if(body.contains("prefsRead"))return "<XIMSS><prefs><DirectoryBooks><subKey key='$domain$'>dc=fixture</subKey></DirectoryBooks></prefs><response id='books'/></XIMSS>";if(body.contains("<bye"))return "<XIMSS><response id='bye'/></XIMSS>";return found;});
        check(UniversityDirectory.people(UniversityDirectory.document(found)).length()==1);
        SavedAccounts.Login login=new SavedAccounts.Login("mail.spbu.ru",993,"fixture@spbu.ru","test-password");
        JSONArray result=directory.search(login,"Иван");check(result.length()==1&&result.getJSONObject(0).getString("sender").equals("Иван Петров"));
        check(calls.size()==4&&calls.get(0).startsWith("/ximsslogin/ userName=fixture%40spbu.ru&password=")&&calls.get(0).contains("errorAsXML=1"));
        JSONObject payload=new JSONObject(calls.get(3).substring(calls.get(3).indexOf(' ')+1));check(payload.getString("scope").equals("sub")&&payload.getInt("limit")==20&&payload.getJSONArray("baseDNs").getString(0).equals("dc=fixture"));
        directory.search(login,"Петров");check(calls.size()==5);directory.close();check(calls.get(5).contains("<bye"));
        check(UniversityDirectory.filter("a*)(mail=*)").contains("a\\2a\\29\\28mail=\\2a\\29"));
        check(UniversityDirectory.xml("<\"&").equals("&lt;&quot;&amp;"));
        rejected(()->UniversityDirectory.query("a"));rejected(()->UniversityDirectory.query("a\nb"));
        rejected(()->directory.search(new SavedAccounts.Login("imap.mail.ru",993,"x@mail.ru","fixture"),"user"));
        rejected(()->UniversityDirectory.document("<!DOCTYPE XIMSS [<!ENTITY leak SYSTEM 'file:///etc/passwd'>]><XIMSS>&leak;</XIMSS>"));
        rejected(()->UniversityDirectory.people(UniversityDirectory.document("<XIMSS><response id='directory' errorText='secret'/></XIMSS>")));
        rejected(()->UniversityDirectory.people(UniversityDirectory.document("<XIMSS/>")));
        UniversityDirectory malformed=new UniversityDirectory((path,body)->"<XIMSS><session urlID='../foreign'/><response id='login'/></XIMSS>");
        rejected(()->malformed.search(login,"user"));
        UniversityDirectory rejectedLogin=new UniversityDirectory((path,body)->"<XIMSS><response errorNum='500' errorText='private server detail'/></XIMSS>");
        rejected(()->rejectedLogin.search(login,"user"));
        UniversityDirectory escapedLogin=new UniversityDirectory((path,body)->{check(path.equals("/ximsslogin/"));check(body.contains("userName=alias%2Bname%40spbu.ru")&&body.contains("password=a%26b%3D%2B"));return "<XIMSS/>";});
        rejected(()->escapedLogin.search(new SavedAccounts.Login("mail.spbu.ru",993,"alias+name@spbu.ru","a&b=+"),"user"));
        System.out.println("PASS: university directory session/search/logout against fixture, Cyrillic names and subKey arrays, deduplication, limit, filter/XML escaping, unsupported accounts, XXE and malformed/error responses. No real account accessed.");
    }
}

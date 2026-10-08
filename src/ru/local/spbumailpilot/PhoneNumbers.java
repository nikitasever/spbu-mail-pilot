package ru.local.spbumailpilot;

import java.util.*;
import java.util.regex.*;

final class PhoneNumbers {
    static String normalize(String value){
        if(value==null||value.length()>60||!value.matches("[+0-9 ()\\-]+"))throw new IllegalArgumentException("Invalid phone number");
        String number=value.replaceAll("[ ()\\-]","");
        if(!number.matches("\\+?[0-9]{5,15}"))throw new IllegalArgumentException("Use full phone number");return number;
    }
    static ArrayList<String> find(String text){
        LinkedHashSet<String> result=new LinkedHashSet<>();
        Matcher matcher=Pattern.compile("(?<![0-9])(?:\\+7|8|\\(8?[0-9]{3}\\))[ ()0-9-]{6,24}[0-9](?![0-9])").matcher(text==null?"":text);
        while(matcher.find()){try{String number=normalize(matcher.group());int digits=number.replace("+","").length();if(digits==10&&!number.startsWith("+"))number="+7"+number;if(digits>=10&&digits<=11)result.add(number);}catch(IllegalArgumentException ignored){}}
        return new ArrayList<>(result);
    }
}

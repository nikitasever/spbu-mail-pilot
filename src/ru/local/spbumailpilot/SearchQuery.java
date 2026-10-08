package ru.local.spbumailpilot;
import com.sun.mail.iap.Argument;
final class SearchQuery {
    static Argument arguments(String query)throws Exception{
        if(query==null||query.isEmpty()||query.length()>200||query.matches("(?s).*\\p{Cntrl}.*"))throw new IllegalArgumentException("Invalid search");
        Argument args=new Argument();args.writeAtom("CHARSET");args.writeAtom("UTF-8");args.writeAtom("OR");args.writeAtom("OR");
        for(String field:new String[]{"SUBJECT","FROM","BODY"}){args.writeAtom(field);args.writeString(query,"UTF-8");}
        return args;
    }
}

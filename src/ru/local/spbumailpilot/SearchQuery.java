package ru.local.spbumailpilot;
import com.sun.mail.iap.Argument;
final class SearchQuery {
    static final class Filters {
        final String query,from,start,end;final boolean attachments;
        Filters(String json)throws Exception{org.json.JSONObject value=new org.json.JSONObject(json);query=value.optString("query").trim();from=value.optString("from").trim();start=value.optString("start");end=value.optString("end");attachments=value.optBoolean("attachments");
            for(String text:new String[]{query,from})if(text.length()>200||text.matches("(?s).*\\p{Cntrl}.*"))throw new IllegalArgumentException("Invalid query");
            for(String date:new String[]{start,end})if(!date.isEmpty())date(date);
            if(!start.isEmpty()&&!end.isEmpty()&&start.compareTo(end)>0)throw new IllegalArgumentException("Invalid range");
        }
        String key(){return query+"\n"+from+"\n"+start+"\n"+end+"\n"+attachments;}
        boolean matches(org.json.JSONObject item){String day=item.optString("date");day=day.length()>=10?day.substring(0,10):"";return (start.isEmpty()||!day.isEmpty()&&day.compareTo(start)>=0)&&(end.isEmpty()||!day.isEmpty()&&day.compareTo(end)<=0)&&(!attachments||item.has("hasAttachments")&&item.optBoolean("hasAttachments"));}
        Argument arguments()throws Exception{Argument args=new Argument();args.writeAtom("CHARSET");args.writeAtom("UTF-8");
            if(!query.isEmpty()){args.writeAtom("OR");args.writeAtom("OR");for(String field:new String[]{"SUBJECT","FROM","BODY"}){args.writeAtom(field);args.writeString(query,"UTF-8");}}
            if(!from.isEmpty()){args.writeAtom("FROM");args.writeString(from,"UTF-8");}
            // Broad server bounds; final match uses the displayed Moscow received date.
            if(!start.isEmpty()){args.writeAtom("SINCE");args.writeAtom(imapDate(start,-1));}
            if(!end.isEmpty()){args.writeAtom("BEFORE");args.writeAtom(imapDate(end,2));}
            if(query.isEmpty()&&from.isEmpty()&&start.isEmpty()&&end.isEmpty())args.writeAtom("ALL");return args;
        }
    }
    private static java.util.Date date(String value)throws Exception{if(!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IllegalArgumentException("Invalid date");java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT);f.setLenient(false);f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));java.util.Date result=f.parse(value);if(!f.format(result).equals(value))throw new IllegalArgumentException("Invalid date");return result;}
    private static String imapDate(String value,int shift)throws Exception{java.util.Calendar c=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"),java.util.Locale.ROOT);c.setTime(date(value));c.add(java.util.Calendar.DATE,shift);java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("dd-MMM-yyyy",java.util.Locale.US);f.setTimeZone(c.getTimeZone());return f.format(c.getTime());}
    static org.json.JSONObject filtered(org.json.JSONObject result,Filters filters)throws Exception{org.json.JSONArray matches=new org.json.JSONArray(),rows=result.getJSONArray("messages");for(int i=0;i<rows.length();i++)if(filters.matches(rows.getJSONObject(i)))matches.put(rows.getJSONObject(i));return result.put("messages",matches).put("scanned",result.optInt("nextOffset")).put("attachmentScope",filters.attachments);}
    static Argument arguments(String query)throws Exception{
        if(query==null||query.isEmpty()||query.length()>200||query.matches("(?s).*\\p{Cntrl}.*"))throw new IllegalArgumentException("Invalid search");
        Argument args=new Argument();args.writeAtom("CHARSET");args.writeAtom("UTF-8");args.writeAtom("OR");args.writeAtom("OR");
        for(String field:new String[]{"SUBJECT","FROM","BODY"}){args.writeAtom(field);args.writeString(query,"UTF-8");}
        return args;
    }
}


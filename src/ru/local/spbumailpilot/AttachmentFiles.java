package ru.local.spbumailpilot;

import java.io.*;
import java.util.Locale;

final class AttachmentFiles {
    static final long MAX_BYTES=100L*1024*1024;
    static String safeName(String value){
        String name=value==null?"Вложение":value.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","_");
        if(name.isEmpty()||name.equals(".")||name.equals(".."))name="Вложение";
        return name.length()>160?name.substring(name.length()-160):name;
    }
    static String mime(String filename,String suggested){
        String name=filename.toLowerCase(Locale.ROOT);
        String[][] types={{".pdf","application/pdf"},{".docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document"},{".doc","application/msword"},{".xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},{".xls","application/vnd.ms-excel"},{".pptx","application/vnd.openxmlformats-officedocument.presentationml.presentation"},{".ppt","application/vnd.ms-powerpoint"},{".txt","text/plain"},{".jpg","image/jpeg"},{".jpeg","image/jpeg"},{".png","image/png"},{".zip","application/zip"}};
        for(String[] type:types)if(name.endsWith(type[0]))return type[1];
        return suggested!=null&&suggested.matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")?suggested:"application/octet-stream";
    }
    static File copy(InputStream input,File directory,String name,long limit)throws Exception{return copy(input,directory,name,limit,bytes->{});}
    static File copy(InputStream input,File directory,String name,long limit,java.util.function.LongConsumer progress)throws Exception{
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create attachment cache");
        File output=File.createTempFile("mail-","-"+safeName(name),directory);boolean complete=false;
        try{
          try(OutputStream stream=new FileOutputStream(output)){
            byte[] buffer=new byte[32768];long size=0;int read;
            while((read=input.read(buffer))!=-1){size+=read;if(size>limit)throw new IOException("Attachment too large");stream.write(buffer,0,read);progress.accept(size);}
          }
          complete=true;return output;
        }finally{if(!complete)output.delete();}
    }
}


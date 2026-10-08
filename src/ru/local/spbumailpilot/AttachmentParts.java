package ru.local.spbumailpilot;

import javax.mail.*;

final class AttachmentParts {
    static String child(String path,String index){return path.equals("root")?index:path+"."+index;}
    static Part resolve(Part root,String path)throws Exception{
        if(path==null||path.length()>160||!path.matches("root|(?:[0-9]{1,5}|m)(?:\\.(?:[0-9]{1,5}|m)){0,12}"))throw new IllegalArgumentException("Invalid MIME path");
        Part part=root;
        if(!path.equals("root"))for(String token:path.split("\\.")){
            if(token.equals("m")){
                if(!part.isMimeType("message/rfc822"))throw new MessagingException("Not a nested message");
                part=(Part)part.getContent();
            }else{
                if(!part.isMimeType("multipart/*"))throw new MessagingException("Not multipart");
                Multipart multi=(Multipart)part.getContent();int index=Integer.parseInt(token);
                if(index>=multi.getCount())throw new MessagingException("Missing attachment");
                part=multi.getBodyPart(index);
            }
        }
        if(part.getFileName()==null&&!Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()))throw new MessagingException("Not an attachment");
        return part;
    }
}

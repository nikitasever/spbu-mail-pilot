package ru.local.spbumailpilot;

import java.io.*;
import java.util.Locale;

/** Native PDF bounds without Android dependencies, shared with regression fixtures. */
final class PdfPreviewPolicy {
    static final int MAX_PIXELS=2_000_000,MAX_BITMAP_SIDE=4096,MAX_DISPLAY_SIDE=16384;
    static boolean pdf(String name,String mime){return "application/pdf".equalsIgnoreCase(mime)||name!=null&&name.toLowerCase(Locale.ROOT).endsWith(".pdf");}
    static File file(File cache,String token)throws IOException{
        if(token==null||token.isEmpty()||token.length()>240||token.equals(".")||token.equals("..")||token.contains("/")||token.contains("\\")||token.matches("(?s).*\\p{Cntrl}.*"))throw new IOException("Invalid PDF reference");
        File base=cache.getCanonicalFile(),file=new File(base,token).getCanonicalFile();
        if(!base.equals(file.getParentFile())||!file.isFile()||file.length()==0||file.length()>AttachmentFiles.MAX_BYTES)throw new IOException("PDF unavailable");return file;
    }
    static int page(String input,int count){
        try{if(input==null||!input.matches("[0-9]{1,10}"))return -1;long value=Long.parseLong(input);return value>=1&&value<=count?(int)value-1:-1;}catch(Exception invalid){return -1;}
    }
    static final class Size {
        final int width,height,displayWidth,displayHeight;
        Size(int w,int h,int dw,int dh){width=w;height=h;displayWidth=dw;displayHeight=dh;}
    }
    static Size size(int pageWidth,int pageHeight,int viewport,int zoom){
        if(pageWidth<1||pageHeight<1||viewport<1||zoom<100||zoom>300)throw new IllegalArgumentException("Invalid page size");
        long dw=Math.min(MAX_DISPLAY_SIDE,Math.max(1,(long)viewport*zoom/100)),dh=Math.max(1,dw*pageHeight/pageWidth);
        if(dh>MAX_DISPLAY_SIDE)throw new IllegalArgumentException("Page too tall");
        double factor=Math.min(1,Math.min((double)MAX_BITMAP_SIDE/Math.max(dw,dh),Math.sqrt((double)MAX_PIXELS/(dw*dh))));
        int w=Math.max(1,(int)(dw*factor)),h=Math.max(1,(int)(dh*factor));return new Size(w,h,(int)dw,(int)dh);
    }
}

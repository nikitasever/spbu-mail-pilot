package ru.local.spbumailpilot;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class PdfPreviewPolicyTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 static void refused(File dir,String token)throws Exception{try{PdfPreviewPolicy.file(dir,token);throw new AssertionError();}catch(IOException expected){}}
 public static void main(String[] args)throws Exception{
  check(PdfPreviewPolicy.pdf("Файл.PDF",null)&&PdfPreviewPolicy.pdf("attachment","APPLICATION/PDF"));check(!PdfPreviewPolicy.pdf("file.docx",null));
  File root=Files.createTempDirectory("pdf-policy-").toFile(),cache=new File(root,"attachments");check(cache.mkdir());File file=new File(cache,"mail-файл.pdf");Files.write(file.toPath(),"%PDF-fixture".getBytes("UTF-8"));check(PdfPreviewPolicy.file(cache,file.getName()).equals(file.getCanonicalFile()));
  for(String invalid:new String[]{"../mail-файл.pdf","..",".","a/b.pdf","a\\b.pdf","bad\n.pdf","missing.pdf",""})refused(cache,invalid);
  File empty=new File(cache,"empty.pdf");check(empty.createNewFile());refused(cache,empty.getName());File huge=new File(cache,"huge.pdf");try(RandomAccessFile sparse=new RandomAccessFile(huge,"rw")){sparse.setLength(AttachmentFiles.MAX_BYTES+1);}refused(cache,huge.getName());
  File outside=new File(root,"outside.pdf");Files.write(outside.toPath(),new byte[]{1});Files.createSymbolicLink(new File(cache,"link.pdf").toPath(),outside.toPath());refused(cache,"link.pdf");
  check(PdfPreviewPolicy.page("1",3)==0&&PdfPreviewPolicy.page("3",3)==2);for(String value:new String[]{"0","4","-1","1.5","9999999999",""," 1","2147483648"})check(PdfPreviewPolicy.page(value,3)==-1);check(PdfPreviewPolicy.page("2147483647",Integer.MAX_VALUE)==Integer.MAX_VALUE-1);
  Random random=new Random(54);
  for(int i=0;i<2000;i++){
   int width=1+random.nextInt(20000),height=1+random.nextInt(20000),view=100+random.nextInt(5000),zoom=100+random.nextInt(201);
   try{PdfPreviewPolicy.Size s=PdfPreviewPolicy.size(width,height,view,zoom);check(s.width>0&&s.height>0&&s.width<=4096&&s.height<=4096);check((long)s.width*s.height<=PdfPreviewPolicy.MAX_PIXELS);check(s.displayWidth<=16384&&s.displayHeight<=16384);}catch(IllegalArgumentException tooTall){check((long)Math.min(16384,(long)view*zoom/100)*height/width>16384);}
  }
  PdfPreviewPolicy.Size a=PdfPreviewPolicy.size(595,842,800,100),b=PdfPreviewPolicy.size(595,842,800,300);check(a.displayWidth==800&&b.displayWidth==2400&&b.width<b.displayWidth);
  check(PdfPreviewPolicy.size(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,300).width>0);
  for(int[] bad:new int[][]{{0,1,800,100},{1,0,800,100},{1,1,0,100},{1,1,800,99},{1,1,800,301},{1,Integer.MAX_VALUE,800,100}})try{PdfPreviewPolicy.size(bad[0],bad[1],bad[2],bad[3]);throw new AssertionError();}catch(IllegalArgumentException expected){}
  System.out.println("PASS: PDF type detection, private-cache/file-size/symlink isolation, page input overflow/ranges, bounded bitmap memory and dimensions across2000 page/zoom shapes. Native PdfRenderer itself requires Android device validation.");
 }
}

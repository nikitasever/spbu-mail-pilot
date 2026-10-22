package ru.local.spbumailpilot;
import android.app.Activity;
import android.content.*;
import android.net.Uri;
import java.io.*;
/** Shared by both Compose variants, file library, reader and WebView bridge. */
final class AttachmentOpen {
 static void open(Activity activity,File file,String name,String mime,String appearance)throws Exception{open(activity,file,name,mime,appearance,0);}
 static void open(Activity activity,File file,String name,String mime,String appearance,int nesting)throws Exception{
  Intent internal=intent(activity,file,name,mime,appearance).putExtra("nesting",nesting);
  boolean builtIn=EmlDocument.eml(name,mime)||PdfPreviewPolicy.pdf(name,mime)||DocxReader.docx(name,mime)||mime.startsWith("image/")||mime.startsWith("text/")||mime.equals("application/json")||mime.equals("application/xml")||mime.equals("application/zip");
  if(!builtIn){
   android.net.Uri uri=internal.getData();
   Intent external=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
   external.setClipData(ClipData.newRawUri(name,uri));
   try{activity.startActivity(external);return;}catch(ActivityNotFoundException missing){}
  }
  activity.startActivity(internal);
 }
 static Intent intent(Activity activity,File input,String name,String mime,String appearance)throws Exception{
  File file=PdfPreviewPolicy.file(new File(activity.getCacheDir(),"attachments"),input.getName());
  if(!input.getCanonicalFile().equals(file))throw new IOException("Invalid attachment reference");
  Uri uri=new Uri.Builder().scheme("content").authority(AttachmentProvider.AUTHORITY).appendPath(file.getName()).build();
  Class<?> viewer=EmlDocument.eml(name,mime)?FilePreviewActivity.class:PdfPreviewPolicy.pdf(name,mime)?PdfPreviewActivity.class:DocxReader.docx(name,mime)?DocxPreviewActivity.class:FilePreviewActivity.class;
  return new Intent(activity,viewer).setData(uri).putExtra("name",AttachmentFiles.safeName(name)).putExtra("mime",mime).putExtra("appearance",appearance);
 }
}

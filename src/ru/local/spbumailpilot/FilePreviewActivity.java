package ru.local.spbumailpilot;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.text.Html;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import org.json.*;

/** Private, local-only attachment view; HTML is text, never an executable WebView. */
public final class FilePreviewActivity extends Activity implements AppLockAware {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private volatile boolean closing,resumed;private volatile long generation;
 private String name,mime,appearance;private Uri uri;private File source,pendingSave;private Uri destination;
 private EmlDocument document;private LinearLayout content;private TextView status;private Button save,external;
 private int nesting;private ScrollView scroll;private int position;
 @Override public String getNativeAppearanceMode(){return appearance==null?"system":appearance;}
 private int dp(int n){return NativeUi.dp(this,n);}
 private boolean active(long ticket){return !closing&&resumed&&ticket==generation&&!AppLock.locked(this);}
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  appearance=AppearancePolicy.mode(getIntent().getStringExtra("appearance"));name=AttachmentFiles.safeName(getIntent().getStringExtra("name"));
  mime=AttachmentFiles.mime(name,getIntent().getStringExtra("mime"));uri=getIntent().getData();nesting=getIntent().getIntExtra("nesting",0);
  if(saved!=null)position=saved.getInt("position",0);build();
 }
 private TextView label(String value,int size){TextView v=new TextView(NativeUi.context(this,false));v.setText(value);v.setTextSize(size);v.setTextColor(NativeUi.ink(this));v.setTextIsSelectable(true);v.setPadding(dp(4),dp(6),dp(4),dp(6));return v;}
 private Button button(String title,Runnable action){Button v=NativeUi.button(this,false);v.setText(title);v.setMinHeight(dp(48));v.setOnClickListener(w->action.run());return v;}
 private void build(){
  NativeUi.bars(this);LinearLayout root=new LinearLayout(NativeUi.context(this,false));root.setOrientation(1);root.setPadding(dp(12),dp(8),dp(12),dp(8));root.setBackgroundColor(NativeUi.paper(this));
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(button("←",()->finish()),new LinearLayout.LayoutParams(dp(48),dp(48)));
  TextView title=label(name,17);title.setTypeface(null,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
  status=label("Загрузка…",12);status.setTextColor(NativeUi.muted(this));root.addView(status);
  scroll=new ScrollView(this);content=new LinearLayout(NativeUi.context(this,false));content.setOrientation(1);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout actions=new LinearLayout(this);save=button("Сохранить",()->chooseSave(source,name,mime));external=button("Другое приложение",()->outside());
  actions.addView(save,new LinearLayout.LayoutParams(0,-2,1));actions.addView(external,new LinearLayout.LayoutParams(0,-2,1));root.addView(actions);save.setEnabled(false);external.setEnabled(false);setContentView(root);
 }
 private File checkedSource()throws Exception{
  if(uri==null||!"content".equals(uri.getScheme())||!AttachmentProvider.AUTHORITY.equals(uri.getAuthority())||uri.getPathSegments().size()!=1||uri.getQuery()!=null||uri.getFragment()!=null||nesting<0||nesting>8)throw new IOException("Invalid preview reference");
  return PdfPreviewPolicy.file(new File(getCacheDir(),"attachments"),uri.getLastPathSegment());
 }
 private void load(){
  if(closing||!resumed||AppLock.locked(this))return;long ticket=++generation;status.setText("Загрузка…");
  worker.execute(()->{try{
   File file=checkedSource();EmlDocument eml=null;String text=null;Bitmap image=null;
   if(EmlDocument.eml(name,mime))eml=EmlDocument.read(file);
   else if(mime.startsWith("image/"))image=image(file);
   else if(mime.startsWith("text/")||mime.equals("application/json")||mime.equals("application/xml"))text=text(file);
   else if(mime.equals("application/zip"))text=archive(file);
   final EmlDocument message=eml;final String value=text;final Bitmap bitmap=image;
   runOnUiThread(()->{if(!active(ticket)){if(bitmap!=null)bitmap.recycle();return;}source=file;document=message;content.removeAllViews();save.setEnabled(true);external.setEnabled(true);
    if(message!=null){renderMessage(message);status.setText("Пересланное письмо · локальный просмотр");}
    else if(bitmap!=null){ImageView view=new ImageView(this);view.setAdjustViewBounds(true);view.setImageBitmap(bitmap);content.addView(view,new LinearLayout.LayoutParams(-1,-2));status.setText("Предпросмотр изображения; анимация и полный вид — в другом приложении.");}
    else if(value!=null){TextView body=label("",16);body.setText(mime.equals("text/html")?Html.fromHtml(value,Html.FROM_HTML_MODE_COMPACT):value);content.addView(body);status.setText(mime.equals("application/zip")?"Содержимое ZIP · файлы не извлекаются автоматически":"Текстовый просмотр · без запуска сценариев");}
    else {content.addView(label("Для полного просмотра этого формата откройте файл в другом приложении. Его также можно сохранить на телефон.",16));status.setText("Файл доступен · "+mime);}
    scroll.post(()->{if(active(ticket))scroll.scrollTo(0,position);});
   });
  }catch(Exception|OutOfMemoryError failure){runOnUiThread(()->{if(active(ticket)){try{source=checkedSource();save.setEnabled(true);external.setEnabled(true);}catch(Exception ignored){}status.setText("Встроенный просмотр недоступен: файл может быть повреждён, защищён или превышать лимит. Для EML лимит — 20 МБ. Сохраните файл или откройте в другом приложении.");}});}});
 }
 private void renderMessage(EmlDocument eml){
  TextView subject=label(eml.subject.isEmpty()?"Без темы":eml.subject,20);subject.setTypeface(null,Typeface.BOLD);content.addView(subject);
  content.addView(label("От: "+eml.from+"\nКому: "+eml.to+"\nДата: "+eml.date,13));
  if(eml.files.length()>0){content.addView(label("Вложения · "+eml.files.length(),16));for(int i=0;i<eml.files.length();i++){
   JSONObject item=eml.files.optJSONObject(i);if(item==null)continue;String path=item.optString("path"),title=item.optString("name","Вложение");
   content.addView(label(title,15));LinearLayout row=new LinearLayout(this);
   row.addView(button("Открыть",()->nested(path,false)),new LinearLayout.LayoutParams(0,-2,1));row.addView(button("Сохранить",()->nested(path,true)),new LinearLayout.LayoutParams(0,-2,1));content.addView(row);
  }}
  if(eml.segments.isEmpty())content.addView(label("Текстовая часть письма отсутствует.",16));
  for(EmlDocument.Segment segment:eml.segments){TextView body=label("",16);body.setText(segment.html?Html.fromHtml(segment.text,Html.FROM_HTML_MODE_COMPACT):segment.text);content.addView(body);}
 }
 private void nested(String path,boolean export){
  if(document==null||!resumed||AppLock.locked(this))return;EmlDocument message=document;long ticket=generation;status.setText("Подготовка вложения…");
  worker.execute(()->{File made=null;try{
   JSONObject result=message.extract(path,new File(getCacheDir(),"attachments"),()->!active(ticket));made=new File(result.getString("file"));final File file=made;
   runOnUiThread(()->{if(!active(ticket)){file.delete();return;}try{String title=result.getString("name"),type=result.getString("mime");if(export)chooseSave(file,title,type);else {
    AttachmentOpen.open(this,file,title,type,appearance,nesting+1);
   }}catch(Exception unavailable){status.setText("Не удалось открыть вложение. Попробуйте сохранить файл.");}});
  }catch(Exception failure){if(made!=null)made.delete();runOnUiThread(()->{if(active(ticket))status.setText("Не удалось подготовить вложение; файл не изменён.");});}});
 }
 private void chooseSave(File file,String title,String type){
  if(file==null||!resumed||AppLock.locked(this)||pendingSave!=null)return;
  try{File checked=PdfPreviewPolicy.file(new File(getCacheDir(),"attachments"),file.getName());if(!checked.equals(file.getCanonicalFile()))return;pendingSave=checked;
   startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type).putExtra(Intent.EXTRA_TITLE,AttachmentFiles.safeName(title)),601);
  }catch(Exception unavailable){pendingSave=null;status.setText("Не удалось открыть сохранение файла.");}
 }
 private void outside(){
  if(source==null||!resumed||AppLock.locked(this))return;
  try{Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri(name,uri));startActivity(intent);}
  catch(ActivityNotFoundException missing){status.setText("На телефоне нет приложения для этого формата. Используйте «Сохранить» или установите подходящий просмотрщик.");}
  catch(Exception unavailable){status.setText("Не удалось открыть файл в другом приложении.");}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);AppLock.result(this,request,result);if(request==601){if(result==RESULT_OK&&data!=null&&data.getData()!=null&&"content".equals(data.getData().getScheme()))destination=data.getData();else pendingSave=null;flushSave();}}
 private void flushSave(){
  if(pendingSave==null||destination==null||!resumed||AppLock.locked(this))return;File file=pendingSave;Uri target=destination;pendingSave=null;destination=null;long ticket=generation;
  worker.execute(()->{try{
   File checked=PdfPreviewPolicy.file(new File(getCacheDir(),"attachments"),file.getName());
   try(InputStream in=new FileInputStream(checked);OutputStream out=getContentResolver().openOutputStream(target,"wt")){
    if(out==null)throw new IOException("No destination");byte[] bytes=new byte[32768];int n;long size=0;
    while((n=in.read(bytes))!=-1){if(!active(ticket))throw new IOException("Cancelled");size+=n;if(size>AttachmentFiles.MAX_BYTES)throw new IOException("Too large");out.write(bytes,0,n);}
   }
   runOnUiThread(()->{if(active(ticket))status.setText("Файл сохранён.");});
  }catch(Exception failure){runOnUiThread(()->{if(active(ticket))status.setText("Сохранение не завершено. Проверьте выбранное место и повторите.");});}});
 }
 @Override protected void onResume(){super.onResume();resumed=true;load();flushSave();}
 @Override protected void onPause(){position=scroll.getScrollY();resumed=false;generation++;document=null;content.removeAllViews();super.onPause();}
 @Override public void appLockChanged(){if(closing||content==null)return;if(AppLock.locked(this)){generation++;document=null;content.removeAllViews();save.setEnabled(false);external.setEnabled(false);}else if(resumed){load();flushSave();}}
 @Override protected void onSaveInstanceState(Bundle saved){super.onSaveInstanceState(saved);saved.putInt("position",scroll.getScrollY());}
 @Override protected void onDestroy(){closing=true;resumed=false;generation++;document=null;content.removeAllViews();worker.shutdown();super.onDestroy();}
 private static String text(File file)throws Exception{
  byte[] bytes;boolean truncated;try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buffer=new byte[4096];int n;while(out.size()<524288&&(n=in.read(buffer,0,Math.min(buffer.length,524288-out.size())))!=-1)out.write(buffer,0,n);truncated=in.read()!=-1;bytes=out.toByteArray();
  }
  String result;int skip=0;Charset charset=null;
  if(bytes.length>=2&&bytes[0]==(byte)0xff&&bytes[1]==(byte)0xfe){charset=StandardCharsets.UTF_16LE;skip=2;}
  else if(bytes.length>=2&&bytes[0]==(byte)0xfe&&bytes[1]==(byte)0xff){charset=StandardCharsets.UTF_16BE;skip=2;}
  else if(bytes.length>=3&&bytes[0]==(byte)0xef&&bytes[1]==(byte)0xbb&&bytes[2]==(byte)0xbf){charset=StandardCharsets.UTF_8;skip=3;}
  if(charset!=null)result=new String(bytes,skip,bytes.length-skip,charset);else try{result=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException legacy){result=new String(bytes,Charset.forName("windows-1251"));}
  if(result.length()>100000){int end=100000;if(Character.isHighSurrogate(result.charAt(end-1)))end--;result=result.substring(0,end);truncated=true;}
  return result+(truncated?"\n[Показана часть файла]":"");
 }
 private static String archive(File file)throws Exception{StringBuilder out=new StringBuilder();try(ZipFile zip=new ZipFile(file)){Enumeration<? extends ZipEntry> entries=zip.entries();int count=0;while(entries.hasMoreElements()&&count++<500){ZipEntry e=entries.nextElement();String title=e.getName().replaceAll("[\\p{Cntrl}]"," ");out.append(title.length()>400?title.substring(0,400):title).append(e.isDirectory()?"/":"").append('\n');}if(entries.hasMoreElements())out.append("[Показаны первые 500 записей]");}return out.toString();}
 private static Bitmap image(File file)throws Exception{BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),options);if(options.outWidth<1||options.outHeight<1||options.outWidth>200000||options.outHeight>200000)throw new IOException("Invalid image size");options.inSampleSize=1;while((long)(options.outWidth/options.inSampleSize)*(options.outHeight/options.inSampleSize)>2000000||options.outWidth/options.inSampleSize>4096||options.outHeight/options.inSampleSize>4096)options.inSampleSize*=2;options.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeFile(file.getAbsolutePath(),options);if(bitmap==null)throw new IOException("Unsupported image");return bitmap;}
}

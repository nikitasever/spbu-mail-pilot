package ru.local.spbumailpilot;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** Selectable, locally decoded DOCX text. No WebView, relationship fetch or field evaluation. */
public final class DocxPreviewActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean closing,validFile;private Uri uri;private String title,appearance;
    private TextView status,text;private ScrollView scroll;private int savedScroll;
    boolean nativeDark(){return AppearancePolicy.dark(appearance,(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES);}
    private int dp(int n){return NativeUi.dp(this,n);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);appearance=AppearancePolicy.mode(getIntent().getStringExtra("appearance"));title=AttachmentFiles.safeName(getIntent().getStringExtra("name"));uri=getIntent().getData();savedScroll=saved==null?0:saved.getInt("scroll",0);build();
        worker.execute(()->{try{
            if(uri==null||!"content".equals(uri.getScheme())||!AttachmentProvider.AUTHORITY.equals(uri.getAuthority())||uri.getPathSegments().size()!=1||uri.getQuery()!=null||uri.getFragment()!=null)throw new IOException("Invalid document reference");
            File file=PdfPreviewPolicy.file(new File(getCacheDir(),"attachments"),uri.getLastPathSegment());validFile=true;DocxReader document=DocxReader.read(file);
            SpannableStringBuilder content=new SpannableStringBuilder();
            for(DocxReader.Block block:document.blocks){if(closing)return;int start=content.length();
                for(DocxReader.Run run:block.runs){int offset=content.length();content.append(run.text);int end=content.length();if(run.bold||run.italic)content.setSpan(new StyleSpan(run.bold?(run.italic?Typeface.BOLD_ITALIC:Typeface.BOLD):Typeface.ITALIC),offset,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);if(run.underline)content.setSpan(new UnderlineSpan(),offset,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
                if(block.heading>0&&content.length()>start){content.setSpan(new StyleSpan(Typeface.BOLD),start,content.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);content.setSpan(new RelativeSizeSpan(block.heading==1?1.3f:1.15f),start,content.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
                content.append("\n\n");if(block.centered)content.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),start,content.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);if(block.list)content.setSpan(new BulletSpan(dp(12)),start,content.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            runOnUiThread(()->{if(closing)return;text.setText(content);status.setText(content.length()==0?"В документе нет поддерживаемого текста.":"Текстовый просмотр DOCX");scroll.post(()->{if(!closing)scroll.scrollTo(0,savedScroll);});});
        }catch(Exception|OutOfMemoryError failure){runOnUiThread(()->{if(!closing){status.setText("Не удалось показать DOCX. Он может быть повреждён, защищён или слишком большим для встроенного просмотра. Откройте его в другом приложении.");text.setText("");}});}});
    }
    private TextView label(String value,int size){TextView view=new TextView(NativeUi.context(this,false));view.setText(value);view.setTextSize(size);view.setTextColor(NativeUi.ink(this));return view;}
    private void build(){
        NativeUi.bars(this);LinearLayout root=new LinearLayout(NativeUi.context(this,false));root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(8),dp(12),dp(8));root.setBackgroundColor(NativeUi.paper(this));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);Button back=NativeUi.button(this,false);back.setText("←");back.setTextSize(17);back.setPadding(dp(4),dp(4),dp(4),dp(4));back.setContentDescription("Вернуться к письму");back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        boolean landscape=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        TextView name=label(title,16);name.setMaxLines(landscape?1:2);name.setEllipsize(TextUtils.TruncateAt.END);name.setPadding(dp(10),0,0,0);header.addView(name,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
        status=label("Открываем DOCX…",13);status.setPadding(dp(4),dp(6),dp(4),dp(6));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);
        scroll=new ScrollView(this);text=label("",17);text.setTextIsSelectable(true);text.setLineSpacing(dp(4),1.25f);text.setPadding(dp(4),dp(8),dp(4),dp(12));scroll.addView(text);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView note=label("Текст, заголовки и базовые стили. Таблицы — построчно; изображения, нумерация и разметка могут отличаться. Полный вид — в другом приложении.",11);note.setMaxLines(landscape?1:4);note.setEllipsize(TextUtils.TruncateAt.END);note.setTextColor(NativeUi.muted(this));note.setPadding(dp(4),dp(6),dp(4),dp(6));root.addView(note);
        Button external=NativeUi.button(this,false);external.setText("Открыть в другом приложении");external.setTextSize(13);external.setMaxLines(1);external.setEllipsize(TextUtils.TruncateAt.END);external.setPadding(dp(8),dp(6),dp(8),dp(6));external.setOnClickListener(v->external());root.addView(external,new LinearLayout.LayoutParams(-1,dp(48)));setContentView(root);
    }
    private void external(){
        if(!validFile||uri==null){status.setText("Файл недоступен. Вернитесь к письму и откройте вложение снова.");return;}
        try{Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.openxmlformats-officedocument.wordprocessingml.document").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri(title,uri));startActivity(intent);}catch(ActivityNotFoundException missing){status.setText("На телефоне нет просмотрщика DOCX.");}catch(Exception unavailable){status.setText("Не удалось открыть документ в другом приложении.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);AppLock.result(this,request,result);}
    @Override protected void onSaveInstanceState(Bundle saved){super.onSaveInstanceState(saved);saved.putInt("scroll",scroll.getScrollY());}
    @Override protected void onDestroy(){closing=true;text.setText("");worker.shutdown();super.onDestroy();}
}

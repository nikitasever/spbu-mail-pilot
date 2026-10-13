package ru.local.spbumailpilot;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** Single-page native rendering. No PDF scripts, remote URLs or external viewer grant by default. */
public final class PdfPreviewActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private PdfRenderer renderer;
    private Uri uri;private String title,appearance;
    private volatile boolean closing,validFile;private volatile long generation;
    private int page=0,zoom=100,pages=0,shownPage=-1,shownWidth=0;private boolean landscape;
    private ImageView image;private TextView status;private Button previous,next,jump,minus,plus,fit;
    private ScrollView vertical;private HorizontalScrollView horizontal;
    private Bundle restored;
    boolean nativeDark(){return AppearancePolicy.dark(appearance,(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES);}
    private int dp(int value){return NativeUi.dp(this,value);}
    private TextView label(String text,int size){TextView view=new TextView(NativeUi.context(this,false));view.setText(text);view.setTextColor(NativeUi.ink(this));view.setTextSize(size);return view;}
    private Button action(LinearLayout row,String text,String description,Runnable callback){
        Button button=NativeUi.button(this,false);button.setText(text);button.setContentDescription(description);button.setTextSize(13);button.setMaxLines(1);button.setEllipsize(android.text.TextUtils.TruncateAt.END);button.setPadding(dp(4),dp(6),dp(4),dp(6));button.setOnClickListener(v->callback.run());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(landscape?dp(80):0,dp(48),landscape?0:1);params.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(button,params);return button;
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);landscape=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;appearance=AppearancePolicy.mode(getIntent().getStringExtra("appearance"));title=AttachmentFiles.safeName(getIntent().getStringExtra("name"));uri=getIntent().getData();restored=saved;
        if(saved!=null){page=Math.max(0,saved.getInt("page",0));zoom=Math.max(100,Math.min(300,saved.getInt("zoom",100)));}
        build();
        worker.execute(()->{
            ParcelFileDescriptor descriptor=null;
            try{
                if(uri==null||!"content".equals(uri.getScheme())||!AttachmentProvider.AUTHORITY.equals(uri.getAuthority())||uri.getPathSegments().size()!=1||uri.getQuery()!=null||uri.getFragment()!=null)throw new IOException("Invalid PDF reference");
                PdfPreviewPolicy.file(new File(getCacheDir(),"attachments"),uri.getLastPathSegment());
                validFile=true;
                descriptor=getContentResolver().openFileDescriptor(uri,"r");if(descriptor==null)throw new IOException("Missing PDF");
                if(closing)return;renderer=new PdfRenderer(descriptor);descriptor=null;
                final int count=renderer.getPageCount();if(count<1)throw new IOException("Empty PDF");
                runOnUiThread(()->{if(closing)return;pages=count;page=Math.min(page,count-1);render();});
            }catch(Exception|OutOfMemoryError invalid){runOnUiThread(()->{if(!closing)error("Не удалось открыть PDF. Возможно, он защищён паролем, повреждён или удалён из кэша.");});}
            finally{if(descriptor!=null)try{descriptor.close();}catch(IOException ignored){}}
        });
    }
    private void build(){
        NativeUi.bars(this);LinearLayout root=new LinearLayout(NativeUi.context(this,false));root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(10),dp(8),dp(10),dp(8));root.setBackgroundColor(NativeUi.paper(this));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        Button back=NativeUi.button(this,false);back.setText("←");back.setTextSize(17);back.setPadding(dp(4),dp(4),dp(4),dp(4));back.setMaxLines(1);back.setContentDescription("Вернуться к письму");back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView name=label(title,16);name.setMaxLines(landscape?1:2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(dp(10),0,0,0);header.addView(name,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
        status=label("Открываем PDF…",13);status.setPadding(dp(4),dp(6),dp(4),dp(6));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);status.setMinHeight(dp(38));root.addView(status);
        vertical=new ScrollView(this);horizontal=new HorizontalScrollView(this);image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_XY);image.setAdjustViewBounds(false);
        horizontal.addView(image,new android.widget.FrameLayout.LayoutParams(1,1));vertical.addView(horizontal,new android.widget.FrameLayout.LayoutParams(-1,-2));root.addView(vertical,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout paging=new LinearLayout(this);previous=action(paging,"Назад","Предыдущая страница",()->changePage(page-1));jump=action(paging,"Страница","Перейти к странице",this::choosePage);next=action(paging,"Далее","Следующая страница",()->changePage(page+1));
        if(landscape){HorizontalScrollView toolbar=new HorizontalScrollView(this);toolbar.addView(paging);root.addView(toolbar);}else root.addView(paging);
        LinearLayout scaling=landscape?paging:new LinearLayout(this);minus=action(scaling,"−","Уменьшить масштаб",()->changeZoom(zoom-50));fit=action(scaling,"По ширине","Уместить страницу по ширине",()->changeZoom(100));plus=action(scaling,"+","Увеличить масштаб",()->changeZoom(zoom+50));if(!landscape)root.addView(scaling);
        Button external=NativeUi.button(this,false);external.setText("Открыть в другом приложении");external.setTextSize(13);external.setMaxLines(1);external.setEllipsize(android.text.TextUtils.TruncateAt.END);external.setPadding(dp(8),dp(6),dp(8),dp(6));external.setOnClickListener(v->external());root.addView(external,new LinearLayout.LayoutParams(-1,dp(48)));
        setContentView(root);controls(false);vertical.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(pages>0&&r-l>0&&r-l!=or-ol)render();});
    }
    private void controls(boolean ready){previous.setEnabled(ready&&page>0);next.setEnabled(ready&&page+1<pages);jump.setEnabled(ready);minus.setEnabled(ready&&zoom>100);plus.setEnabled(ready&&zoom<300);fit.setEnabled(ready&&zoom!=100);}
    private void changePage(int target){if(target<0||target>=pages)return;page=target;render();}
    private void changeZoom(int target){if(target<100||target>300||target==zoom)return;zoom=target;render();}
    private void render(){
        if(closing||pages<1)return;int width=vertical.getWidth();if(width<1){vertical.post(this::render);return;}
        final long ticket=++generation;final int selected=page,percent=zoom,viewport=width;controls(false);status.setText("Страница "+(selected+1)+" из "+pages+" · "+percent+"% · загрузка…");
        worker.execute(()->{
            Bitmap bitmap=null;
            try{
                if(closing||ticket!=generation||renderer==null)return;
                final PdfPreviewPolicy.Size size;
                try(PdfRenderer.Page source=renderer.openPage(selected)){
                    size=PdfPreviewPolicy.size(source.getWidth(),source.getHeight(),viewport,percent);
                    bitmap=Bitmap.createBitmap(size.width,size.height,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);
                    source.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                }
                final Bitmap rendered=bitmap;bitmap=null;
                runOnUiThread(()->{
                    if(closing||ticket!=generation){rendered.recycle();return;}
                    int x=0,y=0;
                    if(restored!=null){x=restored.getInt("x",0);y=restored.getInt("y",0);restored=null;}
                    else if(shownPage==selected&&shownWidth>0){float ratio=(float)size.displayWidth/shownWidth;x=Math.round((horizontal.getScrollX()+horizontal.getWidth()/2f)*ratio-horizontal.getWidth()/2f);y=Math.round((vertical.getScrollY()+vertical.getHeight()/2f)*ratio-vertical.getHeight()/2f);}
                    shownPage=selected;shownWidth=size.displayWidth;image.setImageBitmap(rendered);image.setLayoutParams(new android.widget.FrameLayout.LayoutParams(size.displayWidth,size.displayHeight));image.setContentDescription("PDF · страница "+(selected+1)+" из "+pages);
                    status.setText("Страница "+(selected+1)+" из "+pages+" · "+percent+"%");controls(true);
                    final int targetX=x,targetY=y;image.post(()->{if(!closing&&ticket==generation){vertical.scrollTo(0,targetY);horizontal.scrollTo(targetX,0);}});
                });
            }catch(Exception|OutOfMemoryError invalid){runOnUiThread(()->{if(!closing&&ticket==generation)error("Не удалось показать страницу. Попробуйте масштаб по ширине или другое приложение.");});}
            finally{if(bitmap!=null)bitmap.recycle();}
        });
    }
    private void error(String message){status.setText(message);controls(false);image.setImageDrawable(null);shownPage=-1;shownWidth=0;if(pages>0){fit.setEnabled(true);fit.setOnClickListener(v->{zoom=100;render();});previous.setEnabled(page>0);next.setEnabled(page+1<pages);jump.setEnabled(true);}}
    private void choosePage(){
        EditText input=new EditText(NativeUi.context(this,true));input.setInputType(InputType.TYPE_CLASS_NUMBER);input.setSingleLine(true);input.setText(Integer.toString(page+1));input.setSelectAllOnFocus(true);input.setContentDescription("Номер страницы от 1 до "+pages);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(10)});
        AlertDialog dialog=NativeUi.dialog(this).setTitle("Страница · всего "+pages).setView(input).setNegativeButton("Отмена",null).setPositiveButton("Перейти",null).create();
        dialog.setOnShowListener(v->{dialog.getButton(-1).setOnClickListener(button->{int target=PdfPreviewPolicy.page(input.getText().toString(),pages);if(target<0){input.setError("Введите номер от 1 до "+pages);input.requestFocus();return;}dialog.dismiss();changePage(target);});input.requestFocus();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);});dialog.show();
    }
    private void external(){
        if(uri==null||!validFile){status.setText("Файл недоступен. Вернитесь к письму и откройте вложение снова.");return;}
        try{Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri(title,uri));startActivity(intent);}catch(ActivityNotFoundException missing){status.setText("На телефоне нет другого просмотрщика PDF.");}catch(Exception unavailable){status.setText("Не удалось открыть файл в другом приложении.");}
    }
    @Override protected void onSaveInstanceState(Bundle saved){super.onSaveInstanceState(saved);saved.putInt("page",page);saved.putInt("zoom",zoom);saved.putInt("x",horizontal.getScrollX());saved.putInt("y",vertical.getScrollY());}
    @Override protected void onDestroy(){closing=true;++generation;image.setImageDrawable(null);worker.execute(()->{if(renderer!=null){try{renderer.close();}catch(Exception ignored){}renderer=null;}});worker.shutdown();super.onDestroy();}
}

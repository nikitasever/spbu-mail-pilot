package ru.local.spbumailpilot;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.webkit.*;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService calendarWorker=Executors.newSingleThreadExecutor();
    private MailReader reader;
    private CredentialVault vault;
    private volatile SavedAccounts.Login sessionLogin;
    private WebView web;
    private SharedPreferences local;
    private String account = "";
    private volatile boolean closing;
    private boolean loginBusy;
    private boolean hasSavedAccounts;
    private boolean loginScreen;
    private boolean pendingTestNotice;
    private volatile long calendarSerial;
    private final java.util.concurrent.atomic.AtomicBoolean sending=new java.util.concurrent.atomic.AtomicBoolean();
    private String attachmentPickRequest,attachmentPickOwner;
    private static final int PURPLE = 0xff8051b1;
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xfffaf8fe);
        getWindow().setNavigationBarColor(0xfffaf8fe);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        vault = new CredentialVault(this);
        NoticeCenter.channels(this);
        restoreLogin();
    }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setPadding(0, dp(9), 0, dp(9)); return v;
    }
    private EditText input(LinearLayout box, String label, String value, int type) {
        box.addView(text(label, 14, 0xff726d7c));
        EditText v = new EditText(this); v.setSingleLine(true); v.setText(value); v.setTextSize(16); v.setInputType(type); v.setPadding(dp(12),dp(12),dp(12),dp(12));
        GradientDrawable shape = new GradientDrawable();shape.setColor(Color.WHITE);shape.setCornerRadius(dp(12));shape.setStroke(dp(1),0xffded6e8);v.setBackground(shape);
        v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);box.addView(v,new LinearLayout.LayoutParams(-1,dp(52)));return v;
    }
    private void showLogin() {
        if (isFinishing() || closing) return;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        loginScreen=true;
        loginBusy = false;
        ScrollView scroll = new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xfffaf8fe);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(28),dp(24),dp(24));scroll.addView(box);setContentView(scroll);
        TextView heading=text("Почта СПбГУ",29,0xff29262e);heading.setTypeface(null,Typeface.BOLD);box.addView(heading);
        box.addView(text("Тестовая версия 0.10.1 · реальный ящик",14,0xff8051b1));
        box.addView(text("Данные аккаунтов сохраняются на телефоне в зашифрованном виде. Можно переключать ящики без повторного ввода пароля. «Выйти» закрывает ящик, сохраняя его данные; удалить их можно в списке аккаунтов.",15,0xff726d7c));
        EditText user=input(box,"Логин или полный адрес почты","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password=input(box,"Пароль","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText host=input(box,"Сервер IMAP","mail.spbu.ru",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        EditText port=input(box,"Порт TLS","993",InputType.TYPE_CLASS_NUMBER);
        TextView status=text("",14,0xff9b422b);box.addView(status);
        Button connect=new Button(this);connect.setText("Подключить ящик");connect.setTextColor(Color.WHITE);connect.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE));box.addView(connect,new LinearLayout.LayoutParams(-1,dp(56)));
        box.addView(text("Для входа нужен IMAP. SMTP для отправки можно настроить в приложении. Звонки открываются в «Телефоне». Мессенджер СПбГУ пока не подключён.",13,0xff726d7c));
        Button accounts=new Button(this);accounts.setText("Сохранённые аккаунты");accounts.setOnClickListener(v->{if(!loginBusy)showAccounts();});box.addView(accounts);
        connect.setOnClickListener(v->{
            if (loginBusy) return;
            String u=user.getText().toString().trim(),h=host.getText().toString().trim(),pw=password.getText().toString();
            int p;try{p=Integer.parseInt(port.getText().toString());}catch(Exception e){status.setText("Проверьте порт подключения.");return;}
            if(u.isEmpty()||pw.isEmpty()||!h.matches("[A-Za-z0-9.-]+")||p<1||p>65535){status.setText("Заполните логин, пароль и сервер.");return;}
            loginBusy=true;connect.setEnabled(false);status.setText("Подключение…");
            worker.execute(()->{
                MailReader next=new MailReader();
                try{next.connect(h,p,u,pw);if(closing){next.close();return;}
                    SavedAccounts.Login login=new SavedAccounts.Login(h,p,u,pw);
                    try{vault.save(login);}catch(Exception storage){next.close();runOnUiThread(()->{loginBusy=false;connect.setEnabled(true);status.setText("Не удалось безопасно сохранить вход на телефоне. Повторите попытку.");});return;}
                    attachLogin(next,login);
                    runOnUiThread(()->{password.getText().clear();showMail();});
                }catch(Exception error){next.close();runOnUiThread(()->{loginBusy=false;connect.setEnabled(true);status.setText(error instanceof AuthenticationFailedException?"Сервер не принял вход. Проверьте логин и пароль или доступ к IMAP.":"Не удалось подключиться по TLS. Проверьте сервер, сеть/VPN и доступ к IMAP. Проверка сертификата не отключалась.");});}
            });
        });
    }
    private void attachLogin(MailReader next,SavedAccounts.Login login) throws Exception {
        String oldScope="account-"+digest(login.host+":"+login.port+":"+login.user.toLowerCase(java.util.Locale.ROOT));
        String scope="account-"+digest(login.host.toLowerCase(java.util.Locale.ROOT)+":"+login.port+":"+login.user.toLowerCase(java.util.Locale.ROOT));
        local=getSharedPreferences(scope,MODE_PRIVATE);
        if(!oldScope.equals(scope)&&!local.contains("state")){
            SharedPreferences previous=getSharedPreferences(oldScope,MODE_PRIVATE);
            if(previous.contains("state"))local.edit().putString("state",previous.getString("state","{}")).apply();
        }
        reader=next;account=login.user;sessionLogin=login;
        try{NoticeCenter.seed(this,login,next);NoticeCenter.schedule(this);}catch(Exception notices){}
    }
    private void restoreLogin() {
        if(closing||isFinishing())return;
        showReconnect("Подключение к сохранённому ящику…",true);
        worker.execute(()->{
            SavedAccounts.Login login;
            try{login=vault.load();}catch(Exception storage){runOnUiThread(()->showStorageError());return;}
            if(login==null){runOnUiThread(this::showAccounts);return;}
            MailReader next=new MailReader();
            try{next.connect(login.host,login.port,login.user,login.password);if(closing){next.close();return;}attachLogin(next,login);runOnUiThread(this::showMail);}
            catch(Exception error){next.close();runOnUiThread(()->showReconnect(error instanceof AuthenticationFailedException?"Сервер отклонил сохранённый вход. Если пароль изменился, откройте «Аккаунты» и подключите этот ящик с новым паролем.":"Нет соединения с почтой. Сохранённый вход остаётся на телефоне: проверьте сеть/VPN и повторите подключение.",false));}
        });
    }
    private void showReconnect(String message,boolean busy) {
        if(closing||isFinishing())return;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        loginScreen=false;
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(40),dp(24),dp(24));box.setBackgroundColor(0xfffaf8fe);
        box.addView(text("Почта СПбГУ",29,0xff29262e));box.addView(text(message,16,0xff726d7c));
        if(busy){box.addView(new ProgressBar(this));}else{
            Button retry=new Button(this);retry.setText("Повторить подключение");retry.setOnClickListener(v->restoreLogin());box.addView(retry);
            Button exit=new Button(this);exit.setText("Аккаунты / другой ящик");exit.setOnClickListener(v->leaveAccount());box.addView(exit);
        }
        setContentView(box);
    }
    private void leaveAccount() {
        ++calendarSerial;
        worker.execute(()->{
            try{vault.deactivate();}catch(Exception storage){runOnUiThread(()->Toast.makeText(this,"Не удалось сохранить выход. Повторите попытку.",Toast.LENGTH_LONG).show());return;}
            if(reader!=null)reader.close();reader=null;sessionLogin=null;account="";local=null;
            try{NoticeCenter.schedule(this);}catch(Exception notices){}
            runOnUiThread(()->{if(web!=null){web.removeJavascriptInterface("Pilot");web.destroy();web=null;}showAccounts();});
        });
    }
    private void showStorageError(){
        if(closing||isFinishing())return;
        showReconnect("Сохранённые аккаунты не удалось прочитать. Они не удалены. Можно повторить попытку или сбросить сохранённые входы и добавить ящики заново.",false);
        LinearLayout box=(LinearLayout)((android.view.ViewGroup)findViewById(android.R.id.content)).getChildAt(0);
        Button reset=new Button(this);reset.setText("Сбросить сохранённые входы");reset.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setMessage("Удалить сохранённые данные входа всех аккаунтов? Задачи и заметки останутся.").setPositiveButton("Удалить",(dialog,which)->worker.execute(()->{try{vault.clear();runOnUiThread(this::showLogin);}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Не удалось удалить сохранённые входы.",Toast.LENGTH_LONG).show());}})).setNegativeButton("Отмена",null).show());box.addView(reset);
    }
    private void showAccounts(){
        if(closing||isFinishing())return;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        worker.execute(()->{
            try{
                java.util.ArrayList<SavedAccounts.Login> saved=vault.list();
                runOnUiThread(()->{
                    if(closing||isFinishing())return;
                    hasSavedAccounts=!saved.isEmpty();loginScreen=false;
                    if(saved.isEmpty()){showLogin();return;}
                    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xfffaf8fe);
                    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(28),dp(24),dp(24));scroll.addView(box);
                    box.addView(text("Ваши аккаунты",29,0xff29262e));box.addView(text("Выберите ящик. Пароли сохранены в зашифрованном виде. Задачи и заметки у каждого аккаунта свои.",15,0xff726d7c));
                    for(SavedAccounts.Login login:saved){
                        Button choose=new Button(this);choose.setAllCaps(false);choose.setText(login.user+"\n"+login.host+":"+login.port);choose.setOnClickListener(v->connectSaved(login));box.addView(choose,new LinearLayout.LayoutParams(-1,dp(76)));
                        Button remove=new Button(this);remove.setText("Удалить сохранённый аккаунт");remove.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setMessage("Удалить сохранённый вход для "+login.user+"? Подключения календарей будут удалены с телефона; события сервера, задачи и заметки останутся.").setPositiveButton("Удалить",(dialog,which)->worker.execute(()->{try{new CalendarBook(this,login).clear();vault.remove(login);runOnUiThread(this::showAccounts);}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Не удалось удалить аккаунт.",Toast.LENGTH_LONG).show());}})).setNegativeButton("Отмена",null).show());box.addView(remove);
                    }
                    Button add=new Button(this);add.setText("Добавить другой аккаунт");add.setOnClickListener(v->showLogin());box.addView(add,new LinearLayout.LayoutParams(-1,dp(56)));setContentView(scroll);
                });
            }catch(Exception error){runOnUiThread(this::showStorageError);}
        });
    }
    private void connectSaved(SavedAccounts.Login login){
        showReconnect("Подключение к "+login.user+"…",true);
        worker.execute(()->{
            MailReader next=new MailReader();
            try{next.connect(login.host,login.port,login.user,login.password);if(closing){next.close();return;}vault.save(login);attachLogin(next,login);runOnUiThread(this::showMail);}
            catch(Exception error){next.close();runOnUiThread(()->showAccounts());runOnUiThread(()->Toast.makeText(this,error instanceof AuthenticationFailedException?"Сервер отклонил вход. Добавьте этот ящик заново с новым паролем.":"Не удалось подключиться. Проверьте сеть/VPN и повторите выбор аккаунта.",Toast.LENGTH_LONG).show());}
        });
    }
    private interface ReadOperation {JSONObject run() throws Exception;}
    private JSONObject readWithReconnect(ReadOperation operation) throws Exception {
        try{return operation.run();}catch(Exception disconnected){
            if(!(disconnected instanceof MessagingException)&&!(disconnected instanceof java.io.IOException))throw disconnected;
            if(sessionLogin==null||reader==null)throw disconnected;
            reader.close();reader.connect(sessionLogin.host,sessionLogin.port,sessionLogin.user,sessionLogin.password);
            return operation.run();
        }
    }
    private static String digest(String value)throws Exception{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b));return out.toString();}
    private void showMail() {
        if (closing || isFinishing()) return;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        web=new WebView(this);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setDomStorageEnabled(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setBlockNetworkLoads(true);
        web.addJavascriptInterface(new Bridge(),"Pilot");
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onJsConfirm(WebView view,String url,String message,JsResult result){
                if(closing||isFinishing()){result.cancel();return true;}
                new android.app.AlertDialog.Builder(MainActivity.this).setTitle("Подтверждение").setMessage(message).setPositiveButton("Отключить",(dialog,which)->result.confirm()).setNegativeButton("Отмена",(dialog,which)->result.cancel()).setOnCancelListener(dialog->result.cancel()).show();return true;
            }
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();String path=uri.getPath();
                if("https".equals(uri.getScheme())&&"mail-pilot.local".equals(uri.getHost())&&path!=null&&path.matches("/assets/[a-zA-Z0-9._-]+")){
                    try{String name=path.substring(8);String type=name.endsWith(".js")?"application/javascript":name.endsWith(".css")?"text/css":"text/html";return new WebResourceResponse(type,"UTF-8",getAssets().open(name));}catch(Exception ignored){}
                }
                return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
            }
        });
        setContentView(web);web.loadUrl("https://mail-pilot.local/assets/index.html");
        requestNoticePermission();
    }
    private void requestNoticePermission(){
        if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            SharedPreferences preference=getSharedPreferences("notification-permission",MODE_PRIVATE);
            if(!preference.getBoolean("requested",false)){preference.edit().putBoolean("requested",true).apply();requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},61);}
        }
    }
    private void smtpSettings(){
        if(sessionLogin==null)return;
        SavedAccounts.Login login=sessionLogin;
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),0,dp(20),0);
        EditText host=input(box,"Сервер SMTP",login.smtpHost,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        EditText port=input(box,"Порт",Integer.toString(login.smtpPort),InputType.TYPE_CLASS_NUMBER);
        EditText from=input(box,"Адрес отправителя",login.from,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        Spinner tls=new Spinner(this);tls.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"TLS с начала соединения (обычно 465)","Обязательный STARTTLS (обычно 587)"}));tls.setSelection(login.smtpSsl?0:1);box.addView(tls);
        box.addView(text("Для отправки используются логин и пароль этого аккаунта. Проверка TLS-сертификата включена.",13,0xff726d7c));
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Отправка писем").setView(box).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create();dialog.show();
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{
            try{
                String h=host.getText().toString().trim(),address=from.getText().toString().trim();int p=Integer.parseInt(port.getText().toString());
                if(!h.matches("[A-Za-z0-9.-]+")||p<1||p>65535||OutgoingMail.addresses(address,true).length!=1)throw new IllegalArgumentException();
                SavedAccounts.Login updated=new SavedAccounts.Login(login.host,login.port,login.user,login.password,h,p,tls.getSelectedItemPosition()==0,address);
                worker.execute(()->{try{if(sessionLogin==null||!sessionLogin.identity().equals(login.identity()))return;vault.save(updated);sessionLogin=updated;runOnUiThread(dialog::dismiss);}catch(Exception error){runOnUiThread(()->Toast.makeText(this,"Не удалось сохранить настройки SMTP.",Toast.LENGTH_LONG).show());}});
            }catch(Exception error){Toast.makeText(this,"Проверьте сервер, порт и полный адрес отправителя.",Toast.LENGTH_LONG).show();}
        });
    }
    private java.io.File uploads(SavedAccounts.Login login)throws Exception{return new java.io.File(getFilesDir(),"outgoing/"+NoticeCenter.scope(login));}
    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data){
        super.onActivityResult(requestCode,resultCode,data);if(requestCode!=72||attachmentPickRequest==null)return;
        String request=attachmentPickRequest,owner=attachmentPickOwner;attachmentPickRequest=null;attachmentPickOwner=null;
        if(resultCode!=RESULT_OK||data==null){try{reply(request,new JSONObject().put("cancelled",true));}catch(Exception ignored){}return;}
        java.util.ArrayList<Uri> selected=new java.util.ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)selected.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)selected.add(data.getData());
        worker.execute(()->{
            java.util.ArrayList<java.io.File> created=new java.util.ArrayList<>();
            try{
                SavedAccounts.Login login=sessionLogin;if(login==null||!login.identity().equals(owner)||selected.size()>10)throw new IllegalArgumentException();
                org.json.JSONArray files=new org.json.JSONArray();java.io.File directory=uploads(login);
                for(Uri uri:selected){
                    String name="Вложение";try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}
                    java.io.File file;try(java.io.InputStream input=getContentResolver().openInputStream(uri)){if(input==null)throw new java.io.IOException();file=AttachmentFiles.copy(input,directory,name,AttachmentFiles.MAX_BYTES);}
                    created.add(file);files.put(new JSONObject().put("token",file.getName()).put("name",AttachmentFiles.safeName(name)).put("size",file.length()));
                }
                reply(request,new JSONObject().put("files",files));
            }catch(Exception error){for(java.io.File file:created)file.delete();failure(request,"Не удалось прикрепить файлы. Выберите до 10 файлов размером до 100 МБ каждый.");}
        });
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==61){if(pendingTestNotice&&NoticeCenter.allowed(this))NoticeCenter.test(this);pendingTestNotice=false;worker.execute(()->{try{NoticeCenter.schedule(this);}catch(Exception ignored){}});}}
    @Override protected void onNewIntent(android.content.Intent intent){super.onNewIntent(intent);setIntent(intent);if(web!=null&&intent.getBooleanExtra("openTasks",false))web.evaluateJavascript("window.openTasksFromNotification&&window.openTasksFromNotification()",null);}
    private void reply(String request,JSONObject result){try{result.put("request",request);}catch(Exception ignored){}runOnUiThread(()->{if(web!=null&&!closing)web.evaluateJavascript("window.onNativeResult(JSON.parse("+JSONObject.quote(result.toString())+"))",null);});}
    private void failure(String request,String message){reply(request,new JSONObjectSafe().putValue("error",message));}
    private void readFailure(String request,Exception error,String message){failure(request,error instanceof AuthenticationFailedException?"Сервер отклонил сохранённый вход. Откройте «Профиль» → «Сменить аккаунт» и добавьте этот ящик с новым паролем.":message);}
    private void openAttachmentFile(String request,JSONObject downloaded){
        if(closing||web==null)return;
        try{
            java.io.File file=new java.io.File(downloaded.getString("file"));
            Uri uri=new Uri.Builder().scheme("content").authority(AttachmentProvider.AUTHORITY).appendPath(file.getName()).build();
            android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(uri,downloaded.getString("mime"));intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(android.content.ClipData.newRawUri(downloaded.getString("name"),uri));
            try{startActivity(intent);}catch(android.content.ActivityNotFoundException missing){
                failure(request,"Файл скачан, но на телефоне нет приложения для этого формата. Установите просмотрщик PDF или документов Office и повторите открытие.");return;
            }
            reply(request,new JSONObject().put("opened",true));
        }catch(Exception error){failure(request,"Не удалось открыть скачанный документ. Повторите попытку.");}
    }
    private void calendarReply(SavedAccounts.Login owner,String request,JSONObject result){runOnUiThread(()->{if(!closing&&sessionLogin!=null&&sessionLogin.identity().equals(owner.identity()))reply(request,result);});}
    private void calendarSetup(String month,String request) {
        SavedAccounts.Login owner=sessionLogin;if(owner==null){failure(request,"Войдите в почтовый аккаунт.");return;}
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(12),dp(20),dp(12));scroll.addView(box);
        box.addView(text("Для Mail.ru откройте меню нужного календаря → синхронизация CalDAV и скопируйте ссылку. Используйте пароль внешнего приложения. Для рабочего сервера нужны его CalDAV-ссылка и данные входа.",14,0xff726d7c));
        EditText title=input(box,"Название календаря","Рабочий календарь",InputType.TYPE_CLASS_TEXT);
        EditText url=input(box,"Полная HTTPS-ссылка CalDAV","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        EditText user=input(box,"Логин календаря (полный адрес)",owner.user,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password=input(box,"Пароль календаря / внешнего приложения","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(text("Подключение привязано к этому почтовому аккаунту. Пароль сохраняется зашифрованно. События сервера доступны для просмотра; локальные записи не отправляются в рабочий календарь.",13,0xff726d7c));
        TextView status=text("",14,0xff9b422b);box.addView(status);
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Подключить календарь").setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Подключить",null).create();
        final boolean[] submitted={false};dialog.setOnDismissListener(d->{password.getText().clear();if(!loginScreen)getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);if(!submitted[0])try{reply(request,new JSONObject().put("cancelled",true));}catch(Exception ignored){}});dialog.show();
        dialog.getButton(-1).setOnClickListener(v->{
            final String label=title.getText().toString().trim(),address=url.getText().toString().trim(),login=user.getText().toString().trim(),secret=password.getText().toString();
            try{CalDav.address(address);CalDav.query(month);if(label.isEmpty()||label.length()>120||login.isEmpty()||secret.isEmpty())throw new IllegalArgumentException();}catch(Exception error){status.setText("Укажите название, HTTPS-ссылку, логин и пароль календаря.");return;}
            if(sessionLogin==null||!sessionLogin.identity().equals(owner.identity())){dialog.dismiss();return;}
            submitted[0]=true;dialog.setCancelable(false);dialog.getButton(-1).setEnabled(false);dialog.getButton(-2).setEnabled(false);status.setText("Подключение и загрузка событий…");
            calendarWorker.execute(()->{try{
                CalendarBook book=new CalendarBook(MainActivity.this,owner);book.add(label,address,login,secret,month);
                runOnUiThread(()->{dialog.dismiss();if(sessionLogin!=null&&sessionLogin.identity().equals(owner.identity()))try{reply(request,new JSONObject().put("added",true));}catch(Exception ignored){}});
            }catch(Exception error){runOnUiThread(()->{dialog.dismiss();if(sessionLogin!=null&&sessionLogin.identity().equals(owner.identity()))failure(request,error instanceof CalDav.Failure?error.getMessage():"Не удалось подключить календарь. Проверьте CalDAV-ссылку, пароль внешнего приложения и доступ к серверу.");});}});
        });
    }
    private static final class JSONObjectSafe extends JSONObject { JSONObjectSafe putValue(String key,String value){try{put(key,value);}catch(Exception ignored){}return this;} }
    public final class Bridge {
        @JavascriptInterface public void calendarSetup(String month,String request){runOnUiThread(()->MainActivity.this.calendarSetup(month,request));}
        @JavascriptInterface public void calendars(String month,boolean refresh,String request){SavedAccounts.Login owner=sessionLogin;if(owner==null){failure(request,"Войдите в аккаунт.");return;}long generation=++calendarSerial;calendarWorker.execute(()->{try{if(generation!=calendarSerial){calendarReply(owner,request,new JSONObject().put("cancelled",true));return;}JSONObject result=new CalendarBook(MainActivity.this,owner).list(month,refresh,()->generation==calendarSerial&&!closing&&sessionLogin!=null&&sessionLogin.identity().equals(owner.identity()));calendarReply(owner,request,result);}catch(Exception error){runOnUiThread(()->{if(!closing&&sessionLogin!=null&&sessionLogin.identity().equals(owner.identity()))failure(request,"Не удалось загрузить календарь. Проверьте подключение.");});}});}
        @JavascriptInterface public void removeCalendar(String id,String request){SavedAccounts.Login owner=sessionLogin;if(owner==null||id==null||!id.matches("[a-f0-9-]{36}")){failure(request,"Некорректный календарь.");return;}calendarWorker.execute(()->{try{new CalendarBook(MainActivity.this,owner).remove(id);calendarReply(owner,request,new JSONObject().put("removed",true));}catch(Exception error){failure(request,"Не удалось удалить подключение календаря.");}});}

        @JavascriptInterface public void checkUpdate(){runOnUiThread(()->AppUpdater.check(MainActivity.this));}
        @JavascriptInterface public String account(){return account;}
        @JavascriptInterface public String loadState(){return local==null?"{}":local.getString("state","{}");}
        @JavascriptInterface public boolean saveState(String json){SharedPreferences destination=local;if(destination==null||json==null||json.length()>2000000)return false;try{JSONObject next=new JSONObject(json),previous=new JSONObject(destination.getString("state","{}"));boolean reminders=!java.util.Objects.equals(String.valueOf(previous.opt("tasks")),String.valueOf(next.opt("tasks")))||!java.util.Objects.equals(String.valueOf(previous.opt("settings")),String.valueOf(next.opt("settings")));if(!destination.edit().putString("state",json).commit())return false;if(reminders)worker.execute(()->{try{NoticeCenter.schedule(MainActivity.this);}catch(Exception ignored){}});return true;}catch(Exception ignored){return false;}}
        @JavascriptInterface public boolean notificationsAllowed(){return NoticeCenter.allowed(MainActivity.this);}
        @JavascriptInterface public boolean openTasksRequested(){return getIntent().getBooleanExtra("openTasks",false);}
        @JavascriptInterface public void smtpSettings(){runOnUiThread(()->MainActivity.this.smtpSettings());}
        @JavascriptInterface public void pickAttachments(String request){runOnUiThread(()->{
            if(attachmentPickRequest!=null||sessionLogin==null){failure(request,"Выбор файлов уже открыт.");return;}
            attachmentPickRequest=request;attachmentPickOwner=sessionLogin.identity();
            android.content.Intent pick=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(android.content.Intent.CATEGORY_OPENABLE).putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE,true);
            try{startActivityForResult(pick,72);}catch(Exception error){attachmentPickRequest=null;attachmentPickOwner=null;failure(request,"Не найден системный выбор файлов.");}
        });}
        @JavascriptInterface public void seen(String name,String uid,String validity,boolean seen,String request){
            if(name==null||name.length()>2048||uid==null||validity==null||!uid.matches("[0-9]{1,20}")||!validity.matches("[0-9]{1,20}")){failure(request,"Некорректное письмо.");return;}
            worker.execute(()->{try{reply(request,readWithReconnect(()->reader.seen(name,uid,validity,seen)));}catch(Exception error){readFailure(request,error,"Не удалось сохранить статус прочтения. Проверьте сеть и повторите действие.");}});
        }
        @JavascriptInterface public void dial(String number,String request){runOnUiThread(()->{
            try{String value=PhoneNumbers.normalize(number);startActivity(new android.content.Intent(android.content.Intent.ACTION_DIAL,Uri.fromParts("tel",value,null)));reply(request,new JSONObject().put("dialed",true).put("number",value));}
            catch(IllegalArgumentException error){failure(request,"Укажите полный телефонный номер.");}catch(Exception error){failure(request,"На устройстве не найдено приложение для звонков.");}
        });}
        @JavascriptInterface public void send(String json,String request){
            if(json==null||json.length()>120000){failure(request,"Письмо слишком большое.");return;}
            if(!sending.compareAndSet(false,true)){failure(request,"Предыдущее письмо ещё отправляется.");return;}
            worker.execute(()->{
                try{
                    SavedAccounts.Login login=sessionLogin;if(login==null)throw new IllegalStateException();
                    JSONObject draft=new JSONObject(json);
                    javax.mail.internet.MimeMessage message=MailSender.send(login,draft,uploads(login));
                    String warning="";
                    try{reader.saveSent(message);}catch(Exception copy){warning="Письмо отправлено, но копию в папке «Отправленные» сохранить не удалось. Не отправляйте его повторно.";}
                    reply(request,new JSONObject().put("sent",true).put("warning",warning));
                    org.json.JSONArray attachments=draft.optJSONArray("attachments");if(attachments!=null)for(int i=0;i<attachments.length();i++){try{MailSender.file(uploads(login),attachments.getJSONObject(i).getString("token")).delete();}catch(Exception ignored){}}
                }catch(MailSender.Uncertain unknown){try{reply(request,new JSONObject().put("uncertain",true).put("error","Связь оборвалась во время отправки: результат неизвестен. Проверьте ящик адресата перед повторной отправкой. Черновик сохранён."));}catch(Exception ignored){}}
                catch(javax.mail.AuthenticationFailedException auth){failure(request,"SMTP отклонил вход. Проверьте настройки отправки и пароль аккаунта. Черновик сохранён.");}
                catch(IllegalArgumentException|javax.mail.internet.AddressException invalid){failure(request,"Проверьте полные адреса получателей и поля письма. Черновик сохранён.");}
                catch(Exception error){failure(request,"Не удалось отправить письмо. Проверьте сеть и настройки SMTP. Черновик сохранён.");}
                finally{sending.set(false);}
            });
        }
        @JavascriptInterface public void notificationSettings(){runOnUiThread(()->{android.content.Intent settings=new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(settings);});}
        @JavascriptInterface public void testNotification(){runOnUiThread(()->{if(!NoticeCenter.allowed(MainActivity.this)){pendingTestNotice=true;getSharedPreferences("notification-permission",MODE_PRIVATE).edit().putBoolean("requested",false).apply();requestNoticePermission();}else NoticeCenter.test(MainActivity.this);});}
        @JavascriptInterface public void folders(String request){worker.execute(()->{try{reply(request,readWithReconnect(()->new JSONObject().put("folders",reader.folders())));}catch(Exception e){readFailure(request,e,"Не удалось получить папки. Проверьте сеть/VPN и повторите загрузку.");}});}
        @JavascriptInterface public void page(String name,int offset,String snapshot,String request){if(name==null||name.length()>2048||name.contains("\r")||name.contains("\n")||offset<0){failure(request,"Некорректные параметры списка.");return;}worker.execute(()->{try{reply(request,readWithReconnect(()->reader.page(name,offset,snapshot)));}catch(Exception e){readFailure(request,e,"Не удалось загрузить письма. Повторите попытку; если список изменился, нажмите обновление.");}});}
        @JavascriptInterface public void body(String name,String uid,String validity,String request){if(name==null||name.length()>2048||name.contains("\r")||name.contains("\n")||uid==null||validity==null||!uid.matches("[0-9]{1,20}")||!validity.matches("[0-9]{1,20}")){failure(request,"Некорректный идентификатор письма.");return;}worker.execute(()->{try{reply(request,readWithReconnect(()->reader.body(name,uid,validity)));}catch(Exception e){readFailure(request,e,"Не удалось прочитать письмо. Проверьте сеть и повторите загрузку.");}});}
        @JavascriptInterface public void attachment(String name,String uid,String validity,String path,String request){
            if(name==null||name.length()>2048||name.contains("\r")||name.contains("\n")||uid==null||validity==null||!uid.matches("[0-9]{1,20}")||!validity.matches("[0-9]{1,20}")||path==null||path.length()>160){failure(request,"Некорректные параметры вложения.");return;}
            worker.execute(()->{try{JSONObject downloaded=readWithReconnect(()->reader.attachment(name,uid,validity,path,new java.io.File(getCacheDir(),"attachments")));runOnUiThread(()->openAttachmentFile(request,downloaded));}catch(Exception error){readFailure(request,error,"Не удалось скачать вложение. Проверьте сеть и повторите попытку. Максимальный размер файла — 100 МБ.");}});
        }
        @JavascriptInterface public void logout(){leaveAccount();}
        @JavascriptInterface public void removeUpload(String token){worker.execute(()->{try{if(sessionLogin!=null)MailSender.file(uploads(sessionLogin),token).delete();}catch(Exception ignored){}});}
        @JavascriptInterface public void switchAccount(){leaveAccount();}
    }
    @Override public void onBackPressed(){if(web!=null){web.evaluateJavascript("window.appBack&&window.appBack()",null);}else if(loginScreen&&hasSavedAccounts){showAccounts();}else super.onBackPressed();}
    @Override protected void onDestroy(){closing=true;calendarWorker.shutdownNow();if(web!=null){web.removeJavascriptInterface("Pilot");web.destroy();web=null;}worker.execute(()->{if(reader!=null)reader.close();sessionLogin=null;});worker.shutdown();super.onDestroy();}
}

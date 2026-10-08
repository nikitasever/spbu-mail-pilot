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
    private String exportRequest,exportOwner;private java.io.File exportFile;
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
        v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);box.addView(v,new LinearLayout.LayoutParams(-1,dp(52)));
        if((type&InputType.TYPE_MASK_VARIATION)==InputType.TYPE_TEXT_VARIATION_PASSWORD){
            CheckBox show=new CheckBox(this);show.setText("Показать пароль");show.setOnCheckedChangeListener((button,checked)->{int position=v.getSelectionStart();v.setTransformationMethod(checked?null:android.text.method.PasswordTransformationMethod.getInstance());v.setSelection(Math.max(0,position));});box.addView(show);
        }
        return v;
    }
    private void showLogin() {
        if (isFinishing() || closing) return;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        loginScreen=true;
        loginBusy = false;
        ScrollView scroll = new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xfffaf8fe);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(28),dp(24),dp(24));scroll.addView(box);setContentView(scroll);
        TextView heading=text("Рабочая почта",29,0xff29262e);heading.setTypeface(null,Typeface.BOLD);box.addView(heading);
        box.addView(text("Тестовая версия 0.17 · реальный ящик",14,0xff8051b1));
        box.addView(text("Данные аккаунтов сохраняются на телефоне в зашифрованном виде. Можно переключать ящики без повторного ввода пароля. «Выйти» закрывает ящик, сохраняя его данные; удалить их можно в списке аккаунтов.",15,0xff726d7c));
        box.addView(text("Почтовый сервис",14,0xff726d7c));
        Spinner provider=new Spinner(this);provider.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"СПбГУ","Mail.ru","Другая почта"}));box.addView(provider);
        EditText user=input(box,"Логин или полный адрес почты","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password=input(box,"Пароль","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText host=input(box,"Сервер IMAP","mail.spbu.ru",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        EditText port=input(box,"Порт TLS","993",InputType.TYPE_CLASS_NUMBER);
        TextView help=text("",14,0xff726d7c);box.addView(help);
        provider.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                if(position<2){host.setText(position==1?"imap.mail.ru":"mail.spbu.ru");port.setText("993");}
                host.setEnabled(position==2);port.setEnabled(position==2);
                help.setText(position==1?"Введите полный адрес ящика и пароль для внешнего приложения Mail.ru с доступом к Почте. Создайте его в Mail.ru: Настройки → Все настройки → Безопасность → Пароли для внешних приложений. Получение и отправка настроены автоматически.":"Используйте данные входа своего почтового сервера. Настройки отправки доступны в приложении.");
            }
        });
        TextView status=text("",14,0xff9b422b);box.addView(status);
        Button connect=new Button(this);connect.setText("Подключить ящик");connect.setTextColor(Color.WHITE);connect.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE));box.addView(connect,new LinearLayout.LayoutParams(-1,dp(56)));
        box.addView(text("Для входа нужен IMAP. SMTP для отправки можно настроить в приложении. Звонки открываются в «Телефоне». Мессенджер СПбГУ пока не подключён.",13,0xff726d7c));
        Button accounts=new Button(this);accounts.setText("Сохранённые аккаунты");accounts.setOnClickListener(v->{if(!loginBusy)showAccounts();});box.addView(accounts);
        connect.setOnClickListener(v->{
            if (loginBusy) return;
            String u=user.getText().toString().trim(),h=host.getText().toString().trim(),pw=password.getText().toString();
            if(provider.getSelectedItemPosition()==1&&!u.contains("@")){status.setText("Для Mail.ru нужен полный адрес ящика, включая @ и домен.");return;}
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
                }catch(Exception error){next.close();runOnUiThread(()->{loginBusy=false;connect.setEnabled(true);status.setText(LoginFailure.describe(error,h,u,pw));});}
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
            catch(Exception error){next.close();runOnUiThread(()->showReconnect(LoginFailure.describe(error,login.host,login.user,login.password)+"\n\nСохранённый вход остаётся на телефоне.",false));}
        });
    }
    private void showReconnect(String message,boolean busy) {
        if(closing||isFinishing())return;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        loginScreen=false;
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(40),dp(24),dp(24));box.setBackgroundColor(0xfffaf8fe);
        box.addView(text("Рабочая почта",29,0xff29262e));box.addView(text(message,16,0xff726d7c));
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
        ++calendarSerial;
        if(web!=null){web.removeJavascriptInterface("Pilot");web.destroy();web=null;}
        worker.execute(()->{
            if(reader!=null)reader.close();reader=null;sessionLogin=null;local=null;account="";
            MailReader next=new MailReader();
            try{next.connect(login.host,login.port,login.user,login.password);if(closing){next.close();return;}vault.save(login);attachLogin(next,login);runOnUiThread(this::showMail);}
            catch(Exception error){next.close();runOnUiThread(()->{showAccounts();new android.app.AlertDialog.Builder(this).setTitle("Не удалось открыть ящик").setMessage(LoginFailure.describe(error,login.host,login.user,login.password)).setPositiveButton("Понятно",null).show();});}
        });
    }
    private void quickAccounts(){
        if(closing||sessionLogin==null)return;
        final SavedAccounts.Login owner=sessionLogin;
        worker.execute(()->{try{
            java.util.ArrayList<SavedAccounts.Login> saved=vault.list();
            runOnUiThread(()->{if(closing||sessionLogin==null||!sessionLogin.identity().equals(owner.identity()))return;
                String[] labels=new String[saved.size()+2];for(int i=0;i<saved.size();i++)labels[i]=(saved.get(i).identity().equals(owner.identity())?"✓ ":"")+saved.get(i).user+"\n"+saved.get(i).host;
                labels[saved.size()]="Добавить аккаунт · Mail.ru / СПбГУ";labels[saved.size()+1]="Управление аккаунтами";
                new android.app.AlertDialog.Builder(this).setTitle("Сменить ящик").setItems(labels,(dialog,index)->{
                    if(index<saved.size()){SavedAccounts.Login target=saved.get(index);if(!target.identity().equals(owner.identity()))connectSaved(target);}
                    else if(index==saved.size())leaveForNewAccount();else leaveAccount();
                }).setNegativeButton("Отмена",null).show();
            });
        }catch(Exception error){runOnUiThread(()->Toast.makeText(this,"Не удалось открыть аккаунты.",Toast.LENGTH_LONG).show());}});
    }
    private void leaveForNewAccount(){
        ++calendarSerial;
        worker.execute(()->{try{vault.deactivate();if(reader!=null)reader.close();reader=null;sessionLogin=null;local=null;account="";NoticeCenter.schedule(this);
            runOnUiThread(()->{if(web!=null){web.removeJavascriptInterface("Pilot");web.destroy();web=null;}showLogin();});
        }catch(Exception error){runOnUiThread(()->Toast.makeText(this,"Не удалось открыть добавление аккаунта.",Toast.LENGTH_LONG).show());}});
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
        web.setHorizontalScrollBarEnabled(false);web.setOverScrollMode(View.OVER_SCROLL_NEVER);
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
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==73&&exportRequest!=null){String request=exportRequest,owner=exportOwner;java.io.File file=exportFile;exportRequest=null;exportOwner=null;exportFile=null;
            SavedAccounts.Login login=sessionLogin;if(login==null||!login.identity().equals(owner))return;
            if(resultCode!=RESULT_OK||data==null||data.getData()==null){try{reply(request,new JSONObject().put("cancelled",true));}catch(Exception ignored){}return;}
            Uri target=data.getData();worker.execute(()->{try{if(sessionLogin!=login)return;try(java.io.InputStream input=new java.io.FileInputStream(file);java.io.OutputStream output=getContentResolver().openOutputStream(target,"wt")){if(output==null)throw new java.io.IOException();byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);}if(sessionLogin==login)reply(request,new JSONObject().put("saved",true));}catch(Exception e){if(sessionLogin==login)failure(request,"Не удалось сохранить файл в выбранное место.");}});return;
        }
        if(requestCode!=72||attachmentPickRequest==null)return;
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
    private void chooseExport(String request,JSONObject downloaded,SavedAccounts.Login owner){
        if(closing||sessionLogin!=owner)return;
        if(exportRequest!=null){failure(request,"Сначала завершите сохранение предыдущего файла.");return;}
        try{exportRequest=request;exportOwner=owner.identity();exportFile=new java.io.File(downloaded.getString("file"));
            android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).setType(downloaded.getString("mime")).addCategory(android.content.Intent.CATEGORY_OPENABLE).putExtra(android.content.Intent.EXTRA_TITLE,downloaded.getString("name"));startActivityForResult(intent,73);
        }catch(Exception e){exportRequest=null;exportFile=null;failure(request,"Не удалось открыть системное сохранение файла.");}
    }
    private void calendarReply(SavedAccounts.Login owner,String request,JSONObject result){runOnUiThread(()->{if(!closing&&sessionLogin!=null&&sessionLogin.identity().equals(owner.identity()))reply(request,result);});}
    private void calendarSetup(String month,String request) {
        SavedAccounts.Login owner=sessionLogin;if(owner==null){failure(request,"Войдите в почтовый аккаунт.");return;}
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(12),dp(20),dp(12));scroll.addView(box);
        box.addView(text("Mail.ru: введите полный адрес ящика и пароль внешнего приложения с доступом к Календарю. Приложение найдёт доступные календари. Общие календари появятся, только если сервер предоставляет к ним доступ через CalDAV.",14,0xff726d7c));
        Spinner mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Mail.ru — найти календари","Другой сервер — найти календари","Полная CalDAV-ссылка вручную"}));box.addView(mode);
        EditText title=input(box,"Название календаря (при ручном подключении)","Рабочий календарь",InputType.TYPE_CLASS_TEXT);
        EditText url=input(box,"Сервер CalDAV или полная ссылка","calendar.mail.ru",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                url.setText(position==0?"calendar.mail.ru":"");url.setEnabled(position!=0);
                title.setVisibility(position==2?View.VISIBLE:View.GONE);box.getChildAt(box.indexOfChild(title)-1).setVisibility(position==2?View.VISIBLE:View.GONE);
            }
        });
        EditText user=input(box,"Логин календаря (полный адрес)",owner.user,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password=input(box,"Пароль календаря / внешнего приложения","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(text("Подключение привязано к этому почтовому аккаунту. Пароль сохраняется зашифрованно. События сервера доступны для просмотра; локальные записи не отправляются в рабочий календарь.",13,0xff726d7c));
        TextView status=text("",14,0xff9b422b);box.addView(status);
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Подключить календарь").setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Продолжить",null).create();
        final boolean[] submitted={false};dialog.setOnDismissListener(d->{password.getText().clear();if(!loginScreen)getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);if(!submitted[0])try{reply(request,new JSONObject().put("cancelled",true));}catch(Exception ignored){}});dialog.show();
        dialog.getButton(-1).setOnClickListener(v->{
            final String label=title.getText().toString().trim(),address=url.getText().toString().trim(),login=user.getText().toString().trim(),secret=password.getText().toString();
            final boolean manual=mode.getSelectedItemPosition()==2;
            try{CalDav.address(!manual&&!address.contains("://")?"https://"+address:address);CalDav.query(month);if((manual&&(label.isEmpty()||label.length()>120))||login.isEmpty()||secret.isEmpty())throw new IllegalArgumentException();}catch(Exception error){status.setText("Укажите сервер (для ручного подключения — HTTPS-ссылку), логин и пароль календаря.");return;}
            if(sessionLogin==null||!sessionLogin.identity().equals(owner.identity())){dialog.dismiss();return;}
            submitted[0]=true;dialog.setCancelable(false);dialog.getButton(-1).setEnabled(false);dialog.getButton(-2).setEnabled(false);status.setText("Подключение и загрузка событий…");
            if(!manual){
                status.setText("Поиск доступных календарей…");
                calendarWorker.execute(()->{try{
                    java.util.List<CalendarDiscovery.Calendar> found=CalendarDiscovery.discover(address,login,secret);
                    runOnUiThread(()->{dialog.dismiss();if(!closing&&sessionLogin!=null&&sessionLogin.identity().equals(owner.identity()))chooseCalendar(owner,found,login,secret,month,request);});
                }catch(Exception error){runOnUiThread(()->{if(closing||sessionLogin==null||!sessionLogin.identity().equals(owner.identity())){dialog.dismiss();return;}submitted[0]=false;dialog.setCancelable(true);dialog.getButton(-1).setEnabled(true);dialog.getButton(-2).setEnabled(true);status.setText(error instanceof CalDav.Failure?error.getMessage():"Не удалось найти календари. Проверьте сервер, сеть и пароль внешнего приложения.");});}});
                return;
            }
            calendarWorker.execute(()->{try{
                CalendarBook book=new CalendarBook(MainActivity.this,owner);book.add(label,address,login,secret,month);
                runOnUiThread(()->{dialog.dismiss();if(sessionLogin!=null&&sessionLogin.identity().equals(owner.identity()))try{reply(request,new JSONObject().put("added",true));}catch(Exception ignored){}});
            }catch(Exception error){runOnUiThread(()->{dialog.dismiss();if(sessionLogin!=null&&sessionLogin.identity().equals(owner.identity()))failure(request,error instanceof CalDav.Failure?error.getMessage():"Не удалось подключить календарь. Проверьте CalDAV-ссылку, пароль внешнего приложения и доступ к серверу.");});}});
        });
    }
    private void chooseCalendar(SavedAccounts.Login owner,java.util.List<CalendarDiscovery.Calendar> found,String login,String secret,String month,String request){
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        String[] names=new String[found.size()];for(int i=0;i<names.length;i++)names[i]=found.get(i).title;
        final int[] selected={0};final boolean[] submitted={false};
        android.app.AlertDialog picker=new android.app.AlertDialog.Builder(this).setTitle("Выберите календарь").setSingleChoiceItems(names,0,(dialog,index)->selected[0]=index).setNegativeButton("Отмена",null).setPositiveButton("Подключить",null).create();
        picker.setOnDismissListener(d->{if(!loginScreen)getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);if(!submitted[0])try{reply(request,new JSONObject().put("cancelled",true));}catch(Exception ignored){}});picker.show();
        picker.getButton(-1).setOnClickListener(v->{
            if(closing||sessionLogin==null||!sessionLogin.identity().equals(owner.identity())){picker.dismiss();return;}
            CalendarDiscovery.Calendar calendar=found.get(selected[0]);submitted[0]=true;picker.setCancelable(false);picker.getButton(-1).setEnabled(false);picker.getButton(-2).setEnabled(false);picker.setTitle("Загрузка событий…");
            calendarWorker.execute(()->{try{
                new CalendarBook(MainActivity.this,owner).add(calendar.title,calendar.url,login,secret,month);
                calendarReply(owner,request,new JSONObject().put("added",true));
            }catch(Exception error){calendarReply(owner,request,new JSONObjectSafe().putValue("error",error instanceof CalDav.Failure?error.getMessage():"Не удалось подключить выбранный календарь. Проверьте сеть и доступ к событиям."));}
            finally{runOnUiThread(picker::dismiss);}});
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
        @JavascriptInterface public void searchPage(String name,String query,int offset,String snapshot,String request){
            if(name==null||name.length()>2048||query==null||query.length()>200||query.matches("(?s).*\\p{Cntrl}.*")||offset<0){failure(request,"Проверьте поисковый запрос.");return;}
            SavedAccounts.Login owner=sessionLogin;worker.execute(()->{try{if(owner==null||sessionLogin!=owner)return;calendarReply(owner,request,readWithReconnect(()->reader.page(name,offset,snapshot,query)));}catch(Exception e){if(sessionLogin==owner)readFailure(request,e,"Поиск не выполнен. Проверьте сеть или попробуйте другой запрос.");}});
        }
        @JavascriptInterface public void filesPage(String name,String query,int offset,String snapshot,String request){
            if(name==null||name.length()>2048||query==null||query.length()>200||offset<0){failure(request,"Проверьте запрос поиска файлов.");return;}
            SavedAccounts.Login owner=sessionLogin;worker.execute(()->{try{if(owner==null||sessionLogin!=owner)return;calendarReply(owner,request,readWithReconnect(()->reader.files(name,offset,snapshot,query)));}catch(Exception e){if(sessionLogin==owner)readFailure(request,e,"Не удалось получить вложения. Повторите поиск.");}});
        }
        @JavascriptInterface public void saveDocument(String name,String uid,String validity,String path,boolean message,String request){
            if(name==null||name.length()>2048||uid==null||validity==null||!uid.matches("[0-9]{1,20}")||!validity.matches("[0-9]{1,20}")||path==null||path.length()>160){failure(request,"Некорректные параметры файла.");return;}
            SavedAccounts.Login owner=sessionLogin;worker.execute(()->{try{if(owner==null||sessionLogin!=owner)return;JSONObject downloaded=readWithReconnect(()->message?reader.exportMessage(name,uid,validity,new java.io.File(getCacheDir(),"attachments")):reader.attachment(name,uid,validity,path,new java.io.File(getCacheDir(),"attachments")));runOnUiThread(()->chooseExport(request,downloaded,owner));}catch(Exception e){if(sessionLogin==owner)readFailure(request,e,"Не удалось скачать файл для сохранения. Проверьте сеть и размер файла (до 100 МБ).");}});
        }
        @JavascriptInterface public void attachment(String name,String uid,String validity,String path,String request){
            if(name==null||name.length()>2048||name.contains("\r")||name.contains("\n")||uid==null||validity==null||!uid.matches("[0-9]{1,20}")||!validity.matches("[0-9]{1,20}")||path==null||path.length()>160){failure(request,"Некорректные параметры вложения.");return;}
            worker.execute(()->{try{JSONObject downloaded=readWithReconnect(()->reader.attachment(name,uid,validity,path,new java.io.File(getCacheDir(),"attachments")));runOnUiThread(()->openAttachmentFile(request,downloaded));}catch(Exception error){readFailure(request,error,"Не удалось скачать вложение. Проверьте сеть и повторите попытку. Максимальный размер файла — 100 МБ.");}});
        }
        @JavascriptInterface public void logout(){leaveAccount();}
        @JavascriptInterface public void removeUpload(String token){worker.execute(()->{try{if(sessionLogin!=null)MailSender.file(uploads(sessionLogin),token).delete();}catch(Exception ignored){}});}
        @JavascriptInterface public void switchAccount(){runOnUiThread(()->quickAccounts());}
    }
    @Override public void onBackPressed(){if(web!=null){web.evaluateJavascript("window.appBack&&window.appBack()",null);}else if(loginScreen&&hasSavedAccounts){showAccounts();}else super.onBackPressed();}
    @Override protected void onDestroy(){closing=true;calendarWorker.shutdownNow();if(web!=null){web.removeJavascriptInterface("Pilot");web.destroy();web=null;}worker.execute(()->{if(reader!=null)reader.close();sessionLogin=null;});worker.shutdown();super.onDestroy();}
}

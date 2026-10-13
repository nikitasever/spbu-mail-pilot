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
import org.json.JSONArray;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService prefetchWorker=Executors.newSingleThreadExecutor();
    private volatile long prefetchSerial;
    private JSONObject prefetchPage;private SavedAccounts.Login prefetchOwner;private String prefetchFolder;
    private final ExecutorService bodyWorker=Executors.newSingleThreadExecutor();
    private MailReader bodyReader;
    private SavedAccounts.Login bodyOwner;
    private volatile long bodySerial;
    private final ExecutorService calendarWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService draftWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService directoryWorker=Executors.newSingleThreadExecutor();
    private final UniversityDirectory universityDirectory=new UniversityDirectory();
    private volatile long directorySerial;
    private volatile long advancedSerial;
    private MailReader reader;
    private CredentialVault vault;
    private volatile SavedAccounts.Login sessionLogin;
    private WebView web;
    private SharedPreferences local;
    private String account = "";
    private volatile boolean offlineSession;
    private volatile SavedAccounts.Login offlineCandidate;
    private volatile OfflineMailStore offlineMail;
    private volatile String appearanceMode="system";
    private volatile boolean closing;
    private volatile boolean resumed;
    private MailWatch mailWatch;
    private SavedAccounts.Login watchOwner;
    private String watchedFolder="INBOX",watchFolder="INBOX";
    private final android.os.Handler watchHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable watchUpdate;
    private void stopWatch(){if(mailWatch!=null)mailWatch.close();mailWatch=null;watchOwner=null;if(watchUpdate!=null)watchHandler.removeCallbacks(watchUpdate);watchUpdate=null;}
    private void startWatch(){
        SavedAccounts.Login owner=sessionLogin;
        if(!resumed||closing||offlineSession||web==null||owner==null){stopWatch();return;}
        if(mailWatch!=null&&watchOwner==owner&&watchedFolder.equals(watchFolder))return;
        stopWatch();watchOwner=owner;watchedFolder=watchFolder;String folder=watchFolder;
        final MailWatch[] token=new MailWatch[1];
        token[0]=new MailWatch(()->new ImapWatchConnection(owner,folder),()->runOnUiThread(()->{
            if(mailWatch!=token[0]||sessionLogin!=owner||!resumed||web==null)return;
            if(watchUpdate!=null)watchHandler.removeCallbacks(watchUpdate);
            watchUpdate=()->{if(mailWatch==token[0]&&sessionLogin==owner&&resumed&&web!=null)web.evaluateJavascript("window.mailChanged&&window.mailChanged("+JSONObject.quote(folder)+")",null);};
            watchHandler.postDelayed(watchUpdate,750);
        }));
        mailWatch=token[0];mailWatch.start();
    }
    private boolean loginBusy;
    private boolean hasSavedAccounts;
    private boolean loginScreen;
    private boolean pendingTestNotice;
    private volatile MailNoticeTarget pendingMail;
    private boolean mailPageReady;
    private volatile long calendarSerial;
    private volatile long digestSerial;
    private final GeminiDigest gemini=new GeminiDigest();
    private final java.util.concurrent.atomic.AtomicBoolean sending=new java.util.concurrent.atomic.AtomicBoolean();
    private String attachmentPickRequest,attachmentPickOwner;
    private String batchRequest;private SavedAccounts.Login batchOwner;private java.util.List<BulkAttachments.Ref> batchRefs;
    private final java.util.concurrent.atomic.AtomicBoolean batchBusy=new java.util.concurrent.atomic.AtomicBoolean(),batchCancel=new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.Map<String,Long> attachmentUpdates=new java.util.HashMap<>();
    private String exportRequest,exportOwner;private java.io.File exportFile;
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        NativeUi.bars(this);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        vault = new CredentialVault(this);
        NoticeCenter.channels(this);
        pendingMail=MailNoticeTarget.parse(getIntent().getStringExtra("openMail"));
        restoreLogin();
    }
    private String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception unavailable){return "—";}}
    boolean mailSurface(){return web!=null&&mailPageReady&&web.getParent()!=null;}
    void appLockChanged(){AppLock.window(this);if(web!=null)web.evaluateJavascript("window.appLockChanged&&window.appLockChanged()",null);resumePendingNavigation();}
    boolean nativeDark(){String mode=local!=null?appearanceMode:getSharedPreferences("appearance",MODE_PRIVATE).getString("theme","system");return AppearancePolicy.dark(AppearancePolicy.mode(mode),(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES);}
    private void loadAppearance(){try{JSONObject settings=new JSONObject(local.getString("state","{}")).optJSONObject("settings");appearanceMode=AppearancePolicy.mode(settings==null?"system":settings.optString("theme","system"));}catch(Exception ignored){appearanceMode="system";}}
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(NativeUi.context(this,false)); v.setText(value); v.setTextSize(size); v.setTextColor(NativeUi.color(this,color)); v.setPadding(0, dp(9), 0, dp(9)); return v;
    }
    private EditText input(LinearLayout box, String label, String value, int type) {
        box.addView(text(label, 14, 0xff726d7c));
        EditText v = new EditText(NativeUi.context(this,false)); v.setSingleLine(true); v.setTextColor(NativeUi.ink(this));v.setText(value); v.setTextSize(16); v.setInputType(type); v.setPadding(dp(12),dp(12),dp(12),dp(12));
        GradientDrawable shape = new GradientDrawable();shape.setColor(NativeUi.surface(this));shape.setCornerRadius(dp(12));shape.setStroke(dp(1),nativeDark()?0xff37303f:0xffeeebf3);v.setBackground(shape);
        v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);v.setMinHeight(dp(52));box.addView(v,new LinearLayout.LayoutParams(-1,-2));
        if((type&InputType.TYPE_MASK_VARIATION)==InputType.TYPE_TEXT_VARIATION_PASSWORD){
            CheckBox show=new CheckBox(NativeUi.context(this,false));show.setText("Показать пароль");show.setOnCheckedChangeListener((button,checked)->{int position=v.getSelectionStart();v.setTransformationMethod(checked?null:android.text.method.PasswordTransformationMethod.getInstance());v.setSelection(Math.max(0,position));});box.addView(show);
        }
        return v;
    }
    private void showLogin() {
        if (isFinishing() || closing) return;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        NativeUi.bars(this);loginScreen=true;
        loginBusy = false;
        ScrollView scroll = new ScrollView(NativeUi.context(this,false));scroll.setFillViewport(true);scroll.setBackgroundColor(NativeUi.paper(this));
        LinearLayout box=new LinearLayout(NativeUi.context(this,false));box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(28),dp(24),dp(24));scroll.addView(box);setContentView(scroll);
        TextView heading=text("Рабочая почта",29,0xff29262e);heading.setTypeface(null,Typeface.BOLD);box.addView(heading);
        box.addView(text("Тестовая версия "+appVersion()+" · реальный ящик",14,0xff8051b1));
        box.addView(text("Вход сохранится на этом телефоне в зашифрованном виде. Переключайте ящики без повторного ввода пароля.",15,0xff726d7c));
        box.addView(text("Почтовый сервис",14,0xff726d7c));
        Spinner provider=new Spinner(NativeUi.context(this,false));provider.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"СПбГУ","Mail.ru","Другая почта"}));box.addView(provider);
        EditText user=input(box,"Логин или полный адрес почты","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password=input(box,"Пароль","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText host=input(box,"Сервер IMAP","mail.spbu.ru",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        EditText port=input(box,"Порт TLS","993",InputType.TYPE_CLASS_NUMBER);
        TextView help=text("",14,0xff726d7c);box.addView(help);
        provider.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                if(position<2){host.setText(position==1?"imap.mail.ru":"mail.spbu.ru");port.setText("993");}
                host.setEnabled(position==2);port.setEnabled(position==2);int advanced=position==2?View.VISIBLE:View.GONE;host.setVisibility(advanced);port.setVisibility(advanced);box.getChildAt(box.indexOfChild(host)-1).setVisibility(advanced);box.getChildAt(box.indexOfChild(port)-1).setVisibility(advanced);
                help.setText(position==1?"Введите полный адрес ящика и пароль для внешнего приложения Mail.ru с доступом к Почте. Создайте его в Mail.ru: Настройки → Все настройки → Безопасность → Пароли для внешних приложений. Получение и отправка настроены автоматически.":"Используйте данные входа своего почтового сервера. Настройки отправки доступны в приложении.");
            }
        });
        TextView status=text("",14,0xff9b422b);box.addView(status);
        Button connect=NativeUi.button(this,true);connect.setText("Подключить ящик");box.addView(connect,new LinearLayout.LayoutParams(-1,dp(56)));
        box.addView(text("После входа доступны письма, календарь и ваши задачи.",13,0xff726d7c));
        Button accounts=NativeUi.button(this,false);accounts.setText("Сохранённые аккаунты");accounts.setOnClickListener(v->{if(!loginBusy)showAccounts();});box.addView(accounts);
        if(AppLock.enabled(this)){Button lock=NativeUi.button(this,false);lock.setText("Блокировка приложения");lock.setOnClickListener(v->AppLock.settings(this));box.addView(lock);}
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
        ++prefetchSerial;prefetchPage=null;prefetchOwner=null;
        ++bodySerial;bodyWorker.execute(this::closeBodyReader);
        String oldScope="account-"+digest(login.host+":"+login.port+":"+login.user.toLowerCase(java.util.Locale.ROOT));
        String scope="account-"+digest(login.host.toLowerCase(java.util.Locale.ROOT)+":"+login.port+":"+login.user.toLowerCase(java.util.Locale.ROOT));
        local=getSharedPreferences(scope,MODE_PRIVATE);
        if(!oldScope.equals(scope)&&!local.contains("state")){
            SharedPreferences previous=getSharedPreferences(oldScope,MODE_PRIVATE);
            if(previous.contains("state"))local.edit().putString("state",previous.getString("state","{}")).apply();
        }
        loadAppearance();reader=next;account=login.user;sessionLogin=login;offlineSession=false;offlineCandidate=login;try{offlineMail=new OfflineMailStore(this,login);}catch(Exception unavailable){offlineMail=null;}
        try{NoticeCenter.seed(this,login,next);}catch(Exception seed){}
        try{NoticeCenter.schedule(this);}catch(Exception notices){}
    }
    private void restoreLogin() {
        if(closing||isFinishing())return;
        showReconnect("Подключение к сохранённому ящику…",true);
        worker.execute(()->{
            SavedAccounts.Login login;
            try{login=vault.load();MailNoticeTarget target=pendingMail;if(target!=null){SavedAccounts.Login requested=null;for(SavedAccounts.Login saved:vault.list())if(NoticeCenter.scope(saved).equals(target.account)){requested=saved;break;}if(requested!=null){login=requested;vault.save(login);}else pendingMail=null;}}catch(Exception storage){runOnUiThread(()->showStorageError());return;}
            if(login==null){runOnUiThread(this::showAccounts);return;}
            final SavedAccounts.Login restored=login;
            if(!hasNetwork())try{if(NoticeCenter.settings(this,login)==null||NoticeCenter.settings(this,login).optBoolean("offlineMail",true)){offlineCandidate=login;runOnUiThread(()->openOffline(restored));return;}}catch(Exception ignored){}
            MailReader next=new MailReader();
            try{next.connect(login.host,login.port,login.user,login.password);if(closing){next.close();return;}attachLogin(next,login);runOnUiThread(this::showMail);}
            catch(Exception error){next.close();offlineCandidate=restored;runOnUiThread(()->showReconnect(LoginFailure.describe(error,restored.host,restored.user,restored.password)+"\n\nСохранённый вход остаётся на телефоне.",false));}
        });
    }
    private void showReconnect(String message,boolean busy) {
        if(closing||isFinishing())return;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        NativeUi.bars(this);loginScreen=false;
        LinearLayout box=new LinearLayout(NativeUi.context(this,false));box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(40),dp(24),dp(24));box.setBackgroundColor(NativeUi.paper(this));
        box.addView(text("Рабочая почта",29,0xff29262e));box.addView(text(message,16,0xff726d7c));
        if(busy){box.addView(new ProgressBar(NativeUi.context(this,false)));}else{
            Button retry=NativeUi.button(this,false);retry.setText("Повторить подключение");retry.setOnClickListener(v->restoreLogin());box.addView(retry);
            if(offlineCandidate!=null){Button cached=NativeUi.button(this,true);cached.setText("Открыть сохранённую почту");cached.setOnClickListener(v->openOffline(offlineCandidate));box.addView(cached);}
            Button exit=NativeUi.button(this,false);exit.setText("Аккаунты / другой ящик");exit.setOnClickListener(v->leaveAccount());box.addView(exit);
        }
        setContentView(box);
    }
    private void leaveAccount() {
        ++digestSerial;
        runOnUiThread(this::stopWatch);
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
        Button reset=NativeUi.button(this,false);reset.setText("Сбросить сохранённые входы");reset.setOnClickListener(v->NativeUi.dialog(this).setMessage("Удалить сохранённые данные входа всех аккаунтов? Запланированная отправка остановится. Задачи и заметки останутся.").setPositiveButton("Удалить",(dialog,which)->worker.execute(()->{try{vault.clear();NoticeCenter.schedule(this);runOnUiThread(this::showLogin);}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Не удалось удалить сохранённые входы.",Toast.LENGTH_LONG).show());}})).setNegativeButton("Отмена",null).show());box.addView(reset);
    }
    private void showAccounts(){
        if(closing||isFinishing())return;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        worker.execute(()->{
            try{
                java.util.ArrayList<SavedAccounts.Login> saved=vault.list();
                runOnUiThread(()->{
                    if(closing||isFinishing())return;
                    hasSavedAccounts=!saved.isEmpty();NativeUi.bars(this);loginScreen=false;
                    if(saved.isEmpty()){showLogin();return;}
                    ScrollView scroll=new ScrollView(NativeUi.context(this,false));scroll.setFillViewport(true);scroll.setBackgroundColor(NativeUi.paper(this));
                    LinearLayout box=new LinearLayout(NativeUi.context(this,false));box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(28),dp(24),dp(24));scroll.addView(box);
                    box.addView(text("Ваши аккаунты",29,0xff29262e));box.addView(text("Выберите ящик. Пароли сохранены в зашифрованном виде. Задачи и заметки у каждого аккаунта свои.",15,0xff726d7c));
                    for(SavedAccounts.Login login:saved){
                        LinearLayout choose=NativeUi.accountRow(this,login,false);choose.setOnClickListener(v->connectSaved(login));box.addView(choose,new LinearLayout.LayoutParams(-1,-2));
                        Button remove=NativeUi.button(this,false);remove.setText("Удалить сохранённый аккаунт");remove.setOnClickListener(v->NativeUi.dialog(this).setMessage("Удалить сохранённый вход для "+login.user+"? Подключения календарей будут удалены с телефона, запланированная отправка остановится. События сервера, задачи и заметки останутся.").setPositiveButton("Удалить",(dialog,which)->worker.execute(()->{try{new CalendarBook(this,login).clear();vault.remove(login);NoticeCenter.schedule(this);runOnUiThread(this::showAccounts);}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Не удалось удалить аккаунт.",Toast.LENGTH_LONG).show());}})).setNegativeButton("Отмена",null).show());box.addView(remove);
                    }
                    Button add=NativeUi.button(this,false);add.setText("Добавить другой аккаунт");add.setOnClickListener(v->showLogin());box.addView(add,new LinearLayout.LayoutParams(-1,dp(56)));setContentView(scroll);
                });
            }catch(Exception error){runOnUiThread(this::showStorageError);}
        });
    }
    private boolean hasNetwork(){try{android.net.ConnectivityManager manager=getSystemService(android.net.ConnectivityManager.class);android.net.Network network=manager.getActiveNetwork();android.net.NetworkCapabilities capabilities=network==null?null:manager.getNetworkCapabilities(network);return capabilities!=null&&capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);}catch(Exception unknown){return true;}}
    private boolean offlineEnabled(){try{JSONObject settings=new JSONObject(local==null?"{}":local.getString("state","{}")).optJSONObject("settings");return settings==null||settings.optBoolean("offlineMail",true);}catch(Exception error){return false;}}
    private void openOffline(SavedAccounts.Login login){
        if(login==null)return;showReconnect("Открываем сохранённую почту…",true);worker.execute(()->{try{boolean remembered=false;for(SavedAccounts.Login saved:vault.list())if(saved.identity().equals(login.identity()))remembered=true;if(!remembered)throw new IllegalStateException();stopWatch();if(reader!=null)reader.close();vault.save(login);attachLogin(new MailReader(),login);offlineSession=true;if(!offlineEnabled())throw new IllegalStateException();runOnUiThread(this::showMail);}catch(Exception error){runOnUiThread(()->showReconnect("Не удалось открыть сохранённую почту. Проверьте настройки офлайн-доступа или подключитесь к серверу.",false));}});
    }
    private JSONObject offlinePage(String folder,int offset,String snapshot,String query){try{return offlineEnabled()&&offlineMail!=null?offlineMail.page(folder,offset,snapshot,query):null;}catch(Exception invalid){return null;}}
    private JSONObject offlineBody(String folder,String uid,String validity){try{return offlineEnabled()&&offlineMail!=null?offlineMail.body(folder,uid,validity):null;}catch(Exception invalid){return null;}}
    private void connectSaved(SavedAccounts.Login login){
        ++digestSerial;
        stopWatch();watchFolder="INBOX";
        showReconnect("Подключение к "+login.user+"…",true);
        ++calendarSerial;
        if(web!=null){web.removeJavascriptInterface("Pilot");web.destroy();web=null;}
        worker.execute(()->{
            if(reader!=null)reader.close();reader=null;sessionLogin=null;local=null;account="";
            if(!hasNetwork())try{if(NoticeCenter.settings(this,login)==null||NoticeCenter.settings(this,login).optBoolean("offlineMail",true)){offlineCandidate=login;runOnUiThread(()->openOffline(login));return;}}catch(Exception ignored){}
            MailReader next=new MailReader();
            try{next.connect(login.host,login.port,login.user,login.password);if(closing){next.close();return;}vault.save(login);attachLogin(next,login);runOnUiThread(this::showMail);}
            catch(Exception error){next.close();offlineCandidate=login;runOnUiThread(()->showReconnect(LoginFailure.describe(error,login.host,login.user,login.password)+"\n\nМожно открыть сохранённую почту.",false));}
        });
    }
    private void quickAccounts(){
        if(closing||sessionLogin==null)return;
        final SavedAccounts.Login owner=sessionLogin;
        worker.execute(()->{try{
            java.util.ArrayList<SavedAccounts.Login> saved=vault.list();
            runOnUiThread(()->{if(closing||sessionLogin==null||!sessionLogin.identity().equals(owner.identity()))return;
                ScrollView scroll=new ScrollView(NativeUi.context(this,false));LinearLayout box=new LinearLayout(NativeUi.context(this,false));box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(8),dp(12),dp(8));scroll.addView(box);
                android.app.AlertDialog dialog=NativeUi.dialog(this).setTitle("Ваши аккаунты").setView(scroll).setNegativeButton("Закрыть",null).create();
                for(SavedAccounts.Login target:saved){boolean current=target.identity().equals(owner.identity());LinearLayout row=NativeUi.accountRow(this,target,current);row.setOnClickListener(v->{dialog.dismiss();if(!current)connectSaved(target);});box.addView(row,new LinearLayout.LayoutParams(-1,-2));}
                Button add=NativeUi.button(this,false);add.setText("Добавить аккаунт");add.setOnClickListener(v->{dialog.dismiss();leaveForNewAccount();});box.addView(add);
                Button manage=NativeUi.button(this,false);manage.setText("Управление аккаунтами");manage.setOnClickListener(v->{dialog.dismiss();leaveAccount();});box.addView(manage);
                dialog.setOnDismissListener(d->{if(web!=null&&!closing)web.evaluateJavascript("window.accountPickerClosed&&window.accountPickerClosed()",null);});dialog.show();
            });
        }catch(Exception error){runOnUiThread(()->Toast.makeText(this,"Не удалось открыть аккаунты.",Toast.LENGTH_LONG).show());}});
    }
    private void leaveForNewAccount(){
        ++digestSerial;
        stopWatch();watchFolder="INBOX";
        ++calendarSerial;
        worker.execute(()->{try{vault.deactivate();if(reader!=null)reader.close();reader=null;sessionLogin=null;local=null;account="";NoticeCenter.schedule(this);
            runOnUiThread(()->{if(web!=null){web.removeJavascriptInterface("Pilot");web.destroy();web=null;}showLogin();});
        }catch(Exception error){runOnUiThread(()->Toast.makeText(this,"Не удалось открыть добавление аккаунта.",Toast.LENGTH_LONG).show());}});
    }
    private void prefetchBodies(SavedAccounts.Login owner,String folder,JSONObject page){
        final long serial=++prefetchSerial;final OfflineMailStore cache=offlineMail;final java.util.List<JSONObject> refs;
        try{refs=OfflinePrefetch.refs(folder,page);prefetchPage=new JSONObject(page.toString());prefetchFolder=folder;prefetchOwner=owner;}catch(Exception invalid){return;}
        if(cache==null||!offlineEnabled()||offlineSession||!resumed||owner!=sessionLogin||refs.isEmpty())return;
        prefetchWorker.execute(()->{Thread.currentThread().setPriority(Thread.MIN_PRIORITY);int failed=0;boolean cancelled=false;
            try(MailReader connection=new MailReader()){
                boolean connected=false;for(JSONObject ref:refs){
                    if(closing||!resumed||serial!=prefetchSerial||sessionLogin!=owner||offlineSession||!offlineEnabled()){cancelled=true;break;}
                    try{String uid=ref.getString("uid"),validity=ref.getString("validity");if(cache.body(folder,uid,validity)!=null)continue;
                        if(!connected){connection.connect(owner.host,owner.port,owner.user,owner.password);connected=true;}
                        long seenRevision=cache.seenRevision(folder,uid,validity);JSONObject text=connection.cachedBody(folder,uid,validity);
                        if(closing||!resumed||serial!=prefetchSerial||sessionLogin!=owner||!offlineEnabled()){cancelled=true;break;}cache.savePrefetchedBody(folder,uid,validity,text,seenRevision,()->!closing&&resumed&&serial==prefetchSerial&&sessionLogin==owner&&offlineEnabled());
                    }catch(javax.mail.AuthenticationFailedException rejected){failed++;break;}catch(Exception unavailable){failed++;break;}
                }
            }catch(Exception unavailable){failed++;}
            if(cancelled||closing||serial!=prefetchSerial||sessionLogin!=owner)return;
            int count=0;try{for(JSONObject ref:refs)if(cache.body(folder,ref.getString("uid"),ref.getString("validity"))!=null)count++;}catch(Exception ignored){}
            final int saved=count,errors=failed;runOnUiThread(()->{if(!closing&&web!=null&&serial==prefetchSerial&&sessionLogin==owner)try{web.evaluateJavascript("window.offlinePrefetchDone&&window.offlinePrefetchDone("+new JSONObject().put("folder",folder).put("validity",refs.get(0).optString("validity")).put("saved",saved).put("total",refs.size()).put("failed",errors).toString()+")",null);}catch(Exception ignored){}});
        });
    }
    private void closeBodyReader(){if(bodyReader!=null)bodyReader.close();bodyReader=null;bodyOwner=null;}
    private void bodyReply(SavedAccounts.Login owner,String request,long generation,JSONObject value){runOnUiThread(()->{if(!closing&&sessionLogin==owner){try{reply(request,generation==bodySerial?value:new JSONObject().put("cancelled",true));}catch(org.json.JSONException invalid){failure(request,"Не удалось показать письмо.");}}});}
    private MailReader bodyConnection(SavedAccounts.Login owner)throws Exception{
        if(bodyReader==null||bodyOwner!=owner){closeBodyReader();MailReader next=new MailReader();try{next.connect(owner.host,owner.port,owner.user,owner.password);}catch(Exception failure){next.close();throw failure;}bodyReader=next;bodyOwner=owner;}
        return bodyReader;
    }
    private interface ReadOperation {JSONObject run() throws Exception;}
    private JSONObject readWithReconnect(ReadOperation operation) throws Exception {
        if(offlineSession)throw new java.io.IOException("Offline session");
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
        if(AppLock.enabled(this))getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);NativeUi.bars(this);
        loginScreen=false;mailPageReady=false;web=new WebView(this);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setDomStorageEnabled(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setBlockNetworkLoads(true);
        web.setHorizontalScrollBarEnabled(false);web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.addJavascriptInterface(new Bridge(),"Pilot");
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onJsConfirm(WebView view,String url,String message,JsResult result){
                if(closing||isFinishing()||AppLock.locked(MainActivity.this)||!resumed){result.cancel();return true;}
                NativeUi.dialog(MainActivity.this).setTitle("Подтверждение").setMessage(message).setPositiveButton(message.startsWith("Изменения не сохранены.")?"Выйти без сохранения":message.startsWith("Удалить ")?"Удалить":"Отключить",(dialog,which)->result.confirm()).setNegativeButton("Отмена",(dialog,which)->result.cancel()).setOnCancelListener(dialog->result.cancel()).show();return true;
            }
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){if(view!=web||!url.equals("https://mail-pilot.local/assets/index.html"))return;mailPageReady=true;resumePendingNavigation();}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();String path=uri.getPath();
                if("https".equals(uri.getScheme())&&"mail-pilot.local".equals(uri.getHost())&&path!=null&&path.matches("/assets/[a-zA-Z0-9._-]+")){
                    try{String name=path.substring(8);String type=name.endsWith(".js")?"application/javascript":name.endsWith(".css")?"text/css":name.endsWith(".png")?"image/png":"text/html";return new WebResourceResponse(type,name.endsWith(".png")?null:"UTF-8",getAssets().open(name));}catch(Exception ignored){}
                }
                return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
            }
        });
        setContentView(web);web.loadUrl("https://mail-pilot.local/assets/index.html");
        watchFolder="INBOX";startWatch();BackgroundMailService.reconcile(this,resumed);
        requestNoticePermission();
    }
    private void requestNoticePermission(){
        if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            SharedPreferences preference=getSharedPreferences("notification-permission",MODE_PRIVATE);
            if(!preference.getBoolean("requested",false)){preference.edit().putBoolean("requested",true).apply();requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},61);}
        }
    }
    private void smtpSettings(){
        if(sessionLogin==null||AppLock.locked(this)||!resumed)return;
        SavedAccounts.Login login=sessionLogin;
        LinearLayout box=new LinearLayout(NativeUi.context(this,false));box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),0,dp(20),0);
        EditText host=input(box,"Сервер SMTP",login.smtpHost,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        EditText port=input(box,"Порт",Integer.toString(login.smtpPort),InputType.TYPE_CLASS_NUMBER);
        EditText from=input(box,"Адрес отправителя",login.from,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        Spinner tls=new Spinner(NativeUi.context(this,false));tls.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"TLS с начала соединения (обычно 465)","Обязательный STARTTLS (обычно 587)"}));tls.setSelection(login.smtpSsl?0:1);box.addView(tls);
        box.addView(text("Для отправки используются логин и пароль этого аккаунта. Проверка TLS-сертификата включена.",13,0xff726d7c));
        ScrollView smtpScroll=new ScrollView(NativeUi.context(this,false));smtpScroll.addView(box);android.app.AlertDialog dialog=NativeUi.dialog(this).setTitle("Отправка писем").setView(smtpScroll).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create();dialog.show();
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{
            try{
                String h=host.getText().toString().trim(),address=from.getText().toString().trim();int p=Integer.parseInt(port.getText().toString());
                if(!h.matches("[A-Za-z0-9.-]+")||p<1||p>65535||OutgoingMail.addresses(address,true).length!=1)throw new IllegalArgumentException();
                SavedAccounts.Login updated=new SavedAccounts.Login(login.host,login.port,login.user,login.password,h,p,tls.getSelectedItemPosition()==0,address);
                worker.execute(()->{try{if(sessionLogin==null||!sessionLogin.identity().equals(login.identity()))return;vault.save(updated);sessionLogin=updated;runOnUiThread(dialog::dismiss);}catch(Exception error){runOnUiThread(()->Toast.makeText(this,"Не удалось сохранить настройки SMTP.",Toast.LENGTH_LONG).show());}});
            }catch(Exception error){Toast.makeText(this,"Проверьте сервер, порт и полный адрес отправителя.",Toast.LENGTH_LONG).show();}
        });
    }
    private void runAttachmentBatch(SavedAccounts.Login owner,String request,java.util.List<BulkAttachments.Ref> refs,Uri tree){
        java.util.function.BooleanSupplier stopped=()->batchCancel.get()||closing||sessionLogin!=owner;
        java.io.File directory=new java.io.File(getCacheDir(),"batch-"+java.util.UUID.randomUUID().toString());
        long[] last={0};int[] completed={-1};JSONObject response;
        try{
            BulkAttachments.Result result=BulkAttachments.run(refs,(ref,cancel,progress)->{BulkAttachments.check(cancel);JSONObject downloaded;try{downloaded=reader.attachmentMeasured(ref.folder,ref.uid,ref.validity,ref.path,directory,cancel,(bytes,size)->{ref.transferSize=size;progress.accept(bytes);});}catch(javax.mail.MessagingException|java.io.IOException disconnected){BulkAttachments.check(cancel);reader.close();reader.connect(owner.host,owner.port,owner.user,owner.password);BulkAttachments.check(cancel);downloaded=reader.attachmentMeasured(ref.folder,ref.uid,ref.validity,ref.path,directory,cancel,(bytes,size)->{ref.transferSize=size;progress.accept(bytes);});}return new BulkAttachments.Download(new java.io.File(downloaded.getString("file")),downloaded.getString("name"),downloaded.getString("mime"),true);},new SafAttachmentDestination(getContentResolver(),tree),stopped,(done,total,name,stage,bytes,size)->{
                long now=android.os.SystemClock.elapsedRealtime();if(done==completed[0]&&!stage.equals("finished")&&now-last[0]<150)return;last[0]=now;completed[0]=done;
                try{JSONObject update=new JSONObject().put("request",request).put("completed",done).put("total",total).put("name",name).put("stage",stage).put("bytes",bytes).put("size",size);runOnUiThread(()->{if(web!=null&&!closing&&sessionLogin==owner)web.evaluateJavascript("window.onBatchProgress&&window.onBatchProgress("+update.toString()+")",null);});}catch(Exception ignored){}
            });
            JSONArray failures=new JSONArray();for(BulkAttachments.Failure failed:result.failures)failures.put(new JSONObject().put("name",failed.name).put("reason",failed.reason).put("folder",failed.ref.folder).put("uid",failed.ref.uid).put("validity",failed.ref.validity).put("path",failed.ref.path));
            response=new JSONObject().put("total",result.total).put("saved",result.saved).put("cancelled",result.cancelled).put("failures",failures);
        }catch(Exception error){response=new JSONObjectSafe().putValue("error","Не удалось скачать вложения в выбранную папку. Проверьте доступ, сеть и свободное место; завершённые файлы сохранены.");}
        finally{java.io.File[] temporary=directory.listFiles();if(temporary!=null)for(java.io.File file:temporary)file.delete();directory.delete();batchBusy.set(false);}
        transferReply(owner,request,response);
    }
    private void attachmentProgress(SavedAccounts.Login owner,String request,String stage,long bytes,long total){
        long now=android.os.SystemClock.elapsedRealtime();String key=request+":"+stage;Long last=attachmentUpdates.get(key);if(last!=null&&now-last<150&&bytes!=0&&bytes!=total)return;if(attachmentUpdates.size()>60)attachmentUpdates.clear();attachmentUpdates.put(key,now);
        try{JSONObject update=new JSONObject().put("request",request).put("stage",stage).put("bytes",bytes).put("size",total);runOnUiThread(()->{if(!closing&&sessionLogin==owner&&web!=null)web.evaluateJavascript("window.onFileDownloadProgress&&window.onFileDownloadProgress("+update.toString()+")",null);});}catch(Exception ignored){}
    }
    private void transferReply(SavedAccounts.Login owner,String request,JSONObject result){try{result.put("request",request);}catch(Exception ignored){}runOnUiThread(()->{if(!closing&&sessionLogin==owner&&web!=null)web.evaluateJavascript("window.onNativeResult(JSON.parse("+JSONObject.quote(result.toString())+"))",null);});}
    private void transferFailure(SavedAccounts.Login owner,String request,String message){transferReply(owner,request,new JSONObjectSafe().putValue("error",message));}
    private TransferMessage.Progress transferProgress(String request,SavedAccounts.Login owner,String callback){return (stage,bytes,total)->{
        if(closing||sessionLogin!=owner)throw new java.io.IOException("Transfer cancelled");
        try{JSONObject data=new JSONObject().put("stage",stage).put("bytes",bytes).put("total",total);runOnUiThread(()->{if(!closing&&sessionLogin==owner&&web!=null)web.evaluateJavascript("window."+callback+"&&window."+callback+"("+JSONObject.quote(request)+","+data+")",null);});}catch(org.json.JSONException ignored){}
    };}
    private String draftKey(SavedAccounts.Login owner,String id)throws Exception{
        if(id==null||!id.matches("[A-Za-z0-9-]{1,100}"))throw new IllegalArgumentException();
        SharedPreferences keys=getSharedPreferences("draft-keys-"+NoticeCenter.scope(owner),MODE_PRIVATE);String key=keys.getString(id,null);if(key==null){key=java.util.UUID.randomUUID().toString();if(!keys.edit().putString(id,key).commit())throw new java.io.IOException("Cannot store draft key");}return key;
    }
    private java.io.File uploads(SavedAccounts.Login login)throws Exception{return new java.io.File(getFilesDir(),"outgoing/"+NoticeCenter.scope(login));}
    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(AppLock.result(this,requestCode,resultCode))return;
        if(requestCode==74&&batchRequest!=null){String request=batchRequest;SavedAccounts.Login owner=batchOwner;java.util.List<BulkAttachments.Ref> refs=batchRefs;batchRequest=null;batchOwner=null;batchRefs=null;if(owner==null||sessionLogin!=owner){batchCancel.set(true);batchBusy.set(false);return;}if(resultCode!=RESULT_OK||data==null||data.getData()==null||batchCancel.get()){batchBusy.set(false);transferReply(owner,request,new JSONObjectSafe().putValue("cancelled",true));return;}Uri tree=data.getData();worker.execute(()->runAttachmentBatch(owner,request,refs,tree));return;}

        if(requestCode==73&&exportRequest!=null){String request=exportRequest,owner=exportOwner;java.io.File file=exportFile;exportRequest=null;exportOwner=null;exportFile=null;
            SavedAccounts.Login login=sessionLogin;if(login==null||!login.identity().equals(owner))return;
            if(resultCode!=RESULT_OK||data==null||data.getData()==null){try{reply(request,new JSONObject().put("cancelled",true));}catch(Exception ignored){}return;}
            Uri target=data.getData();worker.execute(()->{try{if(sessionLogin!=login)return;try(java.io.InputStream input=new java.io.FileInputStream(file);java.io.OutputStream output=getContentResolver().openOutputStream(target,"wt")){if(output==null)throw new java.io.IOException();byte[] buffer=new byte[65536];int n;long written=0;attachmentProgress(login,request,"save",0,file.length());while((n=input.read(buffer))!=-1){if(closing||sessionLogin!=login)throw new java.io.IOException("Session changed");output.write(buffer,0,n);written+=n;attachmentProgress(login,request,"save",written,file.length());}}if(sessionLogin==login)reply(request,new JSONObject().put("saved",true));}catch(Exception e){if(sessionLogin==login)failure(request,"Не удалось сохранить файл в выбранное место.");}});return;
        }
        if(requestCode!=72||attachmentPickRequest==null)return;
        String request=attachmentPickRequest,owner=attachmentPickOwner;attachmentPickRequest=null;attachmentPickOwner=null;
        if(resultCode!=RESULT_OK||data==null){try{reply(request,new JSONObject().put("cancelled",true));}catch(Exception ignored){}return;}
        java.util.ArrayList<Uri> selected=new java.util.ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)selected.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)selected.add(data.getData());
        final SavedAccounts.Login importOwner=sessionLogin;if(importOwner==null||!importOwner.identity().equals(owner))return;
        worker.execute(()->{
            java.util.ArrayList<java.io.File> created=new java.util.ArrayList<>();
            try{
                SavedAccounts.Login login=importOwner;if(sessionLogin!=login||selected.size()>10)throw new IllegalArgumentException();
                org.json.JSONArray files=new org.json.JSONArray();java.io.File directory=uploads(login);
                for(Uri uri:selected){
                    String name="Вложение";long total=-1;try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME,android.provider.OpenableColumns.SIZE},null,null,null)){if(cursor!=null&&cursor.moveToFirst()){name=cursor.getString(0);if(!cursor.isNull(1))total=cursor.getLong(1);}}catch(Exception ignored){}
                    final String display=AttachmentFiles.safeName(name);final long expected=total;final int index=files.length()+1;final long[] tick={0};
                    java.io.File file;try(java.io.InputStream input=getContentResolver().openInputStream(uri)){if(input==null)throw new java.io.IOException();file=AttachmentFiles.copy(input,directory,name,AttachmentFiles.MAX_BYTES,bytes->{if(sessionLogin!=login||closing)throw new IllegalStateException("Account changed");long now=System.nanoTime();if(now-tick[0]<100000000&&bytes!=expected)return;tick[0]=now;try{JSONObject progress=new JSONObject().put("bytes",bytes).put("total",expected).put("name",display).put("index",index).put("count",selected.size());runOnUiThread(()->{if(sessionLogin==login&&!closing&&web!=null)web.evaluateJavascript("window.onFileDownloadProgress&&window.onFileDownloadProgress("+JSONObject.quote(request)+","+progress.toString()+")",null);});}catch(Exception ignored){}});}
                    created.add(file);files.put(new JSONObject().put("token",file.getName()).put("name",AttachmentFiles.safeName(name)).put("size",file.length()));
                }
                if(sessionLogin!=login||closing)throw new IllegalStateException();JSONObject imported=new JSONObject().put("files",files);runOnUiThread(()->{if(sessionLogin==login&&!closing)reply(request,imported);});
            }catch(Exception error){for(java.io.File file:created)file.delete();if(sessionLogin!=importOwner||closing)return;failure(request,"Не удалось прикрепить файлы. Выберите до 10 файлов размером до 100 МБ каждый.");}
        });
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==61){BackgroundMailService.reconcile(this,resumed);if(pendingTestNotice&&NoticeCenter.allowed(this))NoticeCenter.test(this);pendingTestNotice=false;worker.execute(()->{try{NoticeCenter.schedule(this);}catch(Exception ignored){}});}}
    @Override protected void onNewIntent(android.content.Intent intent){super.onNewIntent(intent);setIntent(intent);pendingMail=MailNoticeTarget.parse(intent.getStringExtra("openMail"));resumePendingNavigation();}
    private void resumePendingNavigation(){if(!resumed||closing||web==null||!mailPageReady||AppLock.locked(this))return;if(pendingMail!=null){openPendingMail();return;}if(getIntent().hasExtra("openScheduledAccount")){openPendingScheduled();return;}if(getIntent().hasExtra("openWidgetRoute")){openPendingWidget();return;}if(getIntent().getBooleanExtra("openTasks",false))web.evaluateJavascript("window.openTasksFromNotification&&window.openTasksFromNotification()",accepted->{if("true".equals(accepted))getIntent().removeExtra("openTasks");});}
    private void openPendingWidget(){
        String route=getIntent().getStringExtra("openWidgetRoute"),owner=getIntent().getStringExtra("openWidgetAccount");
        if(!("mail".equals(route)||"calendar".equals(route))){getIntent().removeExtra("openWidgetRoute");return;}
        if(sessionLogin==null)return;
        try{if(owner!=null&&!owner.isEmpty()&&!NoticeCenter.scope(sessionLogin).equals(owner)){getIntent().removeExtra("openWidgetRoute");getIntent().removeExtra("openWidgetAccount");MailWidget.request(this);Toast.makeText(this,"Текущий ящик изменился. Виджет обновлён.",Toast.LENGTH_SHORT).show();return;}}catch(Exception unavailable){return;}
        web.evaluateJavascript("window.openWidgetFromNotification&&window.openWidgetFromNotification("+JSONObject.quote(route)+")",accepted->{if("true".equals(accepted)&&route.equals(getIntent().getStringExtra("openWidgetRoute"))){getIntent().removeExtra("openWidgetRoute");getIntent().removeExtra("openWidgetAccount");}});
    }
    private void openPendingScheduled(){
        final String target=getIntent().getStringExtra("openScheduledAccount");if(target==null||!target.matches("account-[0-9a-f]{64}")){getIntent().removeExtra("openScheduledAccount");return;}if(sessionLogin==null)return;
        try{if(NoticeCenter.scope(sessionLogin).equals(target)){web.evaluateJavascript("window.openScheduledFromNotification&&window.openScheduledFromNotification()",accepted->{if("true".equals(accepted)&&target.equals(getIntent().getStringExtra("openScheduledAccount")))getIntent().removeExtra("openScheduledAccount");});return;}}catch(Exception ignored){return;}
        web.evaluateJavascript("window.prepareNotificationNavigation&&window.prepareNotificationNavigation()",accepted->{if(!"true".equals(accepted)||!target.equals(getIntent().getStringExtra("openScheduledAccount")))return;worker.execute(()->{try{SavedAccounts.Login found=null;for(SavedAccounts.Login saved:vault.list())if(NoticeCenter.scope(saved).equals(target)){found=saved;break;}final SavedAccounts.Login selected=found;runOnUiThread(()->{if(closing||!target.equals(getIntent().getStringExtra("openScheduledAccount")))return;if(selected!=null)connectSaved(selected);else{getIntent().removeExtra("openScheduledAccount");Toast.makeText(this,"Ящик запланированной отправки больше не сохранён.",Toast.LENGTH_LONG).show();}});}catch(Exception error){runOnUiThread(()->Toast.makeText(this,"Не удалось открыть очередь ящика.",Toast.LENGTH_LONG).show());}});});
    }
    private void openPendingMail(){
        final MailNoticeTarget target=pendingMail;if(target==null||web==null||!mailPageReady||sessionLogin==null||closing)return;
        try{if(NoticeCenter.scope(sessionLogin).equals(target.account)){
            web.evaluateJavascript("window.openMailFromNotification&&window.openMailFromNotification("+target.reference()+")",accepted->{if("true".equals(accepted)&&pendingMail==target){pendingMail=null;getIntent().removeExtra("openMail");}});return;
        }}catch(Exception ignored){return;}
        web.evaluateJavascript("window.prepareNotificationNavigation&&window.prepareNotificationNavigation()",accepted->{if(!"true".equals(accepted)||pendingMail!=target)return;worker.execute(()->{try{SavedAccounts.Login found=null;for(SavedAccounts.Login saved:vault.list())if(NoticeCenter.scope(saved).equals(target.account)){found=saved;break;}final SavedAccounts.Login selected=found;runOnUiThread(()->{if(pendingMail!=target||closing)return;if(selected!=null)connectSaved(selected);else{pendingMail=null;getIntent().removeExtra("openMail");Toast.makeText(this,"Ящик из уведомления больше не сохранён.",Toast.LENGTH_LONG).show();}});}catch(Exception error){runOnUiThread(()->Toast.makeText(this,"Не удалось открыть ящик из уведомления.",Toast.LENGTH_LONG).show());}});});
    }

    private void reply(String request,JSONObject result){try{result.put("request",request);}catch(Exception ignored){}runOnUiThread(()->{if(web!=null&&!closing)web.evaluateJavascript("window.onNativeResult(JSON.parse("+JSONObject.quote(result.toString())+"))",null);});}
    private void failure(String request,String message){reply(request,new JSONObjectSafe().putValue("error",message));}
    private void readFailure(String request,Exception error,String message){failure(request,error instanceof AuthenticationFailedException?"Сервер отклонил сохранённый вход. Откройте «Профиль» → «Сменить аккаунт» и добавьте этот ящик с новым паролем.":message);}
    private void openAttachmentFile(String request,JSONObject downloaded,SavedAccounts.Login owner){
        if(closing||web==null||sessionLogin!=owner)return;
        if(!resumed||AppLock.locked(this)){failure(request,"Файл загружен. Вернитесь к письму, разблокируйте приложение и нажмите «Открыть» снова.");return;}
        try{
            java.io.File file=new java.io.File(downloaded.getString("file"));
            java.io.File base=new java.io.File(getCacheDir(),"attachments").getCanonicalFile();if(!base.equals(file.getCanonicalFile().getParentFile()))throw new java.io.IOException("Invalid attachment location");
            Uri uri=new Uri.Builder().scheme("content").authority(AttachmentProvider.AUTHORITY).appendPath(file.getName()).build();
            if(PdfPreviewPolicy.pdf(downloaded.optString("name"),downloaded.optString("mime"))){
                PdfPreviewPolicy.file(base,file.getName());startActivity(new android.content.Intent(this,PdfPreviewActivity.class).setData(uri).putExtra("name",downloaded.getString("name")).putExtra("appearance",appearanceMode));reply(request,new JSONObject().put("opened",true));return;
            }
            if(DocxReader.docx(downloaded.optString("name"),downloaded.optString("mime"))){
                PdfPreviewPolicy.file(base,file.getName());startActivity(new android.content.Intent(this,DocxPreviewActivity.class).setData(uri).putExtra("name",downloaded.getString("name")).putExtra("appearance",appearanceMode));reply(request,new JSONObject().put("opened",true));return;
            }
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
        if(AppLock.locked(this)||!resumed){failure(request,"Файл загружен. Разблокируйте приложение и повторите сохранение.");return;}
        if(exportRequest!=null){failure(request,"Сначала завершите сохранение предыдущего файла.");return;}
        try{exportRequest=request;exportOwner=owner.identity();exportFile=new java.io.File(downloaded.getString("file"));
            android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).setType(downloaded.getString("mime")).addCategory(android.content.Intent.CATEGORY_OPENABLE).putExtra(android.content.Intent.EXTRA_TITLE,downloaded.getString("name"));startActivityForResult(intent,73);
        }catch(Exception e){exportRequest=null;exportFile=null;failure(request,"Не удалось открыть системное сохранение файла.");}
    }
    private void calendarReply(SavedAccounts.Login owner,String request,JSONObject result){runOnUiThread(()->{if(!closing&&sessionLogin!=null&&sessionLogin.identity().equals(owner.identity()))reply(request,result);});}
    private void calendarSetup(String month,String request) {
        if(AppLock.locked(this)||!resumed){failure(request,"Разблокируйте приложение и откройте настройку календаря снова.");return;}
        SavedAccounts.Login owner=sessionLogin;if(owner==null){failure(request,"Войдите в почтовый аккаунт.");return;}
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll=new ScrollView(NativeUi.context(this,false));LinearLayout box=new LinearLayout(NativeUi.context(this,false));box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(12),dp(20),dp(12));scroll.addView(box);
        box.addView(text("Mail.ru: введите полный адрес ящика и пароль внешнего приложения с доступом к Календарю. Приложение найдёт доступные календари. Общие календари появятся, только если сервер предоставляет к ним доступ через CalDAV.",14,0xff726d7c));
        Spinner mode=new Spinner(NativeUi.context(this,false));mode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Mail.ru — найти календари","Другой сервер — найти календари","Полная CalDAV-ссылка вручную"}));box.addView(mode);
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
        android.app.AlertDialog dialog=NativeUi.dialog(this).setTitle("Подключить календарь").setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Продолжить",null).create();
        final boolean[] submitted={false};dialog.setOnDismissListener(d->{password.getText().clear();if(!loginScreen)AppLock.window(this);if(!submitted[0])try{reply(request,new JSONObject().put("cancelled",true));}catch(Exception ignored){}});dialog.show();
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
        if(AppLock.locked(this)||!resumed){failure(request,"Разблокируйте приложение и подключите календарь снова.");return;}
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        String[] names=new String[found.size()];for(int i=0;i<names.length;i++)names[i]=found.get(i).title;
        final int[] selected={0};final boolean[] submitted={false};
        android.app.AlertDialog picker=NativeUi.dialog(this).setTitle("Выберите календарь").setSingleChoiceItems(names,0,(dialog,index)->selected[0]=index).setNegativeButton("Отмена",null).setPositiveButton("Подключить",null).create();
        picker.setOnDismissListener(d->{if(!loginScreen)AppLock.window(this);if(!submitted[0])try{reply(request,new JSONObject().put("cancelled",true));}catch(Exception ignored){}});picker.show();
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
    private static final class JSONObjectSafe extends JSONObject { JSONObjectSafe putValue(String key,Object value){try{put(key,value);}catch(Exception ignored){}return this;} }
    private boolean aiEnabled(){SharedPreferences state=local;return state!=null&&AiPolicy.enabled(state.getString("state","{}"));}
    public final class Bridge {
        @JavascriptInterface public String launcherIcon(){return getSharedPreferences("launcher",MODE_PRIVATE).getString("icon","spbu");}
        @JavascriptInterface public void setLauncherIcon(String mode,String request){runOnUiThread(()->{if(!"spbu".equals(mode)&&!"classic".equals(mode)){failure(request,"Неизвестная иконка.");return;}try{android.content.pm.PackageManager pm=getPackageManager();String selected="spbu".equals(mode)?"SpbuLauncher":"ClassicLauncher",other="spbu".equals(mode)?"ClassicLauncher":"SpbuLauncher";pm.setComponentEnabledSetting(new android.content.ComponentName(MainActivity.this,getPackageName()+"."+selected),1,android.content.pm.PackageManager.DONT_KILL_APP);pm.setComponentEnabledSetting(new android.content.ComponentName(MainActivity.this,getPackageName()+"."+other),2,android.content.pm.PackageManager.DONT_KILL_APP);getSharedPreferences("launcher",MODE_PRIVATE).edit().putString("icon",mode).apply();reply(request,new JSONObject().put("icon",mode));}catch(Exception error){failure(request,"Не удалось изменить иконку.");}});}

        @JavascriptInterface public void shareMailText(String text,String request){
            if(text==null||text.trim().isEmpty()||text.length()>140000){failure(request,"Текст пустой или слишком большой.");return;}
            SavedAccounts.Login owner=sessionLogin;if(owner==null)return;
            runOnUiThread(()->{if(closing||web==null||sessionLogin!=owner)return;try{ShareActions.text(MainActivity.this,text);reply(request,new JSONObject().put("chooserOpened",true));}catch(Exception error){failure(request,"Не удалось открыть меню передачи текста.");}});
        }
        @JavascriptInterface public void shareAttachment(String name,String uid,String validity,String path,String request){
            if(name==null||name.length()>2048||name.contains("\r")||name.contains("\n")||uid==null||validity==null||!uid.matches("[0-9]{1,20}")||!validity.matches("[0-9]{1,20}")||path==null||path.length()>160){failure(request,"Некорректные параметры файла.");return;}
            SavedAccounts.Login owner=sessionLogin;if(owner==null)return;
            worker.execute(()->{try{if(closing||sessionLogin!=owner)return;JSONObject downloaded=readWithReconnect(()->reader.attachment(name,uid,validity,path,new java.io.File(getCacheDir(),"attachments")));
                runOnUiThread(()->{if(closing||web==null||sessionLogin!=owner)return;try{ShareActions.file(MainActivity.this,new java.io.File(downloaded.getString("file")),downloaded.getString("mime"),downloaded.getString("name"));reply(request,new JSONObject().put("chooserOpened",true));}catch(Exception error){failure(request,"Не удалось открыть меню передачи файла.");}});
            }catch(Exception error){if(sessionLogin==owner)readFailure(request,error,"Не удалось скачать вложение для передачи.");}});
        }
        @JavascriptInterface public void copyContactEmail(String email,String request){
            if(email==null||email.length()>254||!email.matches("[^\\s<>@]+@[^\\s<>@]+")){failure(request,"Некорректный адрес.");return;}
            runOnUiThread(()->{try{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Адрес контакта",email));reply(request,new JSONObject().put("copied",true));}catch(Exception error){failure(request,"Не удалось скопировать адрес.");}});
        }
        @JavascriptInterface public void shareAi(String text,boolean copy,String request){
            if(!aiEnabled()){failure(request,"Нейроанализ отключён в настройках этого ящика.");return;}
            if(text==null||text.trim().isEmpty()||text.length()>120000){failure(request,"Текст для ИИ пустой или слишком большой.");return;}
            SavedAccounts.Login owner=sessionLogin;if(owner==null)return;
            runOnUiThread(()->{if(sessionLogin!=owner||closing)return;try{
                if(copy){android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Текст для ИИ",text));reply(request,new JSONObject().put("copied",true));}
                else{android.content.Intent share=new android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT,text);startActivity(android.content.Intent.createChooser(share,"Передать текст в приложение ИИ"));reply(request,new JSONObject().put("shared",true));}
            }catch(Exception error){failure(request,"Не удалось передать текст. Попробуйте скопировать его.");}});
        }
        @JavascriptInterface public void quickDownload(String name,String uid,String validity,String path,String request){
            if(android.os.Build.VERSION.SDK_INT<29){saveDocument(name,uid,validity,path,false,request);return;}
            if(name==null||name.length()>2048||name.contains("\r")||name.contains("\n")||uid==null||validity==null||!uid.matches("[0-9]{1,20}")||!validity.matches("[0-9]{1,20}")||path==null||path.length()>160){failure(request,"Некорректные параметры файла.");return;}
            SavedAccounts.Login owner=sessionLogin;if(owner==null)return;
            worker.execute(()->{Uri target=null;try{
                if(sessionLogin!=owner||closing)return;
                long[] last={0};
                JSONObject downloaded=readWithReconnect(()->reader.attachmentMeasured(name,uid,validity,path,new java.io.File(getCacheDir(),"attachments"),()->closing||sessionLogin!=owner,(bytes,total)->{long now=android.os.SystemClock.elapsedRealtime();if(now-last[0]<150&&bytes!=0)return;last[0]=now;attachmentProgress(owner,request,"download",bytes,total);}));
                attachmentProgress(owner,request,"save",0,new java.io.File(downloaded.getString("file")).length());
                if(sessionLogin!=owner||closing)return;
                android.content.ContentValues values=new android.content.ContentValues();values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,downloaded.getString("name"));values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,downloaded.getString("mime"));values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS+"/Рабочая почта");values.put(android.provider.MediaStore.MediaColumns.IS_PENDING,1);
                target=getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(target==null)throw new java.io.IOException("Cannot create download");
                try(java.io.InputStream input=new java.io.FileInputStream(downloaded.getString("file"));java.io.OutputStream output=getContentResolver().openOutputStream(target,"w")){if(output==null)throw new java.io.IOException("Cannot open download");byte[] buffer=new byte[65536];int n;long written=0,total=new java.io.File(downloaded.getString("file")).length();while((n=input.read(buffer))!=-1){if(sessionLogin!=owner||closing)throw new java.io.IOException("Session changed");output.write(buffer,0,n);written+=n;attachmentProgress(owner,request,"save",written,total);}}
                values.clear();values.put(android.provider.MediaStore.MediaColumns.IS_PENDING,0);if(getContentResolver().update(target,values,null,null)!=1)throw new java.io.IOException("Cannot finish download");target=null;
                calendarReply(owner,request,new JSONObject().put("saved",true).put("downloads",true));
            }catch(Exception error){if(target!=null)try{getContentResolver().delete(target,null,null);}catch(Exception ignored){}calendarReply(owner,request,new JSONObjectSafe().putValue("error","Не удалось сохранить файл в Загрузки. Проверьте сеть, свободное место и размер файла (до 100 МБ)."));}});
        }
        @JavascriptInterface public void calendarSetup(String month,String request){runOnUiThread(()->MainActivity.this.calendarSetup(month,request));}
        @JavascriptInterface public void calendars(String month,boolean refresh,String request){SavedAccounts.Login owner=sessionLogin;if(owner==null){failure(request,"Войдите в аккаунт.");return;}long generation=++calendarSerial;calendarWorker.execute(()->{try{if(generation!=calendarSerial){calendarReply(owner,request,new JSONObject().put("cancelled",true));return;}JSONObject result=new CalendarBook(MainActivity.this,owner).list(month,refresh,()->generation==calendarSerial&&!closing&&sessionLogin!=null&&sessionLogin.identity().equals(owner.identity()));calendarReply(owner,request,result);}catch(Exception error){runOnUiThread(()->{if(!closing&&sessionLogin!=null&&sessionLogin.identity().equals(owner.identity()))failure(request,"Не удалось загрузить календарь. Проверьте подключение.");});}});}
        @JavascriptInterface public void removeCalendar(String id,String request){SavedAccounts.Login owner=sessionLogin;if(owner==null||id==null||!id.matches("[a-f0-9-]{36}")){failure(request,"Некорректный календарь.");return;}calendarWorker.execute(()->{try{new CalendarBook(MainActivity.this,owner).remove(id);calendarReply(owner,request,new JSONObject().put("removed",true));}catch(Exception error){failure(request,"Не удалось удалить подключение календаря.");}});}

        @JavascriptInterface public void checkUpdate(){runOnUiThread(()->AppUpdater.check(MainActivity.this));}
        @JavascriptInterface public void appearanceChanged(String mode){SharedPreferences prefs=getSharedPreferences("appearance",MODE_PRIVATE);String next=AppearancePolicy.mode(mode);if(!next.equals(prefs.getString("theme","system")))prefs.edit().putString("theme",next).apply();runOnUiThread(()->NativeUi.bars(MainActivity.this));}
        @JavascriptInterface public boolean systemDark(){return (getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;}
        @JavascriptInterface public String account(){return account;}
        @JavascriptInterface public String loadState(){return local==null?"{}":local.getString("state","{}");}
        @JavascriptInterface public String replyReminderStatuses(){SavedAccounts.Login owner=sessionLogin;return owner==null?"[]":ReplyReminderCenter.status(MainActivity.this,owner);}
        @JavascriptInterface public boolean saveState(String json){synchronized(ReplyReminderCenter.class){SharedPreferences destination=local;if(destination==null||json==null||json.length()>2000000)return false;try{JSONObject next=new JSONObject(json),previous=new JSONObject(destination.getString("state","{}"));boolean reminders=!java.util.Objects.equals(String.valueOf(previous.opt("replyReminders")),String.valueOf(next.opt("replyReminders")))||!java.util.Objects.equals(String.valueOf(previous.opt("snoozedMail")),String.valueOf(next.opt("snoozedMail")))||!java.util.Objects.equals(String.valueOf(previous.opt("tasks")),String.valueOf(next.opt("tasks")))||!java.util.Objects.equals(String.valueOf(previous.opt("settings")),String.valueOf(next.opt("settings")));if(!destination.edit().putString("state",json).commit())return false;JSONObject nextOptions=next.optJSONObject("settings"),oldOptions=previous.optJSONObject("settings");boolean caching=nextOptions==null||nextOptions.optBoolean("offlineMail",true),wasCaching=oldOptions==null||oldOptions.optBoolean("offlineMail",true);if(!caching)++prefetchSerial;else if(!wasCaching)worker.execute(()->{if(prefetchPage!=null&&prefetchOwner==sessionLogin)prefetchBodies(prefetchOwner,prefetchFolder,prefetchPage);});MailWidget.request(MainActivity.this);if(!AiPolicy.enabled(json)){++digestSerial;gemini.cancel();}JSONObject style=next.optJSONObject("settings");appearanceMode=AppearancePolicy.mode(style==null?"system":style.optString("theme","system"));if(reminders)worker.execute(()->{try{NoticeCenter.schedule(MainActivity.this);}catch(Exception ignored){}runOnUiThread(()->BackgroundMailService.reconcile(MainActivity.this,resumed));});return true;}catch(Exception ignored){return false;}}}
        @JavascriptInterface public String backgroundMailStatus(){return BackgroundMailService.status(MainActivity.this);}
        @JavascriptInterface public void allowBackgroundMail(){runOnUiThread(()->{try{startActivity(new android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())));}catch(Exception error){new Bridge().batterySettings();}});}
        @JavascriptInterface public boolean notificationsAllowed(){return NoticeCenter.allowed(MainActivity.this);}
        @JavascriptInterface public boolean directoryAvailable(){return UniversityDirectory.supported(sessionLogin);}
        @JavascriptInterface public void contactsPage(String book,String query,String cookie,String request){
            SavedAccounts.Login owner=sessionLogin;long generation=++directorySerial;
            if(!UniversityDirectory.supported(owner)){failure(request,"Серверные адресные книги доступны для почты СПбГУ.");return;}
            directoryWorker.execute(()->{
                JSONObject result;
                try{if(closing||sessionLogin!=owner||generation!=directorySerial)result=new JSONObject().put("cancelled",true);else result=universityDirectory.page(owner,book,query,cookie);}
                catch(Exception unavailable){result=new JSONObjectSafe().putValue("error","Не удалось получить адресную книгу СПбГУ. Проверьте сеть и доступ к справочнику на сервере.");}
                final JSONObject response=result;runOnUiThread(()->{if(!closing&&sessionLogin==owner&&web!=null)reply(request,response);});
            });
        }
        @JavascriptInterface public void searchRecipients(String query,String request){
            try{UniversityDirectory.query(query);}catch(Exception invalid){failure(request,"Введите не менее двух символов имени или адреса.");return;}
            SavedAccounts.Login owner=sessionLogin;long generation=++directorySerial;
            if(!UniversityDirectory.supported(owner)){failure(request,"Справочник доступен для ящика СПбГУ.");return;}
            directoryWorker.execute(()->{
                JSONObject result;
                try{if(closing||sessionLogin!=owner||generation!=directorySerial)result=new JSONObject().put("cancelled",true);else result=new JSONObject().put("people",universityDirectory.search(owner,query));}
                catch(Exception unavailable){result=new JSONObjectSafe().putValue("error","Не удалось получить справочник СПбГУ. Проверьте сеть; доступ определяется сервером университета.");}
                final JSONObject response=result;
                runOnUiThread(()->{if(!closing&&sessionLogin==owner&&web!=null)reply(request,response);});
            });
        }
        @JavascriptInterface public void batterySettings(){runOnUiThread(()->{try{startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception error){Toast.makeText(MainActivity.this,"Откройте настройки приложения → Батарея.",Toast.LENGTH_LONG).show();}});}
        @JavascriptInterface public boolean appLockEnabled(){return AppLock.enabled(MainActivity.this);}
        @JavascriptInterface public void appLockSettings(){runOnUiThread(()->AppLock.settings(MainActivity.this));}
        @JavascriptInterface public boolean openTasksRequested(){return !AppLock.locked(MainActivity.this)&&getIntent().getBooleanExtra("openTasks",false);}
        @JavascriptInterface public void smtpSettings(){runOnUiThread(()->MainActivity.this.smtpSettings());}
        @JavascriptInterface public void syncDraft(String json,String request){
            if(offlineSession){failure(request,"Нет подключения. Черновик сохранён на телефоне.");return;}
            if(json==null||json.length()>120000){failure(request,"Черновик слишком большой.");return;}SavedAccounts.Login owner=sessionLogin;if(owner==null){failure(request,"Войдите в ящик.");return;}
            draftWorker.execute(()->{try{if(closing||sessionLogin!=owner)return;JSONObject draft=new JSONObject(json);String key=draftKey(owner,draft.getString("id"));TransferMessage message=MailSender.draft(owner,draft,uploads(owner));
                try(MailReader connection=new MailReader()){connection.connect(owner.host,owner.port,owner.user,owner.password);JSONObject saved=ServerDrafts.save(connection.draftStore(),message,key,ServerDrafts.revision(draft),transferProgress(request,owner,"onServerDraftProgress"));
                    if(!closing&&sessionLogin==owner){transferReply(owner,request,new JSONObject().put("serverDraft",saved).put("saved",true));}}
            }catch(Exception failed){if(!closing&&sessionLogin==owner)transferFailure(owner,request,"Черновик остался на телефоне. Сервер не подтвердил загрузку: проверьте сеть, размер вложений и папку «Черновики», затем повторите.");}});
        }
        @JavascriptInterface public void deleteServerDraft(String json,String request){
            SavedAccounts.Login owner=sessionLogin;
            if(owner==null){failure(request,"Войдите в ящик.");return;}
            if(json==null||json.length()>4096){transferFailure(owner,request,"Некорректная ссылка на серверный черновик.");return;}
            draftWorker.execute(()->{try{
                if(closing||sessionLogin!=owner)return;
                JSONObject ref=new JSONObject(json);String key=ref.optString("key");
                SharedPreferences keys=getSharedPreferences("draft-keys-"+NoticeCenter.scope(owner),MODE_PRIVATE);
                boolean owned=false;for(Object value:keys.getAll().values())if(key.equals(value)){owned=true;break;}
                if(!owned)throw new ServerDrafts.DeleteRefused("ownership");
                try(MailReader connection=new MailReader()){
                    connection.connect(owner.host,owner.port,owner.user,owner.password);
                    if(closing||sessionLogin!=owner)return;
                    transferReply(owner,request,ServerDrafts.removeKnown(connection.draftStore(),ref,key));
                }
            }catch(ServerDrafts.DeleteRefused refused){
                String message=refused.reason.equals("uidplus")?"Сервер не поддерживает безопасное удаление одного черновика (UIDPLUS). Черновик сохранён.":"Серверная папка или черновик изменились. Обновите черновик перед удалением; локальная копия сохранена.";
                transferFailure(owner,request,message);
            }catch(Exception unavailable){transferFailure(owner,request,"Сервер не подтвердил удаление черновика. Локальная копия сохранена; проверьте сеть и повторите.");}});
        }
        @JavascriptInterface public void pickAttachments(String request){runOnUiThread(()->{
            if(attachmentPickRequest!=null||sessionLogin==null){failure(request,"Выбор файлов уже открыт.");return;}
            attachmentPickRequest=request;attachmentPickOwner=sessionLogin.identity();
            android.content.Intent pick=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(android.content.Intent.CATEGORY_OPENABLE).putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE,true);
            try{startActivityForResult(pick,72);}catch(Exception error){attachmentPickRequest=null;attachmentPickOwner=null;failure(request,"Не найден системный выбор файлов.");}
        });}
        @JavascriptInterface public void seen(String name,String uid,String validity,boolean seen,String request){
            if(name==null||name.length()>2048||uid==null||validity==null||!uid.matches("[0-9]{1,20}")||!validity.matches("[0-9]{1,20}")){failure(request,"Некорректное письмо.");return;}
            SavedAccounts.Login owner=sessionLogin;OfflineMailStore cache=offlineMail;worker.execute(()->{if(owner==null||sessionLogin!=owner)return;try{JSONObject result=readWithReconnect(()->reader.seen(name,uid,validity,seen));if(MailWidget.installed(MainActivity.this)&&"INBOX".equalsIgnoreCase(name))MailWidget.record(MainActivity.this,owner,reader.unread(result));if(sessionLogin==owner&&offlineEnabled()&&cache!=null)try{cache.seen(name,uid,validity,result.getBoolean("seen"));}catch(Exception ignored){}transferReply(owner,request,result);}catch(Exception error){if(sessionLogin==owner)readFailure(request,error,"Не удалось сохранить статус прочтения. Проверьте сеть и повторите действие.");}});
        }
        @JavascriptInterface public void dial(String number,String request){runOnUiThread(()->{
            try{String value=PhoneNumbers.normalize(number);startActivity(new android.content.Intent(android.content.Intent.ACTION_DIAL,Uri.fromParts("tel",value,null)));reply(request,new JSONObject().put("dialed",true).put("number",value));}
            catch(IllegalArgumentException error){failure(request,"Укажите полный телефонный номер.");}catch(Exception error){failure(request,"На устройстве не найдено приложение для звонков.");}
        });}
        @JavascriptInterface public void scheduledMail(String request){SavedAccounts.Login owner=sessionLogin;worker.execute(()->{try{if(owner==null||owner!=sessionLogin)return;transferReply(owner,request,ScheduledMail.list(MainActivity.this,owner).put("schedulerReady",ScheduledMail.reconcile(MainActivity.this)));}catch(Exception error){transferFailure(owner,request,"Не удалось прочитать очередь отправки. Запланированные письма не удалены.");}});}
        @JavascriptInterface public void scheduleSend(String json,String when,String request){SavedAccounts.Login owner=sessionLogin;worker.execute(()->{try{if(owner==null||owner!=sessionLogin)return;if(json==null||json.length()>120000)throw new IllegalArgumentException();JSONObject draft=new JSONObject(json);transferReply(owner,request,ScheduledMail.create(MainActivity.this,owner,draft,Long.parseLong(when)));}catch(Exception error){transferFailure(owner,request,"Не удалось запланировать. Проверьте дату, адреса, вложения, свободное место и SMTP. Черновик сохранён.");}});}
        @JavascriptInterface public void scheduledSendDetails(String id,String request){SavedAccounts.Login owner=sessionLogin;worker.execute(()->{try{if(owner==null||owner!=sessionLogin)return;transferReply(owner,request,ScheduledMail.details(MainActivity.this,owner,id));}catch(Exception error){transferFailure(owner,request,"Текст очереди недоступен: письмо могло уже отправиться. Обновите очередь, чтобы проверить результат.");}});}
        @JavascriptInterface public void cancelScheduledSend(String id,String request){SavedAccounts.Login owner=sessionLogin;worker.execute(()->{try{if(owner==null||owner!=sessionLogin)return;transferReply(owner,request,ScheduledMail.cancel(MainActivity.this,owner,id));}catch(Exception error){transferFailure(owner,request,"Не удалось отменить: отправка могла уже начаться, либо не удалось сохранить отмену на телефоне. Обновите очередь.");}});}
        @JavascriptInterface public void returnedScheduledDraft(String id,String request){SavedAccounts.Login owner=sessionLogin;worker.execute(()->{try{if(owner==null||owner!=sessionLogin)return;transferReply(owner,request,ScheduledMail.returned(MainActivity.this,owner,id));}catch(Exception error){transferFailure(owner,request,"Не удалось восстановить черновик. Сохранённая запись остаётся в очереди.");}});}
        @JavascriptInterface public void acknowledgeScheduledDraft(String id,String request){SavedAccounts.Login owner=sessionLogin;worker.execute(()->{try{if(owner==null||owner!=sessionLogin)return;ScheduledMail.acknowledgeReturned(MainActivity.this,owner,id);transferReply(owner,request,new JSONObject().put("acknowledged",true));}catch(Exception error){transferFailure(owner,request,"Черновик сохранён, но запись восстановления пока остаётся в очереди.");}});}
        @JavascriptInterface public boolean appVisible(){return resumed&&!closing;}
        @JavascriptInterface public void send(String json,String request){
            if(offlineSession){failure(request,"Подключитесь к серверу перед отправкой. Черновик сохранён на телефоне.");return;}
            if(!resumed||closing){failure(request,"Окно приложения закрыто. Письмо сохранено в черновиках.");return;}
            if(json==null||json.length()>120000){failure(request,"Письмо слишком большое.");return;}
            SavedAccounts.Login login=sessionLogin;if(login==null){failure(request,"Войдите в ящик.");return;}
            if(!sending.compareAndSet(false,true)){failure(request,"Предыдущее письмо ещё отправляется.");return;}
            worker.execute(()->{
                try{
                    if(closing||sessionLogin!=login)return;
                    JSONObject draft=new JSONObject(json);
                    if(ScheduledMail.protects(MainActivity.this,login,draft.optString("id"))){transferFailure(login,request,"Письмо уже запланировано или отправлено. Откройте очередь «По расписанию», чтобы проверить результат или отменить отправку.");return;}
                    javax.mail.internet.MimeMessage message=MailSender.send(login,draft,uploads(login),transferProgress(request,login,"onSendProgress"));
                    String warning="";try{ScheduledMail.returnedSent(MainActivity.this,login,draft.optString("id"));}catch(Exception failedRecord){warning="Письмо отправлено. Не удалось обновить историю запланированных писем; не восстанавливайте его повторно.";}
                    try{reader.saveSent(message);}catch(Exception copy){warning="Письмо отправлено, но копию в папке «Отправленные» сохранить не удалось. Не отправляйте его повторно.";}
                    try{String key=getSharedPreferences("draft-keys-"+NoticeCenter.scope(login),MODE_PRIVATE).getString(draft.optString("id"),null);if(key!=null)ServerDrafts.remove(reader.draftStore(),key);}catch(Exception retained){warning+=(warning.isEmpty()?"":"\n")+"Письмо отправлено; серверный черновик удалить не удалось.";}
                    transferReply(login,request,new JSONObject().put("sent",true).put("warning",warning));
                    org.json.JSONArray attachments=draft.optJSONArray("attachments");if(attachments!=null)for(int i=0;i<attachments.length();i++){try{MailSender.file(uploads(login),attachments.getJSONObject(i).getString("token")).delete();}catch(Exception ignored){}}
                }catch(MailSender.Uncertain unknown){try{transferReply(login,request,new JSONObject().put("uncertain",true).put("error","Связь оборвалась во время отправки: результат неизвестен. Проверьте ящик адресата перед повторной отправкой. Черновик сохранён."));}catch(Exception ignored){}}
                catch(javax.mail.AuthenticationFailedException auth){transferFailure(login,request,"SMTP отклонил вход. Проверьте настройки отправки и пароль аккаунта. Черновик сохранён.");}
                catch(IllegalArgumentException|javax.mail.internet.AddressException invalid){transferFailure(login,request,"Проверьте полные адреса получателей и поля письма. Черновик сохранён.");}
                catch(Exception error){transferFailure(login,request,"Не удалось отправить письмо. Проверьте сеть и настройки SMTP. Черновик сохранён.");}
                finally{sending.set(false);}
            });
        }
        @JavascriptInterface public void notificationSettings(){runOnUiThread(()->{android.content.Intent settings=new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(settings);});}
        @JavascriptInterface public void testNotification(){runOnUiThread(()->{if(!NoticeCenter.allowed(MainActivity.this)){pendingTestNotice=true;getSharedPreferences("notification-permission",MODE_PRIVATE).edit().putBoolean("requested",false).apply();requestNoticePermission();}else NoticeCenter.test(MainActivity.this);});}
        @JavascriptInterface public void continuePendingNavigation(){runOnUiThread(()->resumePendingNavigation());}
        @JavascriptInterface public boolean offlineMode(){return offlineSession;}
        @JavascriptInterface public void reconnectMailbox(String request){final SavedAccounts.Login owner=sessionLogin;worker.execute(()->{if(owner==null||closing||sessionLogin!=owner)return;MailReader next=new MailReader();try{next.connect(owner.host,owner.port,owner.user,owner.password);if(closing||sessionLogin!=owner){next.close();return;}if(reader!=null)reader.close();reader=next;offlineSession=false;reply(request,new JSONObject().put("connected",true));runOnUiThread(()->{startWatch();BackgroundMailService.reconcile(MainActivity.this,resumed);});}catch(Exception error){next.close();if(sessionLogin==owner)readFailure(request,error,"Не удалось подключиться. Сохранённая почта и черновики остаются доступными.");}});}
        @JavascriptInterface public void clearOfflineMail(String request){final SavedAccounts.Login owner=sessionLogin;runOnUiThread(()->{if(owner==null||sessionLogin!=owner)return;NativeUi.dialog(MainActivity.this).setTitle("Удалить сохранённую почту?").setMessage("Удалятся только офлайн-копии писем этого ящика. Письма на сервере, черновики и календарь останутся.").setNegativeButton("Отмена",(dialog,which)->reply(request,new JSONObjectSafe().putValue("cancelled",true))).setPositiveButton("Удалить",(dialog,which)->worker.execute(()->{if(sessionLogin!=owner)return;++prefetchSerial;prefetchPage=null;prefetchOwner=null;try{if(offlineMail!=null)offlineMail.clear();else OfflineMailStore.delete(MainActivity.this,owner);offlineMail=new OfflineMailStore(MainActivity.this,owner);reply(request,new JSONObject().put("cleared",true));}catch(Exception error){failure(request,"Не удалось очистить сохранённую почту.");}})).show();});}
        @JavascriptInterface public void downloadAttachments(String json,String request){
            if(offlineSession){failure(request,"Подключитесь к серверу для скачивания вложений.");return;}final SavedAccounts.Login owner=sessionLogin;if(owner==null||json==null||json.length()>200000||!batchBusy.compareAndSet(false,true)){failure(request,"Завершите текущее скачивание или повторите выбор вложений.");return;}
            try{JSONArray array=new JSONArray(json);if(array.length()<1||array.length()>BulkAttachments.MAX_FILES)throw new IllegalArgumentException();java.util.List<BulkAttachments.Ref> refs=new java.util.ArrayList<>();for(int i=0;i<array.length();i++){JSONObject ref=array.getJSONObject(i);refs.add(new BulkAttachments.Ref(ref.getString("folder"),ref.getString("uid"),ref.getString("validity"),ref.getString("path")));}batchCancel.set(false);runOnUiThread(()->{if(closing||sessionLogin!=owner){batchBusy.set(false);return;}batchRequest=request;batchOwner=owner;batchRefs=refs;android.content.Intent pick=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION|android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);try{startActivityForResult(pick,74);}catch(Exception error){batchRequest=null;batchOwner=null;batchRefs=null;batchBusy.set(false);failure(request,"Не найден системный выбор папки.");}});}catch(Exception invalid){batchBusy.set(false);failure(request,"Проверьте выбранные вложения (не более 500 файлов).");}
        }
        @JavascriptInterface public void cancelAttachmentBatch(){batchCancel.set(true);}
        @JavascriptInterface public void folders(String request){final SavedAccounts.Login owner=sessionLogin;worker.execute(()->{if(owner==null||sessionLogin!=owner)return;try{transferReply(owner,request,readWithReconnect(()->new JSONObject().put("folders",reader.folders())));}catch(Exception e){try{if(offlineEnabled()&&offlineMail!=null&&offlineMail.available()&&!(e instanceof AuthenticationFailedException)){transferReply(owner,request,offlineMail.folders());return;}}catch(Exception ignored){}readFailure(request,e,"Не удалось получить папки. Подключитесь к серверу или откройте сохранённую папку.");}});}
        @JavascriptInterface public void page(String name,int offset,String snapshot,String request){if(name==null||name.length()>2048||name.contains("\r")||name.contains("\n")||offset<0){failure(request,"Некорректные параметры списка.");return;}final SavedAccounts.Login owner=sessionLogin;worker.execute(()->{if(owner==null||sessionLogin!=owner)return;try{if(offlineSession||(snapshot!=null&&snapshot.startsWith("offline:"))){JSONObject cached=offlinePage(name,offset,snapshot,"");if(cached==null)throw new java.io.IOException("No saved page");transferReply(owner,request,cached);return;}JSONObject result=readWithReconnect(()->reader.page(name,offset,snapshot));if(MailWidget.installed(MainActivity.this)&&"INBOX".equalsIgnoreCase(name))MailWidget.record(MainActivity.this,owner,reader.unread(result));if(offlineEnabled()&&offlineMail!=null)try{offlineMail.savePage(name,offset,result);}catch(Exception cacheFailure){}if(sessionLogin==owner){transferReply(owner,request,result);if(offset==0&&offlineEnabled())prefetchBodies(owner,name,result);}}catch(Exception e){if(sessionLogin!=owner)return;JSONObject cached=e instanceof AuthenticationFailedException?null:offlinePage(name,offset,snapshot,"");if(cached!=null){transferReply(owner,request,cached);return;}readFailure(request,e,"В этой папке нет доступной сохранённой страницы. Подключитесь к серверу или обновите список.");}});}
        @JavascriptInterface public void body(String name,String uid,String validity,String request){
            if(name==null||name.length()>2048||name.contains("\r")||name.contains("\n")||uid==null||validity==null||!uid.matches("[0-9]{1,20}")||!validity.matches("[0-9]{1,20}")){failure(request,"Некорректный идентификатор письма.");return;}
            SavedAccounts.Login owner=sessionLogin;final OfflineMailStore bodyCacheStore=offlineMail;long generation=++bodySerial;
            bodyWorker.execute(()->{try{
                if(owner==null||closing||sessionLogin!=owner)return;if(generation!=bodySerial){bodyReply(owner,request,generation,new JSONObject());return;}
                JSONObject result;
                if(offlineSession){result=offlineBody(name,uid,validity);if(result==null)throw new java.io.IOException("No saved body");bodyReply(owner,request,generation,result);return;}
                try{result=bodyConnection(owner).body(name,uid,validity);}
                catch(javax.mail.AuthenticationFailedException rejected){throw rejected;}
                catch(MessagingException|java.io.IOException disconnected){closeBodyReader();if(closing||sessionLogin!=owner)return;if(generation!=bodySerial){bodyReply(owner,request,generation,new JSONObject());return;}result=bodyConnection(owner).body(name,uid,validity);}
                if(sessionLogin==owner&&offlineEnabled()&&bodyCacheStore!=null)try{bodyCacheStore.saveBody(name,uid,validity,result);}catch(Exception cacheFailure){}
                bodyReply(owner,request,generation,result);
            }catch(Exception unavailable){closeBodyReader();if(sessionLogin!=owner)return;JSONObject cached=unavailable instanceof AuthenticationFailedException?null:offlineBody(name,uid,validity);bodyReply(owner,request,generation,cached!=null?cached:new JSONObjectSafe().putValue("error","Текст этого письма ещё не сохранён. Подключитесь к серверу и откройте его."));}});
        }
        @JavascriptInterface public void cancelAdvancedSearch(){++advancedSerial;}
        @JavascriptInterface public void advancedSearchPage(String name,String json,int offset,String snapshot,String request){
            final SearchQuery.Filters filters;try{if(name==null||name.isEmpty()||name.length()>2048||name.matches("(?s).*\\p{Cntrl}.*")||json==null||json.length()>2000||offset<0)throw new IllegalArgumentException();filters=new SearchQuery.Filters(json);}catch(Exception invalid){failure(request,"Проверьте запрос и диапазон дат.");return;}
            SavedAccounts.Login owner=sessionLogin;long serial=++advancedSerial;worker.execute(()->{try{
                if(owner==null||closing||sessionLogin!=owner)return;if(serial!=advancedSerial){calendarReply(owner,request,new JSONObject().put("cancelled",true));return;}
                if(offlineSession||snapshot!=null&&snapshot.startsWith("offline:")){JSONObject cached=offlinePage(name,offset,snapshot,filters.query);if(cached==null)throw new java.io.IOException("No saved search");filterOfflineFrom(cached,filters.from);calendarReply(owner,request,SearchQuery.filtered(cached,filters));return;}
                calendarReply(owner,request,readWithReconnect(()->reader.smartPage(name,offset,snapshot,filters)));
            }catch(Exception unavailable){if(sessionLogin!=owner||closing)return;JSONObject cached=unavailable instanceof AuthenticationFailedException?null:offlinePage(name,offset,snapshot,filters.query);try{if(cached!=null){filterOfflineFrom(cached,filters.from);calendarReply(owner,request,SearchQuery.filtered(cached,filters));return;}}catch(Exception invalid){}readFailure(request,unavailable,"Поиск недоступен. Проверьте подключение и повторите запрос.");}});
        }
        private void filterOfflineFrom(JSONObject result,String from)throws Exception{if(from.isEmpty())return;JSONArray out=new JSONArray(),rows=result.getJSONArray("messages");for(int i=0;i<rows.length();i++){JSONObject m=rows.getJSONObject(i);if((m.optString("sender")+" "+m.optString("email")).toLowerCase(java.util.Locale.ROOT).contains(from.toLowerCase(java.util.Locale.ROOT)))out.put(m);}result.put("messages",out);}
        @JavascriptInterface public void searchPage(String name,String query,int offset,String snapshot,String request){
            if(name==null||name.length()>2048||query==null||query.length()>200||query.matches("(?s).*\\p{Cntrl}.*")||offset<0){failure(request,"Проверьте поисковый запрос.");return;}
            SavedAccounts.Login owner=sessionLogin;worker.execute(()->{try{if(owner==null||sessionLogin!=owner)return;if(offlineSession||(snapshot!=null&&snapshot.startsWith("offline:"))){JSONObject cached=offlinePage(name,offset,snapshot,query);if(cached==null)throw new java.io.IOException("No saved search");calendarReply(owner,request,cached);}else calendarReply(owner,request,readWithReconnect(()->reader.page(name,offset,snapshot,query)));}catch(Exception e){if(sessionLogin==owner){JSONObject cached=e instanceof AuthenticationFailedException?null:offlinePage(name,offset,snapshot,query);if(cached!=null)calendarReply(owner,request,cached);else readFailure(request,e,"Поиск по сохранённым письмам недоступен. Подключитесь к серверу.");}}});
        }
        @JavascriptInterface public void filesPage(String name,String query,int offset,String snapshot,String request){
            if(name==null||name.length()>2048||query==null||query.length()>200||offset<0){failure(request,"Проверьте запрос поиска файлов.");return;}
            SavedAccounts.Login owner=sessionLogin;worker.execute(()->{try{if(owner==null||sessionLogin!=owner)return;calendarReply(owner,request,readWithReconnect(()->reader.files(name,offset,snapshot,query)));}catch(Exception e){if(sessionLogin==owner)readFailure(request,e,"Не удалось получить вложения. Повторите поиск.");}});
        }
        @JavascriptInterface public void saveDocument(String name,String uid,String validity,String path,boolean message,String request){
            if(name==null||name.length()>2048||uid==null||validity==null||!uid.matches("[0-9]{1,20}")||!validity.matches("[0-9]{1,20}")||path==null||path.length()>160){failure(request,"Некорректные параметры файла.");return;}
            SavedAccounts.Login owner=sessionLogin;worker.execute(()->{try{if(owner==null||sessionLogin!=owner)return;JSONObject downloaded=readWithReconnect(()->message?reader.exportMessage(name,uid,validity,new java.io.File(getCacheDir(),"attachments")):reader.attachmentMeasured(name,uid,validity,path,new java.io.File(getCacheDir(),"attachments"),()->closing||sessionLogin!=owner,(bytes,total)->attachmentProgress(owner,request,"download",bytes,total)));attachmentProgress(owner,request,"folder",0,-1);runOnUiThread(()->chooseExport(request,downloaded,owner));}catch(Exception e){if(sessionLogin==owner)readFailure(request,e,"Не удалось скачать файл для сохранения. Проверьте сеть и размер файла (до 100 МБ).");}});
        }
        @JavascriptInterface public void attachment(String name,String uid,String validity,String path,String request){
            if(name==null||name.length()>2048||name.contains("\r")||name.contains("\n")||uid==null||validity==null||!uid.matches("[0-9]{1,20}")||!validity.matches("[0-9]{1,20}")||path==null||path.length()>160){failure(request,"Некорректные параметры вложения.");return;}
            SavedAccounts.Login owner=sessionLogin;worker.execute(()->{try{if(owner==null||sessionLogin!=owner||closing)return;JSONObject downloaded=readWithReconnect(()->reader.attachmentMeasured(name,uid,validity,path,new java.io.File(getCacheDir(),"attachments"),()->closing||sessionLogin!=owner,(bytes,total)->attachmentProgress(owner,request,"download",bytes,total)));runOnUiThread(()->openAttachmentFile(request,downloaded,owner));}catch(Exception error){if(sessionLogin==owner&&!closing)readFailure(request,error,"Не удалось скачать вложение. Проверьте сеть и повторите попытку. Максимальный размер файла — 100 МБ.");}});
        }
        @JavascriptInterface public void geminiSettings(){runOnUiThread(()->{
            if(AppLock.locked(MainActivity.this)||!resumed)return;
            final AiVault secret=new AiVault(MainActivity.this);EditText input=new EditText(NativeUi.context(MainActivity.this,true));input.setSingleLine(true);input.setHint("API-ключ Gemini");input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(512)});
            LinearLayout layout=new LinearLayout(NativeUi.context(MainActivity.this,true));layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(dp(20),dp(10),dp(20),dp(10));TextView hint=new TextView(NativeUi.context(MainActivity.this,true));hint.setText(""+(secret.configured()?"Ключ сохранён. Можно заменить или удалить.\n":"")+"Ключ хранится зашифрованным на телефоне. При открытии умной панели выбранные тексты писем передаются Google Gemini. Вложения не отправляются.");layout.addView(hint);layout.addView(input);CheckBox show=new CheckBox(NativeUi.context(MainActivity.this,true));show.setText("Показать введённый ключ");show.setOnCheckedChangeListener((b,checked)->{input.setTransformationMethod(checked?null:android.text.method.PasswordTransformationMethod.getInstance());input.setSelection(input.length());});layout.addView(show);
            android.app.AlertDialog dialog=NativeUi.dialog(MainActivity.this).setTitle("Gemini · умная сводка").setView(layout).setPositiveButton("Сохранить",null).setNeutralButton("Удалить",null).setNegativeButton("Отмена",null).create();dialog.setOnShowListener(d->{dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.getButton(-1).setOnClickListener(v->{try{secret.save(input.getText().toString());input.setText("");dialog.dismiss();Toast.makeText(MainActivity.this,"Ключ сохранён",Toast.LENGTH_SHORT).show();}catch(Exception invalid){input.setError("Не удалось сохранить ключ. Проверьте значение.");}});dialog.getButton(-3).setOnClickListener(v->{try{secret.remove();gemini.cancel();++digestSerial;input.setText("");dialog.dismiss();}catch(Exception error){input.setError("Не удалось удалить ключ.");}});});dialog.show();
        });}
        @JavascriptInterface public boolean geminiReady(){return new AiVault(MainActivity.this).configured();}
        @JavascriptInterface public void cancelDigest(){++digestSerial;gemini.cancel();}
        @JavascriptInterface public void previews(String folder,String json,String request){
            final org.json.JSONArray refs;
            try{if(folder==null||folder.length()>2048||folder.contains("\r")||folder.contains("\n"))throw new IllegalArgumentException();refs=DigestBatch.references(json);if(refs.length()>5)throw new IllegalArgumentException();}catch(Exception invalid){failure(request,"Некорректные параметры превью.");return;}
            SavedAccounts.Login owner=sessionLogin;worker.execute(()->{try{if(owner==null||closing||sessionLogin!=owner)return;calendarReply(owner,request,readWithReconnect(()->reader.previews(folder,refs)));}catch(Exception unavailable){if(sessionLogin==owner)calendarReply(owner,request,new JSONObject());}});
        }
        @JavascriptInterface public void digestBodies(String folder,String json,String request){digestBatch(folder,json,request,false);}
        @JavascriptInterface public void smartDigest(String folder,String json,String request){if(!aiEnabled()){failure(request,"Нейроанализ отключён в настройках этого ящика.");return;}digestBatch(folder,json,request,true);}
        private void digestBatch(String folder,String json,String request,boolean smart){
            final org.json.JSONArray refs;
            try{if(folder==null||folder.length()>2048||folder.contains("\r")||folder.contains("\n"))throw new IllegalArgumentException();refs=DigestBatch.references(json);}catch(Exception invalid){failure(request,"Выберите от 1 до 20 писем.");return;}
            SavedAccounts.Login owner=sessionLogin;long generation=++digestSerial;
            worker.execute(()->{
                org.json.JSONArray items=new org.json.JSONArray();int failed=0;long started=System.nanoTime();
                for(int i=0;i<refs.length();i++){
                    if(closing||sessionLogin!=owner||owner==null||generation!=digestSerial)return;
                    if(System.nanoTime()-started>java.util.concurrent.TimeUnit.SECONDS.toNanos(60)){failed+=refs.length()-i;break;}
                    try{JSONObject ref=refs.getJSONObject(i);JSONObject body=readWithReconnect(()->reader.body(folder,ref.getString("uid"),ref.getString("validity")));items.put(DigestBatch.item(folder,ref,body));}
                    catch(AuthenticationFailedException rejected){runOnUiThread(()->{if(sessionLogin==owner&&generation==digestSerial)readFailure(request,rejected,"");});return;}
                    catch(Exception unavailable){failed++;}
                }
                if(generation==digestSerial&&sessionLogin==owner)try{JSONObject result=new JSONObject().put("items",items).put("failed",failed);if(smart){if(!aiEnabled())return;if(items.length()==0)throw new java.io.IOException("Не удалось загрузить тексты писем. Повторите попытку.");result.put("analysis",gemini.analyze(new AiVault(MainActivity.this).load(),items,owner.user,()->generation==digestSerial&&sessionLogin==owner&&!closing&&aiEnabled()));}runOnUiThread(()->{if(generation==digestSerial&&sessionLogin==owner&&!closing&&web!=null)reply(request,result);});}catch(Exception error){String message=error instanceof java.io.IOException&&error.getMessage()!=null&&error.getMessage().matches("(?:Gemini|Доступ|Лимит|Модель|Добавьте|Не удалось загрузить|Некоррект|Сводка|Неизвестный|Слишком).*" )?error.getMessage():"Не удалось получить сводку Gemini. Проверьте сеть и ключ в настройках.";runOnUiThread(()->{if(generation==digestSerial&&sessionLogin==owner&&!closing)failure(request,message);});}
            });
        }
        @JavascriptInterface public void watchFolder(String folder){if(folder==null||folder.length()>2048||folder.contains("\r")||folder.contains("\n"))return;runOnUiThread(()->{watchFolder=folder;startWatch();});}
        @JavascriptInterface public void logout(){leaveAccount();}
        @JavascriptInterface public void removeUpload(String token){SavedAccounts.Login owner=sessionLogin;if(owner==null)return;worker.execute(()->{try{MailSender.file(uploads(owner),token).delete();}catch(Exception ignored){}});}
        @JavascriptInterface public void switchAccount(){runOnUiThread(()->quickAccounts());}
    }
    @Override protected void onResume(){super.onResume();resumed=true;MailWidget.request(this);worker.execute(()->{if(prefetchPage!=null&&prefetchOwner==sessionLogin&&!offlineSession)prefetchBodies(prefetchOwner,prefetchFolder,prefetchPage);ScheduledMail.reconcile(this);try{NoticeCenter.snoozes(this);}catch(Exception ignored){}});NativeUi.bars(this);if(web!=null)web.evaluateJavascript("window.systemThemeChanged&&window.systemThemeChanged()",null);startWatch();BackgroundMailService.reconcile(this,true);resumePendingNavigation();}
    @Override protected void onPause(){resumed=false;++prefetchSerial;if(web!=null)web.evaluateJavascript("window.sendWindowHidden&&window.sendWindowHidden()",null);stopWatch();super.onPause();}
    @Override public void onBackPressed(){if(web!=null){web.evaluateJavascript("window.appBack&&window.appBack()",null);}else if(loginScreen&&hasSavedAccounts){showAccounts();}else super.onBackPressed();}
    @Override protected void onDestroy(){closing=true;++prefetchSerial;prefetchWorker.shutdown();batchCancel.set(true);++bodySerial;stopWatch();bodyWorker.execute(this::closeBodyReader);bodyWorker.shutdown();draftWorker.shutdownNow();calendarWorker.shutdownNow();directoryWorker.execute(universityDirectory::close);directoryWorker.shutdown();if(web!=null){web.removeJavascriptInterface("Pilot");web.destroy();web=null;}worker.execute(()->{if(reader!=null)reader.close();sessionLogin=null;});worker.shutdown();gemini.cancel();super.onDestroy();}
}


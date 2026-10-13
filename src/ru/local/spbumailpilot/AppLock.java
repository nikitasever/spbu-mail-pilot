package ru.local.spbumailpilot;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.ColorDrawable;
import android.hardware.biometrics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Opt-in app UI lock. System biometric success or trusted device-credential result only. */
final class AppLock {
    static final int CREDENTIAL_BASE=AppLockSession.CREDENTIAL_BASE;
    private final Context context;private final SharedPreferences prefs;private final AppLockSession session;
    private final Map<Activity,Dialog> masks=new WeakHashMap<>();private final Set<Activity> activities=Collections.newSetFromMap(new WeakHashMap<Activity,Boolean>());
    private final Map<Activity,TextView> messages=new WeakHashMap<>();private Request auth;
    private static final class Request {final Activity owner;final long ticket;final boolean enable,external;int resultCode;CancellationSignal signal;boolean success;Request(Activity a,long t,boolean e,boolean x){owner=a;ticket=t;enable=e;external=x;}}
    AppLock(Context c){context=c;prefs=c.getSharedPreferences("app-ui-lock",Context.MODE_PRIVATE);boolean enabled;try{enabled=prefs.getBoolean("enabled",false);}catch(RuntimeException corrupt){enabled=true;}session=new AppLockSession(enabled);}
    private static AppLock get(Context c){return ((PilotApplication)c.getApplicationContext()).locks;}
    static boolean enabled(Context c){return get(c).session.enabled();}
    static boolean locked(Context c){return !get(c).session.allowed();}
    static void window(Activity a){get(a).apply(a);}
    static void settings(MainActivity a){get(a).configure(a);}
    static boolean result(Activity a,int request,int result){if(request<CREDENTIAL_BASE)return false;get(a).credentialResult(a,request,result);return true;}
    void created(Activity a){activities.add(a);apply(a);}
    void started(Activity a){session.start(a);}
    private void apply(Activity a){if(session.enabled())a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);else if(!(a instanceof MainActivity)||((MainActivity)a).mailSurface())a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    void resumed(Activity a){
        apply(a);if(auth!=null&&auth.owner==a&&auth.external&&auth.success){complete(auth);}
        if(!session.allowed()){
            boolean fresh=!masks.containsKey(a);if(fresh)mask(a);
            if(fresh&&auth==null)authenticate(a,false,false);
        }
    }
    void stopped(Activity a){
        session.stop(a,a.isChangingConfigurations());
        if(auth!=null&&auth.owner==a&&!auth.external){cancel(auth);}
    }
    void destroyed(Activity a){
        if(auth!=null&&auth.owner==a)cancel(auth);session.destroy(a,a.isChangingConfigurations());activities.remove(a);messages.remove(a);Dialog dialog=masks.remove(a);if(dialog!=null)dialog.dismiss();
    }
    private boolean secure(){KeyguardManager keyguard=context.getSystemService(KeyguardManager.class);return keyguard!=null&&keyguard.isDeviceSecure();}
    private void message(Activity a,String text){TextView view=messages.get(a);if(view!=null)view.setText(text);else Toast.makeText(a,text,Toast.LENGTH_LONG).show();}
    private void mask(Activity a){
        Dialog dialog=new Dialog(NativeUi.context(a,true));dialog.setCancelable(false);dialog.setCanceledOnTouchOutside(false);
        LinearLayout box=new LinearLayout(NativeUi.context(a,false));box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(NativeUi.dp(a,24),NativeUi.dp(a,24),NativeUi.dp(a,24),NativeUi.dp(a,24));box.setBackgroundColor(NativeUi.paper(a));
        TextView title=new TextView(NativeUi.context(a,false));title.setText("Почта заблокирована");title.setTextSize(22);title.setTextColor(NativeUi.ink(a));box.addView(title);
        TextView text=new TextView(NativeUi.context(a,false));text.setText("Подтвердите биометрию или PIN устройства.");text.setTextSize(15);text.setTextColor(NativeUi.muted(a));text.setPadding(0,NativeUi.dp(a,12),0,NativeUi.dp(a,12));text.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);box.addView(text);messages.put(a,text);
        Button unlock=NativeUi.button(a,true);unlock.setText("Разблокировать");unlock.setOnClickListener(v->authenticate(a,false,false));box.addView(unlock);
        Button pin=NativeUi.button(a,false);pin.setText("PIN или пароль устройства");pin.setOnClickListener(v->authenticate(a,false,true));box.addView(pin);
        Button device=NativeUi.button(a,false);device.setText("Защита устройства в Android");device.setOnClickListener(v->{try{a.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));}catch(Exception unavailable){message(a,"Откройте настройки Android и включите PIN устройства.");}});box.addView(device);if(!secure())text.setText("На устройстве отключён PIN. Настройте защиту экрана, затем подтвердите вход.");
        Button leave=NativeUi.button(a,false);leave.setText("Закрыть приложение");leave.setOnClickListener(v->a.moveTaskToBack(true));box.addView(leave);
        ScrollView scroll=new ScrollView(NativeUi.context(a,false));scroll.setFillViewport(true);scroll.setBackgroundColor(NativeUi.paper(a));scroll.addView(box,new android.widget.FrameLayout.LayoutParams(-1,-2));
        dialog.setContentView(scroll,new android.view.ViewGroup.LayoutParams(-1,-1));dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.getWindow().setBackgroundDrawable(new ColorDrawable(NativeUi.paper(a)));dialog.show();dialog.getWindow().setLayout(-1,-1);masks.put(a,dialog);
    }
    private void configure(MainActivity a){
        if(!session.allowed()){message(a,"Сначала разблокируйте приложение.");return;}
        boolean enabled=session.enabled();String info="Блокировка действует для всех ящиков и экранов приложения при полном уходе в фон.\n\nБиометрия или PIN устройства. Снимки экранов приложения и превью в недавних будут защищены. Фоновая почта, напоминания и отправка по расписанию продолжат работать. Виджеты и уведомления защищаются отдельно настройками Android.";
        NativeUi.dialog(a).setTitle(enabled?"Блокировка включена":"Блокировка приложения").setMessage(info).setNegativeButton("Отмена",null).setPositiveButton(enabled?"Отключить":"Включить",(d,w)->{
            if(!session.allowed()){message(a,"Сначала разблокируйте приложение.");return;}
            if(enabled){if(!prefs.edit().putBoolean("enabled",false).commit()){message(a,"Не удалось сохранить настройку. Блокировка осталась включена.");return;}session.enabled(false);for(Activity activity:new ArrayList<>(activities))apply(activity);a.appLockChanged();}
            else if(!secure())message(a,"Сначала включите PIN или пароль экрана в настройках Android.");else authenticate(a,true,false);
        }).show();
    }
    private void cancel(Request r){
        if(auth!=r)return;auth=null;session.cancel(r.owner);if(r.signal!=null)r.signal.cancel();
    }
    private void authenticate(Activity a,boolean enable,boolean credential){
        if(a.isFinishing()||a.isDestroyed())return;
        if(auth!=null){if(auth.owner==a&&(credential||auth.external))cancel(auth);else return;}
        if(!secure()){message(a,"Включите PIN устройства в настройках Android.");return;}
        if(credential||Build.VERSION.SDK_INT<28){credential(a,enable);return;}
        Request r=new Request(a,session.begin(a,false),enable,false);auth=r;r.signal=new CancellationSignal();
        try{
            BiometricPrompt.Builder builder=new BiometricPrompt.Builder(a).setTitle(enable?"Включить блокировку почты":"Разблокировать почту").setSubtitle("Подтвердите вход на устройстве").setNegativeButton("PIN устройства",a.getMainExecutor(),(d,w)->{if(auth==r){cancel(r);credential(a,enable);}});
            if(Build.VERSION.SDK_INT>=30)builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG);
            builder.build().authenticate(r.signal,a.getMainExecutor(),new BiometricPrompt.AuthenticationCallback(){
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){if(auth==r)complete(r);}
                @Override public void onAuthenticationFailed(){if(auth==r)message(a,"Не распознано. Попробуйте ещё раз или используйте PIN.");}
                @Override public void onAuthenticationError(int code,CharSequence text){if(auth==r){cancel(r);boolean cancelled=code==BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED||code==BiometricPrompt.BIOMETRIC_ERROR_CANCELED;if(enable&&!cancelled)credential(a,true);else message(a,enable?"Включение блокировки отменено.":"Биометрия не подтверждена. Можно повторить или использовать PIN устройства.");}}
            });
        }catch(Exception unavailable){cancel(r);if(enable)credential(a,true);else message(a,"Биометрия недоступна. Используйте PIN устройства.");}
    }
    private void credential(Activity a,boolean enable){
        if(auth!=null)return;
        try{
            Intent intent=context.getSystemService(KeyguardManager.class).createConfirmDeviceCredentialIntent(enable?"Включить блокировку почты":"Разблокировать почту","Подтвердите PIN или пароль устройства");
            if(intent==null)throw new IllegalStateException();ResolveInfo target=a.getPackageManager().resolveActivity(intent,PackageManager.MATCH_DEFAULT_ONLY|PackageManager.MATCH_SYSTEM_ONLY);if(target==null||target.activityInfo==null)throw new IllegalStateException();intent.setComponent(new ComponentName(target.activityInfo.packageName,target.activityInfo.name));
            // These screens extend framework Activity (full int request codes). Persist before
            // launch; never reuse a code after restart or replacement of a credential challenge.
            long code=prefs.getLong("credential-sequence",CREDENTIAL_BASE);int resultCode=AppLockSession.credentialCode(code);
            if(resultCode<0||!prefs.edit().putLong("credential-sequence",code+1).commit())throw new IllegalStateException();
            Request r=new Request(a,session.begin(a,true),enable,true);r.resultCode=resultCode;auth=r;a.startActivityForResult(intent,resultCode);
        }catch(Exception unavailable){if(auth!=null&&auth.owner==a)cancel(auth);message(a,"Не удалось открыть подтверждение PIN. Проверьте защиту экрана в настройках Android.");}
    }
    private void credentialResult(Activity a,int request,int result){
        Request r=auth;if(r==null||r.owner!=a||!r.external||r.resultCode!=request)return;
        if(result!=Activity.RESULT_OK){cancel(r);message(a,r.enable?"Включение блокировки отменено.":"Вход не подтверждён. Приложение осталось заблокированным.");return;}
        r.success=true;complete(r);
    }
    private void complete(Request r){
        if(auth!=r||r.owner.isFinishing()||r.owner.isDestroyed())return;
        if(!session.current(r.owner,r.ticket)){cancel(r);message(r.owner,"Подтверждение устарело. Повторите вход.");return;}
        if(!session.accept(r.owner,r.ticket))return;
        auth=null;
        if(r.enable){if(!prefs.edit().putBoolean("enabled",true).commit()){message(r.owner,"Не удалось сохранить блокировку. Она не включена.");return;}session.enabled(true);}
        for(Activity a:new ArrayList<>(activities))apply(a);for(Dialog d:new ArrayList<>(masks.values()))d.dismiss();masks.clear();messages.clear();
        if(r.owner instanceof MainActivity)((MainActivity)r.owner).appLockChanged();
    }
}

package ru.local.spbumailpilot;
import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
final class NativeUi {
 static boolean dark(Activity a){return a instanceof MainActivity?((MainActivity)a).nativeDark():a instanceof PdfPreviewActivity?((PdfPreviewActivity)a).nativeDark():a instanceof DocxPreviewActivity?((DocxPreviewActivity)a).nativeDark():(a.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;}
 static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
 static int paper(Activity a){return dark(a)?0xff18171e:0xffffffff;}
 static int surface(Activity a){return dark(a)?0xff24202c:0xfff8f7fb;}
 static int ink(Activity a){return dark(a)?0xfff3eff8:0xff292533;}
 static int muted(Activity a){return dark(a)?0xffbdb3ca:0xff70697b;}
 static int brand(Activity a){return dark(a)?0xffceacf3:0xff7946ae;}
 static int tint(Activity a){return dark(a)?0xff352443:0xfff0e8f9;}
 static int color(Activity a,int value){if(value==0xff29262e)return ink(a);if(value==0xff726d7c)return muted(a);if(value==0xff8051b1)return brand(a);if(value==0xff9b422b)return dark(a)?0xffffb2ac:0xffa43e38;return value;}
 static Context context(Activity a,boolean dialog){String name=(dialog?"PilotDialog":"PilotNative")+(dark(a)?"Dark":"Light");int style=a.getResources().getIdentifier(name,"style",a.getPackageName());return new ContextThemeWrapper(a,style);}
 static AlertDialog.Builder dialog(Activity a){int style=a.getResources().getIdentifier(dark(a)?"PilotDialogDark":"PilotDialogLight","style",a.getPackageName());return new AlertDialog.Builder(a,style){@Override public AlertDialog create(){AlertDialog dialog=super.create();if(AppLock.enabled(a))dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);return dialog;}};}
 static GradientDrawable shape(Activity a,int color,boolean stroke,int radius){GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(a,radius));if(stroke)drawable.setStroke(dp(a,1),dark(a)?0xff37303f:0xffeeebf3);return drawable;}
 static Button button(Activity a,boolean primary){Button b=new Button(context(a,false));b.setAllCaps(false);b.setTextSize(15);b.setTextColor(primary?(dark(a)?0xff2c153e:0xffffffff):brand(a));b.setBackgroundTintList(null);b.setBackground(new RippleDrawable(ColorStateList.valueOf(dark(a)?0x22ffffff:0x227946ae),shape(a,primary?brand(a):surface(a),!primary,12),null));b.setPadding(dp(a,16),dp(a,12),dp(a,16),dp(a,12));b.setMinHeight(dp(a,48));LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.setMargins(0,dp(a,6),0,dp(a,6));b.setLayoutParams(layout);return b;}
 static LinearLayout accountRow(Activity a,SavedAccounts.Login login,boolean selected){LinearLayout row=new LinearLayout(context(a,false));row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(a,12),dp(a,14),dp(a,12),dp(a,14));row.setMinimumHeight(dp(a,76));row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x227946ae),shape(a,selected?tint(a):paper(a),false,14),null));
  TextView avatar=new TextView(context(a,false));avatar.setText(login.user.substring(0,Math.min(2,login.user.length())).toUpperCase(java.util.Locale.ROOT));avatar.setTextColor(brand(a));avatar.setTextSize(15);avatar.setGravity(Gravity.CENTER);avatar.setBackground(shape(a,tint(a),false,24));LinearLayout.LayoutParams icon=new LinearLayout.LayoutParams(dp(a,44),dp(a,44));icon.setMargins(0,0,dp(a,12),0);row.addView(avatar,icon);
  LinearLayout labels=new LinearLayout(context(a,false));labels.setOrientation(LinearLayout.VERTICAL);TextView title=new TextView(context(a,false));title.setText(login.user);title.setTextColor(ink(a));title.setTextSize(16);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(title);TextView service=new TextView(context(a,false));service.setText(AppearancePolicy.service(login.host));service.setTextColor(muted(a));service.setTextSize(12);service.setPadding(0,dp(a,4),0,0);labels.addView(service);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
  if(selected){TextView check=new TextView(context(a,false));check.setText("✓");check.setTextColor(brand(a));check.setTextSize(22);check.setPadding(dp(a,10),0,0,0);row.addView(check);row.setContentDescription(login.user+", "+AppearancePolicy.service(login.host)+", текущий аккаунт");}else row.setContentDescription(login.user+", "+AppearancePolicy.service(login.host));row.setFocusable(true);return row;
 }
 static void bars(Activity a){int color=paper(a);a.getWindow().setStatusBarColor(color);a.getWindow().setNavigationBarColor(color);int flags=a.getWindow().getDecorView().getSystemUiVisibility(),mask=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;a.getWindow().getDecorView().setSystemUiVisibility(dark(a)?flags&~mask:flags|mask);}
}


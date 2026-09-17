package pl.tachograf2;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity {
    static final int CAPTURE=401;
    TextView speed,status,ocr,mode,times;
    Button captureBtn;
    long driveMs=0,totalMs=0,restMs=0,lastTick=System.currentTimeMillis();
    boolean driving=false;
    SharedPreferences prefs;

    @Override public void onCreate(Bundle b){
        super.onCreate(b); prefs=getSharedPreferences("tachograf",0); buildUi();
        IntentFilter filter=new IntentFilter(ScreenCaptureService.ACTION_SPEED);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(receiver,filter);
        if(Build.VERSION.SDK_INT>=33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},55);
        new Handler(Looper.getMainLooper()).post(tick);
    }

    void buildUi(){
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(18,18,18,24); root.setBackgroundColor(Color.rgb(9,10,11));
        TextView title=t("🚛 TACHOGRAF CYFROWY",23); title.setGravity(Gravity.CENTER); root.addView(title,lp());
        TextView sub=t("TRUCKERS OF EUROPE 3 • APK • MEDIA PROJECTION + OCR",11); sub.setGravity(Gravity.CENTER); root.addView(sub,lp());
        speed=t("0 km/h",44); speed.setGravity(Gravity.CENTER); speed.setPadding(0,28,0,10); root.addView(speed,lp());
        mode=t("🛏 ODPOCZYNEK",16); mode.setGravity(Gravity.CENTER); root.addView(mode,lp());
        status=t("Status: NIEPOŁĄCZONY",14); ocr=t("OCR: ---",12); root.addView(status,lp()); root.addView(ocr,lp());
        captureBtn=new Button(this); captureBtn.setText("📡 URUCHOM AUTOMATYCZNY ODCZYT"); captureBtn.setOnClickListener(v->startCapture()); root.addView(captureBtn,lp());
        Button stop=new Button(this); stop.setText("⏹ ZATRZYMAJ ODCZYT"); stop.setOnClickListener(v->stopCapture()); root.addView(stop,lp());
        LinearLayout modes=new LinearLayout(this); modes.setOrientation(LinearLayout.HORIZONTAL);
        Button drive=new Button(this); drive.setText("🚗 JAZDA"); Button rest=new Button(this); rest.setText("🛏 ODPOCZYNEK");
        modes.addView(drive,new LinearLayout.LayoutParams(0,-2,1)); modes.addView(rest,new LinearLayout.LayoutParams(0,-2,1)); root.addView(modes,lp());
        drive.setOnClickListener(v->{driving=true;mode.setText("🚗 JAZDA");}); rest.setOnClickListener(v->{driving=false;mode.setText("🛏 ODPOCZYNEK");});
        TextView info=t("\n📱 JAK TO DZIAŁA\nMediaProjection przechwytuje obraz ekranu po zgodzie Androida. OCR analizuje dokładnie dolny lewy HUD-u TOEU3 i szuka prędkości przy oznaczeniu km/h. Aplikacja działa jako usługa pierwszego planu, więc możesz wrócić do gry.\n\n⚠️ To symulator RP — nie jest certyfikowanym tachografem.",13); root.addView(info,lp());
        TextView cropTitle=t("\n🎯 OBSZAR HUD-U TOEU3 — PRĘDKOŚĆ",16); root.addView(cropTitle,lp());
        TextView cropHint=t("Dla HUD-u z Twojego zrzutu ustawienia startowe to: X 25%, Y 87%, szerokość 7%, wysokość 9%.",12); root.addView(cropHint,lp());
        LinearLayout crop=new LinearLayout(this); crop.setOrientation(LinearLayout.VERTICAL);
        EditText x=field("X %",prefs.getInt("x",25)); EditText y=field("Y %",prefs.getInt("y",87)); EditText w=field("Szerokość %",prefs.getInt("w",7)); EditText h=field("Wysokość %",prefs.getInt("h",9));
        crop.addView(x);crop.addView(y);crop.addView(w);crop.addView(h); root.addView(crop,lp());
        Button save=new Button(this); save.setText("💾 ZAPISZ OBSZAR HUD"); save.setOnClickListener(v->{prefs.edit().putInt("x",num(x,25)).putInt("y",num(y,87)).putInt("w",num(w,7)).putInt("h",num(h,9)).apply(); Toast.makeText(this,"Obszar HUD zapisany",Toast.LENGTH_SHORT).show();}); root.addView(save,lp());
        Button defaults=new Button(this); defaults.setText("🎯 DOMYŚLNY OBSZAR — TEN HUD"); defaults.setOnClickListener(v->{x.setText("25");y.setText("87");w.setText("7");h.setText("9");}); root.addView(defaults,lp());
        times=t("Jazda od pauzy: 00:00:00\nŁączna jazda: 00:00:00\nDystans: 0.0 km",14); times.setPadding(4,20,4,4); root.addView(times,lp());
        scroll.addView(root); setContentView(scroll);
    }
    LinearLayout.LayoutParams lp(){return new LinearLayout.LayoutParams(-1,-2);}
    TextView t(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextColor(Color.rgb(216,255,155));v.setTextSize(size);return v;}
    EditText field(String hint,int val){EditText e=new EditText(this);e.setHint(hint);e.setText(String.valueOf(val));e.setTextColor(Color.WHITE);e.setHintTextColor(Color.GRAY);e.setInputType(2);return e;}
    int num(EditText e,int def){try{return Math.max(0,Math.min(100,Integer.parseInt(e.getText().toString())));}catch(Exception ex){return def;}}
    void startCapture(){MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);startActivityForResult(m.createScreenCaptureIntent(),CAPTURE);}
    void stopCapture(){stopService(new Intent(this,ScreenCaptureService.class));status.setText("Status: ZATRZYMANY");captureBtn.setText("📡 URUCHOM AUTOMATYCZNY ODCZYT");}
    @Override protected void onActivityResult(int r,int c,Intent data){super.onActivityResult(r,c,data);if(r==CAPTURE&&c==RESULT_OK&&data!=null){Intent i=new Intent(this,ScreenCaptureService.class);i.putExtra("code",c);i.putExtra("data",data);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);status.setText("Status: ODCZYT AKTYWNY");captureBtn.setText("🟢 ODCZYT AKTYWNY");}}
    final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){int s=i.getIntExtra("speed",0);String text=i.getStringExtra("text");int conf=i.getIntExtra("confidence",0);speed.setText(s+" km/h");ocr.setText("OCR: "+(text==null?"---":text)+" • Pewność: "+conf+"%");if(s>0&&!driving){driving=true;mode.setText("🚗 JAZDA");}}};
    final Runnable tick=new Runnable(){public void run(){long now=System.currentTimeMillis(),d=Math.max(0,now-lastTick);lastTick=now;if(driving){driveMs+=d;totalMs+=d;restMs=0;}else{restMs+=d;if(restMs>=45*60*1000L&&driveMs>0)driveMs=0;}times.setText("Jazda od pauzy: "+fmt(driveMs)+"\nŁączna jazda: "+fmt(totalMs)+"\nDystans: naliczany na podstawie odczytanej prędkości\n\n⚠️ Limit ciągłej jazdy: 4:30 — symulator RP");new Handler(Looper.getMainLooper()).postDelayed(this,500);}};
    String fmt(long x){long s=x/1000,h=s/3600,m=(s%3600)/60,z=s%60;return String.format(Locale.US,"%02d:%02d:%02d",h,m,z);}
    @Override protected void onDestroy(){try{unregisterReceiver(receiver);}catch(Exception ignored){}super.onDestroy();}
}

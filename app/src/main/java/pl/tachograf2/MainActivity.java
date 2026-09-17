package pl.tachograf2;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity {
    static final int CAPTURE=401;
    TextView speed, status, ocr;
    Button captureBtn;
    long driveMs=0, totalMs=0; long lastTick=System.currentTimeMillis();
    boolean driving=false;

    @Override public void onCreate(Bundle b){ super.onCreate(b); buildUi();
        registerReceiver(receiver,new IntentFilter(ScreenCaptureService.ACTION_SPEED), Context.RECEIVER_NOT_EXPORTED);
        if(Build.VERSION.SDK_INT>=33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},55);
        new Handler().postDelayed(tick,500);
    }

    void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(18,18,18,18); root.setBackgroundColor(0xff090a0b);
        TextView title=t("🚛 TACHOGRAF CYFROWY",22); title.setGravity(17); root.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView sub=t("TRUCKERS OF EUROPE 3 • APK • AUTO OCR",11); sub.setGravity(17); root.addView(sub);
        speed=t("0 km/h",42); speed.setGravity(17); speed.setPadding(0,25,0,8); root.addView(speed);
        TextView mode=t("🛏 ODPOCZYNEK",15); mode.setGravity(17); root.addView(mode);
        LinearLayout grid=new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL); grid.setPadding(0,18,0,10);
        status=t("Status: NIEPOŁĄCZONY",14); ocr=t("OCR: ---",12); grid.addView(status); grid.addView(ocr); root.addView(grid);
        captureBtn=new Button(this); captureBtn.setText("📡 URUCHOM ODCZYT EKRANU"); captureBtn.setOnClickListener(v->startCapture()); root.addView(captureBtn);
        Button stop=new Button(this); stop.setText("⏹ ZATRZYMAJ ODCZYT"); stop.setOnClickListener(v->stopCapture()); root.addView(stop);
        LinearLayout modes=new LinearLayout(this); modes.setOrientation(LinearLayout.HORIZONTAL);
        Button drive=t("🚗 JAZDA",13); Button rest=t("🛏 ODPOCZYNEK",13); modes.addView(drive,new LinearLayout.LayoutParams(0,-2,1)); modes.addView(rest,new LinearLayout.LayoutParams(0,-2,1));
        drive.setOnClickListener(v->{driving=true; mode.setText("🚗 JAZDA");}); rest.setOnClickListener(v->{driving=false; mode.setText("🛏 ODPOCZYNEK");}); root.addView(modes);
        TextView times=t("Jazda od pauzy: 00:00:00\nŁączna jazda: 00:00:00\nDystans: 0.0 km\n\n⚠️ Aplikacja czyta tylko obraz ekranu TOEU3. Nie korzysta z wewnętrznych danych gry.",14); times.setPadding(4,20,4,4); root.addView(times);
        setContentView(root);
    }

    TextView t(String s,int size){ TextView v=new TextView(this); v.setText(s); v.setTextColor(0xffd8ff9b); v.setTextSize(size); return v; }
    void startCapture(){ MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE); startActivityForResult(m.createScreenCaptureIntent(),CAPTURE); }
    void stopCapture(){ stopService(new Intent(this,ScreenCaptureService.class)); status.setText("Status: ZATRZYMANY"); captureBtn.setText("📡 URUCHOM ODCZYT EKRANU"); }
    @Override protected void onActivityResult(int r,int c,Intent data){ super.onActivityResult(r,c,data); if(r==CAPTURE&&c==RESULT_OK&&data!=null){ Intent i=new Intent(this,ScreenCaptureService.class); i.putExtra("code",c); i.putExtra("data",data); if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i); status.setText("Status: ODCZYT AKTYWNY"); captureBtn.setText("🟢 ODCZYT AKTYWNY"); } }

    final BroadcastReceiver receiver=new BroadcastReceiver(){ public void onReceive(Context c,Intent i){ int s=i.getIntExtra("speed",0); String text=i.getStringExtra("text"); int conf=i.getIntExtra("confidence",0); speed.setText(s+" km/h"); ocr.setText("OCR: "+text+"   •   Pewność: "+conf+"%"); if(s>0&&!driving){driving=true;} } };
    final Runnable tick=new Runnable(){ public void run(){ long now=System.currentTimeMillis(),d=now-lastTick; lastTick=now; if(driving){driveMs+=d; totalMs+=d;} TextView v=(TextView)((ViewGroup)((ViewGroup)findViewById(android.R.id.content)).getChildAt(0)).getChildAt(8); v.setText("Jazda od pauzy: "+fmt(driveMs)+"\nŁączna jazda: "+fmt(totalMs)+"\n\n⚠️ 4:30 limit ciągłej jazdy — symulator RP"); new Handler().postDelayed(this,500); }};
    String fmt(long x){long s=x/1000,h=s/3600,m=(s%3600)/60,z=s%60;return String.format(Locale.US,"%02d:%02d:%02d",h,m,z);}
    @Override protected void onDestroy(){try{unregisterReceiver(receiver);}catch(Exception ignored){} super.onDestroy();}
}

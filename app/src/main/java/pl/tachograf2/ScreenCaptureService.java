package pl.tachograf2;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.util.DisplayMetrics;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.regex.*;

public class ScreenCaptureService extends Service {
    public static final String ACTION_SPEED="pl.tachograf2.SPEED";
    MediaProjection projection; VirtualDisplay display; ImageReader reader; TextRecognizer recognizer;
    Handler handler=new Handler(Looper.getMainLooper()); boolean busy=false; long lastOcr=0;
    MediaProjection.Callback projectionCallback;

    @Override public void onCreate(){
        super.onCreate();
        NotificationChannel ch=new NotificationChannel("tachograf","Tachograf2",NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        projectionCallback=new MediaProjection.Callback(){@Override public void onStop(){stopSelf();}};
    }

    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null)return START_NOT_STICKY;
        startForeground(7,new Notification.Builder(this,"tachograf").setContentTitle("Tachograf2 — TOEU3 OCR").setContentText("Automatyczny odczyt prędkości działa w tle").setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build());
        try{
            MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            int code=intent.getIntExtra("code",Activity.RESULT_CANCELED); Intent data;
            if(Build.VERSION.SDK_INT>=33)data=intent.getParcelableExtra("data",Intent.class);else data=intent.getParcelableExtra("data");
            if(data==null)return START_NOT_STICKY;
            projection=m.getMediaProjection(code,data); projection.registerCallback(projectionCallback,handler);
            DisplayMetrics dm=getResources().getDisplayMetrics(); int sw=dm.widthPixels,sh=dm.heightPixels;
            reader=ImageReader.newInstance(sw,sh,PixelFormat.RGBA_8888,2);
            reader.setOnImageAvailableListener(r->process(r,sw,sh),handler);
            display=projection.createVirtualDisplay("Tachograf2-TOEU3",sw,sh,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);
        }catch(Exception e){stopSelf();}
        return START_NOT_STICKY;
    }

    void process(ImageReader r,int sw,int sh){
        long now=System.currentTimeMillis(); if(busy||now-lastOcr<500)return;
        Image im=null; Bitmap full=null; Bitmap crop=null; Bitmap input=null;
        try{
            im=r.acquireLatestImage(); if(im==null)return; busy=true; lastOcr=now;
            Image.Plane p=im.getPlanes()[0]; int ps=p.getPixelStride(),rs=p.getRowStride(),pad=rs-ps*sw;
            full=Bitmap.createBitmap(sw+pad/ps,sh,Bitmap.Config.ARGB_8888); full.copyPixelsFromBuffer(p.getBuffer());
            SharedPreferences sp=getSharedPreferences("tachograf",0);

            // Prędkość w TOEU3 jest na dole po lewej. Domyślnie czytamy większy obszar,
            // żeby nie ucinać cyfr przy różnych rozdzielczościach telefonu.
            int x=clamp(sp.getInt("x",0),0,99);
            int y=clamp(sp.getInt("y",80),0,99);
            int w=clamp(sp.getInt("w",35),1,100-x);
            int h=clamp(sp.getInt("h",20),1,100-y);
            int left=sw*x/100,top=sh*y/100,cw=Math.max(1,sw*w/100),ch=Math.max(1,sh*h/100);
            crop=Bitmap.createBitmap(full,left,top,Math.min(cw,full.getWidth()-left),Math.min(ch,full.getHeight()-top));
            full.recycle();full=null;

            // Duże powiększenie poprawia rozpoznawanie małych cyfr HUD-u.
            input=Bitmap.createScaledBitmap(crop,Math.max(1,crop.getWidth()*3),Math.max(1,crop.getHeight()*3),true);
            crop.recycle();crop=null;
            Bitmap ocrBitmap=input;
            recognizer.process(InputImage.fromBitmap(ocrBitmap,0)).addOnSuccessListener(result->{
                String raw=result.getText()==null?"":result.getText();
                String txt=raw.replace('\n',' ').trim();
                int speed=parseSpeed(raw);
                int conf=speed>=0?estimateConfidence(raw,speed):0;
                if(speed<0)speed=0;

                Intent out=new Intent(ACTION_SPEED); out.setPackage(getPackageName());
                out.putExtra("speed",speed); out.putExtra("text",txt); out.putExtra("confidence",conf); sendBroadcast(out);
                getSharedPreferences("last",0).edit().putInt("speed",speed).putString("text",txt).putInt("confidence",conf).apply();
                try{ocrBitmap.recycle();}catch(Exception ignored){} busy=false;
            }).addOnFailureListener(e->{try{ocrBitmap.recycle();}catch(Exception ignored){}busy=false;});
            input=null;
        }catch(Exception e){busy=false;}finally{
            if(crop!=null)try{crop.recycle();}catch(Exception ignored){}
            if(input!=null)try{input.recycle();}catch(Exception ignored){}
            if(full!=null)try{full.recycle();}catch(Exception ignored){}
            if(im!=null)try{im.close();}catch(Exception ignored){}
        }
    }

    int parseSpeed(String s){
        String normalized=s.toLowerCase();
        // Najpierw szukamy liczby bezpośrednio związanej z km/h.
        Matcher km=Pattern.compile("(\\d{1,3})\\s*(?:km\\s*/?\\s*h|kmh|k[mn]\\s*/?\\s*h)").matcher(normalized);
        int best=-1;
        while(km.find()){
            int n=safe(km.group(1));
            if(n>=0&&n<=160)best=n;
        }
        if(best>=0)return best;

        // Jeśli gra pokazuje samo cyfry, wybieramy największą sensowną prędkość z HUD-u.
        Matcher m=Pattern.compile("(?<!\\d)(\\d{1,3})(?!\\d)").matcher(s);
        while(m.find()){
            int n=safe(m.group(1));
            if(n>=0&&n<=160&&n>best)best=n;
        }
        return best;
    }

    int estimateConfidence(String s,int speed){
        String n=s.toLowerCase();
        if(n.matches(".*\\b"+speed+"\\s*(km\\s*/?\\s*h|kmh)\\b.*"))return 98;
        return 82;
    }
    int safe(String s){try{return Integer.parseInt(s);}catch(Exception e){return -1;}}
    int clamp(int v,int a,int b){return Math.max(a,Math.min(b,v));}

    @Override public void onDestroy(){
        try{
            if(projection!=null)projection.unregisterCallback(projectionCallback);
            if(display!=null)display.release();
            if(reader!=null)reader.close();
            if(projection!=null)projection.stop();
            if(recognizer!=null)recognizer.close();
        }catch(Exception ignored){}
        super.onDestroy();
    }
    @Override public android.os.IBinder onBind(Intent i){return null;}
}

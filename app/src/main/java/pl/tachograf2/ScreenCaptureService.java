package pl.tachograf2;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import java.util.regex.*;

public class ScreenCaptureService extends Service {
    public static final String ACTION_SPEED="pl.tachograf2.SPEED";
    MediaProjection projection; VirtualDisplay display; ImageReader reader; TextRecognizer recognizer; boolean busy=false;
    final int x=25,y=78,w=50,h=22;

    @Override public void onCreate(){ super.onCreate();
        NotificationChannel ch=new NotificationChannel("tachograf","Tachograf2",NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        startForeground(7,new Notification.Builder(this,"tachograf").setContentTitle("Tachograf2 — OCR TOEU3").setContentText("Automatyczny odczyt ekranu jest aktywny").setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build());
        try{
            MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            int code=intent.getIntExtra("code",Activity.RESULT_CANCELED); Intent data=intent.getParcelableExtra("data");
            if(data==null) return START_NOT_STICKY;
            projection=m.getMediaProjection(code,data);
            DisplayMetrics dm=getResources().getDisplayMetrics(); int sw=dm.widthPixels, sh=dm.heightPixels;
            reader=ImageReader.newInstance(sw,sh,PixelFormat.RGBA_8888,2);
            reader.setOnImageAvailableListener(r->process(r,sw,sh),new Handler(Looper.getMainLooper()));
            display=projection.createVirtualDisplay("Tachograf2",sw,sh,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,null);
        }catch(Exception e){ stopSelf(); }
        return START_NOT_STICKY;
    }
    void process(ImageReader r,int sw,int sh){ if(busy)return; Image im=null; try{
        im=r.acquireLatestImage(); if(im==null)return; busy=true;
        Image.Plane p=im.getPlanes()[0]; int ps=p.getPixelStride(), rs=p.getRowStride(), pad=rs-ps*sw; Bitmap full=Bitmap.createBitmap(sw+pad/ps,sh,Bitmap.Config.ARGB_8888); full.copyPixelsFromBuffer(p.getBuffer());
        int left=Math.max(0,sw*x/100), top=Math.max(0,sh*y/100); int cw=Math.min(sw-left,sw*w/100), ch=Math.min(sh-top,sh*h/100); Bitmap crop=Bitmap.createBitmap(full,left,top,cw,ch); full.recycle();
        recognizer.process(InputImage.fromBitmap(crop,0)).addOnSuccessListener(result->{
            String txt=result.getText().replace('\n',' ').trim(); int speed=parseSpeed(result.getText()); int conf=speed>=0?90:0; if(speed<0)speed=0;
            Intent out=new Intent(ACTION_SPEED); out.setPackage(getPackageName()); out.putExtra("speed",speed); out.putExtra("text",txt); out.putExtra("confidence",conf); sendBroadcast(out);
            getSharedPreferences("last",0).edit().putInt("speed",speed).putString("text",txt).apply();
            crop.recycle(); busy=false;
        }).addOnFailureListener(e->{crop.recycle();busy=false;});
    }catch(Exception e){if(im!=null)im.close();busy=false;} finally{if(im!=null)im.close();}}

    int parseSpeed(String s){
        Matcher m=Pattern.compile("(?<!\\d)(\\d{1,3})(?!\\d)").matcher(s.replaceAll("[^0-9]"," "));
        int best=-1; while(m.find()){try{int n=Integer.parseInt(m.group(1));if(n>=0&&n<=160&&n>best)best=n;}catch(Exception ignored){}}
        return best;
    }
    @Override public void onDestroy(){try{if(display!=null)display.release();if(reader!=null)reader.close();if(projection!=null)projection.stop();if(recognizer!=null)recognizer.close();}catch(Exception ignored){}super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent i){return null;}
}

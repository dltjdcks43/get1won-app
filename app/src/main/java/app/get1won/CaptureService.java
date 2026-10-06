package app.get1won;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.view.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import java.util.*;
import java.nio.ByteBuffer;

/** Bundled Korean model. One OCR task and newest ImageReader buffer, never a screenshot file. */
public final class CaptureService extends Service {
    private HandlerThread thread;private Handler handler;private MediaProjection projection;
    private ImageReader reader;private VirtualDisplay display;private int width,height,rotation;
    private volatile boolean closing;private boolean busy;private long lastStamp;
    private TextRecognizer recognizer;
    public static volatile String info="화면 확인이 꺼져 있어요.";
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onCreate(){super.onCreate();AppState.initialize(this);thread=new HandlerThread("KoreanOcrLatestFrame");thread.start();handler=new Handler(thread.getLooper());recognizer=TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null || "STOP".equals(intent.getAction())){AppState.stop();stopSelf();return START_NOT_STICKY;}
        if(projection!=null)return START_NOT_STICKY;
        NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("capture","화면 확인",NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,CaptureService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
        startForeground(1,new Notification.Builder(this,"capture").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("1원 받기 — 화면 확인").setContentText("기기 안에서만 확인합니다").setOngoing(true).addAction(new Notification.Action.Builder(null,"중지",stop).build()).build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        try{
            Rect b=getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();width=b.width();height=b.height();rotation=getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY).getRotation();
            Intent data=intent.getParcelableExtra("data",Intent.class);if(data==null)throw new IllegalArgumentException("화면 확인 동의가 없어요.");
            projection=getSystemService(MediaProjectionManager.class).getMediaProjection(intent.getIntExtra("code",0),data);
            projection.registerCallback(new MediaProjection.Callback(){
                @Override public void onStop(){if(!closing){AppState.engine.pause("화면 확인이 끝났어요. 다시 허용해주세요.");stopSelf();}}
                @Override public void onCapturedContentResize(int w,int h){if(w!=width || h!=height)invalidateGeometry();}
                @Override public void onCapturedContentVisibilityChanged(boolean visible){if(!visible && AppState.engine.active())AppState.engine.pause("화면이 가려져서 잠시 멈췄어요.");}
            },handler);
            getSystemService(DisplayManager.class).registerDisplayListener(displayListener,handler);
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,3);reader.setOnImageAvailableListener(this::onImage,handler);
            display=projection.createVirtualDisplay("local-semantic-ocr",width,height,getResources().getDisplayMetrics().densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);
            AppState.capturing=true;info="한국어 온디바이스 인식 준비됨";
        }catch(Exception ex){AppState.engine.fail("화면 확인을 시작하지 못했어요.");AppState.log(ex.toString());stopSelf();}
        return START_NOT_STICKY;
    }
    private final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){public void onDisplayAdded(int id){}public void onDisplayRemoved(int id){if(id==Display.DEFAULT_DISPLAY)invalidateGeometry();}public void onDisplayChanged(int id){if(id==Display.DEFAULT_DISPLAY){Rect b=getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();if(b.width()!=width || b.height()!=height || getSystemService(DisplayManager.class).getDisplay(id).getRotation()!=rotation)invalidateGeometry();}}};
    private void invalidateGeometry(){if(closing)return;AppState.engine.pause("화면 크기가 바뀌었어요. 화면 확인을 다시 허용해주세요.");stopSelf();}
    private Bitmap sample(Image image,Rect region,Rect overlay){
        float scale=Math.min(1f,900f/region.width());int w=Math.max(1,Math.round(region.width()*scale)),h=Math.max(1,Math.round(region.height()*scale));
        int[] pixels=new int[w*h];Image.Plane plane=image.getPlanes()[0];ByteBuffer data=plane.getBuffer();
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int px=Math.min(region.right-1,region.left+(int)((x+.5f)*region.width()/w)),py=Math.min(region.bottom-1,region.top+(int)((y+.5f)*region.height()/h));
            if(overlay!=null && overlay.contains(px,py)){pixels[y*w+x]=Color.WHITE;continue;}
            int offset=py*plane.getRowStride()+px*plane.getPixelStride();pixels[y*w+x]=Color.rgb(data.get(offset)&255,data.get(offset+1)&255,data.get(offset+2)&255);
        }
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    }
    private void onImage(ImageReader source){
        if(closing)return;
        try(Image image=source.acquireLatestImage()){
            if(image==null || busy)return;
            AutomationService service=AppState.accessibility;if(service==null)return;
            AutomationService.OcrRequest request=service.ocrRequest();if(request==null)return;
            long stamp=image.getTimestamp(),now=System.nanoTime();
            if(stamp<=lastStamp || stamp<=request.after() || stamp<=0 || now-stamp>250_000_000L || stamp>now+50_000_000L)return;
            if(request.generation()!=AppState.engine.generation())return;
            lastStamp=stamp;Rect region=new Rect(request.region());if(!region.intersect(0,0,width,height))return;
            Bitmap bitmap=sample(image,region,service.overlayBounds());busy=true;
            info="한국어 인식 중";
            recognizer.process(InputImage.fromBitmap(bitmap,0)).addOnCompleteListener(task->handler.post(()->{
                try{
                    if(closing)return;
                    if(task.isSuccessful()){
                        List<Semantic.Node> nodes=new ArrayList<>();
                        for(Text.TextBlock block:task.getResult().getTextBlocks()){
                            add(nodes,block.getText(),block.getBoundingBox(),region,bitmap);
                            for(Text.Line line:block.getLines())add(nodes,line.getText(),line.getBoundingBox(),region,bitmap);
                            // Preserve a wrapped anchor even if ML Kit puts nearby card text in the same block.
                            var lines=block.getLines();
                            for(int j=0;j+1<lines.size();j++){
                                Rect first=lines.get(j).getBoundingBox(),second=lines.get(j+1).getBoundingBox();
                                if(first!=null && second!=null && second.top-first.bottom<=Math.max(first.height(),second.height())){Rect joined=new Rect(first);joined.union(second);add(nodes,lines.get(j).getText()+" "+lines.get(j+1).getText(),joined,region,bitmap);}
                            }
                        }
                        info="한국어 인식 완료 ("+((System.nanoTime()-stamp)/1_000_000)+"ms)";
                        service.acceptOcr(request,stamp,nodes);
                    }else{info="글자를 읽지 못했어요.";AppState.log(String.valueOf(task.getException()));}
                }finally{bitmap.recycle();busy=false;}
            }));
        }catch(Exception ex){busy=false;info="화면 확인 오류";AppState.log(ex.toString());}
    }
    private void add(List<Semantic.Node> nodes,String text,Rect r,Rect crop,Bitmap image){
        if(r==null || r.isEmpty())return;
        float sx=(float)crop.width()/image.getWidth(),sy=(float)crop.height()/image.getHeight();
        Semantic.Box b=new Semantic.Box(crop.left+Math.round(r.left*sx),crop.top+Math.round(r.top*sy),crop.left+Math.round(r.right*sx),crop.top+Math.round(r.bottom*sy));
        nodes.add(new Semantic.Node(nodes.size(),-1,text,b,false,true,"OCR"));
    }
    @Override public void onDestroy(){closing=true;AppState.capturing=false;info="화면 확인이 꺼져 있어요.";getSystemService(DisplayManager.class).unregisterDisplayListener(displayListener);if(display!=null)display.release();if(reader!=null)reader.close();if(projection!=null)projection.stop();if(recognizer!=null)recognizer.close();if(thread!=null)thread.quitSafely();super.onDestroy();}
}

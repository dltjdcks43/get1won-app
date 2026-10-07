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
import java.nio.ByteBuffer;
import java.util.*;

/** One bounded OCR job, local Korean model, no saved frames and no overlay capture gate. */
public final class CaptureService extends Service {
    private static volatile CaptureService instance;
    public static volatile boolean ready,preparationFailed;
    public static volatile String info="화면 확인이 꺼져 있어요.";
    private HandlerThread thread;private Handler worker;
    private final Handler main=new Handler(Looper.getMainLooper());
    private MediaProjection projection;private ImageReader reader;private VirtualDisplay display;
    private TextRecognizer recognizer;private volatile boolean closing;private boolean busy,openWhenReady;
    private int width,height;private long lastFrameCheck,frameSignature;
    private OcrTicket wanted;
    public static void request(OcrTicket ticket) {
        CaptureService self=instance;
        if(self!=null && !self.closing) self.worker.post(()->{
            self.wanted=ticket;
            // Ask the existing display to produce a fresh frame, even for a static screen.
            // No second MediaProjection session, overlay hide, or repeated timer.
            if(self.display!=null) { self.display.setSurface(null);self.display.setSurface(self.reader.getSurface()); }
        });
    }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onCreate() {
        super.onCreate();AppState.initialize(this);instance=this;ready=false;preparationFailed=false;
        thread=new HandlerThread("LocalKoreanOcr");thread.start();worker=new Handler(thread.getLooper());
        recognizer=TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
        Bitmap warmup=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);warmup.eraseColor(Color.WHITE);busy=true;
        recognizer.process(InputImage.fromBitmap(warmup,0)).addOnCompleteListener(task->worker.post(()->{
            warmup.recycle();busy=false;if(closing)return;ready=task.isSuccessful();
            if(!ready) { preparationFailed=true;stopSelf(); } else { info="한국어 인식 준비됨";open(); }
        }));
        worker.postDelayed(()->{if(!ready && !closing){preparationFailed=true;stopSelf();}},30000);
    }
    private void open() { main.post(()->{if(!closing && openWhenReady && ready && AppState.capturing && AppState.accessibility!=null){openWhenReady=false;AppState.accessibility.openSession();}}); }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent==null || "STOP".equals(intent.getAction())) { stopSelf();return START_NOT_STICKY; }
        openWhenReady=intent.getBooleanExtra("openSession",false);
        if(projection!=null){open();return START_NOT_STICKY;}
        NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("capture","화면 확인",NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,CaptureService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
        startForeground(1,new Notification.Builder(this,"capture").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("1원 받기 — 화면 확인").setContentText("기기 안에서만 확인합니다").setOngoing(true).addAction(new Notification.Action.Builder(null,"중지",stop).build()).build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        try {
            Rect screen=getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();width=screen.width();height=screen.height();
            Intent data=intent.getParcelableExtra("data",Intent.class);if(data==null)throw new IllegalArgumentException("화면 확인 동의 없음");
            projection=getSystemService(MediaProjectionManager.class).getMediaProjection(intent.getIntExtra("code",0),data);
            projection.registerCallback(new MediaProjection.Callback(){
                @Override public void onStop(){if(!closing)stopSelf();}
                @Override public void onCapturedContentResize(int w,int h){if(w!=width || h!=height){main.post(()->AppState.engine.pause("화면 크기가 바뀌었어요. 화면 확인을 다시 허용해주세요."));stopSelf();}}
                @Override public void onCapturedContentVisibilityChanged(boolean visible){if(!visible)main.post(()->AppState.engine.pause("대상 화면이 가려져 멈췄어요."));}
            },worker);
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);reader.setOnImageAvailableListener(this::frame,worker);
            display=projection.createVirtualDisplay("semantic-v2",width,height,getResources().getDisplayMetrics().densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,worker);
            AppState.capturing=true;open();
        } catch(RuntimeException e) { Diagnostics.error(e);stopSelf(); }
        return START_NOT_STICKY;
    }
    private void frame(ImageReader source) {
        if(closing)return;
        try(Image image=source.acquireLatestImage()) {
            if(image==null)return;
            AutomationService service=AppState.accessibility;if(service==null)return;
            long now=SystemClock.uptimeMillis();
            // Detect actual canvas changes cheaply. Ignore both screen-edge overlay lanes.
            if(now-lastFrameCheck>=400 && AppState.engine.active()) {
                lastFrameCheck=now;long signature=signature(image);
                if(frameSignature!=0 && signature!=frameSignature && wanted==null) service.screenChanged();
                frameSignature=signature;
            }
            OcrTicket ticket=wanted;
            if(ticket==null || busy || !ready)return;
            long ageMs=Math.max(0,(System.nanoTime()-image.getTimestamp())/1000000);
            long frameTime=now-ageMs;
            if(frameTime<ticket.requested() || ageMs>500)return;
            wanted=null;
            if(!service.valid(ticket))return;
            Rect region=new Rect(ticket.region().left(),ticket.region().top(),ticket.region().right(),ticket.region().bottom());
            if(!region.intersect(0,0,width,height))return;
            Bitmap bitmap=sample(image,region,ticket.overlay());busy=true;info="한국어 인식 중";
            recognizer.process(InputImage.fromBitmap(bitmap,0)).addOnCompleteListener(task->worker.post(()->{
                try {
                    if(closing)return;
                    if(task.isSuccessful()) {
                        List<Semantic.Node> nodes=new ArrayList<>();
                        for(Text.TextBlock block:task.getResult().getTextBlocks()) for(Text.Line line:block.getLines()) {
                            Rect r=line.getBoundingBox();if(r==null || r.isEmpty())continue;
                            float sx=(float)region.width()/bitmap.getWidth(),sy=(float)region.height()/bitmap.getHeight();
                            Semantic.Box b=new Semantic.Box(region.left+Math.round(r.left*sx),region.top+Math.round(r.top*sy),region.left+Math.round(r.right*sx),region.top+Math.round(r.bottom*sy));
                            nodes.add(new Semantic.Node(nodes.size(),-1,line.getText(),b,false,true,"OCR"));
                            if(nodes.size()>=400)break;
                        }
                        info="한국어 인식 완료";service.acceptOcr(ticket,nodes,frameTime);
                    } else { info="글자를 읽지 못했어요.";Diagnostics.error(task.getException()); }
                } finally { busy=false;bitmap.recycle(); }
            }));
        } catch(RuntimeException e) { busy=false;wanted=null;Diagnostics.error(e);stopSelf(); }
    }
    private long signature(Image image) {
        Image.Plane p=image.getPlanes()[0];ByteBuffer buffer=p.getBuffer();long hash=1;
        // Relative central area: no app-specific location or template comparison.
        for(int y=height/20;y<height-height/20;y+=Math.max(1,height/40)) for(int x=width/5;x<width*4/5;x+=Math.max(1,width/40)) {
            int offset=y*p.getRowStride()+x*p.getPixelStride();hash=31*hash+(buffer.get(offset)&0xF0)+(buffer.get(offset+1)&0xF0)+(buffer.get(offset+2)&0xF0);
        }
        return hash;
    }
    private Bitmap sample(Image image,Rect region,Semantic.Box overlay) {
        float scale=Math.min(1f,900f/region.width());int w=Math.max(1,Math.round(region.width()*scale)),h=Math.max(1,Math.round(region.height()*scale));
        int[] pixels=new int[w*h];Image.Plane p=image.getPlanes()[0];ByteBuffer buffer=p.getBuffer();
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            int px=region.left+Math.min(region.width()-1,(int)((x+.5f)*region.width()/w)),py=region.top+Math.min(region.height()-1,(int)((y+.5f)*region.height()/h));
            if(px>=overlay.left() && px<overlay.right() && py>=overlay.top() && py<overlay.bottom())pixels[y*w+x]=Color.WHITE;
            else { int offset=py*p.getRowStride()+px*p.getPixelStride();pixels[y*w+x]=Color.rgb(buffer.get(offset)&255,buffer.get(offset+1)&255,buffer.get(offset+2)&255); }
        }
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    }
    private void release(Runnable action) { try{action.run();}catch(RuntimeException e){Diagnostics.error(e);} }
    @Override public void onDestroy() {
        closing=true;instance=null;ready=false;AppState.capturing=false;main.removeCallbacksAndMessages(null);
        if(AppState.accessibility!=null)AppState.accessibility.onCaptureStopped();
        worker.post(()->{wanted=null;if(display!=null)release(display::release);if(reader!=null)release(reader::close);if(projection!=null)release(projection::stop);if(recognizer!=null)release(recognizer::close);thread.quitSafely();});
        super.onDestroy();
    }
}

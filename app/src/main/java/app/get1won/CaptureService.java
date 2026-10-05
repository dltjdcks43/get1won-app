package app.get1won;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.view.*;

/** Acquisition and analysis run on one background looper; no frame queue or full Bitmap. */
public final class CaptureService extends Service {
    private HandlerThread thread;private Handler handler;private MediaProjection projection;
    private ImageReader reader;private VirtualDisplay display;private int width,height,rotation;
    private volatile boolean closing;
    private final float[] roiBuffer=new float[Profile.SAMPLE_W*Profile.SAMPLE_H];
    private final float[] screenBuffer=new float[ScreenStability.SIZE];
    private Rect whole;
    public static volatile long lastActionNanos;
    private static volatile Pending pending;
    private record Pending(Rect roi,long after,long generation) {}
    public static void register(Rect roi){synchronized(AppState.engine){pending=new Pending(new Rect(roi),System.nanoTime()+250_000_000L,AppState.engine.generation());}}
    public static void cancelRegistration(){pending=null;}
    public static Rect registrationRect(){Pending p=pending;return p==null?null:p.roi;}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onCreate(){super.onCreate();AppState.initialize(this);thread=new HandlerThread("LatestFrameAnalysis");thread.start();handler=new Handler(thread.getLooper());}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null || "STOP".equals(intent.getAction())){AppState.stop();stopSelf();return START_NOT_STICKY;}
        if(projection!=null)return START_NOT_STICKY;
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("capture","화면 분석",NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,CaptureService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,"capture").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("1원 받기 — 화면 분석").setContentText("화면은 저장하거나 전송하지 않습니다").setOngoing(true).addAction(new Notification.Action.Builder(null,"중지",stop).build()).build();
        startForeground(1,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        try{
            Rect bounds=getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
            width=bounds.width();height=bounds.height();whole=new Rect(0,0,width,height);
            rotation=getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY).getRotation();
            Intent data=intent.getParcelableExtra("data",Intent.class);if(data==null)throw new IllegalArgumentException("화면 공유 동의가 없습니다");
            projection=getSystemService(MediaProjectionManager.class).getMediaProjection(intent.getIntExtra("code",0),data);
            projection.registerCallback(new MediaProjection.Callback(){
                @Override public void onStop(){AppState.engine.pause("화면 공유 종료");stopSelf();}
                @Override public void onCapturedContentResize(int w,int h){if(w!=width || h!=height)invalidateGeometry();}
                @Override public void onCapturedContentVisibilityChanged(boolean visible){if(!visible)AppState.engine.pause("공유 화면이 보이지 않습니다");}
            },handler);
            getSystemService(DisplayManager.class).registerDisplayListener(displayListener,handler);
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,3);
            reader.setOnImageAvailableListener(this::onImage,handler);
            display=projection.createVirtualDisplay("automation-screen",width,height,getResources().getDisplayMetrics().densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);
            AppState.capturing=true;AppState.notice="사용할 앱 화면에서 세 항목을 지정하세요";handler.post(watchdog);
        }catch(Exception ex){AppState.engine.fail("화면 공유 시작 실패: "+ex.getMessage());stopSelf();}
        return START_NOT_STICKY;
    }
    private final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){
        public void onDisplayAdded(int id){}public void onDisplayRemoved(int id){if(id==Display.DEFAULT_DISPLAY)invalidateGeometry();}
        public void onDisplayChanged(int id){if(id==Display.DEFAULT_DISPLAY){Rect b=getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();int r=getSystemService(DisplayManager.class).getDisplay(id).getRotation();if(b.width()!=width || b.height()!=height || r!=rotation)invalidateGeometry();}}
    };
    private void invalidateGeometry(){synchronized(AppState.engine){AppState.settingsChanged();synchronized(AppState.profile){AppState.profile.invalidate();AppState.profile.save(this);}AppState.notice="해상도/회전 변경 — 세 항목을 다시 지정하세요";}stopSelf();}
    private final Runnable watchdog=new Runnable(){public void run(){if(closing)return;AppState.engine.tick(System.nanoTime());handler.postDelayed(this,100);}};
    private void sampleScreen(Image image,Rect roi,Rect overlay){
        Profile.sample(image,whole,screenBuffer,ScreenStability.WIDTH,ScreenStability.HEIGHT);
        for(int y=0;y<ScreenStability.HEIGHT;y++)for(int x=0;x<ScreenStability.WIDTH;x++){
            int px=(int)((x+.5)*width/ScreenStability.WIDTH),py=(int)((y+.5)*height/ScreenStability.HEIGHT);
            // Ignore clocks/navigation, the completion animation, and floating controls.
            if(y<4 || y>=ScreenStability.HEIGHT-4 || (roi!=null && roi.contains(px,py)) || (overlay!=null && overlay.contains(px,py)))screenBuffer[y*ScreenStability.WIDTH+x]=Float.NaN;
        }
    }
    private void onImage(ImageReader source){
        if(closing)return;long generation=AppState.engine.generation();
        try(Image image=source.acquireLatestImage()){
            if(image==null)return;long stamp=image.getTimestamp(),now=System.nanoTime();
            if(stamp<=0 || stamp>now+50_000_000L || now-stamp>250_000_000L || stamp<=lastActionNanos)return;
            AutomationService service=AppState.accessibility;
            if(service==null || (!AppState.engine.active() && pending==null))return;
            // Node queries can require a main-thread reply. Keep them outside both locks.
            boolean target=service.targetVisible();
            boolean waiting=target && pending==null && service.waitTextVisible();
            long checkedAt=System.nanoTime();
            if(checkedAt-stamp>250_000_000L)return;
            synchronized(AppState.engine){
                if(generation!=AppState.engine.generation())return;
                service.frameEvidence(generation,target,waiting,checkedAt);
                Pending task=pending;
                if(task!=null){
                    if(task.generation!=generation){pending=null;return;}
                    if(stamp<=task.after)return;
                    pending=null;
                    if(!target){AppState.notice="지정한 앱 화면에서 영역을 선택하세요";service.selectionSaved(generation);return;}
                    Profile.sample(image,task.roi,roiBuffer,Profile.SAMPLE_W,Profile.SAMPLE_H);
                    if(Matcher.contrast(roiBuffer)<.025){AppState.notice="단색 영역은 사용할 수 없습니다. 완료 글자 전체를 지정하세요";service.selectionSaved(generation);return;}
                    synchronized(AppState.profile){Profile p=AppState.profile;p.setGeometry(width,height,rotation);p.roi=new Rect(task.roi);p.template=roiBuffer.clone();p.save(this);}
                    AppState.notice="완료 표시 영역 저장 완료";service.selectionSaved(generation);return;
                }
                if(!AppState.engine.active())return;
                if(!target){AppState.engine.pause("지정한 앱 화면을 벗어났습니다");return;}
                Profile p=AppState.profile;
                synchronized(p){
                    if(!p.ready() || !p.geometry(width,height,rotation)){AppState.engine.pause("세 항목을 다시 지정하세요");return;}
                    Profile.sample(image,p.roi,roiBuffer,Profile.SAMPLE_W,Profile.SAMPLE_H);
                    boolean completion=Matcher.score(roiBuffer,p.template,Profile.SAMPLE_W)>=.96;
                    sampleScreen(image,p.roi,service.overlayBounds());
                    AppState.engine.frame(new Engine.Frame(generation,stamp,completion,waiting,screenBuffer));
                }
            }
        }catch(Exception ex){if(!closing)AppState.engine.fail("화면 분석 실패: "+ex.getMessage());}
    }
    @Override public void onDestroy(){
        closing=true;AppState.capturing=false;AppState.engine.pause("화면 공유 중지");cancelRegistration();
        getSystemService(DisplayManager.class).unregisterDisplayListener(displayListener);
        if(handler!=null)handler.removeCallbacksAndMessages(null);if(display!=null)display.release();if(reader!=null)reader.close();if(projection!=null)projection.stop();if(thread!=null)thread.quitSafely();super.onDestroy();
    }
}

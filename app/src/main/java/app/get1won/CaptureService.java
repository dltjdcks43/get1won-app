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
import android.view.WindowManager;
import java.util.*;

public final class CaptureService extends Service {
    private HandlerThread thread; private Handler handler;
    private MediaProjection projection; private ImageReader reader; private VirtualDisplay display;
    private int width,height,rotation; private boolean closing;
    public static volatile long lastActionNanos;
    private static volatile Pending pending;
    private record Pending(String key,Rect roi,long after) {}
    public static synchronized void register(String key,Rect roi) {pending=new Pending(key,new Rect(roi),System.nanoTime()+250_000_000L);}
    public static synchronized void cancelRegistration(){pending=null;}
    public static Rect registrationRect(){Pending p=pending;return p==null?null:new Rect(p.roi);}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){super.onCreate();AppState.initialize(this);thread=new HandlerThread("LatestRoiFrame");thread.start();handler=new Handler(thread.getLooper());}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null || "STOP".equals(intent.getAction())){AppState.engine.stop();stopSelf();return START_NOT_STICKY;}
        if(projection!=null)return START_NOT_STICKY;
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("capture","화면 분석",NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,CaptureService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"capture").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("1원 받기 — 화면 분석 중").setContentText("등록된 영역만 기기에서 분석합니다").setOngoing(true).addAction(new Notification.Action.Builder(null,"중지",stop).build()).build();
        startForeground(1,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        try {
            MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);
            Intent data=intent.getParcelableExtra("data",Intent.class);
            if(data==null)throw new IllegalArgumentException("화면 공유 승인이 없습니다");
            projection=manager.getMediaProjection(intent.getIntExtra("code",0),data);
            projection.registerCallback(new MediaProjection.Callback(){
                @Override public void onStop(){AppState.engine.pause("화면 공유가 종료되었습니다");stopSelf();}
                @Override public void onCapturedContentResize(int w,int h){if(w!=width || h!=height){AppState.engine.pause("화면 크기가 변경되었습니다. 전체 화면 공유를 다시 시작하세요");stopSelf();}}
                @Override public void onCapturedContentVisibilityChanged(boolean visible){if(!visible)AppState.engine.pause("공유 화면이 보이지 않습니다");}
            },handler);
            WindowManager wm=getSystemService(WindowManager.class);Rect bounds=wm.getMaximumWindowMetrics().getBounds();width=bounds.width();height=bounds.height();
            rotation=getSystemService(DisplayManager.class).getDisplay(android.view.Display.DEFAULT_DISPLAY).getRotation();
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,3);
            reader.setOnImageAvailableListener(this::onImage,handler);
            display=projection.createVirtualDisplay("get1won-roi",width,height,getResources().getDisplayMetrics().densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);
            AppState.capturing=true;AppState.log("전체 화면 공유 시작 "+width+"×"+height);
            handler.post(watchdog);
        }catch(Exception ex){AppState.engine.fail("화면 공유 실패: "+ex.getMessage());stopSelf();}
        return START_NOT_STICKY;
    }
    private final Runnable watchdog=new Runnable(){@Override public void run(){if(closing)return;AppState.engine.tick(System.nanoTime());handler.postDelayed(this,100);}};
    private void onImage(ImageReader source) {
        if(closing)return;
        long epoch=AppState.engine.epoch();
        try(Image image=source.acquireLatestImage()) {
            if(image==null)return;
            long stamp=image.getTimestamp(),now=System.nanoTime();
            // Surface timestamps are monotonic nanoseconds. Reject queued or incompatible frames.
            if(stamp<=0 || stamp>now+50_000_000L || now-stamp>250_000_000L || stamp<=lastActionNanos)return;
            int currentRotation=getSystemService(DisplayManager.class).getDisplay(android.view.Display.DEFAULT_DISPLAY).getRotation();
            if(currentRotation!=rotation){AppState.engine.pause("회전 감지 — 화면 공유 및 좌표를 다시 설정하세요");stopSelf();return;}
            synchronized(CaptureService.class) {
                if(pending!=null && stamp>pending.after) {
                    Pending task=pending;pending=null;float[] pixels=Profile.sample(image,task.roi);
                    if(Matcher.contrast(pixels)<0.025){AppState.log("등록 실패: 글자/아이콘이 있는 좁은 영역을 선택하세요");return;}
                    synchronized(AppState.profile) {
                        Profile p=AppState.profile;
                        if(p.width!=width || p.height!=height || p.rotation!=rotation){AppState.log("등록 실패: 화면 크기가 좌표 설정과 다릅니다");return;}
                        // Both labels must use the exact same ROI; their shapes must be separable.
                        if(task.key.equals("waiting") && p.templates.containsKey("reward")) {
                            Profile.Template reward=p.templates.get("reward");
                            if(!reward.rect().equals(task.roi) || Matcher.score(pixels,reward.pixels(),Profile.SAMPLE_W)>0.85){AppState.log("등록 실패: 보상과 대기 문구가 구분되지 않습니다");return;}
                        }
                        p.templates.put(task.key,new Profile.Template(task.roi,pixels));
                        if(task.key.equals("reward"))p.templates.remove("waiting");p.save(this);
                    }AppState.log(task.key+" 기준 이미지 등록 완료");return;
                }
            }
            Engine engine=AppState.engine;
            synchronized(engine) {
                if(!engine.active() || epoch!=engine.epoch())return;
                AutomationService service=AppState.accessibility;
                if(service==null){engine.pause("접근성 연결 끊김");return;}
                Profile p=AppState.profile;
                synchronized(p) {
                    if(!p.ready() || p.width!=width || p.height!=height || p.rotation!=rotation){engine.pause("좌표/영역 또는 화면 크기를 확인하세요");return;}
                    if(!service.targetVisible()){engine.pause("대상 앱을 벗어났거나 화면이 잠겼습니다");return;}
                    HashMap<String,Double> scores=new HashMap<>();
                    float[] rewardPixels=Profile.sample(image,p.templates.get("reward").rect());
                    for(var e:p.templates.entrySet()) {
                        float[] current=(e.getKey().equals("reward") || e.getKey().equals("waiting"))?rewardPixels:Profile.sample(image,e.getValue().rect());
                        scores.put(e.getKey(),Matcher.score(current,e.getValue().pixels(),Profile.SAMPLE_W));
                    }
                    double reward=scores.get("reward"),waiting=scores.get("waiting");
                    boolean blocked=waiting>=0.85 || service.waitTextVisible();
                    boolean won=reward>=0.96 && reward-waiting>=0.10;
                    boolean home=scores.get("home")>=0.96, screenB=scores.get("screenB")>=0.96;
                    engine.frame(new Engine.Frame(epoch,stamp,home,blocked||won,won,blocked,screenB));
                }
            }
        }catch(Exception ex){if(!closing)AppState.engine.fail("프레임 분석 오류: "+ex.getMessage());}
    }
    @Override public void onDestroy() {
        closing=true;AppState.capturing=false;AppState.engine.pause("화면 공유 중지");cancelRegistration();
        if(handler!=null)handler.removeCallbacksAndMessages(null);
        if(display!=null)display.release();if(reader!=null)reader.close();if(projection!=null)projection.stop();
        if(thread!=null)thread.quitSafely();super.onDestroy();
    }
}

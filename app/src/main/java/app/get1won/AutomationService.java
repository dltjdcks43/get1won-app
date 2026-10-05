package app.get1won;

import android.accessibilityservice.*;
import android.app.KeyguardManager;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;

public final class AutomationService extends AccessibilityService {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private WindowManager wm;private LinearLayout panel;private TextView status;private Button toggle;
    private View picker;private boolean bottom=true;private WindowManager.LayoutParams params;
    private volatile Rect bounds;
    private volatile long frameGeneration=-1,frameCheckedAt;
    private volatile boolean frameTarget,frameWaiting=true;
    private String pendingSelection;private boolean pendingStart;private long pendingGeneration,pendingUntil;
    @Override protected void onServiceConnected(){AppState.initialize(this);AppState.accessibility=this;wm=getSystemService(WindowManager.class);createPanel();ui.post(refresh);}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){if(pendingSelection!=null || pendingStart)ui.post(this::handlePending);}
    @Override public void onInterrupt(){AppState.engine.pause("접근성 연결 중단");}
    @Override public void onDestroy(){AppState.engine.pause("접근성 연결 종료");AppState.accessibility=null;ui.removeCallbacksAndMessages(null);if(picker!=null)wm.removeView(picker);if(panel!=null)wm.removeView(panel);super.onDestroy();}
    public boolean targetVisible(){
        if(getSystemService(KeyguardManager.class).isKeyguardLocked())return false;
        if(getPackageName().equals(AppState.profile.targetPackage))return TestActivity.visible;
        AccessibilityNodeInfo root=getRootInActiveWindow();String pkg=root==null?"":String.valueOf(root.getPackageName());
        return !pkg.isEmpty() && pkg.equals(AppState.profile.targetPackage) && (!pkg.equals(getPackageName()) || TestActivity.visible);
    }
    public boolean waitTextVisible(){
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return true;
        for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("3초 구경")){
            String text=String.valueOf(node.getText())+" "+String.valueOf(node.getContentDescription());
            if(node.isVisibleToUser() && (text.contains("3초 구경해요") || text.contains("3초 구경해주세요")))return true;
        }return false;
    }
    public boolean execute(int step,long generation){
        // Accessibility node queries may synchronously ask an app's UI thread. Never do
        // that while holding the engine lock: its UI also uses that lock for stop/pause.
        if(generation!=AppState.engine.generation() || !AppState.engine.active() || frameGeneration!=generation || !frameTarget || System.nanoTime()-frameCheckedAt>250_000_000L)return false;
        CaptureService.lastActionNanos=System.nanoTime();
        if(step==2 || step==4){if(frameWaiting)return false;return performGlobalAction(GLOBAL_ACTION_BACK);}
        Point p=step==1?AppState.profile.a:AppState.profile.b;if(p==null)return false;
        Path path=new Path();path.moveTo(p.x,p.y);
        return dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,1)).build(),new GestureResultCallback(){
            @Override public void onCancelled(GestureDescription gesture){synchronized(AppState.engine){if(AppState.engine.generation()==generation && AppState.engine.active())AppState.engine.fail("터치가 취소되었습니다");}}
        },ui);
    }
    void frameEvidence(long generation,boolean target,boolean waiting,long checkedAt){frameTarget=target;frameWaiting=waiting;frameCheckedAt=checkedAt;frameGeneration=generation;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(11);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(5),0,dp(5),0);b.setOnClickListener(v->action.run());return b;}
    @android.annotation.SuppressLint("RtlHardcoded") // Raw display coordinates must always use the physical left edge.
    private void createPanel(){
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(8),dp(6),dp(8),0);panel.setBackgroundColor(0xF2FFFFFF);
        status=new TextView(this);status.setTextColor(Color.BLACK);status.setTextSize(12);panel.addView(status);
        LinearLayout row=new LinearLayout(this);
        toggle=button("일시정지",()->{if(AppState.engine.state==Engine.State.PAUSED){if(AppState.capturing && targetVisible())AppState.engine.resume(System.nanoTime());}else AppState.engine.pause("사용자 일시정지");});
        row.addView(toggle);row.addView(button("중지",AppState::stop));row.addView(button("위/아래",()->{bottom=!bottom;placePanel();}));panel.addView(row);
        params=new WindowManager.LayoutParams(dp(240),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        params.setFitInsetsTypes(0);params.gravity=Gravity.TOP|Gravity.LEFT;panel.setVisibility(View.GONE);wm.addView(panel,params);
    }
    public Rect overlayBounds(){return bounds;}
    private boolean overlaps(Rect box){synchronized(AppState.profile){Profile p=AppState.profile;return (p.a!=null && box.contains(p.a.x,p.a.y)) || (p.b!=null && box.contains(p.b.x,p.b.y)) || (p.roi!=null && Rect.intersects(box,p.roi));}}
    private boolean placePanel(){
        Rect display=wm.getMaximumWindowMetrics().getBounds();int h=Math.max(panel.getHeight(),dp(120)),w=dp(240);
        int top=dp(34),low=display.height()-h-dp(45);
        for(int y:bottom?new int[]{low,top}:new int[]{top,low})for(int x:new int[]{0,Math.max(0,display.width()-w)}){
            Rect box=new Rect(x,y,x+w,y+h);if(!overlaps(box)){params.x=x;params.y=y;wm.updateViewLayout(panel,params);box.inset(-dp(6),-dp(6));bounds=box;return true;}
        }return false;
    }
    void start(){
        synchronized(AppState.engine){
            CaptureService.cancelRegistration();
            if(!AppState.capturing || !AppState.profile.ready() || !targetVisible()){AppState.notice="접근성·화면 공유·세 항목 설정을 확인하세요";return;}
            if(!placePanel()){AppState.notice="플로팅창과 지정 위치가 겹칩니다";return;}
            AppState.engine.start(System.nanoTime(),AppState.profile.repeats);
        }
    }
    void prepareExternal(String key){
        AppState.settingsChanged();pendingSelection=key;pendingStart=key==null;pendingGeneration=AppState.engine.generation();pendingUntil=SystemClock.uptimeMillis()+5000;
    }
    private void handlePending(){synchronized(AppState.engine){
        if(pendingSelection==null && !pendingStart)return;
        if(pendingGeneration!=AppState.engine.generation() || SystemClock.uptimeMillis()>pendingUntil){pendingSelection=null;pendingStart=false;return;}
        AccessibilityNodeInfo root=getRootInActiveWindow();String pkg=root==null?"":String.valueOf(root.getPackageName());
        if(pkg.isEmpty() || pkg.equals(getPackageName()))return;
        if(pendingStart){if(targetVisible()){pendingStart=false;start();}}
        else{String key=pendingSelection;pendingSelection=null;pick(key);}
    }}
    /** Called by the main screen through the built-in test screen, never from floating controls. */
    @android.annotation.SuppressLint("RtlHardcoded") // Picker rawX/rawY and capture pixels share a physical origin.
    void pick(String key){
        AppState.settingsChanged();removePicker();panel.setVisibility(View.GONE);bounds=null;
        final long generation=AppState.engine.generation();
        picker=new View(this){
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);float x,y,ex,ey;boolean cancel;
            @Override protected void onDraw(Canvas c){paint.setColor(0xEEFFFFFF);c.drawRect(0,getHeight()-dp(78),getWidth(),getHeight(),paint);paint.setColor(Color.BLACK);paint.setTextSize(dp(15));c.drawText(key.equals("completion")?"완료 글자 전체를 드래그하세요":"원하는 위치를 한 번 터치하세요",dp(10),getHeight()-dp(47),paint);c.drawText("취소: 아래 안내 영역 터치",dp(10),getHeight()-dp(20),paint);paint.setColor(Color.RED);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));c.drawRect(Math.min(x,ex),Math.min(y,ey),Math.max(x,ex),Math.max(y,ey),paint);paint.setStyle(Paint.Style.FILL);}
            @Override public boolean onTouchEvent(MotionEvent e){
                if(e.getAction()==MotionEvent.ACTION_DOWN){x=ex=e.getRawX();y=ey=e.getRawY();cancel=y>getHeight()-dp(78);}
                if(e.getAction()==MotionEvent.ACTION_MOVE){ex=e.getRawX();ey=e.getRawY();invalidate();}
                if(e.getAction()==MotionEvent.ACTION_UP){performClick();if(cancel){removePicker();selectionSaved(generation);return true;}finishPick(key,(int)x,(int)y,(int)e.getRawX(),(int)e.getRawY(),generation);}
                return true;
            }
            @Override public boolean performClick(){super.performClick();return true;}
        };
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        lp.setFitInsetsTypes(0);lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;lp.gravity=Gravity.TOP|Gravity.LEFT;wm.addView(picker,lp);
    }
    private void finishPick(String key,int x,int y,int ex,int ey,long generation){
        synchronized(AppState.engine){
            if(generation!=AppState.engine.generation()){removePicker();return;}
            removePicker();Rect screen=wm.getMaximumWindowMetrics().getBounds();int rotation=getSystemService(android.hardware.display.DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY).getRotation();
            AccessibilityNodeInfo target=getRootInActiveWindow();String pkg=target==null?"":String.valueOf(target.getPackageName());
            synchronized(AppState.profile){
                Profile p=AppState.profile;p.setGeometry(screen.width(),screen.height(),rotation);if(!pkg.equals(p.targetPackage))p.invalidate();p.targetPackage=pkg;
                if(key.equals("a"))p.a=new Point(x,y);else if(key.equals("b"))p.b=new Point(x,y);else{
                    Rect roi=new Rect(Math.min(x,ex),Math.min(y,ey),Math.max(x,ex),Math.max(y,ey));
                    if(roi.width()<32 || roi.height()<16){AppState.notice="영역이 너무 작습니다";selectionSaved(generation);return;}
                    CaptureService.register(roi);return;
                }p.save(this);
            }AppState.notice="위치 저장 완료";selectionSaved(generation);
        }
    }
    private void removePicker(){if(picker!=null){wm.removeView(picker);picker=null;}}
    public void cancelSelection(){pendingSelection=null;pendingStart=false;long generation=AppState.engine.generation();ui.post(()->{if(generation==AppState.engine.generation())removePicker();});}
    public void selectionSaved(long generation){ui.post(()->{if(generation!=AppState.engine.generation())return;TestActivity a=TestActivity.current();if(a!=null)a.selectionFinished();});}
    private final Runnable refresh=new Runnable(){public void run(){
        if(panel==null)return;
        handlePending();boolean show=picker==null && (AppState.engine.active() || AppState.engine.state==Engine.State.PAUSED);
        panel.setVisibility(show?View.VISIBLE:View.GONE);
        if(show){status.setText(getString(R.string.floating_status,AppState.engine.active()?"● 실행 중":"● 일시정지",Engine.label(AppState.engine.state),AppState.engine.completed));toggle.setText(AppState.engine.state==Engine.State.PAUSED?"재개":"일시정지");if(!placePanel())AppState.engine.pause("플로팅창이 지정 영역과 겹칩니다");}else bounds=null;
        ui.postDelayed(this,100);
    }};
}

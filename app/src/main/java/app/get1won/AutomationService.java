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
    private View picker,pulse;private boolean bottom=true,pulseLight;private WindowManager.LayoutParams params;
    private volatile Rect bounds;
    private volatile long frameGeneration=-1,frameCheckedAt;
    private volatile boolean frameTarget,frameWaiting=true;
    final StartRequest startRequest=new StartRequest();
    private volatile String pendingSelection;private volatile boolean probing;private long pendingGeneration;
    private volatile long revision;private long readRevision=-1,readGeneration=-1,readAction=-1,readAt;
    private boolean textWaiting,textComplete;private Rect textBounds;private volatile Rect completionBounds;
    private int appliedSize=-1;private Button stopButton,moveButton;private boolean manuallyPlaced;
    public record ScreenInfo(boolean target,boolean waiting,boolean complete,Rect textBounds,String pkg) {}
    public boolean preparing(){return startRequest.pending() || probing || pendingSelection!=null;}
    public boolean probing(){return probing;}
    public boolean wantsFrames(){return AppState.engine.active() || preparing() || CaptureService.registrationRect()!=null;}
    public ScreenInfo readScreen(long generation){
        AccessibilityNodeInfo root=getRootInActiveWindow();String pkg=root==null?"":String.valueOf(root.getPackageName());
        boolean target=!getSystemService(KeyguardManager.class).isKeyguardLocked() && pkg.equals(AppState.profile.targetPackage) && (!pkg.equals(getPackageName()) || TestActivity.visible);
        if(!target)return new ScreenInfo(false,false,false,null,pkg);
        long now=System.nanoTime(),rev=revision,action=CaptureService.lastActionNanos;
        if(readRevision!=rev || readGeneration!=generation || readAction!=action || now-readAt>500_000_000L){
            textWaiting=false;textComplete=false;textBounds=null;
            for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText("3초 구경"))if(n.isVisibleToUser()){String t=String.valueOf(n.getText())+String.valueOf(n.getContentDescription());if(t.contains("3초 구경해요") || t.contains("3초 구경해주세요"))textWaiting=true;}
            for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText("1원"))if(n.isVisibleToUser() && (CompletionText.matches(n.getText()) || CompletionText.matches(n.getContentDescription()))){Rect r=new Rect();n.getBoundsInScreen(r);if(!r.isEmpty() && Rect.intersects(r,wm.getMaximumWindowMetrics().getBounds())){textComplete=true;textBounds=r;break;}}
            readRevision=rev;readGeneration=generation;readAction=action;readAt=now;
        }
        if(textBounds!=null)completionBounds=new Rect(textBounds);
        return new ScreenInfo(true,textWaiting,textComplete,textBounds,pkg);
    }
    void observedForeground(long generation,ScreenInfo info){
        if(generation!=pendingGeneration || !info.target)return;
        String key=pendingSelection;if(key==null)return;pendingSelection=null;
        ui.post(()->{if(generation==AppState.engine.generation())pick(key);});
    }
    void probeSucceeded(long generation){
        if(!probing || generation!=pendingGeneration)return;probing=false;
        AppState.profile.autoVerified=true;AppState.profile.save(this);AppState.notice="완료 화면을 자동으로 찾았어요.";selectionSaved(generation);
    }

    @Override protected void onServiceConnected(){AppState.initialize(this);AppState.accessibility=this;wm=getSystemService(WindowManager.class);createPanel();createPulse();ui.post(refresh);ui.post(refreshPulse);}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || String.valueOf(event.getPackageName()).equals(AppState.profile.targetPackage))revision++;}
    @Override public void onInterrupt(){AppState.stop();AppState.notice="접근성 연결이 끊겼어요. 시작하기를 눌러 다시 연결해주세요.";}
    @Override public void onDestroy(){AppState.stop();AppState.accessibility=null;ui.removeCallbacksAndMessages(null);if(picker!=null)wm.removeView(picker);if(panel!=null)wm.removeView(panel);if(pulse!=null)wm.removeView(pulse);super.onDestroy();}
    public boolean targetVisible(){if(getSystemService(KeyguardManager.class).isKeyguardLocked())return false;AccessibilityNodeInfo n=getRootInActiveWindow();return n!=null && String.valueOf(n.getPackageName()).equals(AppState.profile.targetPackage) && (!getPackageName().equals(AppState.profile.targetPackage) || TestActivity.visible);}
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
    private void createPulse(){
        // A static display can stop producing ImageReader buffers. A tiny, non-interactive
        // compositor heartbeat requests genuinely new frames, never reuses an old image.
        pulse=new View(this);pulse.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(2,2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        p.setFitInsetsTypes(0);p.gravity=Gravity.TOP|Gravity.END;pulse.setVisibility(View.GONE);wm.addView(pulse,p);
    }
    private final Runnable refreshPulse=new Runnable(){public void run(){
        boolean needed=AppState.capturing && wantsFrames();
        pulse.setVisibility(needed?View.VISIBLE:View.GONE);
        if(needed){pulseLight=!pulseLight;pulse.setBackgroundColor(pulseLight?0xFF707070:0xFF808080);}
        ui.postDelayed(this,33);
    }};
    private float scale(){return new float[]{1f,1.2f,1.4f,1.65f}[AppState.profile.panelSize];}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setContentDescription(label);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setOnClickListener(v->action.run());return b;}
    @android.annotation.SuppressLint("RtlHardcoded")
    private void createPanel(){
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setBackgroundColor(0xFAFFFFFF);
        status=new TextView(this){@Override public boolean performClick(){super.performClick();return true;}};status.setTextColor(Color.BLACK);status.setContentDescription("조작창 안내. 잡고 움직이면 위치를 옮길 수 있어요.");panel.addView(status);
        status.setOnTouchListener(new View.OnTouchListener(){float x,y;int px,py;public boolean onTouch(View v,MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();px=params.x;py=params.y;return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){Rect d=wm.getMaximumWindowMetrics().getBounds();params.x=Math.max(0,Math.min(d.width()-panel.getWidth(),px+(int)(e.getRawX()-x)));params.y=Math.max(dp(28),Math.min(d.height()-panel.getHeight()-dp(32),py+(int)(e.getRawY()-y)));wm.updateViewLayout(panel,params);bounds=new Rect(params.x,params.y,params.x+panel.getWidth(),params.y+panel.getHeight());return true;}if(e.getAction()==MotionEvent.ACTION_UP){v.performClick();manuallyPlaced=true;placePanel();return true;}return false;}});
        LinearLayout row=new LinearLayout(this);
        toggle=button("잠시 멈춤",()->{if(probing){probing=false;pick("completion");return;}if(startRequest.pending()){AppState.stop();return;}if(AppState.engine.state==Engine.State.PAUSED){if(AppState.capturing)AppState.engine.resume(System.nanoTime());}else AppState.engine.pause("잠시 멈췄어요.");});
        stopButton=button("중지",AppState::stop);row.addView(toggle,new LinearLayout.LayoutParams(0,-2,1));row.addView(stopButton,new LinearLayout.LayoutParams(0,-2,1));panel.addView(row);
        moveButton=button("위/아래",()->{bottom=!bottom;manuallyPlaced=false;placePanel();});row.addView(moveButton,new LinearLayout.LayoutParams(0,-2,1));
        params=new WindowManager.LayoutParams(dp(336),-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        params.setFitInsetsTypes(0);params.gravity=Gravity.TOP|Gravity.LEFT;panel.setVisibility(View.GONE);wm.addView(panel,params);resizePanel();
    }
    void resizePanel(){if(panel==null)return;float s=scale();appliedSize=AppState.profile.panelSize;status.setTextSize(13*s);panel.setPadding(dp(Math.round(8*s)),dp(Math.round(8*s)),dp(Math.round(8*s)),dp(Math.round(6*s)));for(Button b:new Button[]{toggle,stopButton,moveButton}){b.setTextSize(13*s);b.setMinimumHeight(dp(Math.max(48,Math.round(40*s))));b.setMinHeight(dp(Math.max(48,Math.round(40*s))));b.setPadding(dp(Math.round(6*s)),dp(Math.round(4*s)),dp(Math.round(6*s)),dp(Math.round(4*s)));}params.width=Math.min(dp(Math.round(240*s)),wm.getMaximumWindowMetrics().getBounds().width()-dp(16));wm.updateViewLayout(panel,params);manuallyPlaced=false;}
    public Rect overlayBounds(){return bounds;}
    View controls(){return panel;}
    private boolean overlaps(Rect box){synchronized(AppState.profile){Profile p=AppState.profile;return (p.a!=null && box.contains(p.a.x,p.a.y)) || (p.b!=null && box.contains(p.b.x,p.b.y)) || (p.roi!=null && Rect.intersects(box,p.roi)) || (completionBounds!=null && Rect.intersects(box,completionBounds));}}
    private boolean placePanel(){
        Rect d=wm.getMaximumWindowMetrics().getBounds();int w=params.width;panel.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));int h=panel.getMeasuredHeight();int top=dp(28),low=d.height()-h-dp(34);
        if(low<top)return false;
        if(manuallyPlaced){Rect box=new Rect(params.x,params.y,params.x+w,params.y+h);if(!overlaps(box) && box.bottom<=d.height()-dp(28)){box.inset(-dp(4),-dp(4));bounds=box;return true;}manuallyPlaced=false;}
        java.util.ArrayList<Integer> ys=new java.util.ArrayList<>();ys.add(bottom?low:top);ys.add(bottom?top:low);for(int y=low;y>=top;y-=dp(20))ys.add(y);
        for(int y:ys)for(int x:new int[]{dp(8),Math.max(dp(8),d.width()-w-dp(8))}){Rect box=new Rect(x,y,x+w,y+h);box.inset(-dp(4),-dp(4));if(!overlaps(box)){boolean moved=bounds!=null && (params.x!=x || params.y!=y);params.x=x;params.y=y;wm.updateViewLayout(panel,params);bounds=box;if(moved)AppState.notice="조작창 위치를 자동으로 옮겼어요.";return true;}}
        return false;
    }
    void start(){
        AppState.stop();if(!AppState.capturing || !AppState.profile.ready()){AppState.notice="설정을 다시 확인해주세요.";return;}
        startRequest.request(AppState.engine.generation());AppState.notice="사용할 앱을 열어주세요.";
    }
    void prepareExternal(String key){AppState.settingsChanged();pendingGeneration=AppState.engine.generation();probing="probe".equals(key);pendingSelection=probing?null:key;AppState.notice=probing?"‘1원 받았어요’가 보이는 화면을 열어주세요.":"사용할 앱을 열어주세요.";}
    /** Called by the main screen through the built-in test screen, never from floating controls. */
    @android.annotation.SuppressLint("RtlHardcoded") // Picker rawX/rawY and capture pixels share a physical origin.
    void pick(String key){
        AppState.settingsChanged();removePicker();panel.setVisibility(View.GONE);bounds=null;
        final long generation=AppState.engine.generation();
        picker=new View(this){
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);float x,y,ex,ey;boolean cancel;
            @Override protected void onDraw(Canvas c){paint.setColor(0xEEFFFFFF);c.drawRect(0,getHeight()-dp(78),getWidth(),getHeight(),paint);paint.setColor(Color.BLACK);paint.setTextSize(dp(15));c.drawText(key.equals("completion")?"1원 받았어요 부분을 둘러주세요":"정할 위치를 한 번 눌러주세요",dp(10),getHeight()-dp(47),paint);c.drawText("취소: 아래 안내 영역 터치",dp(10),getHeight()-dp(20),paint);paint.setColor(Color.RED);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));c.drawRect(Math.min(x,ex),Math.min(y,ey),Math.max(x,ex),Math.max(y,ey),paint);paint.setStyle(Paint.Style.FILL);}
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
            AccessibilityNodeInfo target=getRootInActiveWindow();String pkg=target==null?AppState.profile.targetPackage:String.valueOf(target.getPackageName());
            synchronized(AppState.profile){
                Profile p=AppState.profile;p.setGeometry(screen.width(),screen.height(),rotation);if(!pkg.equals(p.targetPackage))p.invalidate();p.targetPackage=pkg;
                if(key.equals("a"))p.a=new Point(x,y);else if(key.equals("b"))p.b=new Point(x,y);else{
                    Rect roi=new Rect(Math.min(x,ex),Math.min(y,ey),Math.max(x,ex),Math.max(y,ey));
                    if(roi.width()<32 || roi.height()<16){AppState.notice="영역이 너무 작습니다";selectionSaved(generation);return;}
                    CaptureService.register(roi);return;
                }p.save(this);
            }AppState.notice=key.equals("a")?"첫 번째 위치를 저장했어요.":"포인트 위치를 저장했어요.";selectionSaved(generation);
        }
    }
    private void removePicker(){if(picker!=null){wm.removeView(picker);picker=null;}}
    public void cancelSelection(){pendingSelection=null;probing=false;startRequest.cancel();long generation=AppState.engine.generation();if(Looper.myLooper()==Looper.getMainLooper())removePicker();else ui.post(()->{if(generation==AppState.engine.generation())removePicker();});}
    public void selectionSaved(long generation){ui.post(()->{if(generation!=AppState.engine.generation())return;TestActivity a=TestActivity.current();if(a!=null && a.ownsRegistration())return;if(a!=null)a.selectionFinished();startActivity(new android.content.Intent(this,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP|android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP));});}
    private final Runnable refresh=new Runnable(){public void run(){
        if(panel==null)return;if(appliedSize!=AppState.profile.panelSize)resizePanel();
        boolean show=picker==null && (AppState.engine.active() || AppState.engine.state==Engine.State.PAUSED || preparing());panel.setVisibility(show?View.VISIBLE:View.GONE);
        if(show){String line=probing?"완료 글자가 보이는 화면을 열어주세요.":startRequest.pending()?"사용할 앱을 열어주세요.":Engine.label(AppState.engine.state);String value="1원 받기\n"+line+"\n완료 "+AppState.engine.completed+"회";if(!value.contentEquals(status.getText()))status.setText(value);String label=probing?"완료 화면 알려주기":AppState.engine.state==Engine.State.PAUSED?"다시 계속":"잠시 멈춤";if(!label.contentEquals(toggle.getText()))toggle.setText(label);if(!placePanel()){AppState.engine.pause("조작창을 놓을 곳이 부족해요. 설정에서 크기를 줄여주세요.");startRequest.cancel();}}else bounds=null;
        ui.postDelayed(this,100);
    }};
}

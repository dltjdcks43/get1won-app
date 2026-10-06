package app.get1won;

import android.accessibilityservice.*;
import android.app.KeyguardManager;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Events and OCR decisions share one worker; no platform call holds the engine monitor. */
public final class AutomationService extends AccessibilityService {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private HandlerThread thread; private Handler worker; private WindowManager wm;
    private LinearLayout panel; private TextView status; private Button primary,stop,move;
    private WindowManager.LayoutParams params; private boolean bottom=true; private int appliedSize=-1;
    private volatile Rect panelBounds; private volatile List<Rect> protectedBounds=List.of();
    private final AtomicLong revision=new AtomicLong();
    private volatile Snapshot latest; private volatile boolean diagnostic,diagnosticPending; private volatile String analysis="아직 분석하지 않았어요.";
    private long scannedRevision=-1,scannedAt; private int screenW,screenH,rotation;
    private View pulse; private boolean pulseLight; private Rect waitingRegion; private long waitingCycle=-1; private int ocrAttempts;
    record Snapshot(long generation,long time,long revision,int windowId,String pkg,Semantic.Scene scene,Map<Integer,AccessibilityNodeInfo> handles){}
    record OcrRequest(long generation,long cycle,Engine.State state,long revision,int windowId,String pkg,long after,Rect region){}
    private volatile OcrRequest wanted;
    public String analysis(){return analysis;}
    public Rect overlayBounds(){Rect b=panelBounds;return b==null?null:new Rect(b);}
    View controls(){return panel;}
    public OcrRequest ocrRequest(){return wanted;}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private static Semantic.Box box(Rect r){return new Semantic.Box(r.left,r.top,r.right,r.bottom);}
    private static Rect rect(Semantic.Box b){return new Rect(b.left(),b.top(),b.right(),b.bottom());}
    @Override protected void onServiceConnected(){
        AppState.initialize(this);AppState.accessibility=this;wm=getSystemService(WindowManager.class);
        Rect d=wm.getMaximumWindowMetrics().getBounds();screenW=d.width();screenH=d.height();rotation=getSystemService(android.hardware.display.DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY).getRotation();
        thread=new HandlerThread("SemanticScreen");thread.start();worker=new Handler(thread.getLooper());
        createPanel();createPulse();ui.post(refresh);worker.post(poll);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e){
        if(worker==null)return;
        Snapshot s=latest;
        // Our accessibility overlay is not the target application's content.
        if(s!=null && e.getWindowId()!=s.windowId && String.valueOf(e.getPackageName()).equals(getPackageName()) && e.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return;
        revision.incrementAndGet();worker.removeCallbacks(scan);worker.post(scan);
    }
    @Override public void onInterrupt(){AppState.engine.pause("접근성 연결이 끊겼어요.");}
    @Override public void onDestroy(){AppState.stop();AppState.accessibility=null;wanted=null;ui.removeCallbacksAndMessages(null);if(worker!=null)worker.removeCallbacksAndMessages(null);if(thread!=null)thread.quitSafely();if(panel!=null)wm.removeView(panel);if(pulse!=null)wm.removeView(pulse);super.onDestroy();}
    public void start(){
        // Read at the user's START tap, not a saved registration or an app launch.
        AccessibilityNodeInfo root=getRootInActiveWindow();
        String pkg=root==null?"":String.valueOf(root.getPackageName());
        AppState.stop();wanted=null;diagnostic=false;diagnosticPending=false;
        if(pkg.isEmpty() || getSystemService(KeyguardManager.class).isKeyguardLocked() || (pkg.equals(getPackageName()) && !TestActivity.visible)){
            AppState.engine.pause("시작할 화면을 찾지 못했어요. 포인트 화면을 열고 다시 시작해주세요.");return;
        }
        AppState.engine.start(System.nanoTime(),AppState.profile.repeats,pkg);revision.incrementAndGet();worker.post(scan);
    }
    public void prepareAnalysis(){AppState.stop();wanted=null;diagnostic=true;analysis="원하는 화면을 열고 조작창의 분석을 눌러주세요.";}
    private void analyzeNow(){worker.post(()->{Snapshot s=read();if(s!=null){latest=s;updateAnalysis(s.scene);diagnosticPending=AppState.capturing;requestOcr(s,Semantic.inspect(s.scene));}diagnostic=false;});}
    private Snapshot read(){
        long gen=AppState.engine.generation(),rev=revision.get(),now=System.nanoTime();
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return null;
        List<Semantic.Node> nodes=new ArrayList<>();Map<Integer,AccessibilityNodeInfo> handles=new HashMap<>();
        Rect display=wm.getMaximumWindowMetrics().getBounds();
        if(AppState.engine.state==Engine.State.WAIT_REWARD_COMPLETE){
            // Completion event fast path: only the reward phrases, no full tree walk.
            for(String query:new String[]{"받았어요","구경"})for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText(query))append(n,-1,nodes,handles,display);
        }else walk(root,-1,nodes,handles,display,0);
        return new Snapshot(gen,now,rev,root.getWindowId(),String.valueOf(root.getPackageName()),new Semantic.Scene(nodes,box(display)),handles);
    }
    private void walk(AccessibilityNodeInfo n,int parent,List<Semantic.Node> nodes,Map<Integer,AccessibilityNodeInfo> handles,Rect display,int depth){
        if(n==null || depth>40 || nodes.size()>1500)return;
        int id=append(n,parent,nodes,handles,display);
        for(int k=0;k<n.getChildCount();k++)walk(n.getChild(k),id,nodes,handles,display,depth+1);
    }
    private int append(AccessibilityNodeInfo n,int parent,List<Semantic.Node> nodes,Map<Integer,AccessibilityNodeInfo> handles,Rect display){
        Rect r=new Rect();n.getBoundsInScreen(r);int id=nodes.size();
        boolean visible=n.isVisibleToUser() && !r.isEmpty() && Rect.intersects(r,display);
        String text=n.getText()!=null?n.getText().toString():n.getContentDescription()!=null?n.getContentDescription().toString():"";
        // Keep containers for hierarchy; nonvisible text never counts as evidence.
        nodes.add(new Semantic.Node(id,parent,visible?text:"",box(r),visible && n.isClickable(),visible && n.isEnabled(),"Accessibility"));
        handles.put(id,n);
        return id;
    }
    private final Runnable scan=()->{
        if(!AppState.engine.active())return;
        try{
            Snapshot s=read();if(s==null){wanted=null;AppState.engine.pause("화면을 찾지 못했어요. 처음 화면으로 돌아가 주세요.");return;}
            latest=s;scannedRevision=s.revision;scannedAt=System.nanoTime();
            if(getSystemService(KeyguardManager.class).isKeyguardLocked() || !s.pkg.equals(AppState.engine.targetPackage) || (s.pkg.equals(getPackageName()) && !TestActivity.visible)){
                wanted=null;AppState.engine.pause("다른 앱으로 이동해서 잠시 멈췄어요.");return;
            }
            Semantic.Found found=Semantic.inspect(s.scene);
            if(waitingCycle!=AppState.engine.cycleId){waitingCycle=AppState.engine.cycleId;waitingRegion=null;ocrAttempts=0;}
            if(found.waiting()!=null)waitingRegion=rect(found.waiting().box());
            boolean missing=needsOcr(found);
            if(missing && AppState.capturing)requestOcr(s,found);else wanted=null;
            decide(s,found,!missing || !AppState.capturing);
        }catch(Exception ex){wanted=null;AppState.engine.fail("화면 분석을 멈췄어요. 다시 시작해주세요.");AppState.log(ex.toString());}
    };
    private boolean needsOcr(Semantic.Found f){return switch(AppState.engine.state){
        case WAIT_HOME,WAIT_HOME_AFTER_REWARD,WAIT_HOME_AFTER_POINTS->!f.home();
        case WAIT_REWARD_COMPLETE->f.complete()==null && f.waiting()==null;
        case WAIT_POINTS_HISTORY->!f.history();default->false;
    };}
    private void requestOcr(Snapshot s,Semantic.Found f){
        if(!AppState.capturing)return;
        Rect region=rect(s.scene.screen());
        // A real waiting bubble supplies a dynamic crop. Otherwise analyze downsampled content.
        if(AppState.engine.state==Engine.State.WAIT_REWARD_COMPLETE && waitingRegion!=null && ++ocrAttempts%4!=0){
            region=new Rect(waitingRegion);int pad=Math.max(region.height(),dp(16));region.inset(-pad,-pad);if(!region.intersect(rect(s.scene.screen())))return;
        }
        wanted=new OcrRequest(s.generation,AppState.engine.cycleId,AppState.engine.state,s.revision,s.windowId,s.pkg,s.time,region);
    }
    public void acceptOcr(OcrRequest request,long stamp,List<Semantic.Node> text){
        if(worker==null)return;
        worker.post(()->{
            if(request.generation!=AppState.engine.generation() || request.cycle!=AppState.engine.cycleId || request.state!=AppState.engine.state || request.revision!=revision.get() || System.nanoTime()-stamp>1_500_000_000L){if(diagnosticPending){diagnosticPending=false;wanted=null;analysis+="\n화면이 바뀌었어요. 다시 분석해주세요.";}return;}
            Snapshot fresh=read();if(fresh==null || !fresh.pkg.equals(request.pkg) || fresh.windowId!=request.windowId || fresh.revision!=request.revision)return;
            List<Semantic.Node> combined=new ArrayList<>(fresh.scene.nodes());int id=combined.size();
            for(Semantic.Node n:text)combined.add(new Semantic.Node(id++,-1,n.text(),n.box(),false,true,"OCR"));
            Semantic.Scene scene=new Semantic.Scene(combined,fresh.scene.screen());
            Snapshot merged=new Snapshot(fresh.generation,System.nanoTime(),fresh.revision,fresh.windowId,fresh.pkg,scene,fresh.handles);
            latest=merged;updateAnalysis(scene);diagnosticPending=false;wanted=null;
            if(AppState.engine.active())decide(merged,Semantic.inspect(scene),true);
        });
    }
    private void decide(Snapshot s,Semantic.Found f,boolean definitive){
        if(s.generation!=AppState.engine.generation() || s.revision!=revision.get())return;
        updateAnalysis(s.scene);
        // Protect only currently relevant controls; on HOME the panel is moved clear of the next target.
        List<Rect> protect=new ArrayList<>();
        Semantic.Node next=switch(AppState.engine.state){case WAIT_HOME,WAIT_HOME_AFTER_POINTS->f.ad();case WAIT_HOME_AFTER_REWARD->f.pointsTarget();case WAIT_REWARD_COMPLETE->f.complete()!=null?f.complete():f.waiting();default->null;};
        if(next!=null)protect.add(rect(next.box()));protectedBounds=List.copyOf(protect);
        Rect overlay=panelBounds;
        if(next!=null && overlay!=null && Rect.intersects(overlay,rect(next.box()))){
            ui.post(()->{placePanel();Rect moved=panelBounds;if(moved!=null && !Rect.intersects(moved,rect(next.box())))worker.post(()->{if(System.nanoTime()-s.time<250_000_000L)decide(s,f,definitive);});});return;
        }
        Engine.Effect effect=AppState.engine.frame(new Engine.Frame(s.generation,s.time,s.pkg,f,definitive));
        if(effect==null)return;
        // No delay, posting, or multi-frame confirmation between first completion decision and BACK.
        int result=execute(effect,s);
        if(result<0)AppState.engine.abandon(effect);else AppState.engine.acknowledge(effect,result==1,System.nanoTime());wanted=null;scannedAt=0;
    }
    private int execute(Engine.Effect effect,Snapshot s){
        if(!AppState.engine.valid(effect) || s.revision!=revision.get() || System.nanoTime()-s.time>250_000_000L)return -1;
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null || root.getWindowId()!=s.windowId || !s.pkg.contentEquals(root.getPackageName()) || !AppState.engine.valid(effect))return -1;
        if(effect.step()==2 || effect.step()==4)return performGlobalAction(GLOBAL_ACTION_BACK)?1:0;
        Semantic.Node target=effect.target();if(target==null)return 0;
        AccessibilityNodeInfo handle=s.handles.get(target.id());
        if(handle!=null && handle.isClickable() && handle.performAction(AccessibilityNodeInfo.ACTION_CLICK))return 1;
        if(!AppState.engine.valid(effect) || s.revision!=revision.get())return -1;
        Rect area=rect(target.box());Rect overlay=panelBounds;
        if(area.isEmpty() || (overlay!=null && Rect.intersects(area,overlay)))return 0;
        Path path=new Path();path.moveTo(area.exactCenterX(),area.exactCenterY());
        return dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,1)).build(),new GestureResultCallback(){
            @Override public void onCancelled(GestureDescription g){if(effect.generation()==AppState.engine.generation())AppState.engine.pause("터치가 취소됐어요. 다시 시작해주세요.");}
        },ui)?1:0;
    }
    private void updateAnalysis(Semantic.Scene scene){
        Semantic.Found f=Semantic.inspect(scene);
        StringBuilder b=new StringBuilder("내 포인트: ").append(f.points()!=null?"찾음":"못 찾음").append("\n광고 안내 문구: ").append(f.anchor()!=null?"찾음":"못 찾음").append("\n1원 받았어요: ").append(f.complete()!=null?"찾음":"못 찾음").append("\n포인트 내역 화면: ").append(f.history()?"찾음":"못 찾음");
        for(Semantic.Node n:scene.nodes())if(!n.text().isEmpty() && (n==f.points() || n==f.anchor() || n==f.complete() || n==f.waiting() || n==f.all() || n==f.historyEntry())){
            Semantic.Node parent=scene.byId(n.parent());b.append("\ntext=").append(n.text()).append(" bounds=").append(n.box()).append(" clickable=").append(n.clickable()).append(" parent clickable=").append(parent!=null && parent.clickable()).append(" source=").append(n.source());
        }
        analysis=b.toString();
    }
    private final Runnable poll=new Runnable(){public void run(){
        if(AppState.accessibility!=AutomationService.this)return;
        AppState.engine.tick(System.nanoTime());
        if(AppState.engine.active() && (revision.get()!=scannedRevision || System.nanoTime()-scannedAt>250_000_000L))scan.run();
        if(wanted!=null && wanted.generation()!=AppState.engine.generation()){wanted=null;diagnosticPending=false;}
        if(!AppState.engine.active() && !diagnosticPending)wanted=null;
        worker.postDelayed(this,50);
    }};
    private Button button(String label,Runnable run){Button b=new Button(this);b.setText(label);b.setContentDescription(label);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setOnClickListener(v->run.run());return b;}
    // Native Material theme; AppCompat is only an OCR transitive dependency.
    @android.annotation.SuppressLint({"RtlHardcoded","ClickableViewAccessibility","AppCompatCustomView"})
    private void createPanel(){
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setBackgroundColor(0xFAFFFFFF);
        status=new TextView(this){@Override public boolean performClick(){super.performClick();return true;}};status.setTextColor(Color.BLACK);panel.addView(status);
        status.setOnTouchListener(new View.OnTouchListener(){float y;int previous;public boolean onTouch(View v,MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){y=e.getRawY();previous=params.y;return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){Rect d=wm.getMaximumWindowMetrics().getBounds();params.y=Math.max(dp(28),Math.min(d.height()-panel.getHeight()-dp(32),previous+(int)(e.getRawY()-y)));wm.updateViewLayout(panel,params);panelBounds=new Rect(params.x,params.y,params.x+panel.getWidth(),params.y+panel.getHeight());return true;}if(e.getAction()==MotionEvent.ACTION_UP){v.performClick();return true;}return false;}});
        primary=button("시작",()->{if(diagnostic){analyzeNow();return;}if(AppState.engine.active())AppState.engine.pause("잠시 멈췄어요. 처음 화면에서 다시 시작해주세요.");else start();});panel.addView(primary,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout row=new LinearLayout(this);stop=button("중지",AppState::stop);move=button("위/아래",()->{bottom=!bottom;placePanel();});row.addView(stop,new LinearLayout.LayoutParams(0,-2,1));row.addView(move,new LinearLayout.LayoutParams(0,-2,1));panel.addView(row);
        params=new WindowManager.LayoutParams(dp(336),-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        params.setFitInsetsTypes(0);params.gravity=Gravity.TOP|Gravity.LEFT;wm.addView(panel,params);resizePanel();
    }
    void resizePanel(){if(panel==null)return;float scale=new float[]{1,1.2f,1.4f,1.65f}[AppState.profile.panelSize];appliedSize=AppState.profile.panelSize;panel.setPadding(dp(8*scale),dp(8*scale),dp(8*scale),dp(6*scale));status.setTextSize(13*scale);for(Button b:new Button[]{primary,stop,move}){b.setTextSize(13*scale);b.setMinHeight(dp(Math.max(b==primary?60:48,40*scale)));b.setMinimumHeight(b.getMinHeight());b.setPadding(dp(6*scale),dp(4*scale),dp(6*scale),dp(4*scale));LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)b.getLayoutParams();lp.topMargin=dp(4*scale);lp.setMarginEnd(b==stop?dp(4*scale):0);b.setLayoutParams(lp);}params.width=Math.min(dp(240*scale),wm.getMaximumWindowMetrics().getBounds().width()-dp(16));wm.updateViewLayout(panel,params);placePanel();}
    private boolean overlaps(Rect r){for(Rect p:protectedBounds)if(Rect.intersects(p,r))return true;return false;}
    private void placePanel(){
        if(panel==null)return;Rect d=wm.getMaximumWindowMetrics().getBounds();panel.measure(View.MeasureSpec.makeMeasureSpec(params.width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));int h=panel.getMeasuredHeight();int top=dp(28),low=Math.max(top,d.height()-h-dp(34));
        List<Integer> ys=new ArrayList<>();ys.add(bottom?low:top);ys.add(bottom?top:low);for(int y=top;y<low;y+=dp(16))ys.add(y);
        for(int y:ys){Rect r=new Rect(dp(8),y,dp(8)+params.width,y+h);if(!overlaps(r)){params.x=r.left;params.y=r.top;wm.updateViewLayout(panel,params);panelBounds=r;return;}}
        if(AppState.engine.active())AppState.engine.pause("조작창을 놓을 곳이 부족해요. 설정에서 크기를 줄여주세요.");
    }
    private void createPulse(){pulse=new View(this);pulse.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);WindowManager.LayoutParams p=new WindowManager.LayoutParams(2,2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.END;wm.addView(pulse,p);}
    private final Runnable refresh=new Runnable(){public void run(){
        if(panel==null)return;Rect d=wm.getMaximumWindowMetrics().getBounds();int r=getSystemService(android.hardware.display.DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY).getRotation();if(screenW!=d.width() || screenH!=d.height() || rotation!=r){screenW=d.width();screenH=d.height();rotation=r;wanted=null;AppState.engine.pause("화면 크기가 바뀌었어요. 처음 화면에서 다시 시작해주세요.");resizePanel();}
        if(appliedSize!=AppState.profile.panelSize)resizePanel();
        String message="1원 받기\n● "+(AppState.engine.active()?"실행 중이에요":Engine.label(AppState.engine.state));
        if(AppState.engine.active())message+="\n"+AppState.engine.reason+"\n완료 "+AppState.engine.completed+"회";
        else if(AppState.engine.state!=Engine.State.IDLE)message+="\n"+AppState.engine.reason;
        String label=diagnostic?"분석":AppState.engine.active()?"잠시 멈춤":"시작";
        boolean changed=!message.contentEquals(status.getText()) || !label.contentEquals(primary.getText());
        if(changed){status.setText(message);status.setContentDescription(message+". 잡고 움직이면 위치를 옮길 수 있어요.");primary.setText(label);primary.setContentDescription(label);placePanel();}
        pulse.setVisibility(wanted!=null?View.VISIBLE:View.GONE);if(wanted!=null){pulseLight=!pulseLight;pulse.setBackgroundColor(pulseLight?0xFF707070:0xFF808080);}
        ui.postDelayed(this,100);
    }};
}

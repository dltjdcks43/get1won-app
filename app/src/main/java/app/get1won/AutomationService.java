package app.get1won;

import android.accessibilityservice.*;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.util.*;

/** Android adapter. All controller mutations run on main; OCR returns immutable data with a ticket. */
public final class AutomationService extends AccessibilityService {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Engine engine=AppState.engine;
    private WindowManager wm;
    private LinearLayout panel; private TextView status; private Button start,stop;
    private WindowManager.LayoutParams panelParams;
    private volatile boolean session,closed; private boolean analysisOnly;
    private volatile long revision; private long lastScan,ocrSequence,lastOcrRequest,observedCycle=-1,observedGeneration=-1;
    private String targetPackage="",diagnostic="",lastFingerprint="",ocrKey="";
    private int targetWindow=-1;
    private long stateSince; private Engine.State observedState;
    private boolean timeoutRechecked;
    private volatile OcrTicket pending;
    private record Snapshot(Semantic.Scene scene,Map<Integer,AccessibilityNodeInfo> handles,int window,String pkg,String fingerprint) implements AutoCloseable {
        @SuppressWarnings("deprecation") public void close() { for(AccessibilityNodeInfo n:handles.values()) n.recycle(); }
    }
    @Override protected void onServiceConnected() {
        AppState.initialize(this);AppState.accessibility=this;wm=getSystemService(WindowManager.class);makePanel();
    }
    public boolean sessionActive() { return session; }
    public String analysis() { return diagnostic; }
    public void prepareAnalysis() { analysisOnly=true; }
    public void resizePanel() { main.post(this::updatePanel); }
    public void openSession() { main.post(()->{if(closed)return;session=true;updatePanel();}); }
    public void closeSession() { endSession();stopService(new Intent(this,CaptureService.class)); }
    public void onCaptureStopped() { if(Looper.myLooper()==main.getLooper()) endSession();else main.post(this::endSession); }
    private void endSession() { engine.stop();session=false;pending=null;main.removeCallbacks(scanTask);updatePanel(); }
    private void begin() {
        if(!CaptureService.ready || !AppState.capturing) return;
        targetPackage="";targetWindow=-1;pending=null;ocrKey="";lastFingerprint="";revision++;
        if(analysisOnly) { analysisOnly=false;try(Snapshot s=snapshot()) { if(s!=null) diagnostic=Semantic.inspect(s.scene).summary(); } return; }
        engine.start(AppState.profile.repeats,SystemClock.uptimeMillis());updatePanel();schedule(0);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(closed || !engine.active()) return;
        if(event.getPackageName()!=null && event.getPackageName().toString().equals(getPackageName())) return;
        int type=event.getEventType();
        if(type==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            revision++;pending=null;schedule(Math.max(0,300-(SystemClock.uptimeMillis()-lastScan)));
        }
    }
    /** Called for a changed captured application frame, not for overlay animation. */
    public void screenChanged() { main.post(()->{if(engine.active()) { revision++;pending=null;schedule(Math.max(0,300-(SystemClock.uptimeMillis()-lastScan))); }}); }
    private void schedule(long delay) {
        if(!engine.active() || closed)return;
        // One bounded auxiliary task, cancelled on pause/stop. Events may advance it.
        main.removeCallbacks(scanTask);main.postDelayed(scanTask,delay);
    }
    private final Runnable scanTask=()->{scan();};
    private void syncCycle() {
        if(observedCycle!=engine.cycleId || observedGeneration!=engine.generation) {
            pending=null;ocrKey="";lastFingerprint="";observedCycle=engine.cycleId;observedGeneration=engine.generation;
        }
        if(observedState!=engine.state) { observedState=engine.state;stateSince=SystemClock.uptimeMillis();timeoutRechecked=false;pending=null;ocrKey=""; }
    }
    private void scan() {
        if(closed || !session || !engine.active()) { updatePanel();return; }
        lastScan=SystemClock.uptimeMillis();syncCycle();
        try(Snapshot s=snapshot()) {
            if(s==null) {
                if(engine.expired(lastScan)) engine.pause("대상 앱의 화면을 확인하지 못했어요.");
            } else {
                if(targetPackage.isEmpty()) { targetPackage=s.pkg;targetWindow=s.window; }
                if(!s.pkg.equals(targetPackage)) { engine.pause("다른 앱으로 전환되어 멈췄어요."); }
                else {
                    if(targetWindow!=s.window || !lastFingerprint.equals(s.fingerprint)) { targetWindow=s.window;lastFingerprint=s.fingerprint;revision++;pending=null; }
                    Semantic.Found f=Semantic.inspect(s.scene);
                    long cycle=engine.cycleId;Engine.State state=engine.state;
                    evaluate(s,f);
                    if(engine.active() && cycle==engine.cycleId && state==engine.state && engine.needsOcr(f,SystemClock.uptimeMillis())) requestOcr(s);
                }
            }
        } catch(RuntimeException error) { Diagnostics.error(error);engine.pause("화면 분석 오류로 멈췄어요."); }
        Diagnostics.record(engine);updatePanel();
        if(engine.active()) schedule(400);else pending=null;
    }
    private void requestOcr(Snapshot s) {
        long now=SystemClock.uptimeMillis();
        String key=engine.generation+":"+engine.cycleId+":"+engine.state+":"+revision;
        boolean recheck=now-stateSince>=5000 && !timeoutRechecked;
        if(pending!=null && now-pending.requested()<4000 || now-lastOcrRequest<500) return;
        if(key.equals(ocrKey) && !recheck) return;
        if(recheck) timeoutRechecked=true;
        ocrKey=key;lastOcrRequest=now;
        pending=new OcrTicket(++ocrSequence,engine.generation,engine.cycleId,engine.state,revision,s.window,s.pkg,now,s.fingerprint,s.scene.screen(),panelBox());
        CaptureService.request(pending);
    }
    public void acceptOcr(OcrTicket ticket,List<Semantic.Node> nodes,long frameTime) {
        main.post(()->{
            if(!valid(ticket)) return;
            pending=null;
            try(Snapshot fresh=snapshot()) {
                if(fresh==null || !ticket.matches(fresh.window,fresh.pkg,fresh.fingerprint,frameTime,SystemClock.uptimeMillis())) return;
                Semantic.Scene merged=Semantic.mergeOcr(fresh.scene,nodes);
                Semantic.Found found=Semantic.inspect(merged);
                evaluate(new Snapshot(merged,fresh.handles,fresh.window,fresh.pkg,fresh.fingerprint),found);
            } catch(RuntimeException e) { Diagnostics.error(e);engine.pause("글자 인식 결과 처리 오류로 멈췄어요."); }
            Diagnostics.record(engine);updatePanel();
            if(engine.active()) schedule(400);
        });
    }
    public boolean valid(OcrTicket t) { return !closed && session && engine.active() && t==pending && t.current(engine.generation,engine.cycleId,engine.state,revision); }
    private void evaluate(Snapshot s,Semantic.Found f) {
        diagnostic=f.summary();
        if(!diagnostic.equals(engine.lastSemanticResult)) AppState.log(diagnostic);
        long cycle=engine.cycleId;
        Engine.Decision decision=engine.observe(f,SystemClock.uptimeMillis());
        if(cycle!=engine.cycleId) { syncCycle();return; }
        if(decision==null) return;
        boolean accepted;
        if(decision.action()==Engine.Action.BACK_REWARD || decision.action()==Engine.Action.BACK_HISTORY) {
            // No sleep, delayed callback or extra confirmation after a valid completion observation.
            accepted=performGlobalAction(GLOBAL_ACTION_BACK);
        } else {
            Semantic.Node target=decision.action()==Engine.Action.POINTS && decision.attempt()==1?Semantic.clickParent(s.scene,decision.target()):decision.target();
            if(movePanelAway(target.box())) { pending=null;revision++;schedule(400);return; }
            AccessibilityNodeInfo handle=s.handles.get(target.id());
            if(decision.attempt()==1 && target.clickable() && handle!=null) accepted=handle.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            else {
                Semantic.Box b=target.box();float x=decision.action()==Engine.Action.AD && decision.attempt()==3?b.left()+b.width()*.65f:b.cx();
                Path path=new Path();path.moveTo(x,b.cy());
                accepted=dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,80)).build(),null,null);
            }
        }
        engine.submitted(decision,accepted,SystemClock.uptimeMillis());pending=null;ocrKey="";syncCycle();
    }
    @SuppressWarnings("deprecation") private Snapshot snapshot() {
        AccessibilityWindowInfo chosen=null;
        for(AccessibilityWindowInfo w:getWindows()) {
            if(w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION && (w.isActive() || w.isFocused()) && (chosen==null || w.getLayer()>chosen.getLayer())) chosen=w;
        }
        if(chosen==null)return null;
        AccessibilityNodeInfo root=chosen.getRoot();if(root==null)return null;
        String pkg=String.valueOf(root.getPackageName());
        if(pkg.equals(getPackageName())) { root.recycle();return null; }
        Rect bounds=new Rect();chosen.getBoundsInScreen(bounds);
        List<Semantic.Node> nodes=new ArrayList<>();Map<Integer,AccessibilityNodeInfo> handles=new HashMap<>();
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();queue.add(root);int count=0;
        while(!queue.isEmpty()) {
            AccessibilityNodeInfo n=queue.removeFirst();
            if(++count>600) { n.recycle();while(!queue.isEmpty())queue.removeFirst().recycle();for(AccessibilityNodeInfo h:handles.values())h.recycle();return null; }
            if(!n.isVisibleToUser()) { n.recycle();continue; }
            Rect b=new Rect();n.getBoundsInScreen(b);int id=nodes.size();
            String text=n.getText()==null?"":n.getText().toString();
            if(text.isBlank() && n.getContentDescription()!=null) text=n.getContentDescription().toString();
            nodes.add(new Semantic.Node(id,-1,text,box(b),n.isClickable(),n.isEnabled(),"Accessibility"));handles.put(id,n);
            for(int j=0;j<n.getChildCount();j++) { AccessibilityNodeInfo child=n.getChild(j);if(child!=null)queue.add(child); }
        }
        Semantic.Scene scene=new Semantic.Scene(nodes,box(bounds));
        return new Snapshot(scene,handles,chosen.getId(),pkg,Integer.toHexString(nodes.hashCode()));
    }
    static Semantic.Box box(Rect b) { return new Semantic.Box(b.left,b.top,b.right,b.bottom); }
    public Semantic.Box panelBox() {
        if(panel==null || panel.getVisibility()!=View.VISIBLE) return new Semantic.Box(0,0,0,0);
        int[] location=new int[2];panel.getLocationOnScreen(location);return new Semantic.Box(location[0],location[1],location[0]+panel.getWidth(),location[1]+panel.getHeight());
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private void makePanel() {
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(6),dp(4),dp(6),dp(4));panel.setBackgroundColor(0xEEFFFFFF);
        status=new TextView(this);status.setTextColor(Color.BLACK);panel.addView(status);
        start=new Button(this);start.setText("시작");start.setOnClickListener(v->begin());panel.addView(start);
        stop=new Button(this);stop.setText("중지");stop.setOnClickListener(v->{closeSession();Diagnostics.record(engine);});panel.addView(stop);
        panelParams=new WindowManager.LayoutParams(dp(150),-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        panelParams.gravity=Gravity.TOP|Gravity.RIGHT;panelParams.y=dp(40);panel.setVisibility(View.GONE);wm.addView(panel,panelParams);
    }
    private boolean movePanelAway(Semantic.Box target) {
        if(!panelBox().overlaps(target))return false;
        Rect screen=wm.getCurrentWindowMetrics().getBounds();int height=Math.max(panel.getHeight(),dp(90));
        for(int y=dp(24);y+height<screen.height();y+=height) {
            Semantic.Box candidate=new Semantic.Box(screen.right-panel.getWidth(),y,screen.right,y+height);
            if(!candidate.overlaps(target)) { panelParams.y=y;wm.updateViewLayout(panel,panelParams);return true; }
        }
        engine.pause("조작창과 클릭 위치가 겹쳐서 멈췄어요.");return true;
    }
    private void updatePanel() {
        if(panel==null || closed)return;
        panel.setVisibility(session?View.VISIBLE:View.GONE);boolean running=engine.active();
        int repeats=AppState.profile.repeats;
        String repeatLabel=repeats==0?"계속":repeats+"회";
        status.setText(running?"● 실행 중 · "+repeatLabel:engine.reason);status.setTextSize(running?12:16);
        start.setVisibility(running?View.GONE:View.VISIBLE);stop.setVisibility(running?View.VISIBLE:View.GONE);
        int width=dp(running?108:150+AppState.profile.panelSize*12);
        if(panelParams.width!=width) { panelParams.width=width;wm.updateViewLayout(panel,panelParams); }
    }
    @Override public void onInterrupt() { engine.pause("접근성이 중단되어 멈췄어요.");pending=null;updatePanel(); }
    @Override public void onDestroy() { closed=true;engine.stop();pending=null;main.removeCallbacksAndMessages(null);if(panel!=null && panel.isAttachedToWindow())wm.removeView(panel);AppState.accessibility=null;super.onDestroy(); }
}
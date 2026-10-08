package app.get1won;

import android.accessibilityservice.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.util.*;

/** Main-thread Android adapter: fresh nodes per scan, locked app/window, one ticket per action. */
public final class AutomationService extends AccessibilityService {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Engine engine=AppState.engine;
    private final ObservationGate observations=new ObservationGate(engine,AppState::log);
    private WindowManager wm;
    private LinearLayout panel; private TextView status; private Button start,stop;
    private WindowManager.LayoutParams panelParams;
    private boolean session,closed,analysisOnly;
    private long lastScan,ocrSequence,lastOcrRequest;
    private String diagnostic="";
    private OcrTicket pending;
    private WindowOcr ocr;
    private final Set<String> pointsIds=new HashSet<>();
    private record Snapshot(Semantic.Scene scene,Map<Integer,AccessibilityNodeInfo> handles,int window,String pkg,int count,boolean partial) implements AutoCloseable {
        @SuppressWarnings("deprecation") public void close() { for(AccessibilityNodeInfo n:handles.values())n.recycle(); }
    }
    @Override protected void onServiceConnected() {
        AppState.initialize(this);AppState.accessibility=this;wm=getSystemService(WindowManager.class);makePanel();
    }
    public boolean sessionActive() { return session; }
    public String analysis() { return diagnostic; }
    public void prepareAnalysis() { analysisOnly=true; }
    public void resizePanel() { main.post(this::updatePanel); }
    public void openSession() {
        if(closed)return;
        if(ocr!=null && WindowOcr.preparationFailed){ocr.close();ocr=null;}
        if(ocr==null){ocr=new WindowOcr(this);}
        session=true;AppState.capturing=true;updatePanel();
    }
    public void closeSession() {
        engine.stop();session=false;pending=null;main.removeCallbacks(scanTask);AppState.capturing=false;
        if(ocr!=null){ocr.close();ocr=null;}updatePanel();
    }
    private void begin() {
        if(!session || closed)return;
        pending=null;pointsIds.clear();
        try(Snapshot s=snapshot()) {
            if(s==null || s.pkg.equals(getPackageName())) { engine.pause("대상 앱 화면을 열어주세요.");updatePanel();return; }
            if(analysisOnly){analysisOnly=false;diagnostic=Semantic.diagnostics(s.scene,Semantic.inspect(s.scene));return;}
            Diagnostics.newSession();engine.start(AppState.profile.repeats,SystemClock.uptimeMillis());observations.bind(s.pkg,s.window);
            AppState.log("시작 target package="+s.pkg+" window="+s.window);
        } catch(RuntimeException e){Diagnostics.error(e);engine.pause("시작 화면을 읽지 못했어요.");}
        updatePanel();schedule(0);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(closed || !engine.active())return;
        if(event.getPackageName()!=null && event.getPackageName().toString().equals(getPackageName()))return;
        // Events trigger observation, but do not invalidate an otherwise fresh OCR ticket.
        schedule(Math.max(0,100-(SystemClock.uptimeMillis()-lastScan)));
    }
    private void schedule(long delay) {
        if(!engine.active() || closed)return;
        main.removeCallbacks(scanTask);main.postDelayed(scanTask,delay);
    }
    private final Runnable scanTask=this::scan;
    private boolean guard(Snapshot s) {
        long generation=engine.generation;
        boolean valid=observations.window(s.pkg,s.window);
        if(generation!=engine.generation){pending=null;pointsIds.clear();}
        return valid;
    }
    private void scan() {
        if(closed || !session || !engine.active()){updatePanel();return;}
        lastScan=SystemClock.uptimeMillis();
        if(pending!=null && !valid(pending))pending=null;
        if(pending!=null && lastScan-pending.requested()>=8000){pending=null;engine.pause("screenshot/OCR 응답 시간 초과로 멈췄어요.");}
        try(Snapshot s=snapshot()) {
            if(s==null) { if(engine.expired(lastScan))engine.pause("대상 window가 가려졌거나 화면을 읽지 못했어요."); }
            else if(engine.active() && guard(s)) {
                Semantic.Found f=Semantic.inspect(s.scene);
                long epoch=engine.actionEpoch,cycle=engine.cycleId;Engine.State state=engine.state;
                if(engine.historyFallbackBeforeRetry(f,lastScan) && !engine.expired(lastScan))requestOcr(s);
                else evaluate(s,f);
                if(engine.active() && epoch==engine.actionEpoch && cycle==engine.cycleId && state==engine.state && engine.needsOcr(f,lastScan))requestOcr(s);
            }
        } catch(RuntimeException e){Diagnostics.error(e);engine.pause("화면 분석 오류로 멈췄어요.");}
        Diagnostics.record(engine);updatePanel();
        if(engine.active())schedule(250);else pending=null;
    }
    private void requestOcr(Snapshot s) {
        long now=SystemClock.uptimeMillis();
        if(pending!=null || now-lastOcrRequest<500 || ocr==null || ocr.busy())return;
        if(WindowOcr.preparationFailed){engine.pause("한국어 글자 인식을 준비하지 못했어요.");return;}
        if(!WindowOcr.ready)return;
        OcrTicket ticket=new OcrTicket(++ocrSequence,engine.generation,engine.cycleId,engine.state,engine.actionEpoch,s.window,s.pkg,now,"",s.scene.screen(),panelBox());
        pending=ticket;lastOcrRequest=now;Diagnostics.ocr("요청 ticket="+ticket.id()+" cycle="+ticket.cycle());
        if(!ocr.request(ticket,new WindowOcr.Result(){
            public void success(OcrTicket t,List<Semantic.Node> nodes,long timestamp){acceptOcr(t,nodes,timestamp);}
            public void failure(OcrTicket t,String stage){if(valid(t)){pending=null;Diagnostics.ocr("실패 ticket="+t.id()+" "+stage);observations.failure(t,stage);schedule(250);}}
        }))pending=null;
    }
    public void acceptOcr(OcrTicket ticket,List<Semantic.Node> nodes,long frameTime) {
        if(!valid(ticket))return;
        pending=null;
        try(Snapshot fresh=snapshot()) {
            if(fresh==null || !guard(fresh))return;
            Semantic.Found found=observations.merge(ticket,fresh.scene,nodes,fresh.pkg,fresh.window,frameTime,SystemClock.uptimeMillis());
            if(found==null)return;
            Semantic.Scene merged=Semantic.mergeOcr(fresh.scene,nodes);
            Diagnostics.ocr("성공 ticket="+ticket.id()+" cycle="+ticket.cycle()+" nodes="+nodes.size()+" anchor="+(found.anchor()!=null));
            AppState.log("OCR 성공 nodes="+nodes.size()+" "+found.summary());
            evaluate(new Snapshot(merged,fresh.handles,fresh.window,fresh.pkg,fresh.count,fresh.partial),found);
        } catch(RuntimeException e){Diagnostics.ocr("처리 오류 ticket="+ticket.id());Diagnostics.error(new IllegalStateException("AutomationService.acceptOcr: merge/inspect/evaluate ticket="+ticket.id()+" state="+engine.state,e));engine.pause("글자 인식 결과 처리 오류로 멈췄어요.");}
        Diagnostics.record(engine);updatePanel();if(engine.active())schedule(250);
    }
    public boolean valid(OcrTicket t) {
        return !closed && session && engine.active() && t==pending && t.current(engine.generation,engine.cycleId,engine.state,engine.actionEpoch);
    }
    private void evaluate(Snapshot s,Semantic.Found f) {
        Diagnostics.points(engine.state==Engine.State.HOME?Semantic.pointsEvidence(s.scene).summary():"");
        Diagnostics.anchor(engine.state==Engine.State.HOME && f.anchor()==null?Semantic.anchorEvidence(s.scene).summary():"");
        diagnostic="package="+s.pkg+" window="+s.window+" nodes="+s.count+" partial="+s.partial+"\n"+(engine.state==Engine.State.HOME?Semantic.diagnostics(s.scene,f):f.summary());
        if(!f.summary().equals(engine.lastSemanticResult))AppState.log(diagnostic);
        Engine.Decision decision=engine.observe(f,SystemClock.uptimeMillis());
        if(decision==null)return;
        if(decision.action()==Engine.Action.AD)AppState.log("AD decision observation: "+f.summary());
        // Recheck the window immediately before dispatch. Handles are scoped to this snapshot only.
        try(WindowIdentity live=window()) {
            if(live==null){engine.defer(decision);return;}
            if(!s.pkg.equals(live.pkg) || s.window!=live.id){
                engine.defer(decision);observations.window(live.pkg,live.id);pending=null;pointsIds.clear();schedule(0);return;
            }
        }
        pending=null;
        if(decision.action()==Engine.Action.BACK_REWARD || decision.action()==Engine.Action.BACK_HISTORY) {
            boolean accepted=performGlobalAction(GLOBAL_ACTION_BACK);
            AppState.log("BACK "+(accepted?"요청 성공 / 실제 복귀 대기":"요청 실패"));
            engine.submitted(decision,accepted,SystemClock.uptimeMillis());return;
        }
        Semantic.Node node=decision.action()==Engine.Action.AD?decision.target():Semantic.clickParent(s.scene,decision.target());
        if(node==null || node.box().width()==0 || node.box().height()==0 || !s.scene.screen().contains(node.box())) {
            engine.pause("현재 클릭 bounds를 확인하지 못했어요.");return;
        }
        AppState.log(decision.action()+" decision attempt="+decision.attempt()+" window="+s.window+" "+Semantic.targetDiagnostics(s.scene,decision.target(),node));
        if(movePanelAway(node.box())){AppState.log("overlay moved: action deferred / fresh target 재탐색");engine.defer(decision);return;}
        AccessibilityNodeInfo handle=s.handles.get(node.id());
        if(decision.attempt()==1 && node.clickable() && handle!=null) {
            boolean accepted=handle.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            AppState.log("ACTION_CLICK "+(accepted?"accepted":"rejected")+" target="+decision.action()+" source="+node.source()+" bounds="+node.box());
            if(accepted){engine.submitted(decision,true,SystemClock.uptimeMillis());return;}
            // Do not reuse this handle/bounds for fallback. Next observation resolves the target again.
            engine.submitted(decision,false,SystemClock.uptimeMillis());return;
        }
        Semantic.Box b=node.box();Path path=new Path();path.moveTo(b.cx(),b.cy());
        boolean accepted=dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,80)).build(),new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription g){result(true);}
            @Override public void onCancelled(GestureDescription g){result(false);}
            private void result(boolean completed){
                if(!engine.current(decision) || closed || !session)return;
                try(WindowIdentity live=window()) {
                    if(live!=null && !observations.window(live.pkg,live.id)){updatePanel();return;}
                    if(!engine.current(decision)){pending=null;pointsIds.clear();schedule(0);return;}
                }
                engine.gestureResult(decision,completed,SystemClock.uptimeMillis());schedule(0);
            }
        },main);
        AppState.log("gesture target="+decision.action()+" source="+node.source()+" bounds="+b);
        engine.gestureSubmitted(decision,accepted,SystemClock.uptimeMillis());
    }
    private record WindowIdentity(int id,String pkg,Semantic.Box bounds,AccessibilityNodeInfo root) implements AutoCloseable {
        @SuppressWarnings("deprecation") public void close(){root.recycle();}
    }
    @SuppressWarnings("deprecation") private WindowIdentity window() {
        List<AccessibilityWindowInfo> windows=getWindows();AccessibilityWindowInfo chosen=null;
        try {
            for(AccessibilityWindowInfo w:windows) {
                if(w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION && (w.isActive() || w.isFocused()) && (chosen==null || w.getLayer()>chosen.getLayer()))chosen=w;
            }
            if(chosen==null)return null;
            for(AccessibilityWindowInfo w:windows) {
                if(w.getType()!=AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY && w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION && w.isActive() && w.getLayer()>chosen.getLayer())return null;
            }
            AccessibilityNodeInfo root=chosen.getRoot();if(root==null)return null;
            Rect bounds=new Rect();chosen.getBoundsInScreen(bounds);
            return new WindowIdentity(chosen.getId(),String.valueOf(root.getPackageName()),box(bounds),root);
        } finally {for(AccessibilityWindowInfo w:windows)w.recycle();}
    }
    @SuppressWarnings("deprecation") private Snapshot snapshot() {
        try(WindowIdentity w=window()) {
            if(w==null)return null;
            List<Semantic.Node> nodes=new ArrayList<>();Map<Integer,AccessibilityNodeInfo> handles=new HashMap<>();
            try {
                // Platform text lookup prioritizes points outside the bounded general traversal.
                int id=collectMatches(w.root.findAccessibilityNodeInfosByText("포인트"),100000,100100,w.bounds,nodes,handles);
                for(String resourceId:new ArrayList<>(pointsIds))
                    id=collectMatches(w.root.findAccessibilityNodeInfosByViewId(resourceId),id,100200,w.bounds,nodes,handles);
                TreeWalk.Result result=TreeWalk.collect(AccessibilityNodeInfo.obtain(w.root),new TreeWalk.Access<AccessibilityNodeInfo>(){
                    public int children(AccessibilityNodeInfo n){return n.getChildCount();}
                    public AccessibilityNodeInfo child(AccessibilityNodeInfo n,int i){return n.getChild(i);}
                    public void visit(AccessibilityNodeInfo n,int id,int parent){addNode(n,id,parent,w.bounds,nodes,handles);}
                    public void release(AccessibilityNodeInfo n){n.recycle();}
                },2000,SystemClock::uptimeMillis,80);
                return new Snapshot(new Semantic.Scene(nodes,w.bounds),handles,w.id,w.pkg,result.visited(),result.partial());
            } catch(RuntimeException e){for(AccessibilityNodeInfo n:handles.values())n.recycle();throw e;}
        }
    }
    @SuppressWarnings("deprecation") private int collectMatches(List<AccessibilityNodeInfo> matches,int id,int limit,Semantic.Box bounds,List<Semantic.Node> nodes,Map<Integer,AccessibilityNodeInfo> handles) {
        try {for(AccessibilityNodeInfo n:matches)if(id<limit)addNode(n,id++,-1,bounds,nodes,handles);return id;}
        finally {for(AccessibilityNodeInfo n:matches)n.recycle();}
    }
    @SuppressWarnings("deprecation") private void addNode(AccessibilityNodeInfo n,int id,int parent,Semantic.Box screen,List<Semantic.Node> nodes,Map<Integer,AccessibilityNodeInfo> handles) {
        if(!n.isVisibleToUser() || n.isPassword())return;
        Rect rect=new Rect();n.getBoundsInScreen(rect);Semantic.Box bounds=box(rect);
        if(bounds.width()==0 || bounds.height()==0 || !bounds.overlaps(screen))return;
        String text=n.getText()==null?"":n.getText().toString(),desc=n.getContentDescription()==null?"":n.getContentDescription().toString();
        String resourceId=n.getViewIdResourceName()==null?"":n.getViewIdResourceName();
        if(Semantic.normalize(text).equals("내포인트") || Semantic.normalize(desc).equals("내포인트")) {
            if(!resourceId.isBlank() && pointsIds.size()<8)pointsIds.add(resourceId);
        } // A known ID prioritizes lookup, but never invents a missing semantic label.
        nodes.add(new Semantic.Node(id,parent,text,bounds,n.isClickable(),n.isEnabled(),"Accessibility",desc,resourceId));
        handles.put(id,AccessibilityNodeInfo.obtain(n));
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
    @Override public void onInterrupt(){engine.pause("접근성이 중단되어 멈췄어요.");pending=null;updatePanel();}
    @Override public void onDestroy(){closeSession();closed=true;main.removeCallbacksAndMessages(null);if(panel!=null && panel.isAttachedToWindow())wm.removeView(panel);AppState.accessibility=null;super.onDestroy();}
}

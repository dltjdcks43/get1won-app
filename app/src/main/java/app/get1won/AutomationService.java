package app.get1won;

import android.accessibilityservice.*;
import android.app.KeyguardManager;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;

public final class AutomationService extends AccessibilityService {
    private final Handler main=new Handler(Looper.getMainLooper());
    private WindowManager wm;private LinearLayout panel;private TextView status;private View picker;
    private WindowManager.LayoutParams panelParams;private boolean bottom=true;private String selectKey;
    @Override protected void onServiceConnected(){AppState.initialize(this);AppState.accessibility=this;wm=getSystemService(WindowManager.class);showPanel();main.post(refresh);}
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt(){AppState.engine.pause("접근성 서비스 중단");}
    @Override public void onDestroy(){AppState.engine.pause("접근성 서비스 종료");AppState.accessibility=null;main.removeCallbacksAndMessages(null);if(panel!=null)wm.removeView(panel);if(picker!=null)wm.removeView(picker);super.onDestroy();}
    public boolean targetVisible() {
        if(getSystemService(KeyguardManager.class).isKeyguardLocked())return false;
        AccessibilityNodeInfo root=getRootInActiveWindow();
        return root!=null && root.getPackageName()!=null && AppState.profile.targetPackage.contentEquals(root.getPackageName());
    }
    public boolean waitTextVisible(){return containsWaiting(getRootInActiveWindow(),0);}
    private boolean containsWaiting(AccessibilityNodeInfo node,int depth) {
        if(node==null || depth>25)return false;
        String text=String.valueOf(node.getText())+" "+String.valueOf(node.getContentDescription());
        if(node.isVisibleToUser() && (text.contains("3초 구경해요") || text.contains("3초 구경해주세요")))return true;
        for(int i=0;i<node.getChildCount();i++)if(containsWaiting(node.getChild(i),depth+1))return true;return false;
    }
    public boolean execute(int step,long epoch) {
        if(epoch!=AppState.engine.epoch() || !targetVisible())return false;
        CaptureService.lastActionNanos=System.nanoTime();
        if(step==2 || step==4) {
            if(waitTextVisible())return false;
            return performGlobalAction(GLOBAL_ACTION_BACK);
        }
        Point p=step==1?AppState.profile.a:AppState.profile.b;if(p==null)return false;
        Path path=new Path();path.moveTo(p.x,p.y);
        return dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,1)).build(),new GestureResultCallback(){
            @Override public void onCancelled(GestureDescription gesture){synchronized(AppState.engine){if(AppState.engine.epoch()==epoch && AppState.engine.active())AppState.engine.fail("터치가 취소되었습니다");}}
        },main);
    }
    private Button button(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setTextSize(11);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(6,2,6,2);b.setOnClickListener(v->action.run());return b;}
    private void showPanel() {
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(6,6,6,6);panel.setBackgroundColor(0xEEFFFFFF);
        status=new TextView(this);status.setTextColor(Color.BLACK);status.setTextSize(11);panel.addView(status);
        LinearLayout row=new LinearLayout(this);row.addView(button("시작",this::start));row.addView(button("일시정지",()->AppState.engine.pause("사용자 일시정지")));row.addView(button("재개",()->{if(AppState.capturing)AppState.engine.resume(System.nanoTime());}));panel.addView(row);
        LinearLayout row2=new LinearLayout(this);row2.addView(button("중지",()->{AppState.engine.stop();CaptureService.cancelRegistration();selectKey=null;removePicker();}));row2.addView(button("위/아래",()->{bottom=!bottom;placePanel();}));row2.addView(button("영역/위치",this::selectionMenu));panel.addView(row2);
        panelParams=new WindowManager.LayoutParams(dp(260),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        panelParams.setFitInsetsTypes(0);panelParams.gravity=Gravity.TOP|Gravity.LEFT;wm.addView(panel,panelParams);panel.post(this::placePanel);
    }
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    public void prepareSelection(String key){AppState.engine.pause("좌표/영역 설정");selectKey=key;AppState.log("대상 화면으로 이동 후 플로팅창의 영역/위치 → 지정 시작을 누르세요");}
    private void selectionMenu() {
        String[] labels={"지정 시작 (선택한 항목)","1번 위치 지정","3번 위치 지정","1원 받았어요 영역 지정","3초 대기 기준 저장 (같은 영역)","HOME 기준 영역 지정","B 화면 기준 영역 지정"};
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("대상 화면을 먼저 열어주세요").setItems(labels,(d,i)->{
            if(i>0)selectKey=new String[]{"a","b","reward","waiting","home","screenB"}[i-1];
            if(selectKey==null){AppState.log("설정 항목을 먼저 선택하세요");return;}
            AppState.engine.pause("설정 중");CaptureService.cancelRegistration();
            if(selectKey.equals("waiting")){
                synchronized(AppState.profile){Profile.Template t=AppState.profile.templates.get("reward");if(t==null){AppState.log("보상 영역을 먼저 지정하세요");return;}CaptureService.register("waiting",t.rect());}return;
            }
            openPicker(selectKey);
        }).setNegativeButton("취소",null).create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);dialog.show();
    }
    private void openPicker(String key) {
        if(!key.equals("a") && !key.equals("b") && !AppState.capturing){AppState.log("화면 공유를 먼저 시작하세요");return;}
        removePicker();panel.setVisibility(View.GONE);
        picker=new View(this) {
            float x,y,endX,endY;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas){paint.setColor(0xCCFFFFFF);canvas.drawRect(0,getHeight()-dp(100),getWidth(),getHeight(),paint);paint.setColor(Color.BLACK);paint.setTextSize(dp(16));canvas.drawText(key+": 위치는 탭 / 영역은 드래그",20,getHeight()-dp(60),paint);canvas.drawText("취소: 아래 안내 영역 터치",20,getHeight()-dp(30),paint);paint.setColor(Color.RED);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);canvas.drawRect(Math.min(x,endX),Math.min(y,endY),Math.max(x,endX),Math.max(y,endY),paint);paint.setStyle(Paint.Style.FILL);}
            @Override public boolean onTouchEvent(MotionEvent e) {
                if(e.getAction()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();endX=x;endY=y;if(y>getHeight()-dp(100)){removePicker();return true;}}
                if(e.getAction()==MotionEvent.ACTION_MOVE){endX=e.getRawX();endY=e.getRawY();invalidate();}
                if(e.getAction()==MotionEvent.ACTION_UP){performClick();endX=e.getRawX();endY=e.getRawY();finishPick(key,(int)x,(int)y,(int)endX,(int)endY);}
                return true;
            }
            @Override public boolean performClick(){super.performClick();return true;}
        };
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;lp.setFitInsetsTypes(0);lp.gravity=Gravity.TOP|Gravity.LEFT;wm.addView(picker,lp);
    }
    private void finishPick(String key,int x,int y,int ex,int ey) {
        AccessibilityNodeInfo root=getRootInActiveWindow();String pkg=root==null || root.getPackageName()==null?"":root.getPackageName().toString();
        removePicker();Profile p=AppState.profile;Rect bounds=wm.getMaximumWindowMetrics().getBounds();
        synchronized(p) {
            int rotation=getSystemService(android.hardware.display.DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY).getRotation();
            if(p.width!=bounds.width() || p.height!=bounds.height() || p.rotation!=rotation || (!p.targetPackage.isEmpty()&&!pkg.equals(p.targetPackage))) {p.a=null;p.b=null;p.templates.clear();}
            p.width=bounds.width();p.height=bounds.height();p.rotation=rotation;p.targetPackage=pkg;
            if(key.equals("a"))p.a=new Point(x,y);else if(key.equals("b"))p.b=new Point(x,y);else {
                Rect r=new Rect(Math.min(x,ex),Math.min(y,ey),Math.max(x,ex),Math.max(y,ey));
                if(r.width()<16 || r.height()<12){AppState.log("영역이 너무 작습니다");return;}
                CaptureService.register(key,r);
            }p.save(this);
        }selectKey=null;AppState.log(key+" 지정: "+x+","+y+" → "+ex+","+ey);placePanel();
    }
    private void removePicker(){if(picker!=null){wm.removeView(picker);picker=null;}if(panel!=null)panel.setVisibility(View.VISIBLE);}
    private boolean overlaps(Rect box) {
        Rect registration=CaptureService.registrationRect();if(registration!=null && Rect.intersects(box,registration))return true;
        synchronized(AppState.profile){Profile p=AppState.profile;
            if((p.a!=null && box.contains(p.a.x,p.a.y)) || (p.b!=null && box.contains(p.b.x,p.b.y)))return true;
            for(Profile.Template t:p.templates.values())if(Rect.intersects(box,t.rect()))return true;return false;}
    }
    private boolean placePanel() {
        if(panel==null)return false;Rect bounds=wm.getMaximumWindowMetrics().getBounds();int height=Math.max(panel.getHeight(),dp(170));int width=dp(260);
        int[] ys=bottom?new int[]{bounds.height()-height-dp(50),dp(40),bounds.height()/2}:new int[]{dp(40),bounds.height()-height-dp(50),bounds.height()/2};
        for(int y:ys)for(int x:new int[]{0,Math.max(0,bounds.width()-width)}){Rect box=new Rect(x,y,x+width,y+height);if(!overlaps(box)){panelParams.x=x;panelParams.y=y;wm.updateViewLayout(panel,panelParams);return true;}}
        return false;
    }
    void start() {
        CaptureService.cancelRegistration();
        if(!AppState.capturing || !AppState.profile.ready()){AppState.log("시작 불가: 접근성·화면 공유·A/B·보상/대기/HOME/B화면 영역을 모두 설정하세요");return;}
        if(!placePanel()){AppState.log("시작 불가: 플로팅창과 좌표/영역이 겹칩니다");return;}
        AppState.engine.start(System.nanoTime(),AppState.profile.repeats,AppState.profile.timeoutSeconds);
    }
    private final Runnable refresh=new Runnable(){@Override public void run(){if(panel==null)return; synchronized(AppState.engine){status.setText("1원 받기  "+(AppState.engine.active()?"● 실행 중":"● 대기")+"\n"+AppState.engine.state+"\n반복: "+AppState.engine.completed+"회\n"+(selectKey==null?AppState.engine.reason:"지정 대기: "+selectKey));}if(!placePanel() && AppState.engine.active())AppState.engine.pause("플로팅창이 감지 영역을 가립니다");main.postDelayed(this,350);}};
}

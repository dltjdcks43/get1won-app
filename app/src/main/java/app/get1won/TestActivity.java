package app.get1won;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.Random;

/** Synthetic UI only. Completion times and animations belong to the fixture, not the engine. */
public final class TestActivity extends Activity {
    public static volatile boolean visible;
    private static WeakReference<TestActivity> instance=new WeakReference<>(null);
    public static TestActivity current(){return instance.get();}
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Random random=new Random(20261006L);
    private final TestAudit audit=new TestAudit();
    private FrameLayout root;private TextView completion;private Button a,b;
    private String screen="home",selection;
    private boolean received,transitioning,monitoring;private volatile boolean calibrating;private boolean registrationOwner;boolean hideCompletionAccessibility;
    private long fixtureGeneration;
    private final android.window.OnBackInvokedCallback back=this::back;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){super.onCreate(state);AppState.initialize(this);instance=new WeakReference<>(this);getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,back);selection=getIntent().getStringExtra("pick");render("completion".equals(selection)?"preview":"home");root.post(()->{if(selection!=null && AppState.accessibility!=null)AppState.accessibility.pick(selection);else if(getIntent().getBooleanExtra("calibrate",false))calibrateForDeviceTest();else if(getIntent().getBooleanExtra("start",false))beginRun();});}
    @Override protected void onResume(){super.onResume();visible=true;instance=new WeakReference<>(this);}
    @Override protected void onPause(){visible=false;if(AppState.engine.active())AppState.engine.pause("테스트 화면이 가려졌습니다");super.onPause();}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);fixtureGeneration++;getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(back);if(current()==this){visible=false;instance.clear();}super.onDestroy();}
    private int screenHeight(){return getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds().height();}
    private <T extends View>T place(T view,float top,int height){FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,dp(height));lp.topMargin=(int)(screenHeight()*top);lp.leftMargin=dp(24);lp.rightMargin=dp(24);root.addView(view,lp);return view;}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.BLACK);t.setTextSize(size);t.setGravity(Gravity.CENTER);return t;}
    private Button button(String value,Runnable run){Button t=new Button(this);t.setText(value);t.setOnClickListener(v->run.run());return t;}
    private void render(String next){
        ui.removeCallbacksAndMessages(null);long token=++fixtureGeneration;screen=next;transitioning=false;
        root=new FrameLayout(this);root.setBackgroundColor(next.equals("home")?0xFFF0F6FF:next.equals("screenB")?0xFFB8DBBE:0xFFFFE4B8);setContentView(root);
        completion=place(text(next.equals("detail")?"3초 구경해요":next.equals("preview")?"1원 받았어요":"",24),.105f,56);completion.setBackgroundColor(Color.WHITE);if(hideCompletionAccessibility)completion.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        place(text(next.equals("home")?"테스트 시작 화면":next.equals("screenB")?"테스트 B 화면":"테스트 상세 화면",24),.26f,64);
        if(next.equals("home")){
            a=place(button("1번 대상 버튼",()->{observe(1);received=false;render("detail");}),.43f,60);
            b=place(button("3번 대상 버튼",()->{observe(3);transitionTo("screenB",250+random.nextInt(751),false);}),.56f,60);
        }
        TextView stats=place(text(audit.summary(),12),.77f,128);stats.setContentDescription("테스트 결과");
        if(next.equals("detail")){
            int delay=AppState.profile.randomTest?5500+random.nextInt(2001):AppState.profile.testDelay;
            if(delay>0)ui.postDelayed(()->{if(token==fixtureGeneration && screen.equals("detail")){received=true;completion.setText("1원 받았어요");}},delay);
        }
    }
    private void observe(int step){if(monitoring)audit.action(step,received,transitioning,!AppState.engine.active());}
    private void back(){
        if(selection!=null || calibrating){AppState.stop();finish();return;}
        if(transitioning){observe(screen.equals("detail")?2:4);return;}
        if(screen.equals("detail")){observe(2);int[] durations={250,400,500,700,1000};transitionTo("home",durations[audit.normal%durations.length],false);}
        else if(screen.equals("screenB")){observe(4);transitionTo("home",250+random.nextInt(751),true);}
        else{AppState.stop();finish();}
    }
    private void transitionTo(String target,int duration,boolean endsCycle){
        ui.removeCallbacksAndMessages(null);long token=++fixtureGeneration;transitioning=true;
        View animation=new View(this){
            final Paint paint=new Paint();final long started=SystemClock.uptimeMillis();boolean finished;
            @Override protected void onDraw(Canvas c){
                long elapsed=SystemClock.uptimeMillis()-started;
                // The moving synthetic scene is sampled by the same detector as the real screen.
                c.drawColor(Color.rgb(90+(int)(elapsed%120),125,175));paint.setColor(Color.WHITE);
                float x=(elapsed%280)/280f*getWidth();for(int y=0;y<getHeight();y+=dp(90))c.drawRect(x-dp(80),y,x+dp(80),y+dp(42),paint);
                if(elapsed>=duration && !finished){finished=true;ui.post(()->{if(token!=fixtureGeneration)return;if(endsCycle)audit.cycleReturned();render(target);});}
                else if(!finished)postInvalidateOnAnimation();
            }
            @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN && monitoring){audit.transitionClicks++;if(!AppState.engine.active())audit.lateActions++;}if(e.getAction()==MotionEvent.ACTION_UP)performClick();return true;}
            @Override public boolean performClick(){super.performClick();return true;}
        };setContentView(animation);
    }
    void beginRun(){monitoring=true;if(AppState.accessibility!=null)AppState.accessibility.start();}
    void selectionFinished(){if(selection!=null)finish();}
    private Rect area(View v){int[] p=new int[2];v.getLocationOnScreen(p);return new Rect(p[0]+dp(4),p[1]+dp(4),p[0]+v.getWidth()-dp(4),p[1]+v.getHeight()-dp(4));}
    /** Device-test helper only: records the same three inputs from live fixture geometry. */
    void calibrateForDeviceTest(){
        AppState.settingsChanged();calibrating=true;registrationOwner=true;root.post(this::calibrateLaidOut);
    }
    private void calibrateLaidOut(){
        if(!a.isLaidOut() || !b.isLaidOut() || a.getWidth()==0 || b.getWidth()==0){root.postOnAnimation(this::calibrateLaidOut);return;}
        Profile p=AppState.profile;
        synchronized(p){Rect display=getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();p.setGeometry(display.width(),display.height(),getDisplay().getRotation());p.targetPackage=getPackageName();Rect ra=area(a),rb=area(b);p.a=new Point(ra.centerX(),ra.centerY());p.b=new Point(rb.centerX(),rb.centerY());p.roi=null;p.template=null;p.autoVerified=false;p.save(this);}
        render("preview");root.post(()->{CaptureService.register(area(completion));waitForCalibration(SystemClock.uptimeMillis()+5000);});
    }
    private void waitForCalibration(long deadline){
        if(AppState.profile.ready()){calibrating=false;AppState.profile.setupDone=true;AppState.profile.save(this);render("home");if(getIntent().getBooleanExtra("calibrate",false)){AppState.notice="테스트 설정 완료. 시작을 누르세요.";finish();}return;}
        if(SystemClock.uptimeMillis()>=deadline){calibrating=false;AppState.notice="시험용 영역 등록 실패";return;}
        ui.postDelayed(()->waitForCalibration(deadline),50);
    }
    boolean ownsRegistration(){return registrationOwner;}
    boolean calibrationDone(){return !calibrating && AppState.profile.ready();}
    int[] counters(){return audit.values();}
}

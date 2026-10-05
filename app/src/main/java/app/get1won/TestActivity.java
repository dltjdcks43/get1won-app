package app.get1won;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Real rendered test screens: the automation uses the same MediaProjection/ROI pipeline. */
public final class TestActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private android.widget.FrameLayout root;private TextView marker,reward,stats;private Button a,b;
    private String screen="home";private int expected=1;private boolean received;private volatile boolean calibrating;
    private int normal,wrongA,earlyBack,missingB,missing4,duplicates,orderErrors;
    private final android.window.OnBackInvokedCallback back=this::back;
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);AppState.initialize(this);getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,back);render("home");}
    private <T extends View>T place(T view,int top,int height){android.widget.FrameLayout.LayoutParams lp=new android.widget.FrameLayout.LayoutParams(-1,dp(height));lp.topMargin=dp(top);lp.leftMargin=dp(24);lp.rightMargin=dp(24);root.addView(view,lp);return view;}
    private TextView label(String text){TextView v=new TextView(this);v.setText(text);v.setTextSize(22);v.setTextColor(Color.BLACK);v.setBackgroundColor(Color.WHITE);v.setGravity(Gravity.CENTER);return v;}
    private Button button(String text,Runnable action){Button v=new Button(this);v.setText(text);v.setOnClickListener(w->action.run());return v;}
    private void render(String next) {
        handler.removeCallbacksAndMessages(null);screen=next;root=new android.widget.FrameLayout(this);root.setBackgroundColor(Color.WHITE);setContentView(root);
        reward=place(label(next.equals("detail")?"3초 구경해요":""),45,52);
        marker=place(label(switch(next){case "home"->"테스트 HOME • 시작 화면";case "screenB"->"테스트 B • 전환 완료";default->"테스트 상세 • 광고 구경";}),130,66);
        if(next.equals("home")) {
            a=place(button("1번 테스트 버튼",()->{if(expected!=1){wrongA++;orderErrors++;}if(expected==3)missingB++;if(expected==4)missing4++;expected=2;received=false;render("detail");}),240,60);
            b=place(button("3번 테스트 버튼",()->{if(expected!=3){duplicates++;orderErrors++;}expected=4;render("screenB");}),325,60);
            place(button("테스트 프로필 자동 등록",this::calibrate),410,56);
            place(button("설정으로 돌아가기",()->{AppState.engine.stop();finish();}),478,48);
        } else if(next.equals("detail") && !calibrating && AppState.profile.testDelay>0) {
            handler.postDelayed(()->{if(screen.equals("detail")){received=true;reward.setText("1원 받았어요");}},AppState.profile.testDelay);
        }
        stats=place(label("정상 사이클: "+normal+"\n잘못된 1번: "+wrongA+" / 너무 빠른 2번: "+earlyBack+"\n3번 누락: "+missingB+" / 4번 누락: "+missing4+"\n중복 동작: "+duplicates+" / 순서 오류: "+orderErrors),540,135);stats.setTextSize(13);
    }
    private void back(){if(calibrating){calibrating=false;CaptureService.cancelRegistration();render("home");return;}if(screen.equals("detail")){if(!received)earlyBack++;if(expected!=2)orderErrors++;expected=3;render("home");}else if(screen.equals("screenB")){if(expected!=4){orderErrors++;missing4++;}else normal++;expected=1;render("home");}else{orderErrors++;AppState.engine.stop();finish();}}
    private Rect rect(View v){int[] xy=new int[2];v.getLocationOnScreen(xy);return new Rect(xy[0]+dp(8),xy[1]+dp(8),xy[0]+v.getWidth()-dp(8),xy[1]+v.getHeight()-dp(8));}
    private Point center(View v){Rect r=rect(v);return new Point(r.centerX(),r.centerY());}
    void calibrate(){
        if(!AppState.capturing || AppState.accessibility==null){Toast.makeText(this,"접근성 및 화면 공유를 먼저 켜세요",Toast.LENGTH_LONG).show();return;}
        AppState.engine.stop();calibrating=true;expected=1;Profile p=AppState.profile;
        synchronized(p){Rect bounds=getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();p.width=bounds.width();p.height=bounds.height();p.rotation=getDisplay().getRotation();p.targetPackage=getPackageName();p.a=center(a);p.b=center(b);p.templates.clear();p.save(this);}
        registerThen("home",rect(marker),()->{
            render("detail");reward.setText("1원 받았어요");root.post(()->registerThen("reward",rect(reward),()->{
                reward.setText("3초 구경해요");registerThen("waiting",rect(reward),()->{
                    render("screenB");root.post(()->registerThen("screenB",rect(marker),()->{calibrating=false;render("home");AppState.log("테스트 프로필 등록 완료 — 플로팅창에서 시작하세요");}));
                });
            }));
        });
    }
    private void registerThen(String key,Rect r,Runnable next){CaptureService.register(key,r);handler.postDelayed(()->{synchronized(AppState.profile){if(!AppState.profile.templates.containsKey(key)){calibrating=false;CaptureService.cancelRegistration();render("home");AppState.log("테스트 기준 등록 실패: "+key);return;}}next.run();},900);}
    boolean calibrationDone(){return !calibrating && AppState.profile.ready();}
    int[] counters(){return new int[]{normal,wrongA,earlyBack,missingB,missing4,duplicates,orderErrors};}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(back);super.onDestroy();}
}

package app.get1won;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public final class MainActivity extends Activity {
    static final String CREDIT="Made by Hwarang · © 2026";
    private LinearLayout content;private TextView readiness;private String page="home",homeState="";
    private final Handler ui=new Handler(Looper.getMainLooper());
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){super.onCreate(state);AppState.initialize(this);if(state!=null)page=state.getString("page","home");render();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("page",page);}
    private void base(String title){readiness=null;ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xFFF6F8FC);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(24),dp(24),dp(20));scroll.addView(content);setContentView(scroll);scroll.setOnApplyWindowInsetsListener((v,insets)->{var i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});TextView heading=text(title,30);heading.setGravity(Gravity.CENTER);heading.setAccessibilityHeading(true);space(24);}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(0xFF17243A);t.setTextSize(size);t.setLineSpacing(dp(3),1);content.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
    private void space(int n){content.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private void button(String s,boolean primary,Runnable run){Button b=new Button(this);b.setText(s);b.setContentDescription(s);b.setAllCaps(false);b.setTextSize(primary?23:20);b.setMinHeight(dp(primary?68:56));b.setPadding(dp(16),dp(12),dp(16),dp(12));b.setTextColor(primary?Color.WHITE:0xFF17243A);GradientDrawable shape=new GradientDrawable();shape.setColor(primary?0xFF1559B5:Color.WHITE);shape.setCornerRadius(dp(16));if(!primary)shape.setStroke(dp(1),0xFF9AAAC0);b.setBackground(shape);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);content.addView(b,lp);b.setOnClickListener(v->run.run());}
    private void footer(){space(30);text(CREDIT,15).setGravity(Gravity.CENTER);}
    private void go(String s){page=s;render();}
    void render(){
        switch(page){
            case "settings"->{base("설정");button("조작창 크기",false,()->new AlertDialog.Builder(this).setTitle("조작창 크기").setSingleChoiceItems(new String[]{"작게","보통","크게","아주 크게"},AppState.profile.panelSize,(d,n)->{AppState.settingsChanged();AppState.profile.panelSize=n;AppState.profile.save(this);if(AppState.accessibility!=null)AppState.accessibility.resizePanel();d.dismiss();}).setNegativeButton("닫기",null).show());button("권한 확인",false,()->go("permissions"));button("자동 인식 상태 확인",false,()->show("자동 인식 상태",AppState.status()+"\n"+CaptureService.info));button("반복 횟수",false,()->new AlertDialog.Builder(this).setTitle("몇 번 반복할까요?").setItems(new String[]{"1회","10회","100회","계속"},(d,n)->{AppState.settingsChanged();AppState.profile.repeats=new int[]{1,10,100,0}[n];AppState.profile.save(this);}).setNegativeButton("닫기",null).show());button("처음부터 다시 설정",false,()->{AppState.settingsChanged();AppState.profile.reset(this);go("permissions");});button("고급 설정",false,()->go("advanced"));button("돌아가기",false,()->go("home"));footer();}
            case "permissions"->{base("권한 확인");readiness=text(AppState.status(),20);text("화면을 누르려면 접근성을 켜주세요. 사용을 시작하려면 화면 확인도 허용해주세요.",20);button("접근성 설정 열기",true,()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));button("화면 확인 허용하기",false,this::requestCapture);text("화면은 휴대전화 안에서만 확인해요. 저장하거나 보내지 않아요.",18);button("돌아가기",false,()->go("home"));footer();}
            case "advanced"->{base("고급 설정");button("현재 화면 분석",false,()->{if(AppState.accessibility==null || !AppState.capturing || !CaptureService.ready || !AppState.accessibility.sessionActive()){go("home");return;}AppState.accessibility.prepareAnalysis();show("현재 화면 분석","원하는 화면을 직접 열고 조작창의 시작을 눌러 분석해주세요. 결과는 이 화면에서 확인할 수 있어요.");});button("분석 결과 보기",false,()->show("분석 결과",AppState.accessibility==null?"접근성을 켜주세요.":AppState.accessibility.analysis()+"\n"+CaptureService.info));button("최근 동작 로그",false,()->show("최근 동작 로그",AppState.advanced()));button("마지막 오류 보기",false,()->Diagnostics.read(this,log->show("마지막 오류 보기",log)));button("화면 확인 끄기",false,()->{AppState.stop();stopService(new Intent(this,CaptureService.class));});button("돌아가기",false,()->go("settings"));footer();}
            default->{base("1원 받기");homeState=AppState.readiness();readiness=text(homeState,22);readiness.setGravity(Gravity.CENTER);space(28);
                if(AppState.accessibility==null)button("설정하기",true,()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
                else if(!AppState.capturing || CaptureService.preparationFailed)button("사용 시작",true,this::requestCapture);
                else if(!CaptureService.ready)text("잠시만 기다려주세요.",22).setGravity(Gravity.CENTER);
                else {text("대상 화면을 열고\n조작창의 시작을 눌러주세요.",26).setGravity(Gravity.CENTER);if(!AppState.accessibility.sessionActive())button("사용 시작",true,this::requestCapture);}
                space(30);button("설정",false,()->go("settings"));footer();}
        }
    }
    private void show(String title,String message){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("닫기",null).show();}
    void requestCapture(){
        if(AppState.accessibility==null){startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return;}
        if(AppState.capturing){if(CaptureService.ready)AppState.accessibility.openSession();else show("화면 확인","화면 확인을 준비하고 있어요. 잠시만 기다려주세요.");render();return;}
        AppState.settingsChanged();CaptureService.preparationFailed=false;
        startActivityForResult(getSystemService(MediaProjectionManager.class).createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()),42);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=42)return;
        if(result==RESULT_OK && data!=null)startForegroundService(new Intent(this,CaptureService.class).putExtra("code",result).putExtra("data",data).putExtra("openSession",true));
        else {if(AppState.accessibility!=null)AppState.accessibility.closeSession();show("화면 확인","화면 확인을 허용해야 사용할 수 있어요.");}
    }
    private final Runnable refresh=new Runnable(){public void run(){
        if(page.equals("home") && !homeState.equals(AppState.readiness()))render();
        else if(readiness!=null && page.equals("permissions"))readiness.setText(AppState.status());
        ui.postDelayed(this,500);
    }};
    @Override protected void onResume(){super.onResume();render();ui.post(refresh);}
    @Override protected void onPause(){ui.removeCallbacks(refresh);super.onPause();}
}

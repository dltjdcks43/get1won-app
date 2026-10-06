package app.get1won;

import android.app.*;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

/** One primary action per setup page; technical controls live behind settings. */
public final class MainActivity extends Activity {
    static final String CREDIT="Made by Hwarang · © 2026";
    private final Handler ui=new Handler(Looper.getMainLooper());
    private LinearLayout content;private TextView live,count,notice;
    private String page="home",purpose="",renderKey="";
    private boolean permissionAsked,permissionPage;private long purposeToken;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){super.onCreate(state);AppState.initialize(this);if(state!=null){page=state.getString("page","home");purpose=state.getString("purpose","");permissionAsked=state.getBoolean("permissionAsked");purposeToken=state.getLong("purposeToken",-1);}render();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("page",page);b.putString("purpose",purpose);b.putBoolean("permissionAsked",permissionAsked);b.putLong("purposeToken",purposeToken);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);purpose="";permissionAsked=false;page=AppState.profile.setupDone?"settings":"wizard";render();}
    private void base(String title){
        live=count=notice=null;ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xFFF6F8FC);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(24),dp(24),dp(20));scroll.addView(content);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});
        TextView heading=text(title,30);heading.setGravity(Gravity.CENTER);heading.setAccessibilityHeading(true);space(24);
    }
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextColor(0xFF17243A);t.setTextSize(size);t.setLineSpacing(dp(3),1);content.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
    private void space(int n){View v=new View(this);content.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    private Button button(String label,boolean primary,Runnable action){Button b=new Button(this);b.setText(label);b.setContentDescription(label);b.setAllCaps(false);b.setTextSize(primary?23:20);b.setMinHeight(dp(primary?68:56));b.setPadding(dp(16),dp(12),dp(16),dp(12));b.setTextColor(primary?Color.WHITE:0xFF17243A);GradientDrawable shape=new GradientDrawable();shape.setColor(primary?0xFF1559B5:Color.WHITE);shape.setCornerRadius(dp(16));if(!primary)shape.setStroke(dp(1),0xFF9AAAC0);b.setBackground(shape);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);content.addView(b,lp);b.setOnClickListener(v->action.run());return b;}
    private void footer(){space(30);TextView t=text(CREDIT,15);t.setGravity(Gravity.CENTER);}
    private void back(){button("돌아가기",false,()->{page="home";purpose="";render();});}
    private void render(){
        permissionPage=false;renderKey=key();Profile p=AppState.profile;
        if(page.equals("settings")){settings();return;}if(page.equals("advanced")){advanced();return;}
        if(page.equals("wizard")){wizard();return;}
        base("1원 받기");
        if(!p.setupDone || !p.ready()){text("쉽게 따라오시면 돼요.",22).setGravity(Gravity.CENTER);space(28);button("처음 설정하기",true,()->begin("setup"));footer();return;}
        live=text(AppState.friendly(),22);live.setGravity(Gravity.CENTER);space(22);
        button("시작하기",true,()->begin("start"));space(20);button("중지",false,()->{purpose="";AppState.stop();render();});space(24);
        count=text("완료 "+AppState.engine.completed+"회",28);count.setGravity(Gravity.CENTER);
        notice=text(userNotice(),18);if(needsHelp())button("완료 화면 알려주기",false,this::fallbackHelp);space(20);button("설정",false,()->{page="settings";render();});footer();
    }
    private void begin(String action){AppState.stop();AppState.notice="준비하고 있어요.";purposeToken=AppState.engine.generation();purpose=action;permissionAsked=false;advance();}
    private void advance(){
        if(purpose.isEmpty())return;if(purposeToken!=AppState.engine.generation()){purpose="";render();return;}
        if(AppState.accessibility==null){permissionPage=true;base("휴대전화 설정");text("자동으로 화면을 누르려면\n휴대전화 설정이 한 번 필요해요.",22);text("설정에서 ‘1원 받기’를 찾아 켜주세요. 돌아오시면 이어서 안내해요.",19);button("설정 열기",true,()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));button("취소",false,()->{purpose="";render();});footer();return;}
        if(!AppState.capturing){permissionPage=true;base("화면 확인 설정");text("화면을 확인하기 위한 설정이에요.\n화면은 저장하거나 전송하지 않아요.",22);button("화면 확인 허용하기",true,this::requestCapture);button("취소",false,()->{purpose="";render();});footer();if(!permissionAsked)requestCapture();return;}
        String next=purpose;purpose="";permissionPage=false;
        switch(next){
            case "start" -> {if(!AppState.profile.ready()){page="wizard";render();}else{AppState.accessibility.start();page="home";render();openTarget();}}
            case "setup" -> {page="wizard";render();}
            case "test" -> {AppState.stop();startActivity(new Intent(this,TestActivity.class).putExtra("calibrate",true));}
            default -> select(next);
        }
    }
    void requestCapture(){if(AppState.capturing)return;permissionAsked=true;startActivityForResult(getSystemService(MediaProjectionManager.class).createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()),42);}
    private void wizard(){
        Profile p=AppState.profile;base("처음 설정하기");
        if(AppState.accessibility==null || !AppState.capturing){text("한 단계씩 안내해 드릴게요.",22);button("설정 시작",true,()->begin("setup"));}
        else if(p.a==null){text("1 / 2",20);space(16);text("처음 들어갈 곳을\n한 번 눌러주세요.",26);button("위치 정하기",true,()->begin("a"));}
        else if(p.b==null){text("첫 번째 위치를 저장했어요.",21);space(16);text("2 / 2",20);text("포인트 버튼을 눌러주세요.",26);button("위치 정하기",true,()->begin("b"));}
        else if(!p.ready()){text("포인트 위치를 저장했어요.",21);space(16);text("완료 화면을 확인해요.",26);text("사용할 앱에서 ‘1원 받았어요’가 보이는 화면을 열어주세요. 글자를 찾으면 자동으로 돌아와요.",20);button("자동 확인하기",true,()->begin("probe"));button("자동으로 찾지 못하나요?",false,()->fallbackHelp());}
        else{text("✓ 설정이 끝났어요!",28);p.setupDone=true;p.save(this);button("바로 시작하기",true,()->begin("start"));button("나중에 시작하기",false,()->{page="home";render();});}
        notice=text(AppState.notice,18);footer();
    }
    private void settings(){
        base("설정");button("처음 위치 다시 정하기",false,()->begin("a"));button("포인트 위치 다시 정하기",false,()->begin("b"));button("완료 화면 자동 인식 다시 확인",false,()->begin("probe"));
        button("조작창 크기",false,()->new AlertDialog.Builder(this).setTitle("조작창 크기").setSingleChoiceItems(new String[]{"작게","보통","크게","아주 크게"},AppState.profile.panelSize,(d,n)->{AppState.settingsChanged();AppState.profile.panelSize=n;AppState.profile.save(this);if(AppState.accessibility!=null)AppState.accessibility.resizePanel();d.dismiss();}).setNegativeButton("닫기",null).show());
        button("반복 횟수",false,()->new AlertDialog.Builder(this).setTitle("몇 번 반복할까요?").setItems(new String[]{"1회","5회","10회","20회","50회","계속"},(d,n)->{AppState.settingsChanged();AppState.profile.repeats=new int[]{1,5,10,20,50,0}[n];AppState.profile.save(this);}).setNegativeButton("닫기",null).show());
        button("처음부터 다시 설정",false,()->new AlertDialog.Builder(this).setMessage("저장한 위치를 지우고 다시 설정할까요?").setPositiveButton("다시 설정",(d,w)->{AppState.settingsChanged();AppState.profile.invalidate();AppState.profile.save(this);page="wizard";begin("setup");}).setNegativeButton("취소",null).show());
        space(20);button("고급 설정",false,()->{page="advanced";render();});back();footer();
    }
    private void advanced(){base("고급 설정");button("완료 화면 자동 인식이 안 될 때",false,this::fallbackHelp);button("고급 정보",false,()->new AlertDialog.Builder(this).setTitle("고급 정보").setMessage(AppState.advanced()+"\n"+CaptureService.frameInfo()).setPositiveButton("닫기",null).show());button("화면 확인 끄기",false,()->{AppState.stop();stopService(new Intent(this,CaptureService.class));page="home";render();});button("자체 테스트 준비",false,()->begin("test"));button("자체 테스트 조건",false,this::testOptions);back();footer();}
    private void fallbackHelp(){new AlertDialog.Builder(this).setTitle("완료 화면 알려주기").setMessage("‘1원 받았어요’가 보이는데 자동으로 찾지 못하나요?\n그 부분을 손가락으로 둘러주세요. 한 번만 알려주시면 기억해요.").setPositiveButton("완료 화면 알려주기",(d,w)->begin("completion")).setNegativeButton("닫기",null).show();}
    private void select(String key){
        if(AppState.profile.targetPackage.isEmpty()){Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);List<ResolveInfo> apps=new ArrayList<>(getPackageManager().queryIntentActivities(query,0));apps.removeIf(r->r.activityInfo.packageName.equals(getPackageName()));apps.sort(Comparator.comparing(r->r.loadLabel(getPackageManager()).toString()));String[] names=apps.stream().map(r->r.loadLabel(getPackageManager()).toString()).toArray(String[]::new);new AlertDialog.Builder(this).setTitle("사용할 앱을 골라주세요").setItems(names,(d,n)->{AppState.profile.targetPackage=apps.get(n).activityInfo.packageName;AppState.profile.save(this);select(key);}).setNegativeButton("취소",null).show();return;}
        AppState.accessibility.prepareExternal(key);openTarget();
    }
    private void openTarget(){
        String pkg=AppState.profile.targetPackage;
        if(pkg.equals(getPackageName())){startActivity(new Intent(this,TestActivity.class));return;}
        Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);
        try{if(launch!=null){startActivity(launch);return;}}catch(ActivityNotFoundException ignored){}
        AppState.notice="사용할 앱을 열어주세요.";moveTaskToBack(true);
    }
    private void testOptions(){String[] labels={"3초","3.5초","5초","6.1초","7초","10초","완료 표시 안 나옴","매번 다르게"};int[] values={3000,3500,5000,6100,7000,10000,0,-1};new AlertDialog.Builder(this).setTitle("시험 화면의 등장 시간").setItems(labels,(d,n)->{AppState.settingsChanged();AppState.profile.randomTest=values[n]<0;if(values[n]>=0)AppState.profile.testDelay=values[n];AppState.profile.save(this);}).setNegativeButton("닫기",null).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==42){if(result==RESULT_OK && data!=null)startForegroundService(new Intent(this,CaptureService.class).putExtra("code",result).putExtra("data",data));else{purpose="";AppState.notice="화면 확인을 허용한 뒤 다시 시작해주세요.";render();}}}
    private boolean needsHelp(){return AppState.engine.state==Engine.State.PAUSED && AppState.engine.reason.contains("완료") && !AppState.profile.imageReady();}
    private String userNotice(){if(needsHelp())return "완료 화면을 자동으로 찾기 어려워요.";if(AppState.engine.state==Engine.State.ERROR)return "설정을 확인한 뒤 다시 시작해주세요.";return AppState.notice;}
    private String key(){Profile p=AppState.profile;return page+":"+p.setupDone+":"+p.ready()+":"+(p.a!=null)+":"+(p.b!=null)+":"+needsHelp();}
    private final Runnable refresh=new Runnable(){public void run(){
        if(!purpose.isEmpty() && AppState.accessibility!=null && (AppState.capturing || !permissionAsked)){advance();}
        else if(!permissionPage && !key().equals(renderKey))render();
        if(live!=null)live.setText(AppState.friendly());if(count!=null)count.setText(getString(R.string.completed_count,AppState.engine.completed));if(notice!=null)notice.setText(userNotice());ui.postDelayed(this,250);
    }};
    @Override protected void onResume(){super.onResume();android.graphics.Rect b=getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();int r=getDisplay().getRotation();synchronized(AppState.engine){synchronized(AppState.profile){if(!AppState.profile.geometry(b.width(),b.height(),r)){AppState.settingsChanged();AppState.profile.setGeometry(b.width(),b.height(),r);AppState.profile.save(this);AppState.notice="화면 크기가 바뀌었어요. 위치를 다시 정해주세요.";}}}ui.post(refresh);}
    @Override protected void onPause(){ui.removeCallbacks(refresh);super.onPause();}
}

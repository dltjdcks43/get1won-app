package app.get1won;

import android.app.*;
import android.content.*;
import android.media.projection.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public final class MainActivity extends Activity {
    private LinearLayout content;private TextView status,setup,notice;private final Handler ui=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);AppState.initialize(this);ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(28,20,28,20);scroll.addView(content);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});
        TextView title=text("1원 받기");title.setTextSize(28);text("사용할 앱을 먼저 열어 두고 위치와 완료 영역을 지정하세요.");
        status=text("");setup=text("");
        button("접근성 설정",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button("화면 공유 시작",this::requestCapture);
        button("1번 위치 지정",()->select("a"));button("3번 위치 지정",()->select("b"));button("완료 표시 영역 지정",()->select("completion"));
        text("반복 횟수");Spinner repeats=new Spinner(this);String[] labels={"1회","5회","10회","20회","50회","100회","무한 반복"};int[] values={1,5,10,20,50,100,0};repeats.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));for(int n=0;n<values.length;n++)if(values[n]==AppState.profile.repeats)repeats.setSelection(n);repeats.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> a){}public void onItemSelected(AdapterView<?> a,View v,int n,long id){if(AppState.profile.repeats!=values[n]){AppState.settingsChanged();AppState.profile.repeats=values[n];AppState.profile.save(MainActivity.this);}}});content.addView(repeats);
        button("시작",()->{if(!ready())return;AppState.stop();if(AppState.profile.targetPackage.equals(getPackageName()))startActivity(new Intent(this,TestActivity.class).putExtra("start",true));else{AppState.accessibility.prepareExternal(null);moveTaskToBack(true);}});
        button("중지",AppState::stop);notice=text("");
        button("자체 테스트",()->new AlertDialog.Builder(this).setTitle("자체 테스트").setMessage("시험용 A/B 위치와 완료 영역을 자동으로 준비합니다. 현재 지정한 세 항목은 테스트 설정으로 바뀝니다.").setPositiveButton("테스트 준비",(d,w)->{if(AppState.accessibility==null || !AppState.capturing){AppState.notice="접근성과 화면 공유를 먼저 켜세요";return;}AppState.stop();startActivity(new Intent(this,TestActivity.class).putExtra("calibrate",true));}).setNeutralButton("등장 시간 선택",(d,w)->testOptions()).setNegativeButton("닫기",null).show());button("고급 정보",()->new AlertDialog.Builder(this).setTitle("고급 정보").setMessage(AppState.advanced()).setPositiveButton("닫기",null).setNeutralButton("화면 공유 중지",(d,w)->{AppState.stop();stopService(new Intent(this,CaptureService.class));}).show());
    }
    private TextView text(String value){TextView t=new TextView(this);t.setText(value);t.setTextSize(15);content.addView(t);return t;}
    void requestCapture(){if(!AppState.capturing)startActivityForResult(getSystemService(MediaProjectionManager.class).createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()),42);}
    private void button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->action.run());content.addView(b);}
    private boolean ready(){if(AppState.accessibility==null || !AppState.capturing || !AppState.profile.ready()){AppState.notice="접근성·화면 공유를 켜고 세 항목을 지정하세요";return false;}return true;}
    private void select(String key){if(AppState.accessibility==null || (key.equals("completion")&&!AppState.capturing)){AppState.notice="접근성 및 화면 공유를 먼저 켜세요";return;}AppState.accessibility.prepareExternal(key);moveTaskToBack(true);}
    private void testOptions(){String[] labels={"3000ms","3500ms","5000ms","6100ms","7000ms","10000ms","완료 표시 안 나옴","장시간 시험: 매회 5500~7500ms 랜덤"};int[] values={3000,3500,5000,6100,7000,10000,0,-1};new AlertDialog.Builder(this).setTitle("완료 표시 등장 시간 (BACK 타이머 아님)").setItems(labels,(d,n)->{AppState.settingsChanged();AppState.profile.randomTest=values[n]<0;if(values[n]>=0)AppState.profile.testDelay=values[n];AppState.profile.save(this);AppState.notice="테스트 조건 변경 완료. 화면 전환은 매회 250~1000ms로 변화합니다.";}).setNegativeButton("닫기",null).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==42 && result==RESULT_OK && data!=null)startForegroundService(new Intent(this,CaptureService.class).putExtra("code",result).putExtra("data",data));}
    private final Runnable refresh=new Runnable(){public void run(){status.setText(AppState.status());setup.setText(AppState.profile.setupSummary());notice.setText(AppState.notice);ui.postDelayed(this,250);}};
    @Override protected void onResume(){super.onResume();android.graphics.Rect b=getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();int r=getDisplay().getRotation();synchronized(AppState.engine){synchronized(AppState.profile){if(!AppState.profile.geometry(b.width(),b.height(),r)){AppState.settingsChanged();AppState.profile.setGeometry(b.width(),b.height(),r);AppState.profile.save(this);AppState.notice="화면 크기가 바뀌었습니다. 세 항목을 다시 지정하세요";}}}ui.post(refresh);}
    @Override protected void onPause(){ui.removeCallbacksAndMessages(null);super.onPause();}
}

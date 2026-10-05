package app.get1won;

import android.app.*;
import android.content.*;
import android.media.projection.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public final class MainActivity extends Activity {
    private LinearLayout content;private TextView status,logs;private final Handler ui=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);AppState.initialize(this);
        ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(24,24,24,24);scroll.addView(content);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});
        TextView title=new TextView(this);title.setText("1원 받기");title.setTextSize(28);content.addView(title);
        TextView help=new TextView(this);help.setText("보상 문구가 보이는 첫 유효 프레임에서 뒤로갑니다. 시간 초과는 일시정지입니다.\n접근성 → 화면 공유 → 대상 앱 → 플로팅창 ‘영역/위치’에서 기준 등록 → HOME에서 시작\n대상 앱의 글꼴·확대율·방향을 바꾸면 좌표와 영역을 다시 등록하세요.");content.addView(help);
        status=new TextView(this);content.addView(status);
        button("접근성 설정",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button("알림 허용",()->requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},4));
        button("화면 공유 시작",()->{
            if(AppState.capturing)return;
            MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);
            startActivityForResult(manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()),42);
        });
        button("화면 공유 중지",()->{AppState.engine.stop();stopService(new Intent(this,CaptureService.class));});
        button("1번 위치 지정",()->select("a"));button("3번 위치 지정",()->select("b"));button("1원 받았어요 영역 지정",()->select("reward"));button("3초 대기 기준 저장",()->select("waiting"));button("HOME 기준 영역 지정",()->select("home"));button("B 화면 기준 영역 지정",()->select("screenB"));
        choose("반복 횟수",new String[]{"1회","5회","10회","20회","50회","무한 반복"},new int[]{1,5,10,20,50,0},AppState.profile.repeats,v->AppState.profile.repeats=v);
        choose("최대 대기",new String[]{"10초","15초","30초","제한 없음"},new int[]{10,15,30,0},AppState.profile.timeoutSeconds,v->AppState.profile.timeoutSeconds=v);
        choose("테스트 보상 전환 시간",new String[]{"3000ms","3500ms","5000ms","7000ms","영구 대기 (보상 없음)"},new int[]{3000,3500,5000,7000,0},AppState.profile.testDelay,v->AppState.profile.testDelay=v);
        button("테스트 화면 열기",()->{AppState.engine.stop();startActivity(new Intent(this,TestActivity.class));});
        button("로그 내보내기",()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"get1won-log.txt"),43));
        logs=new TextView(this);logs.setTextSize(11);logs.setTextIsSelectable(true);content.addView(logs);
    }
    private void button(String label,Runnable run){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->run.run());content.addView(b);}
    private interface IntChoice{void set(int v);}
    private void choose(String label,String[] labels,int[] values,int current,IntChoice action){TextView t=new TextView(this);t.setText(label);content.addView(t);Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));for(int i=0;i<values.length;i++)if(values[i]==current)s.setSelection(i);s.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){if(AppState.engine.active())AppState.engine.pause("설정 변경");synchronized(AppState.profile){action.set(values[position]);AppState.profile.save(MainActivity.this);}}});content.addView(s);}
    private void select(String key){if(AppState.accessibility==null){Toast.makeText(this,"접근성 권한을 먼저 켜세요",Toast.LENGTH_LONG).show();return;}AppState.accessibility.prepareSelection(key);moveTaskToBack(true);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==42 && result==RESULT_OK && data!=null)startForegroundService(new Intent(this,CaptureService.class).putExtra("code",result).putExtra("data",data));if(request==43 && result==RESULT_OK && data!=null && data.getData()!=null){try(java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out!=null)out.write(AppState.logs().getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception ex){AppState.log("로그 내보내기 실패: "+ex.getMessage());}}}
    private final Runnable refresh=new Runnable(){@Override public void run(){status.setText(AppState.status()+"\n"+AppState.profile.summary());logs.setText(AppState.logs());ui.postDelayed(this,500);}};
    @Override protected void onResume(){super.onResume();ui.post(refresh);}
    @Override protected void onPause(){ui.removeCallbacksAndMessages(null);super.onPause();}
}

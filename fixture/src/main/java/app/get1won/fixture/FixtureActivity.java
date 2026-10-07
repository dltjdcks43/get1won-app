package app.get1won.fixture;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.window.OnBackInvokedDispatcher;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Separate application: visible content only, no engine API, hidden tags, or custom descriptions. */
public final class FixtureActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private int cycle,adClicks,pointsClicks,rewardBack,historyBack,earlyBack,wrongClick;
    private String screen="home";private boolean complete;
    private LinearLayout column;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);home();}
    private void base() {
        column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);column.setPadding(dp(24),dp(20),dp(24),dp(16));column.setBackgroundColor(Color.WHITE);setContentView(column);
        column.setOnApplyWindowInsetsListener((v,i)->{var bars=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(24)+bars.left,dp(20)+bars.top,dp(24)+bars.right,dp(16)+bars.bottom);return i;});
    }
    private TextView label(String text,int size) {TextView view=new TextView(this);view.setText(text);view.setTextSize(size);view.setTextColor(Color.BLACK);column.addView(view,new LinearLayout.LayoutParams(-1,-2));return view;}
    private void gap(int height){column.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private void home() {
        screen="home";base();
        TextView points=label("내 포인트",22);points.setOnClickListener(v->{pointsClicks++;if(rewardBack!=cycle+1)wrongClick++;history();});
        label(String.format(java.util.Locale.KOREA,"%,d원",4811+cycle*37),20);gap(70+cycle*9);
        String[] anchors={"다시 혜택 구경하고 1원 받아요","여기서 구경하면 1원 받아요","한번 더 구경하고 1원 받아요"};
        if(cycle%3==1) { label("여기서 구경하면",20);label("1원 받아요",20); } else label(anchors[cycle%3],20);
        gap(18);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setBackgroundColor(0xFFF1F4FA);row.setPadding(dp(10),dp(8),dp(10),dp(8));
        ImageView icon=new ImageView(this);icon.setImageResource(new int[]{android.R.drawable.ic_menu_camera,android.R.drawable.ic_menu_compass,android.R.drawable.ic_menu_gallery}[cycle%3]);row.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(48)));
        String title=new String[]{"제철 식탁을 만나보세요","여행 준비 특별 안내","새로운 생활 공간"}[cycle%3];
        View titleView;
        if(cycle%3==1)titleView=new PaintedTitle(title);
        else {TextView text=new TextView(this);text.setText(title);text.setTextSize(20);text.setTextColor(Color.BLACK);titleView=text;}
        row.addView(titleView,new LinearLayout.LayoutParams(0,dp(60),1));TextView arrow=new TextView(this);arrow.setText("›");arrow.setTextSize(30);row.addView(arrow,new LinearLayout.LayoutParams(dp(24),-2));
        // Canvas-only title and touch handler emulate an ordinary custom-drawn app in cycle 2.
        if(cycle%3==1)row.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP)enterAd();return true;});
        else row.setOnClickListener(v->enterAd());
        column.addView(row,new LinearLayout.LayoutParams(-1,dp(90)));gap(32);
        label("혜택 알림 받고 1원 받기",18).setOnClickListener(v->{wrongClick++;save();});save();
    }
    private void enterAd(){if(!screen.equals("home")){wrongClick++;return;}adClicks++;screen="reward";complete=false;base();label("광고 안내",24);gap(80);TextView waiting=label("3초 구경해요",24);save();ui.postDelayed(()->{if(screen.equals("reward")){complete=true;waiting.setText("1원 받았어요");save();}},3100+cycle*300);}
    private void history(){screen="history";base();label("포인트 내역",24);gap(20);label("전체",20);gap(24);label("방문 적립 +1원",20);gap(30);label("구매 적립 +20원",20);gap(30);label("포인트 사용 -10원",20);save();}
    private void back(){
        if(screen.equals("reward")){if(!complete){earlyBack++;save();return;}rewardBack++;home();}
        else if(screen.equals("history")){historyBack++;cycle++;home();}
        else {wrongClick++;save();}
    }
    private void save(){try(FileOutputStream out=openFileOutput("audit.txt",MODE_PRIVATE)){out.write(("cycles="+cycle+" ad="+adClicks+" points="+pointsClicks+" rewardBack="+rewardBack+" historyBack="+historyBack+" earlyBack="+earlyBack+" wrongClick="+wrongClick).getBytes(StandardCharsets.UTF_8));}catch(IOException error){throw new IllegalStateException(error);}}
    private final class PaintedTitle extends View {
        private final String text;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        PaintedTitle(String text){super(FixtureActivity.this);this.text=text;paint.setColor(Color.BLACK);paint.setTextSize(dp(20));}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawText(text,0,getHeight()/2f+dp(7),paint);}
    }
    @Override public void onDestroy(){ui.removeCallbacksAndMessages(null);super.onDestroy();}
}

package app.get1won;
import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.Random;

/** Synthetic fixture: all timing variation is here, never an automation trigger. */
public final class TestActivity extends Activity {
    public static volatile boolean visible;
    private static WeakReference<TestActivity> instance=new WeakReference<>(null);
    public static TestActivity current(){return instance.get();}
    private final Handler ui=new Handler(Looper.getMainLooper());private final Random random=new Random(20261006L);
    final TestAudit audit=new TestAudit();private FrameLayout root;private String screen="home";private boolean received,transitioning;private long token;
    boolean hideCompletionAccessibility,hideAllText,ambiguous,wrongHistory;int historyClicks;long shownAt,backAt;
    private final android.window.OnBackInvokedCallback back=this::back;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){super.onCreate(state);AppState.initialize(this);instance=new WeakReference<>(this);getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,back);render("home");}
    @Override protected void onResume(){super.onResume();visible=true;instance=new WeakReference<>(this);}
    @Override protected void onPause(){visible=false;super.onPause();}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);token++;getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(back);if(current()==this){visible=false;instance.clear();}super.onDestroy();}
    private <T extends View>T place(T v,float top,int height){FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,dp(height));lp.topMargin=(int)(getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds().height()*top);lp.leftMargin=dp(24);lp.rightMargin=dp(24);root.addView(v,lp);return v;}
    /** Canvas text exposes no TextView text even to findAccessibilityNodeInfosByText. */
    private final class OcrText extends View {
        private String value;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        OcrText(String value,int size){super(TestActivity.this);this.value=value;paint.setColor(Color.BLACK);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,size,getResources().getDisplayMetrics()));setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void value(String text){value=text;invalidate();root.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}
        @Override protected void onMeasure(int w,int h){Paint.FontMetrics fm=paint.getFontMetrics();setMeasuredDimension(View.MeasureSpec.getSize(w),resolveSize(Math.round((fm.descent-fm.ascent)*value.split("\\n").length)+dp(8),h));}
        @Override protected void onDraw(Canvas canvas){String[] lines=value.split("\\n");Paint.FontMetrics fm=paint.getFontMetrics();float line=fm.descent-fm.ascent;float y=(getHeight()-line*lines.length)/2-fm.ascent;for(String text:lines){canvas.drawText(text,getWidth()/2f,y,paint);y+=line;}}
    }
    private View text(String value,int size){if(hideAllText)return new OcrText(value,size);TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.BLACK);t.setGravity(Gravity.CENTER);return t;}
    private LinearLayout card(String label,String sub,int color,Runnable action){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setBackgroundColor(color);c.addView(text(label,23));c.addView(text(sub,18));c.setOnClickListener(v->action.run());return c;}
    void resetHome(){render("home");}
    private void render(String next){
        ui.removeCallbacksAndMessages(null);long current=++token;screen=next;transitioning=false;
        root=new FrameLayout(this);root.setBackgroundColor(0xFFF4F8FF);setContentView(root);
        if(next.equals("home")){
            place(card("내 포인트",String.format(java.util.Locale.KOREA,"%,d원",4300+audit.normal),Color.WHITE,()->{observe(3);transition("history",false);}),.12f,95);
            place(text(audit.normal%2==0?"다시 혜택 구경하고\n1원 받아요":"다시 구경하고 1원 받아요",22),.40f,76);
            int shade=Color.rgb(180+random.nextInt(60),180+random.nextInt(60),180+random.nextInt(60));
            LinearLayout ad=card("상품 "+audit.normal+" · "+new String[]{"여행","음악","생활","건강"}[audit.normal%4],"AD · 새로운 혜택을 확인하세요",shade,()->{observe(1);received=false;transition("detail",false);});
            // Different drawn icon each cycle; not exposed as the semantic anchor.
            View icon=new View(this){final Paint p=new Paint();@Override protected void onDraw(Canvas c){p.setColor(shade^0x00404040);c.drawCircle(getWidth()/2f,getHeight()/2f,Math.min(getWidth(),getHeight())/3f,p);}};ad.addView(icon,new LinearLayout.LayoutParams(-1,dp(20)));place(ad,.52f,120);
            if(ambiguous){Button other=new Button(this);other.setText("다른 광고");other.setOnClickListener(v->historyClicks++);place(other,.53f,80);}
        }else if(next.equals("detail")){
            View completion=place(hideCompletionAccessibility?new OcrText("3초 구경해요",26):text("3초 구경해요",26),.12f,64);completion.setBackgroundColor(Color.WHITE);
            place(text("광고 상세 화면",26),.4f,70);
            int delay=AppState.profile.randomTest?3000+random.nextInt(5001):AppState.profile.testDelay;
            if(delay>0)ui.postDelayed(()->{if(token==current){received=true;shownAt=System.nanoTime();if(completion instanceof OcrText canvas)canvas.value("1원 받았어요.");else ((TextView)completion).setText("1원 받았어요.");}},delay);
        }else if(next.equals("history")){
            place(text(wrongHistory?"다른 화면":"전체",24),.12f,60);
            for(int k=0;k<3;k++){LinearLayout row=card(wrongHistory?"관련 없는 항목":"광고 보고 1원 받기","1원",Color.WHITE,()->historyClicks++);place(row,.25f+k*.12f,80);}
        }
    }
    private void observe(int step){audit.action(step,received,transitioning,!AppState.engine.active());}
    private void back(){
        if(transitioning){observe(screen.equals("detail")?2:4);return;}
        if(screen.equals("detail")){backAt=System.nanoTime();observe(2);transition("home",false);}
        else if(screen.equals("history")){observe(4);transition("home",true);}
        else{AppState.stop();finish();}
    }
    private void transition(String target,boolean end){
        ui.removeCallbacksAndMessages(null);long current=++token;transitioning=true;int duration=250+random.nextInt(951);
        View loading=new View(this){@Override protected void onDraw(Canvas c){c.drawColor(Color.rgb((int)(SystemClock.uptimeMillis()%100)+100,170,190));postInvalidateOnAnimation();}@Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){audit.transitionClicks++;if(!AppState.engine.active())audit.lateActions++;}if(e.getAction()==MotionEvent.ACTION_UP)performClick();return true;}@Override public boolean performClick(){super.performClick();return true;}};setContentView(loading);
        ui.postDelayed(()->{if(current==token){if(end)audit.cycleReturned();render(target);}},duration);
    }
    int[] counters(){return audit.values();}
}

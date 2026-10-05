package app.get1won;

import java.text.SimpleDateFormat;
import java.util.*;

public final class AppState {
    public static final Profile profile=new Profile();
    // Cleared in onDestroy; this is the currently connected platform service, never an Activity.
    @android.annotation.SuppressLint("StaticFieldLeak")
    public static volatile AutomationService accessibility;
    public static volatile boolean capturing;
    public static volatile String notice="사용할 앱 화면에서 세 항목을 지정하세요";
    private static boolean loaded;
    private static final Object logLock=new Object();
    private static final ArrayDeque<String> logs=new ArrayDeque<>();
    public static synchronized void initialize(android.content.Context c){if(!loaded){loaded=true;profile.load(c);}}
    public static void log(String message){synchronized(logLock){logs.addLast(new SimpleDateFormat("HH:mm:ss.SSS",Locale.KOREA).format(new Date())+" "+message);while(logs.size()>100)logs.removeFirst();}}
    public static String logs(){synchronized(logLock){return String.join("\n",logs);}}
    public static final Engine engine=new Engine(new Engine.Port(){
        public boolean act(int step,long generation){AutomationService s=accessibility;return capturing && s!=null && s.execute(step,generation);}
        public void log(String message){AppState.log(message);}
    });
    public static void stop(){synchronized(engine){engine.stop();CaptureService.cancelRegistration();AutomationService s=accessibility;if(s!=null)s.cancelSelection();}}
    public static void settingsChanged(){synchronized(engine){engine.settingsChanged();CaptureService.cancelRegistration();}}
    public static String status(){synchronized(engine){return "접근성   "+(accessibility==null?"○ 꺼짐":"● 켜짐")+"\n화면 공유   "+(capturing?"● 실행 중":"○ 중지")+"\n\n현재: "+Engine.label(engine.state)+"\n완료: "+engine.completed+"회";}}
    public static String advanced(){synchronized(engine){return "state: "+engine.state+"\ngeneration: "+engine.generation+"\ncycleId: "+engine.cycleId+"\n1/2/3/4: "+Arrays.toString(engine.actions)+"\n완료 감지: "+engine.detections+"\n오류: "+engine.errors+"\n"+engine.reason+"\n\n"+logs();}}
}

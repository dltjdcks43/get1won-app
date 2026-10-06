package app.get1won;
import java.util.*;
public final class AppState {
    public static final Profile profile=new Profile();
    @android.annotation.SuppressLint("StaticFieldLeak") public static volatile AutomationService accessibility;
    public static volatile boolean capturing;
    public static volatile String notice="대상 화면을 열고 조작창의 시작을 눌러주세요.";
    private static boolean loaded;
    private static final ArrayDeque<String> logs=new ArrayDeque<>();
    public static synchronized void initialize(android.content.Context c){if(!loaded){loaded=true;profile.load(c);}}
    public static synchronized void log(String s){logs.addLast(s);while(logs.size()>120)logs.removeFirst();}
    public static synchronized String logs(){return String.join("\n",logs);}
    public static final Engine engine=new Engine(AppState::log);
    public static void stop(){engine.stop();}
    public static void settingsChanged(){engine.settingsChanged();}
    public static String status(){return "접근성: "+(accessibility!=null?"켜짐":"꺼짐")+"\n화면 확인: "+(capturing?"켜짐":"꺼짐")+"\n"+engine.reason;}
    public static String advanced(){return "state="+engine.state+" generation="+engine.generation+" cycle="+engine.cycleId+"\n1/2/3/4="+Arrays.toString(engine.actions)+"\n"+logs();}
}

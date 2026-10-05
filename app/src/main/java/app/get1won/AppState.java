package app.get1won;

import java.text.SimpleDateFormat;
import java.util.*;

public final class AppState {
    public static final Profile profile=new Profile();
    public static volatile AutomationService accessibility;
    public static volatile boolean capturing;
    private static boolean loaded;
    private static final ArrayDeque<String> logs=new ArrayDeque<>();
    public static synchronized void initialize(android.content.Context c){if(!loaded){loaded=true;profile.load(c);}}
    public static synchronized void log(String message) {
        logs.addLast(new SimpleDateFormat("HH:mm:ss.SSS",Locale.KOREA).format(new Date())+" "+message);
        while(logs.size()>200)logs.removeFirst();
    }
    public static synchronized String logs(){return String.join("\n",logs);}
    public static final Engine engine=new Engine(new Engine.Port() {
        @Override public boolean act(int step,long epoch) {
            AutomationService service=accessibility;
            return capturing && service!=null && service.execute(step,epoch);
        }
        @Override public void log(String s){AppState.log(s);}
    });
    public static String status() {
        synchronized(engine) {
            return "접근성: "+(accessibility!=null?"켜짐":"꺼짐")+" / 화면 공유: "+(capturing?"실행":"중지")+
                "\n상태: "+engine.state+"\n"+engine.reason+"\n현재 사이클: "+engine.cycleId+" / 완료: "+engine.completed+
                "\n실행 횟수 1/2/3/4: "+Arrays.toString(engine.actions)+"\n보상 감지: "+engine.rewardDetections+" / 오류: "+engine.errors;
        }
    }
}

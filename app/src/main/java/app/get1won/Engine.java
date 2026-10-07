package app.get1won;
import java.util.Arrays;
import java.util.function.Consumer;
/** Observe, decide, submit, confirm transition. Stores no screen target or Android node. */
public final class Engine {
    public enum State { IDLE, HOME, AD_ENTRY, REWARD, HOME_AFTER_REWARD, HISTORY, HOME_AFTER_HISTORY, PAUSED }
    public enum Action { AD, POINTS, BACK_REWARD, BACK_HISTORY }
    public record Decision(Action action, Semantic.Node target, int attempt) {}
    public volatile State state=State.IDLE;
    public volatile long generation,cycleId;
    public volatile int completed;
    public final long[] actions=new long[4];
    public volatile String reason="대상 화면에서 시작해주세요.",lastAction="none",lastSemanticResult="none";
    private int limit,attempts; private long entered,lastTap; private final Consumer<String> log;
    public Engine(Consumer<String> log) { this.log=log; }
    public boolean active() { return state!=State.IDLE && state!=State.PAUSED; }
    public void start(int repeats,long now) { generation++;cycleId++;completed=0;limit=repeats;attempts=0;Arrays.fill(actions,0);move(State.HOME,now); }
    public void stop() { generation++;state=State.IDLE;reason="중지했어요."; }
    public void settingsChanged() { stop(); }
    public void pause(String message) { generation++;state=State.PAUSED;reason=message;log.accept(message); }
    public void fail(String message) { pause(message); }
    private void move(State next,long now) { state=next;entered=now;reason=next.name();log.accept("state="+next+" cycleId="+cycleId); }
    public boolean expired(long now) { return active() && now-entered>=30000; }
    public boolean needsOcr(Semantic.Found f,long now) {
        return switch(state) {
            case HOME -> !f.home() || f.ad()==null;
            case AD_ENTRY -> f.home() ? f.ad()==null && now-lastTap>=1000 : f.waiting()==null && f.complete()==null;
            case REWARD -> f.waiting()==null && f.complete()==null;
            case HOME_AFTER_REWARD,HOME_AFTER_HISTORY -> !f.home();
            case HISTORY -> !f.history();
            default -> false;
        };
    }
    public Decision observe(Semantic.Found f,long now) {
        lastSemanticResult=f.summary();
        if(!active()) return null;
        if(expired(now)) { log.accept(f.failure());pause("화면 전환을 확인하지 못해 멈췄어요.");return null; }
        switch(state) {
            case HOME -> { if(f.home() && f.ad()!=null) return new Decision(Action.AD,f.ad(),1); }
            case AD_ENTRY -> {
                if(!f.home() && !f.history() && (f.waiting()!=null || f.complete()!=null)) {
                    actions[0]++;move(State.REWARD,now);
                    if(f.waiting()==null && f.complete()!=null) return new Decision(Action.BACK_REWARD,null,0);
                } else if(f.home() && now-lastTap>=1000) {
                    if(attempts>=3) pause("광고 화면 진입을 3회 확인하지 못했어요.");
                    else if(f.ad()!=null) return new Decision(Action.AD,f.ad(),attempts+1);
                }
            }
            case REWARD -> { if(!f.home() && !f.history() && f.waiting()==null && f.complete()!=null) return new Decision(Action.BACK_REWARD,null,0); }
            case HOME_AFTER_REWARD -> { if(f.home()) return new Decision(Action.POINTS,f.points(),1); }
            case HISTORY -> { if(!f.home() && f.history()) return new Decision(Action.BACK_HISTORY,null,0); }
            case HOME_AFTER_HISTORY -> {
                if(f.home()) {
                    completed++;cycleId++;attempts=0;
                    if(limit>0 && completed>=limit) { stop();reason="완료했어요."; }
                    else move(State.HOME,now);
                }
            }
            default -> { }
        }
        return null;
    }
    public void submitted(Decision d,boolean accepted,long now) {
        lastAction=d.action+" attempt="+d.attempt+" accepted="+accepted;log.accept(lastAction);
        switch(d.action) {
            case AD -> { attempts=d.attempt;lastTap=now;if(state==State.HOME) move(State.AD_ENTRY,now); }
            case POINTS -> { if(accepted) { actions[2]++;move(State.HISTORY,now); } else pause("내 포인트 클릭을 요청하지 못했어요."); }
            case BACK_REWARD -> { if(accepted) { actions[1]++;move(State.HOME_AFTER_REWARD,now); } else pause("뒤로가기를 요청하지 못했어요."); }
            case BACK_HISTORY -> { if(accepted) { actions[3]++;move(State.HOME_AFTER_HISTORY,now); } else pause("뒤로가기를 요청하지 못했어요."); }
        }
    }
}

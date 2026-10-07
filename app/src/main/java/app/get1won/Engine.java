package app.get1won;
import java.util.Arrays;
import java.util.function.Consumer;
/** Single-thread controller. Requests reserve an action; only semantic transitions confirm success. */
public final class Engine {
    public enum State { IDLE, HOME, AD_ENTRY, REWARD, WAIT_FOR_POINTS, POINTS_ENTRY, HISTORY, HOME_AFTER_HISTORY, PAUSED }
    public enum Action { AD, POINTS, BACK_REWARD, BACK_HISTORY }
    public record Decision(Action action, Semantic.Node target, int attempt, long id, long generation, long cycle) {}
    public volatile State state=State.IDLE;
    public volatile long generation,cycleId,actionEpoch;
    public volatile int completed;
    public final long[] actions=new long[4];
    public volatile String reason="대상 화면에서 시작해주세요.",lastAction="none",lastSemanticResult="none",lastSuccess="없음";
    private int limit,attempts,pointsAttempts;
    private long entered,lastTap,lastPointsTap,reserved,gesture,gestureSince;
    private final Consumer<String> log;
    public Engine(Consumer<String> log) { this.log=log; }
    public boolean active() { return state!=State.IDLE && state!=State.PAUSED; }
    public boolean busy() { return reserved!=0 || gesture!=0; }
    public void start(int repeats,long now) { generation++;cycleId++;completed=0;limit=repeats;attempts=0;pointsAttempts=0;reserved=gesture=0;Arrays.fill(actions,0);lastSuccess="없음";move(State.HOME,now); }
    public void stop() { generation++;reserved=gesture=0;state=State.IDLE;reason="중지했어요."; }
    public void settingsChanged() { stop(); }
    public void pause(String message) { generation++;reserved=gesture=0;state=State.PAUSED;reason=message;log.accept("일시정지: "+message); }
    public void fail(String message) { pause(message); }
    private void move(State next,long now) { state=next;entered=now;reason=next.name();log.accept("state="+next+" cycleId="+cycleId+" completed="+completed); }
    private void success(int index,String message) { actions[index]++;lastSuccess=message;log.accept("화면 전환 확인: "+message); }
    public boolean expired(long now) { return active() && now-entered>=30000; }
    public boolean needsOcr(Semantic.Found f,long now) {
        if(busy())return false;
        return switch(state) {
            case HOME -> !f.home() || f.ad()==null;
            case AD_ENTRY -> f.home() ? f.ad()==null && now-lastTap>=1000 : f.waiting()==null && f.complete()==null;
            case REWARD -> f.waiting()==null && f.complete()==null;
            case WAIT_FOR_POINTS,HOME_AFTER_HISTORY -> f.points()==null;
            case POINTS_ENTRY -> !f.history() && f.points()==null;
            case HISTORY -> !f.history();
            default -> false;
        };
    }
    private Decision decide(Action a,Semantic.Node n,int attempt) {
        reserved=++actionEpoch;
        return new Decision(a,n,attempt,reserved,generation,cycleId);
    }
    public boolean current(Decision d) { return d!=null && active() && d.generation==generation && d.cycle==cycleId && d.id==actionEpoch; }
    /** A moved overlay is not an attempted click; re-observe before making a new decision. */
    public void defer(Decision d) { if(current(d) && reserved==d.id) reserved=0; }
    private static boolean pointsSurface(Semantic.Found f) { return f.points()!=null && !f.history() && f.waiting()==null && f.complete()==null; }
    public Decision observe(Semantic.Found f,long now) {
        lastSemanticResult=f.summary();
        if(!active())return null;
        if(gesture!=0 && now-gestureSince>=3000) { pause("gesture 완료 응답이 없어 멈췄어요.");return null; }
        if(expired(now)) { log.accept(f.failure());pause(state==State.POINTS_ENTRY?"내 포인트를 열지 못했어요.":"화면 전환을 확인하지 못해 멈췄어요. state="+state);return null; }
        if(busy())return null;
        switch(state) {
            case HOME -> { if(f.home() && f.ad()!=null && !f.history() && f.waiting()==null && f.complete()==null) return decide(Action.AD,f.ad(),1); }
            case AD_ENTRY -> {
                if(!f.home() && !f.history() && (f.waiting()!=null || f.complete()!=null)) {
                    success(0,"광고 화면 진입");move(State.REWARD,now);
                    if(f.waiting()==null && f.complete()!=null)return decide(Action.BACK_REWARD,null,0);
                } else if(f.home() && now-lastTap>=1000) {
                    if(attempts>=3)pause("광고 화면 진입을 3회 확인하지 못했어요.");
                    else if(f.ad()!=null)return decide(Action.AD,f.ad(),attempts+1);
                }
            }
            case REWARD -> { if(!f.home() && !f.history() && f.waiting()==null && f.complete()!=null)return decide(Action.BACK_REWARD,null,0); }
            case WAIT_FOR_POINTS -> {
                if(pointsSurface(f)) { if(actions[1]<actions[0])success(1,"적립 후 복귀 / 내 포인트 발견");return decide(Action.POINTS,f.points(),1); }
            }
            case POINTS_ENTRY -> {
                if(!f.home() && f.history()) {
                    success(2,"포인트 내역 실제 진입");move(State.HISTORY,now);
                    return decide(Action.BACK_HISTORY,null,0);
                } else if(pointsSurface(f) && now-lastPointsTap>=1000) {
                    if(pointsAttempts>=3)pause("내 포인트를 열지 못했어요.");
                    else { log.accept("내 포인트 재탐색 "+(pointsAttempts+1)+"/3");return decide(Action.POINTS,f.points(),pointsAttempts+1); }
                }
            }
            case HISTORY -> { if(!f.home() && f.history())return decide(Action.BACK_HISTORY,null,0); }
            case HOME_AFTER_HISTORY -> {
                if(pointsSurface(f)) {
                    success(3,"포인트 내역에서 홈 복귀");completed++;cycleId++;actionEpoch++;attempts=pointsAttempts=0;
                    if(limit>0 && completed>=limit) { stop();reason="완료했어요."; }
                    else move(State.HOME,now);
                }
            }
            default -> { }
        }
        return null;
    }
    public void submitted(Decision d,boolean accepted,long now) {
        if(!current(d) || reserved!=d.id)return;
        reserved=0;lastAction=d.action+" attempt="+d.attempt+" accepted="+accepted;log.accept("동작 요청: "+lastAction);
        switch(d.action) {
            case AD -> { attempts=d.attempt;lastTap=now;if(state==State.HOME)move(State.AD_ENTRY,now); }
            case POINTS -> { pointsAttempts=d.attempt;lastPointsTap=now;if(state==State.WAIT_FOR_POINTS)move(State.POINTS_ENTRY,now); }
            case BACK_REWARD -> { if(accepted)move(State.WAIT_FOR_POINTS,now);else pause("뒤로가기를 요청하지 못했어요."); }
            case BACK_HISTORY -> { if(accepted)move(State.HOME_AFTER_HISTORY,now);else pause("뒤로가기를 요청하지 못했어요."); }
        }
    }
    public void gestureSubmitted(Decision d,boolean accepted,long now) {
        if(!current(d) || reserved!=d.id)return;
        submitted(d,accepted,now);
        if(accepted) { gesture=d.id;gestureSince=now; }
        log.accept("gesture "+(accepted?"submitted":"rejected"));
    }
    public void gestureResult(Decision d,boolean completed,long now) {
        if(!current(d) || gesture!=d.id)return;
        gesture=0;log.accept("gesture "+(completed?"completed":"cancelled")+" / 실제 다음 화면 확인 필요");
        // Cancellation is not success. A fresh observation can retry after the bounded response window.
    }
}

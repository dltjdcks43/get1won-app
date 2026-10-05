package app.get1won;

/** All transitions and effects share this monitor. No queued clicks or delayed BACKs. */
public final class Engine {
    public enum State { IDLE, WAIT_HOME, STEP_1_TAP_A, WAIT_DETAIL, WAIT_REWARD,
        STEP_2_BACK, WAIT_HOME_AFTER_STEP2, STEP_3_TAP_B, WAIT_SCREEN_AFTER_STEP3,
        STEP_4_BACK, WAIT_HOME_AFTER_STEP4, NEXT_CYCLE, PAUSED, ERROR }
    public interface Port { boolean act(int step, long epoch); void log(String message); }
    public record Frame(long epoch, long time, boolean home, boolean detail,
                        boolean reward, boolean waiting, boolean screenB) {}
    public volatile State state = State.IDLE;
    public volatile long epoch, cycleId, completed, rewardDetections, errors;
    public final long[] actions = new long[4];
    public String reason = "준비";
    private final Port port;
    private long entered, watermark, stableSince = -1;
    private long timeout = 15_000_000_000L;
    private int limit = 1, mask;
    private State resumeState;
    public Engine(Port port) { this.port = port; }
    public synchronized boolean active() { return state != State.IDLE && state != State.PAUSED && state != State.ERROR; }
    public synchronized long epoch() { return epoch; }
    public synchronized void start(long now, int repeats, int timeoutSeconds) {
        if (active()) return;
        epoch++; completed=0; cycleId++; mask=0;
        limit=repeats; timeout=timeoutSeconds==0 ? 0 : timeoutSeconds*1_000_000_000L;
        watermark=now; change(State.WAIT_HOME,now);
    }
    public synchronized void pause(String why) {
        if (state==State.PAUSED || state==State.IDLE || state==State.ERROR) return;
        resumeState=state; epoch++; state=State.PAUSED; reason=why; stableSince=-1; port.log("일시정지: "+why);
    }
    public synchronized void resume(long now) {
        if(state!=State.PAUSED || resumeState==null)return;
        epoch++; watermark=now; change(resumeState,now);
    }
    public synchronized void stop() {
        epoch++; state=State.IDLE; resumeState=null; stableSince=-1; reason="중지"; port.log("중지");
    }
    public synchronized void fail(String why) {
        errors++; epoch++; state=State.ERROR; reason=why; stableSince=-1; port.log("오류: "+why);
    }
    public synchronized void tick(long now) {
        if(active() && timeout>0 && now-entered>=timeout) { errors++; pause("시간 초과 — 화면을 확인한 후 재개하세요"); }
    }
    private void change(State next,long now) {
        state=next; entered=now; stableSince=-1; reason=next.name(); port.log("사이클 "+cycleId+" "+next);
    }
    private boolean stable(boolean condition, long now, long duration) {
        if(!condition){stableSince=-1;return false;}
        if(stableSince<0)stableSince=now;
        return now-stableSince>=duration;
    }
    private void act(int step,State action,State after,long now) {
        int bit=1<<(step-1);
        if((mask&bit)!=0){fail("중복 동작 차단: "+step);return;}
        mask|=bit; change(action,now);
        // Commit the state before the platform call. Failure never retries an action.
        if(!port.act(step,epoch)){fail(step+"번 실행 실패");return;}
        actions[step-1]++; watermark=now; change(after,now);
    }
    public synchronized void frame(Frame f) {
        if(!active() || f.epoch!=epoch || f.time<=watermark)return;
        // A timeout can only pause; it cannot authorize any action.
        tick(f.time); if(!active())return;
        boolean home=f.home && !f.detail && !f.screenB && !f.reward && !f.waiting;
        switch(state) {
            case WAIT_HOME, WAIT_HOME_AFTER_STEP4 -> {
                if(stable(home,f.time,350_000_000L)) {
                    if(state==State.WAIT_HOME_AFTER_STEP4) {
                        completed++; port.log("사이클 "+cycleId+" 완료");
                        if(limit>0 && completed>=limit){stop();return;}
                        change(State.NEXT_CYCLE,f.time); cycleId++; mask=0;
                    }
                    act(1,State.STEP_1_TAP_A,State.WAIT_DETAIL,f.time);
                }
            }
            case WAIT_DETAIL, WAIT_REWARD -> {
                if(f.home || f.screenB)return;
                if(state==State.WAIT_DETAIL && f.detail)change(State.WAIT_REWARD,f.time);
                if(state==State.WAIT_REWARD && f.detail && f.reward && !f.waiting) {
                    rewardDetections++; port.log("1원 받았어요 감지");
                    // First valid frame: synchronous call, NO delay and NO multi-frame vote.
                    act(2,State.STEP_2_BACK,State.WAIT_HOME_AFTER_STEP2,f.time);
                }
            }
            case WAIT_HOME_AFTER_STEP2 -> {
                if(stable(home,f.time,350_000_000L))act(3,State.STEP_3_TAP_B,State.WAIT_SCREEN_AFTER_STEP3,f.time);
            }
            case WAIT_SCREEN_AFTER_STEP3 -> {
                // Step 4 has its own explicit B-screen gate, never a timeout fallback.
                if(stable(f.screenB && !f.home && !f.detail && !f.waiting && !f.reward,f.time,500_000_000L))
                    act(4,State.STEP_4_BACK,State.WAIT_HOME_AFTER_STEP4,f.time);
            }
            default -> { }
        }
    }
}

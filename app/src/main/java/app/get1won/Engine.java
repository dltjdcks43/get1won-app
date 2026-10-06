package app.get1won;

/** Serialized frame -> state -> platform effect. No delayed automation actions. */
public final class Engine {
    public enum State { IDLE, STEP1_TAP, WAIT_COMPLETION, STEP2_BACK,
        WAIT_STABLE_AFTER_STEP2, STEP3_TAP, WAIT_STABLE_AFTER_STEP3,
        STEP4_BACK, WAIT_STABLE_AFTER_STEP4, NEXT_CYCLE, PAUSED, ERROR }
    public interface Port { boolean act(int step, long generation); void log(String message); }
    public record Frame(long generation, long time, boolean completion, boolean waiting, float[] screen) {}
    public volatile State state=State.IDLE;
    public volatile long generation, cycleId, completed, detections, errors;
    public final long[] actions=new long[4];
    public volatile String reason="준비";
    private final Port port;
    private final ScreenStability stability=new ScreenStability();
    private long entered,watermark,lastFrame;
    private int limit,mask;
    private State resumeState;
    public Engine(Port port){this.port=port;}
    public synchronized boolean active(){return state!=State.IDLE && state!=State.PAUSED && state!=State.ERROR;}
    public synchronized long generation(){return generation;}
    public synchronized void start(long now,int repeats){
        generation++;cycleId++;completed=0;mask=0;limit=repeats;resumeState=null;
        watermark=lastFrame=now;stability.clear();change(State.STEP1_TAP,now);
    }
    public synchronized void pause(String why){
        generation++;
        if(active()){resumeState=state;state=State.PAUSED;}
        stability.resetQuiet();reason=why;port.log("PAUSE "+why);
    }
    public synchronized void resume(long now){
        if(state!=State.PAUSED || resumeState==null)return;
        generation++;watermark=lastFrame=now;stability.resetQuiet();change(resumeState,now);
    }
    public synchronized void stop(){generation++;state=State.IDLE;resumeState=null;stability.clear();reason="중지";port.log("STOP");}
    public synchronized void settingsChanged(){stop();reason="설정 변경 — 시작을 다시 누르세요";}
    public synchronized void fail(String why){generation++;errors++;state=State.ERROR;resumeState=null;stability.clear();reason=why;port.log("ERROR "+why);}
    public synchronized boolean transitionState(){return state==State.WAIT_STABLE_AFTER_STEP2 || state==State.WAIT_STABLE_AFTER_STEP3 || state==State.WAIT_STABLE_AFTER_STEP4;}
    public synchronized void tick(long now){
        if(!active())return;
        long deadline=transitionState()?2_000_000_000L:30_000_000_000L;
        if(now-entered>=deadline){errors++;pause(transitionState()?"화면 전환 확인 시간 초과":"완료 표시 대기 시간 초과");}
    }
    private void change(State next,long now){state=next;entered=now;reason=label(next);port.log("cycle "+cycleId+" "+next);}
    private void act(int step,State action,State after,Frame frame){
        int bit=1<<(step-1);
        if((mask&bit)!=0){fail("중복 동작 차단");return;}
        mask|=bit;change(action,frame.time);
        if(!port.act(step,generation)){fail(step+"번 요청 실패");return;}
        actions[step-1]++;watermark=frame.time;
        if(step>=2)stability.begin(frame.screen);
        change(after,frame.time);
    }
    public synchronized void frame(Frame f){
        if(!active() || f.generation!=generation || f.time<=watermark || f.time<=lastFrame)return;
        lastFrame=f.time;tick(f.time);if(!active())return;
        switch(state){
            case STEP1_TAP -> {if(!f.completion && !f.waiting)act(1,State.STEP1_TAP,State.WAIT_COMPLETION,f);}
            case WAIT_COMPLETION -> {
                if(f.completion && !f.waiting){
                    detections++;
                    // First valid positive frame invokes BACK in this same call stack.
                    act(2,State.STEP2_BACK,State.WAIT_STABLE_AFTER_STEP2,f);
                }
            }
            case WAIT_STABLE_AFTER_STEP2, WAIT_STABLE_AFTER_STEP3, WAIT_STABLE_AFTER_STEP4 -> {
                if(f.waiting || f.completion){stability.resetQuiet();return;}
                if(!stability.accept(f.screen,f.time))return;
                port.log("화면 변화 및 250ms 안정 확인");
                if(state==State.WAIT_STABLE_AFTER_STEP2)act(3,State.STEP3_TAP,State.WAIT_STABLE_AFTER_STEP3,f);
                else if(state==State.WAIT_STABLE_AFTER_STEP3)act(4,State.STEP4_BACK,State.WAIT_STABLE_AFTER_STEP4,f);
                else {
                    completed++;port.log("cycle "+cycleId+" 완료");
                    if(limit>0 && completed>=limit){stop();return;}
                    change(State.NEXT_CYCLE,f.time);cycleId++;mask=0;
                    act(1,State.STEP1_TAP,State.WAIT_COMPLETION,f);
                }
            }
            default -> { }
        }
    }
    public static String label(State s){return switch(s){
        case IDLE -> "사용할 준비가 됐어요";
        case STEP1_TAP -> "처음 위치를 누르고 있어요";
        case WAIT_COMPLETION -> "완료 화면을 기다리고 있어요";
        case STEP2_BACK -> "이전 화면으로 돌아가요";
        case WAIT_STABLE_AFTER_STEP2,WAIT_STABLE_AFTER_STEP3,WAIT_STABLE_AFTER_STEP4 -> "잠시 기다려주세요";
        case STEP3_TAP -> "포인트 화면을 열고 있어요";
        case STEP4_BACK -> "이전 화면으로 돌아가요";
        case NEXT_CYCLE -> "다음 반복을 준비하고 있어요";
        case PAUSED -> "잠시 멈췄어요";
        case ERROR -> "설정을 다시 확인해주세요";
    };}
}

package app.get1won;

/** Decisions are serialized; platform calls happen outside this monitor. */
public final class Engine {
    public enum State { IDLE, WAIT_HOME, FIND_REWARD_AD, OPEN_REWARD_AD, WAIT_REWARD_COMPLETE,
        BACK_FROM_REWARD, WAIT_HOME_AFTER_REWARD, FIND_MY_POINTS, OPEN_MY_POINTS,
        WAIT_POINTS_HISTORY, BACK_FROM_POINTS, WAIT_HOME_AFTER_POINTS, NEXT_CYCLE, PAUSED, ERROR }
    public interface Port {void log(String message);}
    public record Frame(long generation,long time,String pkg,Semantic.Found found,boolean definitive){}
    public record Effect(int step,long generation,long cycleId,long time,Semantic.Node target){}
    public volatile State state=State.IDLE;
    public volatile long generation,cycleId,completed,detections,errors;
    public final long[] actions=new long[4];
    public volatile String reason="준비됐어요",targetPackage="";
    private final Port port; private long entered,watermark,lastFrame; private int limit,mask;
    private Effect pending; private long decisionEntered,adRequestedAt;
    public volatile int adAttempts;private boolean adAccepted;
    public Engine(Port port){this.port=port;}
    public synchronized boolean active(){return state!=State.IDLE && state!=State.PAUSED && state!=State.ERROR;}
    public synchronized long generation(){return generation;}
    public synchronized void start(long now,int repeats,String pkg){
        generation++;cycleId++;completed=0;mask=0;pending=null;limit=repeats;targetPackage=pkg;
        adAttempts=0;adAccepted=false;watermark=lastFrame=now;change(State.WAIT_HOME,now);
    }
    public synchronized void pause(String why){generation++;pending=null;state=State.PAUSED;reason=why;port.log("PAUSE "+why);}
    public synchronized void stop(){generation++;pending=null;state=State.IDLE;reason="준비됐어요";targetPackage="";port.log("STOP");}
    public synchronized void settingsChanged(){stop();reason="설정을 바꿨어요. 대상 화면에서 시작해주세요.";}
    public synchronized void fail(String why){pause(why);errors++;state=State.ERROR;}
    public synchronized void tick(long now){
        if(!active())return;
        long timeout=30_000_000_000L;
        if(now-entered>=timeout){errors++;pause(state==State.OPEN_REWARD_AD?"광고를 열지 못했어요. 다시 시작해주세요.":state==State.WAIT_REWARD_COMPLETE?"완료 화면을 찾지 못했어요.":"포인트 화면을 찾지 못했어요. 처음 화면으로 돌아가 주세요.");}
    }
    private void change(State next,long now){state=next;entered=now;reason=label(next);port.log("cycle "+cycleId+" "+next);}
    private Effect reserve(int step,State action,Frame f,Semantic.Node target){
        int bit=1<<(step-1);if((mask&bit)!=0){fail("중복 동작을 막고 멈췄어요.");return null;}
        mask|=bit;change(action,f.time);pending=new Effect(step,generation,cycleId,f.time,target);return pending;
    }
    public synchronized boolean valid(Effect e){return e!=null && pending==e && active() && generation==e.generation && cycleId==e.cycleId;}
    /** No platform request was issued: discard stale evidence and reobserve the same stage. */
    public synchronized void abandon(Effect e){
        if(!valid(e))return;pending=null;mask&=~(1<<(e.step-1));
        state=switch(e.step){case 1->adAttempts>0?State.OPEN_REWARD_AD:State.WAIT_HOME;case 2->State.WAIT_REWARD_COMPLETE;case 3->State.WAIT_HOME_AFTER_REWARD;default->State.WAIT_POINTS_HISTORY;};
        entered=decisionEntered;reason=label(state);port.log("stale evidence discarded before step "+e.step);
    }
    public synchronized void acknowledge(Effect e,boolean success,long now){
        if(!valid(e))return;
        pending=null;
        if(e.step==1){adAttempts++;adAccepted|=success;adRequestedAt=now;watermark=now;port.log("STEP1 request "+(success?"accepted":"rejected")+" attempt="+adAttempts);return;}
        if(!success){fail("화면을 누르지 못했어요. 처음 화면에서 다시 시작해주세요.");return;}
        actions[e.step-1]++;watermark=now;
        change(switch(e.step){case 1->State.WAIT_REWARD_COMPLETE;case 2->State.WAIT_HOME_AFTER_REWARD;case 3->State.WAIT_POINTS_HISTORY;default->State.WAIT_HOME_AFTER_POINTS;},now);
    }
    public synchronized Effect frame(Frame f){
        if(!active() || f.generation!=generation || f.time<=watermark || f.time<=lastFrame)return null;
        lastFrame=f.time;
        if(!targetPackage.equals(f.pkg)){pause("다른 앱으로 이동해서 잠시 멈췄어요.");return null;}
        tick(f.time);if(!active() || pending!=null)return null;
        decisionEntered=entered;
        Semantic.Found s=f.found;
        switch(state){
            case WAIT_HOME -> {
                if(!s.home()){if(f.definitive)pause("시작할 화면을 찾지 못했어요. 포인트 화면을 열고 다시 시작해주세요.");return null;}
                return findAd(f);
            }
            case OPEN_REWARD_AD -> {
                if(adAccepted && !s.home() && !s.history() && (s.waiting()!=null || s.complete()!=null)){
                    actions[0]++;port.log("STEP1 screen transition confirmed attempt="+adAttempts);change(State.WAIT_REWARD_COMPLETE,f.time);
                    if(s.complete()!=null && s.waiting()==null){detections++;return reserve(2,State.BACK_FROM_REWARD,f,null);}
                }else if(s.home() && f.time-adRequestedAt>=500_000_000L){
                    if(adAttempts>=3){pause("광고를 열지 못했어요. 다시 시작해주세요.");return null;}
                    if(s.ad()==null)return null;
                    port.log("STEP1 no transition, retry "+(adAttempts+1));mask|=1;
                    pending=new Effect(1,generation,cycleId,f.time,s.ad());return pending;
                }
            }
            case WAIT_REWARD_COMPLETE -> {
                if(s.complete()!=null && s.waiting()==null && !s.history() && !s.home()){
                    detections++;return reserve(2,State.BACK_FROM_REWARD,f,null);
                }
            }
            case WAIT_HOME_AFTER_REWARD -> {
                if(s.home()){
                    change(State.FIND_MY_POINTS,f.time);
                    if(s.pointsTarget()==null){pause("내 포인트를 찾지 못했어요.");return null;}
                    return reserve(3,State.OPEN_MY_POINTS,f,s.pointsTarget());
                }
            }
            case WAIT_POINTS_HISTORY -> {if(s.history() && s.points()==null && s.anchor()==null && !s.reward())return reserve(4,State.BACK_FROM_POINTS,f,null);}
            case WAIT_HOME_AFTER_POINTS -> {
                if(s.home()){
                    completed++;port.log("cycle "+cycleId+" 완료");
                    if(limit>0 && completed>=limit){stop();return null;}
                    change(State.NEXT_CYCLE,f.time);cycleId++;mask=0;return findAd(f);
                }
            }
            default -> {}
        }
        return null;
    }
    private Effect findAd(Frame f){adAttempts=0;adAccepted=false;change(State.FIND_REWARD_AD,f.time);if(f.found.ad()==null){pause("광고 버튼을 찾지 못했어요.");return null;}return reserve(1,State.OPEN_REWARD_AD,f,f.found.ad());}
    public static String label(State s){return switch(s){
        case IDLE->"준비됐어요";case WAIT_REWARD_COMPLETE->"완료될 때까지 기다리고 있어요";
        case OPEN_REWARD_AD,FIND_REWARD_AD->"광고를 열고 있어요";
        case OPEN_MY_POINTS,FIND_MY_POINTS->"내 포인트를 열고 있어요";
        case PAUSED->"잠시 멈췄어요";case ERROR->"처음 화면에서 다시 시작해주세요";
        default->"포인트 화면을 확인하고 있어요";
    };}
}

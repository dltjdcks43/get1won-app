package app.get1won;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class EngineTest {
    static class Rig implements Engine.Port {
        Engine engine=new Engine(this);List<Integer> effects=new ArrayList<>();long time=1_000_000_000L;boolean fail;
        public boolean act(int step,long epoch){effects.add(step);return !fail;}
        public void log(String s){}
        void start(int limit,int timeout){engine.start(time,limit,timeout);}
        void f(long ms,boolean h,boolean d,boolean r,boolean w,boolean b){time+=ms*1_000_000;engine.frame(new Engine.Frame(engine.epoch(),time,h,d,r,w,b));}
        void home(){f(20,true,false,false,false,false);f(360,true,false,false,false,false);}
        void reward(){f(20,false,true,true,false,false);}
        void waiting(long ms){f(ms,false,true,false,true,false);}
        void b(){f(20,false,false,false,false,true);f(510,false,false,false,false,true);}
    }
    @Test public void fiftyCyclesForEveryRewardDelay(){
        for(int delay:new int[]{3000,3500,5000,7000}) {
            Rig r=new Rig();r.start(50,15);r.home();
            for(int cycle=0;cycle<50;cycle++){
                r.waiting(delay);assertEquals(cycle*4+1,r.effects.size());
                r.reward();assertEquals(cycle*4+2,r.effects.size());
                r.reward();assertEquals(cycle*4+2,r.effects.size());
                r.home();assertEquals(cycle*4+3,r.effects.size());
                r.b();assertEquals(cycle*4+4,r.effects.size());r.home();
            }
            assertEquals(50,r.engine.completed);assertEquals(Engine.State.IDLE,r.engine.state);
            assertEquals(200,r.effects.size());for(int i=0;i<200;i++)assertEquals(i%4+1,(int)r.effects.get(i));
        }
    }
    @Test public void waitingForeverNeverBack(){Rig r=new Rig();r.start(0,0);r.home();for(int i=0;i<100;i++)r.waiting(1000);assertEquals(List.of(1),r.effects);assertEquals(Engine.State.WAIT_REWARD,r.engine.state);}
    @Test public void timeoutOnlyPausesEvenIfRewardArrivesLate(){Rig r=new Rig();r.start(1,10);r.home();r.waiting(1);r.waiting(10_001);assertEquals(Engine.State.PAUSED,r.engine.state);r.reward();r.home();r.b();assertEquals(List.of(1),r.effects);}
    @Test public void firstRewardFrameCallsBackSynchronously(){Rig r=new Rig();r.start(1,15);r.home();r.reward();assertEquals(List.of(1,2),r.effects);assertEquals(Engine.State.WAIT_HOME_AFTER_STEP2,r.engine.state);}
    @Test public void bothLabelsAlwaysBlockBack(){Rig r=new Rig();r.start(1,0);r.home();for(int i=0;i<50;i++)r.f(100,false,true,true,true,false);assertEquals(List.of(1),r.effects);}
    @Test public void unknownScreenCannotAuthorizeBOrBack(){Rig r=new Rig();r.start(1,0);r.home();r.reward();for(int i=0;i<20;i++)r.f(1000,false,false,false,false,false);assertEquals(List.of(1,2),r.effects);r.home();for(int i=0;i<20;i++)r.f(1000,false,false,false,false,false);assertEquals(List.of(1,2,3),r.effects);}
    @Test public void bTransitionMustRemainStable(){Rig r=new Rig();r.start(1,15);r.home();r.reward();r.home();r.f(10,false,false,false,false,true);r.f(300,false,false,false,false,false);r.f(300,false,false,false,false,true);assertEquals(List.of(1,2,3),r.effects);r.f(501,false,false,false,false,true);assertEquals(List.of(1,2,3,4),r.effects);}
    @Test public void oldEpochCannotActAfterPauseResume(){Rig r=new Rig();r.start(1,15);r.home();long old=r.engine.epoch();r.engine.pause("test");r.engine.resume(r.time+1);r.engine.frame(new Engine.Frame(old,r.time+20_000_000,false,true,true,false,false));assertEquals(List.of(1),r.effects);r.reward();assertEquals(List.of(1,2),r.effects);}
    @Test public void noEffectsAfterStop(){Rig r=new Rig();r.start(1,15);r.home();r.engine.stop();r.reward();r.home();r.b();assertEquals(List.of(1),r.effects);}
    @Test public void noQueuedHomeTimerAfterPause(){Rig r=new Rig();r.start(1,15);r.home();r.reward();r.f(20,true,false,false,false,false);r.engine.pause("user");r.home();assertEquals(List.of(1,2),r.effects);}
    @Test public void failedActionIsNeverRetried(){Rig r=new Rig();r.start(1,15);r.fail=true;r.home();r.home();assertEquals(List.of(1),r.effects);assertEquals(Engine.State.ERROR,r.engine.state);}
    @Test public void rewardOnHomeIsNotValid(){Rig r=new Rig();r.start(1,15);r.home();r.f(50,true,true,true,false,false);assertEquals(List.of(1),r.effects);}
    @Test public void waitingOnBPreventsStep4(){Rig r=new Rig();r.start(1,0);r.home();r.reward();r.home();r.f(10,false,false,false,true,true);r.f(1000,false,false,false,true,true);assertEquals(List.of(1,2,3),r.effects);}
    @Test public void oldTimestampCannotCompleteNewCycle(){Rig r=new Rig();r.start(1,15);r.home();r.engine.frame(new Engine.Frame(r.engine.epoch(),1,false,true,true,false,false));assertEquals(List.of(1),r.effects);}
    @Test public void watchdogPausesWhenFramesStop(){Rig r=new Rig();r.start(1,10);r.home();r.engine.tick(r.time+10_000_000_001L);assertEquals(Engine.State.PAUSED,r.engine.state);assertEquals(List.of(1),r.effects);}
}

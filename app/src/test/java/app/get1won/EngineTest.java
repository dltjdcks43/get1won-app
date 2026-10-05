package app.get1won;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class EngineTest {
    static class Rig implements Engine.Port {
        final Engine engine=new Engine(this);final List<Integer> actions=new ArrayList<>();
        final float[] home=scene(.15f),detail=scene(.7f),b=scene(.4f),motion=scene(.9f);
        long time=1_000_000_000L;int frameId,backFrame=-1;boolean accepted=true;
        public boolean act(int step,long generation){actions.add(step);if(step==2)backFrame=frameId;return accepted;}
        public void log(String s){}
        static float[] scene(float value){float[] s=new float[ScreenStability.SIZE];Arrays.fill(s,value);return s;}
        void start(int repeats){engine.start(time,repeats);frame(17,false,false,home);}
        void frame(long ms,boolean complete,boolean waiting,float[] image){time+=ms*1_000_000;frameId++;engine.frame(new Engine.Frame(engine.generation(),time,complete,waiting,image));}
        void waiting(long ms){while(ms>0){long delta=Math.min(ms,33);frame(delta,false,true,detail);ms-=delta;}}
        void complete(){frame(17,true,false,detail);}
        void transition(float[] destination,int duration,Random random){
            int prior=actions.size(),elapsed=0;
            while(elapsed<duration){int dt=Math.min(duration-elapsed,10+random.nextInt(31));elapsed+=dt;Arrays.fill(motion,(frameId%2==0)?.92f:.2f);frame(dt,false,false,motion);assertEquals("action during transition",prior,actions.size());}
            frame(17,false,false,destination);
            for(int i=0;i<7;i++){frame(33,false,false,destination);assertEquals(prior,actions.size());}
            frame(33,false,false,destination);
        }
    }
    @Test public void randomizedFiftyAndHundredCyclesPreserveEveryGate(){
        for(int count:new int[]{50,100}){
            Rig r=new Rig();r.start(count);Random random=new Random(725+count);
            for(int cycle=0;cycle<count;cycle++){
                r.waiting(5500+random.nextInt(2001));assertEquals(cycle*4+1,r.actions.size());
                r.complete();assertEquals(cycle*4+2,r.actions.size());assertEquals(r.frameId,r.backFrame);
                r.transition(r.home,300+random.nextInt(601),random);assertEquals(cycle*4+3,r.actions.size());
                r.transition(r.b,300+random.nextInt(601),random);assertEquals(cycle*4+4,r.actions.size());
                r.transition(r.home,300+random.nextInt(601),random);
            }
            assertEquals(count,r.engine.completed);assertEquals(count*4,r.actions.size());assertEquals(Engine.State.IDLE,r.engine.state);
            for(int n=0;n<r.actions.size();n++)assertEquals(n%4+1,(int)r.actions.get(n));
        }
    }
    @Test public void everyRequestedDisplayDelayNeverBecomesBackTimer(){for(int delay:new int[]{3000,3500,5000,6100,7000,10000}){Rig r=new Rig();r.start(1);r.waiting(delay);assertEquals(List.of(1),r.actions);r.complete();assertEquals(List.of(1,2),r.actions);assertEquals(r.frameId,r.backFrame);}}
    @Test public void allTransitionDurationsRequireActualStability(){for(int duration:new int[]{250,400,500,700,1000}){Rig r=new Rig();r.start(1);r.complete();Random random=new Random(duration);r.transition(r.home,duration,random);r.transition(r.b,duration,random);r.transition(r.home,duration,random);assertEquals(List.of(1,2,3,4),r.actions);assertEquals(1,r.engine.completed);}}
    @Test public void completionNeverAppearsOnlyPauses(){Rig r=new Rig();r.start(1);r.waiting(30000);assertEquals(List.of(1),r.actions);assertEquals(Engine.State.PAUSED,r.engine.state);r.complete();assertEquals(List.of(1),r.actions);}
    @Test public void persistentCompletionCannotDuplicateBack(){Rig r=new Rig();r.start(1);r.complete();for(int n=0;n<30;n++)r.complete();assertEquals(List.of(1,2),r.actions);}
    @Test public void simultaneousWaitingBlocksPositiveMatch(){Rig r=new Rig();r.start(1);for(int n=0;n<30;n++)r.frame(33,true,true,r.detail);assertEquals(List.of(1),r.actions);}
    @Test public void unchangedOldScreenIsNotACompletedTransition(){Rig r=new Rig();r.start(1);r.complete();for(int n=0;n<70;n++)r.frame(33,false,false,r.detail);assertEquals(List.of(1,2),r.actions);assertEquals(Engine.State.PAUSED,r.engine.state);}
    @Test public void endlessAnimationPausesWithoutFallback(){Rig r=new Rig();r.start(1);r.complete();for(int n=0;n<70;n++)r.frame(33,false,false,n%2==0?r.home:r.b);assertEquals(List.of(1,2),r.actions);assertEquals(Engine.State.PAUSED,r.engine.state);}
    @Test public void pausedAndResumedFramesNeedNewGenerationAndQuietWindow(){Rig r=new Rig();r.start(1);r.complete();r.frame(17,false,false,r.home);r.frame(200,false,false,r.home);long old=r.engine.generation();r.engine.pause("user");r.time+=1_000_000;r.engine.resume(r.time);r.engine.frame(new Engine.Frame(old,r.time+500_000_000,false,false,r.home));assertEquals(List.of(1,2),r.actions);for(int n=0;n<8;n++)r.frame(33,false,false,r.home);assertEquals(List.of(1,2),r.actions);r.frame(33,false,false,r.home);assertEquals(List.of(1,2,3),r.actions);}
    @Test public void stopInvalidatesAllRemainingFrames(){Rig r=new Rig();r.start(1);r.complete();r.engine.stop();for(int n=0;n<100;n++)r.frame(33,false,false,n%2==0?r.home:r.b);assertEquals(List.of(1,2),r.actions);assertEquals(Engine.State.IDLE,r.engine.state);}
    @Test public void settingsInvalidateEvenWhileIdleOrPaused(){Rig r=new Rig();long g=r.engine.generation();r.engine.settingsChanged();assertTrue(r.engine.generation()>g);r.start(1);r.engine.pause("user");g=r.engine.generation();r.engine.settingsChanged();r.complete();assertTrue(r.engine.generation()>g);assertEquals(List.of(1),r.actions);}
    @Test public void restartDropsOldCompletion(){Rig r=new Rig();r.start(1);long g=r.engine.generation();r.engine.start(++r.time,1);r.engine.frame(new Engine.Frame(g,r.time+1000,true,false,r.detail));assertEquals(List.of(1),r.actions);}
    @Test public void failedPlatformActionNeverRetries(){Rig r=new Rig();r.accepted=false;r.start(1);r.frame(200,false,false,r.home);assertEquals(Engine.State.ERROR,r.engine.state);assertEquals(List.of(1),r.actions);}
    @Test public void missingFramesCannotFinishTransition(){Rig r=new Rig();r.start(1);r.complete();r.engine.tick(r.time+2_000_000_000L);assertEquals(Engine.State.PAUSED,r.engine.state);assertEquals(List.of(1,2),r.actions);}
    @Test public void staleTimestampRejected(){Rig r=new Rig();r.start(1);r.engine.frame(new Engine.Frame(r.engine.generation(),r.time,true,false,r.detail));assertEquals(List.of(1),r.actions);}
    @Test public void waitingOnBBlocksStep4(){Rig r=new Rig();r.start(1);r.complete();r.transition(r.home,400,new Random(1));for(int n=0;n<20;n++)r.frame(33,false,true,r.b);assertEquals(List.of(1,2,3),r.actions);}
}

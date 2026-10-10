package app.get1won;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class Beta18RegressionTest {
    private static Semantic.Node n(int id,String text,int t,int b){return new Semantic.Node(id,-1,text,new Semantic.Box(90,t,1353,b),true,true,"Accessibility");}
    private static final Semantic.Node POINTS=n(1,"내포인트6510원내역보기",773,893),ANCHOR=n(2,"여기서 혜택 구경하고 1원 받아요",1771,1872),AD=n(3,"지금 3초 서명하고 살펴보세요",1943,2116);
    private static Semantic.Found home(boolean waiting,boolean complete,boolean history){return new Semantic.Found(POINTS,ANCHOR,AD,waiting?AD:null,complete?AD:null,history);}
    private static Engine e(Engine.State state,List<String> logs){var e=new Engine(logs::add);e.start(0,0);e.state=state;return e;}
    @Test public void ordinaryStrongHomeRetainsPointsActionWithoutOverrideLog(){var logs=new ArrayList<String>();var e=e(Engine.State.WAIT_FOR_POINTS,logs);assertEquals(Engine.Action.POINTS,e.observe(home(false,false,false),1).action());assertFalse(logs.stream().anyMatch(s->s.contains("strong_home_override")));}
    @Test public void actualAnchorAndThreeSecondCardReproduceWaitingButAllowPoints() {
        var f=Semantic.inspect(new Semantic.Scene(List.of(POINTS,ANCHOR,AD),new Semantic.Box(0,0,1440,3120)));
        assertNotNull(f.points());assertNotNull(f.anchor());assertNotNull(f.ad());assertNotNull(f.waiting());assertFalse(f.history());
        var logs=new ArrayList<String>();var e=e(Engine.State.WAIT_FOR_POINTS,logs);
        var d=e.observe(f,1);assertEquals(Engine.Action.POINTS,d.action());
        // Overlay/window deferrals may re-observe the same state many times; log once.
        for(int i=2;i<12;i++){e.defer(d);d=e.observe(f,i);assertNotNull(d);}
        assertEquals(1,logs.stream().filter(s->s.equals("WAIT_FOR_POINTS strong_home_override waiting=true complete=false")).count());
    }
    @Test public void completeFalsePositiveAllowsStrongHome(){assertEquals(Engine.Action.POINTS,e(Engine.State.WAIT_FOR_POINTS,new ArrayList<>()).observe(home(false,true,false),1).action());}
    @Test public void rewardWaitingNeverActsAsPointsOrBack() {
        var f=new Semantic.Found(null,null,null,AD,null,false);
        for(var state:List.of(Engine.State.WAIT_FOR_POINTS,Engine.State.HOME_AFTER_HISTORY,Engine.State.REWARD))assertNull(e(state,new ArrayList<>()).observe(f,1));
        var both=new Semantic.Found(null,null,null,AD,AD,false);assertNull(e(Engine.State.REWARD,new ArrayList<>()).observe(both,1));
    }
    @Test public void realRewardCompletionKeepsBackReward(){var f=new Semantic.Found(null,null,null,null,AD,false);assertEquals(Engine.Action.BACK_REWARD,e(Engine.State.REWARD,new ArrayList<>()).observe(f,1).action());assertNull(e(Engine.State.WAIT_FOR_POINTS,new ArrayList<>()).observe(f,1));}
    @Test public void historyVetoesStrongHomeEvenWithPoints(){for(var state:List.of(Engine.State.WAIT_FOR_POINTS,Engine.State.HOME_AFTER_HISTORY))assertNull(e(state,new ArrayList<>()).observe(home(true,true,true),1));}
    @Test public void weakPointsSurfaceStillHonorsWaitingAndComplete() {
        for(boolean waiting:List.of(false,true)) {
            var f=new Semantic.Found(POINTS,ANCHOR,null,waiting?AD:null,waiting?null:AD,false);
            assertNull(e(Engine.State.WAIT_FOR_POINTS,new ArrayList<>()).observe(f,1));
            assertNull(e(Engine.State.WAIT_FOR_POINTS,new ArrayList<>()).observe(new Semantic.Found(POINTS,null,AD,f.waiting(),f.complete(),false),1));
        }
        assertEquals(Engine.Action.POINTS,e(Engine.State.WAIT_FOR_POINTS,new ArrayList<>()).observe(new Semantic.Found(POINTS,null,null,null,null,false),1).action());
    }
    @Test public void homeAfterHistoryCountsCycleAndLogsOnlyUsedOverride() {
        var logs=new ArrayList<String>();var e=e(Engine.State.HOME_AFTER_HISTORY,logs);assertNull(e.observe(home(true,false,false),1));
        assertEquals(1,e.completed);assertEquals(Engine.State.HOME,e.state);
        assertEquals(1,logs.stream().filter(s->s.contains("HOME_AFTER_HISTORY strong_home_override")).count());
    }
    @Test public void pointsRetryStillUsesFreshGestureAndHistoryConfirmation() {
        var e=e(Engine.State.WAIT_FOR_POINTS,new ArrayList<>());var first=e.observe(home(true,false,false),1);
        var screen=new Semantic.Box(0,0,1440,3120);
        assertEquals(PointsDispatch.Mode.NATIVE,PointsDispatch.plan(first.target(),true,screen,screen,true,true,false,first.attempt()).mode());
        e.submitted(first,true,2);var second=e.observe(home(true,false,false),1002);assertEquals(2,second.attempt());
        assertEquals(PointsDispatch.Mode.GESTURE,PointsDispatch.plan(second.target(),true,screen,screen,true,true,false,second.attempt()).mode());
        e.gestureSubmitted(second,true,1003);e.gestureResult(second,true,1004);assertEquals(0,e.actions[2]);
        assertEquals(Engine.Action.BACK_HISTORY,e.observe(new Semantic.Found(null,null,null,null,null,true),1005).action());assertEquals(1,e.actions[2]);
    }
}

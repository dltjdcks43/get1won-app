package app.get1won;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class EngineTest {
 private final Engine e=new Engine(s->{});private long now=1_000_000_000L;
 private Semantic.Found home(){return Semantic.inspect(SemanticTest.scene(SemanticTest.home()));}
 private Semantic.Found reward(boolean done,boolean waiting){return new Semantic.Found(null,null,done?SemanticTest.n(0,-1,"1원 받았어요",SemanticTest.b(0,0,100,50),false):null,waiting?SemanticTest.n(1,-1,"3초 구경해요",SemanticTest.b(0,60,100,100),false):null,null,null,null,null);}
 private Semantic.Found history(){return new Semantic.Found(null,null,null,null,SemanticTest.n(0,-1,"전체",SemanticTest.b(0,0,100,50),false),SemanticTest.n(1,-1,"광고 보고 1원 받기",SemanticTest.b(0,60,100,100),true),null,null);}
 private Engine.Effect frame(Semantic.Found f){now+=10_000_000L;return e.frame(new Engine.Frame(e.generation(),now,"target",f,true));}
 private void ack(Engine.Effect a,int step){assertNotNull(a);assertEquals(step,a.step());e.acknowledge(a,true,now);}
 private void start(){e.start(now,0,"target");ack(frame(home()),1);}
 @Test public void hundredSemanticCyclesHaveFourActions(){start();for(int i=0;i<100;i++){now+=3_000_000_000L;assertNull(frame(reward(false,true)));ack(frame(reward(true,false)),2);ack(frame(home()),3);ack(frame(history()),4);ack(frame(home()),1);}assertEquals(100,e.completed);assertArrayEquals(new long[]{101,100,100,100},e.actions);}
 @Test public void waitingAlwaysVetoesBack(){start();assertNull(frame(reward(true,true)));assertEquals(0,e.actions[1]);}
 @Test public void elapsedTimeNeverAuthorizesBack(){start();now+=10_000_000_000L;assertNull(frame(reward(false,true)));assertNull(frame(reward(false,false)));assertEquals(0,e.actions[1]);}
 @Test public void completionReservesBackOnSameFrame(){start();Engine.Effect a=frame(reward(true,false));assertEquals(2,a.step());assertEquals(now,a.time());}
 @Test public void timeoutOnlyPauses(){start();e.tick(now+31_000_000_000L);assertEquals(Engine.State.PAUSED,e.state);assertArrayEquals(new long[]{1,0,0,0},e.actions);}
 @Test public void repeatedCompletionCannotDuplicateBack(){start();var a=frame(reward(true,false));assertNull(frame(reward(true,false)));ack(a,2);assertNull(frame(reward(true,false)));assertEquals(1,e.actions[1]);}
 @Test public void wrongStableDestinationDoesNotAdvance(){start();ack(frame(reward(true,false)),2);for(int i=0;i<20;i++)assertNull(frame(history()));assertEquals(0,e.actions[2]);e.tick(now+11_000_000_000L);assertEquals(Engine.State.PAUSED,e.state);}
 @Test public void historyNeedsBothFeatures(){start();ack(frame(reward(true,false)),2);ack(frame(home()),3);assertNull(frame(reward(false,false)));assertEquals(0,e.actions[3]);}
 @Test public void startOnWrongScreenPausesWithoutClick(){e.start(now,1,"target");assertNull(frame(history()));assertEquals(Engine.State.PAUSED,e.state);assertEquals(0,e.actions[0]);}
 @Test public void foreignPackageInvalidatesReservedEffect(){start();var a=frame(reward(true,false));e.frame(new Engine.Frame(e.generation(),now+1,"foreign",home(),true));assertFalse(e.valid(a));e.acknowledge(a,true,now+2);assertEquals(0,e.actions[1]);}
 @Test public void stopDiscardsLateEffectAndFrame(){start();var a=frame(reward(true,false));long gen=e.generation();e.stop();assertFalse(e.valid(a));e.acknowledge(a,true,now+1);assertNull(e.frame(new Engine.Frame(gen,now+2,"target",home(),true)));assertEquals(0,e.actions[1]);}
 @Test public void settingsInvalidateOldGeneration(){start();var a=frame(reward(true,false));e.settingsChanged();assertFalse(e.valid(a));}
 @Test public void restartNeedsHomeAgain(){start();e.pause("pause");e.start(now,1,"target");assertNull(frame(reward(true,false)));assertEquals(Engine.State.PAUSED,e.state);assertEquals(0,e.actions[1]);}
 @Test public void staleTimestampCannotAct(){start();assertNull(e.frame(new Engine.Frame(e.generation(),now,"target",reward(true,false),true)));}
 @Test public void failedPlatformActionStopsChain(){start();var a=frame(reward(true,false));e.acknowledge(a,false,now);assertEquals(Engine.State.ERROR,e.state);assertNull(frame(home()));assertEquals(0,e.actions[2]);}
 @Test public void completionLimitWaitsForHomeReturn(){e.start(now,1,"target");ack(frame(home()),1);ack(frame(reward(true,false)),2);ack(frame(home()),3);ack(frame(history()),4);assertEquals(0,e.completed);assertNull(frame(home()));assertEquals(1,e.completed);assertEquals(Engine.State.IDLE,e.state);assertArrayEquals(new long[]{1,1,1,1},e.actions);}
}

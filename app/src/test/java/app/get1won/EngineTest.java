package app.get1won;
import org.junit.Test;
import static org.junit.Assert.*;

public class EngineTest {
    private final Engine engine=new Engine(s->{});
    private static Semantic.Found home() {return Semantic.inspect(SemanticTest.home("여기서 구경하면 1원 받아요","바뀌는 콘텐츠"));}
    private static Semantic.Found text(String... texts) {
        java.util.List<Semantic.Node> nodes=new java.util.ArrayList<>();int y=200;
        for(String text:texts){nodes.add(SemanticTest.access(text,y,y+60));y+=150;}
        return Semantic.inspect(new Semantic.Scene(nodes,new Semantic.Box(0,0,1080,2200)));
    }
    private void enter() {engine.start(3,0);var d=engine.observe(home(),1);engine.submitted(d,true,1);}
    @Test public void requestAcceptedDoesNotMeanAdEntered() {enter();assertEquals(0,engine.actions[0]);assertEquals(Engine.State.AD_ENTRY,engine.state);assertNull(engine.observe(home(),999));}
    @Test public void waitingConfirmsEntryButNeverBacksByTime() {enter();assertNull(engine.observe(text("3초 구경해요"),100));assertEquals(1,engine.actions[0]);assertNull(engine.observe(text("3초 구경해요"),15000));assertEquals(0,engine.actions[1]);}
    @Test public void completionBackIsSameObservation() {enter();var back=engine.observe(text("1원 받았어요"),100);assertEquals(Engine.Action.BACK_REWARD,back.action());assertEquals(1,engine.actions[0]);}
    @Test public void waitingAlwaysVetoesCompletion() {enter();assertNull(engine.observe(text("3초 구경해요","1원 받았어요"),100));assertNull(engine.observe(text("3초 구경해요","1원 받았어요"),20000));}
    @Test public void timeoutPausesWithoutAction() {enter();assertNull(engine.observe(text("3초 구경해요"),100));assertNull(engine.observe(text("3초 구경해요"),30101));assertEquals(Engine.State.PAUSED,engine.state);assertEquals(0,engine.actions[1]);}
    @Test public void retriesUseFreshTargetsAndStopAtThree() {
        enter();var fresh=Semantic.inspect(SemanticTest.home("한번 더 구경하고 1원 받아요","다른 광고"));
        var second=engine.observe(fresh,1001);assertSame(fresh.ad(),second.target());assertEquals(2,second.attempt());engine.submitted(second,false,1001);
        var third=engine.observe(home(),2001);assertEquals(3,third.attempt());engine.submitted(third,true,2001);
        assertNull(engine.observe(home(),3001));assertEquals(Engine.State.PAUSED,engine.state);assertEquals(0,engine.actions[0]);
    }
    @Test public void noRetryWithoutFreshHome() {enter();assertNull(engine.observe(text("알 수 없는 화면"),2000));}
    @Test public void freshHomeRequiredBeforePoints() {
        enter();engine.submitted(engine.observe(text("1원 받았어요"),100),true,100);
        assertNull(engine.observe(text("내 포인트"),200));assertEquals(Engine.Action.POINTS,engine.observe(home(),300).action());
    }
    @Test public void threeModeledCyclesDiscardReturnObservation() {
        engine.start(3,0);long now=1;
        for(int i=0;i<3;i++) {
            engine.submitted(engine.observe(home(),now),true,now++);
            assertNull(engine.observe(text("3초 구경해요"),now++));
            engine.submitted(engine.observe(text("1원 받았어요"),now),true,now++);
            engine.submitted(engine.observe(home(),now),true,now++);
            engine.submitted(engine.observe(text("전체","구매 적립 +1원","포인트 사용 -2원"),now),true,now++);
            long cycle=engine.cycleId;assertNull(engine.observe(home(),now++));assertEquals(cycle+1,engine.cycleId);
        }
        assertEquals(3,engine.completed);assertArrayEquals(new long[]{3,3,3,3},engine.actions);assertEquals(Engine.State.IDLE,engine.state);
    }
    @Test public void accessibilitySufficientSkipsOcr() {engine.start(3,0);assertFalse(engine.needsOcr(home(),0));assertTrue(engine.needsOcr(text("내 포인트"),0));}
    @Test public void ocrOnlyRetryGetsFreshFrameWhenRetryIsDue() {
        enter();
        var noCard=text("내 포인트","여기서 구경하면 1원 받아요");
        assertFalse(engine.needsOcr(noCard,500));
        assertTrue(engine.needsOcr(noCard,1001));
        engine.submitted(engine.observe(home(),1001),true,1001);
        assertFalse(engine.needsOcr(noCard,1500));
        assertTrue(engine.needsOcr(noCard,2001));
    }
    private void requestPoints(boolean accepted) {
        enter();engine.submitted(engine.observe(text("1원 받았어요"),100),true,100);
        engine.submitted(engine.observe(home(),200),accepted,200);
    }
    @Test public void pointsAcceptanceDoesNotMeanHistoryEntry() {
        requestPoints(true);assertEquals(Engine.State.POINTS_ENTRY,engine.state);assertEquals(0,engine.actions[2]);
        assertNull(engine.observe(home(),1199));assertEquals(Engine.State.POINTS_ENTRY,engine.state);
    }
    @Test public void actualHistoryConfirmsPointsAndBackInSameObservation() {
        requestPoints(true);var back=engine.observe(text("전체","방문 적립 +1원","포인트 사용 -2원"),300);
        assertEquals(1,engine.actions[2]);assertEquals(Engine.State.HISTORY,engine.state);assertEquals(Engine.Action.BACK_HISTORY,back.action());
    }
    @Test public void pointsRetryUsesFreshTargetsAndPausesAfterThree() {
        requestPoints(true);
        var fresh=Semantic.inspect(SemanticTest.home("한번 더 구경하고 1원 받아요","바뀐 광고"));
        var second=engine.observe(fresh,1200);assertEquals(2,second.attempt());assertSame(fresh.points(),second.target());engine.submitted(second,false,1200);
        assertNull(engine.observe(text("내 포인트"),2200));
        var third=engine.observe(home(),2200);assertEquals(3,third.attempt());engine.submitted(third,true,2200);
        assertNull(engine.observe(home(),3200));assertEquals(Engine.State.PAUSED,engine.state);
        assertEquals("내 포인트를 열지 못했어요.",engine.reason);assertEquals(0,engine.actions[2]);
    }
    @Test public void rejectedPointsSubmissionAlsoHasBoundedRetry() {
        requestPoints(false);assertEquals(Engine.State.POINTS_ENTRY,engine.state);assertEquals(2,engine.observe(home(),1200).attempt());
    }
    @Test public void pointsOcrRefreshIsRequestedAtRetryTime() {
        requestPoints(true);assertFalse(engine.needsOcr(text("내 포인트"),1199));assertTrue(engine.needsOcr(text("내 포인트"),1200));
    }
    @Test public void pointsTimeoutNeverBacksOrCountsSuccess() {
        requestPoints(true);assertNull(engine.observe(text("알 수 없는 화면"),30200));assertEquals(Engine.State.PAUSED,engine.state);assertEquals(0,engine.actions[2]);
    }
    @Test public void stoppedPointsRequestAndObservationsCannotResumeSession() {
        requestPoints(true);var retry=engine.observe(home(),1200);long[] before=engine.actions.clone();engine.stop();
        engine.submitted(retry,true,1300);assertNull(engine.observe(text("전체","방문 적립 +1원","포인트 사용 -2원"),1400));
        assertArrayEquals(before,engine.actions);assertFalse(engine.active());
    }
    @Test public void stopInvalidatesSession() {enter();long before=engine.generation;engine.stop();assertTrue(engine.generation>before);assertNull(engine.observe(text("1원 받았어요"),10));}
}

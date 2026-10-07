package app.get1won;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class ObservationGateTest {
    private final Engine e=new Engine(s->{});
    private final List<String> logs=new ArrayList<>();
    private final ObservationGate gate=new ObservationGate(e,logs::add);
    private final Semantic.Scene fresh=SemanticTest.home("구경하면 1원 받아요","광고");
    private OcrTicket start(){e.start(0,100);gate.bind("target",4);return new OcrTicket(1,e.generation,e.cycleId,e.state,e.actionEpoch,4,"target",100,"original",fresh.screen(),new Semantic.Box(0,0,0,0));}
    @Test public void S_otherAppPausesBeforeDecision(){start();assertFalse(gate.window("other",4));assertEquals(Engine.State.PAUSED,e.state);assertNull(e.observe(Semantic.inspect(fresh),200));}
    @Test public void T_sameAppWindowInvalidatesOldOcrWithoutPausing(){var t=start();assertNull(gate.merge(t,fresh,List.of(),"target",5,110,200));assertEquals(Engine.State.HOME,e.state);assertNotNull(e.observe(Semantic.inspect(fresh),200));}
    @Test public void U_screenshotFailureDoesNotSubmitAction(){var t=start();gate.failure(t,"screenshot 실패");assertArrayEquals(new long[4],e.actions);assertFalse(e.busy());assertTrue(logs.get(0).contains("임의 클릭 없음"));}
    @Test public void V_ocrFailureDoesNotSubmitAction(){var t=start();gate.failure(t,"OCR 실패");assertArrayEquals(new long[4],e.actions);assertFalse(e.busy());}
    @Test public void KQ_previousCycleAndStoppedOcrRejected(){var t=start();e.stop();assertNull(gate.merge(t,fresh,List.of(),"target",4,110,200));e.start(0,201);assertNull(gate.merge(t,fresh,List.of(),"target",4,210,220));assertEquals(0,e.completed);}
    @Test public void mergedAccessibilityAndShiftedOcrAreInspectedTogether(){var t=start();var ocr=List.of(SemanticTest.node("내 포인트",43,203,248,262,"OCR",false),SemanticTest.node("구경하면 1원 받아요",43,803,705,884,"OCR",false));var f=gate.merge(t,fresh,ocr,"target",4,110,200);assertNotNull(f);assertNotNull(f.points());assertNotNull(f.anchor());assertNotNull(f.ad());assertTrue(f.home());assertEquals("Accessibility",f.points().source());}
    @Test public void changedUnrelatedTextDoesNotStarveOcr(){var t=start();var changed=SemanticTest.home("구경하면 1원 받아요","새 제목");assertNotNull(gate.merge(t,changed,List.of(),"target",4,110,200));}
    @Test public void resizedWindowRejectsCoordinateMapping(){var t=start();var resized=new Semantic.Scene(fresh.nodes(),new Semantic.Box(0,0,2200,1080));assertNull(gate.merge(t,resized,List.of(),"target",4,110,200));assertFalse(e.busy());}
    @Test public void oldFrameRejected(){var t=start();assertNull(gate.merge(t,fresh,List.of(),"target",4,99,200));assertNull(gate.merge(t,fresh,List.of(),"target",4,110,3000));}
}

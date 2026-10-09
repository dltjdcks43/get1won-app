package app.get1won;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class Beta12RegressionTest {
    private static Semantic.Node n(int id,String text,int x,int y,int w,int h,String source,boolean click) {
        return new Semantic.Node(id,-1,text,new Semantic.Box(x,y,x+w,y+h),click,true,source);
    }
    private static Semantic.Node point(){return n(100,"내 포인트",40,200,260,60,"OCR",false);}
    private static Semantic.Scene home(Semantic.Node... extra) {
        var nodes=new ArrayList<>(List.of(n(1,"다시 구경하면 1원 받아요",40,800,700,60,"Accessibility",false),
            n(2,"상품 안내",40,940,900,120,"Accessibility",true)));
        nodes.addAll(List.of(extra));return SemanticTest.scene(nodes.toArray(new Semantic.Node[0]));
    }
    private static Engine engine(){Engine e=new Engine(x->{});e.start(0,0);e.state=Engine.State.WAIT_FOR_POINTS;return e;}
    private static OcrTicket ticket(Engine e){return new OcrTicket(20,e.generation,e.cycleId,e.state,e.actionEpoch,7,"target",100,"",home().screen(),new Semantic.Box(0,0,0,0));}
    private static PointsInput.Check check(Engine e,Semantic.Scene nativeScene,List<Semantic.Node> ocr){return PointsInput.check(e,ticket(e),ocr,Semantic.mergeOcr(nativeScene,ocr),"target",7,110,120);}
    @Test public void case1NativeTargetIsPreferredWithoutGesture() {
        var label=n(3,"내 포인트",40,200,260,60,"Accessibility",true);var target=PointsTarget.resolve(home(label),label);
        var policy=new PointsInput();var e=engine();assertNotNull(target.target());assertTrue(policy.preferNative(e.cycleId));
        policy.nativeAttempted(e.cycleId);assertFalse(policy.preferNative(e.cycleId));assertTrue(policy.canGesture(e.cycleId));
    }
    @Test public void case2MissingNativeAllowsFreshOcrCenter() {
        var e=engine();assertNull(PointsTarget.resolve(home(),point()).target());
        var r=check(e,home(),List.of(point()));assertTrue(r.allowed());assertEquals(170,r.box().cx());assertEquals(230,r.box().cy());
    }
    @Test public void case3RejectedNativeAndMissingStructureDoNotVetoConfirmedHome() {
        var parent=n(10,"내 포인트 이벤트",0,1400,950,120,"Accessibility",false);
        var child=new Semantic.Node(11,10,"내 포인트",new Semantic.Box(40,1420,300,1480),false,true,"Accessibility");
        var scene=home(parent,child);var result=PointsTarget.resolve(scene,point());
        assertEquals("context_rejected",result.nativeReason());assertEquals("no_points_structure",result.structure());
        assertTrue(check(engine(),scene,List.of(point())).allowed());
    }
    @Test public void case4DistinctOcrPointsRejectedButDuplicateObservationsCollapse() {
        var e=engine();assertEquals("ocr_points_ambiguous",check(e,home(),List.of(point(),n(101,"내 포인트",40,500,260,60,"OCR",false))).reason());
        var copy=n(101,"내 포인트",43,203,255,58,"OCR",false);
        assertTrue(check(e,home(n(4,"내 포인트",40,200,260,60,"Accessibility",false)),List.of(point(),copy)).allowed());
    }
    @Test public void case5LocalPromotionRejected() {
        for(String text:List.of("알림 받고 포인트 받기","혜택 알림","내 근처 혜택","이벤트","동의","출석"))
            assertEquals(text,"local_promotion",check(engine(),home(),List.of(point(),n(101,text,40,265,600,60,"OCR",false))).reason());
    }
    @Test public void case6SeparateTopPromoDoesNotVetoPoints() {
        assertTrue(check(engine(),home(n(4,"페이스페이 혜택 알림",20,60,900,60,"Accessibility",true)),List.of(point())).allowed());
    }
    @Test public void case7OldFrameWindowPackageRevisionAndCycleRejected() {
        var e=engine();var t=ticket(e);var merged=Semantic.mergeOcr(home(),List.of(point()));
        assertFalse(PointsInput.check(e,t,List.of(point()),merged,"target",7,99,120).allowed());
        assertFalse(PointsInput.check(e,t,List.of(point()),merged,"target",7,110,3000).allowed());
        assertFalse(PointsInput.check(e,t,List.of(point()),merged,"target",8,110,120).allowed());
        assertFalse(PointsInput.check(e,t,List.of(point()),merged,"other",7,110,120).allowed());
        e.windowChanged();assertFalse(PointsInput.check(e,t,List.of(point()),merged,"target",7,110,120).allowed());
        t=ticket(e);e.cycleId++;assertFalse(PointsInput.check(e,t,List.of(point()),merged,"target",7,110,120).allowed());
    }
    @Test public void case8NativeAcceptedOrRejectedUnchangedHomeUsesNewOcrFallback() {
        for(boolean accepted:List.of(true,false)) {
            var e=engine();var policy=new PointsInput();var f=Semantic.inspect(Semantic.mergeOcr(home(),List.of(point())));
            var d=e.observe(f,1);var old=ticket(e);policy.nativeAttempted(e.cycleId);e.submitted(d,accepted,2);
            assertNull(e.observe(f,500));var retry=e.observe(f,1002);assertNotNull(retry);
            assertFalse(policy.preferNative(e.cycleId));assertTrue(policy.canGesture(e.cycleId));
            assertFalse(PointsInput.check(e,old,List.of(point()),Semantic.mergeOcr(home(),List.of(point())),"target",7,110,120).allowed());
            assertTrue(check(e,home(),List.of(point())).allowed());assertEquals(0,e.actions[2]);
        }
    }
    @Test public void case9GestureCallbackIsNotSuccessUntilHistory() {
        var e=engine();var f=Semantic.inspect(Semantic.mergeOcr(home(),List.of(point())));var d=e.observe(f,1);
        e.gestureSubmitted(d,true,2);e.gestureResult(d,true,3);assertEquals(0,e.actions[2]);assertEquals(0,e.completed);
        assertEquals(Engine.Action.BACK_HISTORY,e.observe(new Semantic.Found(null,null,null,null,null,true),4).action());assertEquals(1,e.actions[2]);
    }
    @Test public void historyArrivingDuringFallbackOcrReleasesReservationForExistingSuccessPath() {
        var e=engine();var f=Semantic.inspect(Semantic.mergeOcr(home(),List.of(point())));
        var nativeDecision=e.observe(f,1);e.submitted(nativeDecision,true,2);
        var pendingFallback=e.observe(f,1002);assertNotNull(pendingFallback);assertTrue(e.busy());
        e.defer(pendingFallback);
        assertEquals(Engine.Action.BACK_HISTORY,e.observe(new Semantic.Found(null,null,null,null,null,true),1003).action());
        assertEquals(1,e.actions[2]);
    }
    @Test public void case10WrongPageIsNotSuccessAndTimesOutWithoutBack() {
        var e=engine();var d=e.observe(Semantic.inspect(Semantic.mergeOcr(home(),List.of(point()))),1);e.gestureSubmitted(d,true,2);e.gestureResult(d,true,3);
        var wrong=Semantic.inspect(SemanticTest.scene(n(4,"내 근처 혜택 알림 받고 포인트 받기",20,200,900,100,"Accessibility",false)));
        assertNull(e.observe(wrong,1002));assertEquals(0,e.actions[2]);assertNull(e.observe(wrong,30003));assertEquals(Engine.State.PAUSED,e.state);
    }
    @Test public void atMostTwoGesturesPerCycleEvenAfterWindowChangeAndRetryUsesNewBox() {
        var e=engine();var p=new PointsInput();assertEquals(1,p.gestureAttempted(e.cycleId));e.windowChanged();assertEquals(2,p.gestureAttempted(e.cycleId));assertFalse(p.canGesture(e.cycleId));
        assertTrue(p.canGesture(++e.cycleId));assertTrue(p.preferNative(e.cycleId));
        var newer=n(101,"내 포인트",60,300,260,60,"OCR",false);var result=check(e,home(),List.of(newer));assertEquals(newer.box(),result.box());assertNotEquals(point().box(),result.box());
    }
    @Test public void requiresExactUpperPointsAndHomeWithoutRewardOrHistory() {
        var e=engine();assertFalse(check(e,home(),List.of(n(101,"내 포인트 알림",40,200,260,60,"OCR",false))).allowed());
        assertFalse(check(e,home(),List.of(n(101,"내 포인트",40,1500,260,60,"OCR",false))).allowed());
        assertFalse(check(e,SemanticTest.scene(),List.of(point())).allowed());
        for(String text:List.of("3초 구경해요","1원 받았어요"))assertFalse(check(e,home(n(4,text,40,500,400,60,"Accessibility",false)),List.of(point())).allowed());
        e.state=Engine.State.REWARD;assertEquals("wrong_state",check(e,home(),List.of(point())).reason());
    }
}

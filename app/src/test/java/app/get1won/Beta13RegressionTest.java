package app.get1won;
import org.junit.Test;
import static org.junit.Assert.*;

public class Beta13RegressionTest {
    private static final Semantic.Box SCREEN=new Semantic.Box(0,0,1080,1920);
    private static Semantic.Node target(int id,String source,boolean clickable,int y) {
        return new Semantic.Node(id,-1,"내 포인트",new Semantic.Box(40,y,300,y+60),clickable,true,source);
    }
    private static PointsDispatch.Plan plan(Semantic.Node n,boolean handle){return PointsDispatch.plan(n,handle,SCREEN,SCREEN,true,true,false,1);}
    private static Engine engine(){Engine e=new Engine(x->{});e.start(0,0);e.state=Engine.State.WAIT_FOR_POINTS;return e;}
    private static Semantic.Found found(Semantic.Node n){return new Semantic.Found(n,null,null,null,null,false);}
    @Test public void ocrTargetUsesItsOwnCenter() {
        var n=target(100,"OCR",false,200);var p=plan(n,false);
        assertEquals(PointsDispatch.Mode.GESTURE,p.mode());assertEquals(n.box(),p.box());assertEquals(170,p.box().cx());assertEquals(230,p.box().cy());
    }
    @Test public void exactClickableNativeHandleUsesNativeAction() {
        var n=target(1,"Accessibility",true,200);assertEquals(PointsDispatch.Mode.NATIVE,plan(n,true).mode());
        assertEquals(PointsDispatch.Mode.GESTURE,plan(n,false).mode());
        assertEquals(PointsDispatch.Mode.GESTURE,plan(target(-2,"Accessibility",true,200),true).mode());
        assertEquals(PointsDispatch.Mode.GESTURE,plan(target(1,"Accessibility",false,200),true).mode());
    }
    @Test public void beta8PromotionParentIsNeverChosenForOcr() {
        var ocr=target(100,"OCR",false,200);
        var promo=new Semantic.Node(1,-1,"내 근처 혜택 / 알림 받고 포인트 받기",new Semantic.Box(20,180,900,300),true,true,"Accessibility");
        var scene=SemanticTest.scene(promo,ocr);assertSame(promo,Semantic.clickParent(scene,ocr));
        var p=plan(ocr,false);assertEquals(PointsDispatch.Mode.GESTURE,p.mode());assertEquals(ocr.box(),p.box());assertNotEquals(promo.box().cx(),p.box().cx());
    }
    @Test public void windowOrPackageChangeDefersWithoutCoordinates() {
        var e=engine();var d=e.observe(found(target(100,"OCR",false,200)),1);
        var p=PointsDispatch.plan(d.target(),false,SCREEN,SCREEN,e.current(d),false,false,1);
        assertEquals(PointsDispatch.Mode.DEFER,p.mode());assertNull(p.box());e.defer(d);assertFalse(e.busy());assertEquals("none",e.lastAction);
    }
    @Test public void overlayMoveDefersAndNextObservationSuppliesNewTarget() {
        var e=engine();var d=e.observe(found(target(100,"OCR",false,200)),1);
        assertEquals(PointsDispatch.Mode.DEFER,PointsDispatch.plan(d.target(),false,SCREEN,SCREEN,true,true,true,1).mode());
        e.defer(d);var newer=target(101,"OCR",false,260);var next=e.observe(found(newer),2);
        assertSame(newer,next.target());assertEquals(1,next.attempt());assertFalse(e.current(d));
    }
    @Test public void completedCallbackIsNotBusinessSuccess() {
        var e=engine();var d=e.observe(found(target(100,"OCR",false,200)),1);e.gestureSubmitted(d,true,2);e.gestureResult(d,true,3);
        assertEquals(Engine.State.POINTS_ENTRY,e.state);assertEquals(0,e.actions[2]);assertEquals(0,e.completed);
    }
    @Test public void freshHistoryConfirmsPointsSuccess() {
        var e=engine();var d=e.observe(found(target(100,"OCR",false,200)),1);e.gestureSubmitted(d,true,2);e.gestureResult(d,true,3);
        assertEquals(Engine.Action.BACK_HISTORY,e.observe(new Semantic.Found(null,null,null,null,null,true),4).action());assertEquals(1,e.actions[2]);
    }
    @Test public void unchangedPageRetriesOnlyWithNewObservationThenPausesAfterTwo() {
        var e=engine();var d=e.observe(found(target(100,"OCR",false,200)),1);e.gestureSubmitted(d,true,2);e.gestureResult(d,true,3);
        var newer=target(101,"OCR",false,260);assertNull(e.observe(found(newer),500));var retry=e.observe(found(newer),1002);
        assertEquals(2,retry.attempt());assertSame(newer,retry.target());assertEquals(newer.box(),plan(retry.target(),false).box());
        e.gestureSubmitted(retry,true,1002);e.gestureResult(retry,true,1003);
        assertNull(e.observe(found(newer),2002));assertEquals(Engine.State.PAUSED,e.state);assertEquals(0,e.actions[2]);
    }
    @Test public void nativeAndGestureShareTheTwoAttemptLimitAcrossWindowChange() {
        var e=engine();var d=e.observe(found(target(1,"Accessibility",true,200)),1);e.submitted(d,false,2);e.windowChanged();
        var retry=e.observe(found(target(100,"OCR",false,200)),1002);assertEquals(2,retry.attempt());e.submitted(retry,false,1002);
        assertNull(e.observe(found(target(100,"OCR",false,200)),2002));assertEquals(Engine.State.PAUSED,e.state);
    }
    @Test public void unrelatedPageNeverConfirmsHistoryOrRequestsBack() {
        var e=engine();var d=e.observe(found(target(100,"OCR",false,200)),1);e.submitted(d,true,2);
        var unrelated=new Semantic.Found(null,null,null,null,null,false);
        assertNull(e.observe(unrelated,1002));assertEquals(0,e.actions[2]);assertNull(e.observe(unrelated,30003));assertEquals(Engine.State.PAUSED,e.state);
    }
    @Test public void inactiveDecisionAndInvalidBoundsNeverDispatch() {
        var n=target(100,"OCR",false,200);assertEquals(PointsDispatch.Mode.DEFER,PointsDispatch.plan(n,false,SCREEN,SCREEN,false,true,false,1).mode());
        assertEquals(PointsDispatch.Mode.DEFER,plan(target(100,"OCR",false,1900),false).mode());
        assertEquals(PointsDispatch.Mode.DEFER,plan(new Semantic.Node(1,-1,"内",new Semantic.Box(0,0,0,60),false,true,"OCR"),false).mode());
    }
    @Test public void recentLogKeepsLast400PhysicalLinesInOrder() {
        RecentLog log=new RecentLog(400);for(int i=0;i<250;i++)log.add("state="+(i*2)+"\naction="+(i*2+1));
        String[] lines=log.text().split("\n");assertEquals(400,lines.length);assertEquals("state=100",lines[0]);assertEquals("action=499",lines[399]);
    }
}

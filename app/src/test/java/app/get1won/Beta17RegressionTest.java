package app.get1won;
import org.junit.Test;
import static org.junit.Assert.*;

public class Beta17RegressionTest {
    private static final Semantic.Box SCREEN=new Semantic.Box(0,0,1440,3120);
    private static Semantic.Node point(int top,String source){return new Semantic.Node(1,-1,"내포인트6510원내역보기",new Semantic.Box(90,top,375,top+120),true,true,source);}
    private static Semantic.Found found(Semantic.Node p){return new Semantic.Found(p,null,null,null,null,false);}
    private static Engine engine(){var e=new Engine(x->{});e.start(0,0);e.state=Engine.State.WAIT_FOR_POINTS;return e;}
    private static PointsDispatch.Plan plan(Engine e,Engine.Decision d){return PointsDispatch.plan(d.target(),true,SCREEN,SCREEN,e.current(d),true,false,d.attempt());}
    private static Engine.Decision first(Engine e){return e.observe(found(point(773,"Accessibility")),100);}
    @Test public void firstExactNativeIsNative(){var e=engine();var d=first(e);assertEquals(1,d.attempt());assertEquals(PointsDispatch.Mode.NATIVE,plan(e,d).mode());}
    @Test public void acceptedNativeWithoutHistoryRetriesFreshCenterWithGesture() {
        var e=engine();var d=first(e);e.submitted(d,true,101);assertEquals(0,e.actions[2]);
        assertNull(e.observe(found(point(800,"Accessibility")),1100));
        var fresh=point(810,"Accessibility");var retry=e.observe(found(fresh),1101);
        assertEquals(2,retry.attempt());assertSame(fresh,retry.target());
        var p=plan(e,retry);assertEquals(PointsDispatch.Mode.GESTURE,p.mode());assertEquals(232,p.box().cx());assertEquals(870,p.box().cy());
    }
    @Test public void gestureDeliveryOnlySucceedsAfterHistory() {
        var e=engine();var d=first(e);e.submitted(d,true,101);var retry=e.observe(found(point(773,"Accessibility")),1101);
        assertEquals(PointsDispatch.Mode.GESTURE,plan(e,retry).mode());e.gestureSubmitted(retry,true,1102);e.gestureResult(retry,true,1103);
        assertEquals(0,e.actions[2]);assertEquals(Engine.State.POINTS_ENTRY,e.state);
        assertEquals(Engine.Action.BACK_HISTORY,e.observe(new Semantic.Found(null,null,null,null,null,true),1104).action());
        assertEquals(Engine.State.HISTORY,e.state);assertEquals(1,e.actions[2]);
    }
    @Test public void secondAttemptWithoutHistoryIsBoundedFailure() {
        var e=engine();var d=first(e);e.submitted(d,true,101);var retry=e.observe(found(point(773,"Accessibility")),1101);
        e.gestureSubmitted(retry,true,1102);e.gestureResult(retry,true,1103);
        assertNull(e.observe(found(point(773,"Accessibility")),2102));assertEquals(Engine.State.PAUSED,e.state);assertEquals(0,e.actions[2]);
    }
    @Test public void changedWindowOrStaleDecisionOrOverlayDefersRetry() {
        var p=point(773,"Accessibility");
        assertEquals(PointsDispatch.Mode.DEFER,PointsDispatch.plan(p,true,SCREEN,SCREEN,true,false,false,2).mode());
        assertEquals(PointsDispatch.Mode.DEFER,PointsDispatch.plan(p,true,SCREEN,SCREEN,false,true,false,2).mode());
        assertEquals(PointsDispatch.Mode.DEFER,PointsDispatch.plan(p,true,SCREEN,SCREEN,true,true,true,2).mode());
    }
    @Test public void offscreenTargetOrWindowCannotCreateRetryGesture() {
        var outside=point(-130,"Accessibility");assertEquals(PointsDispatch.Mode.DEFER,PointsDispatch.plan(outside,true,SCREEN,SCREEN,true,true,false,2).mode());
        var shifted=new Semantic.Box(-382,0,1058,3120);
        assertEquals(PointsDispatch.Mode.DEFER,PointsDispatch.plan(point(773,"Accessibility"),true,shifted,SCREEN,true,true,false,2).mode());
    }
    @Test public void ocrTargetRetainsGestureOnBothAttempts() {
        for(int attempt:new int[]{1,2})assertEquals(PointsDispatch.Mode.GESTURE,PointsDispatch.plan(point(773,"OCR"),false,SCREEN,SCREEN,true,true,false,attempt).mode());
    }
}

package app.get1won;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class Beta14RegressionTest {
    private static final Semantic.Box DISPLAY=new Semantic.Box(0,0,1080,2316);
    private static final Semantic.Box LEFT=new Semantic.Box(-382,0,698,2316);
    private static Semantic.Node point(int offset){return new Semantic.Node(1,-1,"내 포인트",new Semantic.Box(81+offset,583,261+offset,627),false,true,"OCR");}
    private static Engine engine(){var e=new Engine(x->{});e.start(0,0);e.state=Engine.State.WAIT_FOR_POINTS;return e;}
    @Test public void fullWindowAllowsOcrAndObservedTargetGesture() {
        assertTrue(WindowGeometry.visible(DISPLAY,DISPLAY));
        var p=PointsDispatch.plan(point(0),false,DISPLAY,DISPLAY,true,true,false);
        assertEquals(PointsDispatch.Mode.GESTURE,p.mode());assertEquals(171,p.box().cx());
    }
    @Test public void equal1080WidthDoesNotMakeTranslatedWindowUsable() {
        assertEquals(DISPLAY.width(),LEFT.width()); // beta13's buffer-size check alone passed.
        var e=engine();var log=new ArrayList<String>();var gate=new WindowGeometry();
        assertFalse(gate.observe(LEFT,DISPLAY,log::add));assertEquals(1,log.size());
        assertTrue(e.active());assertEquals(Engine.State.WAIT_FOR_POINTS,e.state);
        assertEquals(PointsDispatch.Mode.DEFER,PointsDispatch.plan(point(-382),false,LEFT,DISPLAY,true,true,false).mode());
    }
    @Test public void rightTranslationAlsoRequiresReobservation() {
        var right=new Semantic.Box(382,0,1462,2316);assertEquals(1080,right.width());
        assertFalse(WindowGeometry.visible(right,DISPLAY));
        assertEquals(PointsDispatch.Mode.DEFER,PointsDispatch.plan(point(382),false,right,DISPLAY,true,true,false).mode());
    }
    @Test public void resultArrivingAfterSameIdTranslationIsDiscardedThenStableResultWorks() {
        var e=engine();var gate=new ObservationGate(e,x->{});gate.bind("target",1076);
        var t=new OcrTicket(1,e.generation,e.cycleId,e.state,e.actionEpoch,1076,"target",100,"",DISPLAY,new Semantic.Box(0,0,0,0));
        assertTrue(t.matches(1076,"target","",110,120)); // ID/time alone cannot detect translation.
        assertFalse(WindowGeometry.visible(LEFT,DISPLAY));
        assertNull(gate.merge(t,new Semantic.Scene(List.of(),LEFT),List.of(point(0)),"target",1076,110,120));
        assertTrue(e.active());assertEquals(Engine.State.WAIT_FOR_POINTS,e.state);
        var found=gate.merge(t,new Semantic.Scene(List.of(),DISPLAY),List.of(point(0)),"target",1076,110,120);
        assertNotNull(found);assertNotNull(found.points());
    }
    @Test public void negativeOcrCoordinatesNeverProduceGesturePlan() {
        var n=point(-382);assertEquals(-301,n.box().left());assertEquals(-121,n.box().right());assertTrue(LEFT.contains(n.box()));
        var p=PointsDispatch.plan(n,false,LEFT,DISPLAY,true,true,false);
        assertEquals(PointsDispatch.Mode.DEFER,p.mode());assertNull(p.box());
        assertEquals(PointsDispatch.Mode.DEFER,PointsDispatch.plan(n,false,DISPLAY,DISPLAY,true,true,false).mode());
    }
    @Test public void transientThenStableAt250msResumesExistingPointsHistoryPath() {
        var e=engine();var geometry=new WindowGeometry();
        assertFalse(geometry.observe(LEFT,DISPLAY,x->{}));assertEquals("none",e.lastAction);
        assertTrue(geometry.observe(DISPLAY,DISPLAY,x->{}));
        var found=Semantic.inspect(new Semantic.Scene(List.of(point(0)),DISPLAY));var d=e.observe(found,250);
        assertEquals(Engine.Action.POINTS,d.action());assertEquals(1,d.attempt());
        assertEquals(PointsDispatch.Mode.GESTURE,PointsDispatch.plan(d.target(),false,DISPLAY,DISPLAY,e.current(d),true,false).mode());
        e.gestureSubmitted(d,true,251);e.gestureResult(d,true,252);assertEquals(0,e.actions[2]);
        assertEquals(Engine.Action.BACK_HISTORY,e.observe(new Semantic.Found(null,null,null,null,null,true),253).action());
        assertEquals(1,e.actions[2]);
    }
    @Test public void nonZeroMultiwindowOriginRemainsValidAndUnmodified() {
        var w=new Semantic.Box(0,500,1080,1800);assertTrue(WindowGeometry.visible(w,DISPLAY));
        var mapped=new Semantic.Node(1,-1,"내 포인트",new Semantic.Box(w.left()+81,w.top()+83,w.left()+261,w.top()+127),false,true,"OCR");
        var plan=PointsDispatch.plan(mapped,false,w,DISPLAY,true,true,false);
        assertEquals(PointsDispatch.Mode.GESTURE,plan.mode());assertEquals(605,plan.box().cy());
        assertTrue(WindowGeometry.visible(new Semantic.Box(100,500,900,1800),DISPLAY));
    }
    @Test public void bothDeviceSizesRejectTranslationRegardlessOfBufferMatch() {
        var display1440=new Semantic.Box(0,0,1440,3120);var translated1440=new Semantic.Box(-382,0,698,3120);
        assertNotEquals(1440,translated1440.width());assertEquals(1080,LEFT.width());
        assertFalse(WindowGeometry.visible(translated1440,display1440));assertFalse(WindowGeometry.visible(LEFT,DISPLAY));
        assertTrue(WindowGeometry.visible(display1440,display1440));assertTrue(WindowGeometry.visible(DISPLAY,DISPLAY));
    }
    @Test public void transientLogIsDeduplicatedUntilBoundsChangeOrRecovery() {
        var gate=new WindowGeometry();var logs=new ArrayList<String>();
        for(int i=0;i<10;i++)assertFalse(gate.observe(LEFT,DISPLAY,logs::add));assertEquals(1,logs.size());
        assertFalse(gate.observe(new Semantic.Box(-300,0,780,2316),DISPLAY,logs::add));assertEquals(2,logs.size());
        assertTrue(gate.observe(DISPLAY,DISPLAY,logs::add));assertFalse(gate.observe(LEFT,DISPLAY,logs::add));assertEquals(3,logs.size());
    }
    @Test public void displayContainmentAndPositiveCenterProtectAllInputModes() {
        assertFalse(WindowGeometry.input(new Semantic.Box(100,100,100,200),DISPLAY,DISPLAY));
        assertFalse(WindowGeometry.input(new Semantic.Box(100,2300,300,2400),DISPLAY,DISPLAY));
        assertFalse(WindowGeometry.visible(new Semantic.Box(0,0,0,2316),DISPLAY));
        var nativeNode=new Semantic.Node(1,-1,"내 포인트",point(-382).box(),true,true,"Accessibility");
        assertEquals(PointsDispatch.Mode.DEFER,PointsDispatch.plan(nativeNode,true,LEFT,DISPLAY,true,true,false).mode());
    }
}

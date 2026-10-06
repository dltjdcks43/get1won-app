package app.get1won;
import org.junit.Test;
import static org.junit.Assert.*;
public class OverlayCaptureGateTest {
 @Test public void rejectsPreHideFramesEvenAfterSettle(){var gate=new OverlayCaptureGate();Object request=new Object();long t=1_000_000_000L;gate.hide(request,t);assertFalse(gate.accepts(request,t-1,t+70_000_000L));assertFalse(gate.accepts(request,t,t+70_000_000L));assertTrue(gate.accepts(request,t+10_000_000L,t+70_000_000L));}
 @Test public void cannotCaptureBeforeUiSettlesOrAfterTimeout(){var gate=new OverlayCaptureGate();Object request=new Object();long t=1_000_000_000L;gate.hide(request,t);assertFalse(gate.accepts(request,t+10,t+50_000_000L));assertFalse(gate.ready(request,t+700_000_000L));}
 @Test public void oldRestoreCannotReleaseNextCycleCapture(){var gate=new OverlayCaptureGate();Object old=new Object(),next=new Object();gate.hide(old,1);gate.hide(next,2);assertFalse(gate.release(old));assertTrue(gate.hidden());assertTrue(gate.release(next));assertFalse(gate.hidden());}
 @Test public void stopClearsCaptureWithoutResurrection(){var gate=new OverlayCaptureGate();Object request=new Object();gate.hide(request,1);gate.clear();assertFalse(gate.release(request));assertFalse(gate.ready(request,70_000_001L));assertFalse(gate.hidden());}
}

package app.get1won;
import org.junit.Test;
import static org.junit.Assert.*;
public class TestAuditTest {
    @Test public void detectsFaultsIndependentlyOfEngine(){TestAudit a=new TestAudit();a.action(1,false,false,false);a.action(2,false,false,false);a.action(3,false,true,false);a.action(3,false,false,false);a.action(4,true,false,true);assertEquals(1,a.beforeCompletion);assertEquals(1,a.transitionClicks);assertEquals(1,a.wrongOrder);assertEquals(1,a.duplicateClicks);assertEquals(1,a.lateActions);}
}

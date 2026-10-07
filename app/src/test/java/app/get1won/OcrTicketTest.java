package app.get1won;
import org.junit.Test;
import static org.junit.Assert.*;
public class OcrTicketTest {
    private final OcrTicket ticket=new OcrTicket(1,2,3,Engine.State.HOME,4,5,"example.app",1000,"fresh",new Semantic.Box(0,0,1080,2200),new Semantic.Box(0,0,0,0));
    @Test public void rejectsPreviousSessionCycleStateOrRevision(){
        assertTrue(ticket.current(2,3,Engine.State.HOME,4));
        assertFalse(ticket.current(1,3,Engine.State.HOME,4));assertFalse(ticket.current(2,2,Engine.State.HOME,4));
        assertFalse(ticket.current(2,3,Engine.State.AD_ENTRY,4));assertFalse(ticket.current(2,3,Engine.State.HOME,3));
    }
    @Test public void rejectsOldFrameDifferentWindowAndChangedFreshRoot(){
        assertTrue(ticket.matches(5,"example.app","fresh",1100,1500));
        assertFalse(ticket.matches(5,"example.app","fresh",900,1500));
        assertFalse(ticket.matches(5,"example.app","fresh",1100,4000));
        assertFalse(ticket.matches(6,"example.app","fresh",1100,1500));
        assertFalse(ticket.matches(5,"different.app","fresh",1100,1500));
        assertFalse(ticket.matches(5,"example.app","changed",1100,1500));
    }
}

package app.get1won;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** A-Z behavior specifications. Synthetic time advances exercise deadlines without sleeping. */
public class UniversalTest {
    private final Engine e=new Engine(s->{});
    private long now=1;
    private static Semantic.Found home(){return Semantic.inspect(SemanticTest.home("다시 구경하면 1원 받아요","새 광고"));}
    private static Semantic.Found text(String... labels){
        List<Semantic.Node> nodes=new ArrayList<>();int y=200;
        for(String label:labels){nodes.add(SemanticTest.access(label,y,y+60));y+=150;}
        return Semantic.inspect(new Semantic.Scene(nodes,new Semantic.Box(0,0,1080,2200)));
    }
    private static Semantic.Found points(){return text("내 포인트");}
    private static Semantic.Found history(){return text("전체","방문 적립 +1원","사용 -2원");}
    private void submit(Engine.Decision d){assertNotNull(d);e.submitted(d,true,now++);}
    private void reward(){submit(e.observe(home(),now++));submit(e.observe(text("1원 받았어요"),now++));}
    private Engine.Decision pointsRequest(){e.start(0,0);reward();return e.observe(points(),now++);}
    private void cycle(){reward();submit(e.observe(points(),now++));submit(e.observe(history(),now++));assertNull(e.observe(points(),now++));}
    @Test public void A_pointsWithoutAnchorAfterBack(){var d=pointsRequest();assertEquals(Engine.Action.POINTS,d.action());assertNotNull(d.target());}
    @Test public void B_acceptedWithoutHistoryRetriesFreshPoints(){var d=pointsRequest();submit(d);assertEquals(0,e.actions[2]);now+=1000;var fresh=text("내\n포인트");var retry=e.observe(fresh,now);assertSame(fresh.points(),retry.target());assertNotSame(d.target(),retry.target());assertEquals(2,retry.attempt());}
    @Test public void C_cancelledGestureIsNotHistory(){var d=pointsRequest();e.gestureSubmitted(d,true,now++);e.gestureResult(d,false,now++);assertEquals(0,e.actions[2]);assertEquals(0,e.completed);}
    @Test public void D_completedGestureIsNotHistory(){var d=pointsRequest();e.gestureSubmitted(d,true,now++);e.gestureResult(d,true,now++);assertNull(e.observe(points(),now++));assertEquals(0,e.actions[2]);}
    @Test public void E_onlyActualHistoryConfirms(){var d=pointsRequest();e.gestureSubmitted(d,true,now++);assertNull(e.observe(history(),now++));e.gestureResult(d,true,now++);assertEquals(Engine.Action.BACK_HISTORY,e.observe(history(),now++).action());assertEquals(1,e.actions[2]);}
    @Test public void F_pointsOnlyReturnCompletesCycle(){e.start(0,0);cycle();assertEquals(1,e.completed);assertEquals(Engine.State.HOME,e.state);}
    @Test public void G_waitsForLateNextAdvertisement(){e.start(0,0);cycle();assertNull(e.observe(points(),now+10000));assertEquals(Engine.Action.AD,e.observe(home(),now+11000).action());}
    @Test public void H_ocrFallbackUsesObservedBounds(){e.start(0,0);reward();var n=SemanticTest.node("내 포인트",45,310,289,370,"OCR",false);var found=Semantic.inspect(Semantic.mergeOcr(SemanticTest.scene(),List.of(n)));var d=e.observe(found,now++);assertEquals(n.box(),d.target().box());assertEquals("OCR",d.target().source());}
    private OcrTicket ticket(){return new OcrTicket(1,e.generation,e.cycleId,e.state,e.actionEpoch,4,"target",now,"",new Semantic.Box(0,0,1080,2200),new Semantic.Box(0,0,0,0));}
    private boolean current(OcrTicket t){return t.current(e.generation,e.cycleId,e.state,e.actionEpoch);}
    @Test public void K_previousCycleOcrCannotAct(){e.start(0,0);var t=ticket();cycle();assertFalse(current(t));}
    @Test public void L_oldDecisionCannotBeSubmittedAfterRetry(){var first=pointsRequest();submit(first);var retry=e.observe(points(),now+=1000);e.submitted(first,true,now++);assertTrue(e.busy());submit(retry);assertFalse(e.busy());assertTrue(e.lastAction.contains("attempt=2"));}
    private void repeats(int n){e.start(n,0);for(int i=0;i<n;i++)cycle();assertFalse(e.active());assertEquals(n,e.completed);assertArrayEquals(new long[]{n,n,n,n},e.actions);}
    @Test public void M_exactlyOne(){repeats(1);}
    @Test public void N_exactlyTen(){repeats(10);}
    @Test public void O_exactlyHundred(){repeats(100);}
    @Test public void P_continuousUntilStop(){e.start(0,0);for(int i=0;i<101;i++)cycle();assertTrue(e.active());assertEquals(101,e.completed);e.stop();assertFalse(e.active());}
    @Test public void Q_stopInvalidatesOcr(){e.start(0,0);var t=ticket();e.stop();assertFalse(current(t));assertNull(e.observe(home(),now));}
    @Test public void R_stopInvalidatesGesture(){var d=pointsRequest();e.gestureSubmitted(d,true,now++);e.stop();long generation=e.generation;e.gestureResult(d,true,now++);assertEquals(generation,e.generation);assertFalse(e.active());assertEquals(0,e.actions[2]);}
    @Test public void S_otherPackageRejected(){assertFalse(new TargetWindow("target",4).matches("other",4));}
    @Test public void T_otherWindowRejected(){assertFalse(new TargetWindow("target",4).matches("target",5));}
    @Test public void U_screenshotFailureCannotInventTarget(){e.start(0,0);reward();assertNull(e.observe(text(),now++));assertEquals(0,e.actions[2]);assertNull(e.observe(text(),now+30000));assertEquals(Engine.State.PAUSED,e.state);}
    @Test public void V_ocrFailureCannotInventTarget(){e.start(0,0);reward();var f=Semantic.inspect(Semantic.mergeOcr(SemanticTest.scene(),List.of()));assertNull(e.observe(f,now++));assertEquals(0,e.actions[2]);}
    @Test public void W_similarMenusNeverMatch(){for(String s:List.of("내 포인트 출금","내 포인트 확인","포인트 확인","포인트 출금","내 포인트 알림 설정"))assertNull(text(s).points());assertNotNull(text("내 \n 포인트").points());}
    @Test public void X_repeatedEventsCannotDuplicateReservedClick(){e.start(1,0);var d=e.observe(home(),now++);for(int i=0;i<20;i++)assertNull(e.observe(home(),now++));submit(d);assertEquals(0,e.actions[0]);}
    @Test public void Y_noDoubleBackBeforeOrAfterSubmission(){e.start(1,0);submit(e.observe(home(),now++));var back=e.observe(text("1원 받았어요"),now++);assertNull(e.observe(text("1원 받았어요"),now++));submit(back);assertNull(e.observe(text("1원 받았어요"),now++));assertEquals(0,e.actions[1]);}
    @Test public void Z_noDoubleCycleCompletion(){e.start(0,0);cycle();long id=e.cycleId;for(int i=0;i<20;i++)assertNull(e.observe(points(),now++));assertEquals(1,e.completed);assertEquals(id,e.cycleId);}
    @Test public void gestureTimeoutPausesWithoutBack(){var d=pointsRequest();e.gestureSubmitted(d,true,now);assertNull(e.observe(history(),now+3000));assertEquals(Engine.State.PAUSED,e.state);assertEquals(0,e.actions[2]);}
    @Test public void obsoleteGestureAfterNewStartIgnored(){var d=pointsRequest();e.gestureSubmitted(d,true,now++);e.start(1,now++);e.gestureResult(d,true,now++);assertEquals(Engine.State.HOME,e.state);assertEquals(0,e.actions[2]);}
    @Test public void historyHasAlternateHeadingAndMultipleRows(){assertTrue(text("포인트 내역","적립 +1원","사용 -2원").history());assertTrue(text("적립","사용","방문 +1원","출금 -2원").history());assertFalse(text("포인트 내역","전체").history());}
    @Test public void contentDescriptionAndHierarchy(){var label=new Semantic.Node(2,1,"",new Semantic.Box(40,200,250,260),false,true,"Accessibility","내 포인트","target:id/points");var parent=new Semantic.Node(1,-1,"",new Semantic.Box(20,190,300,275),true,true,"Accessibility");var scene=SemanticTest.scene(parent,label);assertSame(label,Semantic.inspect(scene).points());assertSame(parent,Semantic.clickParent(scene,label));}
    @Test public void adClickableParentRetained(){var s=SemanticTest.home("구경하면 1원 받아요","광고");var ad=Semantic.inspect(s).ad();assertTrue(ad.clickable());assertSame(ad,Semantic.clickParent(s,ad));}
    @Test public void unreadableWindowNeverCountsBackAsSuccess(){e.start(1,0);reward();assertEquals(0,e.actions[1]);assertNull(e.observe(text(),now++));assertEquals(0,e.actions[1]);}
    @Test public void ambiguousDescriptionWaitingStillVetoesBack(){
        e.start(1,0);submit(e.observe(home(),now++));
        var a=new Semantic.Node(1,-1,"",new Semantic.Box(20,200,700,260),false,true,"Accessibility","3초 구경해요","");
        var b=new Semantic.Node(2,-1,"",new Semantic.Box(20,600,700,660),false,true,"Accessibility","3초 구경해요","");
        var complete=SemanticTest.access("1원 받았어요",900,960);
        assertNull(e.observe(Semantic.inspect(SemanticTest.scene(a,b,complete)),now++));assertEquals(0,e.actions[1]);
    }
    @Test public void resourceIdAloneCannotInventPointsLabel(){var n=new Semantic.Node(1,-1,"",new Semantic.Box(20,200,700,260),true,true,"Accessibility","","target:id/points");assertNull(Semantic.inspect(SemanticTest.scene(n)).points());}
}

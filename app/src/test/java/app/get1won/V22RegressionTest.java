package app.get1won;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
/** Reproductions of the v2.2 -> beta1 regressions, plus ordered window-transition checks. */
public class V22RegressionTest {
    static Semantic.Node node(int id,int parent,String text,String desc,int top,boolean click){return new Semantic.Node(id,parent,text,new Semantic.Box(40,top,900,top+60),click,true,"Accessibility",desc,"");}
    static Semantic.Scene home(){return SemanticTest.scene(node(1,-1,"내 포인트","",200,true),node(2,-1,"다시 혜택 구경하고 1원 받아요","",800,false),node(3,-1,"새 상품 안내","1원 광고",940,true));}
    private static Engine.Decision ad(Semantic.Scene s){Engine e=new Engine(x->{});e.start(1,0);return e.observe(Semantic.inspect(s),1);}
    @Test public void pointsWithBalanceDoesNotBlockFirstAd(){var s=SemanticTest.scene(node(1,-1,"내 포인트 1,234P","",200,true),home().nodes().get(1),home().nodes().get(2));assertEquals(Engine.Action.AD,ad(s).action());}
    @Test public void pointsWithOtherMenuStillRejected(){for(String t:List.of("내 포인트 출금","내 포인트 확인","내 포인트 알림","내 포인트 1원 받기"))assertFalse(Semantic.points(Semantic.normalize(t)));}
    @Test public void splitDescriptionsRejoinLikeV22Adapter(){var s=SemanticTest.scene(home().nodes().get(0),node(2,-1,"","다시 혜택 구경하고",800,false),node(3,-1,"","1원 받아요",870,false),node(4,-1,"새 상품 안내","",1000,true));assertNotNull(Semantic.inspect(s).anchor());assertEquals(Engine.Action.AD,ad(s).action());}
    @Test public void descriptionDoesNotVetoUsableAdText(){assertEquals(3,ad(home()).target().id());}
    @Test public void descriptionOnlyOtherRewardIsStillExcluded(){var s=SemanticTest.scene(home().nodes().get(0),home().nodes().get(1),node(3,-1,"","쿠폰 혜택 알림 동의하고 1원 받기",940,true));assertNull(Semantic.inspect(s).ad());}
    @Test public void parentDescriptionAndChildLabelAreOneElement(){
        var parent=new Semantic.Node(9,-1,"영역",new Semantic.Box(20,100,1000,500),true,true,"Accessibility","내 포인트","");
        var child=node(1,9,"내 포인트","",200,true);
        var s=SemanticTest.scene(parent,child,home().nodes().get(1),home().nodes().get(2));
        assertEquals(child,Semantic.inspect(s).points());assertNotNull(ad(s));
    }
    @Test public void priorityQueryCopyOfAncestorDoesNotRestoreAmbiguity(){
        var parent=new Semantic.Node(9,-1,"영역",new Semantic.Box(20,100,1000,500),true,true,"Accessibility","내 포인트","");
        var copy=new Semantic.Node(100000,-1,parent.text(),parent.box(),true,true,"Accessibility",parent.description(),"");
        var child=node(1,9,"내 포인트","",200,true);
        assertSame(child,Semantic.inspect(SemanticTest.scene(copy,parent,child)).points());
    }
    @Test public void twoRealChildrenRemainAmbiguousEvenWithParent(){
        var parent=new Semantic.Node(9,-1,"영역",new Semantic.Box(0,0,1080,2000),true,true,"Accessibility","내 포인트","");
        assertNull(Semantic.inspect(SemanticTest.scene(parent,node(1,9,"내 포인트","",200,true),node(2,9,"내 포인트","",1500,true))).points());
    }
    @Test public void adTitleStillSelectsClickableContainer(){
        var parent=new Semantic.Node(3,-1,"",new Semantic.Box(20,920,1000,1100),true,true,"Accessibility");
        var title=node(4,3,"새 상품 안내","",950,false);
        var s=SemanticTest.scene(home().nodes().get(0),home().nodes().get(1),parent,title);
        var d=ad(s);assertSame(parent,d.target());assertSame(parent,Semantic.clickParent(s,d.target()));
    }
    @Test public void homeEvidenceHasV22PriorityOverHistoryLikeHomeSummary(){
        var list=new ArrayList<>(home().nodes());list.add(node(4,-1,"전체","",1300,false));list.add(node(5,-1,"적립 +1원","",1450,false));list.add(node(6,-1,"사용 -2원","",1600,false));
        var s=new Semantic.Scene(list,home().screen());assertTrue(Semantic.inspect(s).history());assertNotNull(ad(s));
    }
    @Test public void diagnosticsExplainActualAdRejection(){
        var s=SemanticTest.scene(home().nodes().get(0),home().nodes().get(1),node(3,-1,"동의하고 1원 받기","",940,true));
        var log=Semantic.diagnostics(s,Semantic.inspect(s));assertTrue(log.contains("points=found"));assertTrue(log.contains("anchor=found"));assertTrue(log.contains("ad=not found"));assertTrue(log.contains("excluded_label"));assertTrue(log.contains("selector=text"));
    }
    @Test public void diagnosticsExplainDuplicatePoints(){var s=SemanticTest.scene(node(1,-1,"내 포인트","",200,true),node(2,-1,"내 포인트","",1500,true));assertTrue(Semantic.diagnostics(s,Semantic.inspect(s)).contains("ambiguous_distinct_targets"));}
    private static Semantic.Found text(String... labels){List<Semantic.Node> nodes=new ArrayList<>();int id=1,y=200;for(String label:labels){nodes.add(node(id++,-1,label,"",y,false));y+=200;}return Semantic.inspect(SemanticTest.scene(nodes.toArray(new Semantic.Node[0])));}
    @Test public void sevenStageModeledFlowAcrossWindows(){
        Engine e=new Engine(x->{});ObservationGate gate=new ObservationGate(e,x->{});e.start(3,0);gate.bind("target",1);int window=1;long now=1;
        for(int cycle=0;cycle<3;cycle++){
            // 1 HOME discovery, 2 input submission (not yet business success).
            var f=Semantic.inspect(home());assertNotNull(f.points());assertNotNull(f.anchor());assertNotNull(f.ad());
            var ad=e.observe(f,now++);assertEquals(Engine.Action.AD,ad.action());e.gestureSubmitted(ad,true,now++);assertEquals(cycle,e.actions[0]);
            // 3 Same-app new window retires the old gesture; fresh waiting proves entry.
            assertTrue(gate.window("target",++window));assertTrue(e.active());e.gestureResult(ad,true,now++);assertFalse(e.busy());
            assertNull(e.observe(text("3초 구경해요"),now++));assertEquals(cycle+1,e.actions[0]);
            // 4 Completion -> BACK -> new HOME; no completion inferred from BACK acceptance.
            var back=e.observe(text("1원 받았어요"),now++);assertEquals(Engine.Action.BACK_REWARD,back.action());e.submitted(back,true,now++);assertEquals(cycle,e.actions[1]);
            assertTrue(gate.window("target",++window));
            // 5 Points independently found and submitted.
            var points=e.observe(text("내 포인트"),now++);assertEquals(Engine.Action.POINTS,points.action());e.submitted(points,true,now++);assertEquals(cycle,e.actions[2]);
            // 6 History must be observed in its new window before history BACK.
            assertTrue(gate.window("target",++window));var history=e.observe(text("전체","적립 +1원","사용 -2원"),now++);assertEquals(Engine.Action.BACK_HISTORY,history.action());assertEquals(cycle+1,e.actions[2]);e.submitted(history,true,now++);
            // 7 Fresh HOME return counts exactly one cycle, ready for the next ad.
            assertTrue(gate.window("target",++window));assertNull(e.observe(text("내 포인트"),now++));assertEquals(cycle+1,e.completed);
        }
        assertArrayEquals(new long[]{3,3,3,3},e.actions);assertFalse(e.active());
    }
    @Test public void oldWindowOcrCannotApplyEvenAfterWindowIdReturns(){Engine e=new Engine(x->{});ObservationGate g=new ObservationGate(e,x->{});e.start(0,0);g.bind("target",1);var ticket=new OcrTicket(1,e.generation,e.cycleId,e.state,e.actionEpoch,1,"target",1,"",home().screen(),new Semantic.Box(0,0,0,0));g.window("target",2);g.window("target",1);assertNull(g.merge(ticket,home(),List.of(),"target",1,2,3));assertTrue(e.active());}
    @Test public void windowChangeInvalidatesReservedDecision(){Engine e=new Engine(x->{});ObservationGate g=new ObservationGate(e,x->{});e.start(0,0);g.bind("target",1);var d=e.observe(Semantic.inspect(home()),1);g.window("target",2);e.submitted(d,true,2);assertEquals(Engine.State.HOME,e.state);assertFalse(e.busy());assertEquals(0,e.actions[0]);}
}

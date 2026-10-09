package app.get1won;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class Beta9RegressionTest {
    private static Semantic.Node n(int id,int parent,String text,int x,int y,int w,int h,boolean click,String source) {
        return new Semantic.Node(id,parent,text,new Semantic.Box(x,y,x+w,y+h),click,true,source);
    }
    private static Semantic.Node label(){return n(2,1,"내 포인트",40,200,210,60,false,"Accessibility");}
    private static Semantic.Node ocr(){return n(100,-1,"내 포인트",43,203,205,59,false,"OCR");}
    private static Semantic.Node priority(){return n(100000,-1,"내 포인트",40,200,210,60,false,"Accessibility");}
    private static Semantic.Scene scene(Semantic.Node... extra) {
        var nodes=new ArrayList<>(List.of(
            n(0,-1,"페이스페이 알림 받고 포인트 받기",30,190,260,80,true,"Accessibility"),
            n(1,-1,"",20,180,900,160,true,"Accessibility"),label(),
            n(3,1,"5,674원",40,270,210,40,false,"Accessibility"),
            n(4,1,"출금",700,270,150,40,false,"Accessibility")));
        nodes.addAll(List.of(extra));return SemanticTest.scene(nodes.toArray(new Semantic.Node[0]));
    }
    @Test public void reproducesOldGlobalContainmentPromotionToPromo() {
        assertEquals(0,Semantic.clickParent(scene(),ocr()).id());
        assertEquals(0,Semantic.clickParent(scene(),priority()).id());
        assertEquals(1,PointsTarget.resolve(scene(),ocr()).target().id());
        assertEquals(1,PointsTarget.resolve(scene(),priority()).target().id());
    }
    @Test public void priorityCopyResolvesThroughActualNativeParent() {
        var s=scene(priority());assertNotNull(Semantic.inspect(s).points());
        assertEquals(1,PointsTarget.resolve(s,priority()).target().id());
    }
    @Test public void splitNativeEvidenceUsesSharedRealParentNeverSyntheticHandle() {
        var nodes=new ArrayList<>(scene().nodes());nodes.removeIf(n->n.id()==2);
        nodes.add(n(5,1,"내",40,200,40,60,false,"Accessibility"));
        nodes.add(n(6,1,"포인트",85,200,165,60,false,"Accessibility"));
        var s=new Semantic.Scene(nodes,scene().screen());var evidence=Semantic.inspect(s).points();
        assertNotNull(evidence);assertTrue(evidence.id()<0);
        assertEquals(1,PointsTarget.resolve(s,evidence).target().id());
    }
    @Test public void neverUsesOcrHandleOrDetachedLabelBoundsFallback() {
        assertNull(PointsTarget.resolve(SemanticTest.scene(ocr(),scene().nodes().get(0)),ocr()).target());
        assertNull(PointsTarget.resolve(SemanticTest.scene(priority(),scene().nodes().get(0)),priority()).target());
    }
    @Test public void rejectsOnlyConnectedPromotionalContext() {
        for(String text:List.of("알림","혜택 알림","포인트 받기","내 근처 혜택","이벤트","동의","출석")) {
            var nodes=new ArrayList<>(scene().nodes());nodes.removeIf(n->n.id()==1);
            nodes.add(n(1,-1,text,20,180,900,160,true,"Accessibility"));
            assertNull(text,PointsTarget.resolve(new Semantic.Scene(nodes,scene().screen()),ocr()).target());
        }
        assertEquals(1,PointsTarget.resolve(scene(n(20,-1,"이벤트",0,1000,1000,100,true,"Accessibility")),ocr()).target().id());
    }
    @Test public void rejectsPromotionalDescendantAndLocalParentEvenForClickableLabel() {
        assertNull(PointsTarget.resolve(scene(n(7,1,"알림 동의",300,270,200,40,false,"Accessibility")),ocr()).target());
        var clickable=n(2,1,"내 포인트",40,200,210,60,true,"Accessibility");
        var promo=n(1,-1,"알림 받고 포인트 받기",20,180,900,160,true,"Accessibility");
        assertNull(PointsTarget.resolve(SemanticTest.scene(clickable,promo),ocr()).target());
    }
    @Test public void distantDuplicateAndStaleEvidenceNeverResolve() {
        assertNull(PointsTarget.resolve(scene(n(8,-1,"내 포인트",40,1500,210,60,true,"Accessibility")),ocr()).target());
        assertNull(PointsTarget.resolve(scene(),n(80,-1,"내 포인트",40,1500,210,60,false,"OCR")).target());
    }
    @Test public void priorityAncestorCopiesRetainTreeLocalContext() {
        var nativeRow=n(100001,-1,"",20,180,900,160,true,"Accessibility");
        var nativeLabel=n(100000,100001,"내 포인트",40,200,210,60,false,"Accessibility");
        var s=scene(nativeRow,nativeLabel,n(7,1,"알림 동의",300,270,200,40,false,"Accessibility"));
        assertNull(PointsTarget.resolve(s,nativeLabel).target());
        // Bounded query parent chain works even if the general BFS never reaches this row.
        var partial=SemanticTest.scene(nativeRow,nativeLabel,scene().nodes().get(0));
        assertEquals(100001,PointsTarget.resolve(partial,ocr()).target().id());
    }
    @Test public void separatePointsRowIsNotVetoedBySmallPagePromotion() {
        var nodes=new ArrayList<>(scene().nodes());nodes.removeIf(n->n.id()==1);
        nodes.add(n(1,9,"",20,180,900,160,true,"Accessibility"));
        nodes.add(n(9,-1,"페이스페이 알림 받고 포인트 받기",0,170,1000,180,false,"Accessibility"));
        assertEquals(1,PointsTarget.resolve(new Semantic.Scene(nodes,scene().screen()),ocr()).target().id());
    }
    @Test public void exactClickableNativeLabelIsPreferredToParent() {
        var nodes=new ArrayList<>(scene().nodes());nodes.removeIf(n->n.id()==2);
        nodes.add(n(2,1,"내 포인트",40,200,210,60,true,"Accessibility"));
        assertEquals(2,PointsTarget.resolve(new Semantic.Scene(nodes,scene().screen()),ocr()).target().id());
    }
    @Test public void acceptedWrongPageIsNotHistorySuccessOrAutomaticBack() {
        Engine e=new Engine(x->{});e.start(0,0);e.state=Engine.State.WAIT_FOR_POINTS;
        var d=e.observe(new Semantic.Found(label(),null,null,null,null,false),1);
        e.submitted(d,true,2);assertEquals(Engine.State.POINTS_ENTRY,e.state);
        assertNull(e.observe(Semantic.inspect(SemanticTest.scene(n(10,-1,"내 근처 혜택 알림 받고 포인트 받기",0,0,900,200,false,"Accessibility"))),3));
        assertEquals(0,e.actions[2]);assertEquals(0,e.completed);
        assertEquals(Engine.Action.BACK_HISTORY,e.observe(new Semantic.Found(null,null,null,null,null,true),4).action());
        assertEquals(1,e.actions[2]);
    }
    @Test public void failureSummaryHasTwoShortLinesWithoutBalance() {
        var r=PointsTarget.resolve(scene(),ocr());Engine e=new Engine(x->{});e.pause("내 포인트를 열지 못했어요.");
        var text=StopSummary.format("beta9",e,"ok","POINTS_ENTRY","","",r.selectedSummary(ocr()),r.targetSummary());
        assertEquals(13,text.lines().count());assertTrue(text.contains("source=OCR"));
        assertTrue(text.contains("clickable=true context=points"));assertFalse(text.contains("5,674"));
        assertFalse(StopSummary.format("beta9",e,"ok","HOME").contains("POINTS target"));
    }
}


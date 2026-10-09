package app.get1won;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class Beta10RegressionTest {
    private static Semantic.Node n(int id,int parent,String text,int y,int height,boolean click) {
        return new Semantic.Node(id,parent,text,new Semantic.Box(20,y,950,y+height),click,true,"Accessibility");
    }
    private static Semantic.Node ocr() {
        return new Semantic.Node(100,-1,"내 포인트",new Semantic.Box(40,200,250,260),false,true,"OCR");
    }
    private static Semantic.Node balance(int parent,String value){return n(parent+1,parent,value,270,40,false);}
    private static Semantic.Node withdrawal(int parent){return n(parent+2,parent,"출금",270,40,false);}
    private static Semantic.Scene scene(Semantic.Node... nodes){return SemanticTest.scene(nodes);}
    @Test public void caseAOnlyOcrLabelResolvesNativeBalanceAndWithdrawal() {
        for(String amount:List.of("123원","5,787원","12,345원","100P","잔액 321포인트")) {
            var s=scene(n(10,-1,"",180,160,true),balance(10,amount),withdrawal(10));
            assertNull(Semantic.pointsEvidence(s).node());
            var result=PointsTarget.resolve(s,ocr());assertEquals(10,result.target().id());
            assertEquals("Accessibility",result.target().source());assertTrue(result.target().clickable());
            assertEquals("points_structure",result.context());
        }
    }
    @Test public void caseBNestedWrappersChooseNarrowestRealSubtree() {
        var s=scene(n(1,-1,"",170,200,true),n(2,1,"",175,180,true),
            n(10,2,"",180,160,true),balance(10,"5,787원"),withdrawal(10));
        assertEquals(10,PointsTarget.resolve(s,ocr()).target().id());
    }
    @Test public void caseCPromotionsStayRejectedEvenWithValidStructure() {
        for(String promo:List.of("페이스페이 혜택 알림 받고 포인트 받기","페이스페이 혜택","알림","혜택 알림","포인트 받기","내 근처 혜택","이벤트","동의","출석")) {
            var result=PointsTarget.resolve(scene(n(10,-1,promo,180,160,true),balance(10,"123원"),withdrawal(10)),ocr());
            assertNull(promo,result.target());assertEquals("promotion_rejected",result.context());
        }
        assertNull(PointsTarget.resolve(scene(n(10,-1,"페이스페이 혜택 알림 받고 포인트 받기",180,160,true)),ocr()).target());
    }
    @Test public void caseDBalanceWithoutWithdrawalIsInsufficient() {
        var result=PointsTarget.resolve(scene(n(10,-1,"",180,160,true),balance(10,"123원")),ocr());
        assertNull(result.target());assertEquals("no_points_structure",result.context());
    }
    @Test public void caseEDistantBalanceIsInsufficientEvenUnderSameAncestor() {
        assertNull(PointsTarget.resolve(scene(n(10,-1,"",180,160,true),n(11,10,"123원",1500,40,false),withdrawal(10)),ocr()).target());
        assertNull(PointsTarget.resolve(scene(n(10,-1,"",180,160,true),n(11,-1,"123원",270,40,false),withdrawal(10)),ocr()).target());
    }
    @Test public void caseFDistinctRegionsRemainAmbiguousEvenAtIdenticalBounds() {
        var result=PointsTarget.resolve(scene(n(10,-1,"",180,160,true),balance(10,"123원"),withdrawal(10),
            n(20,-1,"",180,160,true),balance(20,"123원"),withdrawal(20)),ocr());
        assertNull(result.target());assertEquals("ambiguous_points_regions",result.context());
    }
    @Test public void localPromoChildAndSameFootprintParentVetoButUnrelatedPromoDoesNot() {
        var row=n(10,-1,"",180,160,true);var b=balance(10,"123원");var w=withdrawal(10);
        assertNull(PointsTarget.resolve(scene(row,b,w,n(13,10,"알림",300,30,false)),ocr()).target());
        assertNull(PointsTarget.resolve(scene(n(10,1,"",180,160,true),b,w,n(1,-1,"알림",180,160,false)),ocr()).target());
        assertEquals(10,PointsTarget.resolve(scene(row,b,w,n(1,-1,"알림",180,160,true)),ocr()).target().id());
        assertEquals(10,PointsTarget.resolve(scene(n(10,1,"",180,160,true),b,w,n(1,-1,"페이스페이 혜택",0,1900,false)),ocr()).target().id());
    }
    @Test public void requiresNativeStructureAndEnabledFullyVisibleClickableContainer() {
        var row=n(10,-1,"",180,160,true);var w=withdrawal(10);
        var fake=new Semantic.Node(11,10,"123원",balance(10,"123원").box(),false,true,"OCR");
        assertNull(PointsTarget.resolve(scene(row,fake,w),ocr()).target());
        assertNull(PointsTarget.resolve(scene(n(10,-1,"",180,160,false),balance(10,"123원"),w),ocr()).target());
        assertNull(PointsTarget.resolve(scene(new Semantic.Node(10,-1,"",row.box(),true,false,"Accessibility"),balance(10,"123원"),w),ocr()).target());
        assertNull(PointsTarget.resolve(scene(n(10,-1,"",-10,400,true),balance(10,"123원"),w),ocr()).target());
    }
    @Test public void stalePositionAndNonOcrEvidenceNeverUseStructureFallback() {
        var s=scene(n(10,-1,"",180,160,true),balance(10,"123원"),withdrawal(10));
        var old=new Semantic.Node(100,-1,"내 포인트",new Semantic.Box(40,1500,250,1560),false,true,"OCR");
        assertNull(PointsTarget.resolve(s,old).target());
        assertEquals("no_native_label",PointsTarget.resolve(s,n(100,-1,"내 포인트",200,60,false)).context());
    }
    @Test public void nearbyDistinctNativeTargetsDoNotEnableOcrFallback() {
        var s=scene(n(10,-1,"",180,160,true),balance(10,"123원"),withdrawal(10),
            n(30,-1,"내 포인트",200,60,true),n(31,-1,"내 포인트",200,60,true));
        assertNull(PointsTarget.resolve(s,ocr()).target());
    }
    @Test public void structuralTargetCanBeSubmittedButStillNeedsHistory() {
        var fresh=scene(n(10,-1,"",180,160,true),balance(10,"123원"),withdrawal(10));
        var found=Semantic.inspect(Semantic.mergeOcr(fresh,List.of(ocr())));
        Engine e=new Engine(x->{});e.start(0,0);e.state=Engine.State.WAIT_FOR_POINTS;
        var decision=e.observe(found,1);assertEquals(Engine.Action.POINTS,decision.action());
        assertEquals(10,PointsTarget.resolve(fresh,decision.target()).target().id());
        e.submitted(decision,true,2);assertEquals(0,e.actions[2]);assertEquals(0,e.completed);
        assertEquals(Engine.Action.BACK_HISTORY,e.observe(new Semantic.Found(null,null,null,null,null,true),3).action());
    }
    @Test public void summaryDoesNotExposeBalanceOrGrowBeyondExistingTwoLines() {
        var r=PointsTarget.resolve(scene(n(10,-1,"",180,160,true),balance(10,"5,787원"),withdrawal(10)),ocr());
        String s=r.selectedSummary(ocr())+"\n"+r.targetSummary();
        assertEquals(2,s.lines().count());assertFalse(s.contains("5,787"));assertTrue(s.contains("context=points_structure"));
    }
}

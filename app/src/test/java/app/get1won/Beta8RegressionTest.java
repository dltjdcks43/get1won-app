package app.get1won;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class Beta8RegressionTest {
    private static final String ANCHOR="다시 혜택 구경하고 1원 받아요";
    private static Semantic.Node n(int id,int parent,String text,String desc,int x,int y,int w,int h,String source){return new Semantic.Node(id,parent,text,new Semantic.Box(x,y,x+w,y+h),false,true,source,desc,"");}
    private static Semantic.Node point(String text,String description){return n(1,-1,text,description,40,200,600,60,"Accessibility");}
    private static Semantic.Scene home(List<Semantic.Node> points){var ns=new ArrayList<>(points);ns.add(n(90,-1,ANCHOR,"",40,800,700,60,"Accessibility"));ns.add(new Semantic.Node(91,-1,"상품 안내",new Semantic.Box(40,940,950,1060),true,true,"Accessibility"));return SemanticTest.scene(ns.toArray(new Semantic.Node[0]));}
    @Test public void clearTextIgnoresUnrelatedDescriptionAndStillDecidesAd(){
        for(var p:List.of(point("내 포인트","페이스페이 알림 받고 1원 받고 켜기"),point("내 포인트","혜택 알림"),point("내 포인트 5,674원 출금","상단 프로모션 알림"),point("","내 포인트 5,674원 출금"))){
            var f=Semantic.inspect(home(List.of(p)));assertNotNull(f.points());assertNotNull(f.anchor());assertNotNull(f.ad());Engine e=new Engine(x->{});e.start(0,0);assertEquals(Engine.Action.AD,e.observe(f,1).action());
        }
    }
    @Test public void wrongEffectiveTextOrDescriptionRemainsRejected(){
        for(var p:List.of(point("내 포인트 알림 설정",""),point("","내 포인트 알림 설정"),point("내 포인트 알림 설정","내 포인트"))){assertNull(Semantic.inspect(home(List.of(p))).points());}
        var local=n(10,-1,"","내 포인트 이벤트",20,180,900,120,"Accessibility");var child=n(1,10,"내 포인트","",40,200,300,60,"Accessibility");
        assertNull(Semantic.inspect(home(List.of(local,child))).points());
    }
    @Test public void duplicateCopiesWithPromoDescriptionsAreNotConflicts(){
        var p=point("내 포인트","혜택 알림");var copy=n(100000,-1,p.text(),p.description(),40,200,600,60,"Accessibility");
        var s=home(List.of(p,copy));assertEquals(2,Semantic.pointsEvidence(s).candidates());assertNotNull(Semantic.inspect(s).points());
        var merged=Semantic.mergeOcr(s,List.of(n(20,-1,"내 포인트","",43,203,300,55,"OCR")));assertNotNull(Semantic.inspect(merged).points());
    }
    @Test public void distantPointsStillAmbiguous(){assertNull(Semantic.inspect(home(List.of(point("내 포인트","혜택 알림"),n(2,-1,"내 포인트","",40,1500,600,60,"Accessibility")))).points());}
    @Test public void fullRowAndInkOnlyOcrCollapseWithoutChangingSharedGeometry(){
        var row=n(1,-1,ANCHOR,"",0,750,1080,140,"Accessibility");var ink=n(2,-1,ANCHOR,"",120,803,570,35,"OCR");
        assertFalse(Semantic.samePosition(row.box(),ink.box()));
        var f=Semantic.inspect(SemanticTest.scene(row,ink));assertNotNull(f.anchor());assertEquals("Accessibility",f.anchor().source());
        assertEquals("none",Semantic.anchorEvidence(SemanticTest.scene(row,ink)).reason());
    }
    @Test public void parentDescriptionChildTextAndOcrRemainOneAnchor(){
        var parent=n(10,-1,"",ANCHOR,0,730,1080,180,"Accessibility");var child=n(1,10,ANCHOR,"",40,790,800,60,"Accessibility");var ocr=n(2,-1,ANCHOR,"",43,803,700,35,"OCR");
        assertSame(child,Semantic.inspect(SemanticTest.scene(parent,child,ocr)).anchor());
    }
    @Test public void realDistinctAnchorsAndBridgingRowRemainAmbiguous(){
        var row=n(1,-1,ANCHOR,"",0,750,1080,140,"Accessibility");
        assertNull(Semantic.inspect(SemanticTest.scene(row,n(2,-1,ANCHOR,"",40,1500,700,40,"OCR"))).anchor());
        var left=n(2,-1,ANCHOR,"",40,803,320,35,"OCR");var right=n(3,-1,ANCHOR,"",700,803,320,35,"OCR");
        assertNull(Semantic.inspect(SemanticTest.scene(row,left,right)).anchor());
    }
    @Test public void rowCollapseRequiresSamePhraseAndCrossSource(){
        var row=n(1,-1,ANCHOR,"",0,750,1080,140,"Accessibility");
        assertNull(Semantic.inspect(SemanticTest.scene(row,n(2,-1,"다른 상품 구경하고 1원 받아요","",120,803,570,35,"OCR"))).anchor());
        assertNull(Semantic.inspect(SemanticTest.scene(row,n(2,-1,ANCHOR,"",120,803,570,35,"Accessibility"))).anchor());
    }
}

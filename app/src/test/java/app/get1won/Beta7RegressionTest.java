package app.get1won;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class Beta7RegressionTest {
    private static Semantic.Node n(int id,int parent,String text,String desc,int x,int y,int w,int h,String source){return new Semantic.Node(id,parent,text,new Semantic.Box(x,y,x+w,y+h),false,true,source,desc,"");}
    private static Semantic.Scene home(List<Semantic.Node> points){
        var ns=new ArrayList<>(points);ns.add(n(90,-1,"다시 혜택 구경하고 1원 받아요","",40,800,700,60,"Accessibility"));
        ns.add(new Semantic.Node(91,-1,"상품 안내",new Semantic.Box(40,940,950,1060),true,true,"Accessibility"));return SemanticTest.scene(ns.toArray(new Semantic.Node[0]));
    }
    private static List<Semantic.Node> region(String pageDescription){return new ArrayList<>(List.of(
        n(0,-1,"",pageDescription,20,100,950,240,"Accessibility"),
        n(5,0,"1원 받고 혜택 알림 켜기","",40,110,850,60,"Accessibility"),
        n(10,0,"","",30,185,930,145,"Accessibility"),
        n(1,10,"내 포인트","",40,200,300,60,"Accessibility"),
        n(2,10,"5,674원","",40,275,220,40,"Accessibility"),
        n(3,10,"출금","",650,275,150,40,"Accessibility")
    ));}
    private static void assertAd(Semantic.Scene s){var f=Semantic.inspect(s);assertNotNull(f.points());assertNotNull(f.anchor());assertNotNull(f.ad());Engine e=new Engine(x->{});e.start(0,0);assertEquals(Engine.Action.AD,e.observe(f,1).action());}
    @Test public void pagePromoOutsideLocalPointsDoesNotReject(){assertAd(home(region("1원 받고 혜택 알림 켜기")));}
    @Test public void shortAncestorBenefitsNotificationDoesNotReject(){assertAd(home(region("혜택 알림")));}
    @Test public void aggregatedPagePointsAndPromoCopyDoesNotReject(){assertAd(home(region("1원 받고 혜택 알림 켜기 내 포인트 5,674원 출금")));}
    @Test public void ocrAndPriorityCopyDoNotInheritPagePromotion(){
        var nodes=region("혜택 알림");nodes.add(n(100000,-1,"내 포인트","",40,200,300,60,"Accessibility"));
        var fresh=home(nodes);var merged=Semantic.mergeOcr(fresh,List.of(n(30,-1,"내 포인트","",43,203,295,55,"OCR")));assertAd(merged);
        var noNative=region("혜택 알림 내 포인트 5,674원 출금");noNative.removeIf(n->n.id()==1);
        assertAd(Semantic.mergeOcr(home(noNative),List.of(n(30,-1,"내 포인트","",43,203,295,55,"OCR"))));
    }
    @Test public void exactTwoObservationsAreOneTarget(){
        var s=Semantic.mergeOcr(home(region("혜택 알림")),List.of(n(30,-1,"내 포인트","",43,203,295,55,"OCR")));
        var p=Semantic.pointsEvidence(s);assertEquals(2,p.candidates());assertEquals("none",p.reason());assertAd(s);
    }
    @Test public void localMenuTextAndLocalDescriptionStillReject(){
        for(String text:List.of("내 포인트 알림 설정","내 포인트 이벤트","광고 내 포인트","내 포인트 출석 이벤트","내 포인트 동의하고 1원 받기")){
            var direct=home(List.of(n(1,-1,text,"",40,200,600,60,"Accessibility")));assertNull(Semantic.inspect(direct).points());
            var nodes=List.of(n(10,-1,"",text,20,180,900,120,"Accessibility"),n(1,10,"내 포인트","",40,200,300,60,"Accessibility"));
            var merged=Semantic.mergeOcr(home(nodes),List.of(n(30,-1,"내 포인트","",43,203,295,55,"OCR")));
            assertNull(Semantic.inspect(merged).points());assertEquals("context_rejected",Semantic.pointsEvidence(merged).reason());
            Engine e=new Engine(x->{});e.start(0,0);assertNull(e.observe(Semantic.inspect(merged),1));
        }
    }
    @Test public void realDistantPointsRemainAmbiguousDespiteValidLocalRegion(){
        var nodes=region("혜택 알림");nodes.add(n(8,-1,"내 포인트","",40,1500,300,60,"Accessibility"));
        assertEquals("ambiguous_distinct_targets",Semantic.pointsEvidence(home(nodes)).reason());assertNull(Semantic.inspect(home(nodes)).points());
    }
}

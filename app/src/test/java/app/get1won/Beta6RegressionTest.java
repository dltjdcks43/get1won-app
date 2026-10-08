package app.get1won;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class Beta6RegressionTest {
    private static Semantic.Node n(int id,int parent,String text,String desc,int x,int y,int w,int h,String source){return new Semantic.Node(id,parent,text,new Semantic.Box(x,y,x+w,y+h),false,true,source,desc,"");}
    private static Semantic.Node label(String t){return n(1,-1,t,"",40,200,600,60,"Accessibility");}
    private static Semantic.Scene home(List<Semantic.Node> points){
        List<Semantic.Node> ns=new ArrayList<>(points);ns.add(n(90,-1,"다시 구경하고 1원 받아요","",40,800,700,60,"Accessibility"));
        ns.add(new Semantic.Node(91,-1,"상품 안내",new Semantic.Box(40,940,950,1060),true,true,"Accessibility"));
        return SemanticTest.scene(ns.toArray(new Semantic.Node[0]));
    }
    private static List<List<Semantic.Node>> variants(){
        var parent=n(10,-1,"","내 포인트 5,471원 출금",20,150,950,230,"Accessibility");
        var child=n(1,10,"내 포인트","",40,200,300,60,"Accessibility");
        var empty=n(10,-1,"","",20,150,950,230,"Accessibility");
        var ocr=n(20,-1,"내 포인트","",43,203,300,60,"OCR");
        return List.of(
            List.of(label("내 포인트")), // A
            List.of(label("내 포인트 5,471원")), // B
            List.of(label("내 포인트 5,471원 출금")), // C
            List.of(parent,child), // D
            List.of(empty,child,n(2,10,"5,471원","",40,270,200,50,"Accessibility"),n(3,10,"출금","",600,270,100,50,"Accessibility")), // E
            List.of(n(1,-1,"내","",40,200,60,60,"Accessibility"),n(2,-1,"포인트","",110,200,180,60,"Accessibility")), // F
            List.of(label("내 포인트"),ocr), // G
            List.of(ocr), // H
            List.of(ocr,n(21,-1,"5,471원","",40,280,200,60,"OCR")), // I
            List.of(label("내\n포인트")), // J
            List.of(label("내 포인트 잔액 5,471원 출금")),
            List.of(n(10,-1,"","내 포인트 잔액 5,471원 출금",20,150,950,230,"Accessibility"),child),
            List.of(n(1,-1,"내","",40,200,60,60,"OCR"),n(2,-1,"포인트","",40,270,180,60,"OCR"))
        );
    }
    @Test public void allAToJVariantsAndBalanceContextProduceAdDecision(){
        for(var variant:variants()){
            var s=home(variant);var f=Semantic.inspect(s);
            assertNotNull(variant.toString(),f.points());assertNotNull(f.anchor());assertNotNull(f.ad());
            assertEquals("none",Semantic.pointsEvidence(s).reason());
            Engine e=new Engine(x->{});e.start(0,0);var d=e.observe(f,1);assertNotNull(d);assertEquals(Engine.Action.AD,d.action());
        }
    }
    @Test public void ocrMergeUsesFreshNativeAnchorAndAd(){
        Engine e=new Engine(x->{});e.start(0,0);ObservationGate gate=new ObservationGate(e,x->{});gate.bind("target",1);
        var fresh=home(List.of());var ticket=new OcrTicket(53,e.generation,e.cycleId,e.state,e.actionEpoch,1,"target",100,"",fresh.screen(),new Semantic.Box(0,0,0,0));
        var f=gate.merge(ticket,fresh,List.of(n(1,-1,"내 포인트","",43,203,300,60,"OCR")),"target",1,101,102);
        assertEquals("OCR",f.points().source());assertEquals("Accessibility",f.anchor().source());assertEquals(Engine.Action.AD,e.observe(f,102).action());
    }
    @Test public void menusAdsAndEventsDoNotCreateAdDecisions(){
        for(String t:List.of("내 포인트 알림 설정","다른 이벤트 내 포인트","광고 내 포인트","내 포인트 출금","내 포인트 확인","내 포인트 1원 받기")){
            var s=home(List.of(label(t)));var f=Semantic.inspect(s);assertNull(f.points());assertNotNull(f.anchor());assertNotNull(f.ad());
            assertEquals("context_rejected",Semantic.pointsEvidence(s).reason());Engine e=new Engine(x->{});e.start(0,0);assertNull(e.observe(f,1));
        }
    }
    @Test public void nativeContextAlsoRejectsOcrAndPriorityCopies(){
        var parent=n(10,-1,"","내 포인트 알림 설정",20,180,800,120,"Accessibility");
        var child=n(1,10,"내 포인트","",40,200,300,60,"Accessibility");
        var copy=n(100000,-1,"내 포인트","",40,200,300,60,"Accessibility");
        var ocr=n(20,-1,"내 포인트","",43,203,295,55,"OCR");
        var s=home(List.of(parent,child,copy,ocr));assertNull(Semantic.inspect(s).points());assertEquals("context_rejected",Semantic.pointsEvidence(s).reason());
    }
    @Test public void splitChildrenOfBalanceRegionRemainOneTarget(){
        var parent=n(10,-1,"","내 포인트 잔액 5,471원 출금",20,150,950,230,"Accessibility");
        var a=n(1,10,"내","",40,200,60,60,"Accessibility");var b=n(2,10,"포인트","",110,200,180,60,"Accessibility");
        var f=Semantic.inspect(home(List.of(parent,a,b)));assertNotNull(f.points());assertEquals(a.box().union(b.box()),f.points().box());
    }
    @Test public void farFragmentsAndDistantActualLabelsStayUnusable(){
        assertNull(Semantic.inspect(home(List.of(n(1,-1,"내","",40,200,60,60,"Accessibility"),n(2,-1,"포인트","",700,200,180,60,"Accessibility")))).points());
        var s=home(List.of(label("내 포인트"),n(2,-1,"내 포인트","",40,1500,600,60,"OCR")));
        assertNull(Semantic.inspect(s).points());assertEquals("points candidates=2 reject=ambiguous_distinct_targets",Semantic.pointsEvidence(s).summary());
        Engine e=new Engine(x->{});e.start(0,0);assertNull(e.observe(Semantic.inspect(s),1));
    }
    @Test public void homeTimeoutNamesMissingEvidenceWithoutClaimingTransition(){
        Engine e=new Engine(x->{});e.start(0,0);var f=Semantic.inspect(home(List.of()));assertNull(e.observe(f,1));assertNull(e.observe(f,30000));
        assertEquals("none",e.lastAction);assertTrue(e.reason.contains("HOME 화면 요소"));assertTrue(e.reason.contains("points missing"));assertFalse(e.reason.contains("화면 전환"));
        Engine deferred=new Engine(x->{});deferred.start(0,0);deferred.observe(Semantic.inspect(home(List.of(label("내 포인트")))),30000);assertTrue(deferred.reason.contains("HOME 광고 입력"));
        Engine submitted=new Engine(x->{});submitted.start(0,0);var valid=Semantic.inspect(home(List.of(label("내 포인트"))));var d=submitted.observe(valid,1);submitted.submitted(d,true,2);submitted.observe(valid,30002);assertTrue(submitted.reason.contains("화면 전환"));
    }
    @Test public void summaryAddsOnlyOnePointsLineAndNeverBalance(){
        Engine e=new Engine(x->{});e.start(0,0);var s=home(List.of());e.observe(Semantic.inspect(s),1);e.pause("HOME elements missing");
        String report=StopSummary.format("beta6",e,"ticket=53","IDLE -> HOME",Semantic.anchorEvidence(s).summary(),Semantic.pointsEvidence(s).summary());
        assertEquals(13,report.split("\n").length);assertTrue(report.contains("points candidates=0 reject=label_missing"));assertFalse(report.contains("5,471"));
    }
}

package app.get1won;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Same-window fresh scenes only; these are not physical-device test results. */
public class Beta5RegressionTest {
    private static Semantic.Node n(int id,String text,int x,int y,int w,String source){return new Semantic.Node(id,-1,text,new Semantic.Box(x,y,x+w,y+50),false,true,source);}
    private static Semantic.Scene scene(Semantic.Node... nodes){return SemanticTest.scene(nodes);}
    private static Semantic.Scene home(List<Semantic.Node> anchor){
        List<Semantic.Node> ns=new ArrayList<>(anchor);ns.add(n(90,"내 포인트",40,200,300,"OCR"));
        ns.add(new Semantic.Node(91,-1,"상품 안내",new Semantic.Box(40,1000,950,1120),true,true,"Accessibility"));return scene(ns.toArray(new Semantic.Node[0]));
    }
    private static Semantic.Found only(String t){return Semantic.inspect(scene(n(1,t,40,300,800,"Accessibility")));}
    private static List<List<Semantic.Node>> variants(){
        List<List<Semantic.Node>> out=new ArrayList<>();
        for(String source:List.of("Accessibility","OCR")){
            out.add(List.of(n(1,"여기서 구경하면, 1 원 받아요!",40,800,650,source)));
            out.add(List.of(n(1,"지금 구경하면!",40,800,300,source),n(2,"보너스 1 원 받아",350,803,280,source)));
            out.add(List.of(n(1,"혜택을 구경하고,",40,800,300,source),n(2,"1 원을",350,802,100,source),n(3,"받아요!",460,798,150,source)));
        }
        out.add(List.of(n(1,"여기서 구경하면!",40,800,300,"Accessibility"),n(2,"1 원 받아요",350,803,280,"OCR")));
        out.add(List.of(n(1,"구경하면",40,800,300,"OCR"),n(2,"1원",350,803,100,"Accessibility"),n(3,"받아!",460,800,150,"OCR")));
        out.add(List.of(n(1,"구경하면",40,800,600,"Accessibility"),n(2,"1원 받아요",350,803,280,"OCR")));
        out.add(List.of(n(1,"지금 구경하면",40,800,300,"OCR"),n(2,"l원 받아요!",350,800,280,"OCR")));
        return out;
    }
    @Test public void sourcesPunctuationParticlesAndMinorOcrGlyphs(){for(var v:variants()){
        var f=Semantic.inspect(home(v));assertNotNull(v.toString(),f.anchor());assertNotNull(f.ad());assertTrue(f.home());
    }}
    @Test public void cycleOneThen53FreshOcrObservationsThenSecondAd(){
        for(var v:variants()){
            Engine e=new Engine(s->{});e.start(0,0);long now=1;
            var first=e.observe(Semantic.inspect(home(List.of(n(1,"구경하면 1원 받아요",40,800,650,"Accessibility")))),now++);e.submitted(first,true,now++);
            e.observe(only("3초 구경해요"),now++);var back=e.observe(only("1원 받았어요"),now++);assertEquals(Engine.Action.BACK_REWARD,back.action());e.submitted(back,true,now++);
            var points=e.observe(only("내 포인트"),now++);e.submitted(points,true,now++);
            var history=Semantic.inspect(scene(n(1,"전체",40,200,300,"Accessibility"),n(2,"적립 +1원",40,400,300,"Accessibility"),n(3,"사용 -2원",40,600,300,"Accessibility")));
            var bh=e.observe(history,now++);assertEquals(Engine.Action.BACK_HISTORY,bh.action());e.submitted(bh,true,now++);
            var pointOnly=Semantic.inspect(home(List.of()));assertNull(e.observe(pointOnly,now++));assertEquals(1,e.completed);assertEquals(2,e.cycleId);assertEquals(Engine.State.HOME,e.state);
            ObservationGate gate=new ObservationGate(e,s->{});gate.bind("target",7);
            var nativeNodes=home(v).nodes().stream().filter(n->n.source().equals("Accessibility")).toList();
            var ocrNodes=home(v).nodes().stream().filter(n->n.source().equals("OCR")).toList();
            for(int ticket=1;ticket<=53;ticket++){
                long time=now+ticket*500;
                var fresh=ticket==53?new Semantic.Scene(nativeNodes,home(v).screen()):scene();
                var data=ticket==53?ocrNodes:List.of(n(90,"내 포인트",40,200,300,"OCR"));
                var t=new OcrTicket(ticket,e.generation,e.cycleId,e.state,e.actionEpoch,7,"target",time,"",fresh.screen(),new Semantic.Box(0,0,0,0));
                var merged=gate.merge(t,fresh,data,"target",7,time,time+1);assertNotNull(merged);
                var d=e.observe(merged,time+1);
                if(ticket<53){assertNull(d);assertTrue(e.needsOcr(merged,time+1));}
                else {assertEquals("OCR",merged.points().source());assertNotNull(merged.anchor());assertNotNull(merged.ad());assertEquals(Engine.Action.AD,d.action());}
            }
            assertEquals(1,e.completed);
        }
    }
    @Test public void sameAreaFullOcrAndMixedEvidenceDoNotBecomeAmbiguous(){
        var ns=new ArrayList<>(variants().get(6));ns.add(n(3,"구경하면 1원 받아요",43,804,588,"OCR"));
        assertNotNull(Semantic.inspect(home(ns)).anchor());
        ns.add(n(4,"구경하면 1원 받아요",40,1500,650,"OCR"));
        assertNull(Semantic.inspect(home(ns)).anchor());assertEquals("ambiguous_distinct_targets",Semantic.anchorEvidence(home(ns)).reason());
    }
    @Test public void distantAndReverseOrderEvidenceCannotBridge(){
        assertNull(Semantic.inspect(scene(n(1,"구경하면",40,800,200,"Accessibility"),n(2,"1원 받아요",700,800,300,"OCR"))).anchor());
        assertNull(Semantic.inspect(scene(n(1,"구경하면",40,800,300,"OCR"),n(2,"1원 받아요",40,1600,300,"OCR"))).anchor());
        assertNull(Semantic.inspect(scene(n(1,"받아요",40,800,150,"OCR"),n(2,"1원 구경",500,800,200,"OCR"))).anchor());
    }
    @Test public void otherEventsNeverBecomeAnchors(){
        for(String t:List.of("동의하고 1원 받기","알림 켜고 1원 받기","출석하고 1원 받기","다른 1원 적립 이벤트","동의하고 구경하면 1원 받아요")) {
            assertNull(only(t).anchor());
            assertNull(Semantic.inspect(scene(n(1,"구경하면",40,800,250,"Accessibility"),n(2,t,300,800,600,"OCR"))).anchor());
        }
        assertNull(Semantic.inspect(scene(n(1,"구경",40,800,150,"OCR"),n(2,"동의",195,800,50,"OCR"),n(3,"1원 받아요",250,800,300,"OCR"))).anchor());
    }
    @Test public void noBrowseGuessingOrOtherAmounts(){
        for(String t:List.of("구겅하면 1원 받아요","구경하면 11원 받아요","구경하면 10원 받아요","구경하면 l원 받아요"))assertNull(only(t).anchor());
        for(String glyph:List.of("l","I","|"))assertNotNull(Semantic.inspect(scene(n(1,"구경하면 "+glyph+"원 받아요",40,800,600,"OCR"))).anchor());
    }
    @Test public void noAnchorlessFallbackAndBoundedRegion(){
        assertNull(Semantic.inspect(home(List.of())).ad());
        var wide=new Semantic.Node(1,-1,"구경하면",new Semantic.Box(0,500,1080,1800),false,true,"Accessibility");
        assertNull(Semantic.inspect(scene(wide,n(2,"1원 받아요",40,1000,300,"OCR"))).anchor());
    }
    @Test public void shortSummaryIncludesOneRejectLine(){
        Engine e=new Engine(s->{});e.start(0,0);var s=home(List.of(n(1,"1원 받아요",40,800,300,"OCR")));e.observe(Semantic.inspect(s),1);e.pause("HOME timeout");
        var diagnostic=Semantic.anchorEvidence(s);assertEquals("browse_missing",diagnostic.reason());
        String report=StopSummary.format("beta5",e,"성공 ticket=53","HOME_AFTER_HISTORY -> HOME",diagnostic.summary());
        assertEquals(12,report.split("\n").length);assertTrue(report.contains("anchor candidates=0 reject=browse_missing"));
    }
}

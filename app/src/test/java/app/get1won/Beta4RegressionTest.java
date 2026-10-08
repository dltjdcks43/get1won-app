package app.get1won;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class Beta4RegressionTest {
    private static Semantic.Node n(int id,String text,int x,int y,int width) {
        return new Semantic.Node(id,-1,text,new Semantic.Box(x,y,x+width,y+50),false,true,"Accessibility");
    }
    private static Semantic.Scene scene(Semantic.Node... ns){return SemanticTest.scene(ns);}
    private static Semantic.Found only(String text){return Semantic.inspect(scene(n(1,text,40,200,800)));}
    private static Semantic.Scene home(int y,Semantic.Node... parts){
        var nodes=new ArrayList<>(List.of(parts));nodes.add(n(90,"내 포인트",40,200,300));
        nodes.add(new Semantic.Node(91,-1,"광고 상품",new Semantic.Box(40,y+200,950,y+300),true,true,"Accessibility"));
        return scene(nodes.toArray(new Semantic.Node[0]));
    }
    @Test public void singleVariantsAndWhitespace(){for(String t:List.of("다시 혜택 구경하고 1원 받아요","여기서 구경하면 1원 받아요","구경하면 1원 받아요","여기서\n 구경하면 1원\n받아요"))assertNotNull(only(t).anchor());}
    @Test public void twoFragmentsHorizontalVerticalAndReverseTreeOrder(){
        for(var parts:List.of(new Semantic.Node[]{n(1,"여기서 구경하면",40,800,300),n(2,"1원 받아요",350,800,200)},new Semantic.Node[]{n(1,"여기서 구경하면",40,800,300),n(2,"1원 받아요",40,860,200)})){
            assertNotNull(Semantic.inspect(scene(parts)).anchor());assertNotNull(Semantic.inspect(scene(parts[1],parts[0])).anchor());
        }
    }
    @Test public void threeFragmentsCAndD(){
        for(String[] t:List.of(new String[]{"여기서 구경하면","1원","받아요"},new String[]{"여기서","구경하면 1원","받아요"})) {
            var f=Semantic.inspect(home(800,n(1,t[0],40,800,270),n(2,t[1],320,800,260),n(3,t[2],590,800,160)));
            assertTrue(f.home());assertNotNull(f.ad());
        }
        assertNotNull(Semantic.inspect(scene(n(1,"여기서 구경하면",40,800,300),n(2,"1원",40,860,100),n(3,"받아요",40,920,150))).anchor());
    }
    @Test public void splitAccessibilityAndOcrLineAreOneAnchor(){
        var fresh=home(800,n(1,"여기서 구경하면",40,800,270),n(2,"1원",320,800,100),n(3,"받아요",430,800,150));
        var f=Semantic.inspect(Semantic.mergeOcr(fresh,List.of(n(100,"여기서 구경하면 1원 받아요",43,803,540))));
        assertNotNull(f.anchor());assertEquals("Accessibility",f.anchor().source());assertNotNull(f.ad());
    }
    @Test public void jitteredWholeAnchorAndActualDistantDuplicate(){
        var a=n(1,"구경하면 1원 받아요",40,800,650);
        var f=Semantic.inspect(Semantic.mergeOcr(scene(a),List.of(n(2,a.text(),43,803,655))));assertSame(a,f.anchor());
        assertNull(Semantic.inspect(scene(a,n(2,a.text(),40,1500,650))).anchor());
    }
    @Test public void parentDescriptionAndChildTextOrFragments(){
        var parent=new Semantic.Node(10,-1,"",new Semantic.Box(20,700,980,950),false,true,"Accessibility","여기서 구경하면 1원 받아요","");
        var child=new Semantic.Node(1,10,parent.description(),new Semantic.Box(40,800,700,850),false,true,"Accessibility");
        assertSame(child,Semantic.inspect(scene(parent,child)).anchor());
        var a=new Semantic.Node(2,10,"여기서 구경하면",new Semantic.Box(40,800,310,850),false,true,"Accessibility");
        var b=new Semantic.Node(3,10,"1원",new Semantic.Box(320,800,420,850),false,true,"Accessibility");
        var c=new Semantic.Node(4,10,"받아요",new Semantic.Box(430,800,580,850),false,true,"Accessibility");
        assertEquals(a.box().union(b.box()).union(c.box()),Semantic.inspect(scene(parent,a,b,c)).anchor().box());
    }
    @Test public void farFragmentsAndUnrelatedEventDoNotJoin(){
        assertNull(Semantic.inspect(scene(n(1,"구경하면",40,800,250),n(2,"1원",600,800,100),n(3,"받아요",710,800,150))).anchor());
        assertNull(Semantic.inspect(scene(n(1,"구경하면",40,800,250),n(2,"동의하고 1원 받아요",40,860,500))).anchor());
        assertNull(Semantic.inspect(scene(n(1,"구경하면",40,800,250),n(2,"1원",40,1200,100),n(3,"받아요",40,1260,150))).anchor());
    }
    @Test public void delayedAnchorKeepsRequestingFreshOcrUntilDeadline(){
        Engine e=new Engine(s->{});e.start(0,0);
        for(long now:new long[]{1,1000,10000,29000}){assertNull(e.observe(only("내 포인트"),now));assertTrue(e.active());assertTrue(e.needsOcr(only("내 포인트"),now));}
        var f=Semantic.inspect(home(800,n(1,"구경하면 1원 받아요",40,800,650)));
        assertEquals(Engine.Action.AD,e.observe(f,29999).action());
        Engine timeout=new Engine(s->{});timeout.start(0,0);assertNull(timeout.observe(only("내 포인트"),30000));assertEquals(Engine.State.PAUSED,timeout.state);
    }
    @Test public void fiveCompletedCyclesThenFreshSixthHome(){
        Engine e=new Engine(s->{});e.start(0,0);long now=1;
        for(int cycle=0;cycle<5;cycle++){
            var d=e.observe(Semantic.inspect(home(800,n(1,"구경하면 1원 받아요",40,800,650))),now++);assertEquals(Engine.Action.AD,d.action());e.submitted(d,true,now++);
            assertNull(e.observe(only("3초 구경해요"),now++));var back=e.observe(only("1원 받았어요"),now++);e.submitted(back,true,now++);
            var points=e.observe(only("내 포인트"),now++);e.submitted(points,true,now++);
            var history=Semantic.inspect(scene(n(1,"전체",40,200,300),n(2,"적립 +1원",40,400,300),n(3,"사용 -2원",40,600,300)));
            var bh=e.observe(history,now++);assertEquals(Engine.Action.BACK_HISTORY,bh.action());e.submitted(bh,true,now++);
            assertNull(e.observe(only("내 포인트"),now++));assertEquals(cycle+1,e.completed);
        }
        assertEquals(6,e.cycleId);assertEquals(Engine.State.HOME,e.state);
        var old=new OcrTicket(1,e.generation,e.cycleId-1,e.state,e.actionEpoch,1,"target",now,"",home(800).screen(),new Semantic.Box(0,0,0,0));
        assertFalse(old.current(e.generation,e.cycleId,e.state,e.actionEpoch));
        assertNull(e.observe(only("내 포인트"),now+10000));assertTrue(e.needsOcr(only("내 포인트"),now+10000));
        var fresh=Semantic.inspect(home(1100,n(1,"여기서 구경하면",40,1100,270),n(2,"1원",320,1100,100),n(3,"받아요",430,1100,150)));
        var next=e.observe(fresh,now+11000);assertSame(fresh.ad(),next.target());assertEquals(1300,next.target().box().top());assertEquals(5,e.completed);
    }
    @Test public void stopSummaryIsElevenLinesWithMissingEvidenceAndSource(){
        Engine e=new Engine(s->{});e.start(0,0);e.observe(Semantic.inspect(Semantic.mergeOcr(scene(),List.of(n(1,"내 포인트",40,200,300)))),1);e.pause("timeout\nHOME");
        String report=StopSummary.format("3.0-beta4 (test)",e,"성공\nticket=1","HISTORY -> HOME");
        assertEquals(11,report.split("\n").length);assertTrue(report.contains("points=found source=OCR"));assertTrue(report.contains("anchor=not found"));assertTrue(report.contains("pause reason: timeout HOME"));
    }
}

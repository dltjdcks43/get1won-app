package app.get1won;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class Beta15RegressionTest {
    private static final Semantic.Box SCREEN=new Semantic.Box(0,0,1441,3120);
    private static Semantic.Node node(int id,String text,int l,int t,int r,int b,boolean click,String source) {
        return new Semantic.Node(id,-1,text,new Semantic.Box(l,t,r,b),click,true,source);
    }
    private static final Semantic.Node ANCHOR=node(1,"여기서 구경하면 1원 받아요",90,1778,1353,1872,false,"Accessibility");
    private static final Semantic.Node CARD=node(2,"",0,2236,1441,2476,true,"Accessibility");
    private static final Semantic.Node TITLE=new Semantic.Node(3,2,"광고 상품",new Semantic.Box(100,2300,450,2340),false,true,"Accessibility");
    private static final Semantic.Node POINTS=node(4,"내 포인트",90,500,400,560,false,"OCR");
    private static Semantic.Scene nativeScene(){return new Semantic.Scene(List.of(ANCHOR,CARD,TITLE),SCREEN);}
    private static List<Semantic.Node> lines(int count) {
        // First OCR line crosses the native card's top edge; contained lines alone are not ambiguous.
        var result=new ArrayList<Semantic.Node>();
        result.add(node(10,"씨제이제일제당(주) 비비고",120,2218,1200,2266,false,"OCR"));
        result.add(node(11,"영양삼계탕, 800g",120,2270,1100,2318,false,"OCR"));
        if(count==3)result.add(node(12,"상품 안내",120,2330,1100,2378,false,"OCR"));
        return result;
    }
    @Test public void twoOcrLinesDoNotReplaceNativeCard(){assertEquals(CARD,Semantic.inspect(Semantic.mergeOcr(nativeScene(),lines(2))).ad());}
    @Test public void threeOcrLinesDoNotReplaceNativeCard(){assertEquals(CARD,Semantic.inspect(Semantic.mergeOcr(nativeScene(),lines(3))).ad());}
    @Test public void actualNativeAmbiguityRemains() {
        var left=node(20,"왼쪽 상품",0,2236,650,2476,true,"Accessibility");
        var right=node(21,"오른쪽 상품",750,2236,1441,2476,true,"Accessibility");
        var s=Semantic.mergeOcr(new Semantic.Scene(List.of(ANCHOR,left,right),SCREEN),lines(2));
        var f=Semantic.inspect(s);assertNull(f.ad());assertTrue(Semantic.diagnostics(s,f).contains("ambiguous_cards"));
    }
    @Test public void excludedChildrenFromEitherSourceStillRejectCard() {
        for(String source:List.of("Accessibility","OCR"))for(String text:List.of("동의","알림","내 포인트","출금")) {
            var nodes=new ArrayList<>(nativeScene().nodes());nodes.add(node(30,text,200,2380,600,2430,false,source));
            assertNull(source+text,Semantic.inspect(new Semantic.Scene(nodes,SCREEN)).ad());
        }
    }
    @Test public void ocrPointsAndNativeAnchorCardStartHomeAction() {
        var ocr=new ArrayList<>(lines(2));ocr.add(POINTS);var f=Semantic.inspect(Semantic.mergeOcr(nativeScene(),ocr));
        assertTrue(f.home());assertEquals("OCR",f.points().source());assertEquals(CARD,f.ad());
        var e=new Engine(x->{});e.start(0,0);assertEquals(Engine.Action.AD,e.observe(f,100).action());
    }
    @Test public void missingPointsStillWaitsForNormalOcrObservation() {
        var e=new Engine(x->{});e.start(0,0);var nativeFound=Semantic.inspect(nativeScene());
        assertEquals(CARD,nativeFound.ad());assertFalse(nativeFound.home());assertNull(e.observe(nativeFound,100));assertTrue(e.active());
        var merged=Semantic.inspect(Semantic.mergeOcr(nativeScene(),List.of(POINTS)));
        assertEquals(Engine.Action.AD,e.observe(merged,350).action());
    }
    @Test public void nativeSelectionSurvivesMergeAndDiagnosticsOnlyCountTitles() {
        var before=Semantic.inspect(nativeScene());var scene=Semantic.mergeOcr(nativeScene(),lines(2));var after=Semantic.inspect(scene);
        assertEquals(before.ad(),after.ad());String log=Semantic.diagnostics(scene,after);
        assertTrue(log.contains("nativeCandidates=1"));assertTrue(log.contains("ocrSupport=2"));
        assertFalse(log.contains("비비고"));assertFalse(log.contains("영양삼계탕"));
    }
    @Test public void ocrLinesAloneAreNotCards() {
        var scene=Semantic.mergeOcr(new Semantic.Scene(List.of(ANCHOR),SCREEN),lines(2));var f=Semantic.inspect(scene);
        assertNull(f.ad());assertTrue(Semantic.diagnostics(scene,f).contains("no_eligible_card"));
    }
    @Test public void containedOcrTitleStillSupportsBlankNativeCard() {
        var title=node(40,"상품 제목",100,2300,1100,2350,false,"OCR");
        assertEquals(CARD,Semantic.inspect(Semantic.mergeOcr(new Semantic.Scene(List.of(ANCHOR,CARD),SCREEN),List.of(title))).ad());
    }
}

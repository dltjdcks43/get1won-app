package app.get1won;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class Beta16RegressionTest {
    private static Semantic.Node nativePoint(String text,String description,boolean click,int right,int id) {
        return new Semantic.Node(id,-1,text,new Semantic.Box(90,769,right,889),click,true,"Accessibility",description,"");
    }
    private static Semantic.Scene scene(Semantic.Node... nodes){return new Semantic.Scene(List.of(nodes),new Semantic.Box(0,0,1441,3120));}
    @Test public void nativeTextSummaryIsAccepted(){assertNotNull(Semantic.inspect(scene(nativePoint("내포인트6508원내역보기","",false,1050,1))).points());}
    @Test public void nativeDescriptionSummaryIsAccepted(){var n=nativePoint("","내포인트6508원내역보기",true,393,2);assertEquals(n,Semantic.inspect(scene(n)).points());}
    @Test public void balanceFormatsAndWhitespaceAreAccepted() {
        for(String s:List.of("내포인트","내포인트6508원","내포인트6,508원","내포인트6,508원내역보기","내포인트6508P내역보기","내포인트6508포인트내역보기","내 포인트\n6,508원\n내역 보기","내포인트잔액:6508원내역보기출금›","내포인트6508출금>"))
            assertNotNull(s,Semantic.inspect(scene(nativePoint(s,"",false,1050,1))).points());
    }
    @Test public void nativeHomeNeedsNoOcrPoints() {
        var p=nativePoint("내포인트6508원내역보기","",false,1050,1);
        var a=new Semantic.Node(3,-1,"여기서 구경하면 1원 받아요",new Semantic.Box(90,1778,1353,1872),false,true,"Accessibility");
        var ad=new Semantic.Node(4,-1,"광고 상품",new Semantic.Box(0,2236,1441,2476),true,true,"Accessibility");
        var f=Semantic.inspect(scene(p,a,ad));assertTrue(f.home());assertNotNull(f.ad());assertEquals("Accessibility",f.points().source());
        var e=new Engine(x->{});e.start(0,0);assertEquals(Engine.Action.AD,e.observe(f,100).action());
    }
    @Test public void sameRowNativeAndOcrRemainOneTarget() {
        var p=nativePoint("내포인트6508원내역보기","",false,1050,1);
        var ocr=new Semantic.Node(9,-1,"내포인트",new Semantic.Box(94,775,390,884),false,true,"OCR");
        assertEquals(p,Semantic.inspect(Semantic.mergeOcr(scene(p),List.of(ocr))).points());
    }
    @Test public void smallerClickableNativeWinsWithoutChangingUnique() {
        var wide=nativePoint("내포인트6508원내역보기","",false,1050,1);
        var small=nativePoint("","내포인트6508원내역보기",true,393,2);
        var s=scene(wide,small);var f=Semantic.inspect(s);assertEquals(small,f.points());
        String diagnostic=Semantic.diagnostics(s,f);assertTrue(diagnostic.contains("points nativeCandidates=2 ocrCandidates=0 selected=Accessibility"));
        assertFalse(diagnostic.contains("6508"));
    }
    @Test public void promotionsAndMalformedSummariesStayRejected() {
        for(String s:List.of("내포인트100원받기","내포인트혜택알림받기","내포인트이벤트","내포인트출석","내포인트알림동의","내포인트적립이벤트","내포인트혜택보기","내포인트내역보기","내포인트6508내역보기","내포인트,원내역보기","내포인트6,,508원내역보기")) {
            assertFalse(s,Semantic.points(s));assertNull(s,Semantic.inspect(scene(nativePoint(s,"",false,1050,1))).points());
        }
    }
    @Test public void distantNativeSummariesRemainAmbiguous() {
        var a=nativePoint("내포인트6508원내역보기","",false,1050,1);
        var b=new Semantic.Node(2,-1,a.text(),new Semantic.Box(90,1200,1050,1320),true,true,"Accessibility");
        assertNull(Semantic.inspect(scene(a,b)).points());
    }
}

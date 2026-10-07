package app.get1won;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SemanticTest {
    static Semantic.Node node(String text,int l,int t,int r,int b,String source,boolean click) {return new Semantic.Node(t,-1,text,new Semantic.Box(l,t,r,b),click,true,source);}
    static Semantic.Node access(String text,int t,int b) {return node(text,40,t,700,b,"Accessibility",false);}
    static Semantic.Scene scene(Semantic.Node... nodes) {return new Semantic.Scene(List.of(nodes),new Semantic.Box(0,0,1080,2200));}
    static Semantic.Scene home(String phrase,String title) {return scene(node("내 포인트",40,200,250,260,"Accessibility",true),access(phrase,800,880),node(title,40,940,900,1060,"Accessibility",true));}
    @Test public void variantsAndArbitraryTitles() {
        for(String phrase:List.of("다시 구경하고 1원 받아요","다시 혜택 구경하고 1원 받아요","여기서 구경하면 1원 받아요","한번 더 구경하고 1원 받아요","여기서\n혜택 구경하고\n1원 받아요")) {
            Semantic.Found f=Semantic.inspect(home(phrase,UUID.randomUUID().toString()));assertTrue(f.home());assertNotNull(f.ad());
        }
    }
    @Test public void unrelatedRewardsExcluded() {
        for(String text:List.of("쿠폰 혜택 알림 동의하고 1원 받기","동의문 10초 만에 동의하고 1원 받기","매장 혜택 소식 받고 1원 받기")) assertNull(Semantic.inspect(scene(access(text,800,880))).anchor());
    }
    @Test public void splitAnchor() {
        var f=Semantic.inspect(scene(access("내 포인트",200,260),access("다시 혜택 구경하고",800,840),access("1원 받아요",850,890),access("새로운 상품 안내",950,1010)));
        assertTrue(f.home());assertNotNull(f.ad());
    }
    @Test public void distantFragmentsAreNotJoined() {assertNull(Semantic.inspect(scene(access("구경하고",400,450),access("1원 받아요",800,850))).anchor());}
    @Test public void sideBySideUnrelatedFragmentsAreNotJoined() {assertNull(Semantic.inspect(scene(node("구경하고",0,400,200,450,"OCR",false),node("1원 받아요",600,460,900,510,"OCR",false))).anchor());}
    @Test public void accessibilityPointsWinsOverShiftedOcr() {
        var fresh=scene(node("내 포인트",40,200,250,260,"Accessibility",true));
        var merged=Semantic.mergeOcr(fresh,List.of(node("내 포인트",43,203,248,262,"OCR",false)));
        assertEquals("Accessibility",Semantic.inspect(merged).points().source());
    }
    @Test public void accessibilityAnchorWinsOverShiftedOcr() {
        var fresh=scene(node("다시 구경하면 1원 받아요",40,800,700,880,"Accessibility",false));
        var merged=Semantic.mergeOcr(fresh,List.of(node("다시 구경하면 1원 받아요",43,803,705,884,"OCR",false)));
        assertEquals("Accessibility",Semantic.inspect(merged).anchor().source());
    }
    @Test public void freshApplicationSceneMergedWithOcrHasHomeAndCard() {
        var fresh=home("다시 구경하면 1원 받아요","광고 제목은 바뀝니다");
        var merged=Semantic.mergeOcr(fresh,List.of(node("내 포인트",43,203,248,262,"OCR",false),node("다시 구경하면 1원 받아요",43,803,705,884,"OCR",false),node("광고 제목은 바뀝니다",45,943,895,1062,"OCR",false)));
        var found=Semantic.inspect(merged);assertNotNull(found.points());assertNotNull(found.anchor());assertTrue(found.home());assertNotNull(found.ad());
    }
    @Test public void distantDuplicateAnchorsStayAmbiguous() {assertNull(Semantic.inspect(scene(access("다시 구경하면 1원 받아요",400,480),access("다시 구경하면 1원 받아요",1400,1480))).anchor());}
    @Test public void broadBoxCannotBridgeDistantObservations() {assertNull(Semantic.inspect(scene(access("내 포인트",200,260),access("내 포인트",1000,1060),access("내 포인트",0,1800))).points());}
    @Test public void semanticDuplicatesCanDifferInWording() {
        var f=Semantic.inspect(scene(access("다시 구경하고 1원 받아요",800,880),node("여기서 구경하면 1원 받아요",43,803,705,884,"OCR",false)));assertNotNull(f.anchor());
    }
    @Test public void otherRewardCardNeverChosen() {var f=Semantic.inspect(home("구경하고 1원 받아요","쿠폰 혜택 알림 동의하고 1원 받기"));assertTrue(f.home());assertNull(f.ad());}
    @Test public void twoSideBySideCardsAreAmbiguous() {
        var f=Semantic.inspect(scene(access("구경하고 1원 받아요",800,880),node("광고 왼쪽",20,920,480,1020,"Accessibility",true),node("광고 오른쪽",500,920,1050,1020,"Accessibility",true)));assertNull(f.ad());
    }
    @Test public void nearestCardIsChosen() {var f=Semantic.inspect(scene(access("구경하고 1원 받아요",800,880),access("첫 번째 콘텐츠",920,1020),access("다음 콘텐츠",1100,1200)));assertEquals(920,f.ad().box().top());}
    @Test public void historyNeedsTwoIndependentFeatures() {
        assertTrue(Semantic.inspect(scene(access("전체",200,260),access("방문 적립 +1원",400,460),access("포인트 사용 -10원",600,660))).history());
        assertFalse(Semantic.inspect(scene(access("전체",200,260),access("방문 적립 +1원",400,460))).history());
        assertFalse(Semantic.inspect(scene(access("방문 적립 +1원",400,460),access("포인트 사용 -10원",600,660))).history());
    }
    @Test public void duplicatedHistoryRowIsNotTwoRows() {assertFalse(Semantic.inspect(scene(access("전체",200,260),access("방문 적립 +1원",400,460),node("방문 적립 +1원",43,403,701,464,"OCR",false))).history());}
    @Test public void waitingAndCompletionUseTokens() {var f=Semantic.inspect(scene(access("3초 동안 혜택을 구경해주세요",400,450),access("축하해요! 1원 받았어요",800,850)));assertNotNull(f.waiting());assertNotNull(f.complete());}
    @Test public void splitCompletionRecognized() {assertNotNull(Semantic.inspect(scene(access("1원",400,450),access("받았어요",460,510))).complete());}
}

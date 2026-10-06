package app.get1won;
import org.junit.Test;
import static org.junit.Assert.*;
public class CompletionTextTest {
 @Test public void acceptsVisibleSentenceVariants(){assertTrue(CompletionText.matches("1원 받았어요."));assertTrue(CompletionText.matches("1원\n받았어요"));assertTrue(CompletionText.matches("1원\u00a0받았어요"));}
 @Test public void rejectsWrongAmountAndExtraWords(){for(String s:new String[]{"11원 받았어요","1원 받았어요?","아직 1원 받았어요 아님","1원 받았어요 / 3초 구경해요",""})assertFalse(s,CompletionText.matches(s));}
 @Test public void waitingVetoSupportsBothLabels(){assertTrue(CompletionText.waiting("3초 구경해요"));assertTrue(CompletionText.waiting("3초\n구경해주세요"));assertFalse(CompletionText.waiting("1원 받았어요"));}
}

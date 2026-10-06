package app.get1won;
import org.junit.Test;
import static org.junit.Assert.*;
public class CompletionTextTest {
    @Test public void acceptsOnlyTheCompleteVisiblePhrase(){assertTrue(CompletionText.matches("1원 받았어요"));assertTrue(CompletionText.matches("1원\n받았어요"));assertFalse(CompletionText.matches("11원 받았어요"));assertFalse(CompletionText.matches("1원 받았어요?"));assertFalse(CompletionText.matches("아직 1원 받았어요 아님"));assertFalse(CompletionText.matches("3초 구경해요"));assertFalse(CompletionText.matches(null));}
}

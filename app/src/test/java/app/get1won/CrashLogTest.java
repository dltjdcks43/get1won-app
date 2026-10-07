package app.get1won;
import org.junit.Test;
import static org.junit.Assert.*;
public class CrashLogTest {
 @Test public void recordsCauseStateCycleAndStack(){String log=CrashLog.describe(new IllegalStateException("reader closed",new RuntimeException("cause")),Engine.State.OPEN_REWARD_AD,3);assertTrue(log.contains("java.lang.IllegalStateException"));assertTrue(log.contains("reader closed"));assertTrue(log.contains("OPEN_REWARD_AD"));assertTrue(log.contains("cycleId=3"));assertTrue(log.contains("Caused by:"));assertTrue(log.contains("CrashLogTest"));}
}

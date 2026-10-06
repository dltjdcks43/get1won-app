package app.get1won;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;
public class StartRequestTest {
    private float[] screen(){float[] a=new float[ScreenStability.SIZE];Arrays.fill(a,.5f);return a;}
    @Test public void waitsFiveTenThirtySecondsWithoutExpiring(){for(int delay:new int[]{5000,10000,30000}){StartRequest s=new StartRequest();s.request(7);float[] a=screen();for(long t=1;t<=delay;t+=100)assertFalse(s.frame(7,t*1_000_000,false,false,a));assertTrue(s.pending());for(long t=delay+1;t<delay+251;t+=50)assertFalse(s.frame(7,t*1_000_000,true,false,a));assertTrue(s.frame(7,(delay+251)*1_000_000L,true,false,a));assertFalse(s.pending());}}
    @Test public void stopAndOldGenerationCannotStart(){StartRequest s=new StartRequest();float[] a=screen();s.request(4);s.cancel();assertFalse(s.frame(4,1000000000,true,false,a));s.request(5);assertFalse(s.frame(4,2000000000,true,false,a));assertTrue(s.pending());}
    @Test public void foregroundLossResetsStableInterval(){StartRequest s=new StartRequest();float[] a=screen();s.request(1);s.frame(1,1,true,false,a);s.frame(1,200000001,true,false,a);s.frame(1,210000001,false,false,a);assertFalse(s.frame(1,300000001,true,false,a));assertFalse(s.frame(1,500000001,true,false,a));assertTrue(s.frame(1,550000001,true,false,a));}
    @Test public void waitingOrCompletionCannotStartFromWrongScreen(){StartRequest s=new StartRequest();s.request(1);for(int n=1;n<100;n++)assertFalse(s.frame(1,n*100000000L,true,true,screen()));assertTrue(s.pending());}
}

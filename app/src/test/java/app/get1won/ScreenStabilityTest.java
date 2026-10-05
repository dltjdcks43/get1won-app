package app.get1won;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ScreenStabilityTest {
    private float[] scene(float value){float[] a=new float[ScreenStability.SIZE];Arrays.fill(a,value);return a;}
    @Test public void noChangeNeverPasses(){ScreenStability s=new ScreenStability();float[] a=scene(.2f);s.begin(a);for(int i=1;i<100;i++)assertFalse(s.accept(a,i*33_000_000L));}
    @Test public void changedThen250msQuiet(){ScreenStability s=new ScreenStability();s.begin(scene(.2f));float[] b=scene(.6f);assertFalse(s.accept(b,1));assertFalse(s.accept(b,100_000_001));assertFalse(s.accept(b,249_000_001));assertTrue(s.accept(b,250_000_001));}
    @Test public void gapCannotCountAsContinuousStability(){ScreenStability s=new ScreenStability();s.begin(scene(.2f));float[] b=scene(.6f);assertFalse(s.accept(b,1));assertFalse(s.accept(b,500_000_001));assertFalse(s.accept(b,600_000_001));}
    @Test public void slowDriftComparedAgainstAnchor(){ScreenStability s=new ScreenStability();s.begin(scene(.1f));for(int i=0;i<40;i++)assertFalse(s.accept(scene(.4f+i*.004f),i*33_000_000L+1));}
    @Test public void maskedPixelsDoNotCauseTransition(){ScreenStability s=new ScreenStability();float[] a=scene(.2f),b=scene(.2f);for(int i=0;i<500;i++){a[i]=Float.NaN;b[i]=.8f;}s.begin(a);for(int i=1;i<30;i++)assertFalse(s.accept(b,i*33_000_000L));}
    @Test public void tooLittleVisibleContentFailsClosed(){ScreenStability s=new ScreenStability();float[] a=scene(.2f);Arrays.fill(a,Float.NaN);s.begin(a);assertFalse(s.accept(scene(.8f),500_000_000));}
    @Test public void localizedMotionResetsStability(){ScreenStability s=new ScreenStability();s.begin(scene(.1f));float[] a=scene(.7f);for(int i=0;i<30;i++){a[20*48+20]=i%2;for(int y=12;y<24;y++)for(int x=12;x<24;x++)a[y*48+x]=i%2==0?.3f:.8f;assertFalse(s.accept(a,i*33_000_000L+1));}}
    @Test public void returningToOldScreenAfterFlashCannotPass(){ScreenStability s=new ScreenStability();float[] old=scene(.2f);s.begin(old);assertFalse(s.accept(scene(.8f),1));for(int i=1;i<20;i++)assertFalse(s.accept(old,i*33_000_000L));}
}

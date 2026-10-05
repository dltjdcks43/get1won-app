package app.get1won;

import org.junit.Test;
import static org.junit.Assert.*;
public class MatcherTest {
    private float[] text(){float[] a=new float[160*48];java.util.Arrays.fill(a,.8f);for(int y=12;y<36;y++)for(int x=20;x<140;x++)if((x/4+y/4)%3==0)a[y*160+x]=.1f;return a;}
    @Test public void toleratesBrightness(){float[] a=text(),b=a.clone();for(int i=0;i<b.length;i++)b[i]+=.05f;assertTrue(Matcher.score(a,b,160)>.99);}
    @Test public void blankWhiteCannotMatch(){float[] a=new float[160*48];java.util.Arrays.fill(a,1);assertEquals(-1,Matcher.score(a,a,160),0);assertTrue(Matcher.score(a,text(),160)<.96);}
    @Test public void differentLetterFeaturesFail(){float[] a=text(),b=a.clone();for(int y=12;y<36;y++)for(int x=20;x<140;x++)b[y*160+x]=1-a[y*160+x];assertTrue(Matcher.score(a,b,160)<.96);}
}

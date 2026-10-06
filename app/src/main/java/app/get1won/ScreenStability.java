package app.get1won;

/** Low-resolution luminance only. Masks are NaN; arrays are reused, never retained from a frame. */
public final class ScreenStability {
    public static final int WIDTH=48,HEIGHT=72,SIZE=WIDTH*HEIGHT;
    public static final long QUIET_NANOS=250_000_000L,MAX_FRAME_GAP=200_000_000L;
    private final float[] before=new float[SIZE],anchor=new float[SIZE];
    private boolean initialized,requireChange=true;
    private long quietSince=-1,lastTime;
    public void begin(float[] sample){clear();requireChange=true;if(valid(sample)){System.arraycopy(sample,0,before,0,SIZE);initialized=true;}}
    public void beginInitial(){clear();initialized=true;requireChange=false;}
    public void clear(){initialized=false;resetQuiet();}
    public void resetQuiet(){quietSince=-1;lastTime=0;}
    public static boolean valid(float[] a){if(a==null || a.length!=SIZE)return false;int count=0;for(float v:a)if(Float.isFinite(v))count++;return count>=SIZE*0.35;}
    /** Anchored, per-block differences prevent slow animation drift from accumulating unnoticed. */
    private static double difference(float[] a,float[] b){
        double max=0,total=0;int count=0;
        for(int by=0;by<6;by++)for(int bx=0;bx<4;bx++){
            double sum=0;int n=0;
            for(int y=by*12;y<(by+1)*12;y++)for(int x=bx*12;x<(bx+1)*12;x++){
                int i=y*WIDTH+x;if(Float.isFinite(a[i]) && Float.isFinite(b[i])){sum+=Math.abs(a[i]-b[i]);n++;}
            }
            if(n>=36)max=Math.max(max,sum/n);total+=sum;count+=n;
        }
        return count<SIZE*0.35?Double.POSITIVE_INFINITY:Math.max(max,count==0?1:total/count);
    }
    public boolean accept(float[] sample,long time){
        if(!initialized || !valid(sample)){resetQuiet();return false;}
        if(lastTime!=0 && time<=lastTime)return false;
        boolean gap=lastTime!=0 && time-lastTime>MAX_FRAME_GAP;lastTime=time;
        double movement=requireChange?difference(before,sample):1;
        if(!Double.isFinite(movement)){resetQuiet();return false;}
        if(movement<0.06){quietSince=-1;return false;}
        // A failed/no-op click or BACK cannot pass merely because the old screen is still.
        if(gap || quietSince<0 || difference(anchor,sample)>0.012){
            System.arraycopy(sample,0,anchor,0,SIZE);quietSince=time;return false;
        }
        return time-quietSince>=QUIET_NANOS;
    }
}

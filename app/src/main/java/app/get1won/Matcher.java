package app.get1won;

/** Brightness-tolerant comparison with separate edge agreement. Flat backgrounds never match. */
public final class Matcher {
    private Matcher() {}
    public static double contrast(float[] a) {
        double mean=0,var=0; for(float x:a)mean+=x; mean/=a.length;
        for(float x:a)var+=(x-mean)*(x-mean); return Math.sqrt(var/a.length);
    }
    public static double score(float[] a,float[] b,int width) {
        if(a.length!=b.length || a.length<width*2)return -1;
        double ma=0,mb=0;for(int i=0;i<a.length;i++){ma+=a[i];mb+=b[i];}ma/=a.length;mb/=b.length;
        double aa=0,bb=0,ab=0,err=0,ea=0,eb=0,eab=0;
        for(int i=0;i<a.length;i++) {
            double x=a[i]-ma,y=b[i]-mb;aa+=x*x;bb+=y*y;ab+=x*y;err+=Math.abs(x-y);
            if(i%width>0){double dx=a[i]-a[i-1],dy=b[i]-b[i-1];ea+=dx*dx;eb+=dy*dy;eab+=dx*dy;}
            if(i>=width){double dx=a[i]-a[i-width],dy=b[i]-b[i-width];ea+=dx*dx;eb+=dy*dy;eab+=dx*dy;}
        }
        if(aa/a.length<0.0003 || bb/b.length<0.0003 || ea<0.001 || eb<0.001 || err/a.length>0.10)return -1;
        return Math.min(ab/Math.sqrt(aa*bb),eab/Math.sqrt(ea*eb));
    }
}

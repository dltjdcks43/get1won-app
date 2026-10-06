package app.get1won;
import android.content.Context;
/** Preferences only: target app and pixel locations are never persisted. */
public final class Profile {
    public volatile int panelSize=2,repeats=0;
    public volatile int testDelay=0;
    public volatile boolean randomTest=true;
    public void load(Context c){
        var prefs=c.getSharedPreferences("preferences-v4",Context.MODE_PRIVATE);
        panelSize=Math.max(0,Math.min(3,prefs.getInt("panelSize",2)));repeats=Math.max(0,prefs.getInt("repeats",0));
        // Erase the obsolete coordinate/template profile, including AtomicFile recovery.
        new android.util.AtomicFile(new java.io.File(c.getFilesDir(),"profile.json")).delete();
    }
    public void save(Context c){c.getSharedPreferences("preferences-v4",Context.MODE_PRIVATE).edit().putInt("panelSize",panelSize).putInt("repeats",repeats).apply();}
    public void reset(Context c){panelSize=2;repeats=0;save(c);}
}

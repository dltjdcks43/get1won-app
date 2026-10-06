package app.get1won;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UsabilityTest {
    private final Instrumentation i=InstrumentationRegistry.getInstrumentation();
    private interface Check{boolean ok();}
    private void until(Check c,long ms,String message){long end=SystemClock.uptimeMillis()+ms;while(SystemClock.uptimeMillis()<end){if(c.ok())return;SystemClock.sleep(100);}fail(message+" "+AppState.advanced());}
    private List<TextView> texts(View root){List<TextView> list=new ArrayList<>();if(root instanceof TextView t)list.add(t);if(root instanceof ViewGroup g)for(int n=0;n<g.getChildCount();n++)list.addAll(texts(g.getChildAt(n)));return list;}
    private Button find(Activity a,String label){for(TextView t:texts(a.getWindow().getDecorView()))if(t instanceof Button b && label.contentEquals(t.getText()))return b;return null;}
    private void click(Activity a,String label){i.runOnMainSync(()->{Button b=find(a,label);assertNotNull(label,b);b.performClick();});}
    private MainActivity open(){return (MainActivity)i.startActivitySync(new Intent(i.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));}
    private void checkLayout(View root){for(TextView t:texts(root)){if(t.getVisibility()!=View.VISIBLE || t.getText().length()==0)continue;assertTrue("unmeasured "+t.getText(),t.getWidth()>0);if(t.getLayout()!=null){assertTrue("vertical clip "+t.getText(),t.getLayout().getHeight()<=t.getHeight()-t.getCompoundPaddingTop()-t.getCompoundPaddingBottom());}if(t.getLayout()!=null)for(int line=0;line<t.getLayout().getLineCount();line++)assertEquals("clipped "+t.getText(),0,t.getLayout().getEllipsisCount(line));if(t instanceof Button){float dp=t.getResources().getDisplayMetrics().density;assertTrue("small target "+t.getText(),t.getHeight()>=48*dp);}}}
    // UiAutomation executes an argument string directly, without shell quote/pipeline parsing.
    private void shot(UiDevice device,String name) throws java.io.IOException {String path="/data/local/tmp/get1won-ux/"+name+".png";device.executeShellCommand("mkdir -p /data/local/tmp/get1won-ux");device.executeShellCommand("screencap -p "+path);assertTrue("screenshot "+name,Long.parseLong(device.executeShellCommand("stat -c %s "+path).trim())>8);}
    @Test public void firstRunPermissionsPersistentStartAndLargeText() throws Exception {
        Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);UiDevice device=UiDevice.getInstance(i);device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        try {
            i.runOnMainSync(()->{AppState.stop();AppState.profile.invalidate();AppState.profile.save(i.getTargetContext());i.getTargetContext().stopService(new Intent(i.getTargetContext(),CaptureService.class));});
            device.executeShellCommand("settings put secure enabled_accessibility_services ''");device.executeShellCommand("settings put secure accessibility_enabled 0");until(()->AppState.accessibility==null && !AppState.capturing,10000,"initial permissions");
            MainActivity first=open();SystemClock.sleep(500);i.runOnMainSync(()->{assertNotNull(find(first,"처음 설정하기"));assertNull(find(first,"시작하기"));checkLayout(first.getWindow().getDecorView());});shot(device,"01-first-run");
            click(first,"처음 설정하기");i.runOnMainSync(()->assertNotNull(find(first,"설정 열기")));
            device.executeShellCommand("settings put secure enabled_accessibility_services app.get1won/app.get1won.AutomationService");device.executeShellCommand("settings put secure accessibility_enabled 1");device.executeShellCommand("pm grant app.get1won android.permission.POST_NOTIFICATIONS");until(()->AppState.accessibility!=null,15000,"accessibility permission");
            long end=SystemClock.uptimeMillis()+20000;while(!AppState.capturing && SystemClock.uptimeMillis()<end){UiObject2 yes=device.findObject(By.pkg("com.android.systemui").res("android:id/button1"));if(yes==null)yes=device.findObject(By.pkg("com.android.systemui").text(java.util.regex.Pattern.compile("(?i)Start now|Start recording|Start sharing|Start|Share")));if(yes!=null)yes.click();SystemClock.sleep(200);}until(()->AppState.capturing,5000,"automatic capture permission");
            i.runOnMainSync(()->{Profile p=AppState.profile;p.a=new Point(540,950);p.b=new Point(540,1220);p.targetPackage="app.get1won.uninstalled.fixture";p.autoVerified=true;p.setupDone=true;p.save(i.getTargetContext());});
            Profile restored=new Profile();restored.load(i.getTargetContext());assertTrue(restored.ready());assertTrue(restored.setupDone);assertEquals(2,restored.panelSize);
            MainActivity ready=open();SystemClock.sleep(500);i.runOnMainSync(()->{assertNotNull(find(ready,"시작하기"));assertNull(find(ready,"처음 설정하기"));});shot(device,"02-ready");
            long[] before=AppState.engine.actions.clone();click(ready,"시작하기");until(()->AppState.accessibility.startRequest.pending(),3000,"start pending");
            for(int seconds:new int[]{5,5,20}){SystemClock.sleep(seconds*1000L);assertTrue("start must survive 5/10/30s",AppState.accessibility.startRequest.pending());assertArrayEquals(before,AppState.engine.actions);}
            i.runOnMainSync(AppState::stop);assertFalse(AppState.accessibility.startRequest.pending());SystemClock.sleep(500);assertArrayEquals(before,AppState.engine.actions);
            for(String font:new String[]{"1.3","1.5"}){
                device.executeShellCommand("settings put system font_scale "+font);SystemClock.sleep(1200);MainActivity main=open();SystemClock.sleep(700);i.runOnMainSync(()->checkLayout(main.getWindow().getDecorView()));shot(device,"main-font-"+font);
                for(int size=0;size<4;size++){final int chosen=size;i.runOnMainSync(()->{AppState.profile.panelSize=chosen;AppState.accessibility.resizePanel();AppState.accessibility.prepareExternal("probe");});SystemClock.sleep(700);i.runOnMainSync(()->{View v=AppState.accessibility.controls();checkLayout(v);Rect bounds=AppState.accessibility.overlayBounds();assertNotNull("panel placement",bounds);assertTrue(bounds.width()<=device.getDisplayWidth());assertTrue(bounds.height()<=device.getDisplayHeight());assertFalse(bounds.contains(AppState.profile.a.x,AppState.profile.a.y));assertFalse(bounds.contains(AppState.profile.b.x,AppState.profile.b.y));});shot(device,"panel-"+size+"-font-"+font);i.runOnMainSync(AppState::stop);}
            }
        } finally {device.executeShellCommand("settings put system font_scale 1.0");i.runOnMainSync(()->{AppState.stop();AppState.profile.panelSize=2;AppState.profile.save(i.getTargetContext());});}
    }
}

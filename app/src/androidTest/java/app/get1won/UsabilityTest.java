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
    @Test public void floatingStartFourSizesAndThreeFontScales() throws Exception {
        Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);UiDevice device=UiDevice.getInstance(i);device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        try {
            i.runOnMainSync(()->{AppState.stop();AppState.profile.reset(i.getTargetContext());i.getTargetContext().stopService(new Intent(i.getTargetContext(),CaptureService.class));});
            device.executeShellCommand("settings delete secure enabled_accessibility_services");device.executeShellCommand("settings put secure accessibility_enabled 0");until(()->AppState.accessibility==null,10000,"initial permissions");
            MainActivity first=open();SystemClock.sleep(500);i.runOnMainSync(()->{assertNotNull(find(first,"처음 설정하기"));assertNull(find(first,"시작하기"));checkLayout(first.getWindow().getDecorView());});shot(device,"01-first-run");click(first,"처음 설정하기");i.runOnMainSync(()->assertNotNull(find(first,"접근성 설정 열기")));
            device.executeShellCommand("settings put secure enabled_accessibility_services app.get1won/app.get1won.AutomationService");device.executeShellCommand("settings put secure accessibility_enabled 1");until(()->AppState.accessibility!=null,15000,"accessibility permission");
            assertEquals(2,AppState.profile.panelSize);MainActivity ready=open();SystemClock.sleep(500);i.runOnMainSync(()->{assertNotNull(find(ready,"설정"));assertNull(find(ready,"시작하기"));assertNotNull(AppState.accessibility.controls());});shot(device,"02-ready");
            long[] counts=AppState.engine.actions.clone();UiObject2 start=device.wait(Until.findObject(By.pkg("app.get1won").text("시작")),5000);assertNotNull(start);start.click();until(()->AppState.engine.state==Engine.State.PAUSED,3000,"own main is not a start screen");assertArrayEquals(counts,AppState.engine.actions);i.runOnMainSync(AppState::stop);
            for(String font:new String[]{"1.0","1.3","1.5"}){
                device.executeShellCommand("settings put system font_scale "+font);SystemClock.sleep(1200);MainActivity main=open();SystemClock.sleep(700);i.runOnMainSync(()->checkLayout(main.getWindow().getDecorView()));shot(device,"main-font-"+font);
                for(int size=0;size<4;size++){final int chosen=size;i.runOnMainSync(()->{AppState.stop();AppState.profile.panelSize=chosen;AppState.accessibility.resizePanel();});SystemClock.sleep(700);i.runOnMainSync(()->{View v=AppState.accessibility.controls();checkLayout(v);Rect bounds=AppState.accessibility.overlayBounds();assertNotNull("panel placement",bounds);assertTrue(bounds.width()<=device.getDisplayWidth());assertTrue(bounds.height()<=device.getDisplayHeight());for(TextView t:texts(v))if(t instanceof Button)assertNotNull(t.getContentDescription());});shot(device,"panel-"+size+"-font-"+font);}
            }
        } finally {device.executeShellCommand("settings put system font_scale 1.0");i.runOnMainSync(()->{AppState.stop();AppState.profile.panelSize=2;AppState.profile.save(i.getTargetContext());});}
    }
}

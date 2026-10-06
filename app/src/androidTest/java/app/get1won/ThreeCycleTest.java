package app.get1won;
import android.app.*;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ThreeCycleTest {
    private final Instrumentation i=InstrumentationRegistry.getInstrumentation();
    private final UiDevice device=initialize();
    private UiDevice initialize(){Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);return UiDevice.getInstance(i);}
    private interface Check{boolean ok();}
    private String diagnostic(){return AppState.status()+"\n"+CaptureService.info+"\n"+(AppState.accessibility==null?"":AppState.accessibility.analysis())+"\n"+AppState.advanced();}
    private void until(Check c,long ms,String why){long end=SystemClock.uptimeMillis()+ms;while(SystemClock.uptimeMillis()<end){if(c.ok())return;SystemClock.sleep(100);}fail(why+"\n"+diagnostic());}
    private void floatingStart(){UiObject2 start=device.wait(Until.findObject(By.pkg("app.get1won").text("시작")),5000);assertNotNull(diagnostic(),start);start.click();}
    private void capture(){
        MainActivity main=(MainActivity)i.startActivitySync(new Intent(i.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));i.runOnMainSync(main::requestCapture);
        long end=SystemClock.uptimeMillis()+15000;
        while(!AppState.capturing && SystemClock.uptimeMillis()<end){UiObject2 allow=device.findObject(By.pkg("com.android.systemui").res("android:id/button1"));if(allow==null)allow=device.findObject(By.pkg("com.android.systemui").text(java.util.regex.Pattern.compile("(?i)Start now|Start recording|Start sharing|Start|Share|지금 시작|녹화 시작|공유 시작|시작")));if(allow!=null)allow.click();SystemClock.sleep(200);}
        until(()->AppState.capturing,5000,"capture consent");until(()->CaptureService.ready,20000,"Korean model ready");i.runOnMainSync(main::finish);
    }
    @Test public void threeRealCyclesWithChangingAnchors() throws Exception {
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        device.executeShellCommand("settings put secure enabled_accessibility_services app.get1won/app.get1won.AutomationService");device.executeShellCommand("settings put secure accessibility_enabled 1");device.executeShellCommand("pm grant app.get1won android.permission.POST_NOTIFICATIONS");until(()->AppState.accessibility!=null,15000,"accessibility");
        TestActivity activity=(TestActivity)i.startActivitySync(new Intent(i.getTargetContext(),TestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        capture();until(()->AppState.accessibility.sessionActive(),5000,"session ready");
        i.runOnMainSync(()->{AppState.stop();AppState.profile.repeats=3;AppState.profile.randomTest=false;AppState.profile.testDelay=1200;activity.smokeThree=true;activity.resetHome();});
        floatingStart();
        until(()->AppState.engine.completed==3 && AppState.engine.state==Engine.State.IDLE,180000,"three complete cycles");
        assertArrayEquals(diagnostic(),new long[]{3,3,3,3},AppState.engine.actions);
        i.runOnMainSync(()->{assertArrayEquals(new int[]{3,0,0,0,0,0,0},activity.counters());assertEquals("Other one-won events/history rows",0,activity.historyClicks);});
        android.util.Log.i("ThreeCycle","PASS cycles=3 actions=[3,3,3,3] wrongOrder=0 beforeCompletion=0 otherEventClicks=0");
        i.runOnMainSync(()->{AppState.accessibility.closeSession();activity.finish();});
    }
}

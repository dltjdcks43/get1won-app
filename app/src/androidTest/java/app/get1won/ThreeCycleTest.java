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

/** Exactly three real UI cycles against a separately installed application. No simulated clicks. */
@RunWith(AndroidJUnit4.class)
public class ThreeCycleTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private UiDevice device;
    private interface Check { boolean ok(); }
    private void until(Check check,long timeout,String message) {
        long end=SystemClock.uptimeMillis()+timeout;
        while(SystemClock.uptimeMillis()<end) { if(check.ok())return;SystemClock.sleep(200); }
        fail(message+"\n"+AppState.advanced());
    }
    @Test public void threeExternalApplicationCycles() throws Exception {
        Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        device=UiDevice.getInstance(instrumentation);device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        String previous=device.executeShellCommand("settings get secure enabled_accessibility_services").trim();
        String added=previous.isEmpty() || previous.equals("null")?"app.get1won/app.get1won.AutomationService":previous+":app.get1won/app.get1won.AutomationService";
        try {
            device.executeShellCommand("settings put secure enabled_accessibility_services "+added);
            device.executeShellCommand("settings put secure accessibility_enabled 1");
            device.executeShellCommand("pm grant app.get1won android.permission.POST_NOTIFICATIONS");
            until(()->AppState.accessibility!=null,15000,"Accessibility connection");
            MainActivity main=(MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.runOnMainSync(main::requestCapture);
            long end=SystemClock.uptimeMillis()+15000;
            while(!AppState.capturing && SystemClock.uptimeMillis()<end) {
                UiObject2 allow=device.findObject(By.pkg("com.android.systemui").res("android:id/button1"));
                if(allow!=null)allow.click();SystemClock.sleep(200);
            }
            until(()->AppState.capturing && CaptureService.ready,30000,"Local Korean OCR ready");
            instrumentation.runOnMainSync(()->AppState.profile.repeats=3);
            device.executeShellCommand("am force-stop app.get1won.fixture");
            device.executeShellCommand("am start -n app.get1won.fixture/.FixtureActivity");
            UiObject2 start=device.wait(Until.findObject(By.pkg("app.get1won").text("시작")),5000);assertNotNull(start);start.click();
            until(()->AppState.engine.completed==3 && AppState.engine.state==Engine.State.IDLE,150000,"Three consecutive cycles");
            assertArrayEquals(new long[]{3,3,3,3},AppState.engine.actions);
            String audit=device.executeShellCommand("run-as app.get1won.fixture cat files/audit.txt").trim();
            assertEquals("cycles=3 ad=3 points=3 rewardBack=3 historyBack=3 earlyBack=0 wrongClick=0",audit);
            android.util.Log.i("V2ThreeCycle",audit);
        } finally {
            instrumentation.runOnMainSync(()->{if(AppState.accessibility!=null)AppState.accessibility.closeSession();});
            if(previous.equals("null"))device.executeShellCommand("settings delete secure enabled_accessibility_services");
            else device.executeShellCommand("settings put secure enabled_accessibility_services "+previous);
        }
    }
}

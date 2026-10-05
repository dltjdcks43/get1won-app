package app.get1won;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Runs only on the disposable CI emulator. Real gestures, real projection, real BACK. */
@RunWith(AndroidJUnit4.class)
public class DeviceFlowTest {
    private final Instrumentation i=InstrumentationRegistry.getInstrumentation();
    private final UiDevice device=initializeDevice();
    private UiDevice initializeDevice(){Configurator.getInstance().setUiAutomationFlags(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);return UiDevice.getInstance(i);}
    private interface Check { boolean ok(); }
    private void until(Check check,long ms,String why){long end=SystemClock.uptimeMillis()+ms;while(SystemClock.uptimeMillis()<end){if(check.ok())return;SystemClock.sleep(100);}fail(why+"\n"+AppState.status()+"\n"+AppState.logs());}
    @Test public void fiftyRealCyclesAndNeverLeaveWaiting() throws Exception {
        device.executeShellCommand("settings put secure enabled_accessibility_services app.get1won/app.get1won.AutomationService");
        device.executeShellCommand("settings put secure accessibility_enabled 1");
        device.executeShellCommand("pm grant app.get1won android.permission.POST_NOTIFICATIONS");
        until(()->AppState.accessibility!=null,15000,"접근성 시작 실패");
        i.startActivitySync(new Intent(i.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        UiObject2 share=device.wait(Until.findObject(By.text("화면 공유 시작")),10000);assertNotNull(share);share.click();
        // Android 14/15/16 system consent labels vary; only affirmative capture buttons are matched.
        long end=SystemClock.uptimeMillis()+15000;
        while(!AppState.capturing && SystemClock.uptimeMillis()<end){
            UiObject2 allow=device.findObject(By.text(java.util.regex.Pattern.compile("Start now|Start recording|Start sharing|지금 시작|녹화 시작|공유 시작")));
            if(allow!=null)allow.click();SystemClock.sleep(200);
        }
        until(()->AppState.capturing,5000,"화면 공유 동의 실패");
        TestActivity activity=(TestActivity)i.startActivitySync(new Intent(i.getTargetContext(),TestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        i.waitForIdleSync();i.runOnMainSync(activity::calibrateForDeviceTest);
        until(activity::calibrationDone,15000,"테스트 이미지 등록 실패");
        i.runOnMainSync(()->{AppState.profile.repeats=50;AppState.profile.randomTest=true;activity.beginRun();});
        long deadline=SystemClock.uptimeMillis()+900000;
        while(SystemClock.uptimeMillis()<deadline && AppState.engine.active()){
            SystemClock.sleep(200);
        }
        assertEquals(AppState.logs(),50,AppState.engine.completed);
        assertEquals(Engine.State.IDLE,AppState.engine.state);
        final int[][] counters={null};i.runOnMainSync(()->counters[0]=activity.counters());
        assertArrayEquals(new int[]{50,0,0,0,0,0,0},counters[0]);
        long backs=AppState.engine.actions[1];
        i.runOnMainSync(()->{AppState.profile.repeats=1;AppState.profile.randomTest=false;AppState.profile.testDelay=0;activity.beginRun();});
        until(()->AppState.engine.state==Engine.State.PAUSED,35000,"영구 대기에서 시간 초과 일시정지 실패");
        assertEquals(backs,AppState.engine.actions[1]);
        i.runOnMainSync(AppState::stop);long[] counts=AppState.engine.actions.clone();SystemClock.sleep(1000);assertArrayEquals(counts,AppState.engine.actions);i.runOnMainSync(activity::finish);
    }
}

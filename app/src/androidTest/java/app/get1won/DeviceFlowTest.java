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
public class DeviceFlowTest {
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
    @Test public void hundredRealSemanticCyclesAndSafety() throws Exception {
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        device.executeShellCommand("settings put secure enabled_accessibility_services app.get1won/app.get1won.AutomationService");device.executeShellCommand("settings put secure accessibility_enabled 1");device.executeShellCommand("pm grant app.get1won android.permission.POST_NOTIFICATIONS");until(()->AppState.accessibility!=null,15000,"accessibility");
        i.runOnMainSync(()->{AppState.stop();i.getTargetContext().stopService(new Intent(i.getTargetContext(),CaptureService.class));AppState.profile.repeats=100;AppState.profile.randomTest=true;AppState.profile.panelSize=2;});
        TestActivity activity=(TestActivity)i.startActivitySync(new Intent(i.getTargetContext(),TestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));i.waitForIdleSync();
        // Run the complete OCR path first so OCR regressions fail before the long endurance run.
        capture();i.runOnMainSync(()->{AppState.profile.repeats=1;activity.hideAllText=true;activity.hideCompletionAccessibility=true;activity.resetHome();});floatingStart();
        until(()->AppState.engine.completed==1 && AppState.engine.state==Engine.State.IDLE,120000,"on-device Korean OCR full cycle");
        i.runOnMainSync(()->{assertArrayEquals(new int[]{1,0,0,0,0,0,0},activity.counters());assertArrayEquals("Every OCR phase must actually use OCR evidence",new long[]{1,1,1,1},AppState.accessibility.ocrActions);assertEquals(0,activity.historyClicks);AppState.stop();i.getTargetContext().stopService(new Intent(i.getTargetContext(),CaptureService.class));activity.hideAllText=false;activity.hideCompletionAccessibility=false;activity.resetHome();AppState.profile.repeats=100;});
        until(()->!AppState.capturing,5000,"capture disabled for accessibility-only endurance");
        long[] baseline=AppState.engine.actions.clone();long started=SystemClock.uptimeMillis();floatingStart();
        until(()->AppState.engine.actions[0]>baseline[0],4500,"START must select foreground without 5s delay or registration");assertEquals("app.get1won",AppState.engine.targetPackage);assertTrue(SystemClock.uptimeMillis()-started<5000);assertFalse(AppState.capturing);
        until(()->!AppState.engine.active(),1_300_000,"100 cycles finished");assertEquals(diagnostic(),100,AppState.engine.completed);assertEquals(Engine.State.IDLE,AppState.engine.state);
        long[] delta=AppState.engine.actions.clone();for(int n=0;n<4;n++)delta[n]-=baseline[n];assertArrayEquals(new long[]{100,100,100,100},delta);
        i.runOnMainSync(()->{assertArrayEquals(new int[]{101,0,0,0,0,0,0},activity.counters());assertEquals(0,activity.historyClicks);});
        i.runOnMainSync(()->{activity.resetHome();AppState.profile.randomTest=false;AppState.profile.testDelay=0;});
        long backs=AppState.engine.actions[1];floatingStart();until(()->AppState.engine.state==Engine.State.PAUSED,35000,"never-complete timeout pauses");assertEquals(backs,AppState.engine.actions[1]);
        i.runOnMainSync(()->{AppState.stop();activity.resetHome();AppState.profile.testDelay=8000;});floatingStart();until(()->AppState.engine.state==Engine.State.WAIT_REWARD_COMPLETE,5000,"waiting before active stop");
        i.runOnMainSync(AppState::stop);long[] activeStop=AppState.engine.actions.clone();SystemClock.sleep(8500);assertArrayEquals(activeStop,AppState.engine.actions);i.runOnMainSync(()->assertEquals(0,activity.audit.lateActions));
        i.runOnMainSync(()->{AppState.stop();activity.resetHome();AppState.profile.testDelay=8000;});floatingStart();until(()->AppState.engine.state==Engine.State.WAIT_REWARD_COMPLETE,5000,"waiting before external app");
        long[] before=AppState.engine.actions.clone();i.getTargetContext().startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));until(()->AppState.engine.state==Engine.State.PAUSED,3000,"foreign app pauses");SystemClock.sleep(1200);assertArrayEquals(before,AppState.engine.actions);
        i.runOnMainSync(AppState::stop);long[] stopped=AppState.engine.actions.clone();SystemClock.sleep(8500);assertArrayEquals(stopped,AppState.engine.actions);
        device.pressBack();i.runOnMainSync(activity::finish);
    }
}

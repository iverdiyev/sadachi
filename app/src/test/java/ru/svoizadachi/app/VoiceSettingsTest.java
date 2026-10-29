package ru.svoizadachi.app;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=OfflineTestApplication.class)
public class VoiceSettingsTest {
    @Test public void manualStopIsDefaultAndPreferenceSurvivesNewContextAccess(){
        Application app=RuntimeEnvironment.getApplication();assertEquals(0,VoiceSettings.silenceMillis(app));
        VoiceSettings.setSilenceMillis(app,2500);assertEquals(2500,VoiceSettings.silenceMillis(app));
        VoiceSettings.setSilenceMillis(app,4000);assertEquals(4000,VoiceSettings.silenceMillis(app));
        VoiceSettings.setSilenceMillis(app,0);assertEquals(0,VoiceSettings.silenceMillis(app));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsUnsupportedPause(){
        VoiceSettings.setSilenceMillis(RuntimeEnvironment.getApplication(),100);
    }
}

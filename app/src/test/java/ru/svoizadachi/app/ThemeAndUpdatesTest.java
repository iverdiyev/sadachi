package ru.svoizadachi.app;

import android.app.Application;
import android.view.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=OfflineTestApplication.class)
public class ThemeAndUpdatesTest {
    @Test public void themePersistsLocallyAndCanBeChanged(){
        Application context=RuntimeEnvironment.getApplication();ThemeSettings.setMode(context,2);
        assertEquals(2,ThemeSettings.mode(context));assertTrue(ThemeSettings.dark(context));
        ThemeSettings.setMode(context,1);assertFalse(ThemeSettings.dark(context));
    }
    @Test public void accentChoicePersistsWithoutOverridingIosPalette(){
        Application context=RuntimeEnvironment.getApplication();ThemeSettings.setMode(context,1);
        ThemeSettings.setAccentChoice(context,2);
        assertEquals(2,ThemeSettings.accentChoice(context));assertEquals(0xFF258577,ThemeSettings.accent(context));
        ThemeSettings.setIos(context,true);assertEquals(0xFF007AFF,ThemeSettings.accent(context));
        ThemeSettings.setIos(context,false);assertEquals(0xFF258577,ThemeSettings.accent(context));
    }
    @Test public void darkActivityStartsWithReadablePalette(){
        ThemeSettings.setMode(RuntimeEnvironment.getApplication(),2);
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            assertEquals(0xFFE8ECF6,ThemeSettings.text(controller.get()));
            assertEquals(0,controller.get().getWindow().getDecorView().getSystemUiVisibility()&View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }
    @Test public void manifestOnlyAcceptsThisRepositoriesHttpsReleases() throws Exception {
        JSONObject metadata=new JSONObject().put("versionCode",9).put("versionName","0.5.0")
                .put("apkUrl","https://github.com/iverdiyev/sadachi/releases/download/v0.5.0/Sadachi-9.apk")
                .put("sha256",new String(new char[64]).replace('\0','a'));
        assertTrue(AppUpdates.validManifest(metadata));
        metadata.put("apkUrl","https://evil.example/Sadachi.apk");assertFalse(AppUpdates.validManifest(metadata));
        metadata.put("apkUrl","https://github.com/iverdiyev/sadachi/releases/download/../other.apk");assertFalse(AppUpdates.validManifest(metadata));
        metadata.put("apkUrl","http://github.com/iverdiyev/sadachi/releases/download/v1/app.apk");assertFalse(AppUpdates.validManifest(metadata));
    }
    @Test public void invalidHashAndVersionAreRejected() throws Exception {
        JSONObject metadata=new JSONObject().put("versionCode",0).put("apkUrl","https://github.com/iverdiyev/sadachi/releases/download/v1/app.apk").put("sha256","broken");
        assertFalse(AppUpdates.validManifest(metadata));
    }
    @Test public void downloadedFileHashIsVerified() throws Exception {
        File file=new File(RuntimeEnvironment.getApplication().getCacheDir(),"hash-test");
        try(FileOutputStream output=new FileOutputStream(file)){output.write("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",AppUpdates.sha256(file));
    }
}

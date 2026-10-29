package ru.svoizadachi.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceSilenceDetectorTest {
    @Test public void shortPauseNeverCutsOffSpeech(){
        VoiceSilenceDetector detector=new VoiceSilenceDetector(2500);
        assertFalse(detector.shouldStop(0,1000));assertFalse(detector.shouldStop(.1f,2000));
        assertFalse(detector.shouldStop(0,2200));assertFalse(detector.shouldStop(.04f,4000));
        assertFalse(detector.shouldStop(0,4200));assertFalse(detector.shouldStop(0,6699));
        assertTrue(detector.shouldStop(0,6700));
    }
    @Test public void manualModeIgnoresSilence(){
        VoiceSilenceDetector detector=new VoiceSilenceDetector(0);
        assertFalse(detector.shouldStop(.2f,100));assertFalse(detector.shouldStop(0,100000));
    }
}

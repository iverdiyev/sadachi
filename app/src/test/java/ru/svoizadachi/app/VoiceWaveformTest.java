package ru.svoizadachi.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceWaveformTest {
    @Test public void amplitudeReflectsRealPcmSamplesAndSilence(){
        assertEquals(0f,VoiceWaveform.pcmRms(new byte[128],128),0f);
        byte[] loud=new byte[128];for(int i=0;i<loud.length;i+=2){loud[i]=(byte)0xff;loud[i+1]=0x3f;}
        assertTrue(VoiceWaveform.pcmRms(loud,loud.length)>.45f);
        assertEquals(0f,VoiceWaveform.pcmRms(loud,0),0f);
    }
}

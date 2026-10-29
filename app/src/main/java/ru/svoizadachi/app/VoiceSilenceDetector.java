package ru.svoizadachi.app;

/** Optional end-of-utterance detector. Manual mode never terminates from silence. */
final class VoiceSilenceDetector {
    private final int pauseMillis;
    private boolean heardSpeech;
    private long quietSince;

    VoiceSilenceDetector(int pauseMillis){this.pauseMillis=pauseMillis;}
    boolean shouldStop(float rms,long elapsedMillis){
        if(pauseMillis==0)return false;
        if(rms>.018f){heardSpeech=true;quietSince=0;return false;}
        if(!heardSpeech)return false;
        if(quietSince==0){quietSince=elapsedMillis;return false;}
        return elapsedMillis-quietSince>=pauseMillis;
    }
}

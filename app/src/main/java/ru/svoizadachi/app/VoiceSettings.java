package ru.svoizadachi.app;

import android.content.Context;

/** Recording options supported by the current offline recognizer. */
public final class VoiceSettings {
    private VoiceSettings(){}
    public static int silenceMillis(Context context){return context.getSharedPreferences("voice_preferences",Context.MODE_PRIVATE).getInt("silence_ms",0);}
    public static void setSilenceMillis(Context context,int value){
        if(value!=0&&value!=2500&&value!=4000)throw new IllegalArgumentException("Unsupported silence interval");
        context.getSharedPreferences("voice_preferences",Context.MODE_PRIVATE).edit().putInt("silence_ms",value).apply();
    }
}

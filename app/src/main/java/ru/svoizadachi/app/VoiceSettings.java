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
    /** Foreground recording only; the limit prevents an unattended microphone session. */
    public static int maxDurationMillis(Context context){
        int minutes=context.getSharedPreferences("voice_preferences",Context.MODE_PRIVATE).getInt("max_minutes",10);
        return (minutes==2||minutes==5||minutes==10?minutes:10)*60_000;
    }
    public static void setMaxMinutes(Context context,int minutes){
        if(minutes!=2&&minutes!=5&&minutes!=10)throw new IllegalArgumentException("Unsupported recording duration");
        context.getSharedPreferences("voice_preferences",Context.MODE_PRIVATE).edit().putInt("max_minutes",minutes).apply();
    }
}

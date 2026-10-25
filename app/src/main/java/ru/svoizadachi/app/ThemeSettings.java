package ru.svoizadachi.app;

import android.content.Context;
import android.content.res.Configuration;

public final class ThemeSettings {
    private ThemeSettings(){}
    public static int mode(Context c){return c.getSharedPreferences("appearance",Context.MODE_PRIVATE).getInt("theme",0);}
    public static void setMode(Context c,int mode){c.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putInt("theme",Math.max(0,Math.min(2,mode))).commit();}
    public static boolean dark(Context c){int mode=mode(c);return mode==2||mode==0&&(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
    public static int text(Context c){return dark(c)?0xFFF0F2FA:0xFF212339;}
    public static int subtle(Context c){return dark(c)?0xFFB2BACF:0xFF696E85;}
}

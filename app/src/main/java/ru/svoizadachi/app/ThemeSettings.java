package ru.svoizadachi.app;

import android.content.Context;
import android.content.res.Configuration;

public final class ThemeSettings {
    private ThemeSettings(){}
    public static int mode(Context c){return c.getSharedPreferences("appearance",Context.MODE_PRIVATE).getInt("theme",0);}
    public static void setMode(Context c,int mode){c.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putInt("theme",Math.max(0,Math.min(2,mode))).commit();}
    public static boolean ios(Context c){return c.getSharedPreferences("appearance",Context.MODE_PRIVATE).getBoolean("ios_style",false);}
    public static void setIos(Context c,boolean value){c.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putBoolean("ios_style",value).commit();}
    public static boolean dark(Context c){int mode=mode(c);return mode==2||mode==0&&(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
    public static int text(Context c){return ios(c)?(dark(c)?0xFFF5F5F7:0xFF1C1C1E):(dark(c)?0xFFF0F2FA:0xFF212339);}
    public static int subtle(Context c){return ios(c)?(dark(c)?0xFFAEAEB2:0xFF6E6E73):(dark(c)?0xFFB2BACF:0xFF696E85);}
    public static int accent(Context c){return ios(c)?(dark(c)?0xFF0A84FF:0xFF007AFF):(dark(c)?0xFF91ACFF:0xFF4970DF);}
    public static int background(Context c){return ios(c)?(dark(c)?0xFF000000:0xFFF2F2F7):(dark(c)?0xFF121722:0xFFF6F7FC);}
    public static int card(Context c){return ios(c)?(dark(c)?0xFF1C1C1E:0xFFFFFFFF):(dark(c)?0xFF1E2635:0xFFFFFFFF);}
    public static int field(Context c){return ios(c)?(dark(c)?0xFF2C2C2E:0xFFE9E9EF):(dark(c)?0xFF2A354B:0xFFF0F3FC);}
    public static int border(Context c){return ios(c)?(dark(c)?0xFF38383A:0xFFE5E5EA):(dark(c)?0xFF344055:0xFFE8E8F3);}
}

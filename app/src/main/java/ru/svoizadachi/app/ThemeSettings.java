package ru.svoizadachi.app;

import android.content.Context;
import android.content.res.Configuration;

public final class ThemeSettings {
    private ThemeSettings(){}
    public static int mode(Context c){return c.getSharedPreferences("appearance",Context.MODE_PRIVATE).getInt("theme",0);}
    public static void setMode(Context c,int mode){c.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putInt("theme",Math.max(0,Math.min(2,mode))).commit();}
    public static boolean ios(Context c){return c.getSharedPreferences("appearance",Context.MODE_PRIVATE).getBoolean("ios_style",false);}
    public static void setIos(Context c,boolean value){c.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putBoolean("ios_style",value).commit();}
    public static int accentChoice(Context c){return c.getSharedPreferences("appearance",Context.MODE_PRIVATE).getInt("accent",0);}
    public static void setAccentChoice(Context c,int choice){c.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putInt("accent",Math.max(0,Math.min(2,choice))).commit();}
    public static boolean dark(Context c){int mode=mode(c);return mode==2||mode==0&&(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
    public static int text(Context c){return ios(c)?(dark(c)?0xFFF5F5F7:0xFF1C1C1E):(dark(c)?0xFFE8ECF6:0xFF202435);}
    public static int subtle(Context c){return ios(c)?(dark(c)?0xFFAEAEB2:0xFF6E6E73):(dark(c)?0xFFA4AEC2:0xFF818695);}
    public static int accent(Context c){if(ios(c))return dark(c)?0xFF0A84FF:0xFF007AFF;
        int choice=accentChoice(c);return dark(c)?choice==1?0xFFC0A7F9:choice==2?0xFF84D8CA:0xFFA9B8FF:
                choice==1?0xFF7956CB:choice==2?0xFF258577:0xFF566FE8;}
    public static int background(Context c){return ios(c)?(dark(c)?0xFF000000:0xFFF2F2F7):(dark(c)?0xFF111622:0xFFF7F8FC);}
    public static int card(Context c){return ios(c)?(dark(c)?0xFF1C1C1E:0xFFFFFFFF):(dark(c)?0xFF1D2535:0xFFFFFFFF);}
    public static int field(Context c){return ios(c)?(dark(c)?0xFF2C2C2E:0xFFE9E9EF):(dark(c)?0xFF293449:0xFFEEF1FF);}
    public static int border(Context c){return ios(c)?(dark(c)?0xFF38383A:0xFFE5E5EA):(dark(c)?0xFF344054:0xFFE8EAF1);}
}

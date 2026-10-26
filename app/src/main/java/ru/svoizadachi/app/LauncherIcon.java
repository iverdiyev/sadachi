package ru.svoizadachi.app;

import android.content.*;
import android.content.pm.PackageManager;

public final class LauncherIcon {
    private LauncherIcon(){}
    public static boolean dark(Context c){return c.getSharedPreferences("appearance",Context.MODE_PRIVATE).getBoolean("dark_icon",false);}
    public static void setDark(Context c,boolean dark){
        switchTo(c,dark);
        c.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putBoolean("dark_icon",dark).commit();
    }
    /** Updates may reset alias state, so restore the chosen icon without touching preferences. */
    public static void apply(Context c){switchTo(c,dark(c));}
    private static void switchTo(Context c,boolean dark){
        PackageManager pm=c.getPackageManager();
        ComponentName enable=new ComponentName(c.getPackageName(),"ru.svoizadachi.app.Launcher"+(dark?"Dark":"Light"));
        ComponentName disable=new ComponentName(c.getPackageName(),"ru.svoizadachi.app.Launcher"+(dark?"Light":"Dark"));
        // Enable the replacement first; never leave the launcher without an entry.
        setState(pm,enable,true);setState(pm,disable,false);
    }
    private static void setState(PackageManager pm,ComponentName component,boolean enabled){
        int state=enabled?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        try{if(pm.getComponentEnabledSetting(component)!=state)pm.setComponentEnabledSetting(component,state,PackageManager.DONT_KILL_APP);}
        catch(RuntimeException ignored){/* The alias is unavailable on this build; keep the current icon. */}
    }
}

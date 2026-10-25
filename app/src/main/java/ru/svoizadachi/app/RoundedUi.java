package ru.svoizadachi.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.*;

public final class RoundedUi {
    private RoundedUi(){}
    public static GradientDrawable background(Context c,int radius){
        GradientDrawable shape=new GradientDrawable();shape.setColor(ThemeSettings.dark(c)?0xFF1E2635:0xFFFFFFFF);
        shape.setCornerRadius(radius*c.getResources().getDisplayMetrics().density);return shape;
    }
    public static void dialog(AlertDialog dialog){
        Window window=dialog.getWindow();if(window==null)return;Context context=dialog.getContext();
        window.setBackgroundDrawable(background(context,28));
        window.getDecorView().setClipToOutline(true);window.getDecorView().setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        int width=context.getResources().getDisplayMetrics().widthPixels-Math.round(32*context.getResources().getDisplayMetrics().density);
        window.setLayout(width,WindowManager.LayoutParams.WRAP_CONTENT);
    }
}

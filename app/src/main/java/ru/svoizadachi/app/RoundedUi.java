package ru.svoizadachi.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.Button;

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
    public static void actions(AlertDialog dialog){
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE}){
            Button button=dialog.getButton(which);if(button==null)continue;
            int color=which==AlertDialog.BUTTON_POSITIVE?(ThemeSettings.dark(dialog.getContext())?0xFF91ACFF:0xFF4970DF):
                    (ThemeSettings.dark(dialog.getContext())?0xFF2A354B:0xFFF0F3FC);
            GradientDrawable background=new GradientDrawable();background.setColor(color);
            background.setCornerRadius(16*dialog.getContext().getResources().getDisplayMetrics().density);
            button.setBackgroundTintList(null);button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x224970DF),background,null));
            button.setTextColor(which==AlertDialog.BUTTON_POSITIVE?(ThemeSettings.dark(dialog.getContext())?Color.BLACK:Color.WHITE):
                    ThemeSettings.text(dialog.getContext()));
            button.setAllCaps(false);button.setMinHeight(Math.round(48*dialog.getContext().getResources().getDisplayMetrics().density));
            int horizontal=Math.round(12*dialog.getContext().getResources().getDisplayMetrics().density);
            button.setPadding(horizontal,0,horizontal,0);
        }
    }
}

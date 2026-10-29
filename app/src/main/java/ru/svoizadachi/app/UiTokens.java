package ru.svoizadachi.app;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

/** Logical dp/sp sizes shared by the compact native phone surfaces. */
final class UiTokens {
    static final int PAGE=20, PAGE_NARROW=16, SECTION=24, GAP=8;
    static final int HEADING=28, SECTION_TEXT=17, TASK=15, BODY=14, SETTING=16, FILTER=13, CAPTION=12;
    static final int ICON_TOUCH=44, FILTER_HEIGHT=36, TASK_MIN=64, SETTING_HEIGHT=54, PRIMARY_HEIGHT=48;
    private UiTokens(){}
    static int dp(Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    static int page(Context context){return dp(context,context.getResources().getConfiguration().screenWidthDp<360?PAGE_NARROW:PAGE);}
    static void text(TextView view,int size,boolean medium){
        view.setTextSize(size);
        view.setTypeface(Typeface.create(medium?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        view.setIncludeFontPadding(false);
    }
}

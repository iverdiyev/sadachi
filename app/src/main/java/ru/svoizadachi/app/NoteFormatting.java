package ru.svoizadachi.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.text.style.*;
import android.view.Gravity;
import android.widget.*;

/** Native spans, not a WebView. Only text formatting is persisted as HTML. */
public final class NoteFormatting {
    private NoteFormatting(){}
    private static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    public static SpannableStringBuilder clean(CharSequence value){
        SpannableStringBuilder text=new SpannableStringBuilder(value);
        for(Object span:text.getSpans(0,text.length(),Object.class)){
            if(!(span instanceof StyleSpan||span instanceof UnderlineSpan||span instanceof StrikethroughSpan||span instanceof TypefaceSpan))text.removeSpan(span);
        }
        while(text.length()>0&&Character.isWhitespace(text.charAt(text.length()-1)))text.delete(text.length()-1,text.length());
        while(text.length()>0&&Character.isWhitespace(text.charAt(0)))text.delete(0,1);
        return text;
    }
    public static CharSequence load(Task task){
        if(!task.descriptionHtml.isEmpty()){
            SpannableStringBuilder styled=clean(Html.fromHtml(task.descriptionHtml,Html.FROM_HTML_MODE_COMPACT));
            if(styled.toString().equals(task.description))return styled;
        }
        return task.description;
    }
    public static void save(Task task,CharSequence value){
        SpannableStringBuilder clean=clean(value);task.description=clean.toString();
        task.descriptionHtml=clean.length()==0?"":Html.toHtml(clean,Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL);
    }
    public static HorizontalScrollView toolbar(Context c,EditText editor){
        return toolbar(c,editor,()->{});
    }
    public static HorizontalScrollView toolbar(Context c,EditText editor,Runnable changed){
        HorizontalScrollView scroll=new HorizontalScrollView(c);scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(c);scroll.addView(row);
        String[] names={"Ж","К","Ч","З","Код","•"};
        String[] descriptions={"Жирный текст","Курсив","Подчёркнутый текст","Зачёркнутый текст","Моноширинный текст","Маркированный список"};
        for(int i=0;i<names.length;i++){
            final int index=i;TextView button=new TextView(c);button.setText(names[i]);button.setTextSize(15);
            button.setTextColor(ThemeSettings.ios(c)?ThemeSettings.accent(c):(ThemeSettings.dark(c)?0xFFB6C8FF:0xFF375DBB));button.setGravity(Gravity.CENTER);
            button.setTypeface(Typeface.DEFAULT,i==0?Typeface.BOLD:i==1?Typeface.ITALIC:Typeface.NORMAL);
            button.setContentDescription(descriptions[i]);button.setFocusable(true);
            GradientDrawable background=new GradientDrawable();background.setColor(ThemeSettings.ios(c)?ThemeSettings.field(c):ThemeSettings.dark(c)?0xFF2A354B:0xFFEDF1FC);background.setCornerRadius(dp(c,ThemeSettings.ios(c)?18:14));
            button.setBackground(background);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(c,i==4?64:48),dp(c,48));p.setMargins(0,dp(c,4),dp(c,6),dp(c,4));row.addView(button,p);
            button.setOnClickListener(v->{if(index==5)bullets(editor);else toggle(editor,index);changed.run();});
        }
        return scroll;
    }
    public static void toggle(EditText editor,int kind){
        int start=Math.max(0,editor.getSelectionStart()),end=Math.max(0,editor.getSelectionEnd());
        if(start>end){int swap=start;start=end;end=swap;}
        if(start==end){Toast.makeText(editor.getContext(),"Выдели текст, затем выбери стиль",Toast.LENGTH_SHORT).show();return;}
        Editable text=editor.getText();Object selected=null;
        for(Object span:text.getSpans(start,end,Object.class)){
            boolean same=kind==0&&span instanceof StyleSpan&&((StyleSpan)span).getStyle()==Typeface.BOLD
                    ||kind==1&&span instanceof StyleSpan&&((StyleSpan)span).getStyle()==Typeface.ITALIC
                    ||kind==2&&span instanceof UnderlineSpan||kind==3&&span instanceof StrikethroughSpan
                    ||kind==4&&span instanceof TypefaceSpan;
            if(same&&text.getSpanStart(span)<=start&&text.getSpanEnd(span)>=end){selected=span;break;}
        }
        if(selected!=null){
            int left=text.getSpanStart(selected),right=text.getSpanEnd(selected);text.removeSpan(selected);
            if(left<start)text.setSpan(span(kind),left,start,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if(right>end)text.setSpan(span(kind),end,right,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }else text.setSpan(span(kind),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        editor.requestFocus();editor.setSelection(start,end);
    }
    private static Object span(int kind){
        return kind==0?new StyleSpan(Typeface.BOLD):kind==1?new StyleSpan(Typeface.ITALIC):kind==2?new UnderlineSpan():kind==3?new StrikethroughSpan():new TypefaceSpan("monospace");
    }
    public static void bullets(EditText editor){
        Editable text=editor.getText();int start=Math.max(0,editor.getSelectionStart()),end=Math.max(start,editor.getSelectionEnd());
        while(start>0&&text.charAt(start-1)!='\n')start--;
        int position=start;
        do{
            if(position+2<=text.length()&&text.subSequence(position,position+2).toString().equals("• ")){text.delete(position,position+2);end=Math.max(position,end-2);}
            else {text.insert(position,"• ");end+=2;}
            int next=TextUtils.indexOf(text,'\n',position);if(next<0||next>=end)break;position=next+1;
        }while(position<=text.length());
        editor.requestFocus();editor.setSelection(Math.min(end,text.length()));
    }
}

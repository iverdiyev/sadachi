package ru.svoizadachi.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int BACKGROUND=Color.rgb(246,247,251);
    private static final int PURPLE=Color.rgb(101,88,232);
    private static final int TEXT=Color.rgb(35,36,55);
    private static final int SUBTLE=Color.rgb(110,114,135);
    private static final int DIVIDER=Color.rgb(232,233,241);
    private static final int[] FILTERS={0,1,2,3};
    private static final String[] FILTER_NAMES={"Активные","Сегодня","Предстоящие","Готово"};
    private int filter=0;
    private LinearLayout cards,chipRow;
    private TextView preview,countLabel;
    private EditText entry;
    private TaskStore store;
    private boolean askedExact=false;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        store=new TaskStore(this);
        ReminderScheduler.createChannel(this);
        buildInterface();
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},17);
    }
    @Override protected void onResume(){super.onResume();ReminderScheduler.scheduleAll(this);if(cards!=null)refresh();}
    private int dp(float x){return (int)(getResources().getDisplayMetrics().density*x+.5f);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable stroke(int color,int radius,int border){GradientDrawable d=bg(color,radius);d.setStroke(dp(1),border);return d;}
    private TextView label(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private void buildInterface(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setFitsSystemWindows(true);root.setBackgroundColor(BACKGROUND);
        root.setPadding(dp(20),dp(20),dp(20),dp(8));setContentView(root);
        TextView header=label("Свои задачи  ✓",29,TEXT,true);
        root.addView(header,lp(-1,46));
        TextView subtitle=label("Меньше планов — больше сделано",13,SUBTLE,false);
        root.addView(subtitle,lp(-1,26));
        LinearLayout upper=new LinearLayout(this);upper.setOrientation(LinearLayout.VERTICAL);
        upper.setPadding(dp(14),dp(13),dp(14),dp(12));upper.setBackground(bg(Color.WHITE,20));
        LinearLayout.LayoutParams upperLp=lp(-1,-2);upperLp.topMargin=dp(18);root.addView(upper,upperLp);
        entry=new EditText(this);entry.setTextSize(16);entry.setSingleLine(false);
        entry.setMinLines(1);entry.setMaxLines(3);entry.setTextColor(TEXT);
        entry.setHintTextColor(SUBTLE);entry.setHint("Например: домашка завтра в 18:00");
        entry.setBackgroundColor(Color.TRANSPARENT);
        entry.setImeOptions(EditorInfo.IME_ACTION_DONE);
        upper.addView(entry,lp(-1,-2));
        preview=label("Напиши задачу — дату я пойму сам ✨",12,SUBTLE,false);
        LinearLayout.LayoutParams pp=lp(-1,-2);pp.topMargin=dp(5);upper.addView(preview,pp);
        TextView add=label("＋  Добавить задачу",15,Color.WHITE,true);
        add.setGravity(Gravity.CENTER);add.setBackground(bg(PURPLE,14));
        LinearLayout.LayoutParams addLp=lp(-1,48);addLp.topMargin=dp(12);upper.addView(add,addLp);
        add.setOnClickListener(v->createTask());
        entry.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){createTask();return true;}return false;});
        entry.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            @Override public void onTextChanged(CharSequence s,int st,int b,int c){updatePreview();}
            @Override public void afterTextChanged(Editable e){}
        });
        countLabel=label("",17,TEXT,true);
        LinearLayout.LayoutParams countLp=lp(-1,36);countLp.topMargin=dp(19);root.addView(countLabel,countLp);
        HorizontalScrollView scrollChips=new HorizontalScrollView(this);scrollChips.setHorizontalScrollBarEnabled(false);
        chipRow=new LinearLayout(this);chipRow.setOrientation(LinearLayout.HORIZONTAL);
        scrollChips.addView(chipRow);
        root.addView(scrollChips,lp(-1,48));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        cards=new LinearLayout(this);cards.setOrientation(LinearLayout.VERTICAL);cards.setPadding(0,dp(10),0,dp(32));
        scroll.addView(cards);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        refresh();
    }
    private void updatePreview(){
        TaskDateParser.Result r=TaskDateParser.parse(entry.getText().toString());
        if(entry.getText().toString().trim().isEmpty()){
            preview.setText("Напиши задачу — дату я пойму сам ✨");return;
        }
        if(r.dueAt==null){preview.setText("Задача без даты · можно добавить");return;}
        LocalDateTime date=LocalDateTime.ofInstant(Instant.ofEpochMilli(r.dueAt),ZoneId.systemDefault());
        String s="📅 "+date.format(DateTimeFormatter.ofPattern("d MMMM",new Locale("ru")));
        if(r.hasTime)s+="  ·  🕒 "+date.format(DateTimeFormatter.ofPattern("HH:mm"));
        if(r.repeat!=0)s+=r.repeat==1?"  ·  ежедневно":"  ·  каждую неделю";
        preview.setText(s);
    }
    private void createTask(){
        String raw=entry.getText().toString().trim();if(raw.isEmpty()){entry.setError("Напиши название");return;}
        TaskDateParser.Result r=TaskDateParser.parse(raw);
        Task t=new Task(store.allocateId(),r.title,r.dueAt,r.hasTime,r.repeat,false);
        store.upsert(t);ReminderScheduler.schedule(this,t);
        entry.setText("");filter=0;refresh();
        if(t.dueAt!=null && t.hasTime)maybeAskExact();
        Toast.makeText(this,"Задача сохранена",Toast.LENGTH_SHORT).show();
    }
    private void maybeAskExact(){
        if(Build.VERSION.SDK_INT>=31 && !ReminderScheduler.hasExactAccess(this) && !askedExact){
            askedExact=true;
            new AlertDialog.Builder(this)
                    .setTitle("Точные напоминания")
                    .setMessage("Для уведомлений точно в указанное время разреши приложению «Будильники и напоминания» в настройках Android. Без разрешения уведомления могут задерживаться.")
                    .setNegativeButton("Позже",(d,w)->{})
                    .setPositiveButton("Открыть настройки",(d,w)->{
                        try {startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                android.net.Uri.parse("package:"+getPackageName())));}
                        catch(Exception ignored){}
                    }).show();
        }
    }
    private void renderChips(){
        chipRow.removeAllViews();
        for(int i=0;i<FILTER_NAMES.length;i++){
            final int index=i;
            TextView chip=label(FILTER_NAMES[i],13,i==filter?Color.WHITE:SUBTLE,i==filter);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(16),0,dp(16),0);
            chip.setBackground(bg(i==filter?PURPLE:Color.WHITE,18));
            LinearLayout.LayoutParams p=lp(-2,36);p.rightMargin=dp(8);chipRow.addView(chip,p);
            chip.setOnClickListener(v->{filter=index;refresh();});
        }
    }
    private void refresh(){
        if(cards==null)return;
        List<Task> tasks=store.all();
        tasks.sort((a,b)->{
            if(a.done!=b.done)return a.done?1:-1;
            if(a.dueAt==null&&b.dueAt==null)return Long.compare(b.id,a.id);
            if(a.dueAt==null)return 1;
            if(b.dueAt==null)return -1;
            int cmp=Long.compare(a.dueAt,b.dueAt);
            return cmp!=0?cmp:Long.compare(a.id,b.id);
        });
        long active=tasks.stream().filter(t->!t.done).count();
        countLabel.setText("Задачи  ·  осталось "+active);
        renderChips();cards.removeAllViews();
        int displayed=0;
        LocalDate today=LocalDate.now();
        for(Task t:tasks){
            if(filter==3 && !t.done)continue;
            if(filter!=3 && t.done)continue;
            LocalDate day=t.dueAt==null?null:Instant.ofEpochMilli(t.dueAt).atZone(ZoneId.systemDefault()).toLocalDate();
            if(filter==1 && (day==null||day.isAfter(today)))continue;
            if(filter==2 && (day==null||!day.isAfter(today)))continue;
            createCard(t);displayed++;
        }
        if(displayed==0){
            TextView empty=label(filter==3?"Выполненных задач пока нет 🌱":"Здесь пока пусто. Добавь первую задачу ✨",14,SUBTLE,false);
            empty.setGravity(Gravity.CENTER);empty.setPadding(dp(18),dp(45),dp(18),dp(45));
            cards.addView(empty);
        }
    }
    private void createCard(Task task){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(dp(12),dp(11),dp(12),dp(11));
        row.setBackground(stroke(Color.WHITE,16,DIVIDER));
        LinearLayout.LayoutParams p=lp(-1,-2);p.bottomMargin=dp(9);cards.addView(row,p);
        CheckBox done=new CheckBox(this);done.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        done.setChecked(task.done);row.addView(done,lp(44,46));
        done.setOnCheckedChangeListener((v,checked)->{
            task.done=checked;store.upsert(task);
            if(checked)ReminderScheduler.cancel(this,task.id);
            else ReminderScheduler.schedule(this,task);
            refresh();
        });
        LinearLayout textCol=new LinearLayout(this);textCol.setOrientation(LinearLayout.VERTICAL);
        row.addView(textCol,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=label(task.title,16,task.done?SUBTLE:TEXT,true);
        if(task.done)title.setPaintFlags(title.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        textCol.addView(title,lp(-1,-2));
        String dateText=taskDate(task);
        if(!dateText.isEmpty()){
            TextView date=label(dateText,12,dateText.contains("Просрочено")?Color.rgb(212,74,74):SUBTLE,false);
            LinearLayout.LayoutParams x=lp(-1,-2);x.topMargin=dp(6);textCol.addView(date,x);
        }
        TextView dots=label("⋯",28,SUBTLE,true);dots.setGravity(Gravity.CENTER);
        row.addView(dots,lp(35,50));
        dots.setOnClickListener(v->taskOptions(task));
        textCol.setOnClickListener(v->taskOptions(task));
    }
    private String taskDate(Task task){
        if(task.dueAt==null)return "Без даты";
        LocalDateTime value=LocalDateTime.ofInstant(Instant.ofEpochMilli(task.dueAt),ZoneId.systemDefault());
        LocalDate day=value.toLocalDate();LocalDate today=LocalDate.now();
        String date=day.isEqual(today)?"Сегодня":day.isEqual(today.plusDays(1))?"Завтра":
                day.format(DateTimeFormatter.ofPattern("d MMM",new Locale("ru")));
        if(day.isBefore(today)&&!task.done)date="Просрочено · "+date;
        if(task.hasTime)date+=" · "+value.format(DateTimeFormatter.ofPattern("HH:mm"));
        if(task.repeat!=0)date+=task.repeat==1?" · каждый день":" · каждую неделю";
        return date;
    }
    private void taskOptions(Task task){
        String[] ops={"Изменить","Удалить"};
        new AlertDialog.Builder(this).setTitle(task.title).setItems(ops,(dialog,which)->{
            if(which==0)editTask(task);
            else new AlertDialog.Builder(this).setTitle("Удалить задачу?")
                .setMessage(task.title).setNegativeButton("Отмена",null)
                .setPositiveButton("Удалить",(d,w)->{
                    ReminderScheduler.cancel(this,task.id);store.remove(task.id);refresh();
                }).show();
        }).show();
    }
    private void editTask(Task task){
        EditText edit=new EditText(this);edit.setText(task.title);edit.setSingleLine(false);
        LinearLayout lay=new LinearLayout(this);lay.setPadding(dp(22),dp(8),dp(22),0);lay.addView(edit);
        new AlertDialog.Builder(this).setTitle("Изменить задачу")
            .setMessage("Можно указать новую дату словами: «завтра в 16:00». Если дату не указать, прежняя сохранится.")
            .setView(lay).setNegativeButton("Отмена",null)
            .setPositiveButton("Сохранить",(d,w)->{
                String newText=edit.getText().toString().trim();if(newText.isEmpty())return;
                TaskDateParser.Result parsed=TaskDateParser.parse(newText);
                task.title=parsed.title;
                if(parsed.dueAt!=null){task.dueAt=parsed.dueAt;task.hasTime=parsed.hasTime;task.repeat=parsed.repeat;}
                store.upsert(task);ReminderScheduler.schedule(this,task);refresh();
            }).show();
    }
}

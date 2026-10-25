package ru.svoizadachi.app;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Native, offline UI. No WebView, network or heavyweight animation framework. */
public final class MainActivity extends Activity {
    private static final int BACKGROUND=Color.rgb(246,247,252);
    private static final int PURPLE=Color.rgb(103,83,222);
    private static final int TEXT=Color.rgb(33,35,57);
    private static final int SUBTLE=Color.rgb(105,110,133);
    private static final int PALE=Color.rgb(237,233,255);
    private static final int LINE=Color.rgb(232,232,243);
    private static final int GREEN=Color.rgb(37,126,107);
    private static final Locale RU=new Locale("ru");
    private static final String[] FILTER_NAMES={"Все","Сегодня","Потом","Готово"};
    private int filter;
    private LinearLayout root,cards,chipRow,permissionCard;
    private ScrollView scroll;
    private TextView preview,countLabel,heroTitle,heroDetail,permissionText;
    private ProgressBar progress;
    private EditText entry;
    private TaskStore store;
    private boolean askedExact;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable previewUpdate=()->updatePreview();
    private final Runnable clockTick=new Runnable(){
        @Override public void run(){
            if(cards!=null){refresh(false);updatePreview();}
            handler.postDelayed(this,60000);
        }
    };

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(BACKGROUND);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                |View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility()
                |View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        store=new TaskStore(this);
        ReminderScheduler.createChannel(this);
        if(state!=null)filter=state.getInt("filter",0);
        buildInterface();
        if(state!=null){entry.setText(state.getString("draft",""));entry.setSelection(entry.length());}
    }
    @Override protected void onResume(){
        super.onResume();
        ReminderScheduler.scheduleAll(this);
        if(cards!=null)refresh(false);
        handler.removeCallbacks(clockTick);handler.postDelayed(clockTick,60000);
    }
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(clockTick);}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){
        state.putInt("filter",filter);state.putString("draft",entry.getText().toString());
        super.onSaveInstanceState(state);
    }
    private int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
    private LinearLayout.LayoutParams lp(int width,int height){
        return new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));
    }
    private GradientDrawable bg(int color,int radius){
        GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(radius));return drawable;
    }
    private GradientDrawable border(int color,int radius){
        GradientDrawable drawable=bg(color,radius);drawable.setStroke(dp(1),LINE);return drawable;
    }
    private void touchBackground(View view,int color,int radius){
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x226753DE),bg(color,radius),bg(Color.WHITE,radius)));
        view.setClickable(true);view.setFocusable(true);
    }
    private TextView label(String text,int size,int color,boolean bold){
        TextView view=new TextView(this);view.setText(text);view.setTextSize(size);view.setTextColor(color);
        view.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        view.setGravity(Gravity.CENTER_VERTICAL);view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(3),1);return view;
    }
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private void gap(LinearLayout parent,int height){parent.addView(new View(this),lp(1,height));}
    private boolean motion(){return ValueAnimator.areAnimatorsEnabled();}
    private void enter(View view,int delay){
        if(!motion())return;
        view.setAlpha(0);view.setTranslationY(dp(10));
        view.animate().alpha(1).translationY(0).setStartDelay(delay).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
    }
    private void buildInterface(){
        root=column();root.setBackgroundColor(BACKGROUND);root.setFocusableInTouchMode(true);
        setContentView(root);
        // Android 15+ enforces edge-to-edge. Apply real system/cutout/keyboard
        // insets rather than fitsSystemWindows or guessed status-bar heights.
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int left,right,top,bottom;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());
                left=safe.left;right=safe.right;top=safe.top;bottom=Math.max(safe.bottom,ime.bottom);
            }else{
                left=insets.getSystemWindowInsetLeft();right=insets.getSystemWindowInsetRight();
                top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();
                if(Build.VERSION.SDK_INT>=28 && insets.getDisplayCutout()!=null){
                    left=Math.max(left,insets.getDisplayCutout().getSafeInsetLeft());
                    right=Math.max(right,insets.getDisplayCutout().getSafeInsetRight());
                    top=Math.max(top,insets.getDisplayCutout().getSafeInsetTop());
                    bottom=Math.max(bottom,insets.getDisplayCutout().getSafeInsetBottom());
                }
            }
            view.setPadding(left,top,right,bottom);return insets;
        });
        root.requestApplyInsets();
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout page=column();page.setPadding(dp(24),dp(18),dp(24),dp(28));
        scroll.addView(page,new ScrollView.LayoutParams(-1,-2));

        TextView date=label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM",RU)),13,SUBTLE,false);
        page.addView(date,lp(-1,-2));gap(page,8);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("Свои задачи",30,TEXT,true);
        heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView settings=label("⋯",26,PURPLE,true);settings.setGravity(Gravity.CENTER);
        settings.setContentDescription("Настройки напоминаний");touchBackground(settings,Color.WHITE,18);
        heading.addView(settings,lp(48,48));settings.setOnClickListener(v->showReminderSettings());
        page.addView(heading,lp(-1,-2));gap(page,20);

        LinearLayout hero=column();hero.setPadding(dp(22),dp(22),dp(22),dp(22));
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(112,90,229),Color.rgb(81,73,179)});
        gradient.setCornerRadius(dp(28));hero.setBackground(gradient);
        hero.addView(label("В СВОЁМ РИТМЕ",11,0xFFE2DDFF,true),lp(-1,-2));gap(hero,12);
        heroTitle=label("Место для важного",23,Color.WHITE,true);hero.addView(heroTitle,lp(-1,-2));gap(hero,8);
        heroDetail=label("Один маленький шаг — уже прогресс",13,0xFFE7E2FF,false);
        hero.addView(heroDetail,lp(-1,-2));gap(hero,18);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);progress.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0x44FFFFFF));
        hero.addView(progress,lp(-1,5));page.addView(hero,lp(-1,-2));gap(page,18);

        LinearLayout composer=column();composer.setPadding(dp(18),dp(18),dp(18),dp(18));
        composer.setBackground(border(Color.WHITE,24));
        composer.addView(label("Что хочется сделать?",16,TEXT,true),lp(-1,-2));gap(composer,10);
        entry=new EditText(this);entry.setId(View.generateViewId());entry.setTextSize(16);entry.setTextColor(TEXT);
        entry.setHintTextColor(SUBTLE);entry.setHint("Например, чай через 2 минуты");
        entry.setContentDescription("Название задачи и время напоминания");
        entry.setBackgroundColor(Color.TRANSPARENT);entry.setPadding(0,dp(8),0,dp(8));
        entry.setMinLines(1);entry.setMaxLines(5);entry.setGravity(Gravity.TOP|Gravity.START);
        entry.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        entry.setImeOptions(EditorInfo.IME_ACTION_DONE);composer.addView(entry,lp(-1,-2));
        preview=label("Дату и время можно написать словами",12,SUBTLE,false);
        preview.setVisibility(View.GONE);
        preview.setPadding(dp(12),dp(10),dp(12),dp(10));preview.setBackground(bg(BACKGROUND,12));
        composer.addView(preview,lp(-1,-2));gap(composer,6);
        HorizontalScrollView suggestions=new HorizontalScrollView(this);suggestions.setHorizontalScrollBarEnabled(false);
        suggestions.setVisibility(View.GONE);
        entry.setOnFocusChangeListener((v,focused)->suggestions.setVisibility(focused?View.VISIBLE:View.GONE));
        LinearLayout shortcuts=new LinearLayout(this);
        String[] names={"Через 2 мин","Через час","Завтра"};
        String[] phrases={"через 2 минуты","через час","завтра в 9:00"};
        for(int i=0;i<names.length;i++){
            final String phrase=phrases[i];
            TextView shortcut=label(names[i],12,PURPLE,true);shortcut.setGravity(Gravity.CENTER);
            shortcut.setPadding(dp(12),0,dp(12),0);touchBackground(shortcut,Color.WHITE,12);
            LinearLayout.LayoutParams p=lp(-2,48);p.rightMargin=dp(4);shortcuts.addView(shortcut,p);
            shortcut.setOnClickListener(v->{
                String raw=entry.getText().toString().trim();
                TaskDateParser.Result parsed=TaskDateParser.parse(raw);
                String name=parsed.dueAt==null?raw:parsed.title;
                if(name.equals("Напоминание"))name="";
                entry.setText((name+" "+phrase).trim());entry.setSelection(entry.length());
                updatePreview();
            });
        }
        suggestions.addView(shortcuts);composer.addView(suggestions,lp(-1,-2));gap(composer,4);
        TextView add=label("＋  Добавить задачу",15,Color.WHITE,true);add.setGravity(Gravity.CENTER);
        add.setPadding(dp(14),dp(16),dp(14),dp(16));add.setMinHeight(dp(52));touchBackground(add,PURPLE,16);
        composer.addView(add,lp(-1,-2));add.setOnClickListener(v->createTask());
        entry.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_DONE){createTask();return true;}return false;
        });
        entry.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                handler.removeCallbacks(previewUpdate);handler.postDelayed(previewUpdate,100);
            }
            @Override public void afterTextChanged(Editable e){}
        });
        page.addView(composer,lp(-1,-2));gap(page,16);

        permissionCard=column();permissionCard.setPadding(dp(16),dp(14),dp(16),dp(14));
        touchBackground(permissionCard,Color.rgb(255,245,225),18);
        permissionCard.addView(label("Проверь напоминания",14,Color.rgb(123,85,25),true),lp(-1,-2));
        gap(permissionCard,6);permissionText=label("",12,Color.rgb(123,85,25),false);
        permissionCard.addView(permissionText,lp(-1,-2));permissionCard.setOnClickListener(v->showReminderSettings());
        page.addView(permissionCard,lp(-1,-2));

        countLabel=label("Мои задачи",19,TEXT,true);
        LinearLayout.LayoutParams countLp=lp(-1,-2);countLp.topMargin=dp(24);countLp.bottomMargin=dp(12);
        page.addView(countLabel,countLp);
        HorizontalScrollView filters=new HorizontalScrollView(this);filters.setHorizontalScrollBarEnabled(false);
        chipRow=new LinearLayout(this);filters.addView(chipRow);page.addView(filters,lp(-1,-2));
        cards=column();cards.setPadding(0,dp(14),0,dp(8));page.addView(cards,lp(-1,-2));
        root.requestFocus();refresh(false);enter(hero,0);enter(composer,60);
    }
    private void updatePreview(){
        String raw=entry.getText().toString();TaskDateParser.Result parsed=TaskDateParser.parse(raw);
        if(raw.trim().isEmpty()){
            preview.setVisibility(View.GONE);
            preview.setText("Дату и время можно написать словами");preview.setTextColor(SUBTLE);return;
        }
        preview.setVisibility(View.VISIBLE);
        if(parsed.dueAt==null){preview.setText("Без напоминания · просто сохранить задачу");preview.setTextColor(SUBTLE);return;}
        LocalDateTime date=LocalDateTime.ofInstant(Instant.ofEpochMilli(parsed.dueAt),ZoneId.systemDefault());
        String text=(parsed.hasTime?"Напомню: ":"Дата: ")+date.format(DateTimeFormatter.ofPattern("d MMMM",RU));
        if(parsed.hasTime)text+=" в "+date.format(DateTimeFormatter.ofPattern("HH:mm"));
        if(parsed.repeat!=0)text+=parsed.repeat==1?" · каждый день":" · каждую неделю";
        if(!parsed.hasTime)text+=" · без уведомления";
        preview.setText(text);preview.setTextColor(PURPLE);
    }
    private void createTask(){
        String raw=entry.getText().toString().trim();
        if(raw.isEmpty()){entry.setError("Напиши, что нужно сделать");entry.requestFocus();return;}
        TaskDateParser.Result parsed=TaskDateParser.parse(raw);
        Task task=new Task(store.allocateId(),parsed.title,parsed.dueAt,parsed.hasTime,parsed.repeat,false);
        store.upsert(task);ReminderScheduler.schedule(this,task);
        handler.removeCallbacks(previewUpdate);entry.setText("");updatePreview();filter=0;
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(entry.getWindowToken(),0);
        root.requestFocus();refresh(true);
        Toast.makeText(this,task.hasTime?"Задача и напоминание сохранены":"Задача сохранена",Toast.LENGTH_SHORT).show();
        if(task.dueAt!=null&&task.hasTime)ensureReminderAccess();
    }
    private boolean notificationsEnabled(){
        return ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()
                && ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                .getNotificationChannel(ReminderScheduler.CHANNEL).getImportance()!=NotificationManager.IMPORTANCE_NONE;
    }
    private void ensureReminderAccess(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},17);return;
        }
        maybeAskExact();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==17){refresh(false);if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)maybeAskExact();}
    }
    private void maybeAskExact(){
        if(Build.VERSION.SDK_INT>=31&&!ReminderScheduler.hasExactAccess(this)&&!askedExact){
            askedExact=true;
            new AlertDialog.Builder(this).setTitle("Напоминать вовремя")
                    .setMessage("Разреши «Будильники и напоминания», чтобы уведомление «через 2 минуты» пришло точно по времени. Без этого Android может его задержать.")
                    .setNegativeButton("Позже",null).setPositiveButton("Разрешить",(d,w)->openExactSettings()).show();
        }
    }
    private void openExactSettings(){
        if(Build.VERSION.SDK_INT>=31){
            try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:"+getPackageName())));}catch(Exception e){openAppSettings();}
        }
    }
    private void openAppSettings(){
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));
    }
    private void showReminderSettings(){
        String[] options={"Уведомления: "+(notificationsEnabled()?"включены":"выключены"),
                "Точное время: "+(ReminderScheduler.hasExactAccess(this)?"разрешено":"нужно разрешить"),
                "Фоновая работа на OnePlus"};
        new AlertDialog.Builder(this).setTitle("Настройки напоминаний").setItems(options,(d,which)->{
            if(which==0)startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,ReminderScheduler.CHANNEL));
            else if(which==1){if(Build.VERSION.SDK_INT>=31)openExactSettings();else Toast.makeText(this,"Точные напоминания доступны",Toast.LENGTH_SHORT).show();}
            else new AlertDialog.Builder(this).setTitle("Напоминания на OnePlus")
                    .setMessage("В настройках приложения открой «Использование батареи» и разреши фоновую работу, если уведомления задерживаются. Названия пунктов зависят от OxygenOS. Принудительная остановка приложения отключает напоминания до следующего открытия.")
                    .setNegativeButton("Понятно",null).setPositiveButton("Настройки приложения",(a,b)->openAppSettings()).show();
        }).setNegativeButton("Закрыть",null).show();
    }
    private void renderChips(){
        chipRow.removeAllViews();
        for(int i=0;i<FILTER_NAMES.length;i++){
            final int index=i;TextView chip=label(FILTER_NAMES[i],13,i==filter?Color.WHITE:SUBTLE,true);
            chip.setGravity(Gravity.CENTER);chip.setPadding(dp(16),dp(13),dp(16),dp(13));chip.setMinHeight(dp(48));
            touchBackground(chip,i==filter?PURPLE:Color.WHITE,16);
            chip.setSelected(i==filter);chip.setContentDescription(FILTER_NAMES[i]+(i==filter?", выбрано":""));
            LinearLayout.LayoutParams p=lp(-2,-2);p.rightMargin=dp(6);chipRow.addView(chip,p);
            chip.setOnClickListener(v->{if(filter!=index){filter=index;refresh(true);}});
        }
    }
    private void refresh(boolean animate){
        if(cards==null)return;
        List<Task> tasks=store.all();
        tasks.sort((a,b)->{
            if(a.done!=b.done)return a.done?1:-1;
            if(a.dueAt==null&&b.dueAt==null)return Long.compare(b.id,a.id);
            if(a.dueAt==null)return 1;if(b.dueAt==null)return -1;
            int comparison=Long.compare(a.dueAt,b.dueAt);return comparison!=0?comparison:Long.compare(a.id,b.id);
        });
        long active=tasks.stream().filter(t->!t.done).count();long done=tasks.size()-active;
        heroTitle.setText(active==0?(tasks.isEmpty()?"Место для важного":"Всё сделано. Выдохни."):"Всё получится");
        heroDetail.setText(tasks.isEmpty()?"Начни с одной небольшой задачи":active==0?"Можно отдохнуть или задумать что-то новое":
                "Выполнено "+done+" из "+tasks.size()+" · без спешки, по шагу");
        progress.setProgress(tasks.isEmpty()?0:(int)(100*done/tasks.size()),animate&&motion());
        countLabel.setText(filter==3?"Завершённые задачи":"Мои задачи · "+active);
        boolean notifications=notificationsEnabled(),exact=ReminderScheduler.hasExactAccess(this);
        boolean timedTasks=tasks.stream().anyMatch(t->!t.done&&t.hasTime&&t.dueAt!=null);
        permissionCard.setVisibility(!timedTasks||notifications&&exact?View.GONE:View.VISIBLE);
        permissionText.setText(!notifications?"Уведомления выключены. Нажми, чтобы разрешить напоминания.":
                "Для точного времени нужно разрешение Android. Нажми, чтобы настроить.");
        renderChips();cards.removeAllViews();int displayed=0;LocalDate today=LocalDate.now();
        for(Task task:tasks){
            if(filter==3&&!task.done||filter!=3&&task.done)continue;
            LocalDate day=task.dueAt==null?null:Instant.ofEpochMilli(task.dueAt).atZone(ZoneId.systemDefault()).toLocalDate();
            if(filter==1&&(day==null||day.isAfter(today)))continue;
            if(filter==2&&(day==null||!day.isAfter(today)))continue;
            View card=createCard(task);if(animate&&displayed<8)enter(card,displayed*25);displayed++;
        }
        if(displayed==0){
            LinearLayout empty=column();empty.setPadding(dp(20),dp(30),dp(20),dp(30));empty.setBackground(border(Color.WHITE,24));
            TextView icon=label(filter==3?"✓":"○",36,PURPLE,false);icon.setGravity(Gravity.CENTER);empty.addView(icon,lp(-1,50));gap(empty,12);
            TextView title=label(filter==3?"Пока без галочек":filter==1?"На сегодня всё спокойно":filter==2?"Будущее свободно":"Чистый лист",18,TEXT,true);
            title.setGravity(Gravity.CENTER);empty.addView(title,lp(-1,-2));gap(empty,8);
            TextView detail=label(filter==3?"Завершённые задачи появятся здесь":"Добавь задачу выше. Например:\n«Выпить воду через 2 минуты»",13,SUBTLE,false);
            detail.setGravity(Gravity.CENTER);empty.addView(detail,lp(-1,-2));cards.addView(empty,lp(-1,-2));
            if(animate)enter(empty,0);
        }
    }
    private View createCard(Task task){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8),dp(14),dp(8),dp(14));row.setBackground(border(Color.WHITE,22));
        LinearLayout.LayoutParams p=lp(-1,-2);p.bottomMargin=dp(10);cards.addView(row,p);
        CheckBox done=new CheckBox(this);done.setButtonTintList(ColorStateList.valueOf(task.done?GREEN:PURPLE));
        done.setContentDescription((task.done?"Вернуть задачу: ":"Завершить задачу: ")+task.title);
        done.setChecked(task.done);row.addView(done,lp(48,48));
        LinearLayout text=column();text.setPadding(dp(2),dp(2),dp(2),dp(2));
        row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=label(task.title,16,task.done?SUBTLE:TEXT,true);
        if(task.done)title.setPaintFlags(title.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        text.addView(title,lp(-1,-2));gap(text,7);
        boolean overdue=!task.done&&task.dueAt!=null&&task.hasTime&&task.dueAt<System.currentTimeMillis();
        TextView date=label(taskDate(task),12,overdue?Color.rgb(177,63,76):task.hasTime?PURPLE:SUBTLE,false);
        text.addView(date,lp(-1,-2));text.setOnClickListener(v->taskOptions(task));
        text.setFocusable(true);text.setContentDescription(task.title+". "+taskDate(task)+". Изменить задачу");
        TextView more=label("⋮",24,SUBTLE,false);more.setGravity(Gravity.CENTER);
        more.setContentDescription("Действия с задачей: "+task.title);touchBackground(more,Color.WHITE,14);
        row.addView(more,lp(48,48));more.setOnClickListener(v->taskOptions(task));
        done.setOnCheckedChangeListener((v,checked)->{
            done.setEnabled(false);task.done=checked;store.upsert(task);
            if(checked)ReminderScheduler.cancel(this,task.id);else ReminderScheduler.schedule(this,task);
            if(motion())row.animate().alpha(0).translationX(dp(12)).setDuration(160).withEndAction(()->refresh(true)).start();
            else refresh(false);
        });
        return row;
    }
    private String taskDate(Task task){
        if(task.dueAt==null)return "Без даты · в своём темпе";
        ZonedDateTime value=Instant.ofEpochMilli(task.dueAt).atZone(ZoneId.systemDefault());
        LocalDate day=value.toLocalDate(),today=LocalDate.now();
        String date=day.equals(today)?"Сегодня":day.equals(today.plusDays(1))?"Завтра":
                day.format(DateTimeFormatter.ofPattern("d MMM",RU));
        if(task.hasTime)date+=" · "+value.format(DateTimeFormatter.ofPattern("HH:mm"));
        if(!task.done&&(task.hasTime?task.dueAt<System.currentTimeMillis():day.isBefore(today)))date="Просрочено · "+date;
        if(task.repeat!=0)date+=task.repeat==1?" · ежедневно":" · еженедельно";
        return date;
    }
    private void taskOptions(Task task){
        new AlertDialog.Builder(this).setTitle(task.title).setItems(new String[]{"Изменить","Удалить"},(dialog,which)->{
            if(which==0)editTask(task);
            else new AlertDialog.Builder(this).setTitle("Удалить задачу?").setMessage(task.title)
                    .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{
                        ReminderScheduler.cancel(this,task.id);store.remove(task.id);refresh(true);
                    }).show();
        }).show();
    }
    private void editTask(Task task){
        LinearLayout layout=column();layout.setPadding(dp(24),dp(12),dp(24),dp(8));
        EditText edit=new EditText(this);edit.setText(task.title);edit.setTextSize(16);edit.setMinLines(2);edit.setMaxLines(5);
        edit.setTextColor(TEXT);edit.setSelectAllOnFocus(true);layout.addView(edit,lp(-1,-2));
        CheckBox removeDate=new CheckBox(this);removeDate.setText("Убрать дату и напоминание");removeDate.setTextColor(SUBTLE);
        removeDate.setVisibility(task.dueAt==null?View.GONE:View.VISIBLE);layout.addView(removeDate,lp(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Изменить задачу")
                .setMessage("Новое время можно написать словами, например «через 2 минуты». Без новой даты прежнее время сохранится.")
                .setView(layout).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String raw=edit.getText().toString().trim();if(raw.isEmpty()){edit.setError("Название не должно быть пустым");return;}
            TaskDateParser.Result parsed=TaskDateParser.parse(raw);task.title=parsed.title;
            if(removeDate.isChecked()){task.dueAt=null;task.hasTime=false;task.repeat=0;}
            else if(parsed.dueAt!=null){task.dueAt=parsed.dueAt;task.hasTime=parsed.hasTime;task.repeat=parsed.repeat;}
            ReminderScheduler.cancel(this,task.id);store.upsert(task);ReminderScheduler.schedule(this,task);
            refresh(true);dialog.dismiss();if(task.hasTime&&!task.done)ensureReminderAccess();
        }));dialog.show();
    }
}

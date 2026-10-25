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
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Native, offline UI. No WebView, network or heavyweight animation framework. */
public final class MainActivity extends Activity {
    private int BACKGROUND=Color.rgb(246,247,252);
    private int PURPLE=Color.rgb(73,112,223);
    private int TEXT=Color.rgb(33,35,57);
    private int SUBTLE=Color.rgb(105,110,133);
    private int CARD=Color.WHITE;
    private boolean dark;
    private static final int PALE=Color.rgb(237,233,255);
    private int LINE=Color.rgb(232,232,243);
    private static final int GREEN=Color.rgb(37,126,107);
    private static final Locale RU=new Locale("ru");
    private static final String[] FILTER_NAMES={"Все","Сегодня","Потом","Готово"};
    private int filter;
    private LinearLayout root,cards,chipRow,permissionCard,undoBar;
    private TextView undoMessage;
    private Runnable undoAction;
    private final Runnable hideUndo=()->{if(undoBar!=null)undoBar.setVisibility(View.GONE);undoAction=null;};
    private ScrollView scroll;
    private TextView preview,countLabel,heroTitle,heroDetail,permissionText;
    private ProgressBar progress;
    private EditText entry;
    private TaskStore store;
    private boolean askedExact;
    private String selectedFolder;
    private Long draftDue;
    private boolean draftHasTime;
    private TextView folderLabel,dateChip;
    private TextSwitcher hints;
    private int hintIndex;
    private final Set<Long> expanded=new HashSet<>();
    private static final String[] HINTS={"Что нужно сделать сегодня?", "Напиши, что сделать через час", "Например: чай через 2 минуты", "Разбей большую задачу на маленькие шаги"};
    private final Runnable hintTick=new Runnable(){
        @Override public void run(){
            if(hints!=null&&entry.length()==0&&!entry.hasFocus()&&motion())hints.setText(HINTS[(++hintIndex)%HINTS.length]);
            handler.postDelayed(this,4500);
        }
    };
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable previewUpdate=()->updatePreview();
    private final Runnable clockTick=new Runnable(){
        @Override public void run(){
            if(cards!=null){refresh(false);updatePreview();}
            handler.postDelayed(this,60000);
        }
    };

    @Override public void onCreate(Bundle state){
        dark=ThemeSettings.dark(this);setTheme(dark?R.style.AppTheme_Dark:R.style.AppTheme);
        super.onCreate(state);
        if(dark){BACKGROUND=0xFF121722;TEXT=0xFFF0F2FA;SUBTLE=0xFFB2BACF;CARD=0xFF1E2635;LINE=0xFF344055;PURPLE=0xFF91ACFF;}
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(BACKGROUND);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                |View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        int flags=getWindow().getDecorView().getSystemUiVisibility();
        int light=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(dark?flags&~light:flags|light);
        store=new TaskStore(this);
        ReminderScheduler.createChannel(this);
        if(state!=null){
            filter=state.getInt("filter",0);selectedFolder=state.getString("folder");
            if(state.containsKey("draft_due")){draftDue=state.getLong("draft_due");draftHasTime=state.getBoolean("draft_time");}
        }
        buildInterface();
        if(state!=null){entry.setText(state.getString("draft",""));entry.setSelection(entry.length());}
        openWidgetIntent(getIntent());
        AppUpdates.checkOnEntry(this);
    }
    @Override protected void onResume(){
        super.onResume();
        ReminderScheduler.scheduleAll(this);
        if(cards!=null)refresh(false);
        handler.removeCallbacks(clockTick);handler.postDelayed(clockTick,60000);
        handler.removeCallbacks(hintTick);if(motion())handler.postDelayed(hintTick,4500);
    }
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(clockTick);handler.removeCallbacks(hintTick);}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){
        state.putInt("filter",filter);state.putString("draft",entry.getText().toString());
        state.putString("folder",selectedFolder);
        if(draftDue!=null){state.putLong("draft_due",draftDue);state.putBoolean("draft_time",draftHasTime);}
        super.onSaveInstanceState(state);
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);openWidgetIntent(intent);}
    private void openWidgetIntent(Intent intent){
        if(intent==null)return;
        long id=intent.getLongExtra("open_task",-1);boolean add=intent.getBooleanExtra("new_task",false);
        intent.removeExtra("open_task");intent.removeExtra("new_task");
        if(add)handler.post(()->{entry.requestFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(entry,InputMethodManager.SHOW_IMPLICIT);});
        else if(id>=0)handler.post(()->{Task task=store.find(id);if(task!=null)editTask(task);});
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
        view.setOnTouchListener((v,event)->{
            if(motion()){
                boolean down=event.getActionMasked()==MotionEvent.ACTION_DOWN;
                if(down||event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)
                    v.animate().scaleX(down?.97f:1).scaleY(down?.97f:1).setDuration(down?90:240)
                            .setInterpolator(new OvershootInterpolator(.8f)).start();
            }
            return false;
        });
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
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout page=column();page.setPadding(dp(24),dp(18),dp(24),dp(28));
        scroll.addView(page,new ScrollView.LayoutParams(-1,-2));

        TextView date=label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM",RU)),13,SUBTLE,false);
        page.addView(date,lp(-1,-2));gap(page,8);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("Входящие",30,TEXT,true);
        heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView settings=label("⋯",26,PURPLE,true);settings.setGravity(Gravity.CENTER);
        settings.setContentDescription("Настройки приложения");touchBackground(settings,CARD,18);
        heading.addView(settings,lp(48,48));settings.setOnClickListener(v->showAppSettings());
        page.addView(heading,lp(-1,-2));
        folderLabel=label("Все заметки  ▾",14,PURPLE,true);folderLabel.setPadding(dp(10),dp(12),dp(10),dp(12));
        folderLabel.setMinHeight(dp(48));touchBackground(folderLabel,CARD,14);
        folderLabel.setOnClickListener(v->showFolders());folderLabel.setOnLongClickListener(v->{manageFolders();return true;});
        LinearLayout.LayoutParams foldersLp=lp(-1,-2);foldersLp.topMargin=dp(10);page.addView(folderLabel,foldersLp);gap(page,16);

        LinearLayout hero=column();hero.setPadding(dp(18),dp(16),dp(18),dp(16));
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(79,123,230),Color.rgb(66,96,199)});
        gradient.setCornerRadius(dp(22));hero.setBackground(gradient);
        heroTitle=label("Мой день",18,Color.WHITE,true);hero.addView(heroTitle,lp(-1,-2));gap(hero,6);
        heroDetail=label("",12,0xFFE7EEFF,false);
        hero.addView(heroDetail,lp(-1,-2));gap(hero,12);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);progress.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0x44FFFFFF));
        hero.addView(progress,lp(-1,5));page.addView(hero,lp(-1,-2));gap(page,18);

        undoBar=new LinearLayout(this);undoBar.setGravity(Gravity.CENTER_VERTICAL);undoBar.setPadding(dp(16),dp(4),dp(8),dp(4));
        undoBar.setBackground(bg(dark?CARD:TEXT,16));undoBar.setVisibility(View.GONE);
        undoMessage=label("Задача выполнена",13,Color.WHITE,false);
        undoBar.addView(undoMessage,new LinearLayout.LayoutParams(0,-2,1));
        TextView undo=label("Отменить",13,0xFFB8CEFF,true);undo.setGravity(Gravity.CENTER);touchBackground(undo,dark?CARD:TEXT,12);
        undoBar.addView(undo,lp(96,48));undo.setOnClickListener(v->{if(undoAction!=null)undoAction.run();handler.removeCallbacks(hideUndo);hideUndo.run();});
        LinearLayout.LayoutParams undoLp=lp(-1,-2);undoLp.setMargins(dp(20),0,dp(20),dp(8));root.addView(undoBar,undoLp);
        LinearLayout composer=column();composer.setPadding(dp(16),dp(10),dp(16),dp(12));
        composer.setBackground(border(CARD,22));
        hints=new TextSwitcher(this);hints.setFactory(()->label("",12,SUBTLE,false));hints.setCurrentText(HINTS[0]);
        android.view.animation.AnimationSet hintIn=new android.view.animation.AnimationSet(true);
        hintIn.addAnimation(new android.view.animation.AlphaAnimation(0,1));hintIn.addAnimation(new android.view.animation.TranslateAnimation(0,0,dp(5),0));hintIn.setDuration(220);
        android.view.animation.AnimationSet hintOut=new android.view.animation.AnimationSet(true);
        hintOut.addAnimation(new android.view.animation.AlphaAnimation(1,0));hintOut.addAnimation(new android.view.animation.TranslateAnimation(0,0,0,-dp(5)));hintOut.setDuration(160);
        if(motion()){hints.setInAnimation(hintIn);hints.setOutAnimation(hintOut);}composer.addView(hints,lp(-1,-2));
        entry=new EditText(this);entry.setId(View.generateViewId());entry.setTextSize(16);entry.setTextColor(TEXT);
        entry.setHintTextColor(SUBTLE);entry.setHint("Название задачи или заметки");
        entry.setContentDescription("Название задачи и время напоминания");
        entry.setBackgroundColor(Color.TRANSPARENT);entry.setPadding(0,dp(8),0,dp(8));
        entry.setMinLines(1);entry.setMaxLines(5);entry.setGravity(Gravity.TOP|Gravity.START);
        entry.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        entry.setImeOptions(EditorInfo.IME_ACTION_DONE);composer.addView(entry,lp(-1,-2));
        dateChip=label(DateTimePicker.format(draftDue,draftHasTime),12,PURPLE,true);dateChip.setPadding(dp(10),dp(12),dp(10),dp(12));
        dateChip.setMinHeight(dp(48));touchBackground(dateChip,BACKGROUND,12);
        dateChip.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Дата задачи")
                .setItems(new String[]{"Выбрать дату и время","Убрать выбранную дату"},(dialog,index)->{
                    if(index==0)DateTimePicker.show(this,draftDue,(due,time)->setDraftDate(due,time));else setDraftDate(null,false);
                }).show());composer.addView(dateChip,lp(-1,-2));
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
            shortcut.setPadding(dp(12),0,dp(12),0);touchBackground(shortcut,CARD,12);
            LinearLayout.LayoutParams p=lp(-2,48);p.rightMargin=dp(4);shortcuts.addView(shortcut,p);
            shortcut.setOnClickListener(v->{
                String raw=entry.getText().toString().trim();
                TaskDateParser.Result parsed=TaskDateParser.parse(raw);
                String name=parsed.dueAt==null?raw:parsed.title;
                if(name.equals("Напоминание"))name="";
                draftDue=null;draftHasTime=false;dateChip.setText("Выбрать дату");
                entry.setText((name+" "+phrase).trim());entry.setSelection(entry.length());
                updatePreview();
            });
        }
        suggestions.addView(shortcuts);composer.addView(suggestions,lp(-1,-2));gap(composer,4);
        TextView add=label("＋  Добавить задачу",15,dark?BACKGROUND:Color.WHITE,true);add.setGravity(Gravity.CENTER);
        add.setPadding(dp(14),dp(16),dp(14),dp(16));add.setMinHeight(dp(52));touchBackground(add,PURPLE,16);
        composer.addView(add,lp(-1,-2));add.setOnClickListener(v->createTask());
        entry.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_DONE){createTask();return true;}return false;
        });
        entry.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                hints.setVisibility(s.length()==0?View.VISIBLE:View.GONE);
                handler.removeCallbacks(previewUpdate);handler.postDelayed(previewUpdate,100);
            }
            @Override public void afterTextChanged(Editable e){}
        });
        LinearLayout.LayoutParams composerLp=lp(-1,-2);composerLp.setMargins(dp(20),dp(4),dp(20),dp(10));
        root.addView(composer,composerLp);

        permissionCard=column();permissionCard.setPadding(dp(16),dp(14),dp(16),dp(14));
        int warningText=dark?0xFFFFD89C:Color.rgb(123,85,25);
        touchBackground(permissionCard,dark?0xFF3B3020:Color.rgb(255,245,225),18);
        permissionCard.addView(label("Проверь напоминания",14,warningText,true),lp(-1,-2));
        gap(permissionCard,6);permissionText=label("",12,warningText,false);
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
        String raw=entry.getText().toString();TaskDateParser.Result parsed=parsedEntry();
        if(raw.trim().isEmpty()&&draftDue==null){
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
    private TaskDateParser.Result parsedEntry(){
        TaskDateParser.Result parsed=TaskDateParser.parse(entry.getText().toString());
        return draftDue==null?parsed:new TaskDateParser.Result(parsed.title,draftDue,draftHasTime,parsed.repeat);
    }
    private void setDraftDate(Long due,boolean time){
        draftDue=due;draftHasTime=time;dateChip.setText(DateTimePicker.format(due,time));updatePreview();
    }
    private void createTask(){
        String raw=entry.getText().toString().trim();
        if(raw.isEmpty()){entry.setError("Напиши, что нужно сделать");entry.requestFocus();return;}
        TaskDateParser.Result parsed=parsedEntry();
        Task task=new Task(store.allocateId(),parsed.title,parsed.dueAt,parsed.hasTime,parsed.repeat,false);
        task.folder=selectedFolder==null?"":selectedFolder;
        store.upsert(task);ReminderScheduler.schedule(this,task);
        draftDue=null;draftHasTime=false;dateChip.setText("Выбрать дату");
        handler.removeCallbacks(previewUpdate);entry.setText("");updatePreview();filter=0;
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(entry.getWindowToken(),0);
        root.requestFocus();refresh(true);
        Toast.makeText(this,task.hasTime?"Задача и напоминание сохранены":"Задача сохранена",Toast.LENGTH_SHORT).show();
        if(task.dueAt!=null&&task.hasTime)ensureReminderAccess();
    }
    private boolean notificationsEnabled(){
        return ReminderScheduler.notificationsEnabled(this);
    }
    private void ensureReminderAccess(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},17);return;
        }
        maybeAskExact();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==17){ReminderScheduler.scheduleAll(this);refresh(false);if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)maybeAskExact();}
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
                "Фоновая работа на OnePlus", "Проверить уведомление сейчас", "Таймер на 10 секунд", "Диагностика"};
        new AlertDialog.Builder(this).setTitle("Настройки напоминаний").setItems(options,(d,which)->{
            if(which==0){
                if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
                        &&shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))ensureReminderAccess();
                else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
            }
            else if(which==1){if(Build.VERSION.SDK_INT>=31)openExactSettings();else Toast.makeText(this,"Точные напоминания доступны",Toast.LENGTH_SHORT).show();}
            else if(which==2)new AlertDialog.Builder(this).setTitle("Напоминания на OnePlus")
                    .setMessage("В настройках приложения открой «Использование батареи» и разреши фоновую работу, если уведомления задерживаются. Названия пунктов зависят от OxygenOS. Принудительная остановка приложения отключает напоминания до следующего открытия.")
                    .setNegativeButton("Понятно",null).setPositiveButton("Настройки приложения",(a,b)->openAppSettings()).show();
            else if(which==3){
                boolean posted=ReminderReceiver.post(this,null);
                ReminderScheduler.record(this,posted?"Тест передан Android":"Тест заблокирован настройками уведомлений");
                new AlertDialog.Builder(this).setTitle(posted?"Проверь шторку уведомлений":"Уведомления выключены")
                        .setMessage(posted?"Если уведомление есть в шторке, но нет звука или баннера, проверь канал «Напоминания» и режим «Не беспокоить».":"Разреши уведомления в настройках приложения, затем повтори тест.")
                        .setPositiveButton("Понятно",null).show();
            }else if(which==4){
                Task test=new Task(store.allocateId(),"Проверка напоминаний",System.currentTimeMillis()+10000,true,0,false);
                store.upsert(test);ReminderScheduler.schedule(this,test);refresh(true);ensureReminderAccess();
                Toast.makeText(this,ReminderScheduler.hasExactAccess(this)?"Заблокируй экран. Напоминание через 10 секунд":"Без доступа к точным таймерам Android может задержать тест",Toast.LENGTH_LONG).show();
            }else new AlertDialog.Builder(this).setTitle("Диагностика напоминаний")
                    .setMessage(ReminderScheduler.diagnostics(this)).setPositiveButton("Закрыть",null)
                    .setNeutralButton("Скопировать",(a,b)->{
                        ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(
                                android.content.ClipData.newPlainText("Диагностика",ReminderScheduler.diagnostics(this)));
                    }).show();
        }).setNegativeButton("Закрыть",null).show();
    }
    private void showAppSettings(){
        new AlertDialog.Builder(this).setTitle("Настройки")
                .setItems(new String[]{"Тема приложения","Обновления","Напоминания","Как добавить виджет"},(d,index)->{
                    if(index==0)new AlertDialog.Builder(this).setTitle("Тема приложения")
                            .setSingleChoiceItems(new String[]{"Как в системе","Светлая","Тёмная"},ThemeSettings.mode(this),(dialog,mode)->{
                                ThemeSettings.setMode(this,mode);TaskWidgetProvider.updateAll(this);dialog.dismiss();recreate();
                            }).setNegativeButton("Отмена",null).show();
                    else if(index==1)AppUpdates.show(this);
                    else if(index==2)showReminderSettings();
                    else new AlertDialog.Builder(this).setTitle("Виджеты")
                            .setMessage("Зажми свободное место на рабочем столе → Виджеты → Свои задачи. Выбери «Список» или «Сегодня». Размер можно изменить долгим нажатием на виджет.")
                            .setPositiveButton("Понятно",null).show();
                }).show();
    }
    private void renderChips(){
        chipRow.removeAllViews();
        for(int i=0;i<FILTER_NAMES.length;i++){
            final int index=i;TextView chip=label(FILTER_NAMES[i],13,i==filter?(dark?BACKGROUND:Color.WHITE):SUBTLE,true);
            chip.setGravity(Gravity.CENTER);chip.setPadding(dp(16),dp(13),dp(16),dp(13));chip.setMinHeight(dp(48));
            touchBackground(chip,i==filter?PURPLE:CARD,16);
            chip.setSelected(i==filter);chip.setContentDescription(FILTER_NAMES[i]+(i==filter?", выбрано":""));
            LinearLayout.LayoutParams p=lp(-2,-2);p.rightMargin=dp(6);chipRow.addView(chip,p);
            chip.setOnClickListener(v->{if(filter!=index){
                int direction=index>filter?1:-1;filter=index;
                if(motion())cards.animate().alpha(0).translationX(dp(-12*direction)).setDuration(90).withEndAction(()->{
                    refresh(false);cards.setTranslationX(dp(12*direction));cards.animate().alpha(1).translationX(0)
                            .setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
                }).start();
                else refresh(false);
            }});
        }
    }
    private void refresh(boolean animate){
        if(cards==null)return;
        List<Task> tasks=store.all();
        if(selectedFolder!=null&&!selectedFolder.isEmpty()&&!store.folders().contains(selectedFolder))selectedFolder=null;
        folderLabel.setText((selectedFolder==null?"Все заметки":selectedFolder.isEmpty()?"Без папки":selectedFolder)+"  ▾");
        if(selectedFolder!=null)tasks.removeIf(task->!task.folder.equals(selectedFolder));
        tasks.sort((a,b)->{
            if(a.done!=b.done)return a.done?1:-1;
            if(a.dueAt==null&&b.dueAt==null)return Long.compare(b.id,a.id);
            if(a.dueAt==null)return 1;if(b.dueAt==null)return -1;
            int comparison=Long.compare(a.dueAt,b.dueAt);return comparison!=0?comparison:Long.compare(a.id,b.id);
        });
        long active=tasks.stream().filter(t->!t.done).count();long done=tasks.size()-active;
        heroTitle.setText(active==0?"Все задачи выполнены":"Осталось задач: "+active);
        heroDetail.setText(tasks.isEmpty()?"Добавь первую задачу внизу": "Выполнено "+done+" из "+tasks.size()+" · смахни задачу, чтобы завершить");
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
            LinearLayout empty=column();empty.setPadding(dp(20),dp(30),dp(20),dp(30));empty.setBackground(border(CARD,24));
            TextView icon=label(filter==3?"✓":"○",36,PURPLE,false);icon.setGravity(Gravity.CENTER);empty.addView(icon,lp(-1,50));gap(empty,12);
            TextView title=label(filter==3?"Пока без галочек":filter==1?"На сегодня всё спокойно":filter==2?"Будущее свободно":"Чистый лист",18,TEXT,true);
            title.setGravity(Gravity.CENTER);empty.addView(title,lp(-1,-2));gap(empty,8);
            TextView detail=label(filter==3?"Завершённые задачи появятся здесь":"Добавь задачу внизу. Например:\n«Выпить воду через 2 минуты»",13,SUBTLE,false);
            detail.setGravity(Gravity.CENTER);empty.addView(detail,lp(-1,-2));cards.addView(empty,lp(-1,-2));
            if(animate)enter(empty,0);
        }
    }
    private View createCard(Task task){
        SwipeTaskLayout row=new SwipeTaskLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6),dp(10),dp(6),dp(10));row.setBackground(border(CARD,18));
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
        text.addView(date,lp(-1,-2));
        TextView summary=label((task.folder.isEmpty()?"":task.folder+" · ")+(task.items.isEmpty()?"Раскрыть заметку ▾":task.completedItems()+"/"+task.items.size()+" пунктов ▾"),12,SUBTLE,false);
        LinearLayout.LayoutParams summaryLp=lp(-1,-2);summaryLp.topMargin=dp(7);text.addView(summary,summaryLp);
        LinearLayout details=column();details.setVisibility(expanded.contains(task.id)?View.VISIBLE:View.GONE);text.addView(details,lp(-1,-2));
        gap(details,12);
        TextView description=label(task.description.isEmpty()?"Добавь описание или чек-лист через «Редактировать»":task.description,14,SUBTLE,false);
        description.setTextIsSelectable(true);details.addView(description,lp(-1,-2));
        for(Task.Item item:task.items){
            CheckBox bullet=new CheckBox(this);bullet.setText(item.text);bullet.setTextSize(14);bullet.setTextColor(TEXT);bullet.setMinHeight(dp(48));
            bullet.setButtonTintList(ColorStateList.valueOf(PURPLE));bullet.setChecked(item.done);
            bullet.setOnCheckedChangeListener((v,checked)->{
                item.done=checked;store.upsert(task);summary.setText(task.completedItems()+"/"+task.items.size()+" пунктов ▾");
                if(motion())bullet.animate().alpha(.65f).setDuration(80).withEndAction(()->bullet.animate().alpha(1).setDuration(140).start()).start();
            });details.addView(bullet,lp(-1,-2));
        }
        TextView edit=label("Редактировать заметку",13,PURPLE,true);edit.setMinHeight(dp(48));touchBackground(edit,BACKGROUND,12);
        details.addView(edit,lp(-1,-2));edit.setOnClickListener(v->editTask(task));
        text.setOnClickListener(v->{
            boolean open=details.getVisibility()!=View.VISIBLE;
            if(motion())android.transition.TransitionManager.beginDelayedTransition(cards,new android.transition.AutoTransition().setDuration(180));
            if(open)expanded.add(task.id);else expanded.remove(task.id);details.setVisibility(open?View.VISIBLE:View.GONE);
        });
        text.setFocusable(true);text.setContentDescription(task.title+". "+taskDate(task)+". Раскрыть описание и чек-лист");
        TextView more=label("⋮",24,SUBTLE,false);more.setGravity(Gravity.CENTER);
        more.setContentDescription("Действия с задачей: "+task.title);touchBackground(more,CARD,14);
        row.addView(more,lp(48,48));more.setOnClickListener(v->taskOptions(task));
        row.onComplete(()->completeTask(task,!task.done,row,title));
        done.setOnCheckedChangeListener((v,checked)->{done.setEnabled(false);completeTask(task,checked,row,title);});
        return row;
    }
    private void completeTask(Task task,boolean checked,View row,TextView title){
        boolean previous=task.done;if(checked==previous)return;
        row.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        task.done=checked;store.upsert(task);
        if(checked)ReminderScheduler.cancel(this,task.id);else ReminderScheduler.schedule(this,task);
        undoAction=()->{
            Task current=store.find(task.id);if(current==null)return;
            current.done=previous;store.upsert(current);
            if(previous)ReminderScheduler.cancel(this,current.id);else ReminderScheduler.schedule(this,current);
            refresh(false);
        };
        undoMessage.setText(checked?"Задача выполнена":"Задача возвращена");undoBar.setVisibility(View.VISIBLE);enter(undoBar,0);
        handler.removeCallbacks(hideUndo);handler.postDelayed(hideUndo,5000);
        if(!motion()){refresh(false);return;}
        if(checked)title.setPaintFlags(title.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        row.animate().alpha(0).scaleX(.96f).scaleY(.96f).setDuration(160).withEndAction(()->{
            int height=row.getHeight();if(height<=0){refresh(false);return;}
            ValueAnimator collapse=ValueAnimator.ofInt(height,0);collapse.setDuration(180);
            collapse.setInterpolator(new DecelerateInterpolator());
            collapse.addUpdateListener(a->{row.getLayoutParams().height=(int)a.getAnimatedValue();row.requestLayout();});
            collapse.addListener(new android.animation.AnimatorListenerAdapter(){
                @Override public void onAnimationEnd(android.animation.Animator animator){refresh(false);}
            });collapse.start();
        }).start();
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
        new AlertDialog.Builder(this).setTitle(task.title).setItems(new String[]{"Изменить","Через час","Завтра в 9:00","Удалить"},(dialog,which)->{
            if(which==0)editTask(task);
            else if(which==1||which==2){
                ReminderScheduler.cancel(this,task.id);task.done=false;task.hasTime=true;
                task.dueAt=(which==1?ZonedDateTime.now().plusHours(1):LocalDate.now().plusDays(1).atTime(9,0).atZone(ZoneId.systemDefault())).toInstant().toEpochMilli();
                store.upsert(task);ReminderScheduler.schedule(this,task);refresh(true);ensureReminderAccess();
            }
            else new AlertDialog.Builder(this).setTitle("Удалить задачу?").setMessage(task.title)
                    .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{
                        ReminderScheduler.cancel(this,task.id);store.remove(task.id);refresh(true);
                    }).show();
        }).show();
    }
    private void editTask(Task task){
        TaskEditor.show(this,task,()->{expanded.add(task.id);refresh(true);Task updated=store.find(task.id);if(updated!=null&&updated.hasTime&&!updated.done)ensureReminderAccess();});
    }
    private void showFolders(){
        List<String> folders=store.folders();List<String> options=new ArrayList<>();
        options.add("Все заметки");options.add("Без папки");options.addAll(folders);options.add("＋ Создать папку");options.add("Управление папками");
        new AlertDialog.Builder(this).setTitle("Папки").setItems(options.toArray(new String[0]),(d,index)->{
            if(index==folders.size()+2)folderName(null);
            else if(index==folders.size()+3)manageFolders();
            else {selectedFolder=index==0?null:index==1?"":folders.get(index-2);filter=0;refresh(true);}
        }).show();
    }
    private void folderName(String old){
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Например: Учёба");if(old!=null)name.setText(old);
        LinearLayout content=column();content.setPadding(dp(24),dp(8),dp(24),0);content.addView(name,lp(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(old==null?"Новая папка":"Название папки").setView(content)
                .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=name.getText().toString().trim();
            boolean success=old==null?store.addFolder(value):store.renameFolder(old,value);
            if(!success){name.setError("Введи непустое уникальное название");return;}
            selectedFolder=value;filter=0;refresh(true);dialog.dismiss();
        }));dialog.show();
    }
    private void manageFolders(){
        List<String> folders=store.folders();if(folders.isEmpty()){folderName(null);return;}
        new AlertDialog.Builder(this).setTitle("Управление папками").setItems(folders.toArray(new String[0]),(d,index)->{
            String folder=folders.get(index);new AlertDialog.Builder(this).setTitle(folder)
                    .setItems(new String[]{"Переименовать","Удалить папку"},(a,operation)->{
                        if(operation==0)folderName(folder);
                        else new AlertDialog.Builder(this).setTitle("Удалить папку?")
                                .setMessage("Заметки не удалятся и останутся в разделе «Без папки».")
                                .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(b,c)->{
                                    store.removeFolder(folder);if(folder.equals(selectedFolder))selectedFolder="";refresh(true);
                                }).show();
                    }).show();
        }).show();
    }
}

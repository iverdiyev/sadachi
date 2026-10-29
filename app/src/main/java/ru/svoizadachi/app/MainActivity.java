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
    private boolean dark,iosStyle;
    private static final int PALE=Color.rgb(237,233,255);
    private int LINE=Color.rgb(232,232,243);
    private static final int GREEN=Color.rgb(37,126,107);
    private static final Locale RU=new Locale("ru");
    private static final String[] FILTER_NAMES={"Все","Сегодня","Предстоящие","Выполнено"};
    private int filter;
    private LinearLayout root,cards,chipRow,permissionCard,undoBar,composer,quickBar,settingsScreen,settingsGroupRows;
    private TextView undoMessage;
    private Runnable undoAction;
    private final Runnable hideUndo=()->{if(undoBar!=null)undoBar.setVisibility(View.GONE);undoAction=null;};
    private ScrollView scroll;
    private HorizontalScrollView suggestions;
    private boolean composerOpen,settingsOpen,keyboardVisible;
    private TextView preview,countLabel,heroTitle,heroDetail,permissionText;
    private ProgressBar progress;
    private EditText entry;
    private TaskStore store;
    private VoiceInput voice;
    private final Map<TextView,Task> dateLabels=new HashMap<>();
    private final java.util.concurrent.ExecutorService io=java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean askedExact;
    private String selectedFolder;
    private Long draftDue;
    private boolean draftHasTime;
    private TextView folderLabel,dateChip,timeChip;
    private TextSwitcher hints;
    private int hintIndex;
    private final Set<Long> expanded=new HashSet<>();
    private final Map<Long,CharSequence> noteDrafts=new HashMap<>();
    private boolean inlineEditing;
    private static final String[] HINTS={"Что нужно сделать сегодня?", "Напиши, что сделать через час", "Например: чай через 2 минуты", "Первая строка — заголовок, дальше — описание"};
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
            if(cards!=null){for(Map.Entry<TextView,Task> row:dateLabels.entrySet())row.getKey().setText(taskDate(row.getValue()));updatePreview();}
            handler.postDelayed(this,60000);
        }
    };

    @Override public void onCreate(Bundle state){
        dark=ThemeSettings.dark(this);iosStyle=ThemeSettings.ios(this);
        setTheme(iosStyle?(dark?R.style.AppTheme_Ios_Dark:R.style.AppTheme_Ios):(dark?R.style.AppTheme_Dark:R.style.AppTheme));
        super.onCreate(state);
        BACKGROUND=ThemeSettings.background(this);TEXT=ThemeSettings.text(this);SUBTLE=ThemeSettings.subtle(this);
        CARD=ThemeSettings.card(this);LINE=ThemeSettings.border(this);PURPLE=ThemeSettings.accent(this);
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
        voice=new VoiceInput(this);
        LauncherIcon.apply(this);
        ReminderScheduler.createChannel(this);
        if(state!=null){
            filter=state.getInt("filter",0);selectedFolder=state.getString("folder");
            if(state.containsKey("draft_due")){draftDue=state.getLong("draft_due");draftHasTime=state.getBoolean("draft_time");}
            long[] drafts=state.getLongArray("note_draft_ids");if(drafts!=null)for(long id:drafts){CharSequence text=state.getCharSequence("note_draft_"+id);if(text!=null){noteDrafts.put(id,text);expanded.add(id);}}
        }
        buildInterface();
        if(state!=null){entry.setText(state.getString("draft",""));entry.setSelection(entry.length());
            if(state.getBoolean("composer_open",false)||entry.length()>0)showComposer(false);
            if(state.getBoolean("settings_open",false))showAppSettings();}
        openWidgetIntent(getIntent());
        AppUpdates.checkOnEntry(this);
    }
    @Override protected void onResume(){
        super.onResume();
        ReminderScheduler.scheduleAll(this);
        if(cards!=null)refresh(false);
        handler.removeCallbacks(clockTick);handler.postDelayed(clockTick,60000);
        handler.removeCallbacks(hintTick);
    }
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(clockTick);handler.removeCallbacks(hintTick);if(voice!=null)voice.pause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(voice!=null)voice.destroy();io.shutdown();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){
        state.putInt("filter",filter);state.putString("draft",entry.getText().toString());
        state.putBoolean("composer_open",composerOpen);state.putBoolean("settings_open",settingsOpen);
        state.putString("folder",selectedFolder);
        if(draftDue!=null){state.putLong("draft_due",draftDue);state.putBoolean("draft_time",draftHasTime);}
        long[] ids=new long[noteDrafts.size()];int index=0;for(Map.Entry<Long,CharSequence> draft:noteDrafts.entrySet()){
            ids[index++]=draft.getKey();state.putCharSequence("note_draft_"+draft.getKey(),draft.getValue());
        }state.putLongArray("note_draft_ids",ids);
        super.onSaveInstanceState(state);
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);openWidgetIntent(intent);}
    private void openWidgetIntent(Intent intent){
        if(intent==null)return;
        long id=intent.getLongExtra("open_task",-1);boolean add=intent.getBooleanExtra("new_task",false);
        intent.removeExtra("open_task");intent.removeExtra("new_task");
        if(add)handler.post(()->showComposer(true));
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
                    v.animate().scaleX(down?.97f:1).scaleY(down?.97f:1).setDuration(down?120:160)
                            .setInterpolator(new DecelerateInterpolator()).start();
            }
            return false;
        });
    }
    private TextView label(String text,int size,int color,boolean bold){
        TextView view=new TextView(this);view.setText(text);view.setTextColor(color);
        UiTokens.text(view,size,bold);view.setGravity(Gravity.CENTER_VERTICAL);
        view.setLineSpacing(0,1.06f);return view;
    }
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private void gap(LinearLayout parent,int height){parent.addView(new View(this),lp(1,height));}
    private boolean motion(){return ValueAnimator.areAnimatorsEnabled();}
    @Override public void onBackPressed(){
        if(settingsOpen){closeSettings();return;}
        if(composerOpen){showQuickBar();return;}
        super.onBackPressed();
    }
    private void hideKeyboard(){
        View focused=getCurrentFocus();
        if(focused!=null){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(),0);focused.clearFocus();}
        root.requestFocus();
    }
    private void showComposer(boolean focus){
        if(settingsOpen)closeSettings();
        composerOpen=true;composer.setVisibility(View.VISIBLE);quickBar.setVisibility(View.GONE);
        if(motion())enter(composer,0);
        if(focus){entry.requestFocus();entry.post(()->{
            if(composerOpen&&!settingsOpen&&entry.hasFocus())
                ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(entry,InputMethodManager.SHOW_IMPLICIT);
        });}
    }
    private void showQuickBar(){
        hideKeyboard();composerOpen=false;composer.setVisibility(View.GONE);quickBar.setVisibility(View.VISIBLE);
    }
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
                keyboardVisible=ime.bottom>safe.bottom;
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
            view.setPadding(left,top,right,bottom);
            if(suggestions!=null&&composerOpen)suggestions.setVisibility(keyboardVisible?View.GONE:View.VISIBLE);
            return insets;
        });
        root.requestApplyInsets();
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        boolean ios=iosStyle;
        LinearLayout page=column();int pageInset=UiTokens.page(this);page.setPadding(pageInset,dp(12),pageInset,dp(20));
        scroll.addView(page,new ScrollView.LayoutParams(-1,-2));

        String todayLabel=LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM",RU));
        TextView date=label(todayLabel.substring(0,1).toUpperCase(RU)+todayLabel.substring(1),13,SUBTLE,false);
        page.addView(date,lp(-1,-2));gap(page,4);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("Задачи",UiTokens.HEADING,TEXT,true);
        heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView theme=label(dark?"☀":"☾",20,PURPLE,false);theme.setGravity(Gravity.CENTER);
        theme.setContentDescription(dark?"Включить светлую тему":"Включить тёмную тему");touchBackground(theme,ThemeSettings.field(this),14);
        heading.addView(theme,lp(44,44));theme.setOnClickListener(v->{ThemeSettings.setMode(this,dark?1:2);TaskWidgetProvider.updateAll(this);recreate();});
        TextView settings=label("⋯",21,PURPLE,false);settings.setGravity(Gravity.CENTER);
        settings.setContentDescription("Настройки приложения");touchBackground(settings,ThemeSettings.field(this),14);
        LinearLayout.LayoutParams settingsLp=lp(44,44);settingsLp.leftMargin=dp(8);
        heading.addView(settings,settingsLp);settings.setOnClickListener(v->showAppSettings());
        page.addView(heading,lp(-1,-2));
        folderLabel=label("Все заметки  ▾",13,PURPLE,true);folderLabel.setPadding(dp(12),0,dp(12),0);
        folderLabel.setMinHeight(dp(40));touchBackground(folderLabel,ThemeSettings.field(this),14);
        folderLabel.setOnClickListener(v->showFolders());folderLabel.setOnLongClickListener(v->{manageFolders();return true;});
        LinearLayout.LayoutParams foldersLp=lp(-2,-2);foldersLp.topMargin=dp(8);page.addView(folderLabel,foldersLp);

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
        hero.addView(progress,lp(-1,5));hero.setVisibility(View.GONE);page.addView(hero,lp(-1,-2));

        undoBar=new LinearLayout(this);undoBar.setGravity(Gravity.CENTER_VERTICAL);undoBar.setPadding(dp(16),dp(4),dp(8),dp(4));
        undoBar.setBackground(bg(dark?CARD:TEXT,16));undoBar.setVisibility(View.GONE);
        undoMessage=label("Задача выполнена",13,Color.WHITE,false);
        undoBar.addView(undoMessage,new LinearLayout.LayoutParams(0,-2,1));
        TextView undo=label("Отменить",13,0xFFB8CEFF,true);undo.setGravity(Gravity.CENTER);touchBackground(undo,dark?CARD:TEXT,12);
        undoBar.addView(undo,lp(96,48));undo.setOnClickListener(v->{if(undoAction!=null)undoAction.run();handler.removeCallbacks(hideUndo);hideUndo.run();});
        LinearLayout.LayoutParams undoLp=lp(-1,-2);undoLp.setMargins(dp(20),0,dp(20),dp(8));root.addView(undoBar,undoLp);
        composer=column();composer.setPadding(dp(16),dp(12),dp(16),dp(12));
        composer.setBackground(bg(CARD,20));
        hints=new TextSwitcher(this);hints.setFactory(()->label("",12,SUBTLE,false));hints.setCurrentText(HINTS[0]);
        composer.addView(hints,lp(-1,-2));hints.setVisibility(View.GONE);
        LinearLayout quickRow=new LinearLayout(this);quickRow.setGravity(Gravity.CENTER_VERTICAL);composer.addView(quickRow,lp(-1,-2));
        entry=new EditText(this);entry.setId(View.generateViewId());entry.setTextSize(16);entry.setTextColor(TEXT);
        entry.setHintTextColor(SUBTLE);entry.setHint(ios?"Новая задача или заметка":"Название задачи");
        entry.setContentDescription("Название задачи и время напоминания");
        entry.setBackground(bg(ThemeSettings.field(this),12));entry.setPadding(dp(14),dp(10),dp(14),dp(10));
        entry.setMinLines(1);entry.setMaxLines(3);entry.setGravity(Gravity.TOP|Gravity.START);
        entry.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        entry.setImeOptions(EditorInfo.IME_ACTION_DONE);quickRow.addView(entry,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton microphone=new ImageButton(this);microphone.setImageResource(R.drawable.voice_mic);microphone.setImageTintList(ColorStateList.valueOf(PURPLE));microphone.setContentDescription("Голосовой ввод по-русски, офлайн");
        touchBackground(microphone,ThemeSettings.field(this),12);LinearLayout.LayoutParams micLp=lp(44,44);micLp.leftMargin=dp(8);quickRow.addView(microphone,micLp);microphone.setOnClickListener(v->{hideKeyboard();startVoiceCapture();});
        LinearLayout metadata=new LinearLayout(this);metadata.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams metaLp=lp(-1,-2);metaLp.topMargin=dp(8);composer.addView(metadata,metaLp);
        dateChip=label(draftDue==null?"Дата":DateTimePicker.compact(draftDue,false),13,PURPLE,true);dateChip.setPadding(dp(10),0,dp(10),0);
        dateChip.setContentDescription("Выбрать дату задачи");
        dateChip.setMinHeight(dp(40));dateChip.setSingleLine(true);
        dateChip.setEllipsize(android.text.TextUtils.TruncateAt.END);touchBackground(dateChip,ThemeSettings.field(this),12);
        dateChip.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Дата задачи")
                .setItems(new String[]{"Выбрать дату и время","Убрать выбранную дату"},(dialog,index)->{
                    if(index==0)DateTimePicker.show(this,draftDue,(due,time)->setDraftDate(due,time));else setDraftDate(null,false);
                }).show());metadata.addView(dateChip,new LinearLayout.LayoutParams(0,dp(40),1));
        timeChip=label(draftDue!=null&&draftHasTime?Instant.ofEpochMilli(draftDue).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm")):"Время",13,PURPLE,true);timeChip.setPadding(dp(10),0,dp(10),0);
        timeChip.setMinHeight(dp(40));timeChip.setSingleLine(true);timeChip.setContentDescription("Выбрать время задачи");
        touchBackground(timeChip,ThemeSettings.field(this),12);
        LinearLayout.LayoutParams timeLp=new LinearLayout.LayoutParams(0,dp(40),1);timeLp.leftMargin=dp(8);metadata.addView(timeChip,timeLp);
        timeChip.setOnClickListener(v->{
            if(draftDue==null)DateTimePicker.show(this,null,(due,time)->setDraftDate(due,time));
            else DateTimePicker.showTime(this,draftDue,(due,time)->setDraftDate(due,time));
        });
        TextView folderChoice=label(selectedFolder==null||selectedFolder.isEmpty()?"Список  ▾":selectedFolder+"  ▾",13,PURPLE,true);folderChoice.setPadding(dp(10),0,dp(10),0);
        folderChoice.setMinHeight(dp(40));folderChoice.setSingleLine(true);
        folderChoice.setEllipsize(android.text.TextUtils.TruncateAt.END);touchBackground(folderChoice,ThemeSettings.field(this),12);
        LinearLayout.LayoutParams folderChoiceLp=new LinearLayout.LayoutParams(0,dp(40),1);folderChoiceLp.leftMargin=dp(8);metadata.addView(folderChoice,folderChoiceLp);
        folderChoice.setOnClickListener(v->{List<String> names=new ArrayList<>();names.add("Без папки");names.addAll(store.folders());
            int chosen=selectedFolder==null||selectedFolder.isEmpty()?0:Math.max(0,names.indexOf(selectedFolder));
            new AlertDialog.Builder(this).setTitle("Список задачи").setSingleChoiceItems(names.toArray(new String[0]),chosen,(dialog,index)->{
                selectedFolder=index==0?"":names.get(index);folderChoice.setText(index==0?"Список  ▾":names.get(index)+"  ▾");dialog.dismiss();
            }).setNegativeButton("Отмена",null).show();
        });
        preview=label("Дату и время можно написать словами",12,SUBTLE,false);
        preview.setVisibility(View.GONE);
        preview.setPadding(dp(4),dp(4),dp(4),dp(4));composer.addView(preview,lp(-1,-2));
        suggestions=new HorizontalScrollView(this);suggestions.setHorizontalScrollBarEnabled(false);
        suggestions.setVisibility(View.GONE);
        entry.setOnFocusChangeListener((v,focused)->suggestions.setVisibility(focused&&!keyboardVisible?View.VISIBLE:View.GONE));
        LinearLayout shortcuts=new LinearLayout(this);
        String[] names={"Сегодня","Завтра","Через час"};
        String[] phrases={"сегодня","завтра","через час"};
        for(int i=0;i<names.length;i++){
            final String phrase=phrases[i];
            TextView shortcut=label(names[i],12,PURPLE,true);shortcut.setGravity(Gravity.CENTER);
            shortcut.setPadding(dp(12),0,dp(12),0);touchBackground(shortcut,ThemeSettings.field(this),16);
            LinearLayout.LayoutParams p=lp(-2,32);p.rightMargin=dp(6);shortcuts.addView(shortcut,p);
            shortcut.setOnClickListener(v->{
                String raw=entry.getText().toString().trim();
                TaskDateParser.Result parsed=TaskDateParser.parse(raw);
                String name=parsed.dueAt==null?raw:parsed.title;
                if(name.equals("Напоминание"))name="";
                setDraftDate(null,false);
                entry.setText((name+" "+phrase).trim());entry.setSelection(entry.length());
                updatePreview();
            });
        }
        suggestions.addView(shortcuts);LinearLayout.LayoutParams suggestionLp=lp(-1,-2);suggestionLp.topMargin=dp(8);composer.addView(suggestions,suggestionLp);
        LinearLayout.LayoutParams actionsLp=lp(-1,-2);actionsLp.topMargin=dp(8);composer.addView(actions,actionsLp);
        TextView cancel=label("Отмена",13,SUBTLE,false);cancel.setGravity(Gravity.CENTER);cancel.setMinHeight(dp(44));
        actions.addView(cancel,lp(-2,44));cancel.setOnClickListener(v->showQuickBar());
        TextView extra=label("Ещё",13,PURPLE,true);extra.setGravity(Gravity.CENTER);extra.setMinHeight(dp(44));
        LinearLayout.LayoutParams extraLp=lp(52,44);extraLp.leftMargin=dp(8);actions.addView(extra,extraLp);
        extra.setOnClickListener(v->{
            String[] parts=entry.getText().toString().split("\\R",2);
            Task draft=new Task(0,parts[0],draftDue,draftHasTime,0,false);
            if(parts.length>1)draft.description=parts[1].trim();
            draft.folder=selectedFolder==null?"":selectedFolder;hideKeyboard();
            TaskEditor.show(this,draft,()->{entry.setText("");setDraftDate(null,false);showQuickBar();refresh(true);});
        });
        TextView add=label("Сохранить",15,Color.WHITE,true);add.setContentDescription("Добавить задачу");add.setGravity(Gravity.CENTER);
        touchBackground(add,PURPLE,14);LinearLayout.LayoutParams addLp=new LinearLayout.LayoutParams(0,dp(48),1);
        addLp.leftMargin=dp(8);actions.addView(add,addLp);add.setOnClickListener(v->createTask());
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
        LinearLayout.LayoutParams composerLp=lp(-1,-2);composerLp.setMargins(dp(12),dp(4),dp(12),dp(8));
        root.addView(composer,composerLp);composer.setVisibility(View.GONE);
        quickBar=new LinearLayout(this);quickBar.setGravity(Gravity.CENTER_VERTICAL);
        quickBar.setPadding(dp(12),dp(6),dp(12),dp(6));quickBar.setBackground(bg(CARD,20));
        ImageButton quickMic=new ImageButton(this);quickMic.setImageResource(R.drawable.voice_mic);
        quickMic.setImageTintList(ColorStateList.valueOf(PURPLE));quickMic.setContentDescription("Голосовой ввод по-русски, офлайн");
        touchBackground(quickMic,ThemeSettings.field(this),14);quickBar.addView(quickMic,lp(44,44));
        quickMic.setOnClickListener(v->startVoiceCapture());
        TextView newTask=label("＋  Новая задача",15,Color.WHITE,true);newTask.setGravity(Gravity.CENTER);
        newTask.setContentDescription("Открыть создание задачи");touchBackground(newTask,PURPLE,14);
        LinearLayout.LayoutParams newTaskLp=new LinearLayout.LayoutParams(0,dp(48),1);newTaskLp.leftMargin=dp(8);
        quickBar.addView(newTask,newTaskLp);newTask.setOnClickListener(v->showComposer(true));
        LinearLayout.LayoutParams quickLp=lp(-1,-2);quickLp.setMargins(dp(12),0,dp(12),dp(8));root.addView(quickBar,quickLp);

        permissionCard=column();permissionCard.setPadding(dp(16),dp(14),dp(16),dp(14));
        int warningText=dark?0xFFFFD89C:Color.rgb(123,85,25);
        touchBackground(permissionCard,dark?0xFF3B3020:Color.rgb(255,245,225),ios?22:18);
        permissionCard.addView(label("Проверь напоминания",14,warningText,true),lp(-1,-2));
        gap(permissionCard,6);permissionText=label("",12,warningText,false);
        permissionCard.addView(permissionText,lp(-1,-2));permissionCard.setOnClickListener(v->showReminderSettings());
        page.addView(permissionCard,lp(-1,-2));

        HorizontalScrollView filters=new HorizontalScrollView(this);filters.setHorizontalScrollBarEnabled(false);
        chipRow=new LinearLayout(this);filters.addView(chipRow);page.addView(filters,lp(-1,-2));
        countLabel=label("",12,SUBTLE,false);
        LinearLayout.LayoutParams countLp=lp(-1,-2);countLp.topMargin=dp(20);countLp.bottomMargin=dp(8);page.addView(countLabel,countLp);
        cards=column();cards.setPadding(0,0,0,dp(8));page.addView(cards,lp(-1,-2));
        root.requestFocus();refresh(false);
    }
    private void updatePreview(){
        String raw=entry.getText().toString();TaskDateParser.Result parsed=parsedEntry();
        if(raw.trim().isEmpty()&&draftDue==null){
            preview.setVisibility(View.GONE);
            preview.setText("Дату и время можно написать словами");preview.setTextColor(SUBTLE);return;
        }
        preview.setVisibility(View.VISIBLE);
        if(parsed.dueAt==null){preview.setVisibility(View.GONE);return;}
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
        draftDue=due;draftHasTime=time;dateChip.setText(due==null?"Дата":DateTimePicker.compact(due,false));
        timeChip.setText(due!=null&&time?Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")):"Время");
        updatePreview();
    }
    private void createTask(){
        String raw=entry.getText().toString().trim();
        if(raw.isEmpty()){
            Task draft=new Task(0,"",draftDue,draftHasTime,0,false);draft.folder=selectedFolder==null?"":selectedFolder;
            TaskEditor.show(this,draft,()->{refresh(true);Task created=store.find(store.nextId()-1);if(created!=null&&created.hasTime)ensureReminderAccess();});return;
        }
        TaskDateParser.Result parsed=parsedEntry();
        String[] lines=raw.split("\\R",2);
        Task task=new Task(store.allocateId(),lines.length==1?parsed.title:TaskDateParser.parse(lines[0]).title,parsed.dueAt,parsed.hasTime,parsed.repeat,false);
        if(lines.length>1)task.description=lines[1].trim();
        task.folder=selectedFolder==null?"":selectedFolder;
        store.upsert(task);ReminderScheduler.schedule(this,task);
        setDraftDate(null,false);
        handler.removeCallbacks(previewUpdate);entry.setText("");updatePreview();filter=0;
        showQuickBar();refresh(true);
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
        if(ReminderScheduler.hasExactAccess(this))maybeAskBackground();else maybeAskExact();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(voice!=null)voice.permissionResult(request,results);
        if(request==17){ReminderScheduler.scheduleAll(this);refresh(false);if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)ensureReminderAccess();}
    }
    public void startVoice(EditText target){
        if(voice==null)return;voice.request(text->{
            if(isFinishing()||isDestroyed()||!target.isAttachedToWindow())return;
            int start=Math.max(0,target.getSelectionStart()),end=Math.max(start,target.getSelectionEnd());
            String prefix=start>0&&!Character.isWhitespace(target.getText().charAt(start-1))?" ":"";
            target.getText().replace(start,end,prefix+text);target.setSelection(start+prefix.length()+text.length());target.requestFocus();
        });
    }
    private void startVoiceCapture(){
        if(voice==null)return;
        voice.request(text->{
            if(isFinishing()||isDestroyed())return;
            VoiceTaskReview.show(this,store,selectedFolder,text,()->{
                filter=0;refresh(true);
                if(store.all().stream().anyMatch(t->t.hasTime&&t.dueAt!=null))ensureReminderAccess();
            });
        });
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
                "Фоновая работа на OnePlus", "Проверить уведомление сейчас", "Таймер на 10 секунд", "Диагностика",
                "Надёжный режим: "+(ReminderScheduler.reliableMode(this)?"включён":"выключен")};
        new AlertDialog.Builder(this).setTitle("Настройки напоминаний").setItems(options,(d,which)->{
            if(which==0){
                if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
                        &&shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))ensureReminderAccess();
                else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
            }
            else if(which==1){if(Build.VERSION.SDK_INT>=31)openExactSettings();else Toast.makeText(this,"Точные напоминания доступны",Toast.LENGTH_SHORT).show();}
            else if(which==2)backgroundSetup();
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
            }else if(which==6){
                if(ReminderScheduler.reliableMode(this)){ReminderScheduler.reliableMode(this,false);refresh(false);}
                else reliableSetup();
            }else new AlertDialog.Builder(this).setTitle("Диагностика напоминаний")
                    .setMessage(ReminderScheduler.diagnostics(this)).setPositiveButton("Закрыть",null)
                    .setNeutralButton("Скопировать",(a,b)->{
                        ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(
                                android.content.ClipData.newPlainText("Диагностика",ReminderScheduler.diagnostics(this)));
                    }).show();
        }).setNegativeButton("Закрыть",null).show();
    }
    private void maybeAskBackground(){
        if(!ReminderScheduler.unrestrictedBattery(this)&&!ReminderScheduler.reliableMode(this)
                &&!getPreferences(MODE_PRIVATE).getBoolean("background_help_seen",false)){
            getPreferences(MODE_PRIVATE).edit().putBoolean("background_help_seen",true).apply();backgroundSetup();
        }
    }
    private void backgroundSetup(){
        new AlertDialog.Builder(this).setTitle("Напоминания с выключенным экраном")
                .setMessage("Прошивка может откладывать таймеры в фоне, даже когда уведомления и точное время разрешены. Разреши работу без оптимизации батареи. На OnePlus также проверь «Использование батареи → Разрешить фоновую работу». Если задержки останутся, включи надёжный режим Android. Принудительную остановку приложения обойти нельзя.")
                .setNegativeButton("Позже",null).setNeutralButton("Надёжный режим",(d,w)->reliableSetup())
                .setPositiveButton("Разрешить фон",(d,w)->{
                    try{startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,android.net.Uri.parse("package:"+getPackageName())));}
                    catch(RuntimeException e){openAppSettings();}
                }).show();
    }
    private void reliableSetup(){
        new AlertDialog.Builder(this).setTitle("Надёжный режим Android")
                .setMessage("Напоминания будут ставиться через системный API будильника, а не обычный таймер. Android может показывать значок и время ближайшего будильника. Это не воспроизводит мелодию будильника: приложение по-прежнему показывает уведомление. Режим можно выключить здесь же.")
                .setNegativeButton("Отмена",null).setPositiveButton("Включить",(d,w)->{
                    ReminderScheduler.reliableMode(this,true);refresh(false);if(!ReminderScheduler.hasExactAccess(this))maybeAskExact();
                }).show();
    }
    private void showAppSettings(){
        if(settingsOpen)return;
        hideKeyboard();settingsOpen=true;scroll.setVisibility(View.GONE);composer.setVisibility(View.GONE);
        quickBar.setVisibility(View.GONE);undoBar.setVisibility(View.GONE);
        settingsScreen=column();settingsScreen.setBackgroundColor(BACKGROUND);
        int inset=UiTokens.page(this);settingsScreen.setPadding(inset,dp(8),inset,0);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=label("‹",28,PURPLE,false);back.setGravity(Gravity.CENTER);back.setContentDescription("Назад к задачам");
        touchBackground(back,ThemeSettings.field(this),12);header.addView(back,lp(44,44));back.setOnClickListener(v->closeSettings());
        TextView title=label("Настройки",24,TEXT,true);LinearLayout.LayoutParams titleLp=lp(-2,-2);titleLp.leftMargin=dp(12);
        header.addView(title,titleLp);settingsScreen.addView(header,lp(-1,52));
        ScrollView content=new ScrollView(this);content.setFillViewport(false);content.setVerticalScrollBarEnabled(false);
        LinearLayout list=column();list.setPadding(0,dp(8),0,dp(24));content.addView(list);
        settingsScreen.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        settingsGroup(list,"Оформление");
        settingsRow(list,"Тема","Системная, светлая или тёмная",()->new AlertDialog.Builder(this).setTitle("Тема приложения")
                .setSingleChoiceItems(new String[]{"Как в системе","Светлая","Тёмная"},ThemeSettings.mode(this),(dialog,mode)->{
                    ThemeSettings.setMode(this,mode);TaskWidgetProvider.updateAll(this);dialog.dismiss();recreate();
                }).setNegativeButton("Отмена",null).show());
        if(!iosStyle)settingsRow(list,"Акцентный цвет","Синий, сиреневый или бирюзовый",()->new AlertDialog.Builder(this).setTitle("Акцентный цвет")
                .setSingleChoiceItems(new String[]{"Синий","Сиреневый","Бирюзовый"},ThemeSettings.accentChoice(this),(dialog,choice)->{
                    ThemeSettings.setAccentChoice(this,choice);TaskWidgetProvider.updateAll(this);dialog.dismiss();recreate();
                }).setNegativeButton("Отмена",null).show());
        settingsRow(list,"Стиль интерфейса","Sadachi или iOS‑подобный",()->new AlertDialog.Builder(this).setTitle("Стиль интерфейса")
                .setSingleChoiceItems(new String[]{"Sadachi","iOS‑подобный"},ThemeSettings.ios(this)?1:0,(dialog,choice)->{
                    ThemeSettings.setIos(this,choice==1);TaskWidgetProvider.updateAll(this);dialog.dismiss();recreate();
                }).setNegativeButton("Отмена",null).show());
        settingsRow(list,"Иконка приложения",null,()->new AlertDialog.Builder(this).setTitle("Иконка приложения")
                .setSingleChoiceItems(new String[]{"Светлая","Тёмная"},LauncherIcon.dark(this)?1:0,(dialog,choice)->{
                    LauncherIcon.setDark(this,choice==1);dialog.dismiss();Toast.makeText(this,"Лаунчер может обновить иконку с небольшой задержкой",Toast.LENGTH_LONG).show();
                }).show());
        settingsGroup(list,"Задачи");
        settingsRow(list,"Напоминания",null,()->showReminderSettings());
        settingsRow(list,"Виджеты",null,()->new AlertDialog.Builder(this).setTitle("Виджеты")
                .setMessage("Зажми свободное место на рабочем столе → Виджеты → Sadachi. Выбери «Список» или «Сегодня». Размер можно изменить долгим нажатием на виджет.")
                .setPositiveButton("Понятно",null).show());
        settingsGroup(list,"Голос");
        settingsRow(list,"Голосовой ввод","Русский · офлайн Vosk",()->{
            int silence=VoiceSettings.silenceMillis(this);
            new AlertDialog.Builder(this).setTitle("Окончание голосовой записи")
                    .setSingleChoiceItems(new String[]{"Остановить вручную","После тишины 2,5 секунды","После тишины 4 секунды"},silence==2500?1:silence==4000?2:0,(dialog,choice)->{
                        VoiceSettings.setSilenceMillis(this,choice==1?2500:choice==2?4000:0);dialog.dismiss();
                    }).setNegativeButton("Отмена",null).show();
        });
        settingsGroup(list,"Данные");
        settingsRow(list,"Перенос и резервные копии",null,()->showDataSettings());
        settingsRow(list,"Обновления",null,()->AppUpdates.show(this));
        settingsRow(list,"О приложении",null,()->new AlertDialog.Builder(this).setTitle("Sadachi")
                .setMessage("Задачи и заметки хранятся локально. Голосовой ввод работает офлайн. Версия "+installedVersion()+"; управление обновлениями — в разделе «Обновления».")
                .setPositiveButton("Понятно",null).show());
        root.addView(settingsScreen,new LinearLayout.LayoutParams(-1,0,1));
        if(motion())enter(settingsScreen,0);
    }
    private void settingsGroup(LinearLayout list,String name){
        TextView header=label(name,12,SUBTLE,true);header.setPadding(dp(4),0,0,0);
        LinearLayout.LayoutParams p=lp(-1,24);p.topMargin=dp(20);p.bottomMargin=dp(4);list.addView(header,p);
        settingsGroupRows=column();settingsGroupRows.setBackground(bg(CARD,16));list.addView(settingsGroupRows,lp(-1,-2));
    }
    private void settingsRow(LinearLayout list,String title,String subtitle,Runnable action){
        if(settingsGroupRows.getChildCount()>0){View divider=new View(this);divider.setBackgroundColor(LINE);
            LinearLayout.LayoutParams separator=lp(-1,1);separator.leftMargin=dp(12);separator.rightMargin=dp(12);settingsGroupRows.addView(divider,separator);}
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(dp(12),0,dp(12),0);
        line.setMinimumHeight(dp(UiTokens.SETTING_HEIGHT));
        LinearLayout text=column();text.setGravity(Gravity.CENTER_VERTICAL);
        text.addView(label(title,UiTokens.SETTING,TEXT,false),lp(-1,-2));
        if(subtitle!=null){TextView detail=label(subtitle,12,SUBTLE,false);LinearLayout.LayoutParams sub=lp(-1,-2);sub.topMargin=dp(2);text.addView(detail,sub);}
        line.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=label("›",22,SUBTLE,false);arrow.setGravity(Gravity.CENTER);line.addView(arrow,lp(24,44));
        settingsGroupRows.addView(line,lp(-1,-2));
        line.setContentDescription(title);line.setOnClickListener(v->action.run());
    }
    private void closeSettings(){
        if(!settingsOpen)return;
        settingsOpen=false;root.removeView(settingsScreen);settingsScreen=null;
        scroll.setVisibility(View.VISIBLE);undoBar.setVisibility(View.GONE);
        (composerOpen?composer:quickBar).setVisibility(View.VISIBLE);
        root.requestFocus();
    }
    private String installedVersion(){
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(android.content.pm.PackageManager.NameNotFoundException ignored){return "неизвестна";}
    }
    private void renderChips(){
        chipRow.removeAllViews();
        for(int i=0;i<FILTER_NAMES.length;i++){
            final int index=i;TextView chip=label(FILTER_NAMES[i],UiTokens.FILTER,i==filter?PURPLE:SUBTLE,true);
            chip.setGravity(Gravity.CENTER);chip.setPadding(dp(14),0,dp(14),0);chip.setMinHeight(dp(UiTokens.FILTER_HEIGHT));
            touchBackground(chip,i==filter?ThemeSettings.field(this):BACKGROUND,18);
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
        boolean notifications=notificationsEnabled(),exact=ReminderScheduler.hasExactAccess(this);
        boolean timedTasks=tasks.stream().anyMatch(t->!t.done&&t.hasTime&&t.dueAt!=null);
        boolean background=ReminderScheduler.unrestrictedBattery(this)||ReminderScheduler.reliableMode(this);
        permissionCard.setVisibility(!timedTasks||notifications&&exact&&background?View.GONE:View.VISIBLE);
        permissionText.setText(!notifications?"Уведомления выключены. Нажми, чтобы разрешить напоминания.":
                !exact?"Для точного времени нужно разрешение Android. Нажми, чтобы настроить.":"Фон может ограничиваться прошивкой. Нажми → «Фоновая работа» или «Надёжный режим». ");
        renderChips();cards.removeAllViews();dateLabels.clear();int displayed=0;LocalDate today=LocalDate.now();
        for(Task task:tasks){
            if(filter==3&&!task.done||filter!=3&&task.done)continue;
            LocalDate day=task.dueAt==null?null:Instant.ofEpochMilli(task.dueAt).atZone(ZoneId.systemDefault()).toLocalDate();
            if(filter==1&&(day==null||day.isAfter(today)))continue;
            if(filter==2&&(day==null||!day.isAfter(today)))continue;
            View card=createCard(task);if(animate&&displayed<8)enter(card,displayed*25);displayed++;
        }
        countLabel.setText(filter==3?"Выполнено · "+displayed:filter==1?"Сегодня · "+displayed+" задач":
                filter==2?"Предстоящие · "+displayed+" задач":"Задачи · "+active);
        if(displayed==0){
            LinearLayout empty=column();empty.setPadding(dp(8),dp(20),dp(8),dp(20));
            TextView icon=label(filter==3?"✓":"○",24,PURPLE,false);icon.setGravity(Gravity.CENTER);empty.addView(icon,lp(-1,36));gap(empty,8);
            TextView title=label(filter==3?"Пока нет выполненных":filter==1?"Сегодня задач нет":filter==2?"Пока нет предстоящих":"Пока нет задач",17,TEXT,true);
            title.setGravity(Gravity.CENTER);empty.addView(title,lp(-1,-2));gap(empty,4);
            TextView detail=label(filter==3?"Завершённые задачи появятся здесь":"Добавь первую задачу или продиктуй её",13,SUBTLE,false);
            detail.setGravity(Gravity.CENTER);empty.addView(detail,lp(-1,-2));cards.addView(empty,lp(-1,-2));
            if(animate)enter(empty,0);
        }
    }
    private View createCard(Task task){
        SwipeTaskLayout row=new SwipeTaskLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        boolean ios=iosStyle;row.setPadding(dp(8),dp(6),dp(8),dp(6));row.setMinimumHeight(dp(UiTokens.TASK_MIN));row.setBackground(bg(CARD,16));
        LinearLayout.LayoutParams p=lp(-1,-2);p.bottomMargin=dp(8);cards.addView(row,p);
        CheckBox done=new CheckBox(this);done.setButtonTintList(ColorStateList.valueOf(task.done?GREEN:PURPLE));
        done.setContentDescription((task.done?"Вернуть задачу: ":"Завершить задачу: ")+task.title);
        done.setChecked(task.done);row.addView(done,lp(48,48));
        LinearLayout text=column();text.setPadding(dp(2),dp(2),dp(2),dp(2));
        row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=label(task.title,UiTokens.TASK,task.done?SUBTLE:TEXT,true);
        if(task.done)title.setPaintFlags(title.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        title.setMaxLines(3);title.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(title,lp(-1,-2));
        boolean overdue=!task.done&&task.dueAt!=null&&task.hasTime&&task.dueAt<System.currentTimeMillis();
        TextView date=label(taskDate(task),12,overdue?Color.rgb(177,63,76):task.hasTime?PURPLE:SUBTLE,false);
        dateLabels.put(date,task);
        LinearLayout.LayoutParams dateLp=lp(-1,-2);dateLp.topMargin=dp(4);text.addView(date,dateLp);
        if(task.dueAt==null)date.setVisibility(View.GONE);
        boolean hasDetails=!task.description.trim().isEmpty()||!task.descriptionHtml.isEmpty()||!task.items.isEmpty();
        boolean hideSummary=!hasDetails&&task.folder.isEmpty();
        TextView summary=label((task.folder.isEmpty()?"":task.folder+" · ")+(task.items.isEmpty()?(ios?"Заметка ▾":"Раскрыть заметку ▾"):task.completedItems()+"/"+task.items.size()+" пунктов ▾"),12,SUBTLE,false);
        LinearLayout.LayoutParams summaryLp=lp(-1,-2);summaryLp.topMargin=dp(4);text.addView(summary,summaryLp);
        if(hideSummary&&!expanded.contains(task.id))summary.setVisibility(View.GONE);
        LinearLayout details=column();details.setVisibility(expanded.contains(task.id)?View.VISIBLE:View.GONE);text.addView(details,lp(-1,-2));
        row.setSwipeEnabled(!expanded.contains(task.id));
        if(expanded.contains(task.id))buildDetails(task,details,summary);
        text.setOnClickListener(v->{
            boolean open=details.getVisibility()!=View.VISIBLE;
            if(open&&details.getChildCount()==0)buildDetails(task,details,summary);
            if(motion())android.transition.TransitionManager.beginDelayedTransition(cards,new android.transition.AutoTransition().setDuration(160));
            if(open)expanded.add(task.id);else expanded.remove(task.id);details.setVisibility(open?View.VISIBLE:View.GONE);
            if(hideSummary)summary.setVisibility(open?View.VISIBLE:View.GONE);
            row.setSwipeEnabled(!open);row.setGravity(open?Gravity.TOP:Gravity.CENTER_VERTICAL);
        });
        text.setFocusable(true);text.setContentDescription(task.title+". "+taskDate(task)+". Раскрыть описание и чек-лист");
        TextView more=label("⋮",20,SUBTLE,false);more.setGravity(Gravity.CENTER);
        more.setContentDescription("Действия с задачей: "+task.title);touchBackground(more,CARD,14);
        row.addView(more,lp(40,48));more.setOnClickListener(v->taskOptions(task));
        row.onComplete(()->completeTask(task,!task.done,row,title));
        done.setOnCheckedChangeListener((v,checked)->{done.setEnabled(false);completeTask(task,checked,row,title);});
        return row;
    }
    private void buildDetails(Task task,LinearLayout details,TextView summary){
        gap(details,12);
        EditText description=new EditText(this);description.setTextSize(15);description.setTextColor(TEXT);description.setHintTextColor(SUBTLE);
        description.setHint("Дописать описание…");description.setMinLines(2);description.setMaxLines(8);
        description.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        description.setBackground(bg(BACKGROUND,18));description.setPadding(dp(14),dp(12),dp(14),dp(12));
        description.setText(noteDrafts.containsKey(task.id)?noteDrafts.get(task.id):NoteFormatting.load(task));
        description.setOnFocusChangeListener((v,focused)->inlineEditing=focused);
        Runnable retainDraft=()->noteDrafts.put(task.id,new android.text.SpannableStringBuilder(description.getText()));
        details.addView(NoteFormatting.toolbar(this,description,retainDraft),lp(-1,-2));details.addView(description,lp(-1,-2));
        TextView saveNote=label("Сохранить описание",13,PURPLE,true);saveNote.setMinHeight(dp(48));touchBackground(saveNote,BACKGROUND,16);
        details.addView(saveNote,lp(-1,-2));saveNote.setOnClickListener(v->saveInline(task,description,null));
        TextView detected=label("",12,PURPLE,true);detected.setMinHeight(dp(48));detected.setVisibility(View.GONE);details.addView(detected,lp(-1,-2));
        description.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void afterTextChanged(Editable value){retainDraft.run();}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                TaskDateParser.Result result=TaskDateParser.parse(s.toString());detected.setVisibility(result.dueAt==null?View.GONE:View.VISIBLE);
                if(result.dueAt!=null)detected.setText("Сохранить и назначить: "+DateTimePicker.format(result.dueAt,result.hasTime));
            }
        });
        detected.setOnClickListener(v->saveInline(task,description,TaskDateParser.parse(description.getText().toString())));
        for(Task.Item item:task.items){
            CheckBox bullet=new CheckBox(this);bullet.setText(item.text);bullet.setTextSize(14);bullet.setTextColor(TEXT);bullet.setMinHeight(dp(48));
            bullet.setButtonTintList(ColorStateList.valueOf(PURPLE));bullet.setChecked(item.done);
            bullet.setOnCheckedChangeListener((v,checked)->{
                item.done=checked;Task current=store.find(task.id);int index=task.items.indexOf(item);
                if(current!=null&&index>=0&&index<current.items.size()){current.items.get(index).done=checked;store.upsert(current);summary.setText(current.completedItems()+"/"+current.items.size()+" пунктов ▾");}
                if(motion())bullet.animate().alpha(.65f).setDuration(80).withEndAction(()->bullet.animate().alpha(1).setDuration(140).start()).start();
            });details.addView(bullet,lp(-1,-2));
        }
        TextView edit=label("Редактировать заметку",13,PURPLE,true);edit.setMinHeight(dp(48));touchBackground(edit,BACKGROUND,12);
        details.addView(edit,lp(-1,-2));edit.setOnClickListener(v->editTask(task));
    }
    private void saveInline(Task original,EditText editor,TaskDateParser.Result timing){
        Task task=store.find(original.id);if(task==null)return;
        NoteFormatting.save(task,editor.getText());
        if(timing!=null&&timing.dueAt!=null){ReminderScheduler.cancel(this,task.id);task.dueAt=timing.dueAt;task.hasTime=timing.hasTime;task.repeat=timing.repeat;}
        store.upsert(task);if(timing!=null)ReminderScheduler.schedule(this,task);
        noteDrafts.remove(task.id);inlineEditing=false;
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(editor.getWindowToken(),0);
        root.requestFocus();refresh(false);
        Toast.makeText(this,"Описание сохранено",Toast.LENGTH_SHORT).show();if(timing!=null&&task.hasTime)ensureReminderAccess();
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
        Task initial=store.find(task.id);if(initial==null)return;
        if(noteDrafts.containsKey(task.id))NoteFormatting.save(initial,noteDrafts.get(task.id));
        TaskEditor.show(this,initial,()->{
            noteDrafts.remove(task.id);inlineEditing=false;expanded.add(task.id);refresh(true);
            Task updated=store.find(task.id);if(updated!=null&&updated.hasTime&&!updated.done)ensureReminderAccess();
        });
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
    private void showDataSettings(){
        new AlertDialog.Builder(this).setTitle("Данные хранятся на телефоне")
                .setItems(new String[]{"Перенести из старого приложения","Сохранить JSON-копию","Импортировать JSON-копию"},(dialog,which)->{
                    if(which==0){
                        if(!"com.ilkinsadachi".equals(getPackageName())){
                            new AlertDialog.Builder(this).setMessage("Это версия для переноса. Установи APK com.ilkinsadachi, затем выбери перенос уже в новом приложении. Старые заметки останутся здесь.").setPositiveButton("Понятно",null).show();return;
                        }
                        if(LocalBackup.migrated(this)){Toast.makeText(this,"Заметки из старого приложения уже перенесены",Toast.LENGTH_LONG).show();return;}
                        new AlertDialog.Builder(this).setTitle("Перенести заметки?")
                                .setMessage("Нужна версия старого приложения 0.6.0 для переноса с тем же ключом подписи. Заметки и папки будут скопированы локально, старые данные не удалятся. Старые напоминания отключатся после успешного копирования, чтобы не было дублей.")
                                .setNegativeButton("Отмена",null).setPositiveButton("Перенести",(d,w)->io.execute(()->{
                                    try{int count=LocalBackup.fromLegacy(this);runOnUiThread(()->{
                                        if(isDestroyed())return;refresh(true);boolean paused=getSharedPreferences("migration",MODE_PRIVATE).getBoolean("legacy_paused",false);
                                        Toast.makeText(this,"Перенесено заметок: "+count+(paused?"":". Проверь отключение старых напоминаний"),Toast.LENGTH_LONG).show();
                                        if(store.all().stream().anyMatch(task->!task.done&&task.hasTime))ensureReminderAccess();
                                    });}
                                    catch(Exception error){runOnUiThread(()->{if(!isDestroyed())new AlertDialog.Builder(this).setTitle("Перенос недоступен")
                                            .setMessage("Установи поверх старой версии APK для переноса, не удаляя её. Либо сохрани там JSON-копию и импортируй её здесь. Подписи приложений должны совпадать.").setPositiveButton("Понятно",null).show();});}
                                })).show();
                    }else if(which==1)startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"sadachi-backup.json"),501);
                    else startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE),502);
                }).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null||(request!=501&&request!=502))return;
        android.net.Uri uri=data.getData();
        if(request==502){
            new AlertDialog.Builder(this).setTitle("Импортировать копию?").setMessage("Заметки из файла будут добавлены к существующим. Повторный импорт создаст дубли.")
                    .setNegativeButton("Отмена",null).setPositiveButton("Импортировать",(d,w)->backupOperation(uri,false)).show();
        }else backupOperation(uri,true);
    }
    private void backupOperation(android.net.Uri uri,boolean export){
        io.execute(()->{
            String message;
            try{
                if(export){
                    try(java.io.OutputStream output=getContentResolver().openOutputStream(uri,"wt")){
                        if(output==null)throw new java.io.IOException("No file");output.write(LocalBackup.snapshot(this).toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }message="Локальная копия сохранена";
                }else message="Добавлено заметок: "+LocalBackup.restore(this,LocalBackup.read(getContentResolver().openInputStream(uri)));
            }catch(Exception e){message="Не удалось прочитать или сохранить копию. Проверь файл и свободное место.";}
            String text=message;runOnUiThread(()->{if(!isDestroyed()){
                refresh(false);Toast.makeText(this,text,Toast.LENGTH_LONG).show();
                if(!export&&store.all().stream().anyMatch(task->!task.done&&task.hasTime))ensureReminderAccess();
            }});
        });
    }
    private void folderName(String old){
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Например: Учёба");if(old!=null)name.setText(old);
        name.setTextColor(TEXT);name.setHintTextColor(SUBTLE);name.setPadding(dp(16),dp(12),dp(16),dp(12));name.setMinHeight(dp(48));
        name.setBackground(border(CARD,18));
        LinearLayout content=column();content.setPadding(dp(24),dp(8),dp(24),0);content.addView(name,lp(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(old==null?"Новая папка":"Название папки").setView(content)
                .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=name.getText().toString().trim();
            boolean success=old==null?store.addFolder(value):store.renameFolder(old,value);
            if(!success){name.setError("Введи непустое уникальное название");return;}
            selectedFolder=value;filter=0;refresh(true);dialog.dismiss();
        }));dialog.show();RoundedUi.dialog(dialog);RoundedUi.actions(dialog);
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

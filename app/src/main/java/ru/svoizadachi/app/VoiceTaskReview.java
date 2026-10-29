package ru.svoizadachi.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** No transcription is saved as a task until the user confirms these candidates. */
public final class VoiceTaskReview {
    private final Activity activity;
    private final TaskStore store;
    private final String folder;
    private final Runnable saved;
    private final List<Row> rows=new ArrayList<>();
    private EditText transcript;
    private LinearLayout candidates;
    private AlertDialog dialog;
    private boolean changed,saving;

    private VoiceTaskReview(Activity activity,TaskStore store,String folder,Runnable saved){
        this.activity=activity;this.store=store;this.folder=folder==null?"":folder;this.saved=saved;
    }
    public static void show(Activity activity,TaskStore store,String folder,String text,Runnable saved){
        if(text==null||text.trim().isEmpty())return;
        VoiceTaskReview review=new VoiceTaskReview(activity,store,folder,saved);review.open(text);
    }
    private int dp(int size){return Math.round(size*activity.getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size){
        TextView view=new TextView(activity);view.setText(value);UiTokens.text(view,size,false);
        view.setTextColor(ThemeSettings.text(activity));return view;
    }
    private void open(String text){
        ScrollView scroll=new ScrollView(activity);
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(8),dp(16),dp(16));scroll.addView(body);
        TextView heading=label("Исходный текст · его можно исправить",13);heading.setTextColor(ThemeSettings.subtle(activity));body.addView(heading);
        transcript=new EditText(activity);transcript.setText(text);UiTokens.text(transcript,UiTokens.TASK,false);transcript.setTextColor(ThemeSettings.text(activity));
        transcript.setMinLines(2);transcript.setMaxLines(4);transcript.setContentDescription("Распознанный текст");body.addView(transcript);
        TextView reparse=label("Разобрать текст заново",14);reparse.setTextColor(ThemeSettings.accent(activity));
        reparse.setGravity(Gravity.CENTER_VERTICAL);reparse.setMinHeight(dp(48));body.addView(reparse);
        candidates=new LinearLayout(activity);candidates.setOrientation(LinearLayout.VERTICAL);body.addView(candidates);
        reparse.setOnClickListener(v->{render();reparse.setText("Разобрать текст заново");});
        dialog=new AlertDialog.Builder(activity).setTitle("Проверь задачи перед добавлением").setView(scroll)
                .setNegativeButton("Отмена",null).setPositiveButton("Добавить",null).create();
        dialog.show();if(activity.getResources().getConfiguration().screenWidthDp<600)RoundedUi.sheet(dialog);
        else RoundedUi.dialog(dialog);
        RoundedUi.actions(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(ignored->save());
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                |android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        render();
        transcript.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){changed=true;reparse.setText("Текст изменён · разобрать заново");}
            @Override public void afterTextChanged(Editable text){}
        });
    }
    private void render(){
        changed=false;rows.clear();candidates.removeAllViews();
        for(VoiceTaskParser.Candidate candidate:VoiceTaskParser.parse(transcript.getText().toString(),ZonedDateTime.now())){
            LinearLayout panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(12),dp(6),dp(12),dp(6));
            LinearLayout.LayoutParams panelParams=new LinearLayout.LayoutParams(-1,-2);panelParams.bottomMargin=dp(8);
            android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(ThemeSettings.field(activity));shape.setCornerRadius(dp(14));panel.setBackground(shape);
            candidates.addView(panel,panelParams);
            EditText title=new EditText(activity);title.setSingleLine(false);title.setMaxLines(3);title.setText(candidate.title);
            UiTokens.text(title,UiTokens.TASK,true);title.setTextColor(ThemeSettings.text(activity));title.setHint("Название задачи");
            title.setContentDescription("Название распознанной задачи: "+candidate.sourceText);panel.addView(title);
            Row row=new Row(title,candidate.dueAt,candidate.hasTime,candidate.repeat,candidate.needsReview,folder);rows.add(row);
            if(candidate.needsReview){TextView warning=label("Неоднозначно: проверь смысл, дату и повтор. Срок не назначен.",12);
                warning.setTextColor(ThemeSettings.accent(activity));panel.addView(warning);
                CheckBox confirm=new CheckBox(activity);confirm.setText("Я проверил(а) эту задачу");confirm.setTextSize(13);
                confirm.setTextColor(ThemeSettings.text(activity));confirm.setMinHeight(dp(48));panel.addView(confirm);
                confirm.setOnCheckedChangeListener((button,checked)->row.confirmed=checked);
            }
            LinearLayout actions=new LinearLayout(activity);actions.setGravity(Gravity.CENTER_VERTICAL);panel.addView(actions);
            TextView date=label(DateTimePicker.format(row.due,row.hasTime),13);date.setTextColor(ThemeSettings.accent(activity));
            date.setGravity(Gravity.CENTER_VERTICAL);date.setMinHeight(dp(40));date.setMaxLines(2);
            date.setContentDescription("Изменить дату задачи: "+candidate.sourceText);
            actions.addView(date,new LinearLayout.LayoutParams(0,-2,1));
            date.setOnClickListener(v->DateTimePicker.show(activity,row.due,(due,hasTime)->{
                row.due=due;row.hasTime=hasTime;date.setText(DateTimePicker.format(due,hasTime));
            }));
            LinearLayout metadata=new LinearLayout(activity);metadata.setGravity(Gravity.CENTER_VERTICAL);panel.addView(metadata);
            TextView repeat=label(repeatLabel(row.repeat),13);repeat.setTextColor(ThemeSettings.accent(activity));repeat.setMinHeight(dp(40));
            repeat.setContentDescription("Повтор задачи: "+candidate.sourceText);
            repeat.setGravity(Gravity.CENTER_VERTICAL);repeat.setSingleLine(true);
            repeat.setEllipsize(android.text.TextUtils.TruncateAt.END);
            metadata.addView(repeat,new LinearLayout.LayoutParams(0,dp(40),1));
            repeat.setOnClickListener(v->new AlertDialog.Builder(activity).setTitle("Повтор")
                    .setSingleChoiceItems(new String[]{"Не повторять","Каждый день","Каждую неделю"},row.repeat,(popup,choice)->{
                        row.repeat=choice;repeat.setText(repeatLabel(choice));popup.dismiss();
                    }).setNegativeButton("Отмена",null).show());
            TextView list=label("Список: "+(row.folder.isEmpty()?"Входящие":row.folder),13);
            list.setTextColor(ThemeSettings.accent(activity));list.setMinHeight(dp(40));
            list.setGravity(Gravity.CENTER_VERTICAL);list.setContentDescription("Выбрать список задачи: "+candidate.sourceText);
            list.setSingleLine(true);list.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams listParams=new LinearLayout.LayoutParams(0,dp(40),1);listParams.leftMargin=dp(8);
            metadata.addView(list,listParams);
            list.setOnClickListener(v->{
                List<String> names=new ArrayList<>();names.add("Входящие");names.addAll(store.folders());
                int selected=row.folder.isEmpty()?0:Math.max(0,names.indexOf(row.folder));
                new AlertDialog.Builder(activity).setTitle("Список задачи").setSingleChoiceItems(names.toArray(new String[0]),selected,(popup,choice)->{
                    row.folder=choice==0?"":names.get(choice);list.setText("Список: "+names.get(choice));popup.dismiss();
                }).setNegativeButton("Отмена",null).show();
            });
            TextView remove=label("Убрать",13);remove.setTextColor(ThemeSettings.subtle(activity));remove.setGravity(Gravity.CENTER);
            remove.setMinWidth(dp(64));remove.setMinHeight(dp(48));remove.setContentDescription("Убрать задачу: "+candidate.sourceText);actions.addView(remove);
            remove.setOnClickListener(v->{rows.remove(row);candidates.removeView(panel);updateButton();});
        }
        updateButton();
    }
    private static String repeatLabel(int repeat){return "Повтор: "+(repeat==1?"каждый день":repeat==2?"каждую неделю":"нет");}
    private void updateButton(){if(dialog!=null)dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Добавить задач: " + rows.size());}
    private void save(){
        if(saving)return;
        if(changed){Toast.makeText(activity,"Сначала нажми «Разобрать текст заново»",Toast.LENGTH_LONG).show();return;}
        if(rows.isEmpty()){Toast.makeText(activity,"Нет задач для добавления",Toast.LENGTH_SHORT).show();return;}
        List<Task> tasks=new ArrayList<>();
        for(Row row:rows){String title=row.title.getText().toString().trim();
            if(title.isEmpty()){row.title.setError("Нужно название");row.title.requestFocus();return;}
            if(row.needsReview&&!row.confirmed){Toast.makeText(activity,"Подтверди неоднозначную задачу или убери её",Toast.LENGTH_LONG).show();return;}
            if(row.repeat!=0&&row.due==null){Toast.makeText(activity,"Для повтора сначала выбери дату",Toast.LENGTH_LONG).show();return;}
            Task task=new Task(0,title,row.due,row.hasTime,row.repeat,false);task.folder=row.folder;tasks.add(task);
        }
        saving=true;dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        if(!store.addBatch(tasks)){
            saving=false;dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            Toast.makeText(activity,"Не удалось сохранить. Текст и правки остались здесь",Toast.LENGTH_LONG).show();return;
        }
        for(Task task:tasks)ReminderScheduler.schedule(activity,task);
        dialog.dismiss();saved.run();
    }
    private static final class Row {
        final EditText title;final boolean needsReview;Long due;boolean hasTime,confirmed;int repeat;String folder;
        Row(EditText title,Long due,boolean hasTime,int repeat,boolean needsReview,String folder){
            this.title=title;this.due=due;this.hasTime=hasTime;this.repeat=repeat;this.needsReview=needsReview;this.folder=folder;
        }
    }
}

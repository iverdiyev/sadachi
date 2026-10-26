package ru.svoizadachi.app;

import android.app.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Title (H1), description (H2/body) and compact metadata, with an isolated draft. */
public final class TaskEditor {
    private final Activity activity;
    private final Task task;
    private final Runnable saved;
    private final List<ItemRow> rows=new ArrayList<>();
    private LinearLayout checklist;
    private Long due;
    private boolean hasTime,dateChanged;
    private TextView dateButton;
    private EditText title,description;
    private Spinner folder,repeat;
    private List<String> folders;
    private TaskEditor(Activity activity,Task task,Runnable saved){this.activity=activity;this.task=task;this.saved=saved;due=task.dueAt;hasTime=task.hasTime;}
    public static void show(Activity activity,Task task,Runnable saved){new TaskEditor(activity,task,saved).show();}
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private TextView caption(String text){TextView view=new TextView(activity);view.setText(text);view.setTextSize(12);view.setTextColor(ThemeSettings.subtle(activity));view.setPadding(0,dp(12),0,dp(6));return view;}
    private Button button(String text){
        Button button=new Button(activity);button.setText(text);button.setTextSize(13);button.setAllCaps(false);button.setMinHeight(dp(40));button.setMinimumHeight(dp(40));
        android.graphics.drawable.GradientDrawable shape=RoundedUi.background(activity,12);shape.setColor(ThemeSettings.dark(activity)?0xFF2A354B:0xFFF0F3FC);
        button.setBackgroundTintList(null);button.setBackground(shape);button.setTextColor(ThemeSettings.dark(activity)?0xFFB6C8FF:0xFF375DBB);
        button.setPadding(dp(8),dp(6),dp(8),dp(6));return button;
    }
    private EditText input(String hint,String value,int size){
        EditText edit=new EditText(activity);edit.setText(value);edit.setHint(hint);edit.setTextSize(size);edit.setTextColor(ThemeSettings.text(activity));edit.setHintTextColor(ThemeSettings.subtle(activity));
        edit.setIncludeFontPadding(true);edit.setGravity(Gravity.TOP|Gravity.START);edit.setPadding(0,dp(8),0,dp(8));edit.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        return edit;
    }
    private void show(){
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);LinearLayout form=column();form.setPadding(dp(20),dp(8),dp(20),dp(16));scroll.addView(form);
        LinearLayout metadata=new LinearLayout(activity);metadata.setGravity(Gravity.CENTER_VERTICAL);form.addView(metadata);
        dateButton=button(DateTimePicker.compact(due,hasTime));dateButton.setTextSize(12);dateButton.setMaxLines(2);
        metadata.addView(dateButton,new LinearLayout.LayoutParams(0,-2,1));
        dateButton.setOnClickListener(v->DateTimePicker.show(activity,due,(value,time)->setDate(value,time)));
        folders=new ArrayList<>();folders.add("Без папки");folders.addAll(new TaskStore(activity).folders());
        folder=new Spinner(activity);folder.setMinimumHeight(dp(48));folder.setContentDescription("Папка заметки");
        folder.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,folders){
            @Override public View getView(int position,View convert,ViewGroup parent){TextView view=(TextView)super.getView(position,convert,parent);view.setTextSize(12);view.setSingleLine(true);view.setEllipsize(android.text.TextUtils.TruncateAt.END);return view;}
        });folder.setSelection(task.folder.isEmpty()?0:Math.max(0,folders.subList(1,folders.size()).indexOf(task.folder)+1));metadata.addView(folder,new LinearLayout.LayoutParams(0,-2,1));
        title=input("Название задачи",task.title,24);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));title.setMinLines(1);title.setMaxLines(4);title.setContentDescription("Заголовок H1");form.addView(title);
        description=input("Подробности, ссылки, мысли…","",16);description.setText(NoteFormatting.load(task));description.setMinLines(4);description.setMaxLines(12);description.setContentDescription("Описание заметки");form.addView(description);
        HorizontalScrollView formatting=NoteFormatting.toolbar(activity,description);formatting.setVisibility(View.GONE);form.addView(formatting);
        LinearLayout tools=new LinearLayout(activity);tools.setGravity(Gravity.CENTER_VERTICAL);form.addView(tools);
        Button styles=button("Aa · Стиль");tools.addView(styles,new LinearLayout.LayoutParams(0,dp(48),1));styles.setOnClickListener(v->formatting.setVisibility(formatting.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        ImageButton microphone=new ImageButton(activity);microphone.setImageResource(R.drawable.voice_mic);microphone.setContentDescription("Голосовой ввод по-русски, офлайн");
        microphone.setBackground(RoundedUi.background(activity,12));tools.addView(microphone,new LinearLayout.LayoutParams(dp(48),dp(48)));
        microphone.setOnClickListener(v->{if(activity instanceof MainActivity)((MainActivity)activity).startVoice(description.hasFocus()?description:title);});
        Button detected=button("Найти дату в тексте");detected.setVisibility(View.GONE);form.addView(detected);
        Runnable detect=()->{
            TaskDateParser.Result parsed=TaskDateParser.parse(title.getText()+"\n"+description.getText());detected.setVisibility(parsed.dueAt==null?View.GONE:View.VISIBLE);
            if(parsed.dueAt!=null)detected.setText("Использовать: "+DateTimePicker.format(parsed.dueAt,parsed.hasTime));
        };
        android.text.TextWatcher watcher=new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){detect.run();}
            @Override public void afterTextChanged(android.text.Editable value){}
        };title.addTextChangedListener(watcher);description.addTextChangedListener(watcher);detect.run();
        detected.setOnClickListener(v->{TaskDateParser.Result parsed=TaskDateParser.parse(title.getText()+"\n"+description.getText());if(parsed.dueAt!=null)setDate(parsed.dueAt,parsed.hasTime);});
        checklist=column();form.addView(checklist);for(Task.Item item:task.items)addItem(item.text,item.done);
        Button add=button("＋ Добавить пункт");form.addView(add);add.setOnClickListener(v->{addItem("",false);rows.get(rows.size()-1).text.requestFocus();});
        Button options=button("Дата, повтор и параметры");form.addView(options);LinearLayout extra=column();extra.setVisibility(View.GONE);form.addView(extra);
        options.setOnClickListener(v->extra.setVisibility(extra.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        Button clear=button("Без даты и напоминания");extra.addView(clear);clear.setOnClickListener(v->setDate(null,false));
        extra.addView(caption("Повтор"));repeat=new Spinner(activity);repeat.setMinimumHeight(dp(48));
        repeat.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,new String[]{"Не повторять","Каждый день","Каждую неделю"}));repeat.setSelection(Math.max(0,Math.min(2,task.repeat)));extra.addView(repeat);
        AlertDialog.Builder builder=new AlertDialog.Builder(activity).setTitle(task.id==0?"Новая задача":"Задача").setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null);
        if(task.id>0)builder.setNeutralButton(task.done?"Вернуть":"Завершить",null);
        AlertDialog dialog=builder.create();dialog.setOnShowListener(ignored->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->commit(dialog,false));
            if(task.id>0)dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->commit(dialog,true));
        });dialog.show();RoundedUi.dialog(dialog);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private void setDate(Long value,boolean time){due=value;hasTime=time;dateChanged=true;dateButton.setText(DateTimePicker.compact(due,hasTime));}
    private void commit(AlertDialog dialog,boolean toggleDone){
        String raw=title.getText().toString().trim();if(raw.isEmpty()){title.setError("Нужно название");title.requestFocus();return;}
        TaskDateParser.Result parsed=TaskDateParser.parse(raw);TaskStore store=new TaskStore(activity);
        Task current=task.id==0?new Task(store.allocateId(),parsed.title,task.dueAt,task.hasTime,task.repeat,false):store.find(task.id);
        if(current==null){dialog.dismiss();return;}
        current.title=parsed.title;NoteFormatting.save(current,description.getText());current.folder=folder.getSelectedItemPosition()==0?"":folders.get(folder.getSelectedItemPosition());
        if(dateChanged){current.dueAt=due;current.hasTime=hasTime;}
        else if(parsed.dueAt!=null){current.dueAt=parsed.dueAt;current.hasTime=parsed.hasTime;}
        else if(task.id==0&&task.dueAt==null){TaskDateParser.Result body=TaskDateParser.parse(description.getText().toString());current.dueAt=body.dueAt;current.hasTime=body.hasTime;parsed=body;}
        current.repeat=current.dueAt==null?0:repeat.getSelectedItemPosition();if(!dateChanged&&parsed.repeat!=0)current.repeat=parsed.repeat;
        if(toggleDone)current.done=!current.done;
        current.items.clear();for(ItemRow row:rows){String text=row.text.getText().toString().trim();if(!text.isEmpty())current.items.add(new Task.Item(text,row.done.isChecked()));}
        boolean timing=task.id==0||toggleDone||!Objects.equals(task.dueAt,current.dueAt)||task.hasTime!=current.hasTime||task.repeat!=current.repeat;
        if(timing)ReminderScheduler.cancel(activity,current.id);store.upsert(current);if(timing)ReminderScheduler.schedule(activity,current);
        dialog.dismiss();saved.run();
    }
    private void addItem(String text,boolean done){
        LinearLayout line=new LinearLayout(activity);line.setGravity(Gravity.TOP);
        CheckBox check=new CheckBox(activity);check.setChecked(done);check.setContentDescription("Пункт выполнен");line.addView(check,new LinearLayout.LayoutParams(dp(40),dp(48)));
        EditText edit=input("Пункт списка",text,16);edit.setMinLines(1);line.addView(edit,new LinearLayout.LayoutParams(0,-2,1));
        TextView remove=new TextView(activity);remove.setText("×");remove.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,24);remove.setGravity(Gravity.CENTER);remove.setContentDescription("Удалить пункт");line.addView(remove,new LinearLayout.LayoutParams(dp(40),dp(48)));
        ItemRow row=new ItemRow(check,edit);rows.add(row);checklist.addView(line);remove.setOnClickListener(v->{rows.remove(row);checklist.removeView(line);});
    }
    private static final class ItemRow {final CheckBox done;final EditText text;ItemRow(CheckBox done,EditText text){this.done=done;this.text=text;}}
}

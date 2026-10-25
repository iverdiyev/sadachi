package ru.svoizadachi.app;

import android.app.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Edits a draft, so Cancel never changes the persisted task or its checklist. */
public final class TaskEditor {
    private final Activity activity;
    private final Task task;
    private final Runnable saved;
    private final List<ItemRow> rows=new ArrayList<>();
    private LinearLayout checklist;
    private Long due;
    private boolean hasTime,dateChanged;
    private TextView dateButton;
    private TaskEditor(Activity activity,Task task,Runnable saved){this.activity=activity;this.task=task;this.saved=saved;due=task.dueAt;hasTime=task.hasTime;}
    public static void show(Activity activity,Task task,Runnable saved){new TaskEditor(activity,task,saved).show();}
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private void heading(LinearLayout parent,String text){
        TextView view=new TextView(activity);view.setText(text);view.setTextSize(13);view.setTextColor(0xFF696E85);
        view.setPadding(0,dp(16),0,dp(6));parent.addView(view);
    }
    private Button button(String text){Button button=new Button(activity);button.setText(text);button.setAllCaps(false);button.setMinHeight(dp(48));return button;}
    private EditText input(String hint,String value){
        EditText edit=new EditText(activity);edit.setText(value);edit.setHint(hint);edit.setTextSize(16);edit.setTextColor(0xFF212339);
        edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        edit.setMaxLines(8);return edit;
    }
    private void show(){
        ScrollView scroll=new ScrollView(activity);LinearLayout form=column();form.setPadding(dp(20),dp(8),dp(20),dp(16));scroll.addView(form);
        heading(form,"Название");EditText title=input("Название заметки или задачи",task.title);form.addView(title);
        heading(form,"Описание");EditText description=input("Подробности, ссылки, мысли…",task.description);description.setMinLines(3);form.addView(description);
        heading(form,"Папка");
        List<String> folders=new ArrayList<>();folders.add("Без папки");folders.addAll(new TaskStore(activity).folders());
        Spinner folder=new Spinner(activity);folder.setMinimumHeight(dp(48));folder.setContentDescription("Папка заметки");
        folder.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,folders));
        folder.setSelection(task.folder.isEmpty()?0:Math.max(0,folders.subList(1,folders.size()).indexOf(task.folder)+1));form.addView(folder);
        heading(form,"Дата и напоминание");dateButton=button(DateTimePicker.format(due,hasTime));form.addView(dateButton);
        dateButton.setOnClickListener(v->DateTimePicker.show(activity,due,(value,time)->{
            due=value;hasTime=time;dateChanged=true;dateButton.setText(DateTimePicker.format(due,hasTime));
        }));
        Button clear=button("Без даты и напоминания");form.addView(clear);
        clear.setOnClickListener(v->{due=null;hasTime=false;dateChanged=true;dateButton.setText("Выбрать дату");});
        heading(form,"Повтор");Spinner repeat=new Spinner(activity);repeat.setMinimumHeight(dp(48));
        repeat.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,new String[]{"Не повторять","Каждый день","Каждую неделю"}));
        repeat.setSelection(Math.max(0,Math.min(2,task.repeat)));form.addView(repeat);
        heading(form,"Чек-лист");checklist=column();form.addView(checklist);
        for(Task.Item item:task.items)addItem(item.text,item.done);
        Button add=button("＋ Добавить пункт");form.addView(add);add.setOnClickListener(v->{addItem("",false);rows.get(rows.size()-1).text.requestFocus();});
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Заметка")
                .setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String raw=title.getText().toString().trim();if(raw.isEmpty()){title.setError("Нужно название");title.requestFocus();return;}
            TaskDateParser.Result parsed=TaskDateParser.parse(raw);
            Task current=new TaskStore(activity).find(task.id);if(current==null){dialog.dismiss();return;}
            current.title=parsed.title;current.description=description.getText().toString().trim();
            current.folder=folder.getSelectedItemPosition()==0?"":folders.get(folder.getSelectedItemPosition());
            if(dateChanged){current.dueAt=due;current.hasTime=hasTime;}
            else if(parsed.dueAt!=null){current.dueAt=parsed.dueAt;current.hasTime=parsed.hasTime;}
            current.repeat=current.dueAt==null?0:repeat.getSelectedItemPosition();
            if(!dateChanged&&parsed.repeat!=0)current.repeat=parsed.repeat;
            current.items.clear();for(ItemRow row:rows){String text=row.text.getText().toString().trim();if(!text.isEmpty())current.items.add(new Task.Item(text,row.done.isChecked()));}
            // Description-only edits must not re-arm an already delivered reminder.
            boolean timingChanged=!Objects.equals(task.dueAt,current.dueAt)||task.hasTime!=current.hasTime||task.repeat!=current.repeat;
            if(timingChanged)ReminderScheduler.cancel(activity,current.id);
            new TaskStore(activity).upsert(current);if(timingChanged)ReminderScheduler.schedule(activity,current);
            dialog.dismiss();saved.run();
        }));dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private void addItem(String text,boolean done){
        LinearLayout line=new LinearLayout(activity);line.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox check=new CheckBox(activity);check.setChecked(done);check.setContentDescription("Пункт выполнен");
        line.addView(check,new LinearLayout.LayoutParams(dp(48),dp(48)));
        EditText edit=input("Пункт списка",text);line.addView(edit,new LinearLayout.LayoutParams(0,-2,1));
        TextView remove=new TextView(activity);remove.setText("×");remove.setTextSize(24);remove.setGravity(Gravity.CENTER);remove.setContentDescription("Удалить пункт");
        line.addView(remove,new LinearLayout.LayoutParams(dp(48),dp(48)));
        ItemRow row=new ItemRow(check,edit);rows.add(row);checklist.addView(line);
        remove.setOnClickListener(v->{rows.remove(row);checklist.removeView(line);});
    }
    private static final class ItemRow {
        final CheckBox done;final EditText text;
        ItemRow(CheckBox done,EditText text){this.done=done;this.text=text;}
    }
}

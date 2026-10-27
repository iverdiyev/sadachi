package ru.svoizadachi.app;

import android.app.*;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
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
    private TextView folderButton,repeatButton;
    private int selectedFolder,selectedRepeat;
    private List<String> folders;
    private TaskEditor(Activity activity,Task task,Runnable saved){this.activity=activity;this.task=task;this.saved=saved;due=task.dueAt;hasTime=task.hasTime;}
    public static void show(Activity activity,Task task,Runnable saved){new TaskEditor(activity,task,saved).show();}
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private int surface(){return ThemeSettings.field(activity);}
    private GradientDrawable shape(int color,int radius){GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(radius));return drawable;}
    private TextView button(String text){
        TextView view=new TextView(activity);view.setText(text);view.setTextSize(14);view.setTextColor(ThemeSettings.ios(activity)?ThemeSettings.accent(activity):
                (ThemeSettings.dark(activity)?0xFFB6C8FF:0xFF375DBB));
        view.setGravity(Gravity.CENTER_VERTICAL);view.setMinHeight(dp(48));view.setPadding(dp(14),dp(10),dp(14),dp(10));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x224970DF),shape(surface(),16),shape(0xFFFFFFFF,16)));
        view.setClickable(true);view.setFocusable(true);return view;
    }
    private LinearLayout.LayoutParams block(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);return p;}
    static boolean stackMetadata(int widthDp,float fontScale){return widthDp<400||fontScale>1.25f;}
    private EditText input(String hint,String value,int size){
        EditText edit=new EditText(activity);edit.setText(value);edit.setHint(hint);edit.setTextSize(size);edit.setTextColor(ThemeSettings.text(activity));edit.setHintTextColor(ThemeSettings.subtle(activity));
        edit.setIncludeFontPadding(true);edit.setGravity(Gravity.TOP|Gravity.START);edit.setPadding(dp(16),dp(14),dp(16),dp(14));
        GradientDrawable background=shape(ThemeSettings.card(activity),ThemeSettings.ios(activity)?20:18);
        background.setStroke(dp(1),ThemeSettings.border(activity));edit.setBackground(background);
        edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        return edit;
    }
    private void show(){
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout form=column();form.setPadding(dp(16),dp(8),dp(16),dp(16));scroll.addView(form);
        int widthDp=activity.getResources().getConfiguration().screenWidthDp;
        boolean stacked=stackMetadata(widthDp,activity.getResources().getConfiguration().fontScale);
        LinearLayout metadata=new LinearLayout(activity);metadata.setOrientation(stacked?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        form.addView(metadata,block());
        dateButton=button("◷  "+DateTimePicker.compact(due,hasTime));dateButton.setMaxLines(2);dateButton.setContentDescription("Дата и время задачи");
        LinearLayout.LayoutParams dateLp=stacked?block():new LinearLayout.LayoutParams(0,-2,1);
        metadata.addView(dateButton,dateLp);
        dateButton.setOnClickListener(v->DateTimePicker.show(activity,due,(value,time)->setDate(value,time)));
        folders=new ArrayList<>();folders.add("Без папки");folders.addAll(new TaskStore(activity).folders());
        selectedFolder=task.folder.isEmpty()?0:Math.max(0,folders.indexOf(task.folder));
        folderButton=button("▤  "+folders.get(selectedFolder)+"  ▾");folderButton.setMaxLines(2);
        folderButton.setEllipsize(android.text.TextUtils.TruncateAt.END);folderButton.setContentDescription("Выбрать папку");
        LinearLayout.LayoutParams folderLp=stacked?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1);
        if(!stacked)folderLp.leftMargin=dp(8);metadata.addView(folderButton,folderLp);
        folderButton.setOnClickListener(v->new AlertDialog.Builder(activity).setTitle("Папка").setSingleChoiceItems(folders.toArray(new String[0]),selectedFolder,(dialog,choice)->{
            selectedFolder=choice;folderButton.setText("▤  "+folders.get(choice)+"  ▾");dialog.dismiss();
        }).setNegativeButton("Отмена",null).show());
        title=input("Название задачи",task.title,ThemeSettings.ios(activity)?26:24);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));title.setMinLines(1);title.setMaxLines(4);title.setContentDescription("Заголовок H1");form.addView(title,block());
        description=input("Подробности, ссылки, мысли…","",16);description.setText(NoteFormatting.load(task));description.setMinLines(3);description.setMaxLines(12);description.setContentDescription("Описание заметки");form.addView(description,block());
        LinearLayout tools=new LinearLayout(activity);tools.setGravity(Gravity.CENTER_VERTICAL);form.addView(tools,block());
        TextView styles=button("Aa  Форматирование");tools.addView(styles,new LinearLayout.LayoutParams(0,-2,1));
        HorizontalScrollView formatting=NoteFormatting.toolbar(activity,description);formatting.setVisibility(View.GONE);
        styles.setOnClickListener(v->formatting.setVisibility(formatting.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        ImageButton microphone=new ImageButton(activity);microphone.setImageResource(R.drawable.voice_mic);
        microphone.setImageTintList(ColorStateList.valueOf(ThemeSettings.accent(activity)));
        microphone.setContentDescription("Голосовой ввод по-русски, офлайн");
        microphone.setBackground(shape(surface(),16));LinearLayout.LayoutParams micLp=new LinearLayout.LayoutParams(dp(48),dp(48));micLp.leftMargin=dp(8);tools.addView(microphone,micLp);
        microphone.setOnClickListener(v->{if(activity instanceof MainActivity)((MainActivity)activity).startVoice(description.hasFocus()?description:title);});
        form.addView(formatting,block());
        TextView detected=button("Найти дату в тексте");detected.setVisibility(View.GONE);form.addView(detected,block());
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
        TextView add=button("＋  Добавить пункт списка");form.addView(add,block());add.setOnClickListener(v->{addItem("",false);rows.get(rows.size()-1).text.requestFocus();});
        TextView options=button("Повтор и параметры  ▾");form.addView(options,block());LinearLayout extra=column();extra.setVisibility(View.GONE);form.addView(extra);
        options.setOnClickListener(v->extra.setVisibility(extra.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        TextView clear=button("Убрать дату и напоминание");extra.addView(clear,block());clear.setOnClickListener(v->setDate(null,false));
        String[] repeats={"Не повторять","Каждый день","Каждую неделю"};selectedRepeat=Math.max(0,Math.min(2,task.repeat));
        repeatButton=button("Повтор: "+repeats[selectedRepeat]+"  ▾");extra.addView(repeatButton,block());
        repeatButton.setOnClickListener(v->new AlertDialog.Builder(activity).setTitle("Повтор").setSingleChoiceItems(repeats,selectedRepeat,(dialog,choice)->{
            selectedRepeat=choice;repeatButton.setText("Повтор: "+repeats[choice]+"  ▾");dialog.dismiss();
        }).setNegativeButton("Отмена",null).show());
        TextView complete=null;
        if(task.id>0){complete=button(task.done?"Вернуть задачу в список":"Завершить задачу");extra.addView(complete,block());}
        AlertDialog.Builder builder=new AlertDialog.Builder(activity).setTitle(task.id==0?"Новая задача":"Задача").setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null);
        AlertDialog dialog=builder.create();dialog.setOnShowListener(ignored->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->commit(dialog,false));
        });dialog.show();RoundedUi.dialog(dialog);RoundedUi.actions(dialog);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if(complete!=null)complete.setOnClickListener(v->commit(dialog,true));
    }
    private void setDate(Long value,boolean time){due=value;hasTime=time;dateChanged=true;dateButton.setText("◷  "+DateTimePicker.compact(due,hasTime));}
    private void commit(AlertDialog dialog,boolean toggleDone){
        String raw=title.getText().toString().trim();if(raw.isEmpty()){title.setError("Нужно название");title.requestFocus();return;}
        TaskDateParser.Result parsed=TaskDateParser.parse(raw);TaskStore store=new TaskStore(activity);
        Task current=task.id==0?new Task(store.allocateId(),parsed.title,task.dueAt,task.hasTime,task.repeat,false):store.find(task.id);
        if(current==null){dialog.dismiss();return;}
        current.title=parsed.title;NoteFormatting.save(current,description.getText());current.folder=selectedFolder==0?"":folders.get(selectedFolder);
        if(dateChanged){current.dueAt=due;current.hasTime=hasTime;}
        else if(parsed.dueAt!=null){current.dueAt=parsed.dueAt;current.hasTime=parsed.hasTime;}
        else if(task.id==0&&task.dueAt==null){TaskDateParser.Result body=TaskDateParser.parse(description.getText().toString());current.dueAt=body.dueAt;current.hasTime=body.hasTime;parsed=body;}
        current.repeat=current.dueAt==null?0:selectedRepeat;if(!dateChanged&&parsed.repeat!=0)current.repeat=parsed.repeat;
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
        ItemRow row=new ItemRow(check,edit);rows.add(row);checklist.addView(line,block());remove.setOnClickListener(v->{rows.remove(row);checklist.removeView(line);});
    }
    private static final class ItemRow {final CheckBox done;final EditText text;ItemRow(CheckBox done,EditText text){this.done=done;this.text=text;}}
}

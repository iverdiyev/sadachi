package ru.svoizadachi.app;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=OfflineTestApplication.class)
public class NotesAndWidgetsTest {
    private final Application context=RuntimeEnvironment.getApplication();
    private Task create(String name,Long due){
        TaskStore store=new TaskStore(context);Task task=new Task(store.allocateId(),name,due,due!=null,0,false);store.upsert(task);return task;
    }
    private <T extends View> T find(View view,Class<T> type,String text){
        if(type.isInstance(view)&&(text==null||view instanceof TextView&&((TextView)view).getText().toString().equals(text)))return type.cast(view);
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){
            T result=find(group.getChildAt(i),type,text);if(result!=null)return result;
        }}return null;
    }
    @Test public void oldTaskJsonMigratesWithoutLosingFields() throws Exception {
        Task old=Task.fromJson(new JSONObject("{\"id\":9,\"title\":\"Старая задача\",\"dueAt\":12345,\"hasTime\":true,\"repeat\":1,\"done\":false}"));
        assertEquals("Старая задача",old.title);assertEquals(Long.valueOf(12345),old.dueAt);
        assertEquals("",old.description);assertEquals("",old.folder);assertTrue(old.items.isEmpty());
    }
    @Test public void noteAndChecklistRoundTrip() throws Exception {
        Task task=create("Проект",null);task.description="Первая строка\nВторая строка";task.folder="Учёба";
        task.items.add(new Task.Item("Прочитать",true));task.items.add(new Task.Item("Написать",false));
        new TaskStore(context).upsert(task);Task restored=new TaskStore(context).find(task.id);
        assertEquals(task.description,restored.description);assertEquals("Учёба",restored.folder);
        assertEquals(2,restored.items.size());assertEquals(1,restored.completedItems());assertFalse(restored.done);
    }
    @Test public void emptyFoldersPersistAndRenameKeepsNotes(){
        TaskStore store=new TaskStore(context);assertTrue(store.addFolder("Учёба"));assertFalse(store.addFolder(" Учёба "));
        assertTrue(new TaskStore(context).folders().contains("Учёба"));
        Task task=create("Зачёт",null);task.folder="Учёба";store.upsert(task);
        assertTrue(store.renameFolder("Учёба","Университет"));assertEquals("Университет",store.find(task.id).folder);
        store.removeFolder("Университет");assertEquals("",store.find(task.id).folder);assertEquals("Зачёт",store.find(task.id).title);
    }
    @Test public void todayWidgetIncludesOverdueButNotFutureOrUndated(){
        Task past=create("Вчера",LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
        Task today=create("Сегодня",System.currentTimeMillis());
        create("Завтра",LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
        create("Без даты",null);Task done=create("Готово",System.currentTimeMillis());done.done=true;new TaskStore(context).upsert(done);
        List<Task> shown=TaskWidgetProvider.visibleTasks(new TaskStore(context).all(),true);
        assertEquals(2,shown.size());assertEquals(past.id,shown.get(0).id);assertEquals(today.id,shown.get(1).id);
    }
    @Test public void widgetRendersUpdatesAndCompletesTask(){
        Task task=create("Купить молоко",null);AppWidgetManager manager=AppWidgetManager.getInstance(context);
        int id=shadowOf(manager).createWidget(TaskWidgetProvider.class,R.layout.widget_tasks);
        View widget=shadowOf(manager).getViewFor(id);
        assertNotNull(find(widget,TextView.class,"Купить молоко"));
        assertTrue(widget.findViewById(R.id.widget_check).performClick());shadowOf(Looper.getMainLooper()).idle();
        assertTrue(new TaskStore(context).find(task.id).done);
        assertEquals(View.VISIBLE,shadowOf(manager).getViewFor(id).findViewById(R.id.widget_empty).getVisibility());
    }
    @Test public void todayAndAllWidgetsUseDifferentProviders(){
        create("Без даты",null);AppWidgetManager manager=AppWidgetManager.getInstance(context);
        int all=shadowOf(manager).createWidget(TaskWidgetProvider.class,R.layout.widget_tasks);
        int today=shadowOf(manager).createWidget(TodayWidgetProvider.class,R.layout.widget_tasks);
        assertNotNull(find(shadowOf(manager).getViewFor(all),TextView.class,"Без даты"));
        assertNull(find(shadowOf(manager).getViewFor(today),TextView.class,"Без даты"));
        assertNotNull(find(shadowOf(manager).getViewFor(today),TextView.class,"Сегодня"));
    }
    @Test public void editorCancelLeavesOriginalDescriptionAndChecklist(){
        Task task=create("Заметка",null);task.description="Оригинал";task.items.add(new Task.Item("Пункт",false));new TaskStore(context).upsert(task);
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            TaskEditor.show(controller.get(),task,()->{});shadowOf(Looper.getMainLooper()).idle();AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            find(dialog.getWindow().getDecorView(),EditText.class,"Оригинал").setText("Изменено");
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            Task restored=new TaskStore(context).find(task.id);assertEquals("Оригинал",restored.description);assertEquals(1,restored.items.size());
        }
    }
    @Test public void editorSavesDescriptionAndChecklist(){
        Task task=create("Заметка",null);
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            TaskEditor.show(controller.get(),task,()->{});shadowOf(Looper.getMainLooper()).idle();AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            View root=dialog.getWindow().getDecorView();
            for(EditText edit:edits(root))if("Подробности, ссылки, мысли…".contentEquals(edit.getHint()))edit.setText("Описание заметки");
            find(root,TextView.class,"＋  Добавить пункт списка").performClick();
            for(EditText edit:edits(root))if("Пункт списка".contentEquals(edit.getHint()))edit.setText("Первый шаг");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();Task restored=new TaskStore(context).find(task.id);
            assertEquals("Описание заметки",restored.description);assertEquals("Первый шаг",restored.items.get(0).text);
        }
    }
    @Test public void datePickerSelectsDateAndTime(){
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            Long[] result={null};boolean[] timed={false};
            DateTimePicker.show(controller.get(),null,(due,time)->{result[0]=due;timed[0]=time;});
            DatePickerDialog date=(DatePickerDialog)org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            date.getDatePicker().updateDate(2027,0,3);date.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();
            TimePickerDialog time=(TimePickerDialog)org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            TimePicker picker=find(time.getWindow().getDecorView(),TimePicker.class,null);picker.setHour(14);picker.setMinute(25);
            time.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();
            assertTrue(timed[0]);assertEquals(Long.valueOf(LocalDate.of(2027,1,3).atTime(14,25).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()),result[0]);
        }
    }
    @Test public void dateOnlyDoesNotEnableNotification(){
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            Long[] result={null};boolean[] timed={true};
            DateTimePicker.show(controller.get(),null,(due,time)->{result[0]=due;timed[0]=time;});
            DatePickerDialog date=(DatePickerDialog)org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            date.getDatePicker().updateDate(2027,1,4);date.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();shadowOf(Looper.getMainLooper()).idle();
            assertFalse(timed[0]);assertEquals(Long.valueOf(LocalDate.of(2027,2,4).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()),result[0]);
        }
    }
    @Test public void timeChipKeepsThePreviouslyChosenDate(){
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            Long initial=LocalDate.of(2027,1,3).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            Long[] result={null};boolean[] timed={false};
            DateTimePicker.showTime(controller.get(),initial,(due,time)->{result[0]=due;timed[0]=time;});
            TimePickerDialog dialog=(TimePickerDialog)org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            TimePicker picker=find(dialog.getWindow().getDecorView(),TimePicker.class,null);picker.setHour(14);picker.setMinute(25);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();
            assertTrue(timed[0]);assertEquals(Long.valueOf(LocalDate.of(2027,1,3).atTime(14,25).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()),result[0]);
        }
    }
    private List<EditText> edits(View view){
        List<EditText> result=new ArrayList<>();if(view instanceof EditText)result.add((EditText)view);
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)result.addAll(edits(group.getChildAt(i)));}
        return result;
    }
}

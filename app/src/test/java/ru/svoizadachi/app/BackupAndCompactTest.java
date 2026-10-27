package ru.svoizadachi.app;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=OfflineTestApplication.class)
public class BackupAndCompactTest {
    @Test public void localBackupPreservesNotesAndAppendsWithoutDeleting() throws Exception {
        Application context=RuntimeEnvironment.getApplication();TaskStore store=new TaskStore(context);store.addFolder("Учёба");
        Task original=new Task(store.allocateId(),"Проект",null,false,0,false);original.folder="Учёба";original.description="Описание";original.items.add(new Task.Item("Пункт",true));store.upsert(original);
        JSONObject backup=LocalBackup.snapshot(context);assertEquals(1,LocalBackup.restore(context,backup));
        assertEquals(2,store.all().size());assertEquals("Описание",store.find(original.id).description);
        assertNotEquals(store.all().get(0).id,store.all().get(1).id);assertTrue(store.all().get(1).items.get(0).done);
    }
    @Test public void invalidBackupDoesNotEraseExistingData() throws Exception {
        Application context=RuntimeEnvironment.getApplication();TaskStore store=new TaskStore(context);
        store.upsert(new Task(store.allocateId(),"Оставить",null,false,0,false));
        try{LocalBackup.restore(context,new JSONObject().put("format","wrong"));fail("Invalid format accepted");}catch(java.io.IOException expected){}
        assertEquals(1,store.all().size());
    }
    @Test public void tinyWidgetDoesNotInflateLargeCards(){
        Application context=RuntimeEnvironment.getApplication();TaskStore store=new TaskStore(context);
        for(int i=0;i<5;i++)store.upsert(new Task(store.allocateId(),"Задача "+i,null,false,0,false));
        AppWidgetManager manager=AppWidgetManager.getInstance(context);int id=shadowOf(manager).createWidget(TaskWidgetProvider.class,R.layout.widget_tasks);
        Bundle size=new Bundle();size.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,90);size.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,150);manager.updateAppWidgetOptions(id,size);
        LinearLayout rows=shadowOf(manager).getViewFor(id).findViewById(R.id.widget_rows);assertTrue(rows.getChildCount()<=1);
        assertEquals(View.GONE,shadowOf(manager).getViewFor(id).findViewById(R.id.widget_progress).getVisibility());
    }
    @Test public void launcherIconChoiceIsStoredIndependentlyOfTheme(){
        Application context=RuntimeEnvironment.getApplication();ThemeSettings.setMode(context,1);LauncherIcon.setDark(context,true);
        assertTrue(LauncherIcon.dark(context));assertFalse(ThemeSettings.dark(context));LauncherIcon.setDark(context,false);assertFalse(LauncherIcon.dark(context));
    }
    @Test public void iosStyleIsOptionalAndDoesNotTouchNotesOrTheme(){
        Application context=RuntimeEnvironment.getApplication();TaskStore store=new TaskStore(context);
        Task task=new Task(store.allocateId(),"Остаться на месте",null,false,0,false);store.upsert(task);
        ThemeSettings.setMode(context,1);LauncherIcon.setDark(context,true);
        assertFalse(ThemeSettings.ios(context));ThemeSettings.setIos(context,true);
        assertEquals(0xFFF2F2F7,ThemeSettings.background(context));
        assertEquals(0xFF007AFF,ThemeSettings.accent(context));
        assertFalse(ThemeSettings.dark(context));assertTrue(LauncherIcon.dark(context));
        assertEquals("Остаться на месте",store.find(task.id).title);
        ThemeSettings.setMode(context,2);assertEquals(0xFF000000,ThemeSettings.background(context));
        assertEquals(0xFF0A84FF,ThemeSettings.accent(context));
        ThemeSettings.setIos(context,false);assertEquals(0xFF121722,ThemeSettings.background(context));
        assertEquals("Остаться на месте",store.find(task.id).title);
    }
    @Test public void iosWidgetUsesTheSameAccentAsTheApp(){
        Application context=RuntimeEnvironment.getApplication();ThemeSettings.setMode(context,1);ThemeSettings.setIos(context,true);
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        int id=shadowOf(manager).createWidget(TaskWidgetProvider.class,R.layout.widget_tasks);
        TaskWidgetProvider.updateAll(context);
        TextView title=shadowOf(manager).getViewFor(id).findViewById(R.id.widget_title);
        TextView add=shadowOf(manager).getViewFor(id).findViewById(R.id.widget_add);
        assertEquals(ThemeSettings.accent(context),title.getCurrentTextColor());
        assertEquals(ThemeSettings.accent(context),add.getCurrentTextColor());
        ThemeSettings.setMode(context,2);TaskWidgetProvider.updateAll(context);
        TextView darkAdd=shadowOf(manager).getViewFor(id).findViewById(R.id.widget_add);
        assertEquals(ThemeSettings.accent(context),darkAdd.getCurrentTextColor());
    }
    @Test public void iosStyleAppliesToHomeAndKeepsInputUsable(){
        Application context=RuntimeEnvironment.getApplication();ThemeSettings.setMode(context,1);ThemeSettings.setIos(context,true);
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();View root=((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
            assertEquals(ThemeSettings.background(activity),((android.graphics.drawable.ColorDrawable)root.getBackground()).getColor());
            View input=described(root,"Название задачи и время напоминания");View add=described(root,"Добавить задачу");
            assertNotNull(input);assertNotNull(add);assertTrue(add.isClickable());
        }
    }
    @Test public void dialogsLeaveMarginsOnPhonesAndDoNotStretchAcrossTablets(){
        assertEquals(288,RoundedUi.dialogWidth(320,1f));
        assertEquals(560,RoundedUi.dialogWidth(1200,1f));
        assertEquals(576,RoundedUi.dialogWidth(640,2f));
    }
    @Test public void compactEditorCanCreateWithoutAnEmptyTaskOnCancel(){
        Application context=RuntimeEnvironment.getApplication();
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            TaskEditor.show(controller.get(),new Task(0,"",null,false,0,false),()->{});
            android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.cancel();
            assertTrue(new TaskStore(context).all().isEmpty());
        }
    }
    @Test public void collapsedNotesDoNotAllocateHiddenEditors(){
        Application context=RuntimeEnvironment.getApplication();java.util.List<Task> tasks=new java.util.ArrayList<>();
        for(int i=1;i<=100;i++)tasks.add(new Task(i,"Заметка "+i,null,false,0,false));new TaskStore(context).save(tasks);
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            assertEquals(1,editors(controller.get().findViewById(android.R.id.content)));
        }
    }
    private int editors(View view){int count=view instanceof EditText?1:0;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)count+=editors(group.getChildAt(i));}return count;}
    private View described(View view,String label){if(label.equals(view.getContentDescription()))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){
            View found=described(group.getChildAt(i),label);if(found!=null)return found;
        }}return null;
    }
    @Test public void editorMetadataStacksOnPhonesAndAtLargeFont(){
        assertTrue(TaskEditor.stackMetadata(360,1f));assertTrue(TaskEditor.stackMetadata(600,1.8f));
        assertFalse(TaskEditor.stackMetadata(600,1f));
    }
    @Test public void editorHasRoundedFieldsWithoutNativeSpinners(){
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            TaskEditor.show(controller.get(),new Task(0,"",null,false,0,false),()->{});
            android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();View root=dialog.getWindow().getDecorView();
            View title=described(root,"Заголовок H1"),body=described(root,"Описание заметки"),folder=described(root,"Выбрать папку");
            assertNotNull(title);assertNotNull(body);assertNotNull(folder);
            assertTrue(title.getBackground() instanceof android.graphics.drawable.GradientDrawable);
            assertTrue(body.getBackground() instanceof android.graphics.drawable.GradientDrawable);
            assertEquals(0,spinners(root));dialog.dismiss();
        }
    }
    private int spinners(View view){int count=view instanceof Spinner?1:0;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)count+=spinners(group.getChildAt(i));}return count;}
    @Test public void quickInputKeepsFullWidthWhenControlsWrap(){
        assertTrue(TaskEditor.stackMetadata(360,1f));
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();View root=activity.findViewById(android.R.id.content);
            View field=described(root,"Название задачи и время напоминания");View add=described(root,"Добавить задачу");
            assertNotNull(field);assertNotNull(add);
            assertNotSame(field.getParent(),add.getParent());
        }
    }
    @Test public void largeFontKeepsQuickInputInsideTheWindow(){
        Application context=RuntimeEnvironment.getApplication();android.content.res.Configuration config=new android.content.res.Configuration(context.getResources().getConfiguration());
        config.fontScale=1.8f;context.getResources().updateConfiguration(config,context.getResources().getDisplayMetrics());
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            ViewGroup root=(ViewGroup)((ViewGroup)controller.get().findViewById(android.R.id.content)).getChildAt(0);float density=context.getResources().getDisplayMetrics().density;
            int width=Math.round(360*density),height=Math.round(640*density);
            root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);
            assertTrue(root.getChildAt(0).getHeight()>0);assertTrue(root.getChildAt(root.getChildCount()-1).getBottom()<=height);
        }
    }
}

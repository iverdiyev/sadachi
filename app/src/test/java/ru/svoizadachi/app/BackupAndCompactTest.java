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

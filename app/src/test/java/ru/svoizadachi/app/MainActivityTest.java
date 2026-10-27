package ru.svoizadachi.app;

import android.graphics.Insets;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Layout/interaction regression tests. Physical OxygenOS delivery is a separate device check. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=OfflineTestApplication.class)
public class MainActivityTest {
    private ViewGroup content(MainActivity activity){
        return (ViewGroup)((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
    }
    private <T extends View> T find(View view,Class<T> type,String text){
        if(type.isInstance(view)&&(text==null||view instanceof TextView&&((TextView)view).getText().toString().equals(text)))return type.cast(view);
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){T found=find(group.getChildAt(i),type,text);if(found!=null)return found;}
        }
        return null;
    }
    private View findDescription(View view,String description){
        if(description.equals(view.getContentDescription()))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){
            View found=findDescription(group.getChildAt(i),description);if(found!=null)return found;
        }}return null;
    }
    @Test public void systemBarsAndCutoutAreSafeOnAndroid15(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            ViewGroup root=content(controller.get());
            root.dispatchApplyWindowInsets(new WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.systemBars(),Insets.of(0,88,0,64))
                    .setInsets(WindowInsets.Type.displayCutout(),Insets.of(34,0,28,0)).build());
            assertEquals(88,root.getPaddingTop());assertEquals(64,root.getPaddingBottom());
            assertEquals(34,root.getPaddingLeft());assertEquals(28,root.getPaddingRight());
        }
    }
    @Test public void keyboardInsetUsesMaximumNotSum(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            ViewGroup root=content(controller.get());
            root.dispatchApplyWindowInsets(new WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.systemBars(),Insets.of(0,80,0,48))
                    .setInsets(WindowInsets.Type.ime(),Insets.of(0,0,0,700)).build());
            assertEquals(700,root.getPaddingBottom());
            root.dispatchApplyWindowInsets(new WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.systemBars(),Insets.of(0,80,0,48)).build());
            assertEquals(48,root.getPaddingBottom());
        }
    }
    @Test public void addingRelativeReminderPersistsTimedTask(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();ViewGroup root=content(activity);
            find(root,EditText.class,null).setText("Чай через 2 минуты");
            long before=System.currentTimeMillis();
            assertTrue(findDescription(root,"Добавить задачу").performClick());
            Task task=new TaskStore(activity).all().get(0);
            assertEquals("Чай",task.title);assertTrue(task.hasTime);assertFalse(task.done);
            assertNotNull(task.dueAt);assertTrue(task.dueAt>=before+120000);
            assertTrue(task.dueAt<=System.currentTimeMillis()+120000);
        }
    }
    @Test public void draftSurvivesRecreation(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            find(content(controller.get()),EditText.class,null).setText("Позвонить через час");
            Bundle saved=new Bundle();controller.saveInstanceState(saved);
            controller.pause().stop().destroy();
            try(ActivityController<MainActivity> restored=Robolectric.buildActivity(MainActivity.class).create(saved).start().resume()){
                assertEquals("Позвонить через час",find(content(restored.get()),EditText.class,null).getText().toString());
            }
        }
    }
    @Test public void multilineInputUsesTitleAndDetectsTimeInBody(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();ViewGroup root=content(activity);
            find(root,EditText.class,null).setText("Проект\nОтправить завтра в 18:00 документы");
            findDescription(root,"Добавить задачу").performClick();
            Task task=new TaskStore(activity).all().get(0);
            assertEquals("Проект",task.title);assertEquals("Отправить завтра в 18:00 документы",task.description);
            assertTrue(task.hasTime);assertNotNull(task.dueAt);
        }
    }
    @Test public void expandedNoteDraftSurvivesRotationUntilExplicitSave(){
        TaskStore store=new TaskStore(org.robolectric.RuntimeEnvironment.getApplication());
        Task task=new Task(store.allocateId(),"Моя заметка",null,false,0,false);task.description="Старое описание";store.upsert(task);
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            TextView title=find(content(controller.get()),TextView.class,"Моя заметка");((View)title.getParent()).performClick();
            find(content(controller.get()),EditText.class,null).setText("Новое описание");
            assertEquals("Старое описание",store.find(task.id).description);
            controller.recreate();assertEquals("Новое описание",find(content(controller.get()),EditText.class,null).getText().toString());
            find(content(controller.get()),TextView.class,"Сохранить описание").performClick();
            assertEquals("Новое описание",store.find(task.id).description);
        }
    }
}

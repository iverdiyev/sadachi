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
    @Test public void compactBarOpensEditorAndSettingsHideInput(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            ViewGroup root=content(controller.get());
            View field=findDescription(root,"Название задачи и время напоминания");
            View quick=findDescription(root,"Открыть создание задачи");
            assertNotNull(quick);assertEquals(View.GONE,((View)field.getParent().getParent()).getVisibility());
            quick.performClick();assertEquals(View.VISIBLE,((View)field.getParent().getParent()).getVisibility());
            assertTrue(new TaskStore(controller.get()).all().isEmpty());
            field.requestFocus();findDescription(root,"Настройки приложения").performClick();
            assertNotNull(find(root,TextView.class,"Настройки"));
            assertFalse(field.hasFocus());assertEquals(View.GONE,((View)field.getParent().getParent()).getVisibility());
            assertNotNull(findDescription(root,"Перенос и резервные копии"));
            findDescription(root,"Назад к задачам").performClick();
            assertEquals(View.VISIBLE,((View)field.getParent().getParent()).getVisibility());
        }
    }
    @Test public void editorStaysAboveImeAtPhoneWidths(){
        for(int widthDp:new int[]{320,360,375,390,393,430}){
            try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
                ViewGroup root=content(controller.get());
                findDescription(root,"Открыть создание задачи").performClick();
                View field=findDescription(root,"Название задачи и время напоминания");
                float density=controller.get().getResources().getDisplayMetrics().density;
                int width=Math.round(widthDp*density),height=Math.round((widthDp==320?640:720)*density),ime=Math.round(290*density);
                root.dispatchApplyWindowInsets(new WindowInsets.Builder()
                        .setInsets(WindowInsets.Type.systemBars(),Insets.of(0,Math.round(24*density),0,Math.round(24*density)))
                        .setInsets(WindowInsets.Type.ime(),Insets.of(0,0,0,ime)).build());
                root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);
                View editor=(View)field.getParent().getParent();
                assertEquals(widthDp,ime,root.getPaddingBottom());
                assertTrue("Editor visible at "+widthDp,editor.getHeight()>0);
                assertTrue("Editor above IME at "+widthDp,editor.getBottom()<=height-ime);
                assertTrue("No horizontal overflow at "+widthDp,editor.getRight()<=width-root.getPaddingRight());
                View save=findDescription(root,"Добавить задачу");
                assertEquals("Save stays visible at "+widthDp,View.VISIBLE,save.getVisibility());
                assertTrue("Save has tappable width at "+widthDp,save.getWidth()>=Math.round(80*density));
            }
        }
    }
}

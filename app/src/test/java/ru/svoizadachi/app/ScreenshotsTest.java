package ru.svoizadachi.app;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.*;
import java.time.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Native Android-view renders with synthetic data, never a connected phone's notes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=OfflineTestApplication.class,qualifiers="ru-rRU-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenshotsTest {
    private void seed(){
        Application c=RuntimeEnvironment.getApplication();TaskStore store=new TaskStore(c);store.addFolder("Учёба");store.addFolder("Личное");
        long today=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Task project=new Task(store.allocateId(),"Подготовить презентацию",today,false,0,false);project.folder="Учёба";
        NoteFormatting.save(project,android.text.Html.fromHtml("Собрать <b>ключевые слайды</b>.<br>Главное — <i>коротко и по делу</i>.",android.text.Html.FROM_HTML_MODE_COMPACT));
        project.items.add(new Task.Item("Собрать материалы",true));project.items.add(new Task.Item("Оформить слайды",false));store.upsert(project);
        Task shopping=new Task(store.allocateId(),"Купить продукты",today,false,0,false);shopping.folder="Личное";shopping.items.add(new Task.Item("Овощи и фрукты",false));store.upsert(shopping);
        store.upsert(new Task(store.allocateId(),"Прогулка без телефона",null,false,0,false));
        store.upsert(new Task(store.allocateId(),"Почитать 20 минут",today,false,0,true));
    }
    private void capture(View view,int width,int height,String name) throws Exception {
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        view.layout(0,0,width,height);
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(0xFFE9EDF6);view.draw(canvas);
        File directory=new File("build/screenshots");assertTrue(directory.isDirectory()||directory.mkdirs());File file=new File(directory,name+".png");
        try(FileOutputStream output=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));}
        bitmap.recycle();assertTrue("Screenshot should contain rendered UI",file.length()>10000);
    }
    private ViewGroup root(MainActivity activity){return (ViewGroup)((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);}
    private void app(boolean dark,String name,boolean note) throws Exception {
        seed();ThemeSettings.setMode(RuntimeEnvironment.getApplication(),dark?2:1);
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            ViewGroup root=root(controller.get());shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
            root.measure(View.MeasureSpec.makeMeasureSpec(1233,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2673,View.MeasureSpec.EXACTLY));root.layout(0,0,1233,2673);
            if(note){
                TextView title=find(root,"Подготовить презентацию");assertNotNull(title);((View)title.getParent()).performClick();
                shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
                ((ScrollView)root.getChildAt(0)).scrollTo(0,Math.round(260*controller.get().getResources().getDisplayMetrics().density));
            }
            capture(root,1233,2673,name);
        }
    }
    private TextView find(View view,String text){
        if(view instanceof TextView&&((TextView)view).getText().toString().equals(text))return (TextView)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){TextView found=find(group.getChildAt(i),text);if(found!=null)return found;}}
        return null;
    }
    @Test public void lightScreen() throws Exception {app(false,"light",false);}
    @Test public void darkScreen() throws Exception {app(true,"dark",false);}
    @Test public void expandedNote() throws Exception {app(false,"note",true);}
    @Test public void widgetScreen() throws Exception {
        seed();Application context=RuntimeEnvironment.getApplication();ThemeSettings.setMode(context,1);AppWidgetManager manager=AppWidgetManager.getInstance(context);
        int id=shadowOf(manager).createWidget(TaskWidgetProvider.class,R.layout.widget_tasks);
        Bundle options=new Bundle();options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,376);manager.updateAppWidgetOptions(id,options);
        capture(shadowOf(manager).getViewFor(id),1233,1128,"widget");
    }
}

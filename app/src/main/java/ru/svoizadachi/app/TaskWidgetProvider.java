package ru.svoizadachi.app;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class TaskWidgetProvider extends AppWidgetProvider {
    private static final String COMPLETE="ru.svoizadachi.WIDGET_COMPLETE";
    public static List<Task> visibleTasks(List<Task> source,boolean today){
        List<Task> tasks=new ArrayList<>();LocalDate now=LocalDate.now();
        for(Task task:source){
            if(task.done)continue;
            if(today&&(task.dueAt==null||Instant.ofEpochMilli(task.dueAt).atZone(ZoneId.systemDefault()).toLocalDate().isAfter(now)))continue;
            tasks.add(task);
        }
        tasks.sort(Comparator.comparing((Task t)->t.dueAt,Comparator.nullsLast(Long::compareTo)).thenComparingLong(t->t.id));
        return tasks;
    }
    public static void updateAll(Context context){
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        updateProvider(context,manager,TaskWidgetProvider.class,false);
        updateProvider(context,manager,TodayWidgetProvider.class,true);
    }
    private static void updateProvider(Context c,AppWidgetManager manager,Class<?> provider,boolean today){
        int[] ids=manager.getAppWidgetIds(new ComponentName(c,provider));if(ids.length==0)return;
        List<Task> source=new TaskStore(c).all();
        for(int id:ids){
            try{render(c,manager,id,today,provider,source);}
            catch(RuntimeException error){android.util.Log.w("SadachiWidget","Widget update failed: "+id,error);}
        }
    }
    private static PendingIntent open(Context c,long id,boolean add){
        Intent intent=new Intent(c,MainActivity.class).setData(Uri.parse("sadachi://open/"+(add?"new":id)))
                .putExtra("open_task",id).putExtra("new_task",add).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static void render(Context c,AppWidgetManager manager,int id,boolean today,Class<?> provider,List<Task> source){
        RemoteViews view=new RemoteViews(c.getPackageName(),R.layout.widget_tasks);
        view.setInt(R.id.widget_root,"setBackgroundResource",ThemeSettings.dark(c)?R.drawable.widget_background_dark:R.drawable.widget_background);
        view.setTextColor(R.id.widget_title,ThemeSettings.text(c));view.setTextColor(R.id.widget_count,ThemeSettings.subtle(c));
        view.setTextColor(R.id.widget_empty,ThemeSettings.subtle(c));
        view.setTextColor(R.id.widget_footer,ThemeSettings.dark(c)?0xFFB6C8FF:0xFF375DBB);
        List<Task> tasks=visibleTasks(source,today);
        view.setTextViewText(R.id.widget_title,today?"Сегодня":"Задачи");
        view.setTextColor(R.id.widget_title,ThemeSettings.dark(c)?0xFF65DACB:0xFF149E92);
        view.setTextViewText(R.id.widget_count,tasks.isEmpty()?"Нет активных задач":tasks.size()+" задач");
        view.setOnClickPendingIntent(R.id.widget_add,open(c,-1,true));
        view.setOnClickPendingIntent(R.id.widget_title,open(c,-1,false));
        view.setOnClickPendingIntent(R.id.widget_footer,open(c,-1,false));
        view.setOnClickPendingIntent(R.id.widget_menu,open(c,-1,false));
        view.removeAllViews(R.id.widget_rows);
        Bundle options=manager.getAppWidgetOptions(id);
        int height=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,140);
        float scale=Math.max(1,c.getResources().getConfiguration().fontScale);
        boolean showDetails=height>=200&&scale<=1.3f;
        int rowHeight=Math.round((showDetails?44:32)*scale);
        int limit=Math.max(0,Math.min(6,(height-Math.round(30*scale)-16)/rowHeight));
        for(int i=0;i<Math.min(limit,tasks.size());i++){
            Task task=tasks.get(i);RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_task_row);
            row.setTextColor(R.id.widget_task_title,ThemeSettings.text(c));row.setTextColor(R.id.widget_task_detail,ThemeSettings.subtle(c));
            row.setTextColor(R.id.widget_check,ThemeSettings.dark(c)?0xFFB6C8FF:0xFF4970DF);
            row.setTextViewText(R.id.widget_task_title,task.title);
            String detail=task.folder.isEmpty()?"Без папки":task.folder;
            if(task.dueAt!=null)detail=Instant.ofEpochMilli(task.dueAt).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern(task.hasTime?"d MMM · HH:mm":"d MMM",new Locale("ru")))+" · "+detail;
            if(!task.items.isEmpty())detail+=" · "+task.completedItems()+"/"+task.items.size();
            row.setTextViewText(R.id.widget_task_detail,detail);
            row.setViewVisibility(R.id.widget_task_detail,showDetails?android.view.View.VISIBLE:android.view.View.GONE);
            row.setOnClickPendingIntent(R.id.widget_task_text,open(c,task.id,false));
            Intent complete=new Intent(c,provider).setAction(COMPLETE).setData(Uri.parse("sadachi://complete/"+task.id)).putExtra("id",task.id);
            row.setOnClickPendingIntent(R.id.widget_check,PendingIntent.getBroadcast(c,0,complete,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            row.setContentDescription(R.id.widget_check,"Завершить: "+task.title);
            view.addView(R.id.widget_rows,row);
        }
        view.setViewVisibility(R.id.widget_empty,tasks.isEmpty()&&height>80?android.view.View.VISIBLE:android.view.View.GONE);
        manager.updateAppWidget(id,view);
    }
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){updateAll(c);}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager manager,int id,Bundle options){updateAll(c);}
    @Override public void onReceive(Context c,Intent intent){
        super.onReceive(c,intent);
        if(COMPLETE.equals(intent.getAction())){
            long id=intent.getLongExtra("id",-1);TaskStore store=new TaskStore(c);Task task=store.find(id);
            if(task!=null&&!task.done){task.done=true;store.upsert(task);ReminderScheduler.cancel(c,id);}
            updateAll(c);
        }
    }
}

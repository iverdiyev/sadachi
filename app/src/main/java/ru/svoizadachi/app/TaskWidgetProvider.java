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
        for(int id:manager.getAppWidgetIds(new ComponentName(c,provider))){
            try{render(c,manager,id,today,provider);}
            catch(RuntimeException error){android.util.Log.w("SadachiWidget","Widget update failed: "+id,error);}
        }
    }
    private static PendingIntent open(Context c,long id,boolean add){
        Intent intent=new Intent(c,MainActivity.class).setData(Uri.parse("sadachi://open/"+(add?"new":id)))
                .putExtra("open_task",id).putExtra("new_task",add).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static void render(Context c,AppWidgetManager manager,int id,boolean today,Class<?> provider){
        RemoteViews view=new RemoteViews(c.getPackageName(),R.layout.widget_tasks);
        List<Task> tasks=visibleTasks(new TaskStore(c).all(),today);
        view.setTextViewText(R.id.widget_title,today?"Сегодня":"Свои задачи");
        view.setTextViewText(R.id.widget_count,tasks.isEmpty()?"Нет активных задач":tasks.size()+" задач");
        view.setOnClickPendingIntent(R.id.widget_add,open(c,-1,true));
        view.setOnClickPendingIntent(R.id.widget_title,open(c,-1,false));
        view.removeAllViews(R.id.widget_rows);
        Bundle options=manager.getAppWidgetOptions(id);
        int height=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,250);
        int limit=Math.max(1,Math.min(5,(height-100)/64));
        for(int i=0;i<Math.min(limit,tasks.size());i++){
            Task task=tasks.get(i);RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_task_row);
            row.setTextViewText(R.id.widget_task_title,task.title);
            String detail=task.folder.isEmpty()?"Без папки":task.folder;
            if(task.dueAt!=null)detail=Instant.ofEpochMilli(task.dueAt).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern(task.hasTime?"d MMM · HH:mm":"d MMM",new Locale("ru")))+" · "+detail;
            if(!task.items.isEmpty())detail+=" · "+task.completedItems()+"/"+task.items.size();
            row.setTextViewText(R.id.widget_task_detail,detail);
            row.setOnClickPendingIntent(R.id.widget_task_text,open(c,task.id,false));
            Intent complete=new Intent(c,provider).setAction(COMPLETE).setData(Uri.parse("sadachi://complete/"+task.id)).putExtra("id",task.id);
            row.setOnClickPendingIntent(R.id.widget_check,PendingIntent.getBroadcast(c,0,complete,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            row.setContentDescription(R.id.widget_check,"Завершить: "+task.title);
            view.addView(R.id.widget_rows,row);
        }
        view.setViewVisibility(R.id.widget_empty,tasks.isEmpty()?android.view.View.VISIBLE:android.view.View.GONE);
        manager.updateAppWidget(id,view);
    }
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){updateAll(c);}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager manager,int id,Bundle options){updateAll(c);}
    @Override public void onReceive(Context c,Intent intent){
        super.onReceive(c,intent);
        if(COMPLETE.equals(intent.getAction())){
            long id=intent.getLongExtra("id",-1);TaskStore store=new TaskStore(c);Task task=store.find(id);
            if(task!=null&&!task.done){task.done=true;store.upsert(task);ReminderScheduler.cancel(c,id);}
        }
    }
}

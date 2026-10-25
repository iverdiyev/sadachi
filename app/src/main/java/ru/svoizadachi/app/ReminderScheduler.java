package ru.svoizadachi.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import java.time.*;

public final class ReminderScheduler {
    public static final String CHANNEL="reminders_v1";
    public static final String ACTION_NOTIFY="ru.svoizadachi.NOTIFY";
    public static final String ACTION_DONE="ru.svoizadachi.DONE";
    public static final String ACTION_SNOOZE="ru.svoizadachi.SNOOZE";
    public static final String EXTRA_ID="task_id";
    private ReminderScheduler(){}
    public static int requestId(long id){return (int)(id & 0x7fffffff);}
    public static void createChannel(Context context){
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch=new NotificationChannel(CHANNEL,"Напоминания",NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Уведомления по времени задач");
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);
    }
    public static PendingIntent actionIntent(Context context,long id,String action){
        Intent intent=new Intent(context,ReminderReceiver.class).setAction(action).putExtra(EXTRA_ID,id);
        // Stable and distinct request codes for all actions, including snooze.
        int offset=action.equals(ACTION_NOTIFY)?0:action.equals(ACTION_DONE)?100000000:200000000;
        return PendingIntent.getBroadcast(context,requestId(id)+offset,intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static boolean hasExactAccess(Context context){
        AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT<31 || alarm.canScheduleExactAlarms();
    }
    public static void schedule(Context context,Task task){
        AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(actionIntent(context,task.id,ACTION_NOTIFY));
        if(task.done||task.dueAt==null||!task.hasTime||task.dueAt<=System.currentTimeMillis())return;
        PendingIntent pending=actionIntent(context,task.id,ACTION_NOTIFY);
        try {
            if(hasExactAccess(context)) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,task.dueAt,pending);
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,task.dueAt,pending);
        } catch(SecurityException ignored){
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,task.dueAt,pending);
        }
    }
    public static void cancel(Context context,long id){
        AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(actionIntent(context,id,ACTION_NOTIFY));
        ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(requestId(id));
    }
    public static void scheduleAll(Context context){
        createChannel(context);
        for(Task t:new TaskStore(context).all())schedule(context,t);
    }
    public static void snooze(Context context,long id){
        Task t=new TaskStore(context).find(id);
        if(t==null||t.done)return;
        AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent intent=new Intent(context,ReminderReceiver.class).setAction(ACTION_NOTIFY)
                .putExtra(EXTRA_ID,id).putExtra("snoozed",true);
        PendingIntent pending=PendingIntent.getBroadcast(context,requestId(id)+300000000,intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        long when=System.currentTimeMillis()+10*60*1000L;
        if(hasExactAccess(context)){
            try{alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending);return;}
            catch(SecurityException ignored){}
        }
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending);
    }
    public static void advanceRepeating(Context context,Task t){
        if(t.repeat==TaskDateParser.NO_REPEAT||t.dueAt==null)return;
        ZonedDateTime due=Instant.ofEpochMilli(t.dueAt).atZone(ZoneId.systemDefault());
        ZonedDateTime now=ZonedDateTime.now();
        do{due=due.plusDays(t.repeat==TaskDateParser.DAILY?1:7);}while(!due.isAfter(now));
        t.dueAt=due.toInstant().toEpochMilli();
        new TaskStore(context).upsert(t);
        schedule(context,t);
    }
}

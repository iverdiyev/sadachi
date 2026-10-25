package ru.svoizadachi.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import java.time.*;
import java.time.format.DateTimeFormatter;

public final class ReminderScheduler {
    public static final String CHANNEL="reminders_v1";
    public static final String ACTION_NOTIFY="ru.svoizadachi.NOTIFY";
    public static final String ACTION_DONE="ru.svoizadachi.DONE";
    public static final String ACTION_SNOOZE="ru.svoizadachi.SNOOZE";
    public static final String EXTRA_ID="task_id";
    public static final String EXTRA_DUE="expected_due";
    private static final long RECOVERY_WINDOW=24*60*60*1000L;
    private ReminderScheduler(){}
    private static SharedPreferences state(Context c){return c.getSharedPreferences("reminder_delivery_v2",Context.MODE_PRIVATE);}
    public static int requestId(long id){return (int)(id & 0x7fffffff);}
    public static void createChannel(Context context){
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Напоминания",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Уведомления по времени задач");channel.enableVibration(true);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    public static boolean notificationsEnabled(Context c){
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false;
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        NotificationChannel channel=nm.getNotificationChannel(CHANNEL);
        return nm.areNotificationsEnabled()&&channel!=null&&channel.getImportance()!=NotificationManager.IMPORTANCE_NONE;
    }
    public static PendingIntent actionIntent(Context c,long id,String action){
        Intent intent=new Intent(c,ReminderReceiver.class).setAction(action).putExtra(EXTRA_ID,id);
        return PendingIntent.getBroadcast(c,requestId(id),intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static Intent alarmIntent(Context c,long id,long due){
        return new Intent(c,ReminderReceiver.class).setAction(ACTION_NOTIFY)
                .setData(Uri.parse("sadachi://reminder/"+id)).putExtra(EXTRA_ID,id).putExtra(EXTRA_DUE,due);
    }
    private static PendingIntent alarmPending(Context c,long id,long due){
        return PendingIntent.getBroadcast(c,requestId(id),alarmIntent(c,id,due),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static boolean hasExactAccess(Context c){return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    public static long effectiveDue(Context c,Task task){
        long snooze=state(c).getLong("snooze_"+task.id,0);
        return snooze>0?snooze:task.dueAt==null?0:task.dueAt;
    }
    public static void record(Context c,String message){state(c).edit().putString("event",message).putLong("event_time",System.currentTimeMillis()).commit();}
    public static void schedule(Context c,Task task){
        AlarmManager alarm=c.getSystemService(AlarmManager.class);
        // Cancel the pre-0.3 alarm identity as well during migration.
        alarm.cancel(actionIntent(c,task.id,ACTION_NOTIFY));
        alarm.cancel(alarmPending(c,task.id,0));
        long due=effectiveDue(c,task);
        if(task.done||!task.hasTime||due==0)return;
        if(state(c).getLong("posted_"+task.id,0)==due)return;
        long now=System.currentTimeMillis();
        if(due<=now){
            // Recover a missed alarm after reboot, an update or permission grant.
            // Never flood the user with tasks overdue by several days.
            if(now-due<=RECOVERY_WINDOW)c.sendBroadcast(alarmIntent(c,task.id,due));
            else if(task.repeat!=TaskDateParser.NO_REPEAT)advanceRepeating(c,task);
            return;
        }
        PendingIntent pending=alarmPending(c,task.id,due);
        try{
            if(hasExactAccess(c))alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,pending);
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,pending);
            state(c).edit().putLong("scheduled_"+task.id,due).commit();
        }catch(SecurityException error){
            try{alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,pending);}
            catch(RuntimeException failure){record(c,"Android отклонил таймер: "+failure.getClass().getSimpleName());}
        }catch(RuntimeException error){record(c,"Не удалось поставить таймер: "+error.getClass().getSimpleName());}
    }
    public static boolean shouldDeliver(Context c,Task task,long expected){
        long due=effectiveDue(c,task);
        return !task.done&&task.hasTime&&due>0&&(expected==0||expected==due)
                &&due<=System.currentTimeMillis()+1000&&state(c).getLong("posted_"+task.id,0)!=due;
    }
    public static void delivered(Context c,Task task,long due){
        boolean snoozed=state(c).getLong("snooze_"+task.id,0)>0;
        long posted=snoozed&&task.repeat==TaskDateParser.NO_REPEAT&&task.dueAt!=null?task.dueAt:due;
        state(c).edit().putLong("posted_"+task.id,posted).remove("snooze_"+task.id).commit();
        record(c,"Уведомление передано Android");
    }
    public static void cancel(Context c,long id){
        AlarmManager alarm=c.getSystemService(AlarmManager.class);
        alarm.cancel(alarmPending(c,id,0));alarm.cancel(actionIntent(c,id,ACTION_NOTIFY));
        Intent legacy=new Intent(c,ReminderReceiver.class).setAction(ACTION_NOTIFY).putExtra(EXTRA_ID,id).putExtra("snoozed",true);
        alarm.cancel(PendingIntent.getBroadcast(c,requestId(id)+300000000,legacy,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        state(c).edit().remove("snooze_"+id).remove("scheduled_"+id).remove("posted_"+id).commit();
        c.getSystemService(NotificationManager.class).cancel(requestId(id));
    }
    public static void scheduleAll(Context c){createChannel(c);for(Task task:new TaskStore(c).all())schedule(c,task);}
    public static void snooze(Context c,long id){
        Task task=new TaskStore(c).find(id);if(task==null||task.done)return;
        state(c).edit().putLong("snooze_"+id,System.currentTimeMillis()+10*60*1000L).commit();
        schedule(c,task);
    }
    public static void advanceRepeating(Context c,Task task){
        if(task.repeat==TaskDateParser.NO_REPEAT||task.dueAt==null)return;
        ZonedDateTime due=Instant.ofEpochMilli(task.dueAt).atZone(ZoneId.systemDefault()),now=ZonedDateTime.now();
        do{due=due.plusDays(task.repeat==TaskDateParser.DAILY?1:7);}while(!due.isAfter(now));
        task.dueAt=due.toInstant().toEpochMilli();new TaskStore(c).upsert(task);schedule(c,task);
    }
    public static String diagnostics(Context c){
        createChannel(c);PowerManager power=c.getSystemService(PowerManager.class);
        String event=state(c).getString("event","Пока нет срабатываний");long time=state(c).getLong("event_time",0);
        if(time>0)event+=" · "+Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        return "Уведомления: "+(notificationsEnabled(c)?"разрешены":"выключены")
                +"\nТочные таймеры: "+(hasExactAccess(c)?"разрешены":"нет доступа — возможна задержка")
                +"\nОптимизация батареи: "+(power.isIgnoringBatteryOptimizations(c.getPackageName())?"без ограничений":"включена")
                +"\n\nПоследнее событие:\n"+event;
    }
}

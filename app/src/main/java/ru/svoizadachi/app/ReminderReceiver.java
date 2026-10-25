package ru.svoizadachi.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        if(intent==null)return;long id=intent.getLongExtra(ReminderScheduler.EXTRA_ID,-1);if(id<0)return;
        TaskStore store=new TaskStore(c);Task task=store.find(id);if(task==null)return;
        String action=intent.getAction();NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(ReminderScheduler.ACTION_DONE.equals(action)){
            if(task.repeat==TaskDateParser.NO_REPEAT){task.done=true;store.upsert(task);ReminderScheduler.cancel(c,id);}
            else {
                ReminderScheduler.cancel(c,id);
                if(task.dueAt!=null&&task.dueAt>System.currentTimeMillis())ReminderScheduler.schedule(c,task);
                else ReminderScheduler.advanceRepeating(c,task);
            }
            nm.cancel(ReminderScheduler.requestId(id));return;
        }
        if(ReminderScheduler.ACTION_SNOOZE.equals(action)){
            ReminderScheduler.snooze(c,id);nm.cancel(ReminderScheduler.requestId(id));return;
        }
        if(!ReminderScheduler.ACTION_NOTIFY.equals(action)||!ReminderScheduler.shouldDeliver(c,task,intent.getLongExtra(ReminderScheduler.EXTRA_DUE,0)))return;
        long due=ReminderScheduler.effectiveDue(c,task);
        boolean snoozed=due!=(task.dueAt==null?0:task.dueAt);
        ReminderScheduler.record(c,"Таймер сработал");
        if(post(c,task)){
            ReminderScheduler.delivered(c,task,due,intent.getBooleanExtra("recovered",false));
            if(!snoozed)ReminderScheduler.advanceRepeating(c,task);
            else ReminderScheduler.schedule(c,task);
        }
    }
    public static boolean post(Context c,Task task){
        ReminderScheduler.createChannel(c);
        if(!ReminderScheduler.notificationsEnabled(c)){
            ReminderScheduler.record(c,"Показ заблокирован: разреши уведомления приложения и канал «Напоминания»");return false;
        }
        Intent open=new Intent(c,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(c,ReminderScheduler.CHANNEL)
                .setSmallIcon(R.drawable.ic_notification).setColor(Color.rgb(73,112,223))
                .setContentTitle(task==null?"Уведомления работают":"Пора: "+task.title)
                .setContentText(task==null?"Теперь проверь напоминание через 10 секунд с выключенным экраном.":"Заверши задачу или отложи на 10 минут")
                .setStyle(new Notification.BigTextStyle().bigText(task==null?"Тестовое уведомление «Свои задачи».":task.title))
                .setAutoCancel(true).setContentIntent(content).setCategory(Notification.CATEGORY_REMINDER)
                .setWhen(System.currentTimeMillis()).setShowWhen(true);
        if(task!=null)builder
                .addAction(new Notification.Action.Builder(null,"Готово",ReminderScheduler.actionIntent(c,task.id,ReminderScheduler.ACTION_DONE)).build())
                .addAction(new Notification.Action.Builder(null,"Через 10 минут",ReminderScheduler.actionIntent(c,task.id,ReminderScheduler.ACTION_SNOOZE)).build());
        try{
            c.getSystemService(NotificationManager.class).notify(task==null?Integer.MAX_VALUE:ReminderScheduler.requestId(task.id),builder.build());
            return true;
        }catch(RuntimeException error){ReminderScheduler.record(c,"Ошибка уведомления: "+error.getClass().getSimpleName());return false;}
    }
}

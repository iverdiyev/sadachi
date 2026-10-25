package ru.svoizadachi.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.time.*;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx,Intent intent){
        if(intent==null)return;
        long id=intent.getLongExtra(ReminderScheduler.EXTRA_ID,-1);
        if(id<0)return;
        TaskStore store=new TaskStore(ctx);
        Task task=store.find(id);
        if(task==null)return;
        String action=intent.getAction();
        NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(ReminderScheduler.ACTION_DONE.equals(action)){
            if(task.repeat==TaskDateParser.NO_REPEAT){task.done=true;store.upsert(task);ReminderScheduler.cancel(ctx,id);}
            nm.cancel(ReminderScheduler.requestId(id));
            return;
        }
        if(ReminderScheduler.ACTION_SNOOZE.equals(action)){
            ReminderScheduler.snooze(ctx,id);
            nm.cancel(ReminderScheduler.requestId(id));
            return;
        }
        if(!ReminderScheduler.ACTION_NOTIFY.equals(action)||task.done)return;
        ReminderScheduler.createChannel(ctx);
        Intent open=new Intent(ctx,MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(ctx,ReminderScheduler.requestId(id),open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(ctx,ReminderScheduler.CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Пора выполнить задачу")
                .setContentText(task.title)
                .setStyle(new Notification.BigTextStyle().bigText(task.title))
                .setAutoCancel(true).setContentIntent(content)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setWhen(System.currentTimeMillis())
                .addAction(new Notification.Action.Builder(null,"Готово",
                        ReminderScheduler.actionIntent(ctx,id,ReminderScheduler.ACTION_DONE)).build())
                .addAction(new Notification.Action.Builder(null,"Через 10 минут",
                        ReminderScheduler.actionIntent(ctx,id,ReminderScheduler.ACTION_SNOOZE)).build())
                .build();
        try {nm.notify(ReminderScheduler.requestId(id),notification);}catch(SecurityException ignored){}
        if(!intent.getBooleanExtra("snoozed",false))ReminderScheduler.advanceRepeating(ctx,task);
    }
}

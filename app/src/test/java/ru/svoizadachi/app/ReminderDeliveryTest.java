package ru.svoizadachi.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.os.Looper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class ReminderDeliveryTest {
    private Application context;
    private NotificationManager notifications;
    private AlarmManager alarms;
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();notifications=context.getSystemService(NotificationManager.class);
        alarms=context.getSystemService(AlarmManager.class);
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        shadowOf(notifications).setNotificationsEnabled(true);
        ShadowAlarmManager.setCanScheduleExactAlarms(true);
        ReminderScheduler.createChannel(context);
    }
    private Task task(long due,int repeat){
        TaskStore store=new TaskStore(context);Task task=new Task(store.allocateId(),"Проверить чай",due,true,repeat,false);
        store.upsert(task);return task;
    }
    private void fire(Task task,long due){
        new ReminderReceiver().onReceive(context,new Intent(context,ReminderReceiver.class)
                .setAction(ReminderScheduler.ACTION_NOTIFY).putExtra(ReminderScheduler.EXTRA_ID,task.id).putExtra(ReminderScheduler.EXTRA_DUE,due));
    }
    @Test public void timedTaskUsesExactWakeupAlarm(){
        Task task=task(System.currentTimeMillis()+120000,0);ReminderScheduler.schedule(context,task);
        ShadowAlarmManager.ScheduledAlarm alarm=shadowOf(alarms).peekNextScheduledAlarm();
        assertNotNull(alarm);assertEquals(task.dueAt.longValue(),alarm.getTriggerAtMs());
        assertEquals(AlarmManager.RTC_WAKEUP,alarm.getType());assertTrue(alarm.isAllowWhileIdle());assertEquals(0,alarm.getWindowLengthMs());
    }
    @Test public void openingAppDoesNotReplaceExistingFutureAlarm(){
        Task task=task(System.currentTimeMillis()+120000,0);ReminderScheduler.schedule(context,task);
        ShadowAlarmManager.ScheduledAlarm original=shadowOf(alarms).peekNextScheduledAlarm();
        ReminderScheduler.scheduleAll(context);
        assertSame(original,shadowOf(alarms).peekNextScheduledAlarm());
        ReminderScheduler.scheduleAll(context,true);
        assertNotSame(original,shadowOf(alarms).peekNextScheduledAlarm());
    }
    @Test public void reliableModeUsesSystemAlarmClockOnlyWithExactPermission(){
        ReminderScheduler.reliableMode(context,true);
        Task task=task(System.currentTimeMillis()+120000,0);ReminderScheduler.schedule(context,task);
        assertNotNull(shadowOf(alarms).peekNextScheduledAlarm().getAlarmClockInfo());
        ShadowAlarmManager.setCanScheduleExactAlarms(false);ReminderScheduler.scheduleAll(context,true);
        assertNull(shadowOf(alarms).peekNextScheduledAlarm().getAlarmClockInfo());
    }
    @Test public void deniedExactAccessUsesInexactFallback(){
        ShadowAlarmManager.setCanScheduleExactAlarms(false);
        ReminderScheduler.schedule(context,task(System.currentTimeMillis()+120000,0));
        assertNotEquals(0,shadowOf(alarms).peekNextScheduledAlarm().getWindowLengthMs());
    }
    @Test public void receiverPostsNotificationAndDeduplicates(){
        Task task=task(System.currentTimeMillis()-1000,0);fire(task,task.dueAt);
        assertNotNull(shadowOf(notifications).getNotification(ReminderScheduler.requestId(task.id)));
        assertFalse(ReminderScheduler.shouldDeliver(context,task,task.dueAt));
        notifications.cancel(ReminderScheduler.requestId(task.id));fire(task,task.dueAt);
        assertEquals(0,shadowOf(notifications).size());
    }
    @Test public void blockedNotificationCanRecoverAfterPermissionGrant(){
        Task task=task(System.currentTimeMillis()-1000,0);
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);fire(task,task.dueAt);
        assertEquals(0,shadowOf(notifications).size());assertTrue(ReminderScheduler.diagnostics(context).contains("заблокирован"));
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ReminderScheduler.scheduleAll(context);shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(shadowOf(notifications).getNotification(ReminderScheduler.requestId(task.id)));
    }
    @Test public void recentMissedReminderRecoversButOldTaskDoesNotFlood(){
        Task recent=task(System.currentTimeMillis()-60000,0);Task old=task(System.currentTimeMillis()-3*86400000L,0);
        ReminderScheduler.scheduleAll(context);shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(shadowOf(notifications).getNotification(ReminderScheduler.requestId(recent.id)));
        assertNull(shadowOf(notifications).getNotification(ReminderScheduler.requestId(old.id)));
    }
    @Test public void staleBroadcastCannotPostEditedOrCompletedTask(){
        Task task=task(System.currentTimeMillis()-1000,0);long old=task.dueAt;
        task.dueAt=System.currentTimeMillis()+3600000;new TaskStore(context).upsert(task);fire(task,old);
        assertEquals(0,shadowOf(notifications).size());
        task.dueAt=old;task.done=true;new TaskStore(context).upsert(task);fire(task,old);
        assertEquals(0,shadowOf(notifications).size());
    }
    @Test public void snoozeSurvivesRescheduleAndCancelsOnCompletion(){
        Task task=task(System.currentTimeMillis()-1000,0);fire(task,task.dueAt);ReminderScheduler.snooze(context,task.id);
        long snoozed=ReminderScheduler.effectiveDue(context,task);assertTrue(snoozed>System.currentTimeMillis());
        ReminderScheduler.scheduleAll(context);
        assertEquals(snoozed,shadowOf(alarms).peekNextScheduledAlarm().getTriggerAtMs());
        ReminderScheduler.cancel(context,task.id);assertTrue(shadowOf(alarms).getScheduledAlarms().isEmpty());
        assertEquals(task.dueAt.longValue(),ReminderScheduler.effectiveDue(context,task));
    }
    @Test public void repeatDoneDoesNotSkipNextOccurrence(){
        Task task=task(System.currentTimeMillis()-1000,TaskDateParser.DAILY);fire(task,task.dueAt);
        long next=new TaskStore(context).find(task.id).dueAt;
        new ReminderReceiver().onReceive(context,new Intent(context,ReminderReceiver.class)
                .setAction(ReminderScheduler.ACTION_DONE).putExtra(ReminderScheduler.EXTRA_ID,task.id));
        assertEquals(next,new TaskStore(context).find(task.id).dueAt.longValue());
        assertNotNull(shadowOf(alarms).peekNextScheduledAlarm());
    }
    @Test public void packageUpdateRestoresAlarms(){
        Task task=task(System.currentTimeMillis()+60000,0);
        new BootReceiver().onReceive(context,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
        assertEquals(task.dueAt.longValue(),shadowOf(alarms).peekNextScheduledAlarm().getTriggerAtMs());
    }
    @Test public void snoozedRepeatRestoresNextRegularAlarm(){
        Task task=task(System.currentTimeMillis()-1000,TaskDateParser.DAILY);fire(task,task.dueAt);
        task=new TaskStore(context).find(task.id);long next=task.dueAt;
        long snoozed=System.currentTimeMillis()-500;
        context.getSharedPreferences("reminder_delivery_v2",Context.MODE_PRIVATE).edit().putLong("snooze_"+task.id,snoozed).commit();
        fire(task,snoozed);
        assertEquals(next,shadowOf(alarms).peekNextScheduledAlarm().getTriggerAtMs());
    }
    @Test public void snoozedOneOffDoesNotRestoreOriginalNotification(){
        Task task=task(System.currentTimeMillis()-1000,0);fire(task,task.dueAt);
        long snoozed=System.currentTimeMillis()-500;
        context.getSharedPreferences("reminder_delivery_v2",Context.MODE_PRIVATE).edit().putLong("snooze_"+task.id,snoozed).commit();
        fire(task,snoozed);
        assertFalse(ReminderScheduler.shouldDeliver(context,task,task.dueAt));
        assertTrue(shadowOf(alarms).getScheduledAlarms().isEmpty());
    }
}

package ru.svoizadachi.app;

import android.app.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class DateTimePicker {
    public interface Selection {void selected(Long due,boolean hasTime);}
    private DateTimePicker(){}
    public static String format(Long due,boolean time){
        return due==null?"Выбрать дату":Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern(time?"d MMM yyyy · HH:mm":"d MMM yyyy · без времени",new Locale("ru")));
    }
    public static void show(Activity activity,Long due,Selection callback){
        ZonedDateTime initial=due==null?ZonedDateTime.now().plusHours(1):Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault());
        DatePickerDialog dialog=new DatePickerDialog(activity,(picker,year,month,day)->{
            LocalDate date=LocalDate.of(year,month+1,day);
            TimePickerDialog time=new TimePickerDialog(activity,(view,hour,minute)->
                    callback.selected(date.atTime(hour,minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),true),
                    initial.getHour(),initial.getMinute(),true);
            time.setTitle("Время напоминания");time.show();RoundedUi.dialog(time);
        },initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth());
        dialog.setTitle("Дата задачи");
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL,"Без времени",(d,w)->{
            android.widget.DatePicker picker=dialog.getDatePicker();
            picker.clearFocus();
            callback.selected(LocalDate.of(picker.getYear(),picker.getMonth()+1,picker.getDayOfMonth())
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),false);
        });dialog.show();RoundedUi.dialog(dialog);
    }
}

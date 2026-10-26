import ru.svoizadachi.app.TaskDateParser;
import java.time.*;
import java.util.*;
public class ParserTest {
    static int checked = 0;
    static void check(boolean b,String msg){checked++;if(!b)throw new AssertionError(msg);}
    static void has(String s, String title, String iso, boolean time, int repeat, ZonedDateTime now){
        TaskDateParser.Result p=TaskDateParser.parse(s,now);
        check(p.title.equals(title),"title in '"+s+"': '"+p.title+"' vs '"+title+"'");
        check(p.hasTime==time,"hasTime in '"+s+"'");
        check(p.repeat==repeat,"repeat in '"+s+"'");
        if(iso==null)check(p.dueAt==null,"expected no date '"+s+"'");
        else {check(p.dueAt!=null,"expected date '"+s+"'");check(Instant.ofEpochMilli(p.dueAt).atZone(now.getZone()).toLocalDateTime().toString().equals(iso),"date for '"+s+"' -> "+Instant.ofEpochMilli(p.dueAt).atZone(now.getZone()).toLocalDateTime());}
    }
    public static void main(String[] args){
        ZonedDateTime now=ZonedDateTime.of(2026,10,24,18,30,0,0,ZoneId.of("Europe/Amsterdam"));
        has("Домашка завтра в 18:00","Домашка","2026-10-25T18:00",true,0,now);
        has("Позвонить маме послезавтра в 9 утра","Позвонить маме","2026-10-26T09:00",true,0,now);
        has("Купить хлеб сегодня","Купить хлеб","2026-10-24T00:00",false,0,now);
        has("Сделать проект через 3 дня в 21:30","Сделать проект","2026-10-27T21:30",true,0,now);
        has("Сдать зачёт 5 ноября в 14:00","Сдать зачёт","2026-11-05T14:00",true,0,now);
        has("Встреча 01.11 в 10.30","Встреча","2026-11-01T10:30",true,0,now);
        has("Дз в понедельник в 8","Дз","2026-10-26T08:00",true,0,now);
        has("Вынести мусор каждый день в 9 вечера","Вынести мусор","2026-10-24T21:00",true,1,now);
        has("Вынести мусор каждую неделю в 19:00","Вынести мусор","2026-10-24T19:00",true,2,now);
        has("Оплатить связь в 13:00","Оплатить связь","2026-10-25T13:00",true,0,now);
        has("Просто задача","Просто задача",null,false,0,now);
        has("Проверить чай через 2 минуты","Проверить чай","2026-10-24T18:32",true,0,now);
        has("через 2 минуты","Напоминание","2026-10-24T18:32",true,0,now);
        has("Перерыв через час","Перерыв","2026-10-24T19:30",true,0,now);
        has("Позвонить через два часа","Позвонить","2026-10-24T20:30",true,0,now);
        has("Чай через одну минуту","Чай","2026-10-24T18:31",true,0,now);
        has("Таймер через 30 секунд","Таймер","2026-10-24T18:30:30",true,0,now);
        has("Вода через 5 мин","Вода","2026-10-24T18:35",true,0,now);
        has("Завтра позвонить маме в 9 утра","позвонить маме","2026-10-25T09:00",true,0,now);
        has("Позвонить завтра маме в 9 утра","Позвонить маме","2026-10-25T09:00",true,0,now);
        has("Через пять минут проверить чай","проверить чай","2026-10-24T18:35",true,0,now);
        has("Проверить через час и 30 минут духовку","Проверить духовку","2026-10-24T20:00",true,0,now);
        has("Через полчаса сделать перерыв","сделать перерыв","2026-10-24T19:00",true,0,now);
        has("Повторить через две недели материал","Повторить материал","2026-11-07T00:00",false,0,now);
        has("Встреча 5 ноября 2027 в 14:00","Встреча","2027-11-05T14:00",true,0,now);
        has("Встреча в 10.11 завтра","Встреча","2026-10-25T10:11",true,0,now);
        has("позвонить завтра в девять утра","позвонить","2026-10-25T09:00",true,0,now);
        has("позвонить завтра в девять тридцать","позвонить","2026-10-25T09:30",true,0,now);
        has("чай через пятнадцать минут","чай","2026-10-24T18:45",true,0,now);
        has("перерыв через двадцать пять минут","перерыв","2026-10-24T18:55",true,0,now);
        has("План\nОтправить завтра в 18:00 документы","План Отправить документы","2026-10-25T18:00",true,0,now);
        ZonedDateTime late=now.withHour(23).withMinute(59).withSecond(20);
        TaskDateParser.Result midnight=TaskDateParser.parse("Сон через 2 минуты",late);
        check(midnight.dueAt==late.plusMinutes(2).toInstant().toEpochMilli(),"relative duration across midnight preserves seconds");
        check(TaskDateParser.parse("Таймер через 0 минут",now).dueAt==null,"zero duration is not scheduled");
        // Simulate typing each character: the original app crashed on the first keystroke.
        String[] typed = {
            "Проверить через час и 30 минут духовку", "Через пять минут проверить чай", "Повторить через две недели материал",
            "Чай через 2 минуты", "Перерыв через час", "Таймер через 30 секунд",
            "Домашка завтра в 18:00",
            "Позвонить маме послезавтра в 9 утра",
            "Сдать зачёт 5 ноября в 14:00",
            "Купить молоко сегодня в 20:15",
            "Отправить письмо каждую неделю в 19:00",
            "Проверить счёт 01.11 в 10.30",
            "👋 Написать другу в пятницу в 8 вечера",
            "просто русский текст без даты"
        };
        for (String phrase : typed) {
            for (int len=0; len<=phrase.length(); len++) {
                TaskDateParser.Result preview = TaskDateParser.parse(phrase.substring(0,len),now);
                check(preview!=null && preview.title!=null,"crash for prefix of '"+phrase+"' length "+len);
            }
        }
        check(TaskDateParser.parse(null,now).dueAt==null,"null should be safe");
        System.out.println("OK: "+checked+" assertions (including typed prefixes)");
    }
}

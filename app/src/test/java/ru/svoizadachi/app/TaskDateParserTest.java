package ru.svoizadachi.app;

import org.junit.Test;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static org.junit.Assert.*;

public class TaskDateParserTest {
    private final ZonedDateTime now = ZonedDateTime.of(2026, 10, 24, 18, 30, 0, 0, ZoneId.of("Europe/Moscow"));

    @Test public void relativeMinutesKeepSecondsAndTitle() {
        ZonedDateTime clock=now.withSecond(37);
        TaskDateParser.Result result=TaskDateParser.parse("Проверить чай через 2 минуты",clock);
        assertEquals("Проверить чай",result.title);
        assertTrue(result.hasTime);
        assertEquals(Long.valueOf(clock.plusMinutes(2).toInstant().toEpochMilli()),result.dueAt);
    }

    @Test public void relativePhraseWithoutTitleCreatesReminder() {
        TaskDateParser.Result result=TaskDateParser.parse("через 2 минуты",now);
        assertEquals("Напоминание",result.title);
        assertEquals(Long.valueOf(now.plusMinutes(2).toInstant().toEpochMilli()),result.dueAt);
    }

    @Test public void relativeHoursAndSeconds() {
        assertEquals(Long.valueOf(now.plusHours(1).toInstant().toEpochMilli()),TaskDateParser.parse("Перерыв через час",now).dueAt);
        assertEquals(Long.valueOf(now.plusHours(2).toInstant().toEpochMilli()),TaskDateParser.parse("Перерыв через два часа",now).dueAt);
        assertEquals(Long.valueOf(now.plusSeconds(30).toInstant().toEpochMilli()),TaskDateParser.parse("Таймер через 30 секунд",now).dueAt);
        assertNull(TaskDateParser.parse("через 0 минут",now).dueAt);
    }

    @Test public void relativeTimeCrossesMidnightAndDaylightSaving() {
        ZonedDateTime late=now.withHour(23).withMinute(59).withSecond(40);
        assertEquals(Long.valueOf(late.plusMinutes(2).toInstant().toEpochMilli()),TaskDateParser.parse("Вода через 2 минуты",late).dueAt);
        ZonedDateTime dst=ZonedDateTime.of(2026,10,25,2,59,0,0,ZoneId.of("Europe/Amsterdam"));
        assertEquals(Long.valueOf(dst.toInstant().toEpochMilli()+120000),TaskDateParser.parse("Вода через 2 минуты",dst).dueAt);
    }

    @Test public void russianTomorrowAndTime() {
        TaskDateParser.Result result = TaskDateParser.parse("Домашка завтра в 18:00", now);
        assertEquals("Домашка", result.title);
        assertTrue(result.hasTime);
        assertNotNull(result.dueAt);
        assertEquals(25, java.time.Instant.ofEpochMilli(result.dueAt).atZone(now.getZone()).getDayOfMonth());
    }

    @Test public void everyTypedCharacterMustNotCrash() {
        String[] examples = {
                "Домашка завтра в 18:00", "Завтра", "Купить продукты послезавтра",
                "Написать маме каждый день в 9:00", "Зачёт 5 ноября в 14:00",
                "Встреча в пятницу", "Обновить пароль в 19:30",
                "👋 Позвонить другу в 16:00", "Чай через 2 минуты", "Отдохнуть через час", "через 30 секунд"
        };
        for (String example : examples) {
            for (int i = 0; i <= example.length(); i++) {
                TaskDateParser.Result preview = TaskDateParser.parse(example.substring(0, i), now);
                assertNotNull("Не разобрал начало ввода: " + example.substring(0, i), preview);
                assertNotNull(preview.title);
            }
        }
    }
}

package ru.svoizadachi.app;

import org.junit.Test;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static org.junit.Assert.*;

public class TaskDateParserTest {
    private final ZonedDateTime now = ZonedDateTime.of(2026, 10, 24, 18, 30, 0, 0, ZoneId.of("Europe/Moscow"));

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
                "👋 Позвонить другу в 16:00"
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

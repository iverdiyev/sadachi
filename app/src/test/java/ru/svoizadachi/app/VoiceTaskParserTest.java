package ru.svoizadachi.app;

import org.junit.Test;
import java.time.*;
import java.util.List;
import static org.junit.Assert.*;

public class VoiceTaskParserTest {
    private final ZonedDateTime now=ZonedDateTime.of(2026,10,29,12,0,0,0,ZoneId.of("Europe/Amsterdam"));
    @Test public void separatesThreeTasksWithoutInventingSaturdayTime(){
        List<VoiceTaskParser.Candidate> tasks=VoiceTaskParser.parse("Завтра в 10 утра сдать отчёт, в субботу купить продукты, и в пятницу в 19:00 тренировка",now);
        assertEquals(3,tasks.size());assertEquals("сдать отчёт",tasks.get(0).title);
        assertEquals(LocalTime.of(10,0),Instant.ofEpochMilli(tasks.get(0).dueAt).atZone(now.getZone()).toLocalTime());
        assertEquals("купить продукты",tasks.get(1).title);assertFalse(tasks.get(1).hasTime);
        assertEquals("тренировка",tasks.get(2).title);assertTrue(tasks.get(2).hasTime);
    }
    @Test public void keepsAmbiguousTimeAndUnsupportedRepeatUnscheduled(){
        List<VoiceTaskParser.Candidate> tasks=VoiceTaskParser.parse("Завтра в пять позвони Саше, и каждый понедельник в 19:00 тренировка",now);
        assertEquals(2,tasks.size());for(VoiceTaskParser.Candidate task:tasks){assertTrue(task.needsReview);assertNull(task.dueAt);assertEquals(task.sourceText,task.title);}
    }
    @Test public void doesNotSplitShoppingListOrEmptyInput(){
        assertEquals(1,VoiceTaskParser.parse("Купить хлеб и молоко",now).size());
        assertTrue(VoiceTaskParser.parse("  ",now).isEmpty());
        assertEquals(1,VoiceTaskParser.parse("Напомни завтра… нет, послезавтра",now).size());
    }
    @Test public void oneHundredDeterministicRussianPhraseCombinationsKeepDateAndTitle(){
        String[] titles={"Купить хлеб","Сдать отчёт","Позвонить маме","Отправить документы","Заплатить за интернет",
                "Навестить врача","Проверить почту","Забрать посылку","Подготовить урок","Купить продукты"};
        String[] dates={"сегодня","завтра","послезавтра","через один день","через два дня",
                "через три дня","через четыре дня","через пять дней","через шесть дней","через неделю"};
        for(String title:titles)for(int i=0;i<dates.length;i++){
            List<VoiceTaskParser.Candidate> candidates=VoiceTaskParser.parse(title+" "+dates[i],now);
            assertEquals(title+" "+dates[i],1,candidates.size());
            VoiceTaskParser.Candidate candidate=candidates.get(0);
            assertEquals(title,candidate.title);assertFalse(candidate.needsReview);
            assertNotNull(candidate.dueAt);
            int offset=i<3?i:i<9?i-2:7;
            assertEquals(now.toLocalDate().plusDays(offset),Instant.ofEpochMilli(candidate.dueAt).atZone(now.getZone()).toLocalDate());
        }
    }
}

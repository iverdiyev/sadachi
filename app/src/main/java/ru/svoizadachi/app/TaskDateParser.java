package ru.svoizadachi.app;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java, offline Russian-language date parser. No network, no AI subscription. */
public final class TaskDateParser {
    public static final int NO_REPEAT = 0;
    public static final int DAILY = 1;
    public static final int WEEKLY = 2;
    // Explicit Unicode word boundaries work on both Android and desktop Java.
    // They avoid the unsupported Android UNICODE_CHARACTER_CLASS flag.
    private static final String BEFORE_WORD = "(?<![\\p{L}\\p{N}_])";
    private static final String AFTER_WORD = "(?![\\p{L}\\p{N}_])";
    private static final Map<String,Integer> MONTHS = new HashMap<>();
    private static final Map<String,DayOfWeek> DAYS = new LinkedHashMap<>();
    static {
        String[][] names = {
            {"января","январь","янв"}, {"февраля","февраль","фев"},
            {"марта","март","мар"}, {"апреля","апрель","апр"},
            {"мая","май"}, {"июня","июнь"}, {"июля","июль"},
            {"августа","август","авг"}, {"сентября","сентябрь","сен","сент"},
            {"октября","октябрь","окт"}, {"ноября","ноябрь","ноя"},
            {"декабря","декабрь","дек"}
        };
        for (int i=0;i<names.length;i++) for (String s : names[i]) MONTHS.put(s,i+1);
        DAYS.put("понедельник", DayOfWeek.MONDAY); DAYS.put("понедельника", DayOfWeek.MONDAY);
        DAYS.put("вторник", DayOfWeek.TUESDAY); DAYS.put("вторника", DayOfWeek.TUESDAY);
        DAYS.put("среду", DayOfWeek.WEDNESDAY); DAYS.put("среда", DayOfWeek.WEDNESDAY);
        DAYS.put("четверг", DayOfWeek.THURSDAY); DAYS.put("четверга", DayOfWeek.THURSDAY);
        DAYS.put("пятницу", DayOfWeek.FRIDAY); DAYS.put("пятница", DayOfWeek.FRIDAY);
        DAYS.put("субботу", DayOfWeek.SATURDAY); DAYS.put("суббота", DayOfWeek.SATURDAY);
        DAYS.put("воскресенье", DayOfWeek.SUNDAY);
    }
    public static final class Result {
        public final String title;
        public final Long dueAt;
        public final boolean hasTime;
        public final int repeat;
        public Result(String title, Long dueAt, boolean hasTime, int repeat) {
            this.title=title;this.dueAt=dueAt;this.hasTime=hasTime;this.repeat=repeat;
        }
    }
    private static final class MatchResult {
        final String remaining; final Matcher match;
        MatchResult(String remaining, Matcher match){ this.remaining=remaining;this.match=match; }
    }
    // Android always uses Unicode character classes. Unlike desktop Java, it does
    // NOT support Pattern.UNICODE_CHARACTER_CLASS or the inline (?U) modifier.
    // Enabling either could crash the app as soon as the user types a character.
    private static MatchResult extract(String input,String pattern) {
        Matcher m=Pattern.compile(pattern,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(input);
        if (!m.find()) return null;
        return new MatchResult((input.substring(0,m.start()) + " " + input.substring(m.end())).trim(),m);
    }
    public static Result parse(String input) { return parse(input, ZonedDateTime.now()); }
    public static Result parse(String input, ZonedDateTime now) {
        String work=input==null?"":input.trim();
        if(work.isEmpty()) return new Result("",null,false,NO_REPEAT);
        int repeat=NO_REPEAT;
        MatchResult rep=extract(work,BEFORE_WORD+"(?:каждый\\s+день|ежедневно)"+AFTER_WORD);
        if (rep!=null){repeat=DAILY;work=rep.remaining;}
        else {
            rep=extract(work,BEFORE_WORD+"(?:каждую\\s+неделю|еженедельно)"+AFTER_WORD);
            if(rep!=null){repeat=WEEKLY;work=rep.remaining;}
        }
        LocalDate date = null;
        MatchResult m;
        m=extract(work,BEFORE_WORD+"(послезавтра|завтра|сегодня)"+AFTER_WORD);
        if(m!=null){
            String found=m.match.group(1).toLowerCase(new Locale("ru"));
            date=now.toLocalDate().plusDays(found.equals("послезавтра")?2:found.equals("завтра")?1:0);
            work=m.remaining;
        }
        if(date==null){
            m=extract(work,BEFORE_WORD+"через\\s+(\\d{1,3})\\s+(?:день|дня|дней|сутки|суток)"+AFTER_WORD);
            if(m!=null){date=now.toLocalDate().plusDays(Integer.parseInt(m.match.group(1)));work=m.remaining;}
        }
        if(date==null){
            m=extract(work,"(?<!\\d)(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2,4}))?(?!\\d)");
            if(m!=null){
                try {
                    int day=Integer.parseInt(m.match.group(1));int month=Integer.parseInt(m.match.group(2));
                    int year=m.match.group(3)==null?now.getYear():Integer.parseInt(m.match.group(3));
                    if(year<100)year+=2000;
                    LocalDate test=LocalDate.of(year,month,day);
                    if(m.match.group(3)==null && test.isBefore(now.toLocalDate()))test=test.plusYears(1);
                    date=test;work=m.remaining;
                }catch(Exception ignored){}
            }
        }
        if(date==null){
            m=extract(work,BEFORE_WORD+"(\\d{1,2})\\s+(января|январь|янв|февраля|февраль|фев|марта|март|мар|апреля|апрель|апр|мая|май|июня|июнь|июля|июль|августа|август|авг|сентября|сентябрь|сент|сен|октября|октябрь|окт|ноября|ноябрь|ноя|декабря|декабрь|дек)"+AFTER_WORD);
            if(m!=null){
                try{
                    int day=Integer.parseInt(m.match.group(1));int month=MONTHS.get(m.match.group(2).toLowerCase(new Locale("ru")));
                    LocalDate test=LocalDate.of(now.getYear(),month,day);
                    if(test.isBefore(now.toLocalDate()))test=test.plusYears(1);
                    date=test;work=m.remaining;
                }catch(Exception ignored){}
            }
        }
        if(date==null){
            m=extract(work,BEFORE_WORD+"(?:в|во|на)\\s+(понедельник|понедельника|вторник|вторника|среду|среда|четверг|четверга|пятницу|пятница|субботу|суббота|воскресенье)"+AFTER_WORD);
            if(m!=null){
                DayOfWeek day=DAYS.get(m.match.group(1).toLowerCase(new Locale("ru")));
                date=now.toLocalDate().with(TemporalAdjusters.nextOrSame(day));
                work=m.remaining;
            }
        }
        LocalTime time=null;
        m=extract(work,"(?<!\\d)(?:"+BEFORE_WORD+"в\\s+)?([01]?\\d|2[0-3])[:.]([0-5]\\d)(?!\\d)");
        if(m!=null){
            time=LocalTime.of(Integer.parseInt(m.match.group(1)),Integer.parseInt(m.match.group(2)));
            work=m.remaining;
        }
        if(time==null){
            m=extract(work,BEFORE_WORD+"в\\s+([01]?\\d|2[0-3])(?:\\s*час(?:а|ов)?)?\\s*(утра|дня|вечера|ночи)?"+AFTER_WORD);
            if(m!=null){
                int h=Integer.parseInt(m.match.group(1));
                String part=m.match.group(2);
                if(part!=null){
                    part=part.toLowerCase(new Locale("ru"));
                    if((part.equals("дня")||part.equals("вечера"))&&h<12)h+=12;
                    if(part.equals("ночи")&&h==12)h=0;
                }
                if(h<24){time=LocalTime.of(h,0);work=m.remaining;}
            }
        }
        // If only a time was specified, pick today, or tomorrow if already past.
        if (date==null && time!=null) {
            date=now.toLocalDate();
            if(!LocalDateTime.of(date,time).atZone(now.getZone()).isAfter(now))date=date.plusDays(1);
        }
        if(date==null && repeat!=NO_REPEAT) date=now.toLocalDate();
        if(date!=null && time!=null && date.equals(now.toLocalDate()) && repeat!=NO_REPEAT
                && !LocalDateTime.of(date,time).atZone(now.getZone()).isAfter(now))
            date=date.plusDays(repeat==DAILY?1:7);
        Long due=null;
        if(date!=null)due=LocalDateTime.of(date,time==null?LocalTime.MIDNIGHT:time)
                .atZone(now.getZone()).toInstant().toEpochMilli();
        String title=work.replaceAll("\\s+"," ").replaceAll("^[,.;: \\t]+|[,.;: \\t]+$","").trim();
        if(title.isEmpty())title=input.trim();
        return new Result(title,due,time!=null,repeat);
    }
}

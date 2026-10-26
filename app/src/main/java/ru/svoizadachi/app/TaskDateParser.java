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
    private static long number(String amount){
        if(amount==null)return 1;
        String value=amount.toLowerCase(new Locale("ru"));
        if(value.contains(" ")){long total=0;for(String part:value.trim().split("\\s+"))total+=number(part);return total;}
        switch(value){
            case "ноль":return 0;case "один":case "одна":case "одну":return 1;case "два":case "две":return 2;case "три":return 3;
            case "четыре":return 4;case "пять":return 5;case "шесть":return 6;case "семь":return 7;
            case "восемь":return 8;case "девять":return 9;case "десять":return 10;
            case "одиннадцать":return 11;case "двенадцать":return 12;case "тринадцать":return 13;case "четырнадцать":return 14;
            case "пятнадцать":return 15;case "шестнадцать":return 16;case "семнадцать":return 17;case "восемнадцать":return 18;case "девятнадцать":return 19;
            case "двадцать":return 20;case "тридцать":return 30;case "сорок":return 40;case "пятьдесят":return 50;
            default:return Long.parseLong(value);
        }
    }
    private static final String ONES="(?:ноль|одну|одна|один|две|два|три|четыре|пять|шесть|семь|восемь|девять)";
    private static final String WORD_NUMBER="(?:(?:двадцать|тридцать|сорок|пятьдесят)(?:\\s+"+ONES+")?|десять|одиннадцать|двенадцать|тринадцать|четырнадцать|пятнадцать|шестнадцать|семнадцать|восемнадцать|девятнадцать|"+ONES+")";
    private static final String AMOUNT="(?:\\d{1,5}|"+WORD_NUMBER+")";
    public static Result parse(String input, ZonedDateTime now) {
        String work=input==null?"":input.trim();
        if(work.isEmpty()) return new Result("",null,false,NO_REPEAT);
        int repeat=NO_REPEAT;
        // Relative durations are instants, not calendar dates. Keep seconds and
        // use the supplied clock so a two-minute reminder is exactly two minutes.
        MatchResult half=extract(work,BEFORE_WORD+"через\\s+(полчаса|полтора\\s+часа)"+AFTER_WORD);
        if(half!=null){
            String title=half.remaining.replaceAll("\\s+"," ").trim();if(title.isEmpty())title="Напоминание";
            return new Result(title,now.plusMinutes(half.match.group(1).equalsIgnoreCase("полчаса")?30:90).toInstant().toEpochMilli(),true,NO_REPEAT);
        }
        MatchResult relative=extract(work,BEFORE_WORD+"через\\s+(?:("+AMOUNT+")\\s+)?(минуту|минуты|минут|мин\\.?|часа|часов|час|секунду|секунды|секунд|сек\\.?)"+AFTER_WORD
                +"(?:\\s+(?:и\\s+)?("+AMOUNT+")\\s+(минуту|минуты|минут|мин\\.?|секунду|секунды|секунд|сек\\.?)"+AFTER_WORD+")?");
        if(relative!=null){
            String amount=relative.match.group(1);
            long count=number(amount);
            if(count>0){
                String unit=relative.match.group(2).toLowerCase(Locale.ROOT);
                long seconds=count*(unit.startsWith("час")?3600:unit.startsWith("мин")?60:1);
                if(relative.match.group(3)!=null)seconds+=number(relative.match.group(3))*(relative.match.group(4).toLowerCase(Locale.ROOT).startsWith("мин")?60:1);
                String title=relative.remaining.replaceAll("\\s+"," ").replaceAll("^[,.;: \\t]+|[,.;: \\t]+$","").trim();
                if(title.isEmpty())title="Напоминание";
                return new Result(title,now.plusSeconds(seconds).toInstant().toEpochMilli(),true,NO_REPEAT);
            }
        }
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
            m=extract(work,BEFORE_WORD+"через\\s+(?:("+AMOUNT+")\\s+)?(день|дня|дней|сутки|суток|неделю|недели|недель)"+AFTER_WORD);
            if(m!=null&&number(m.match.group(1))>0){date=now.toLocalDate().plusDays(number(m.match.group(1))*(m.match.group(2).toLowerCase(Locale.ROOT).startsWith("недел")?7:1));work=m.remaining;}
        }
        if(date==null){
            m=extract(work,"(?<![\\d:])(?<![вВ] )(?<![вВ]  )(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2,4}))?(?![\\d:])");
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
            m=extract(work,BEFORE_WORD+"(\\d{1,2})\\s+(января|январь|янв|февраля|февраль|фев|марта|март|мар|апреля|апрель|апр|мая|май|июня|июнь|июля|июль|августа|август|авг|сентября|сентябрь|сент|сен|октября|октябрь|окт|ноября|ноябрь|ноя|декабря|декабрь|дек)"+AFTER_WORD+"(?:\\s+(20\\d{2})"+AFTER_WORD+")?");
            if(m!=null){
                try{
                    int day=Integer.parseInt(m.match.group(1));int month=MONTHS.get(m.match.group(2).toLowerCase(new Locale("ru")));
                    LocalDate test=LocalDate.of(m.match.group(3)==null?now.getYear():Integer.parseInt(m.match.group(3)),month,day);
                    if(m.match.group(3)==null&&test.isBefore(now.toLocalDate()))test=test.plusYears(1);
                    date=test;work=m.remaining;
                }catch(Exception ignored){}
            }
        }
        if(date==null){
            m=extract(work,BEFORE_WORD+"(?:(?:в|во|на)\\s+)?(понедельник|понедельника|вторник|вторника|среду|среда|четверг|четверга|пятницу|пятница|субботу|суббота|воскресенье)"+AFTER_WORD);
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
            m=extract(work,BEFORE_WORD+"в\\s+("+WORD_NUMBER+")"+AFTER_WORD+"(?:\\s*час(?:а|ов)?)?(?:\\s+("+WORD_NUMBER+")"+AFTER_WORD+"(?:\\s*минут(?:у|ы)?)?)?(?:\\s+(утра|дня|вечера|ночи))?"+AFTER_WORD);
            if(m!=null){
                int hour=(int)number(m.match.group(1)),minute=m.match.group(2)==null?0:(int)number(m.match.group(2));String part=m.match.group(3);
                if(part!=null){part=part.toLowerCase(Locale.ROOT);if((part.equals("дня")||part.equals("вечера"))&&hour<12)hour+=12;if(part.equals("ночи")&&hour==12)hour=0;}
                if(hour<24&&minute<60){time=LocalTime.of(hour,minute);work=m.remaining;}
            }
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

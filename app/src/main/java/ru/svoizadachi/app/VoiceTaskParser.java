package ru.svoizadachi.app;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative offline extraction. Uncertain time/recurrence stays in the source text for review. */
public final class VoiceTaskParser {
    private static final Pattern NEXT_TASK=Pattern.compile("(?:[,;]|\\s+и\\s+)(?=\\s*(?:и\\s+)?(?:сегодня|завтра|послезавтра|в\\s+(?:понедельник|вторник|среду|четверг|пятницу|субботу|воскресенье)|каждый|каждую|через\\s+\\S+\\s+(?:день|дня|дней|неделю|недели)))",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern UNSUPPORTED=Pattern.compile("(?:следующ(?:ей|ую|ий|ую)|после\\s+обеда|вечером|утром|по\\s+будням|раз\\s+в\\s+две\\s+недели|каждый\\s+(?:понедельник|вторник|четверг)|каждую\\s+(?:среду|пятницу|субботу|неделю\\s+и))",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern UNCLEAR_HOUR=Pattern.compile("(?<![\\p{L}\\p{N}])в\\s+(?:[0-9]{1,2}|один|два|три|четыре|пять|шесть|семь|восемь|девять|десять|одиннадцать|двенадцать)(?![:.\\d])(?:\\s+час(?:а|ов)?)?(?!\\s+(?:утра|дня|вечера|ночи))",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern CORRECTION=Pattern.compile("(?<![\\p{L}])(?:нет|не\\s+создавай|хотя\\s+нет)(?![\\p{L}])",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);

    private VoiceTaskParser(){}
    public static final class Candidate {
        public final String sourceText,title;
        public final Long dueAt;
        public final boolean hasTime,needsReview;
        public final int repeat;
        Candidate(String sourceText,String title,Long dueAt,boolean hasTime,int repeat,boolean needsReview){
            this.sourceText=sourceText;this.title=title;this.dueAt=dueAt;this.hasTime=hasTime;this.repeat=repeat;this.needsReview=needsReview;
        }
    }
    public static List<Candidate> parse(String transcript,ZonedDateTime now){
        List<Candidate> result=new ArrayList<>();
        if(transcript==null||transcript.trim().isEmpty())return result;
        if(CORRECTION.matcher(transcript).find()){add(result,transcript,now);return result;}
        Matcher boundaries=NEXT_TASK.matcher(transcript);int start=0;
        while(boundaries.find()){add(result,transcript.substring(start,boundaries.start()),now);start=boundaries.end();}
        add(result,transcript.substring(start),now);return result;
    }
    private static void add(List<Candidate> result,String piece,ZonedDateTime now){
        String source=piece.trim().replaceAll("^(?i:и)\\s+","").replaceAll("^[,;\\s]+|[,;\\s]+$","").trim();
        if(source.isEmpty())return;
        boolean unclear=UNSUPPORTED.matcher(source).find()||UNCLEAR_HOUR.matcher(source).find()
                ||CORRECTION.matcher(source).find();
        if(unclear){result.add(new Candidate(source,source,null,false,0,true));return;}
        TaskDateParser.Result parsed=TaskDateParser.parse(source,now);
        result.add(new Candidate(source,parsed.title,parsed.dueAt,parsed.hasTime,parsed.repeat,false));
    }
}

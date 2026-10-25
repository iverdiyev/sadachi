package ru.svoizadachi.app;

import org.json.JSONException;
import org.json.JSONObject;

public final class Task {
    public long id;
    public String title;
    public Long dueAt;
    public boolean hasTime;
    public boolean done;
    public int repeat;

    public Task(long id, String title, Long dueAt, boolean hasTime, int repeat, boolean done) {
        this.id=id; this.title=title; this.dueAt=dueAt;
        this.hasTime=hasTime; this.repeat=repeat; this.done=done;
    }
    public JSONObject json() throws JSONException {
        JSONObject j=new JSONObject();
        j.put("id",id);j.put("title",title);j.put("dueAt",dueAt==null?JSONObject.NULL:dueAt);
        j.put("hasTime",hasTime);j.put("repeat",repeat);j.put("done",done);
        return j;
    }
    public static Task fromJson(JSONObject j) {
        return new Task(j.optLong("id"),j.optString("title"),j.isNull("dueAt")?null:j.optLong("dueAt"),
                j.optBoolean("hasTime"),j.optInt("repeat"),j.optBoolean("done"));
    }
}

package ru.svoizadachi.app;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

public final class Task {
    public long id;
    public String title;
    public Long dueAt;
    public boolean hasTime;
    public boolean done;
    public int repeat;
    public String description="";
    public String folder="";
    public final List<Item> items=new ArrayList<>();

    public static final class Item {
        public String text;
        public boolean done;
        public Item(String text,boolean done){this.text=text;this.done=done;}
    }
    public int completedItems(){int count=0;for(Item item:items)if(item.done)count++;return count;}

    public Task(long id, String title, Long dueAt, boolean hasTime, int repeat, boolean done) {
        this.id=id; this.title=title; this.dueAt=dueAt;
        this.hasTime=hasTime; this.repeat=repeat; this.done=done;
    }
    public JSONObject json() throws JSONException {
        JSONObject j=new JSONObject();
        j.put("id",id);j.put("title",title);j.put("dueAt",dueAt==null?JSONObject.NULL:dueAt);
        j.put("hasTime",hasTime);j.put("repeat",repeat);j.put("done",done);
        j.put("description",description);j.put("folder",folder);
        JSONArray checklist=new JSONArray();
        for(Item item:items){JSONObject value=new JSONObject();value.put("text",item.text);value.put("done",item.done);checklist.put(value);}
        j.put("items",checklist);
        return j;
    }
    public static Task fromJson(JSONObject j) {
        Task task=new Task(j.optLong("id"),j.optString("title"),j.isNull("dueAt")?null:j.optLong("dueAt"),
                j.optBoolean("hasTime"),j.optInt("repeat"),j.optBoolean("done"));
        task.description=j.optString("description","");task.folder=j.optString("folder","");
        JSONArray checklist=j.optJSONArray("items");
        if(checklist!=null)for(int i=0;i<checklist.length();i++){
            JSONObject item=checklist.optJSONObject(i);if(item!=null)task.items.add(new Item(item.optString("text"),item.optBoolean("done")));
        }
        return task;
    }
}

package ru.svoizadachi.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class TaskStore {
    private final SharedPreferences pref;
    public TaskStore(Context ctx){pref=ctx.getSharedPreferences("tasks_local_v1",Context.MODE_PRIVATE);}
    public synchronized List<Task> all(){
        List<Task> list=new ArrayList<>();
        try {
            JSONArray arr=new JSONArray(pref.getString("tasks","[]"));
            for(int i=0;i<arr.length();i++){
                JSONObject j=arr.optJSONObject(i);
                if(j!=null)list.add(Task.fromJson(j));
            }
        } catch (JSONException ignored) { }
        return list;
    }
    public synchronized void save(List<Task> list){
        JSONArray arr=new JSONArray();
        for(Task t:list){try {arr.put(t.json());}catch(JSONException ignored){}}
        pref.edit().putString("tasks",arr.toString()).commit();
    }
    public Task find(long id){
        for(Task t:all())if(t.id==id)return t;
        return null;
    }
    public void upsert(Task task){
        List<Task> list=all();boolean updated=false;
        for(int i=0;i<list.size();i++)if(list.get(i).id==task.id){list.set(i,task);updated=true;break;}
        if(!updated)list.add(task);
        save(list);
    }
    public void remove(long id){
        List<Task> list=all();
        list.removeIf(t->t.id==id);
        save(list);
    }
    public long nextId(){ return pref.getLong("nextId",0L)+1L; }
    public long allocateId(){
        long id=nextId();
        pref.edit().putLong("nextId",id).commit();
        return id;
    }
}

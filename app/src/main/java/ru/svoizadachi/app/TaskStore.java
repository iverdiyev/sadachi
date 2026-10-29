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
    private final Context context;
    private static final Object LOCK=new Object();
    public TaskStore(Context ctx){context=ctx.getApplicationContext();pref=context.getSharedPreferences("tasks_local_v1",Context.MODE_PRIVATE);}
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
        TaskWidgetProvider.updateAll(context);
    }
    public Task find(long id){
        for(Task t:all())if(t.id==id)return t;
        return null;
    }
    public void upsert(Task task){
        synchronized(LOCK){
        List<Task> list=all();boolean updated=false;
        for(int i=0;i<list.size();i++)if(list.get(i).id==task.id){list.set(i,task);updated=true;break;}
        if(!updated)list.add(task);
        save(list);
        }
    }
    /** Persist a confirmed voice batch with one commit and one widget refresh. */
    public boolean addBatch(List<Task> batch){
        if(batch==null||batch.isEmpty())return false;
        synchronized(LOCK){
            List<Task> all=all();long next=pref.getLong("nextId",0L);
            for(Task existing:all)next=Math.max(next,existing.id);
            for(Task task:batch){if(task==null||task.title==null||task.title.trim().isEmpty()||task.id!=0)return false;}
            JSONArray values=new JSONArray();
            for(Task task:all)try{values.put(task.json());}catch(JSONException ignored){return false;}
            for(Task task:batch){
                task.id=++next;
                try{values.put(task.json());}catch(JSONException ignored){for(Task created:batch)created.id=0;return false;}
            }
            if(!pref.edit().putString("tasks",values.toString()).putLong("nextId",next).commit()){
                for(Task created:batch)created.id=0;return false;
            }
            TaskWidgetProvider.updateAll(context);return true;
        }
    }
    public void remove(long id){
        synchronized(LOCK){
        List<Task> list=all();
        list.removeIf(t->t.id==id);
        save(list);
        }
    }
    public long nextId(){ return pref.getLong("nextId",0L)+1L; }
    public long allocateId(){
        synchronized(LOCK){
        long id=nextId();
        pref.edit().putLong("nextId",id).commit();
        return id;
        }
    }
    public List<String> folders(){
        List<String> folders=new ArrayList<>();
        try{JSONArray values=new JSONArray(pref.getString("folders","[]"));for(int i=0;i<values.length();i++){
            String name=values.optString(i).trim();if(!name.isEmpty()&&!folders.contains(name))folders.add(name);
        }}catch(JSONException ignored){}
        for(Task task:all())if(!task.folder.isEmpty()&&!folders.contains(task.folder))folders.add(task.folder);
        return folders;
    }
    private void saveFolders(List<String> folders){pref.edit().putString("folders",new JSONArray(folders).toString()).commit();}
    public boolean addFolder(String name){synchronized(LOCK){
        name=name.trim();List<String> folders=folders();if(name.isEmpty()||folders.contains(name))return false;
        folders.add(name);saveFolders(folders);return true;
    }}
    public boolean renameFolder(String oldName,String newName){synchronized(LOCK){
        newName=newName.trim();List<String> folders=folders();
        if(newName.isEmpty()||!folders.contains(oldName)||!oldName.equals(newName)&&folders.contains(newName))return false;
        folders.set(folders.indexOf(oldName),newName);saveFolders(folders);
        List<Task> tasks=all();for(Task task:tasks)if(task.folder.equals(oldName))task.folder=newName;save(tasks);return true;
    }}
    public void removeFolder(String name){synchronized(LOCK){
        List<String> folders=folders();folders.remove(name);saveFolders(folders);
        List<Task> tasks=all();for(Task task:tasks)if(task.folder.equals(name))task.folder="";save(tasks);
    }}
}

package ru.svoizadachi.app;

import android.content.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.util.*;

/** User-selected local JSON files. No backup server or automatic deletion. */
public final class LocalBackup {
    private LocalBackup(){}
    public static JSONObject snapshot(Context c) throws JSONException {
        TaskStore store=new TaskStore(c);JSONArray tasks=new JSONArray();for(Task task:store.all())tasks.put(task.json());
        return new JSONObject().put("format","sadachi-local-v1").put("tasks",tasks).put("folders",new JSONArray(store.folders()));
    }
    public static JSONObject read(InputStream input) throws Exception {
        try(InputStream stream=input;ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buffer=new byte[16384];int count;while((count=stream.read(buffer))!=-1){bytes.write(buffer,0,count);if(bytes.size()>10*1024*1024)throw new IOException("Backup too large");}
            JSONObject json=new JSONObject(bytes.toString("UTF-8"));
            if(!"sadachi-local-v1".equals(json.optString("format"))||json.optJSONArray("tasks")==null)throw new IOException("Unknown backup format");
            return json;
        }
    }
    public static int restore(Context c,JSONObject json) throws Exception {
        if(!"sadachi-local-v1".equals(json.optString("format")))throw new IOException("Unknown backup");
        JSONArray rows=json.getJSONArray("tasks");if(rows.length()>10000)throw new IOException("Too many tasks");
        List<Task> imported=new ArrayList<>();for(int i=0;i<rows.length();i++){
            Task task=Task.fromJson(rows.getJSONObject(i));if(task.title.trim().isEmpty())throw new IOException("Task title is empty");imported.add(task);
        }
        TaskStore store=new TaskStore(c);List<Task> all=store.all();
        Set<Long> used=new HashSet<>();for(Task task:all)used.add(task.id);
        for(Task task:imported){do{task.id=store.allocateId();}while(!used.add(task.id));all.add(task);}
        JSONArray folders=json.optJSONArray("folders");if(folders!=null)for(int i=0;i<folders.length();i++)store.addFolder(folders.optString(i));
        store.save(all);for(Task task:imported)ReminderScheduler.schedule(c,task);return imported.size();
    }
    public static boolean migrated(Context c){return c.getSharedPreferences("migration",Context.MODE_PRIVATE).getBoolean("legacy_done",false);}
    public static int fromLegacy(Context c) throws Exception {
        if(migrated(c))throw new IOException("Already migrated");
        Uri uri=Uri.parse("content://ru.svoizadachi.app.migration/backup");
        JSONObject snapshot=read(c.getContentResolver().openInputStream(uri));int count=restore(c,snapshot);
        c.getSharedPreferences("migration",Context.MODE_PRIVATE).edit().putBoolean("legacy_done",true).commit();
        // Only pause old reminders after a verified copy has been committed locally.
        try{
            android.os.Bundle result=c.getContentResolver().call(Uri.parse("content://ru.svoizadachi.app.migration"),"pause_reminders",null,null);
            c.getSharedPreferences("migration",Context.MODE_PRIVATE).edit().putBoolean("legacy_paused",result!=null&&result.getBoolean("paused")).commit();
        }
        catch(RuntimeException e){ReminderScheduler.record(c,"Заметки перенесены; проверь отключение напоминаний в старом приложении");}
        return count;
    }
}

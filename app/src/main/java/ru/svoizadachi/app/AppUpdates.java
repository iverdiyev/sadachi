package ru.svoizadachi.app;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Executors;

/** Only release metadata and APKs are requested. Tasks never leave local storage. */
public final class AppUpdates {
    private static final String MANIFEST="https://github.com/iverdiyev/sadachi/releases/latest/download/update.json";
    private static final java.util.concurrent.ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static boolean checking;
    private AppUpdates(){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("app_updates",Context.MODE_PRIVATE);}
    public static boolean automatic(Context c){return prefs(c).getBoolean("automatic",true);}
    public static long installedCode(Context c){
        try{return versionCode(c.getPackageManager().getPackageInfo(c.getPackageName(),0));}catch(PackageManager.NameNotFoundException e){return 0;}
    }
    private static long versionCode(PackageInfo info){return android.os.Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
    public static String installedVersion(Context c){
        try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(PackageManager.NameNotFoundException e){return "?";}
    }
    public static boolean validManifest(JSONObject json){
        String url=json.optString("apkUrl"),hash=json.optString("sha256");
        try{
            URI uri=new URI(url);String path=uri.getPath();
            return json.optLong("versionCode")>0&&json.optLong("versionCode")<=Integer.MAX_VALUE
                    &&json.optString("versionName").matches("[0-9]+(?:\\.[0-9]+){1,3}")
                    &&"https".equals(uri.getScheme())&&"github.com".equals(uri.getHost())
                    &&uri.getUserInfo()==null&&(uri.getPort()==-1||uri.getPort()==443)&&uri.getQuery()==null&&uri.getFragment()==null
                    &&path!=null&&path.startsWith("/iverdiyev/sadachi/releases/download/")&&path.endsWith(".apk")
                    &&!path.contains("..")&&hash.matches("[a-fA-F0-9]{64}");
        }catch(Exception e){return false;}
    }
    public static void checkOnEntry(Activity activity){
        if(automatic(activity)&&System.currentTimeMillis()-prefs(activity).getLong("last_check",0)>6*60*60*1000L)check(activity,false);
    }
    public static void show(Activity activity){
        new AlertDialog.Builder(activity).setTitle("Обновления · "+installedVersion(activity))
                .setItems(new String[]{"Проверить сейчас","Скачать последнюю найденную версию","Установить скачанный APK",
                        "Автопроверка: "+(automatic(activity)?"включена":"выключена")},(d,index)->{
                    if(index==0)check(activity,true);
                    else if(index==1)download(activity);
                    else if(index==2)install(activity);
                    else {prefs(activity).edit().putBoolean("automatic",!automatic(activity)).commit();show(activity);}
                }).setNegativeButton("Закрыть",null).show();
    }
    private static boolean alive(Activity a){return !a.isFinishing()&&!a.isDestroyed();}
    private static void message(Activity a,String text){if(alive(a))new AlertDialog.Builder(a).setTitle("Обновления").setMessage(text).setPositiveButton("Понятно",null).show();}
    public static void check(Activity activity,boolean manual){
        synchronized(AppUpdates.class){if(checking){if(manual)Toast.makeText(activity,"Проверка уже идёт",Toast.LENGTH_SHORT).show();return;}checking=true;}
        if(manual)Toast.makeText(activity,"Проверяю релизы GitHub…",Toast.LENGTH_SHORT).show();
        Context context=activity.getApplicationContext();
        WORK.execute(()->{
            JSONObject metadata=null;String error=null;HttpURLConnection connection=null;
            try{
                connection=(HttpURLConnection)new URL(MANIFEST).openConnection();connection.setConnectTimeout(10000);connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent","Sadachi-Android");
                int status=connection.getResponseCode();
                if(status==404)error="Опубликованных обновлений пока нет.";
                else if(status!=200)error="GitHub временно недоступен. Попробуй позже.";
                else try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1){output.write(buffer,0,count);if(output.size()>65536)throw new IOException("Manifest too large");}
                    metadata=new JSONObject(output.toString("UTF-8"));if(!validManifest(metadata))throw new IOException("Invalid release metadata");
                    prefs(context).edit().putString("metadata",metadata.toString()).commit();
                }
            }catch(Exception failure){error="Не удалось проверить обновления. Задачи доступны без интернета.";}
            finally{if(connection!=null)connection.disconnect();prefs(context).edit().putLong("last_check",System.currentTimeMillis()).commit();synchronized(AppUpdates.class){checking=false;}}
            JSONObject result=metadata;String failure=error;
            activity.runOnUiThread(()->{
                if(!alive(activity))return;
                if(failure!=null){if(manual)message(activity,failure);return;}
                long code=result.optLong("versionCode");
                if(code>installedCode(context)){
                    if(!manual&&prefs(context).getLong("shown",0)==code)return;
                    prefs(context).edit().putLong("shown",code).commit();
                    new AlertDialog.Builder(activity).setTitle("Доступна версия "+result.optString("versionName"))
                            .setMessage("Скачать APK в локальное хранилище приложения? Заметки и настройки не отправляются на сервер. Установка потребует подтверждения Android.")
                            .setNegativeButton("Позже",null).setPositiveButton("Скачать",(d,w)->download(activity)).show();
                }else if(manual)message(activity,"Установлена актуальная версия: "+installedVersion(context));
            });
        });
    }
    private static JSONObject metadata(Context c){try{JSONObject json=new JSONObject(prefs(c).getString("metadata","{}"));return validManifest(json)?json:null;}catch(Exception e){return null;}}
    private static File file(Context c,long code){File directory=c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);return directory==null?null:new File(directory,"Sadachi-"+code+".apk");}
    private static int status(Context c,long id){
        try(Cursor cursor=c.getSystemService(DownloadManager.class).query(new DownloadManager.Query().setFilterById(id))){
            return cursor!=null&&cursor.moveToFirst()?cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)):-1;
        }catch(RuntimeException e){return -1;}
    }
    public static void download(Activity activity){
        JSONObject json=metadata(activity);if(json==null){message(activity,"Сначала нажми «Проверить сейчас».");return;}
        long previous=prefs(activity).getLong("download_id",-1);int previousStatus=status(activity,previous);
        if(previousStatus==DownloadManager.STATUS_RUNNING||previousStatus==DownloadManager.STATUS_PENDING||previousStatus==DownloadManager.STATUS_PAUSED){message(activity,"Скачивание уже идёт. Его состояние видно в шторке уведомлений.");return;}
        long code=json.optLong("versionCode");File target=file(activity,code);
        if(target==null){message(activity,"Локальное хранилище сейчас недоступно.");return;}
        if(target.isFile()&&previousStatus==DownloadManager.STATUS_SUCCESSFUL&&prefs(activity).getLong("download_code",0)==code){message(activity,"APK уже сохранён локально. Выбери «Установить скачанный APK».");return;}
        try{
            // Only a failed/partial download of this exact release is replaced.
            if(target.exists()&&!target.delete())throw new IOException("Cannot replace partial APK");
            DownloadManager.Request request=new DownloadManager.Request(Uri.parse(json.getString("apkUrl")))
                    .setTitle("Свои задачи · "+json.optString("versionName"))
                    .setDescription("Обновление приложения")
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(activity,Environment.DIRECTORY_DOWNLOADS,target.getName());
            long id=activity.getSystemService(DownloadManager.class).enqueue(request);
            prefs(activity).edit().putLong("download_id",id).putLong("download_code",code).putString("download_sha256",json.getString("sha256")).commit();
            message(activity,"Скачивание началось. APK сохранится на телефоне, прогресс — в шторке. Затем открой «Обновления → Установить скачанный APK».");
        }catch(Exception error){message(activity,"Не удалось начать скачивание. Проверь доступное место и системный «Диспетчер загрузки».");}
    }
    public static String sha256(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream input=new FileInputStream(file)){
            byte[] buffer=new byte[16384];int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);
        }StringBuilder hex=new StringBuilder();for(byte value:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",value&255));return hex.toString();
    }
    public static void install(Activity activity){
        long id=prefs(activity).getLong("download_id",-1),code=prefs(activity).getLong("download_code",0);
        if(status(activity,id)!=DownloadManager.STATUS_SUCCESSFUL){message(activity,"Готового APK пока нет. Сначала скачай его; прогресс виден в шторке.");return;}
        Context context=activity.getApplicationContext();String expected=prefs(activity).getString("download_sha256","");
        WORK.execute(()->{
            String error=null;
            try{
                File apk=file(context,code);if(!expected.equalsIgnoreCase(sha256(apk)))throw new IOException("Checksum mismatch");
                PackageManager pm=context.getPackageManager();PackageInfo archive=pm.getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNATURES);
                PackageInfo current=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNATURES);
                if(archive==null||!context.getPackageName().equals(archive.packageName)||versionCode(archive)!=code)throw new IOException("Wrong package");
                if(archive.signatures==null||current.signatures==null||archive.signatures.length==0||!Arrays.equals(archive.signatures,current.signatures))error="Подпись APK отличается от установленной версии. Не удаляй приложение с важными заметками: для обновления без потерь нужен прежний ключ подписи.";
            }catch(Exception failure){
                error="Проверка скачанного APK не прошла. Файл будет удалён; скачай его заново.";
                if(prefs(context).getLong("download_id",-1)==id){
                    try{context.getSystemService(DownloadManager.class).remove(id);}catch(RuntimeException ignored){}
                    prefs(context).edit().remove("download_id").commit();
                }
            }
            String failure=error;
            activity.runOnUiThread(()->{
                if(!alive(activity))return;if(failure!=null){message(activity,failure);return;}
                if(!activity.getPackageManager().canRequestPackageInstalls()){
                    new AlertDialog.Builder(activity).setTitle("Разрешение Android")
                            .setMessage("Разреши установку обновления из «Своих задач», затем снова нажми «Установить скачанный APK».")
                            .setNegativeButton("Отмена",null).setPositiveButton("Настройки",(d,w)->activity.startActivity(
                                    new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())))).show();return;
                }
                Uri uri=activity.getSystemService(DownloadManager.class).getUriForDownloadedFile(id);
                if(uri==null){message(activity,"Скачанный файл не найден.");return;}
                try{activity.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
                catch(RuntimeException e){message(activity,"Не удалось открыть системный установщик APK.");}
            });
        });
    }
}

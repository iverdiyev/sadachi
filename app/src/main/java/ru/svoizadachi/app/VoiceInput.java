package ru.svoizadachi.app;

import android.Manifest;
import android.app.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import android.widget.*;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;

/** Offline Russian ASR. No audio file, upload, background service or LLM server. */
public final class VoiceInput {
    public interface Result {void text(String value);}
    public static final int PERMISSION=824;
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private final Activity activity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Session current;
    private Result pending;
    public VoiceInput(Activity activity){this.activity=activity;}
    public void request(Result result){
        if(current!=null)return;
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            pending=result;activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},PERMISSION);return;
        }
        start(result);
    }
    public void permissionResult(int request,int[] results){
        if(request!=PERMISSION)return;Result result=pending;pending=null;
        if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED&&result!=null)start(result);
        else if(!activity.isFinishing())Toast.makeText(activity,"Для диктовки нужен доступ к микрофону",Toast.LENGTH_LONG).show();
    }
    private void start(Result callback){
        Session session=new Session(callback);current=session;
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        int padding=Math.round(20*activity.getResources().getDisplayMetrics().density);content.setPadding(padding,padding,padding,padding);
        session.status=new TextView(activity);session.status.setTextColor(ThemeSettings.subtle(activity));session.status.setTextSize(14);
        session.status.setText("Подготовка встроенной русской модели…");content.addView(session.status);
        session.preview=new TextView(activity);session.preview.setTextSize(18);session.preview.setTextColor(ThemeSettings.text(activity));
        session.preview.setPadding(0,padding,0,padding);content.addView(session.preview);
        session.dialog=new AlertDialog.Builder(activity).setTitle("Голосовой ввод · офлайн").setView(content)
                .setNegativeButton("Отмена",(d,w)->session.cancelled.set(true)).setPositiveButton("Готово",null).create();
        session.dialog.setOnDismissListener(d->{session.stop.set(true);if(!session.accepted.get())session.cancelled.set(true);if(current==session)current=null;});
        session.dialog.setOnShowListener(d->{
            session.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            session.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                session.accepted.set(true);session.stop.set(true);session.status.setText("Завершаю распознавание…");
                session.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            });
        });session.dialog.show();RoundedUi.dialog(session.dialog);
        WORK.execute(()->recognize(session));
    }
    public void cancel(){
        Session session=current;if(session!=null){session.cancelled.set(true);session.stop.set(true);session.dialog.dismiss();}
    }
    public void destroy(){cancel();pending=null;}
    /** Leaving the screen stops recording but must not discard a pending permission result. */
    public void pause(){Session session=current;if(session!=null){session.cancelled.set(true);session.stop.set(true);session.dialog.dismiss();}}
    private void ui(Session s,Runnable action){main.post(()->{if(current==s&&!activity.isFinishing()&&!activity.isDestroyed()&&!s.cancelled.get())action.run();});}
    private void recognize(Session s){
        AudioRecord recorder=null;String error=null;StringBuilder words=new StringBuilder();
        try{
            File path=unpack(activity.getApplicationContext().getFilesDir(),s);
            if(s.cancelled.get())return;
            try(Model model=new Model(path.getAbsolutePath());Recognizer recognizer=new Recognizer(model,16000f)){
                if(s.cancelled.get())return;
                int size=Math.max(AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT),6400);
                recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size);
                if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Microphone unavailable");
                recorder.startRecording();if(recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IOException("Microphone busy");
                ui(s,()->{s.status.setText("Говори по-русски. Запись не сохраняется. До 60 секунд.");s.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);});
                byte[] buffer=new byte[3200];long deadline=SystemClock.elapsedRealtime()+60000,last=0;
                while(!s.stop.get()&&!s.cancelled.get()&&SystemClock.elapsedRealtime()<deadline){
                    int count=recorder.read(buffer,0,buffer.length);if(count<0)throw new IOException("Audio read failed");if(count==0){Thread.sleep(20);continue;}
                    if(recognizer.acceptWaveForm(buffer,count))append(words,new JSONObject(recognizer.getResult()).optString("text"));
                    if(SystemClock.elapsedRealtime()-last>250){
                        String preview=(words+" "+new JSONObject(recognizer.getPartialResult()).optString("partial")).trim();
                        ui(s,()->s.preview.setText(preview));last=SystemClock.elapsedRealtime();
                    }
                }
                if(!s.cancelled.get()){
                    append(words,new JSONObject(recognizer.getFinalResult()).optString("text"));s.accepted.set(true);
                }
            }
        }catch(Exception|LinkageError failure){android.util.Log.w("SadachiVoice","Offline ASR failed",failure);error="Не удалось запустить офлайн-распознавание. Проверь микрофон и свободное место.";}
        finally{if(recorder!=null){try{recorder.stop();}catch(RuntimeException ignored){}recorder.release();}}
        String result=words.toString().trim(),message=error;
        ui(s,()->{
            if(message!=null){s.status.setText(message);s.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Закрыть");s.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);s.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->s.dialog.dismiss());}
            else {s.dialog.dismiss();if(!result.isEmpty())s.callback.text(result);else Toast.makeText(activity,"Речь не распознана. Попробуй ещё раз в тихом месте.",Toast.LENGTH_LONG).show();}
        });
    }
    private static void append(StringBuilder words,String value){if(!value.trim().isEmpty()){if(words.length()>0)words.append(' ');words.append(value.trim());}}
    private File unpack(File files,Session s) throws Exception {
        File directory=new File(files,"asr/ru-0.22"),marker=new File(directory,"complete");
        if(marker.isFile())return new File(directory,"vosk-model-small-ru-0.22");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("No model directory");
        String root=directory.getCanonicalPath()+File.separator;long total=0;
        try(ZipInputStream zip=new ZipInputStream(activity.getAssets().open("voice-ru-0.22.zip"))){
            ZipEntry entry;byte[] buffer=new byte[32768];
            while((entry=zip.getNextEntry())!=null){
                if(s.cancelled.get())throw new InterruptedIOException("Cancelled");
                File target=new File(directory,entry.getName());if(!target.getCanonicalPath().startsWith(root))throw new IOException("Invalid model archive");
                if(entry.isDirectory()){if(!target.isDirectory()&&!target.mkdirs())throw new IOException("Cannot unpack model");continue;}
                File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot unpack model");
                try(OutputStream output=new FileOutputStream(target)){
                    int count;while((count=zip.read(buffer))!=-1){total+=count;if(total>200*1024*1024L)throw new IOException("Model too large");output.write(buffer,0,count);}
                }
            }
        }
        File model=new File(directory,"vosk-model-small-ru-0.22");if(!new File(model,"am/final.mdl").isFile())throw new IOException("Model missing");
        try(FileOutputStream output=new FileOutputStream(marker)){output.write(1);}return model;
    }
    private static final class Session {
        final AtomicBoolean stop=new AtomicBoolean(),cancelled=new AtomicBoolean(),accepted=new AtomicBoolean();final Result callback;
        AlertDialog dialog;TextView status,preview;
        Session(Result callback){this.callback=callback;}
    }
}

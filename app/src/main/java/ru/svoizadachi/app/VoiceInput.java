package ru.svoizadachi.app;

import android.Manifest;
import android.app.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import android.view.Gravity;
import android.view.WindowManager;
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
    enum State {PREPARING,LISTENING,TRANSCRIBING,ERROR,EMPTY}
    private static final String EMPTY_PREVIEW="Скажи одну или несколько задач. Перед сохранением их можно исправить.";
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
        else if(!activity.isFinishing())Toast.makeText(activity,"Без разрешения микрофона можно добавить задачу текстом",Toast.LENGTH_LONG).show();
    }
    private void start(Result callback){
        Session session=new Session(callback);current=session;
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        int padding=UiTokens.dp(activity,16);content.setPadding(padding,padding,padding,padding);
        TextView heading=new TextView(activity);heading.setText("Голосовое добавление");
        UiTokens.text(heading,UiTokens.SECTION_TEXT,true);heading.setTextColor(ThemeSettings.text(activity));
        content.addView(heading);
        session.status=new TextView(activity);session.status.setTextColor(ThemeSettings.subtle(activity));UiTokens.text(session.status,UiTokens.BODY,false);
        session.status.setPadding(0,UiTokens.dp(activity,8),0,UiTokens.dp(activity,8));content.addView(session.status);
        session.wave=new VoiceWaveform(activity);content.addView(session.wave,new LinearLayout.LayoutParams(-1,UiTokens.dp(activity,72)));
        TextView transcriptLabel=new TextView(activity);transcriptLabel.setText("Распознанный текст");UiTokens.text(transcriptLabel,UiTokens.FILTER,false);
        transcriptLabel.setTextColor(ThemeSettings.subtle(activity));content.addView(transcriptLabel);
        ScrollView transcriptScroll=new ScrollView(activity);
        session.preview=new TextView(activity);UiTokens.text(session.preview,UiTokens.TASK,false);session.preview.setTextColor(ThemeSettings.text(activity));
        session.preview.setText(EMPTY_PREVIEW);
        session.preview.setPadding(0,UiTokens.dp(activity,8),0,UiTokens.dp(activity,8));transcriptScroll.addView(session.preview);
        content.addView(transcriptScroll,new LinearLayout.LayoutParams(-1,UiTokens.dp(activity,96)));
        TextView privacy=new TextView(activity);privacy.setText("Офлайн · запись не сохраняется");UiTokens.text(privacy,UiTokens.CAPTION,false);
        privacy.setTextColor(ThemeSettings.subtle(activity));privacy.setGravity(Gravity.CENTER);content.addView(privacy);
        session.dialog=new AlertDialog.Builder(activity).setView(content)
                .setNegativeButton("Отмена",(d,w)->session.cancelled.set(true))
                .setNeutralButton("Повторить",null).setPositiveButton("Остановить",null).create();
        session.dialog.setOnDismissListener(d->{session.stop.set(true);session.wave.stop();if(!session.accepted.get())session.cancelled.set(true);if(current==session)current=null;});
        session.dialog.show();RoundedUi.sheet(session.dialog);RoundedUi.actions(session.dialog);
        // Recording needs room for the actual transcript, not a permanently 75%-high sheet.
        session.dialog.getWindow().setLayout(RoundedUi.sheetWidth(activity.getResources().getDisplayMetrics().widthPixels,
                activity.getResources().getDisplayMetrics().density),WindowManager.LayoutParams.WRAP_CONTENT);
        session.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(session.state!=State.LISTENING)return;
            session.stop.set(true);session.wave.stop();render(session,State.TRANSCRIBING);
        });
        session.dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
            if(session.state!=State.ERROR&&session.state!=State.EMPTY)return;
            session.dialog.dismiss();start(session.callback);
        });
        render(session,State.PREPARING);
        WORK.execute(()->recognize(session));
    }
    private void render(Session s,State state){
        s.state=state;
        String message=state==State.PREPARING?"Подготовка встроенной русской модели…":
                state==State.LISTENING?"Слушаю… До 60 секунд. Останови запись, когда закончишь.":
                state==State.TRANSCRIBING?"Распознаю речь…":
                state==State.EMPTY?"Не удалось разобрать слова. Попробуй ещё раз или введи задачу текстом.":
                "Не удалось запустить офлайн-распознавание. Проверь микрофон и свободное место.";
        s.status.setText(message);
        Button positive=s.dialog.getButton(AlertDialog.BUTTON_POSITIVE),retry=s.dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        positive.setEnabled(state==State.LISTENING||state==State.ERROR||state==State.EMPTY);
        positive.setText(state==State.ERROR||state==State.EMPTY?"Закрыть":"Остановить");
        retry.setEnabled(state==State.ERROR||state==State.EMPTY);
        retry.setVisibility(state==State.ERROR||state==State.EMPTY?android.view.View.VISIBLE:android.view.View.GONE);
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
                VoiceSilenceDetector silence=new VoiceSilenceDetector(VoiceSettings.silenceMillis(activity));
                ui(s,()->render(s,State.LISTENING));
                byte[] buffer=new byte[3200];long deadline=SystemClock.elapsedRealtime()+60000,last=0,lastWave=0;
                while(!s.stop.get()&&!s.cancelled.get()&&SystemClock.elapsedRealtime()<deadline){
                    int count=recorder.read(buffer,0,buffer.length);if(count<0)throw new IOException("Audio read failed");if(count==0){Thread.sleep(20);continue;}
                    float rms=VoiceWaveform.pcmRms(buffer,count);long now=SystemClock.elapsedRealtime();
                    if(now-lastWave>=48){ui(s,()->{if(s.state==State.LISTENING&&!s.stop.get())s.wave.level(rms);});lastWave=now;}
                    if(silence.shouldStop(rms,now)){ui(s,()->render(s,State.TRANSCRIBING));break;}
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
        }catch(Exception|LinkageError failure){android.util.Log.w("SadachiVoice","Offline ASR failed",failure);error="recognition error";}
        finally{ui(s,()->s.wave.stop());if(recorder!=null){try{recorder.stop();}catch(RuntimeException ignored){}recorder.release();}}
        String result=words.toString().trim(),message=error;
        ui(s,()->{
            if(message!=null){
                render(s,State.ERROR);
                String available=result.isEmpty()?s.preview.getText().toString().trim():result;
                if(available.equals(EMPTY_PREVIEW))available="";
                final String recovered=available;
                s.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(recovered.isEmpty()?"Закрыть":"Проверить текст");
                s.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{s.dialog.dismiss();if(!recovered.isEmpty())s.callback.text(recovered);});
            }
            else if(result.isEmpty()){render(s,State.EMPTY);s.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->s.dialog.dismiss());}
            else {s.dialog.dismiss();s.callback.text(result);}
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
        AlertDialog dialog;TextView status,preview;VoiceWaveform wave;State state;
        Session(Result callback){this.callback=callback;}
    }
}

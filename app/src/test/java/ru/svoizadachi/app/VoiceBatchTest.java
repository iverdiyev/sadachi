package ru.svoizadachi.app;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=OfflineTestApplication.class)
public class VoiceBatchTest {
    @Test public void confirmedBatchIsSavedTogetherWithoutChangingExistingTasks(){
        Application app=RuntimeEnvironment.getApplication();TaskStore store=new TaskStore(app);
        Task old=new Task(store.allocateId(),"Старая заметка",null,false,0,false);old.description="Не трогать";store.upsert(old);
        Task first=new Task(0,"Первое",null,false,0,false),second=new Task(0,"Второе",null,false,0,false);
        assertTrue(store.addBatch(Arrays.asList(first,second)));
        assertEquals(3,store.all().size());assertEquals("Не трогать",store.find(old.id).description);
        assertNotEquals(first.id,second.id);assertNotNull(store.find(first.id));assertNotNull(store.find(second.id));
    }
    @Test public void invalidBatchDoesNotSaveAnyTaskOrAdvanceIds(){
        TaskStore store=new TaskStore(RuntimeEnvironment.getApplication());long next=store.nextId();
        Task first=new Task(0,"Первая",null,false,0,false),invalid=new Task(0," ",null,false,0,false);
        assertFalse(store.addBatch(Arrays.asList(first,invalid)));
        assertEquals(0,store.all().size());assertEquals(next,store.nextId());assertEquals(0,first.id);
    }
    @Test public void reviewDoesNotSaveBeforeConfirmationAndCanDiscardACandidate(){
        TaskStore store=new TaskStore(RuntimeEnvironment.getApplication());
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            VoiceTaskReview.show(controller.get(),store,"","Завтра купить хлеб, в субботу купить молоко",()->{});
            android.app.AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
            assertEquals(0,store.all().size());
            android.view.View root=dialog.getWindow().getDecorView();
            android.widget.TextView remove=findText(root,"Убрать");assertNotNull(remove);remove.performClick();
            assertEquals("Добавить задач: 1",dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).getText().toString());
            assertTrue(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(1,store.all().size());
        }
    }
    @Test public void ambiguousSpeechRequiresExplicitReviewAndNeverInventsADeadline(){
        TaskStore store=new TaskStore(RuntimeEnvironment.getApplication());
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            VoiceTaskReview.show(controller.get(),store,"","Завтра в пять позвони Саше",()->{});
            android.app.AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
            android.view.View root=dialog.getWindow().getDecorView();
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(0,store.all().size());
            android.widget.CheckBox checked=(android.widget.CheckBox)findText(root,"Я проверил(а) эту задачу");
            assertNotNull(checked);checked.setChecked(true);
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(1,store.all().size());assertNull(store.all().get(0).dueAt);
        }
    }
    @Test public void eachCandidateCanChooseItsOwnFolderBeforeSaving(){
        TaskStore store=new TaskStore(RuntimeEnvironment.getApplication());
        assertTrue(store.addFolder("Работа"));assertTrue(store.addFolder("Дом"));
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            VoiceTaskReview.show(controller.get(),store,"Дом","Завтра сдать отчёт, в субботу купить продукты",()->{});
            android.app.AlertDialog review=ShadowAlertDialog.getLatestAlertDialog();
            assertEquals(0,store.all().size());
            android.view.View root=review.getWindow().getDecorView();
            android.view.View first=findDescription(root,"Выбрать список задачи: Завтра сдать отчёт");
            assertNotNull(first);first.performClick();
            android.app.AlertDialog chooser=ShadowAlertDialog.getLatestAlertDialog();
            assertNotSame(review,chooser);
            chooser.getListView().performItemClick(new android.view.View(controller.get()),1,chooser.getListView().getItemIdAtPosition(1));
            assertEquals("Список: Работа",((android.widget.TextView)first).getText().toString());
            review.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(2,store.all().size());
            for(Task task:store.all()){
                if("сдать отчёт".equals(task.title))assertEquals("Работа",task.folder);
                else if("купить продукты".equals(task.title))assertEquals("Дом",task.folder);
                else fail("Unexpected candidate: "+task.title);
            }
        }
    }
    @Test public void editedTranscriptMustBeReparsedBeforeSaving(){
        TaskStore store=new TaskStore(RuntimeEnvironment.getApplication());
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            VoiceTaskReview.show(controller.get(),store,"","Завтра купить хлеб",()->{});
            android.app.AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
            android.view.View root=dialog.getWindow().getDecorView();
            android.widget.EditText transcript=(android.widget.EditText)findDescription(root,"Распознанный текст");
            assertNotNull(transcript);transcript.setText("Завтра купить молоко");
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(0,store.all().size());
            android.widget.TextView reparse=findText(root,"Текст изменён · разобрать заново");
            assertNotNull(reparse);reparse.performClick();
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(1,store.all().size());assertEquals("купить молоко",store.all().get(0).title);
        }
    }
    @Test public void dismissingReviewNeverWritesCandidates(){
        TaskStore store=new TaskStore(RuntimeEnvironment.getApplication());
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            VoiceTaskReview.show(controller.get(),store,"","Завтра купить хлеб",()->{});
            ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();
            assertEquals(0,store.all().size());
        }
    }
    private android.view.View findDescription(android.view.View view,String value){
        if(value.equals(view.getContentDescription()))return view;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                android.view.View found=findDescription(group.getChildAt(i),value);if(found!=null)return found;
            }}
        return null;
    }
    private android.widget.TextView findText(android.view.View view,String value){
        if(view instanceof android.widget.TextView&&value.equals(((android.widget.TextView)view).getText().toString()))return (android.widget.TextView)view;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){android.widget.TextView found=findText(group.getChildAt(i),value);if(found!=null)return found;}}
        return null;
    }
}

package ru.svoizadachi.app;

import android.graphics.Typeface;
import android.text.*;
import android.text.style.*;
import android.widget.EditText;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=OfflineTestApplication.class)
public class FormattingTest {
    @Test public void boldItalicAndUnderlineSurviveStorage() throws Exception {
        SpannableStringBuilder text=new SpannableStringBuilder("Важный текст\nВторая строка");
        text.setSpan(new StyleSpan(Typeface.BOLD),0,6,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(Typeface.ITALIC),7,12,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new UnderlineSpan(),13,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Task task=new Task(1,"Заметка",null,false,0,false);NoteFormatting.save(task,text);
        Task restored=Task.fromJson(task.json());CharSequence loaded=NoteFormatting.load(restored);
        assertEquals(text.toString(),loaded.toString());assertTrue(loaded instanceof Spanned);
        assertTrue(((Spanned)loaded).getSpans(0,6,StyleSpan.class).length>0);
        assertTrue(((Spanned)loaded).getSpans(13,loaded.length(),UnderlineSpan.class).length>0);
    }
    @Test public void plainOldNotesRemainPlainAndSafe(){
        Task task=new Task(1,"Заметка",null,false,0,false);task.description="Старое описание";
        assertEquals(task.description,NoteFormatting.load(task).toString());
        task.descriptionHtml="<b>Не то описание</b>";
        assertEquals(task.description,NoteFormatting.load(task).toString());
    }
    @Test public void formattingCanBeToggledAndBulletListsStayLocal(){
        EditText edit=new EditText(RuntimeEnvironment.getApplication());edit.setText("Один\nДва");edit.setSelection(0,4);
        NoteFormatting.toggle(edit,0);assertEquals(1,edit.getText().getSpans(0,4,StyleSpan.class).length);
        NoteFormatting.toggle(edit,0);assertEquals(0,edit.getText().getSpans(0,4,StyleSpan.class).length);
        edit.setSelection(0,edit.length());NoteFormatting.bullets(edit);
        assertEquals("• Один\n• Два",edit.getText().toString());
    }
    @Test public void linksAndPastedColorsAreNotPersistedAsActiveContent(){
        SpannableStringBuilder value=new SpannableStringBuilder("Текст");
        value.setSpan(new URLSpan("https://example.invalid"),0,5,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        value.setSpan(new ForegroundColorSpan(0xFFFFFFFF),0,5,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        assertEquals(0,NoteFormatting.clean(value).getSpans(0,5,URLSpan.class).length);
        assertEquals(0,NoteFormatting.clean(value).getSpans(0,5,ForegroundColorSpan.class).length);
    }
}

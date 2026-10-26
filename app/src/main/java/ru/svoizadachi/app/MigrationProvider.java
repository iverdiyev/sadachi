package ru.svoizadachi.app;

import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.io.*;

/** Narrow, same-signature-only bridge from the old application ID. */
public final class MigrationProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private void authorize(){
        if(getContext().getPackageManager().checkSignatures(Binder.getCallingUid(),android.os.Process.myUid())!=PackageManager.SIGNATURE_MATCH)
            throw new SecurityException("A matching app signature is required");
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        authorize();if(!"r".equals(mode)||!"/backup".equals(uri.getPath()))throw new FileNotFoundException("Read-only backup endpoint");
        try{
            byte[] bytes=LocalBackup.snapshot(getContext()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
            new Thread(()->{try(OutputStream output=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){output.write(bytes);}catch(IOException ignored){}},"sadachi-local-transfer").start();
            return pipe[0];
        }catch(Exception e){throw new FileNotFoundException("Local backup is unavailable");}
    }
    @Override public Bundle call(String method,String argument,Bundle extras){
        authorize();if(!"pause_reminders".equals(method))throw new IllegalArgumentException("Unknown operation");
        getContext().getSharedPreferences("reminder_delivery_v2",Context.MODE_PRIVATE).edit().putBoolean("migration_paused",true).commit();
        for(Task task:new TaskStore(getContext()).all())ReminderScheduler.cancel(getContext(),task.id);
        Bundle result=new Bundle();result.putBoolean("paused",true);return result;
    }
    @Override public String getType(Uri uri){return "application/json";}
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}

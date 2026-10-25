package ru.svoizadachi.app;

/** UI tests must not call the real GitHub update endpoint. */
public final class OfflineTestApplication extends android.app.Application {
    @Override public void onCreate(){super.onCreate();getSharedPreferences("app_updates",MODE_PRIVATE).edit().putBoolean("automatic",false).commit();}
}

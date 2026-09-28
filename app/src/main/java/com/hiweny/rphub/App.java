package com.hiweny.rphub;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * App entry point. The shell follows the system light/dark setting; on top of the
 * page's own theme the WebView is allowed to force-darken pages that have no dark
 * theme of their own (e.g. the embedded 万相广场 frame).
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}

package com.hiweny.rphub;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * App entry point. Forces the whole app (window background, system bars and the
 * WebView surface) to follow the system light/dark setting.
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}

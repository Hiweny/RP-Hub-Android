package com.hiweny.rphub;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * App entry point. RP Hub ships its own dark theme, so the shell always runs in
 * dark mode (window background, system bars and the WebView surface).
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }
}

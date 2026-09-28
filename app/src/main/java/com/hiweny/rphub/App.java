package com.hiweny.rphub;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * App entry point. The shell always runs in dark mode: the site's own dark theme is
 * used for the app surface, and our injected fallback layer + WebView force-dark
 * cover the pages that ship no dark theme of their own.
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }
}

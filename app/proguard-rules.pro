# Keep the JS bridge: methods annotated with @JavascriptInterface must survive R8.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.hiweny.rphub.Bridge { *; }
-keep class com.hiweny.rphub.App { *; }

# WebView / AndroidX are referenced from the manifest.
-keep class androidx.appcompat.** { *; }
-keep class androidx.webkit.** { *; }

# Do not warn about missing platform classes.
-dontwarn android.webkit.**

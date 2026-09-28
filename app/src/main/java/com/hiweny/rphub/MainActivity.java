package com.hiweny.rphub;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Full-screen immersive WebView shell for RP Hub.
 *
 * <p>Loads the live site so the APK always tracks the web version, and layers on
 * device-side polish: immersive edge-to-edge display, an always-dark theme with a
 * fallback layer covering surfaces the site misses, mobile performance tuning, a
 * keyboard that lifts the page's own composer, and working upload / export flows.</p>
 */
public class MainActivity extends AppCompatActivity implements Bridge.Listener {

    private static final String START_URL = "https://sta1n156.github.io/RP-Hub/";
    private static final int COLOR_DARK = 0xFF1E1E1E;

    private FrameLayout rootView;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private ActivityResultLauncher<Intent> saveLauncher;
    private String fallbackScript = "";

    private byte[] pendingSaveData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-edge + the IME insets API only work well together on Android 11+.
        // On older devices we let the platform resize the window for the keyboard.
        boolean manualIme = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), !manualIme);

        // Upload: pick a file through the system file manager.
        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (fileCallback == null) return;
                    Uri[] uris = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        uris = WebChromeClient.FileChooserParams.parseResult(
                                result.getResultCode(), result.getData());
                    }
                    fileCallback.onReceiveValue(uris);
                    fileCallback = null;
                });

        // Export: never write silently - ask the user where to save (SAF).
        saveLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    byte[] data = pendingSaveData;
                    pendingSaveData = null;
                    if (data == null) return;
                    if (result.getResultCode() == RESULT_OK
                            && result.getData() != null
                            && result.getData().getData() != null) {
                        writeTo(result.getData().getData(), data);
                    } else {
                        Toast.makeText(this, "已取消导出", Toast.LENGTH_SHORT).show();
                    }
                });

        buildWebView(manualIme);
        applySystemUi();

        if (savedInstanceState == null) {
            webView.loadUrl(START_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void buildWebView(boolean manualIme) {
        webView = new WebView(this);

        rootView = new FrameLayout(this);
        rootView.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(rootView);

        webView.setBackgroundColor(COLOR_DARK);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);       // localStorage holds all chat data
        s.setDatabaseEnabled(true);         // IndexedDB
        s.setAllowFileAccess(false);        // site is remote http(s); uploads use content URIs
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setSaveFormData(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.setSafeBrowsingEnabled(false); // fewer round-trips on navigation
        }

        // --- low-level rendering tuning -------------------------------------------------
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.OFF_SCREEN_PRERASTER)) {
                WebSettingsCompat.setOffscreenPreRaster(s, true);
            }
        } catch (Throwable ignored) { }
        try {
            // Let WebView force-darken pages that ship no dark theme (the embedded
            // 万相广场 frame); pages that declare colour-scheme support are untouched.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, true);
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(s, WebSettingsCompat.FORCE_DARK_AUTO);
            }
        } catch (Throwable ignored) { }

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setScrollbarFadingEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
            } catch (Throwable ignored) { }
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new Bridge(this), "RPHubBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (fallbackScript != null && !fallbackScript.isEmpty()) {
                    view.evaluateJavascript(fallbackScript, null);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    fileChooserLauncher.launch(Intent.createChooser(intent, "选择文件"));
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                if (url == null || url.startsWith("blob:") || url.startsWith("data:")) return;
                try {
                    systemDownload(url, userAgent, contentDisposition, mimetype);
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ignored) { }
                }
            }
        });

        if (manualIme) {
            // In a full-screen (edge to edge) shell the window never resizes for the
            // keyboard. Shrinking the content view is exactly what the page expects
            // (its viewport meta declares interactive-widget=resizes-content), so its
            // own layout lifts the composer above the keyboard.
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                int bottom = Math.max(ime.bottom, 0);
                if (v.getPaddingBottom() != bottom) {
                    v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
                }
                return insets;
            });
            ViewCompat.requestApplyInsets(rootView);
        }

        injectScripts();
    }

    private void systemDownload(String url, String userAgent,
                                String contentDisposition, String mimetype) {
        android.app.DownloadManager.Request request =
                new android.app.DownloadManager.Request(Uri.parse(url));
        if (mimetype != null) request.setMimeType(mimetype);
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null) request.addRequestHeader("cookie", cookies);
        if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
        String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
        request.setTitle(fileName);
        request.setNotificationVisibility(
                android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(
                android.os.Environment.DIRECTORY_DOWNLOADS, fileName);
        android.app.DownloadManager dm =
                (android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm != null) {
            dm.enqueue(request);
            Toast.makeText(this, "开始下载：" + fileName, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean handleUrl(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        if (scheme.equals("http") || scheme.equals("https")
                || scheme.equals("blob") || scheme.equals("data")
                || scheme.equals("about") || scheme.equals("javascript")) {
            return false; // stay inside the WebView
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) { }
        return true;
    }

    private void injectScripts() {
        String mainCss = readAsset("inject/perf.css") + "\n" + readAsset("inject/dark.css");
        String frameCss = readAsset("inject/external-dark.css");

        String script = readAsset("inject/theme.js")
                + "\n" + readAsset("inject/net.js")
                + "\n" + readAsset("inject/download.js")
                + "\n" + cssInjector(mainCss, frameCss);

        // Run before any page script. Each injected JS file guards on the host name so
        // cross-origin embedded frames (万相广场) are never touched by page hooks.
        try {
            WebViewCompat.addDocumentStartJavaScript(webView, script, Collections.singleton("*"));
            return;
        } catch (Throwable ignored) {
            // Feature unsupported on this WebView -> fall back to injecting on finish.
        }
        fallbackScript = script;
    }

    /**
     * Injects the right stylesheet per origin: the full dark fallback on the main
     * app, and a minimal force-dark layer inside embedded cross-origin frames.
     */
    private static String cssInjector(String mainCss, String frameCss) {
        return "(function(){if(window.__RPHUB_CSS__)return;window.__RPHUB_CSS__=1;"
                + "var main=(location.hostname==='sta1n156.github.io');"
                + "var css=main?(" + JSONObject.quote(mainCss) + "):(" + JSONObject.quote(frameCss) + ");"
                + "if(!css)return;"
                + "function add(){try{var root=document.head||document.documentElement;"
                + "if(!root)return false;var s=document.createElement('style');"
                + "s.setAttribute('data-rphub','1');s.textContent=css;root.appendChild(s);"
                + "return true;}catch(e){return false;}}"
                + "if(!add()){document.addEventListener('DOMContentLoaded',add,{once:true});}})();";
    }

    private String readAsset(String path) {
        try (InputStream is = getAssets().open(path)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private void applySystemUi() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        window.getDecorView().setBackgroundColor(COLOR_DARK);
        if (webView != null) {
            webView.setBackgroundColor(COLOR_DARK);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        // Immersive full-screen: no status bar, no navigation bar -> no white edges.
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applySystemUi();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    // ---- Bridge.Listener : blob export forwarded from the page ----

    @Override
    public void onDownload(String name, String mime, byte[] data) {
        runOnUiThread(() -> startSaveFlow(name, mime, data));
    }

    private void startSaveFlow(String name, String mime, byte[] data) {
        if (name == null || name.isEmpty()) name = "export";
        String cleanMime = (mime == null || mime.isEmpty())
                ? "application/octet-stream" : mime.split(";")[0].trim();
        pendingSaveData = data;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(cleanMime);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        try {
            saveLauncher.launch(intent);
        } catch (Exception e) {
            pendingSaveData = null;
            Toast.makeText(this, "无法打开保存对话框", Toast.LENGTH_SHORT).show();
        }
    }

    private void writeTo(Uri uri, byte[] data) {
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) throw new IOException("无法写入目标文件");
            os.write(data);
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}

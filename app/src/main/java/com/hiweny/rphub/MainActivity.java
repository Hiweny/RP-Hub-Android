package com.hiweny.rphub;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Full-screen immersive WebView shell for RP Hub.
 *
 * <p>Loads the live site so the APK always tracks the web version, and layers on
 * device-side polish: immersive edge-to-edge display, system light/dark following,
 * mobile performance tweaks, and working upload/download flows.</p>
 */
public class MainActivity extends AppCompatActivity implements Bridge.Listener {

    private static final String START_URL = "https://sta1n156.github.io/RP-Hub/";
    private static final int REQ_WRITE_STORAGE = 1001;
    private static final int COLOR_LIGHT = 0xFFF9FAFB;
    private static final int COLOR_DARK = 0xFF1E1E1E;

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private String fallbackScript = "";

    private byte[] pendingData;
    private String pendingName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

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

        buildWebView();
        applySystemUi();

        if (savedInstanceState == null) {
            webView.loadUrl(START_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void buildWebView() {
        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(webView);

        boolean night = isNightMode();
        webView.setBackgroundColor(night ? COLOR_DARK : COLOR_LIGHT);

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
        // The page styles its own dark theme, so never let WebView algorithmically dim it.
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, false);
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(s, WebSettingsCompat.FORCE_DARK_OFF);
            }
        } catch (Throwable ignored) { }

        // Scrolling polish: no edge glow, no scrollbars.
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setScrollbarFadingEnabled(true);

        // Keep the renderer alive / high priority to avoid reload stalls.
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

        // Plain http(s) downloads are handed to the system DownloadManager.
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                if (url == null || url.startsWith("blob:") || url.startsWith("data:")) return;
                try {
                    DownloadManagerRequest(url, userAgent, contentDisposition, mimetype);
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ignored) { }
                }
            }
        });

        injectScripts();
    }

    private void DownloadManagerRequest(String url, String userAgent,
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
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
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
        // tel:, mailto:, intent:, market:, custom app schemes -> system
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) { }
        return true;
    }

    private void injectScripts() {
        String script = readAsset("inject/theme.js")
                + "\n" + readAsset("inject/download.js")
                + "\n" + cssInjector(readAsset("inject/perf.css"));

        // Preferred: run before any page script so theme + download hooks exist first.
        try {
            WebViewCompat.addDocumentStartJavaScript(webView, script, Collections.singleton("*"));
            return;
        } catch (Throwable ignored) {
            // Feature unsupported on this WebView -> fall back to injecting on finish.
        }
        fallbackScript = script;
    }

    private static String cssInjector(String css) {
        return "(function(){if(window.__RPHUB_CSS__)return;window.__RPHUB_CSS__=1;"
                + "var css=" + JSONObject.quote(css) + ";"
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
        boolean night = isNightMode();
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        window.getDecorView().setBackgroundColor(night ? COLOR_DARK : COLOR_LIGHT);
        if (webView != null) {
            webView.setBackgroundColor(night ? COLOR_DARK : COLOR_LIGHT);
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

    private boolean isNightMode() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
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

    // ---- Bridge.Listener : blob downloads forwarded from the page ----

    @Override
    public void onDownload(String name, String mime, byte[] data) {
        runOnUiThread(() -> saveToDownloads(name, mime, data));
    }

    private void saveToDownloads(String name, String mime, byte[] data) {
        if (name == null || name.isEmpty()) name = "rphub-download";
        if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(name, mime, data);
            } else {
                if (ContextCompat.checkSelfPermission(this,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    pendingData = data;
                    pendingName = name;
                    requestPermissions(new String[]{
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_WRITE_STORAGE);
                    return;
                }
                saveLegacy(name, data);
            }
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveWithMediaStore(String name, String mime, byte[] data) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        ContentResolver resolver = getContentResolver();
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("无法创建下载项");

        try (OutputStream os = resolver.openOutputStream(uri)) {
            if (os == null) throw new IOException("无法打开输出流");
            os.write(data);
        }

        values.clear();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(uri, values, null, null);

        toastSaved(name);
    }

    private void saveLegacy(String name, byte[] data) throws IOException {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("无法创建下载目录");
        File file = uniqueFile(dir, name);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
        toastSaved(file.getName());
    }

    private File uniqueFile(File dir, String name) {
        File file = new File(dir, name);
        if (!file.exists()) return file;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int i = 1;
        while (file.exists()) {
            file = new File(dir, base + "(" + i + ")" + ext);
            i++;
        }
        return file;
    }

    private void toastSaved(String name) {
        Toast.makeText(this, "已保存到「下载」：" + name, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && pendingData != null) {
                try {
                    saveLegacy(pendingName != null ? pendingName : "rphub-download", pendingData);
                } catch (Exception e) {
                    Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "未获得存储权限，无法保存文件", Toast.LENGTH_LONG).show();
            }
            pendingData = null;
            pendingName = null;
        }
    }
}

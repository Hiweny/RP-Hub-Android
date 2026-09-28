package com.hiweny.rphub;

import android.util.Base64;
import android.webkit.JavascriptInterface;

import java.io.ByteArrayOutputStream;

/**
 * Bridge exposed to the web page as {@code window.RPHubBridge}.
 *
 * <p>Android WebView cannot download {@code blob:} URLs (the page builds character
 * cards / chats with {@code URL.createObjectURL(blob)} + {@code a.download}), so the
 * injected {@code download.js} forwards the blob bytes here in base64 chunks and the
 * native side writes them into the public Downloads collection.</p>
 */
public class Bridge {

    public interface Listener {
        void onDownload(String name, String mime, byte[] data);
    }

    private final Listener listener;
    private ByteArrayOutputStream buffer;
    private String name;
    private String mime;

    public Bridge(Listener listener) {
        this.listener = listener;
    }

    @JavascriptInterface
    public void startSave(String fileName, String mimeType) {
        buffer = new ByteArrayOutputStream();
        name = fileName;
        mime = mimeType;
    }

    @JavascriptInterface
    public void appendChunk(String base64Chunk) {
        if (buffer == null || base64Chunk == null) return;
        try {
            // Each chunk is a multiple of 4 chars, so it decodes independently.
            byte[] part = Base64.decode(base64Chunk, Base64.DEFAULT);
            buffer.write(part, 0, part.length);
        } catch (Exception ignored) {
        }
    }

    @JavascriptInterface
    public void finishSave() {
        byte[] data = buffer != null ? buffer.toByteArray() : new byte[0];
        String n = name;
        String m = mime;
        buffer = null;
        name = null;
        mime = null;
        if (listener != null) {
            listener.onDownload(n, m, data);
        }
    }
}

package com.theprobyte.cozybeach;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The beach page in a WebView. The page and three.js are served from the app's assets,
 * so it works offline; only the live weather needs the internet.
 */
final class BeachWeb {
    /** Android's reserved address for app assets (it never goes to the network). */
    static final String HOST = "appassets.androidplatform.net";
    static final String PAGE = "https://" + HOST + "/assets/index.html";
    /** The page imports three.js from this address; the app answers it from its own copy. */
    private static final String THREE_URL = "https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.module.js";

    private BeachWeb() {}

    @SuppressLint("SetJavaScriptEnabled")
    static WebView create(Context context) {
        final AssetManager assets = context.getApplicationContext().getAssets();
        WebView web = new WebView(context);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // saves the ⚙ settings
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        web.setBackgroundColor(0xFF0B1630);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                String path = null;
                if (HOST.equals(url.getHost()) && url.getPath() != null && url.getPath().startsWith("/assets/")) {
                    path = url.getPath().substring("/assets/".length());
                } else if (THREE_URL.equals(url.toString())) {
                    path = "three.module.js";
                }
                if (path == null) return null;     // anything else (weather, fonts) goes to the network
                Map<String, String> headers = new HashMap<>();
                headers.put("Access-Control-Allow-Origin", "*");   // module scripts are loaded with CORS
                headers.put("Cache-Control", "no-cache");
                try {
                    InputStream in = assets.open(path);
                    return new WebResourceResponse(mimeFor(path), "utf-8", 200, "OK", headers, in);
                } catch (IOException e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", headers, null);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;   // stay on the beach: no navigating away
            }
        });
        return web;
    }

    private static String mimeFor(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".js")) return "text/javascript";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }
}

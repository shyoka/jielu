package com.jielu.timetable;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 节律 · 课程表
 *
 * 单 Activity + WebView 的轻量壳：网页资源全部打包在 assets/www 中，
 * 通过 shouldInterceptRequest 以 https://appassets.androidplatform.net 提供，
 * 既避免 file:// 的存储限制，也让页面拥有稳定的安全上下文。
 */
public class MainActivity extends Activity {

    private static final String ASSET_HOST = "appassets.androidplatform.net";
    private static final String HOME_URL =
            "https://appassets.androidplatform.net/index.html?app=1";

    /** 返回键：先关弹层、再回课表页，都没有时才退出应用。 */
    private static final String BACK_SCRIPT =
            "(function(){try{"
                    + "var app=document.querySelector('.app');"
                    + "if(app&&app.classList.contains('is-sheet-open')){"
                    + "var s=document.getElementById('sheetScrim');if(s){s.click();}return 'handled';}"
                    + "var d=document.getElementById('watchDialog');"
                    + "if(d&&d.open){d.close();return 'handled';}"
                    + "var n=document.querySelector('.nav-item.is-active');"
                    + "if(n&&n.getAttribute('data-page-target')!=='0'){"
                    + "var h=document.querySelector('.nav-item[data-page-target=\"0\"]');if(h){h.click();}return 'handled';}"
                    + "return 'exit';}catch(e){return 'exit';}})()";

    private WebView webView;
    private boolean backPending = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int initialBar = night ? 0xFF151110 : 0xFFFDFAF7;

        webView = new WebView(this);
        webView.setBackgroundColor(initialBar);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        // 课程表按像素排版，固定文字缩放，避免系统字体大小破坏布局
        settings.setTextZoom(100);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(false);
        }

        webView.addJavascriptInterface(new NativeBridge(), "JieluNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (!ASSET_HOST.equals(request.getUrl().getHost())) {
                    return null;
                }
                return serveAsset(request.getUrl().getPath());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (ASSET_HOST.equals(url.getHost())) {
                    return false;
                }
                // 页面本身完全离线，只有开源信息里的外链会走到这里，交给系统浏览器
                openExternal(url.toString());
                return true;
            }
        });

        // WebView 默认不弹 JS 对话框，confirm() 会直接返回 false，
        // 导致「删除课程 / 清空课表 / 恢复示例」看起来完全没反应。
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                        .setOnCancelListener(dialog -> result.confirm())
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
                        .setOnCancelListener(dialog -> result.cancel())
                        .show();
                return true;
            }
        });

        setContentView(webView);
        applySystemBars(initialBar, !night);
        webView.loadUrl(HOME_URL);
    }

    /* ---------- 本地资源 ---------- */

    private WebResourceResponse serveAsset(String rawPath) {
        String path = rawPath == null ? "" : rawPath;
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.isEmpty()) {
            path = "index.html";
        }
        if (path.contains("..")) {
            return notFound();
        }

        String mime = mimeOf(path);
        String encoding = mime.startsWith("text/")
                || mime.contains("javascript")
                || mime.contains("json")
                || mime.contains("svg")
                ? "utf-8" : null;

        try {
            InputStream stream = getAssets().open("www/" + path);
            return new WebResourceResponse(mime, encoding, 200, "OK", cacheHeaders(), stream);
        } catch (IOException error) {
            return notFound();
        }
    }

    private static Map<String, String> cacheHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-cache");
        headers.put("Access-Control-Allow-Origin", "*");
        return headers;
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse(
                "text/plain", "utf-8", 404, "Not Found",
                cacheHeaders(), new ByteArrayInputStream(new byte[0]));
    }

    private static String mimeOf(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    /* ---------- 系统栏 ---------- */

    /** 只放行 http(s) 外链，其余 scheme 一律忽略。 */
    private void openExternal(String url) {
        final String target = url == null ? "" : url.trim();
        if (!target.startsWith("https://") && !target.startsWith("http://")) {
            return;
        }
        runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception ignored) {
                // 没有可用的浏览器时保持静默，页面上的地址仍可长按复制
            }
        });
    }

    private class NativeBridge {
        @JavascriptInterface
        public void setSystemBars(final String colorHex, final boolean darkIcons) {
            runOnUiThread(() -> {
                int color;
                try {
                    color = Color.parseColor(colorHex);
                } catch (Exception error) {
                    return;
                }
                applySystemBars(color, darkIcons);
            });
        }

        @JavascriptInterface
        public void openUrl(final String url) {
            openExternal(url);
        }

        /** 把整份课表 JSON 通过 Data Layer 推送到手表。 */
        @JavascriptInterface
        public void sendToWatch(final String json) {
            if (json == null || json.length() < 2) {
                notifyWatchResult("fail");
                return;
            }
            runOnUiThread(() -> {
                try {
                    Wearable.getNodeClient(MainActivity.this).getConnectedNodes()
                            .addOnSuccessListener(nodes -> {
                                if (nodes == null || nodes.isEmpty()) {
                                    notifyWatchResult("no-node");
                                    return;
                                }
                                PutDataMapRequest request = PutDataMapRequest.create("/jielu/data");
                                request.getDataMap().putString("json", json);
                                request.getDataMap().putLong("sentAt", System.currentTimeMillis());
                                Wearable.getDataClient(MainActivity.this)
                                        .putDataItem(request.asPutDataRequest().setUrgent())
                                        .addOnCompleteListener(task ->
                                                notifyWatchResult(task.isSuccessful() ? "ok" : "fail"));
                            })
                            .addOnFailureListener(error -> notifyWatchResult("no-gms"));
                } catch (Throwable error) {
                    notifyWatchResult("no-gms");
                }
            });
        }
    }

    /** 把发送结果回传给网页，由页面弹 snackbar。 */
    private void notifyWatchResult(String status) {
        runOnUiThread(() -> {
            if (webView == null) return;
            webView.evaluateJavascript(
                    "window.JieluWatchResult&&window.JieluWatchResult('" + status + "')", null);
        });
    }

    private void applySystemBars(int color, boolean darkIcons) {
        getWindow().setStatusBarColor(color);
        getWindow().setNavigationBarColor(color);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(darkIcons ? mask : 0, mask);
            }
        } else {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            if (darkIcons) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (darkIcons) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                } else {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    /* ---------- 生命周期 ---------- */

    @Override
    public void onBackPressed() {
        if (webView == null || backPending) {
            return;
        }
        backPending = true;
        webView.evaluateJavascript(BACK_SCRIPT, value -> {
            backPending = false;
            if (value == null || !value.contains("handled")) {
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    protected void onPause() {
        // 后台暂停渲染与定时器，和网页端的「分钟级刷新」一起控制耗电
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}

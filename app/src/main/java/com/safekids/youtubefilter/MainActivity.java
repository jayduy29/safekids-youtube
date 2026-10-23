package com.safekids.youtubefilter;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private FrameLayout fullscreenContainer;
    private ProgressBar progressBar;
    private FloatingActionButton btnSettings;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private static final String PREF_NAME = "SafeKidsPrefs";
    private SharedPreferences prefs;

    // Default Filter Rules
    private static final String DEFAULT_KEYWORDS = "skibidi,toilet,elsagate,poppy playtime,huggy wuggy,siren head,momo,horror,kinh dị,quái vật,troll 24h,đánh nhau,squid game,bloody";
    private static final String DEFAULT_WHITELIST = "VTV7,CoComelon,Super Simple Songs,BabyBus,Peppa Pig,ChuChu TV,PBS KIDS,Pinkfong,Khan Academy Kids,National Geographic Kids";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Keep screen on while playing video
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        webView = findViewById(R.id.webview);
        fullscreenContainer = findViewById(R.id.fullscreen_container);
        progressBar = findViewById(R.id.progress_bar);
        btnSettings = findViewById(R.id.btn_parent_settings);

        setupWebView();

        btnSettings.setOnClickListener(v -> showPinDialog());

        // Load YouTube Kids
        webView.loadUrl("https://www.youtubekids.com/");
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);

        // Set modern Mobile Chrome User-Agent for standard YouTube Kids layout
        String defaultUA = settings.getUserAgentString();
        settings.setUserAgentString(defaultUA + " Mobile SafeKidsBrowser/1.0");

        // Bind Bridge for JavaScript communication
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");

        // Custom WebChromeClient for Fullscreen Video handling
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    onHideCustomView();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

                fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                fullscreenContainer.setVisibility(View.VISIBLE);
                webView.setVisibility(View.GONE);
                btnSettings.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;

                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                fullscreenContainer.removeView(customView);
                fullscreenContainer.setVisibility(View.GONE);
                customView = null;
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
                webView.setVisibility(View.VISIBLE);
                btnSettings.setVisibility(View.VISIBLE);
            }
        });

        // Restricted navigation WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                if (host != null && (host.contains("youtubekids.com") ||
                        host.contains("google.com") ||
                        host.contains("gstatic.com") ||
                        host.contains("youtube.com"))) {
                    return false; // Allow internal navigation
                }
                // Block external website popups or redirections
                Toast.makeText(MainActivity.this, "Trang ngoài đã bị chặn để bảo vệ an toàn cho bé!", Toast.LENGTH_SHORT).show();
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                injectFilterScript();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                injectFilterScript();
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                // Ensure filter stays active during Single Page App (SPA) navigation
                injectFilterScript();
            }
        });
    }

    private void injectFilterScript() {
        try {
            InputStream is = getAssets().open("safe_filter.js");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            String script = sb.toString();
            webView.evaluateJavascript(script, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // PIN Authentication for Parent Settings
    private void showPinDialog() {
        String savedPin = prefs.getString("pinCode", "0000");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Khóa Phụ Huynh");
        builder.setMessage("Nhập mã PIN 4 số (Mặc định: 0000):");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        builder.setView(input);

        builder.setPositiveButton("Mở", (dialog, which) -> {
            String entered = input.getText().toString().trim();
            if (entered.equals(savedPin)) {
                showSettingsDialog();
            } else {
                Toast.makeText(this, "Mã PIN không đúng!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Hủy", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    // Parent Settings Menu
    private void showSettingsDialog() {
        boolean filterEnabled = prefs.getBoolean("filterEnabled", true);
        String filterMode = prefs.getString("filterMode", "blacklist");

        String[] options = {
                (filterEnabled ? "✓ Bộ lọc: Đang BẬT" : "○ Bộ lọc: Đang TẮT"),
                "Chế độ: " + (filterMode.equals("whitelist") ? "Chỉ kênh được duyệt (Whitelist)" : "Chặn từ khóa & kênh (Blacklist)"),
                "Quản lý Từ khóa bị chặn",
                "Quản lý Kênh được phép (Whitelist)",
                "Đổi mã PIN bảo vệ",
                "Tải lại YouTube Kids"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Bảng Điều Khiển Phụ Huynh");
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // Toggle filter
                    prefs.edit().putBoolean("filterEnabled", !filterEnabled).apply();
                    injectFilterScript();
                    Toast.makeText(this, "Đã cập nhật trạng thái bộ lọc!", Toast.LENGTH_SHORT).show();
                    break;
                case 1: // Toggle Mode
                    String newMode = filterMode.equals("whitelist") ? "blacklist" : "whitelist";
                    prefs.edit().putString("filterMode", newMode).apply();
                    injectFilterScript();
                    Toast.makeText(this, "Đã chuyển sang chế độ: " + newMode, Toast.LENGTH_SHORT).show();
                    break;
                case 2: // Edit Keywords
                    showEditListDialog("Từ khóa bị chặn (phân cách bằng dấu phẩy):", "keywords", DEFAULT_KEYWORDS);
                    break;
                case 3: // Edit Whitelist
                    showEditListDialog("Kênh an toàn được phép (phân cách bằng dấu phẩy):", "whitelist", DEFAULT_WHITELIST);
                    break;
                case 4: // Change PIN
                    showChangePinDialog();
                    break;
                case 5: // Reload
                    webView.reload();
                    break;
            }
        });
        builder.show();
    }

    private void showEditListDialog(String title, String prefKey, String defVal) {
        String current = prefs.getString(prefKey, defVal);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        final EditText input = new EditText(this);
        input.setText(current);
        builder.setView(input);

        builder.setPositiveButton("Lưu", (dialog, which) -> {
            prefs.edit().putString(prefKey, input.getText().toString().trim()).apply();
            injectFilterScript();
            Toast.makeText(this, "Đã lưu thay đổi!", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    private void showChangePinDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Đổi mã PIN mới (4 số)");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("Lưu PIN", (dialog, which) -> {
            String newPin = input.getText().toString().trim();
            if (newPin.length() == 4) {
                prefs.edit().putString("pinCode", newPin).apply();
                Toast.makeText(this, "Đã cập nhật mã PIN mới!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Mã PIN phải gồm đúng 4 chữ số!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            WebChromeClient client = new WebChromeClient();
            onHideCustomViewInternal();
            return;
        }
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            // Confirm exit to prevent accidental app closing by the child
            new AlertDialog.Builder(this)
                    .setTitle("Thoát Ứng Dụng")
                    .setMessage("Bạn có chắc chắn muốn thoát SafeKids YouTube?")
                    .setPositiveButton("Có", (dialog, which) -> finish())
                    .setNegativeButton("Không", null)
                    .show();
        }
    }

    private void onHideCustomViewInternal() {
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
        }
        fullscreenContainer.setVisibility(View.GONE);
        fullscreenContainer.removeAllViews();
        customView = null;
        webView.setVisibility(View.VISIBLE);
        btnSettings.setVisibility(View.VISIBLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    // JavaScript Bridge
    public class WebAppInterface {
        Context context;

        WebAppInterface(Context c) {
            context = c;
        }

        @JavascriptInterface
        public String getFilterConfig() {
            try {
                JSONObject json = new JSONObject();
                json.put("filterEnabled", prefs.getBoolean("filterEnabled", true));
                json.put("filterMode", prefs.getString("filterMode", "blacklist"));

                String kwStr = prefs.getString("keywords", DEFAULT_KEYWORDS);
                JSONArray kwArray = new JSONArray();
                for (String k : kwStr.split(",")) {
                    if (!k.trim().isEmpty()) kwArray.put(k.trim());
                }
                json.put("blacklistedKeywords", kwArray);

                String wlStr = prefs.getString("whitelist", DEFAULT_WHITELIST);
                JSONArray wlArray = new JSONArray();
                for (String w : wlStr.split(",")) {
                    if (!w.trim().isEmpty()) wlArray.put(w.trim());
                }
                json.put("whitelistedChannels", wlArray);

                return json.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void onVideoBlocked(String title, String reason) {
            // Can be used for logging or parent audit
        }
    }
}

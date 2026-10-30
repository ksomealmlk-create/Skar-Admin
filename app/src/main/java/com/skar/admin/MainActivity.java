package com.skar.admin;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.webkit.CookieManager;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** تطبيق إدارة الاشتراكات: يفتح لوحة /admin بشاشة كاملة (ثيم داكن) ويحتفظ بجلسة الدخول */
public class MainActivity extends Activity {

    // لم يتغير شيء هنا: نفس السيرفر ونفس الرابط
    private static final String HOST = "skar-subs.ksomealmlk.workers.dev";
    private static final String URL = "https://" + HOST + "/admin";

    private static final int BG = 0xFF0A0F1A;
    private static final int TEAL = 0xFF14B8A6;

    private WebView web;
    private FrameLayout splash;
    private ObjectAnimator pulse;
    private String darkJs = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable failsafe = this::hideSplash;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        darkJs = loadAsset("dark.js");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        web = new WebView(this);
        web.setBackgroundColor(BG);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setHorizontalScrollBarEnabled(false);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        splash = buildSplash();
        root.addView(splash, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        setupWebSettings();

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                // أي رابط خارجي (مثل واتساب) يُفتح بالتطبيق المناسب
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "تعذر فتح الرابط", Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    view.loadDataWithBaseURL(null, errorHtml(), "text/html", "UTF-8", null);
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (isOurPage(url)) showSplash();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (isOurPage(url) && !darkJs.isEmpty()) {
                    view.evaluateJavascript(darkJs, v -> handler.postDelayed(() -> hideSplash(), 150));
                } else {
                    hideSplash();
                }
            }
        });

        // بدون هذا الكلاس، نوافذ confirm() و alert() ما تظهر وأزرار الحذف والإيقاف ما تشتغل
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                // نطبّق الثيم مبكراً قبل اكتمال التحميل لتقليل الوميض
                if (progress >= 35 && isOurPage(view.getUrl()) && !darkJs.isEmpty()) {
                    view.evaluateJavascript(darkJs, null);
                }
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                        .setMessage(message)
                        .setPositiveButton("تأكيد", (d, w) -> result.confirm())
                        .setNegativeButton("إلغاء", (d, w) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                        .setMessage(message)
                        .setPositiveButton("موافق", (d, w) -> result.confirm())
                        .setOnCancelListener(d -> result.confirm())
                        .show();
                return true;
            }
        });

        showSplash();
        boolean restored = false;
        if (savedInstanceState != null) {
            restored = web.restoreState(savedInstanceState) != null;
        }
        if (!restored) web.loadUrl(URL);
    }

    @SuppressWarnings("deprecation")
    private void setupWebSettings() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // إصلاح العرض: عرض الجهاز الحقيقي بدون تكبير/تصغير يُخرج الصفحة عن الشاشة
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);

        // الوضع الداكن المدمج بالنظام: أخف وأسلس بالتمرير من فلاتر CSS
        if (Build.VERSION.SDK_INT >= 33) {
            s.setAlgorithmicDarkeningAllowed(true);
        } else if (Build.VERSION.SDK_INT >= 29) {
            s.setForceDark(WebSettings.FORCE_DARK_ON);
        }
    }

    private boolean isOurPage(String url) {
        return url != null && url.startsWith("https://" + HOST);
    }

    /* ------------------------- شاشة التحميل ------------------------- */

    private FrameLayout buildSplash() {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0A0F1A, 0xFF0E2A31, 0xFF141A3A}));
        f.setClickable(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        col.addView(logo, new LinearLayout.LayoutParams(dp(96), dp(96)));

        pulse = ObjectAnimator.ofPropertyValuesHolder(logo,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.10f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.10f));
        pulse.setDuration(900);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.setRepeatMode(ObjectAnimator.REVERSE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());

        TextView title = new TextView(this);
        title.setText("إدارة صكار");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(18);
        col.addView(title, tp);

        TextView sub = new TextView(this);
        sub.setText("جاري التحميل…");
        sub.setTextColor(0xFF7DD3C8);
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(6);
        col.addView(sub, sp);

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        bar.getIndeterminateDrawable().setColorFilter(TEAL, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(150), dp(4));
        bp.topMargin = dp(22);
        col.addView(bar, bp);

        f.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return f;
    }

    private void showSplash() {
        handler.removeCallbacks(failsafe);
        splash.animate().cancel();
        splash.setAlpha(1f);
        splash.setVisibility(View.VISIBLE);
        if (pulse != null && !pulse.isStarted()) pulse.start();
        handler.postDelayed(failsafe, 8000); // احتياط: لا نترك الشاشة مغطاة أبداً
    }

    private void hideSplash() {
        handler.removeCallbacks(failsafe);
        if (splash == null || splash.getVisibility() != View.VISIBLE) return;
        splash.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            splash.setVisibility(View.GONE);
            if (pulse != null) pulse.cancel();
        }).start();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /* ------------------------- صفحة الخطأ ------------------------- */

    private String errorHtml() {
        return "<html dir='rtl'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                + "<body style=\"margin:0;min-height:100vh;display:flex;flex-direction:column;align-items:center;"
                + "justify-content:center;text-align:center;padding:24px;box-sizing:border-box;color:#E6EDF7;"
                + "font-family:sans-serif;background:radial-gradient(500px 360px at 85% 0%,rgba(20,184,166,.35),transparent 70%),"
                + "radial-gradient(460px 400px at 0% 100%,rgba(99,102,241,.30),transparent 70%),#0A0F1A\">"
                + "<svg width='110' height='110' viewBox='0 0 24 24' fill='none' stroke='#14B8A6' stroke-width='1.6' "
                + "stroke-linecap='round' stroke-linejoin='round'><path d='M17.5 19H9a7 7 0 1 1 6.7-9h1.8a4.5 4.5 0 0 1 0 9z'/>"
                + "<path d='M3 3l18 18' stroke='#F87171'/></svg>"
                + "<h2 style='margin:22px 0 8px'>لا يوجد اتصال بالسيرفر</h2>"
                + "<p style='margin:0 0 28px;color:#94A3B8'>تحقق من الإنترنت ثم أعد المحاولة.</p>"
                + "<button style=\"font-size:17px;padding:14px 32px;border:0;border-radius:14px;color:#fff;"
                + "background:linear-gradient(135deg,#14B8A6,#0F766E);box-shadow:0 8px 24px rgba(20,184,166,.4)\" "
                + "onclick=\"location.href='" + URL + "'\">إعادة المحاولة</button></body></html>";
    }

    private String loadAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    /* ------------------------- دورة الحياة ------------------------- */

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush(); // حفظ جلسة الدخول
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (pulse != null) pulse.cancel();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}

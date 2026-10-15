package com.polas.amarhisab;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * পুরো অ্যাপটা assets/index.html-এ। এই Activity শুধু WebView চালায় আর
 * ডেটা সেভের জন্য একটা ছোট JS bridge দেয়।
 */
public class MainActivity extends Activity {

    private static final String DATA_FILE = "hisab.json";
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#1D3270")); // হেডারের রঙের সাথে মিলিয়ে

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);     // file:///android_asset লোড করার জন্য
        s.setTextZoom(100);             // ফোনের বড় ফন্ট সেটিংসে লেআউট না ভাঙে

        // JS থেকে window.AndroidBridge নামে পাওয়া যাবে
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                // বাইরের লিংক ব্রাউজারে খোলে, অ্যাপের ভেতরে না
                startActivity(new Intent(Intent.ACTION_VIEW, u));
                return true;
            }
        });

        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    /** ব্যাক বাটন: আগে JS-কে সুযোগ দাও (মডাল বন্ধ / হোমে ফেরা), না হলে অ্যাপ বন্ধ */
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.appBack ? window.appBack() : false", value -> {
            if (!"true".equals(value)) finish();
        });
    }

    public class Bridge {

        /** ডেটা অ্যাপের internal storage-এ। আগে temp ফাইলে লিখে তারপর rename, যাতে মাঝপথে বন্ধ হলেও ফাইল নষ্ট না হয় */
        @JavascriptInterface
        public synchronized void save(String json) throws IOException {
            File tmp = new File(getFilesDir(), DATA_FILE + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            File dst = new File(getFilesDir(), DATA_FILE);
            if (!tmp.renameTo(dst)) throw new IOException("rename failed");
        }

        @JavascriptInterface
        public synchronized String load() {
            File f = new File(getFilesDir(), DATA_FILE);
            if (!f.exists()) return null;
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[(int) f.length()];
                int off = 0, n;
                while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
                return new String(buf, 0, off, StandardCharsets.UTF_8);
            } catch (IOException e) {
                return null;
            }
        }

        /** ব্যাকআপ লেখা WhatsApp / Drive / ইমেইলে পাঠানোর জন্য */
        @JavascriptInterface
        public void share(String text) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, "আমার হিসাব ব্যাকআপ");
            i.putExtra(Intent.EXTRA_TEXT, text);
            runOnUiThread(() -> startActivity(Intent.createChooser(i, "ব্যাকআপ পাঠান")));
        }
    }
}

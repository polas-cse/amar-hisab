package com.polas.betonkhata;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.HapticFeedbackConstants;
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
import java.util.ArrayList;
import java.util.Locale;

import org.json.JSONArray;

/**
 * পুরো অ্যাপটা assets/index.html-এ। এই Activity শুধু WebView চালায় আর
 * ডেটা সেভের জন্য একটা ছোট JS bridge দেয়।
 */
public class MainActivity extends Activity {

    private static final String DATA_FILE = "hisab.json";
    private static final int REQ_VOICE = 41;
    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;

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

        // বাংলা ভয়েস: আগে bn-BD, না থাকলে bn-IN
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            int r = tts.setLanguage(new Locale("bn", "BD"));
            if (r < TextToSpeech.LANG_AVAILABLE) r = tts.setLanguage(new Locale("bn", "IN"));
            if (r >= TextToSpeech.LANG_AVAILABLE) {
                tts.setSpeechRate(0.9f);   // একটু ধীরে, যাতে সংখ্যা পরিষ্কার শোনা যায়
                ttsReady = true;
            }
        });

        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tts != null) tts.stop();   // অ্যাপ থেকে বের হলে কথা বন্ধ
        web.evaluateJavascript("window.appPause&&appPause()", null);   // লক টাইমার শুরু
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        // ১ মিনিটের বেশি বাইরে থাকলে JS আবার পিন চাইবে
        web.evaluateJavascript("window.appResume&&appResume()", null);
    }

    /** Google-এর কথা চেনার স্ক্রিন থেকে ফিরে আসা লেখা JS-কে দেওয়া */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_VOICE) return;
        ArrayList<String> res = (resultCode == RESULT_OK && data != null)
                ? data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) : null;
        if (res == null || res.isEmpty()) {
            web.evaluateJavascript("window.onVoiceError&&onVoiceError('cancel')", null);
        } else {
            // JSONArray ঠিকমতো escape করে, তাই সরাসরি JS-এ পাঠানো নিরাপদ
            web.evaluateJavascript("window.onVoiceResult&&onVoiceResult(" + new JSONArray(res) + ")", null);
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
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

        /** লং-প্রেসে কার্ডের হিসাব জোরে পড়ে শোনানো। বাংলা ভয়েস না থাকলে false */
        @JavascriptInterface
        public boolean speak(String text) {
            runOnUiThread(() -> web.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS));
            if (!ttsReady) return false;
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "card");
            return true;
        }

        /** মুখে প্রশ্ন: ফোনের Google speech স্ক্রিন খোলে, mic permission অ্যাপের লাগে না */
        @JavascriptInterface
        public void listen() {
            runOnUiThread(() -> {
                if (tts != null) tts.stop();
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD");
                i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
                i.putExtra(RecognizerIntent.EXTRA_PROMPT, "বলুন, যেমন: আমার কত টাকা বাকি?");
                try {
                    startActivityForResult(i, REQ_VOICE);
                } catch (ActivityNotFoundException e) {
                    web.evaluateJavascript("window.onVoiceError&&onVoiceError('noapp')", null);
                }
            });
        }

        /** ফোনে বাংলা ভয়েস না থাকলে TTS ডেটা ইনস্টলের পাতা খোলা */
        @JavascriptInterface
        public void installVoice() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
                } catch (Exception e) {
                    startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
                }
            });
        }

        /** ব্যাকআপ লেখা WhatsApp / Drive / ইমেইলে পাঠানোর জন্য */
        @JavascriptInterface
        public void share(String text) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, "বেতনের খাতা ব্যাকআপ");
            i.putExtra(Intent.EXTRA_TEXT, text);
            runOnUiThread(() -> startActivity(Intent.createChooser(i, "ব্যাকআপ পাঠান")));
        }
    }
}

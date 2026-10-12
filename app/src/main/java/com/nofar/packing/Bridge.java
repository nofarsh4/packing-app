package com.nofar.packing;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.webkit.JavascriptInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** What the page can ask the phone to do: keep data in files, copy/paste, schedule reminders. */
public class Bridge {
    private final Context ctx;
    private final MainActivity act;

    Bridge(MainActivity a) { act = a; ctx = a.getApplicationContext(); }

    private File file(String key) {
        File dir = new File(ctx.getFilesDir(), "kv");
        dir.mkdirs();
        return new File(dir, key.replaceAll("[^A-Za-z0-9_\\-]", "_"));
    }

    @JavascriptInterface
    public synchronized void put(String key, String value) {
        File f = file(key), tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (!tmp.renameTo(f)) throw new RuntimeException("rename failed");
    }

    @JavascriptInterface
    public synchronized String get(String key) {
        File f = file(key);
        if (!f.exists()) return "";
        try (FileInputStream i = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int n = 0;
            while (n < buf.length) {
                int r = i.read(buf, n, buf.length - n);
                if (r < 0) break;
                n += r;
            }
            return new String(buf, 0, n, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    @JavascriptInterface
    public synchronized void del(String key) { file(key).delete(); }

    @JavascriptInterface
    public void copy(String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("orezet", text));
    }

    @JavascriptInterface
    public String paste() {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData d = cm.getPrimaryClip();
            if (d == null || d.getItemCount() == 0) return "";
            CharSequence t = d.getItemAt(0).coerceToText(ctx);
            return t == null ? "" : t.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @JavascriptInterface
    public void schedule(String json) { Notifs.schedule(ctx, json); }

    private static final String API = "https://generativelanguage.googleapis.com/v1beta/";

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        return o.toString("UTF-8");
    }

    /** One question to Gemini. Returns null when the model name is unknown (404). */
    private static String generate(String model, String key, String prompt) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API + "models/" + model + ":generateContent").openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(90000);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("x-goog-api-key", key);
        c.setDoOutput(true);
        JSONObject body = new JSONObject().put("contents", new JSONArray().put(
                new JSONObject().put("role", "user").put("parts", new JSONArray().put(new JSONObject().put("text", prompt)))));
        c.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));
        int code = c.getResponseCode();
        if (code == 404) return null;
        if (code != 200) {
            String err = readAll(c.getErrorStream());
            throw new Exception("http " + code + ": " + (err.length() > 200 ? err.substring(0, 200) : err));
        }
        JSONArray parts = new JSONObject(readAll(c.getInputStream())).getJSONArray("candidates")
                .getJSONObject(0).getJSONObject("content").getJSONArray("parts");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) sb.append(parts.getJSONObject(i).optString("text"));
        if (sb.length() == 0) throw new Exception("empty answer");
        return sb.toString();
    }

    /** Fallback when the default model name is gone: ask Google which text models this key can use. */
    private static String pickModel(String key) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API + "models?pageSize=200").openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("x-goog-api-key", key);
        if (c.getResponseCode() != 200) throw new Exception("http " + c.getResponseCode() + ": " + readAll(c.getErrorStream()));
        JSONArray models = new JSONObject(readAll(c.getInputStream())).getJSONArray("models");
        String best = null;
        for (int i = 0; i < models.length(); i++) {
            JSONObject m = models.getJSONObject(i);
            String name = m.optString("name").replace("models/", "");
            if (!name.contains("flash") || !m.optString("supportedGenerationMethods").contains("generateContent")) continue;
            if (name.matches(".*(lite|image|tts|live|audio|embedding|preview|exp).*")) continue;
            if (best == null || name.compareTo(best) > 0) best = name;
        }
        return best;
    }

    @JavascriptInterface
    public void ask(final String id, final String key, final String prompt) {
        new Thread(() -> {
            boolean ok = false;
            String out;
            try {
                String r = generate("gemini-flash-latest", key, prompt);
                if (r == null) {
                    String m = pickModel(key);
                    if (m != null) r = generate(m, key, prompt);
                }
                if (r != null) { ok = true; out = r; } else out = "no model available";
            } catch (Exception e) {
                out = String.valueOf(e.getMessage());
            }
            final String js = "window.orzAnswer&&window.orzAnswer(" + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(out) + ")";
            act.runOnUiThread(() -> act.web.evaluateJavascript(js, null));
        }).start();
    }

    @JavascriptInterface
    public String version() {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}

package com.nofar.packing;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.webkit.JavascriptInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** What the page can ask the phone to do: keep data in files, copy/paste, schedule reminders. */
public class Bridge {
    private final Context ctx;

    Bridge(Context c) { ctx = c.getApplicationContext(); }

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

    @JavascriptInterface
    public String version() {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}

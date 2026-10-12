package com.nofar.packing;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;

import java.io.File;

public class MainActivity extends Activity {
    private static final int PICK = 7;
    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private Uri camUri;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Notifs.ensureChannel(this);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(this), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                    pick.setType("image/*");
                    pick.addCategory(Intent.CATEGORY_OPENABLE);
                    Intent chooser = Intent.createChooser(pick, "תמונה של הפריט בתיק");
                    try {
                        File dir = new File(getCacheDir(), "cam");
                        dir.mkdirs();
                        File f = new File(dir, "shot_" + System.currentTimeMillis() + ".jpg");
                        camUri = FileProvider.getUriForFile(MainActivity.this, "com.nofar.packing.files", f);
                        Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        cam.putExtra(MediaStore.EXTRA_OUTPUT, camUri);
                        cam.setClipData(ClipData.newRawUri("", camUri));
                        cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Parcelable[]{cam});
                    } catch (Exception e) {
                        camUri = null;
                    }
                    startActivityForResult(chooser, PICK);
                } catch (Exception e) {
                    fileCb.onReceiveValue(null);
                    fileCb = null;
                }
                return true;
            }
        });
        if (b == null) web.loadUrl("file:///android_asset/index.html");
        else web.restoreState(b);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != PICK || fileCb == null) return;
        Uri[] out = null;
        if (res == RESULT_OK) {
            if (data != null && data.getData() != null) out = new Uri[]{data.getData()};
            else if (camUri != null) out = new Uri[]{camUri};
        }
        fileCb.onReceiveValue(out);
        fileCb = null;
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.orzBack&&window.orzBack())?1:0", v -> {
            if (!"1".equals(v)) finish();
        });
    }
}

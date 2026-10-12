# אורזת (packing-app)

Personal Android packing-list app. The whole UI is one web page (`web/app.html`, wrapped into
`app/src/main/assets/index.html`) shown in a WebView; the Java side stores data in files,
picks/takes photos and schedules reminders. Every push to `main` builds an APK and publishes it
under Releases.

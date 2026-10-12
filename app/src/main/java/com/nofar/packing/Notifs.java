package com.nofar.packing;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

/** Reminders: the page sends the full list each time it changes; we replace all alarms with it. */
final class Notifs {
    static final String CHANNEL = "packing";
    private static final int MAX_ID = 5;

    static void ensureChannel(Context c) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "תזכורות אריזה", NotificationManager.IMPORTANCE_HIGH);
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private static PendingIntent pi(Context c, int id, String title, String text) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.putExtra("id", id);
        i.putExtra("title", title);
        i.putExtra("text", text);
        return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c, String json) {
        c.getSharedPreferences("notifs", Context.MODE_PRIVATE).edit().putString("list", json).apply();
        apply(c, json);
    }

    static void reschedule(Context c) {
        apply(c, c.getSharedPreferences("notifs", Context.MODE_PRIVATE).getString("list", "[]"));
    }

    private static void apply(Context c, String json) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        for (int id = 1; id <= MAX_ID; id++) am.cancel(pi(c, id, "", ""));
        try {
            JSONArray a = new JSONArray(json);
            long now = System.currentTimeMillis();
            for (int k = 0; k < a.length(); k++) {
                JSONObject o = a.getJSONObject(k);
                int id = o.getInt("id");
                long at = o.getLong("at");
                if (id < 1 || id > MAX_ID || at <= now) continue;
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, id, o.optString("title"), o.optString("text")));
            }
        } catch (Exception ignored) {
        }
    }
}

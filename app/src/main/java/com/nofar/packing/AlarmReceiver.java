package com.nofar.packing;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        Notifs.ensureChannel(c);
        String title = in.getStringExtra("title"), text = in.getStringExtra("text");
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, Notifs.CHANNEL)
                .setSmallIcon(R.drawable.ic_small)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        try {
            c.getSystemService(NotificationManager.class).notify(in.getIntExtra("id", 1), n);
        } catch (Exception ignored) {
        }
    }
}

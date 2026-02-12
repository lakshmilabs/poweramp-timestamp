package com.poweramp.timestamp;

import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;

public class PowerAmpNotificationListener extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn.getPackageName().equals("com.maxmpz.audioplayer")) {
            Notification notification = sbn.getNotification();
            if (notification.extras != null) {
                Bundle extras = notification.extras;
                
                String title = extras.getString(Notification.EXTRA_TITLE, "");
                String text = extras.getString(Notification.EXTRA_TEXT, "");
                String subText = extras.getString(Notification.EXTRA_SUB_TEXT, "");
                
                // Save to SharedPreferences so FloatingButtonService can read it
                SharedPreferences prefs = getSharedPreferences("poweramp_data", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("notification_title", title);
                editor.putString("notification_text", text);
                editor.putString("notification_subtext", subText);
                editor.putLong("last_update", System.currentTimeMillis());
                editor.apply();
            }
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Optional: handle when notification is removed
    }
}

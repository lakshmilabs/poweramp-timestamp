package com.poweramp.timestamp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

public class PowerAmpReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        Bundle extras = intent.getExtras();
        if (extras != null) {
            // Get playback position in milliseconds
            int position = extras.getInt("pos", -1);
            
            // Get track info
            String track = extras.getString("track", "");
            String path = extras.getString("path", "");
            
            // Save to SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences("poweramp_data", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            if (position >= 0) {
                editor.putLong("playback_position", position);
            }
            
            if (!track.isEmpty()) {
                editor.putString("broadcast_track", track);
            }
            
            if (!path.isEmpty()) {
                editor.putString("broadcast_path", path);
            }
            
            editor.putLong("last_broadcast_time", System.currentTimeMillis());
            editor.apply();
        }
    }
}

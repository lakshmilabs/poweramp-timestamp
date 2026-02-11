package com.poweramp.timestamp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class PowerAmpReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (action == null) return;

        // Get track info from PowerAmp broadcast
        Bundle extras = intent.getExtras();
        if (extras != null) {
            // Try to get track path/name
            String path = extras.getString("path", "");
            String track = extras.getString("track", "");
            
            // Get playback position in milliseconds
            int position = extras.getInt("pos", 0);
            
            // Update the service with this info
            Intent serviceIntent = new Intent(context, FloatingButtonService.class);
            serviceIntent.putExtra("filename", !path.isEmpty() ? path : track);
            serviceIntent.putExtra("position", position);
            
            // Send to service via static method
            updateServiceInfo(context, !path.isEmpty() ? path : track, position);
        }
    }

    private void updateServiceInfo(Context context, String filename, int position) {
        // Access the running service instance
        if (FloatingButtonService.isRunning()) {
            // Store in shared preferences as a fallback
            context.getSharedPreferences("poweramp_data", Context.MODE_PRIVATE)
                    .edit()
                    .putString("current_file", filename)
                    .putLong("current_position", position)
                    .apply();
        }
    }
}

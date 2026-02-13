package com.poweramp.timestamp;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

public class FloatingButtonService extends Service {

    private static final String TAG = "FloatingButtonService";
    private static boolean running = false;
    private WindowManager windowManager;
    private View floatingView;
    private String currentFilename = "";
    private PowerAmpBroadcastReceiver powerAmpReceiver;
    private Handler handler = new Handler(Looper.getMainLooper());

    private static final String POWERAMP_PACKAGE = "com.maxmpz.audioplayer";
    private static final String ACTION_API_COMMAND = POWERAMP_PACKAGE + ".API_COMMAND";
    private static final String ACTION_TRACK_POS_SYNC = POWERAMP_PACKAGE + ".TPOS_SYNC";
    private static final String EXTRA_COMMAND = "cmd";
    private static final int COMMAND_POS_SYNC = 16;

    public static boolean isRunning() {
        return running;
    }

    private class PowerAmpBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            Log.d(TAG, "📻 Received broadcast: " + action);
            
            Bundle extras = intent.getExtras();
            if (extras != null) {
                // ONLY CHANGE: PowerAmp sends position in SECONDS, convert to MILLISECONDS
                int positionSeconds = extras.getInt("pos", -1);
                long positionMs = -1;
                
                if (positionSeconds >= 0) {
                    positionMs = positionSeconds * 1000L;  // Convert seconds to milliseconds
                    Log.d(TAG, "✓ Position: " + positionSeconds + "s = " + positionMs + "ms = " + formatTime(positionMs));
                }
                
                // Get track info
                String track = extras.getString("track", "");
                String path = extras.getString("path", "");
                
                // Save to SharedPreferences
                SharedPreferences prefs = context.getSharedPreferences("poweramp_data", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                
                if (positionMs >= 0) {
                    editor.putLong("playback_position", positionMs);
                    editor.putLong("last_position_update", System.currentTimeMillis());
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

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        Log.d(TAG, "🚀 Service created");

        // Register PowerAmp broadcast receiver DYNAMICALLY
        powerAmpReceiver = new PowerAmpBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.maxmpz.audioplayer.STATUS_CHANGED");
        filter.addAction("com.maxmpz.audioplayer.PLAYING_MODE_CHANGED");
        filter.addAction("com.maxmpz.audioplayer.TRACK_CHANGED");
        filter.addAction(ACTION_TRACK_POS_SYNC);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerAmpReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(powerAmpReceiver, filter);
        }
        Log.d(TAG, "✓ Registered PowerAmp broadcast receiver");

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null);
        ImageButton btnTimestamp = floatingView.findViewById(R.id.btnTimestamp);
        
        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O 
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 20;
        params.y = 300;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        btnTimestamp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "🖱️ Button clicked!");
                
                if (!isNotificationAccessGranted()) {
                    Toast.makeText(FloatingButtonService.this, 
                        "⚠️ Notification Access needed!\n\nOpening settings...", 
                        Toast.LENGTH_LONG).show();
                    
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                    }, 1500);
                    return;
                }
                
                saveTimestamp();
            }
        });

        windowManager.addView(floatingView, params);
        startForeground(1, createNotification());
        
        if (!isNotificationAccessGranted()) {
            Toast.makeText(this, "⚠️ Tap button to enable Notification Access", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "✓ Ready! Tap to save timestamps", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isNotificationAccessGranted() {
        try {
            String enabledListeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            return enabledListeners != null && enabledListeners.contains(getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, "poweramp_timestamp_channel")
                .setContentTitle("PowerAmp Timestamp")
                .setContentText("Tap to save")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void saveTimestamp() {
        SharedPreferences prefs = getSharedPreferences("poweramp_data", Context.MODE_PRIVATE);
        
        // Request fresh position
        long requestTime = System.currentTimeMillis();
        Intent requestIntent = new Intent(ACTION_API_COMMAND);
        requestIntent.putExtra(EXTRA_COMMAND, COMMAND_POS_SYNC);
        requestIntent.setPackage(POWERAMP_PACKAGE);
        startService(requestIntent);
        Log.d(TAG, "📡 Requested position sync");
        
        // Wait for update with timeout
        long timeout = requestTime + 2000; // 2 seconds max wait
        boolean updated = false;
        while (System.currentTimeMillis() < timeout) {
            long lastUpdate = prefs.getLong("last_position_update", 0);
            if (lastUpdate > requestTime) {
                updated = true;
                Log.d(TAG, "✓ Position updated after request");
                break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (!updated) {
            Toast.makeText(this, "⚠️ Position update timed out", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "❌ Position sync timeout");
            return;
        }
        
        // Proceed with the rest (get title, text, etc.)
        String title = prefs.getString("notification_title", "");
        String text = prefs.getString("notification_text", "");
        String subText = prefs.getString("notification_subtext", "");
        
        long position = prefs.getLong("playback_position", 0);
        
        Log.d(TAG, "📊 DEBUG INFO:");
        Log.d(TAG, "  Title: " + title);
        Log.d(TAG, "  Text: " + text);
        Log.d(TAG, "  SubText: " + subText);
        Log.d(TAG, "  Position: " + position + " ms");
        
        // Find filename (UNCHANGED - original logic)
        String found = "";
        if (!title.isEmpty() && !title.startsWith("content://")) found = title;
        else if (!text.isEmpty() && !text.startsWith("content://")) found = text;
        else if (!subText.isEmpty() && !subText.startsWith("content://")) found = subText;
        
        if (found.isEmpty()) {
            // Try broadcast track info as fallback
            String broadcastTrack = prefs.getString("broadcast_track", "");
            String broadcastPath = prefs.getString("broadcast_path", "");
            
            if (!broadcastTrack.isEmpty()) found = broadcastTrack;
            else if (!broadcastPath.isEmpty()) found = broadcastPath;
        }
        
        if (found.isEmpty()) {
            Toast.makeText(this, "❌ No track detected", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "❌ No track found!");
            return;
        }
        
        currentFilename = cleanFilename(found);
        String timestamp = formatTime(position);
        
        Log.d(TAG, "💾 Saving timestamp: " + timestamp + " to file: " + currentFilename + ".txt");
        Toast.makeText(this, "💾 Saving: " + timestamp, Toast.LENGTH_SHORT).show();
        
        File dir = new File("/storage/emulated/0/_Edit-times");
        dir.mkdirs();
        File file = new File(dir, currentFilename + ".txt");
        
        try {
            if (!file.exists()) {
                FileOutputStream fos = new FileOutputStream(file);
                fos.write((currentFilename + "\n" + timestamp + "\n").getBytes());
                fos.close();
                Toast.makeText(this, "✅ Created file!", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "✅ Created new file: " + file.getAbsolutePath());
            } else {
                FileOutputStream fos = new FileOutputStream(file, true);
                fos.write((timestamp + "\n").getBytes());
                fos.close();
                Toast.makeText(this, "✅ Saved: " + timestamp, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "✅ Appended to file: " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            Toast.makeText(this, "❌ Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "❌ Error saving file", e);
        }
    }

    private String cleanFilename(String filename) {
        if (filename == null || filename.isEmpty()) return "";
        if (filename.contains("/")) filename = filename.substring(filename.lastIndexOf("/") + 1);
        if (filename.contains(".")) filename = filename.substring(0, filename.lastIndexOf("."));
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        
        // Unregister the broadcast receiver
        if (powerAmpReceiver != null) {
            try {
                unregisterReceiver(powerAmpReceiver);
                Log.d(TAG, "✓ Unregistered PowerAmp receiver");
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering receiver", e);
            }
        }
        
        if (floatingView != null) {
            try { 
                windowManager.removeView(floatingView); 
                Log.d(TAG, "✓ Removed floating view");
            } catch (Exception e) {
                Log.e(TAG, "Error removing view", e);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

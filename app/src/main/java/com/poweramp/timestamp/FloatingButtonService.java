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

    public static boolean isRunning() {
        return running;
    }

    private class PowerAmpBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            Log.e(TAG, "╔═══════════════════════════════════════════════════════════╗");
            Log.e(TAG, "║  POWERAMP BROADCAST RECEIVED                              ║");
            Log.e(TAG, "╚═══════════════════════════════════════════════════════════╝");
            Log.e(TAG, "Action: " + action);
            
            Bundle extras = intent.getExtras();
            if (extras == null) {
                Log.e(TAG, "❌ NO EXTRAS IN BROADCAST!");
                return;
            }

            Log.e(TAG, "");
            Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Log.e(TAG, "ALL EXTRAS (COPY THIS ENTIRE SECTION!):");
            Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            // Log EVERY single extra
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                String type = value != null ? value.getClass().getSimpleName() : "null";
                Log.e(TAG, String.format("  %-20s = %-30s (%s)", key, value, type));
            }
            
            Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Log.e(TAG, "");
            
            // Try to find position in MULTIPLE possible fields
            long position = -1;
            String positionSource = "NOT_FOUND";
            
            // Try all possible position field names
            String[] possibleKeys = {
                "pos", "position", "currentPosition", "current_position",
                "playbackPosition", "playback_position", "trackPosition", "track_position",
                "time", "currentTime", "current_time", "elapsed", "elapsedTime",
                "positionMs", "position_ms", "timeMs", "time_ms"
            };
            
            for (String key : possibleKeys) {
                if (extras.containsKey(key)) {
                    Object val = extras.get(key);
                    if (val instanceof Integer) {
                        long testPos = ((Integer) val).longValue();
                        Log.e(TAG, "🔍 Found '" + key + "' = " + testPos);
                        
                        // Use the first one we find, or the largest value
                        if (position < 0 || testPos > position) {
                            position = testPos;
                            positionSource = key;
                        }
                    } else if (val instanceof Long) {
                        long testPos = (Long) val;
                        Log.e(TAG, "🔍 Found '" + key + "' = " + testPos);
                        
                        if (position < 0 || testPos > position) {
                            position = testPos;
                            positionSource = key;
                        }
                    }
                }
            }
            
            // Get other useful fields
            String track = extras.getString("track", "");
            String path = extras.getString("path", "");
            
            // Try multiple ways to determine if playing
            boolean isPlaying = false;
            if (extras.containsKey("playing")) {
                isPlaying = extras.getBoolean("playing", false);
            } else if (extras.containsKey("paused")) {
                isPlaying = !extras.getBoolean("paused", true);
            } else if (extras.containsKey("state")) {
                int state = extras.getInt("state", -1);
                isPlaying = (state == 1 || state == 3); // Common playing states
            }
            
            // Get duration if available
            long duration = -1;
            if (extras.containsKey("duration")) {
                duration = extras.getInt("duration", -1);
            } else if (extras.containsKey("trackDuration")) {
                duration = extras.getInt("trackDuration", -1);
            }
            
            Log.e(TAG, "╔═══════════════════════════════════════════════════════════╗");
            Log.e(TAG, "║  EXTRACTED VALUES                                         ║");
            Log.e(TAG, "╚═══════════════════════════════════════════════════════════╝");
            Log.e(TAG, "Position:        " + position + " ms (from field: '" + positionSource + "')");
            Log.e(TAG, "Formatted:       " + formatTime(position));
            Log.e(TAG, "Duration:        " + duration + " ms");
            Log.e(TAG, "Track:           " + track);
            Log.e(TAG, "Path:            " + path);
            Log.e(TAG, "Is Playing:      " + isPlaying);
            Log.e(TAG, "");
            
            // Save to SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences("poweramp_data", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            if (position >= 0) {
                editor.putLong("playback_position", position);
                editor.putString("position_source", positionSource);
                editor.putLong("position_timestamp", System.currentTimeMillis());
                Log.e(TAG, "✅ SAVED POSITION: " + position + " ms");
            } else {
                Log.e(TAG, "❌ NO POSITION FOUND IN ANY FIELD!");
            }
            
            if (duration >= 0) {
                editor.putLong("track_duration", duration);
            }
            
            if (!track.isEmpty()) {
                editor.putString("broadcast_track", track);
            }
            
            if (!path.isEmpty()) {
                editor.putString("broadcast_path", path);
            }
            
            editor.putBoolean("is_playing", isPlaying);
            editor.putLong("last_broadcast_time", System.currentTimeMillis());
            editor.apply();
            
            Log.e(TAG, "╚═══════════════════════════════════════════════════════════╝");
            Log.e(TAG, "");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;

        powerAmpReceiver = new PowerAmpBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.maxmpz.audioplayer.STATUS_CHANGED");
        filter.addAction("com.maxmpz.audioplayer.PLAYING_MODE_CHANGED");
        filter.addAction("com.maxmpz.audioplayer.TRACK_CHANGED");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerAmpReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(powerAmpReceiver, filter);
        }

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
                Log.e(TAG, "");
                Log.e(TAG, "🖱️🖱️🖱️ BUTTON CLICKED! 🖱️🖱️🖱️");
                Log.e(TAG, "");
                
                if (!isNotificationAccessGranted()) {
                    showToast("⚠️ Notification Access needed!");
                    handler.postDelayed(new Runnable() {
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
        
        showToast("✓ Debug mode - check logs!");
    }

    private void showToast(final String message) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FloatingButtonService.this, message, Toast.LENGTH_SHORT).show();
            }
        });
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
                .setContentTitle("PowerAmp Timestamp DEBUG")
                .setContentText("Check logs")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void saveTimestamp() {
        SharedPreferences prefs = getSharedPreferences("poweramp_data", Context.MODE_PRIVATE);
        
        long position = prefs.getLong("playback_position", 0);
        String posSource = prefs.getString("position_source", "UNKNOWN");
        long duration = prefs.getLong("track_duration", -1);
        
        Log.e(TAG, "╔═══════════════════════════════════════════════════════════╗");
        Log.e(TAG, "║  SAVING TIMESTAMP                                         ║");
        Log.e(TAG, "╚═══════════════════════════════════════════════════════════╝");
        Log.e(TAG, "Position to save: " + position + " ms (from '" + posSource + "')");
        Log.e(TAG, "Formatted:        " + formatTime(position));
        Log.e(TAG, "Track duration:   " + duration + " ms");
        Log.e(TAG, "");
        
        // Get filename
        String title = prefs.getString("notification_title", "");
        String text = prefs.getString("notification_text", "");
        String subText = prefs.getString("notification_subtext", "");
        String broadcastTrack = prefs.getString("broadcast_track", "");
        String broadcastPath = prefs.getString("broadcast_path", "");
        
        String found = "";
        if (!title.isEmpty() && !title.startsWith("content://")) found = title;
        else if (!text.isEmpty() && !text.startsWith("content://")) found = text;
        else if (!subText.isEmpty() && !subText.startsWith("content://")) found = subText;
        else if (!broadcastTrack.isEmpty()) found = broadcastTrack;
        else if (!broadcastPath.isEmpty()) found = broadcastPath;
        
        if (found.isEmpty()) {
            showToast("❌ No track detected");
            Log.e(TAG, "❌ No filename found!");
            return;
        }
        
        currentFilename = cleanFilename(found);
        String timestamp = formatTime(position);
        
        Log.e(TAG, "Filename:         " + currentFilename);
        Log.e(TAG, "Timestamp:        " + timestamp);
        
        showToast("💾 " + timestamp + " (from:" + posSource + ")");
        
        File dir = new File("/storage/emulated/0/_Edit-times");
        dir.mkdirs();
        File file = new File(dir, currentFilename + ".txt");
        
        try {
            if (!file.exists()) {
                FileOutputStream fos = new FileOutputStream(file);
                fos.write((currentFilename + "\n" + timestamp + "\n").getBytes());
                fos.close();
                Log.e(TAG, "✅ File created: " + file.getAbsolutePath());
            } else {
                FileOutputStream fos = new FileOutputStream(file, true);
                fos.write((timestamp + "\n").getBytes());
                fos.close();
                Log.e(TAG, "✅ Appended to: " + file.getAbsolutePath());
            }
            showToast("✅ Saved: " + timestamp);
            Log.e(TAG, "╚═══════════════════════════════════════════════════════════╝");
            Log.e(TAG, "");
        } catch (Exception e) {
            showToast("❌ Error saving");
            Log.e(TAG, "❌ Error: " + e.getMessage());
            e.printStackTrace();
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
        
        if (powerAmpReceiver != null) {
            try {
                unregisterReceiver(powerAmpReceiver);
            } catch (Exception e) {}
        }
        
        if (floatingView != null) {
            try { 
                windowManager.removeView(floatingView);
            } catch (Exception e) {}
        }
        
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    }

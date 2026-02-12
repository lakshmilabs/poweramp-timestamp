package com.poweramp.timestamp;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
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
    private TextView debugTextView;
    private String currentFilename = "";
    private PowerAmpBroadcastReceiver powerAmpReceiver;
    private Handler handler = new Handler(Looper.getMainLooper());
    private StringBuilder debugInfo = new StringBuilder();
    private int broadcastCount = 0;

    public static boolean isRunning() {
        return running;
    }

    private class PowerAmpBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            broadcastCount++;
            
            Bundle extras = intent.getExtras();
            if (extras == null) {
                updateDebugText("❌ Broadcast #" + broadcastCount + " - NO EXTRAS!");
                return;
            }

            // Collect all extras
            StringBuilder extrasText = new StringBuilder();
            extrasText.append("📻 Broadcast #").append(broadcastCount).append("\n");
            extrasText.append("Action: ").append(action.substring(action.lastIndexOf(".") + 1)).append("\n\n");
            extrasText.append("ALL FIELDS:\n");
            
            long position = -1;
            String posField = "NONE";
            
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                String type = value != null ? value.getClass().getSimpleName() : "null";
                extrasText.append("• ").append(key).append(" = ").append(value).append(" (").append(type).append(")\n");
                
                // Try to find position
                if (value instanceof Integer || value instanceof Long) {
                    long val = value instanceof Integer ? ((Integer)value).longValue() : (Long)value;
                    if (key.toLowerCase().contains("pos") || 
                        key.toLowerCase().contains("time") || 
                        key.toLowerCase().contains("elapsed")) {
                        if (val > position) {
                            position = val;
                            posField = key;
                        }
                    }
                }
            }
            
            extrasText.append("\n🎯 BEST POSITION GUESS:\n");
            extrasText.append("Field: ").append(posField).append("\n");
            extrasText.append("Value: ").append(position).append(" ms\n");
            extrasText.append("Time: ").append(formatTime(position));
            
            updateDebugText(extrasText.toString());
            
            // Save to SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences("poweramp_data", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            if (position >= 0) {
                editor.putLong("playback_position", position);
                editor.putString("position_field", posField);
                editor.putLong("position_timestamp", System.currentTimeMillis());
            }
            
            String track = extras.getString("track", "");
            if (!track.isEmpty()) {
                editor.putString("broadcast_track", track);
            }
            
            editor.putLong("last_broadcast_time", System.currentTimeMillis());
            editor.putInt("broadcast_count", broadcastCount);
            editor.apply();
            
            showToast("📻 Broadcast #" + broadcastCount + " - Pos: " + formatTime(position));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;

        // Register receiver
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

        // Create floating view with debug info
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Color.parseColor("#CC000000"));
        container.setPadding(16, 16, 16, 16);
        
        // Button
        ImageButton btnTimestamp = new ImageButton(this);
        btnTimestamp.setId(View.generateViewId());
        btnTimestamp.setImageResource(android.R.drawable.ic_input_add);
        btnTimestamp.setBackgroundColor(Color.parseColor("#FF6200EE"));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(150, 150);
        btnParams.gravity = Gravity.CENTER;
        btnTimestamp.setLayoutParams(btnParams);
        
        // Debug text
        debugTextView = new TextView(this);
        debugTextView.setTextColor(Color.WHITE);
        debugTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        debugTextView.setText("Waiting for PowerAmp...\n\nPlay music and watch this area!");
        debugTextView.setPadding(8, 8, 8, 8);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        debugTextView.setLayoutParams(textParams);
        debugTextView.setMaxLines(15);
        
        container.addView(btnTimestamp);
        container.addView(debugTextView);
        
        floatingView = container;
        
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
        params.y = 100;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        btnTimestamp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveTimestamp();
            }
        });

        windowManager.addView(floatingView, params);
        startForeground(1, createNotification());
        
        showToast("🔍 VISUAL DEBUG MODE - Watch the black box!");
        updateDebugText("Waiting for PowerAmp...\n\nBroadcasts received: 0\n\nPlay music in PowerAmp!");
    }

    private void updateDebugText(final String text) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (debugTextView != null) {
                    debugTextView.setText(text);
                }
            }
        });
    }

    private void showToast(final String message) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FloatingButtonService.this, message, Toast.LENGTH_LONG).show();
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
                .setContentTitle("PowerAmp DEBUG")
                .setContentText("Visual mode")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void saveTimestamp() {
        SharedPreferences prefs = getSharedPreferences("poweramp_data", Context.MODE_PRIVATE);
        
        long position = prefs.getLong("playback_position", 0);
        String posField = prefs.getString("position_field", "NONE");
        int broadcasts = prefs.getInt("broadcast_count", 0);
        
        StringBuilder saveInfo = new StringBuilder();
        saveInfo.append("💾 SAVING:\n\n");
        saveInfo.append("Broadcasts received: ").append(broadcasts).append("\n");
        saveInfo.append("Position field: ").append(posField).append("\n");
        saveInfo.append("Position value: ").append(position).append(" ms\n");
        saveInfo.append("Formatted: ").append(formatTime(position)).append("\n\n");
        
        updateDebugText(saveInfo.toString());
        
        // Get filename
        String title = prefs.getString("notification_title", "");
        String text = prefs.getString("notification_text", "");
        String broadcastTrack = prefs.getString("broadcast_track", "");
        
        String found = "";
        if (!title.isEmpty()) found = title;
        else if (!text.isEmpty()) found = text;
        else if (!broadcastTrack.isEmpty()) found = broadcastTrack;
        else found = "unknown";
        
        currentFilename = cleanFilename(found);
        String timestamp = formatTime(position);
        
        showToast("💾 Saving: " + timestamp + "\nFrom field: " + posField);
        
        File dir = new File("/storage/emulated/0/_Edit-times");
        dir.mkdirs();
        File file = new File(dir, currentFilename + ".txt");
        
        try {
            if (!file.exists()) {
                FileOutputStream fos = new FileOutputStream(file);
                fos.write((currentFilename + "\n" + timestamp + "\n").getBytes());
                fos.close();
            } else {
                FileOutputStream fos = new FileOutputStream(file, true);
                fos.write((timestamp + "\n").getBytes());
                fos.close();
            }
            
            saveInfo.append("✅ SAVED to:\n").append(file.getName());
            updateDebugText(saveInfo.toString());
            showToast("✅ Saved: " + timestamp);
        } catch (Exception e) {
            saveInfo.append("❌ ERROR:\n").append(e.getMessage());
            updateDebugText(saveInfo.toString());
            showToast("❌ Error: " + e.getMessage());
        }
    }

    private String cleanFilename(String filename) {
        if (filename == null || filename.isEmpty()) return "unknown";
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
        

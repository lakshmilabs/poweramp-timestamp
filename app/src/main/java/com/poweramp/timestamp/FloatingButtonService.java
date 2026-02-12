package com.poweramp.timestamp;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
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

    private static boolean running = false;
    private WindowManager windowManager;
    private View floatingView;
    private String currentFilename = "";

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;

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
        
        // Get filename from notification listener
        String title = prefs.getString("notification_title", "");
        String text = prefs.getString("notification_text", "");
        String subText = prefs.getString("notification_subtext", "");
        
        // Get playback position from broadcast receiver
        long position = prefs.getLong("playback_position", 0);
        
        // Find filename
        String found = "";
        if (!title.isEmpty() && !title.startsWith("content://")) found = title;
        else if (!text.isEmpty() && !text.startsWith("content://")) found = text;
        else if (!subText.isEmpty() && !subText.startsWith("content://")) found = subText;
        
        if (found.isEmpty()) {
            Toast.makeText(this, "❌ No track detected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        currentFilename = cleanFilename(found);
        String timestamp = formatTime(position);
        
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
            } else {
                FileOutputStream fos = new FileOutputStream(file, true);
                fos.write((timestamp + "\n").getBytes());
                fos.close();
                Toast.makeText(this, "✅ Saved: " + timestamp, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "❌ Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
        if (floatingView != null) {
            try { windowManager.removeView(floatingView); } catch (Exception e) {}
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
            }

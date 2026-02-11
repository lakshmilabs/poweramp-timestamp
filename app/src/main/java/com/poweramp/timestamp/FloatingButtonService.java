package com.poweramp.timestamp;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

public class FloatingButtonService extends Service {

    private static boolean running = false;
    private WindowManager windowManager;
    private View floatingView;
    private Handler handler;
    private Runnable updateTrackInfoRunnable;
    
    private String currentFilename = "";
    private long currentPosition = 0;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;

        // Create floating button
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null);
        
        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 20;
        params.y = 300;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        ImageButton btnTimestamp = floatingView.findViewById(R.id.btnTimestamp);
        
        // Handle dragging AND clicking
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long touchStartTime;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        isDragging = false;
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;
                        
                        // If moved more than 10 pixels, it's a drag
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            isDragging = true;
                            params.x = initialX + (int) (initialTouchX - event.getRawX());
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(floatingView, params);
                        }
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                        long touchDuration = System.currentTimeMillis() - touchStartTime;
                        
                        // If touch was short and didn't drag, it's a click
                        if (!isDragging && touchDuration < 300) {
                            saveTimestamp();
                        }
                        return true;
                }
                return false;
            }
        });

        // Add the button immediately
        try {
            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error showing button: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        // Update track info periodically
        handler = new Handler(Looper.getMainLooper());
        updateTrackInfoRunnable = new Runnable() {
            @Override
            public void run() {
                updateCurrentTrackInfo();
                handler.postDelayed(this, 2000); // Check every 2 seconds
            }
        };
        handler.post(updateTrackInfoRunnable);

        // Start as foreground service
        startForeground(1, createNotification());
        
        Toast.makeText(this, "Floating button active\nTap to save, drag to move", Toast.LENGTH_LONG).show();
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, "poweramp_timestamp_channel")
                .setContentTitle("PowerAmp Timestamp")
                .setContentText("Floating button active - Tap to save timestamps")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void updateCurrentTrackInfo() {
        // Try to get info from PowerAmp notification (this works with "Open with")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                StatusBarNotification[] notifications = nm.getActiveNotifications();
                
                for (StatusBarNotification sbn : notifications) {
                    if (sbn.getPackageName().equals("com.maxmpz.audioplayer")) {
                        Notification notification = sbn.getNotification();
                        if (notification.extras != null) {
                            // Try EXTRA_TITLE first (this should have the filename)
                            String title = notification.extras.getString(Notification.EXTRA_TITLE, "");
                            
                            // If title is empty or looks like a content URI, try EXTRA_TEXT
                            if (title.isEmpty() || title.startsWith("content://")) {
                                title = notification.extras.getString(Notification.EXTRA_TEXT, "");
                            }
                            
                            // Try subtext as another fallback
                            if (title.isEmpty() || title.startsWith("content://")) {
                                title = notification.extras.getString(Notification.EXTRA_SUB_TEXT, "");
                            }
                            
                            if (!title.isEmpty() && !title.startsWith("content://")) {
                                String cleanedFilename = cleanFilename(title);
                                if (!cleanedFilename.equals("unknown")) {
                                    currentFilename = cleanedFilename;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        // Also try shared preferences (from PowerAmpReceiver broadcasts)
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("poweramp_data", Context.MODE_PRIVATE);
            String filename = prefs.getString("current_file", "");
            long position = prefs.getLong("current_position", 0);
            
            if (!filename.isEmpty() && !filename.startsWith("content://")) {
                String cleanedFilename = cleanFilename(filename);
                if (!cleanedFilename.equals("unknown")) {
                    currentFilename = cleanedFilename;
                }
            }
            
            if (position > 0) {
                currentPosition = position;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String cleanFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "unknown";
        }
        
        // Skip content URIs
        if (filename.startsWith("content://")) {
            return "unknown";
        }
        
        // Remove path
        if (filename.contains("/")) {
            filename = filename.substring(filename.lastIndexOf("/") + 1);
        }
        if (filename.contains("\\")) {
            filename = filename.substring(filename.lastIndexOf("\\") + 1);
        }
        
        // Remove extension
        if (filename.contains(".")) {
            int lastDot = filename.lastIndexOf(".");
            if (lastDot > 0) {
                filename = filename.substring(0, lastDot);
            }
        }
        
        // Remove invalid characters but keep more characters
        filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        filename = filename.trim();
        
        if (filename.isEmpty()) {
            filename = "unknown";
        }
        
        return filename;
    }

    private void saveTimestamp() {
        // Update track info one more time before saving
        updateCurrentTrackInfo();
        
        if (currentFilename.isEmpty() || currentFilename.equals("unknown")) {
            Toast.makeText(this, 
                "❌ No track detected\n\nMake sure PowerAmp is playing and the track name is visible in the notification", 
                Toast.LENGTH_LONG).show();
            return;
        }

        String timestamp = formatTime(currentPosition);
        
        // Correct path for user's device
        File dir = new File("/storage/emulated/0/_Edit-times");
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                Toast.makeText(this, "❌ Could not create folder\n/storage/emulated/0/_Edit-times", Toast.LENGTH_LONG).show();
                return;
            }
        }

        File file = new File(dir, currentFilename + ".txt");
        
        try {
            if (!file.exists()) {
                // Create new file with filename as first line
                FileOutputStream fos = new FileOutputStream(file);
                String content = currentFilename + "\n" + timestamp + "\n";
                fos.write(content.getBytes());
                fos.close();
                Toast.makeText(this, "✓ Created file:\n" + currentFilename + ".txt\n\nSaved: " + timestamp, Toast.LENGTH_SHORT).show();
            } else {
                // Append timestamp
                FileOutputStream fos = new FileOutputStream(file, true);
                String content = timestamp + "\n";
                fos.write(content.getBytes());
                fos.close();
                Toast.makeText(this, "✓ Saved: " + timestamp + "\n→ " + currentFilename + ".txt", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "❌ Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
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
        
        if (handler != null && updateTrackInfoRunnable != null) {
            handler.removeCallbacks(updateTrackInfoRunnable);
        }
        
        if (floatingView != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        Toast.makeText(this, "Floating button removed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

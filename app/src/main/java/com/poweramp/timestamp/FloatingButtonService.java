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
    private ImageButton btnTimestamp;
    private WindowManager.LayoutParams params;
    private Handler handler;
    private Runnable updateTrackInfoRunnable;
    
    private String currentFilename = "";
    private long currentPosition = 0;
    
    private int initialX;
    private int initialY;
    private float initialTouchX;
    private float initialTouchY;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;

        // Create floating button
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null);
        btnTimestamp = floatingView.findViewById(R.id.btnTimestamp);
        
        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
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
        
        // Simple touch handler on the whole view
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        
                        // If moved less than 10 pixels, it's a click
                        if (Math.abs(deltaX) < 10 && Math.abs(deltaY) < 10) {
                            saveTimestamp();
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (initialTouchX - event.getRawX());
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });

        // Add the button
        try {
            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        // Update track info periodically
        handler = new Handler(Looper.getMainLooper());
        updateTrackInfoRunnable = new Runnable() {
            @Override
            public void run() {
                updateCurrentTrackInfo();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(updateTrackInfoRunnable);

        // Start as foreground service
        startForeground(1, createNotification());
        
        Toast.makeText(this, "Button active - Tap to save, drag to move", Toast.LENGTH_LONG).show();
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, "poweramp_timestamp_channel")
                .setContentTitle("PowerAmp Timestamp")
                .setContentText("Button active")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void updateCurrentTrackInfo() {
        // Read from PowerAmp notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                StatusBarNotification[] notifications = nm.getActiveNotifications();
                
                for (StatusBarNotification sbn : notifications) {
                    if (sbn.getPackageName().equals("com.maxmpz.audioplayer")) {
                        Notification notification = sbn.getNotification();
                        if (notification.extras != null) {
                            String title = notification.extras.getString(Notification.EXTRA_TITLE, "");
                            
                            if (title.isEmpty() || title.startsWith("content://")) {
                                title = notification.extras.getString(Notification.EXTRA_TEXT, "");
                            }
                            
                            if (!title.isEmpty() && !title.startsWith("content://")) {
                                currentFilename = cleanFilename(title);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String cleanFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "unknown";
        }
        
        if (filename.startsWith("content://")) {
            return "unknown";
        }
        
        // Remove path
        if (filename.contains("/")) {
            filename = filename.substring(filename.lastIndexOf("/") + 1);
        }
        
        // Remove extension
        if (filename.contains(".")) {
            int lastDot = filename.lastIndexOf(".");
            if (lastDot > 0) {
                filename = filename.substring(0, lastDot);
            }
        }
        
        // Remove invalid characters
        filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        filename = filename.trim();
        
        if (filename.isEmpty()) {
            return "unknown";
        }
        
        return filename;
    }

    private void saveTimestamp() {
        // Force update track info
        updateCurrentTrackInfo();
        
        // Show debug info
        Toast.makeText(this, "Attempting to save...\nFilename: " + currentFilename, Toast.LENGTH_SHORT).show();
        
        if (currentFilename.isEmpty() || currentFilename.equals("unknown")) {
            Toast.makeText(this, 
                "No track detected\n\nMake sure PowerAmp notification is visible", 
                Toast.LENGTH_LONG).show();
            return;
        }

        String timestamp = formatTime(currentPosition);
        
        File dir = new File("/storage/emulated/0/_Edit-times");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File file = new File(dir, currentFilename + ".txt");
        
        try {
            if (!file.exists()) {
                FileOutputStream fos = new FileOutputStream(file);
                String content = currentFilename + "\n" + timestamp + "\n";
                fos.write(content.getBytes());
                fos.close();
                Toast.makeText(this, "Created: " + currentFilename + ".txt\n" + timestamp, Toast.LENGTH_LONG).show();
            } else {
                FileOutputStream fos = new FileOutputStream(file, true);
                String content = timestamp + "\n";
                fos.write(content.getBytes());
                fos.close();
                Toast.makeText(this, "Saved: " + timestamp, Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
        
        Toast.makeText(this, "Button removed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

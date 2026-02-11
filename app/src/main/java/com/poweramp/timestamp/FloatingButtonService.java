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
    private WindowManager.LayoutParams params;
    private Handler handler;
    private Runnable updateTrackInfoRunnable;
    
    private String currentFilename = "";
    
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

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null);
        
        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        // CRITICAL: Remove FLAG_NOT_FOCUSABLE to allow touches!
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 20;
        params.y = 300;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;
                        
                        if (Math.abs(deltaX) < 10 && Math.abs(deltaY) < 10) {
                            // It's a click!
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

        try {
            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        handler = new Handler(Looper.getMainLooper());
        updateTrackInfoRunnable = new Runnable() {
            @Override
            public void run() {
                updateCurrentTrackInfo();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(updateTrackInfoRunnable);

        startForeground(1, createNotification());
        
        Toast.makeText(this, "Button ready - Tap it to test!", Toast.LENGTH_LONG).show();
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
        if (filename == null || filename.isEmpty() || filename.startsWith("content://")) {
            return "";
        }
        
        if (filename.contains("/")) {
            filename = filename.substring(filename.lastIndexOf("/") + 1);
        }
        
        if (filename.contains(".")) {
            int lastDot = filename.lastIndexOf(".");
            if (lastDot > 0) {
                filename = filename.substring(0, lastDot);
            }
        }
        
        filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        return filename.trim();
    }

    private void saveTimestamp() {
        Toast.makeText(this, "BUTTON TAPPED!", Toast.LENGTH_SHORT).show();
        
        updateCurrentTrackInfo();
        
        if (currentFilename.isEmpty()) {
            Toast.makeText(this, "No track detected\nFilename is empty", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Detected: " + currentFilename, Toast.LENGTH_SHORT).show();
        
        // Get current timestamp (we'll use 00:00:00 for now since we don't have position)
        String timestamp = "00:00:00";
        
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
                Toast.makeText(this, "✓ Created: " + currentFilename + ".txt", Toast.LENGTH_LONG).show();
            } else {
                FileOutputStream fos = new FileOutputStream(file, true);
                String content = timestamp + "\n";
                fos.write(content.getBytes());
                fos.close();
                Toast.makeText(this, "✓ Saved: " + timestamp, Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "ERROR: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        
        if (handler != null) {
            handler.removeCallbacks(updateTrackInfoRunnable);
        }
        
        if (floatingView != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

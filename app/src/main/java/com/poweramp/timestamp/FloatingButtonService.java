package com.poweramp.timestamp;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FloatingButtonService extends Service {

    private static boolean running = false;
    private WindowManager windowManager;
    private View floatingView;
    private Handler handler;
    private Runnable checkPowerAmpRunnable;
    
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

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 100;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        ImageButton btnTimestamp = floatingView.findViewById(R.id.btnTimestamp);
        
        // Handle button click
        btnTimestamp.setOnClickListener(v -> saveTimestamp());
        
        // Handle dragging
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });

        // Start checking for PowerAmp
        handler = new Handler(Looper.getMainLooper());
        checkPowerAmpRunnable = new Runnable() {
            @Override
            public void run() {
                updateFloatingButtonVisibility();
                handler.postDelayed(this, 500); // Check every 500ms
            }
        };
        handler.post(checkPowerAmpRunnable);

        // Start as foreground service
        startForeground(1, createNotification());
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, "poweramp_timestamp_channel")
                .setContentTitle("PowerAmp Timestamp")
                .setContentText("Service is running")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void updateFloatingButtonVisibility() {
        boolean powerAmpVisible = isPowerAmpInForeground();
        
        if (powerAmpVisible) {
            if (floatingView.getParent() == null) {
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) floatingView.getLayoutParams();
                windowManager.addView(floatingView, params);
            }
            updateCurrentTrackInfo();
        } else {
            if (floatingView.getParent() != null) {
                windowManager.removeView(floatingView);
            }
        }
    }

    private boolean isPowerAmpInForeground() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
        
        if (!tasks.isEmpty()) {
            String topPackage = tasks.get(0).topActivity.getPackageName();
            return topPackage.equals("com.maxmpz.audioplayer");
        }
        return false;
    }

    private void updateCurrentTrackInfo() {
        // First try to get from shared preferences (from PowerAmpReceiver)
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("poweramp_data", Context.MODE_PRIVATE);
            String filename = prefs.getString("current_file", "");
            long position = prefs.getLong("current_position", 0);
            
            if (!filename.isEmpty()) {
                currentFilename = cleanFilename(filename);
                currentPosition = position;
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Fallback: Try to get info from notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                StatusBarNotification[] notifications = nm.getActiveNotifications();
                
                for (StatusBarNotification sbn : notifications) {
                    if (sbn.getPackageName().equals("com.maxmpz.audioplayer")) {
                        Notification notification = sbn.getNotification();
                        if (notification.extras != null) {
                            String title = notification.extras.getString(Notification.EXTRA_TITLE, "");
                            String text = notification.extras.getString(Notification.EXTRA_TEXT, "");
                            
                            // Try to extract filename from notification
                            if (!title.isEmpty()) {
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
        // Remove path and extension
        if (filename.contains("/")) {
            filename = filename.substring(filename.lastIndexOf("/") + 1);
        }
        if (filename.contains("\\")) {
            filename = filename.substring(filename.lastIndexOf("\\") + 1);
        }
        if (filename.contains(".")) {
            filename = filename.substring(0, filename.lastIndexOf("."));
        }
        return filename;
    }

    private void saveTimestamp() {
        if (currentFilename.isEmpty()) {
            Toast.makeText(this, "No track detected", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get current position from PowerAmp receiver data
        String timestamp = formatTime(currentPosition);
        
        File dir = new File("/sdcard/_Edit-times");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File file = new File(dir, currentFilename + ".txt");
        
        try {
            // Create file if doesn't exist and write filename on first line
            if (!file.exists()) {
                FileOutputStream fos = new FileOutputStream(file);
                String content = currentFilename + "\n" + timestamp + "\n";
                fos.write(content.getBytes());
                fos.close();
            } else {
                // Append timestamp
                FileOutputStream fos = new FileOutputStream(file, true);
                String content = timestamp + "\n";
                fos.write(content.getBytes());
                fos.close();
            }
            
            Toast.makeText(this, "Saved: " + timestamp, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_LONG).show();
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

    public void updateTrackInfo(String filename, long position) {
        if (filename != null && !filename.isEmpty()) {
            this.currentFilename = cleanFilename(filename);
        }
        this.currentPosition = position;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        
        if (handler != null && checkPowerAmpRunnable != null) {
            handler.removeCallbacks(checkPowerAmpRunnable);
        }
        
        if (floatingView != null && floatingView.getParent() != null) {
            windowManager.removeView(floatingView);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

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
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.FileOutputStream;

public class FloatingButtonService extends Service {

    private static boolean running = false;
    private WindowManager windowManager;
    private View floatingView;
    private Handler handler;
    private Runnable updateTrackInfoRunnable;
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
                0, // NO FLAGS - just to test
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 20;
        params.y = 300;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        // ONLY click listener - NO dragging for now
        btnTimestamp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(FloatingButtonService.this, "🎯 BUTTON WORKS!", Toast.LENGTH_LONG).show();
                saveTimestamp();
            }
        });

        windowManager.addView(floatingView, params);
        
        // Update track info
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
        Toast.makeText(this, "TEST VERSION - Button should be clickable now!", Toast.LENGTH_LONG).show();
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, "poweramp_timestamp_channel")
                .setContentTitle("PowerAmp Timestamp TEST")
                .setContentText("Testing button click")
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
                            if (!title.isEmpty() && !title.startsWith("content://")) {
                                currentFilename = cleanFilename(title);
                            }
                        }
                    }
                }
            } catch (Exception e) {}
        }
    }

    private String cleanFilename(String filename) {
        if (filename == null || filename.isEmpty()) return "";
        if (filename.contains("/")) filename = filename.substring(filename.lastIndexOf("/") + 1);
        if (filename.contains(".")) filename = filename.substring(0, filename.lastIndexOf("."));
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private void saveTimestamp() {
        if (currentFilename.isEmpty()) {
            Toast.makeText(this, "❌ No filename detected", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "📝 Filename: " + currentFilename, Toast.LENGTH_LONG).show();
        
        File dir = new File("/storage/emulated/0/_Edit-times");
        dir.mkdirs();
        File file = new File(dir, currentFilename + ".txt");
        
        try {
            FileOutputStream fos = new FileOutputStream(file, !file.exists());
            if (!file.exists()) {
                fos.write((currentFilename + "\n00:00:00\n").getBytes());
            } else {
                fos.write("00:00:00\n".getBytes());
            }
            fos.close();
            Toast.makeText(this, "✅ SAVED!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "❌ Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        if (handler != null) handler.removeCallbacks(updateTrackInfoRunnable);
        if (floatingView != null) {
            try { windowManager.removeView(floatingView); } catch (Exception e) {}
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

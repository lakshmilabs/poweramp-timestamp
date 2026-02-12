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
        
        // ONLY CLICK - NO DRAGGING
        btnTimestamp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(FloatingButtonService.this, "🔘 Button clicked!", Toast.LENGTH_SHORT).show();
                saveTimestamp();
            }
        });

        windowManager.addView(floatingView, params);
        
        // Update track info every 2 seconds
        handler = new Handler(Looper.getMainLooper());
        Runnable updateTask = new Runnable() {
            @Override
            public void run() {
                updateCurrentTrackInfo();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(updateTask);

        startForeground(1, createNotification());
        Toast.makeText(this, "✓ Button ready - just tap it!", Toast.LENGTH_LONG).show();
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

    private void updateCurrentTrackInfo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                StatusBarNotification[] notifications = nm.getActiveNotifications();
                
                for (StatusBarNotification sbn : notifications) {
                    if (sbn.getPackageName().equals("com.maxmpz.audioplayer")) {
                        Notification n = sbn.getNotification();
                        if (n.extras != null) {
                            String title = n.extras.getString(Notification.EXTRA_TITLE, "");
                            String text = n.extras.getString(Notification.EXTRA_TEXT, "");
                            String subText = n.extras.getString(Notification.EXTRA_SUB_TEXT, "");
                            
                            // Find first non-empty, non-content URI
                            String found = "";
                            if (!title.isEmpty() && !title.startsWith("content://")) found = title;
                            else if (!text.isEmpty() && !text.startsWith("content://")) found = text;
                            else if (!subText.isEmpty() && !subText.startsWith("content://")) found = subText;
                            
                            if (!found.isEmpty()) {
                                currentFilename = cleanFilename(found);
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
        if (filename == null || filename.isEmpty()) return "";
        if (filename.contains("/")) filename = filename.substring(filename.lastIndexOf("/") + 1);
        if (filename.contains(".")) filename = filename.substring(0, filename.lastIndexOf("."));
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private void saveTimestamp() {
        updateCurrentTrackInfo(); // Force check
        
        if (currentFilename.isEmpty()) {
            Toast.makeText(this, "❌ No track detected\n\nMake sure PowerAmp is playing", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "📝 File: " + currentFilename, Toast.LENGTH_SHORT).show();
        
        File dir = new File("/storage/emulated/0/_Edit-times");
        dir.mkdirs();
        File file = new File(dir, currentFilename + ".txt");
        
        try {
            String timestamp = "00:00:00";
            
            if (!file.exists()) {
                FileOutputStream fos = new FileOutputStream(file);
                fos.write((currentFilename + "\n" + timestamp + "\n").getBytes());
                fos.close();
                Toast.makeText(this, "✅ Created: " + currentFilename + ".txt", Toast.LENGTH_LONG).show();
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        if (handler != null) handler.removeCallbacks(null);
        if (floatingView != null) {
            try { windowManager.removeView(floatingView); } catch (Exception e) {}
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
            }

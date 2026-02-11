package com.poweramp.timestamp;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_STORAGE_PERMISSION = 1002;
    private static final int REQUEST_MANAGE_STORAGE = 1003;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1004;

    private Button btnStart;
    private Button btnStop;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        tvStatus = findViewById(R.id.tvStatus);

        createNotificationChannel();

        btnStart.setOnClickListener(v -> {
            if (checkPermissions()) {
                startFloatingService();
            }
        });

        btnStop.setOnClickListener(v -> stopFloatingService());

        updateStatus();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "poweramp_timestamp_channel",
                    "PowerAmp Timestamp Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps the floating button active");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private boolean checkPermissions() {
        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            return false;
        }

        // Check storage permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                return false;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_STORAGE_PERMISSION);
                return false;
            }
        }

        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION);
                return false;
            }
        }

        return true;
    }

    private void startFloatingService() {
        Intent intent = new Intent(this, FloatingButtonService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        updateStatus();
        Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show();
    }

    private void stopFloatingService() {
        Intent intent = new Intent(this, FloatingButtonService.class);
        stopService(intent);
        updateStatus();
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
    }

    private void updateStatus() {
        boolean isRunning = FloatingButtonService.isRunning();
        tvStatus.setText(isRunning ? "Service is running" : "Service is stopped");
        btnStart.setEnabled(!isRunning);
        btnStop.setEnabled(isRunning);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION || 
            requestCode == REQUEST_MANAGE_STORAGE) {
            if (checkPermissions()) {
                startFloatingService();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION || 
            requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (checkPermissions()) {
                startFloatingService();
            }
        }
    }
}

# PowerAmp Timestamp App

A simple Android app that shows a floating button when PowerAmp is running. Tap the button to save the current playback timestamp to a text file.

## Features

- 🎵 Floating button appears only when PowerAmp is on screen
- 📝 Saves timestamps in hh:mm:ss format
- 💾 Stores timestamps in `/sdcard/_Edit-times/[filename].txt`
- 🎯 Each file contains the basename (without path/extension) and timestamps

## Requirements

- Android 7.0 (API 24) or higher
- PowerAmp music player installed
- MIUI 14+ (or any Android ROM)

## Building the App

You have **two easy options** to build this app without Android Studio:

### Option 1: GitHub Actions (Easiest - Cloud Build)

1. **Create a GitHub repository:**
   - Go to [github.com](https://github.com) and create a new repository
   - Name it anything you like (e.g., "poweramp-timestamp")

2. **Upload this project:**
   - Download this entire folder as a ZIP
   - Go to your GitHub repository
   - Click "uploading an existing file"
   - Drag and drop all the files from this project

3. **GitHub will automatically build your APK!**
   - Go to the "Actions" tab in your repository
   - Wait for the build to complete (takes ~5 minutes)
   - Click on the completed build
   - Download the APK from "Artifacts" section

### Option 2: Build on Your Phone (Termux)

1. **Install Termux** from F-Droid (not Google Play):
   - Download F-Droid: https://f-droid.org/
   - Open F-Droid and install Termux

2. **Setup Termux** (copy and paste these commands):
   ```bash
   # Update packages
   pkg update && pkg upgrade -y
   
   # Install required packages
   pkg install -y openjdk-17 git wget unzip
   
   # Create workspace
   cd ~
   ```

3. **Transfer this project to your phone:**
   - Compress this folder to a ZIP file on your computer
   - Transfer the ZIP to your phone (via USB, cloud, etc.)
   - Move it to `/sdcard/Download/`
   
4. **Build in Termux:**
   ```bash
   # Go to Downloads folder
   cd /sdcard/Download
   
   # Unzip the project
   unzip poweramp-timestamp-app.zip
   cd poweramp-timestamp-app
   
   # Make gradlew executable
   chmod +x gradlew
   
   # Build the APK (this takes 5-10 minutes)
   ./gradlew assembleRelease
   ```

5. **Find your APK:**
   - Location: `app/build/outputs/apk/release/app-release-unsigned.apk`
   - Copy it to Downloads: 
   ```bash
   cp app/build/outputs/apk/release/app-release-unsigned.apk /sdcard/Download/poweramp-timestamp.apk
   ```

## Installation

1. **Install the APK:**
   - Open the APK file from your Downloads folder
   - Allow "Install from Unknown Sources" if prompted

2. **Grant Permissions:**
   - Open the app
   - Tap "Start Service"
   - Grant the following permissions:
     - ✅ Display over other apps
     - ✅ All files access / Storage
     - ✅ Notifications (Android 13+)

3. **MIUI-Specific Settings:**
   - Go to Settings → Apps → PowerAmp Timestamp
   - Other permissions → Display pop-up windows while running in the background: **Allow**
   - Battery saver → No restrictions
   - Autostart: **Enabled**

## Usage

1. **Start the service** in the app
2. **Open PowerAmp** and play any MP3 file
3. A **purple floating button** will appear
4. Tap the button to **save the current timestamp**
5. Files are saved to `/sdcard/_Edit-times/`

### File Format

Each file is named `[song-name].txt` and contains:

```
song-name
00:01:23
00:02:45
00:05:12
```

## Troubleshooting

### Button doesn't appear
- Make sure PowerAmp is in the foreground
- Check that "Display over other apps" permission is granted
- On MIUI: Enable "Display pop-up windows while running in the background"

### Can't save files
- Grant "All files access" permission
- Check that `/sdcard/_Edit-times/` folder exists

### Service stops
- On MIUI: Disable battery optimization for this app
- Enable "Autostart" in MIUI settings

### PowerAmp not detected
- Make sure you're using the official PowerAmp app (package: `com.maxmpz.audioplayer`)
- Try playing a track to trigger the notification

## Technical Details

### How it works:
1. **Service Detection:** Checks every 500ms if PowerAmp is in the foreground
2. **Track Info:** Reads from PowerAmp's notification title
3. **Position:** Receives broadcasts from PowerAmp with playback position
4. **Storage:** Uses standard file I/O to append timestamps

### Permissions Explained:
- **SYSTEM_ALERT_WINDOW:** Required for floating button
- **FOREGROUND_SERVICE:** Keeps service running
- **MANAGE_EXTERNAL_STORAGE:** Access to `/sdcard/_Edit-times/`
- **POST_NOTIFICATIONS:** Show service notification (Android 13+)

## PowerAmp API

This app uses PowerAmp's broadcast intents:
- `com.maxmpz.audioplayer.STATUS_CHANGED`
- `com.maxmpz.audioplayer.TRACK_CHANGED`
- `com.maxmpz.audioplayer.PLAYING_MODE_CHANGED`

## Known Issues

- **First launch:** May need to play/pause once in PowerAmp to initialize
- **MIUI battery saver:** Can aggressively kill background services
- **Position accuracy:** Depends on PowerAmp broadcasts (usually accurate to ~1 second)

## Privacy

- ✅ No internet permission
- ✅ No data collection
- ✅ No analytics
- ✅ Completely offline
- ✅ Open source

## Building Tips

### If build fails in Termux:
```bash
# Clear cache and retry
./gradlew clean
./gradlew assembleRelease
```

### If you get "Out of Memory" error:
Edit `gradle.properties` and change:
```
org.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8
```

## Credits

Created as a simple tool for marking edit points in audio files while listening in PowerAmp.

## License

Public Domain - Use freely!

# PowerAmp Timestamp - Complete Summary

## What You're Getting

A complete Android app project that creates a **floating button** that:
- ✅ Only appears when PowerAmp is on screen
- ✅ Captures the current MP3 filename from PowerAmp
- ✅ Saves timestamps in `hh:mm:ss` format
- ✅ Stores files in `/sdcard/_Edit-times/`
- ✅ Works on MIUI 14 and other Android versions

## File Structure

```
poweramp-timestamp-app/
├── QUICK_START.md                    ← Start here!
├── README.md                          ← Full documentation
├── app/
│   ├── src/main/
│   │   ├── java/com/poweramp/timestamp/
│   │   │   ├── MainActivity.java              (Main screen)
│   │   │   ├── FloatingButtonService.java     (Floating button logic)
│   │   │   └── PowerAmpReceiver.java          (Listens to PowerAmp)
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml          (Main screen layout)
│   │   │   │   └── floating_button.xml        (Button design)
│   │   │   ├── drawable/
│   │   │   │   └── floating_button_bg.xml     (Button background)
│   │   │   └── values/
│   │   │       └── strings.xml                (App name)
│   │   └── AndroidManifest.xml                (App permissions)
│   └── build.gradle                            (App build config)
├── .github/workflows/build.yml                 (GitHub auto-build)
├── build.gradle                                (Project build config)
├── settings.gradle                             (Project settings)
├── gradlew                                     (Build script)
└── gradle/wrapper/                             (Gradle wrapper)
```

## Two Ways to Build

### 🌟 RECOMMENDED: GitHub Actions (Cloud Build)
**Time:** 5 minutes  
**Requirements:** Just a GitHub account  
**Difficulty:** ⭐ Easy

1. Create GitHub account
2. Upload all these files to a new repository
3. GitHub builds it automatically
4. Download the APK

👉 **See QUICK_START.md for step-by-step instructions**

### 📱 Alternative: Termux (Phone Build)
**Time:** 10-15 minutes  
**Requirements:** Termux app from F-Droid  
**Difficulty:** ⭐⭐ Medium

1. Install Termux
2. Transfer this ZIP to your phone
3. Run build commands
4. Install the APK

👉 **See README.md for Termux instructions**

## After Building

### Installation Steps
1. Install the APK on your phone
2. Open "PowerAmp Timestamp" app
3. Tap "Start Service"
4. Grant these permissions:
   - Display over other apps ✓
   - All files access ✓
   - Notifications ✓

### MIUI-Specific Settings
⚠️ **IMPORTANT for MIUI users:**

Go to: **Settings → Apps → PowerAmp Timestamp**
- Battery saver → **No restrictions**
- Autostart → **Enable**
- Other permissions → Display pop-up windows → **Allow**

### Usage
1. Start the service in the app
2. Open PowerAmp
3. Play any MP3 file
4. A purple button appears
5. Tap it to save timestamps!

## Output Format

Files are saved to `/sdcard/_Edit-times/`

Example: `my-song.txt`
```
my-song
00:01:23
00:02:45
00:05:12
```

## Permissions Explained

The app needs these permissions to work:

| Permission | Why |
|------------|-----|
| Display over other apps | Show floating button |
| Storage access | Save timestamp files |
| Notifications | Keep service running |

**Privacy:** No internet permission! All data stays on your device.

## How It Works

```
PowerAmp Playing
     ↓
Service detects PowerAmp is active
     ↓
Shows floating button
     ↓
You tap button
     ↓
Reads track name from notification
     ↓
Gets current playback position
     ↓
Saves to /sdcard/_Edit-times/[filename].txt
```

## Troubleshooting

### "Button doesn't appear"
- Check: Is PowerAmp actually open and playing?
- Check: Did you grant "Display over other apps"?
- MIUI: Enable "Display pop-up windows"

### "No track detected"
- Play/pause the track once
- Make sure you opened the MP3 with PowerAmp
- Check PowerAmp notification is visible

### "Error saving"
- Grant "All files access" permission
- Try manually creating `/sdcard/_Edit-times/` folder

### "Service stops"
- MIUI: Disable battery optimization
- Enable Autostart
- Don't clear from recent apps

## Support

- Check **README.md** for detailed troubleshooting
- Build issues? Check the **Actions** tab on GitHub for error logs
- MIUI issues? See the MIUI-specific settings above

## Quick Reference

| Action | Location |
|--------|----------|
| Start service | Open app → "Start Service" |
| View timestamps | File manager → `/sdcard/_Edit-times/` |
| Grant permissions | Settings → Apps → PowerAmp Timestamp |
| MIUI settings | Settings → Apps → PowerAmp Timestamp → Battery/Autostart |
| Build on GitHub | Upload files → Actions tab → Download artifact |
| Build on phone | Termux → `./gradlew assembleRelease` |

---

## Next Steps

1. **First Time?** → Read **QUICK_START.md**
2. **Need More Info?** → Read **README.md**
3. **Ready to Build?** → Choose GitHub or Termux method
4. **Built Successfully?** → Install and grant permissions
5. **Having Issues?** → Check troubleshooting section

Good luck! 🎵

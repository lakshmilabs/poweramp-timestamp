# Quick Start - Build on GitHub (No Android Studio!)

This is the **fastest and easiest** way to build your app without installing anything on your computer.

## Step 1: Create GitHub Account
- Go to [github.com](https://github.com)
- Sign up (it's free!)

## Step 2: Create Repository
1. Click the **+** button (top right) → **New repository**
2. Name it: `poweramp-timestamp`
3. Make it **Public**
4. Click **Create repository**

## Step 3: Upload Project Files
1. **Download this entire project folder**
2. On your new GitHub repository page, click: **uploading an existing file**
3. **Drag and drop ALL files** from this project folder
4. Click **Commit changes**

## Step 4: Wait for Build
1. Click the **Actions** tab at the top
2. You'll see a build running (yellow dot)
3. Wait **~5 minutes** for it to complete (green checkmark)

## Step 5: Download APK
1. Click on the completed build
2. Scroll down to **Artifacts**
3. Download **app-release**
4. Unzip the file to get `app-release-unsigned.apk`

## Step 6: Install on Phone
1. Transfer the APK to your phone
2. Open it and install (allow "Unknown sources" if asked)
3. Open the app and tap "Start Service"
4. Grant all permissions

## Done! 🎉

Now open PowerAmp, play a song, and the floating button will appear!

---

## Need Help?

**Build Failed?**
- Make sure you uploaded ALL files (including hidden `.github` folder)
- Check the Actions tab for error messages

**APK Not Installing?**
- Enable "Install from Unknown Sources" in Settings
- On MIUI: Settings → Privacy → Special permissions → Install unknown apps

**Button Not Showing?**
- Grant "Display over other apps" permission
- On MIUI: Settings → Apps → PowerAmp Timestamp → Other permissions → Display pop-up windows: Allow

---

## Alternative: Build on Your Phone

If you prefer to build directly on your Android phone:
1. Install **Termux** from [F-Droid](https://f-droid.org/)
2. See **README.md** for detailed Termux instructions

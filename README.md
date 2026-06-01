# 📱 Screen Time Tracker

A battery-friendly Android app to track your daily screen time with an infinite log and CSV export.

## Features
- ⏱ Tracks time spent per app every day
- 🗄 Infinite history stored in a local SQLite database (never deleted)
- 📤 Export all data as CSV (email, Google Drive, etc.)
- 🔋 Very low battery impact — uses WorkManager + Android's UsageStatsManager (no always-on service)
- 📊 Tap any app to see its full history and daily average

---

## How to Build & Install (GitHub Actions — no Android Studio needed)

### Step 1 — Create a GitHub account
Go to https://github.com and sign up for free if you don't have one.

### Step 2 — Create a new repository
1. Click the **+** button (top right) → **New repository**
2. Name it: `ScreenTimeTracker`
3. Set it to **Public** (required for free GitHub Actions)
4. Click **Create repository**

### Step 3 — Upload the project files
1. On the new repo page, click **uploading an existing file**
2. Drag the entire `ScreenTimeTracker` folder contents into the upload area
3. Make sure the folder structure is preserved (you should see `app/`, `.github/`, `build.gradle`, etc.)
4. Click **Commit changes**

### Step 4 — Wait for the build (~3-5 minutes)
1. Click the **Actions** tab at the top of your repo
2. You'll see a workflow called **Build APK** running (yellow circle = in progress)
3. Wait for it to turn green ✅

### Step 5 — Download the APK
1. Click on the completed workflow run
2. Scroll to the bottom → **Artifacts**
3. Click **ScreenTimeTracker-debug** to download a zip
4. Unzip it — you'll find `app-debug.apk`

### Step 6 — Install on your phone
1. On your Android phone go to: **Settings → Security → Install unknown apps**
   - On some phones: Settings → Apps → Special app access → Install unknown apps
2. Enable it for your **Files** app or browser
3. Transfer the APK to your phone (email it to yourself, or use a USB cable)
4. Open the APK file on your phone and tap **Install**

### Step 7 — Grant Usage Access permission
1. Open **Screen Time Tracker**
2. Tap **Open Settings** when prompted
3. Find **Screen Time Tracker** in the list and tap it
4. Enable **Permit usage access**
5. Go back to the app — it will start collecting data immediately

---

## How it Works

| What | How |
|---|---|
| Data collection | WorkManager runs every 30 minutes, reads from Android's UsageStatsManager |
| Storage | SQLite database on your device (unlimited rows, no cloud) |
| Battery | No foreground service. WorkManager is battery-optimized by Android |
| Export | Taps the system share sheet — send to Gmail, Drive, WhatsApp, etc. |

## CSV Export Format

```
Date,App Name,Package,Minutes,Hours
2024-01-15,YouTube,com.google.android.youtube,127,2.12
2024-01-15,Chrome,com.android.chrome,45,0.75
```

---

## Troubleshooting

**"No data yet" after installing**
→ Tap the blue refresh button (bottom right). Make sure Usage Access permission is granted.

**Data seems wrong**
→ Android's UsageStatsManager resets at midnight. Data for the current day accumulates through the day.

**Build failed on GitHub Actions**
→ Check the Actions tab for error details. Common fix: make sure all files were uploaded correctly.

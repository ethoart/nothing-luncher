# ⌚ Watch Launcher — Wear OS Launcher App

A custom Android launcher built specifically for **Wear OS** smartwatches with small round screens.

---

## ✨ Features

| Feature | How it works |
|---|---|
| 🕐 **Clock widget** | Custom `ClockView` draws digital time + date directly on canvas, auto-updates every minute |
| 🖼️ **Custom wallpaper** | Reads system wallpaper; long-press home to pick from gallery or 3 presets |
| 📱 **App Drawer** | 3-column grid showing all installed apps, sorted A–Z |
| 🔍 **Search bar** | Real-time filter as you type in the drawer |
| ⚓ **Dock** | 5 pinned apps at the bottom of the home screen |
| 👆 **One-finger gestures** | Swipe UP = open drawer · Swipe DOWN in drawer = go home · Long-press = wallpaper picker |

---

## 🛠️ Build Instructions

### Requirements
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- A Wear OS device or emulator (API 26+)

### Steps

1. **Open in Android Studio**
   ```
   File → Open → select the WatchLauncher folder
   ```

2. **Wait for Gradle sync** (downloads dependencies automatically)

3. **Connect your watch**
   - Enable Developer Options on your Wear OS watch
   - Enable ADB over Wi-Fi (Settings → Developer Options → ADB over Wi-Fi)
   - Or use a Wear OS emulator (AVD Manager → Wear OS Round)

4. **Run the app**
   - Select your watch from the device dropdown
   - Click ▶ Run

5. **Set as default launcher** (on the watch)
   - When prompted "Select a launcher", choose **Watch Launcher**
   - Or: Settings → Apps → Default apps → Home app → Watch Launcher

---

## 📁 Project Structure

```
WatchLauncher/
├── app/src/main/
│   ├── AndroidManifest.xml          ← HOME intent filter (makes this a launcher)
│   └── java/com/watchlauncher/
│       ├── MainActivity.kt          ← Home screen (clock + dock + gestures)
│       ├── AppDrawerActivity.kt     ← App grid + search
│       ├── WallpaperPickerActivity.kt ← Wallpaper selection
│       ├── AppAdapter.kt            ← RecyclerView adapter
│       ├── AppInfo.kt               ← Data class
│       └── ClockView.kt             ← Custom canvas clock
│   └── res/
│       ├── layout/                  ← XML layouts
│       ├── drawable/                ← Backgrounds + presets
│       └── anim/                   ← Slide/fade animations
```

---

## 🎨 Customization Tips

- **Change dock apps**: Edit `queryPinnedApps()` in `MainActivity.kt` — replace package names with your preferred apps
- **Change clock style**: Edit `ClockView.kt` — modify `timePaint` colors, font, or add an analog hands implementation
- **Add more wallpaper presets**: Add new `wallpaper_preset_N.xml` drawables and add buttons in `WallpaperPickerActivity`
- **Adjust grid columns**: Change `GridLayoutManager(this, 3)` in `AppDrawerActivity.kt` (try 2 for bigger icons)

---

## 📋 Permissions Used

| Permission | Why |
|---|---|
| `QUERY_ALL_PACKAGES` | Read all installed apps for the drawer |
| `SET_WALLPAPER` | Apply selected wallpaper |
| `READ_MEDIA_IMAGES` | Pick image from gallery (Android 13+) |
| `READ_EXTERNAL_STORAGE` | Pick image from gallery (Android 12 and below) |

---

## 🔧 Troubleshooting

**"App not installed" on watch**: Make sure `minSdk 26` and you're deploying to a Wear OS device, not a phone.

**Clock not showing**: Verify `ClockView` is in the layout and the layout is inflated in `MainActivity`.

**Apps not loading in drawer**: The `QUERY_ALL_PACKAGES` permission requires adding it to the manifest (already done) — on some ROMs you may also need to grant it manually in Settings.

# Aero Bar - Implementation Plan

## Goal
Create a persistent System Navigation Bar & Central Dock for the Aero ecosystem. It handles system navigation, app switching, and acts as the central App Drawer, replacing the need for a traditional launcher.

## Architecture & Tech Stack
- **Installation Type:** System App (`/system/priv-app`). Grants full elevated permissions including `KILL_BACKGROUND_PROCESSES`, `FORCE_STOP_PACKAGES`, and `PACKAGE_USAGE_STATS` without user prompts.
- **Service-Based UI:** Built using a Foreground `Service` managing `WindowManager` overlays (requires `SYSTEM_ALERT_WINDOW` permissions — auto-granted as System App).
- **UI Framework:** Jetpack Compose.
- **State Management:** Coroutines and StateFlow.
- **Local Storage:** Room Database (for pinned apps order & hidden apps blocklist).
- **Layout Direction:** Fixed LTR only across the entire app. No RTL support.
- **Build Flavor:** Unified with the Aero ecosystem. **Active Flavor: `dongfeng_shine` only.** Additional car models will be added as new flavors in future releases.

## Dock Bar Layout (Fixed, Non-Expandable)
```
[ Back ]  [ Home ]  [ Recents ]  ....empty space....  [ Pinned Apps Area ]  [ Apps >> ]
  Left                                                                          Right
```
- **Left Side (Always):** Back, Home, Recent Apps system buttons.
- **Center:** Pinned Apps area (empty by default on first launch, populated via drag-and-drop or long-press > Pin).
- **Right Side (Always):** App Grid trigger button/icon to open the Floating App Drawer.

## Floating App Drawer Panel (Overlay)
- Triggered by the Apps button on the right.
- A secondary `WindowManager` overlay (~70% screen size), centered.
- **App List Contents:**
  - All installed apps fetched via `PackageManager`.
  - Hidden apps are excluded from this list.
  - Default sort order: By install time (most recent first).
  - Supports **Drag & Drop** to reorder apps (order persisted in Room DB).

## Recent Apps Floating Screen
- Triggered by the **Recents** button in the dock bar.
- A `WindowManager` overlay showing currently running background apps (fetched via `ActivityManager.getRunningAppProcesses` or `UsageStatsManager`).
- **Layout:** Horizontal card carousel (similar to native Android Recents), each card shows app icon + name + a screenshot/thumbnail preview if available.
- **Close Single App:** An **X button** on the top-right corner of each card kills that app's process.
- **Swipe Left** on any card also dismisses and kills that app.
- **"Clear All" button** at the bottom or corner of the overlay to kill all background apps at once.
- Tapping any card (not the X) brings that app to the foreground.

## Long-Press Context Menu (Per App)
Each app icon in the Floating Drawer supports a long-press that reveals a context menu with:
1. **Pin to Dock** — adds the app to the pinned apps area in the dock bar.
2. **Hide** — removes the app from the Floating Drawer (added to hidden list in Room DB).
3. **App Details** — opens system App Info screen.
4. **Uninstall** — triggers system uninstall flow.
- Pinned apps in the dock bar also support long-press > **Unpin** and **Drag & Drop** to reorder.

## Hidden Apps Management
- Accessible via a dedicated entry in the Floating Drawer (e.g., a small "Hidden Apps" button at the bottom of the panel).
- Shows a list of all hidden apps.
- Each item has an **Unhide** button to restore the app to the main drawer.

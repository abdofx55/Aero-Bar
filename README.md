# AeroBar — Unified Navigation & HVAC Dock for Dongfeng Shine (960×1280) 🚗⚡

> **A mission-critical, zero-latency system navigation dock and HVAC telematics overlay for Dongfeng Shine / Aeolus D53 (Qualcomm Snapdragon Android 8.1 API 27).**

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.02-4285F4?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Platform](https://img.shields.io/badge/Platform-Android%208.1%20API%2027-00D2FF?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Security](https://img.shields.io/badge/Security-R8%20Hardened%20%26%20Anti--Tamper-success?style=for-the-badge&logo=shield)](https://github.com/)
[![Signing](https://img.shields.io/badge/Signed-Dongfeng%20Platform%20Key-orange?style=for-the-badge&logo=google-authenticator)](https://github.com/)

---

## 1. Overview & Architecture

**AeroBar** is a complete ground-up replacement for the factory stock OEM navigation and climate bar (`SystemUI Navigation & HVAC Bar`) on Dongfeng Shine headunits. Engineered from the ground up using **Kotlin** and **Jetpack Compose**, it strictly adheres to **Clean Architecture**, **SOLID principles**, and the unidirectional **MVI (Model-View-Intent)** reactive pattern.

It interfaces directly with the vehicle's underlying Qualcomm CAN-Bus and Binder IPC services with zero latency, utilizing 100% programmatic vector graphics (**Pure Compose `ImageVector`**) without reliance on heavy raster bitmaps.

```mermaid
flowchart TB
    subgraph UI["Presentation Layer (Jetpack Compose + MVI)"]
        direction TB
        Service["AeroBarOverlayService\n(Permanent 88dp System Dock + Overlay Host)"]
        BarView["AeroBarView.kt\n(Single-Row 960x88px • 13 Controls • Swipe Steppers)"]
        RecentsView["RecentAppsView.kt\n(860dp Pure Charcoal • 5 Visible Cards • RAM Bar)"]
        DrawerView["AppDrawerView.kt\n(860dp Pure Charcoal • 5-Column Grid)"]
        Icons["AeroIcons.kt\n(12 Pure Compose ImageVector Icons)"]

        BarVM["AeroBarViewModel\n(StateFlow<AeroBarState>)"]
        RecentsVM["RecentAppsViewModel\n(StateFlow<RecentAppsState>)"]
        DrawerVM["AppDrawerViewModel\n(StateFlow<AppDrawerState>)"]

        Service --> BarView
        Service --> RecentsView
        Service --> DrawerView
        BarView --> Icons
        BarView <-->|"Intent / State"| BarVM
        RecentsView <-->|"Intent / State"| RecentsVM
        DrawerView <-->|"Intent / State"| DrawerVM
    end

    subgraph Domain["Domain Layer (Pure Kotlin • SOLID Interfaces & Models)"]
        direction LR
        HvacRepo["VehicleHvacRepository\n(Interface)"]
        AppRepo["AppRepository\n(Interface)"]
        RecentRepo["RecentAppsRepository\n(Interface)"]
        Models["Domain Models\n• HvacTelemetryState\n• AppItem\n• RecentTaskItem"]
    end

    subgraph Data["Data & Hardware Layer (Binder IPC + System Services)"]
        direction TB
        HvacImpl["QingganVehicleHvacRepositoryImpl\n(Binder IPC + Broadcast Bridge + Telemetry Parser)"]
        AppImpl["AppRepositoryImpl\n(8 Essential Car Apps + User Apps Filter)"]
        RecentImpl["RecentAppsRepositoryImpl\n(Multi-Tier Task Killer + Smart BT Protection)"]
        AirCondParcel["com.qinggan.canbus.AirCondition\n(61-Field Binary Parcelable)"]
    end

    subgraph Hardware["Dongfeng Shine Hardware & OS Services (Android 8.1)"]
        direction LR
        CanBus["qg.canbus\n(ICanBusService)"]
        VehicleSvc["vehicle.service\n(IVehicleService)"]
        PowerSvc["qg.power_manager\n(IPowerManagerService)"]
        PolicySvc["SystemPolicyService\n(ISystemPolicy • 360° AVM)"]
        AMS["ActivityManagerService\n& WindowManagerService"]
    end

    BarVM --> HvacRepo
    BarVM --> AppRepo
    BarVM --> RecentRepo
    RecentsVM --> RecentRepo
    DrawerVM --> AppRepo

    HvacRepo <|.. HvacImpl
    AppRepo <|.. AppImpl
    RecentRepo <|.. RecentImpl
    HvacImpl --> AirCondParcel
    HvacImpl --> CanBus
    HvacImpl --> VehicleSvc
    HvacImpl --> PowerSvc
    HvacImpl --> PolicySvc
    RecentImpl --> AMS
```

---

## 2. Key Features

### 1. Single-Row Ultra-Compact Dock (`960×88px`)
- **88px Screen Space Reclamation:** Consolidates the factory dual-row (176px) bar into a sleek, single-row **88px** dock, returning **88px of vertical screen real estate** to navigation maps and third-party apps (`960×1192px` net usable area).
- **Automotive Cockpit Design:** Pure OLED black dock background (`#000000`) with zero external borders (`Zero Outlines / Zero Elevation`).
- **Option 1 Modern Selection Style:** Active buttons illuminate with a subtle dark charcoal pill (`#121822`) and vibrant Electric Cyan (`#00D2FF`) glow.
- **13 Integrated Functions in One Row:**
  - **Navigation Cluster (Left):** Back, Home (long-press triggers screen standby), Recent Apps (single-tap opens recent cards; rapid double-tap executes instant `Alt+Tab` switching), All Apps Drawer.
  - **360° AVM Camera Button:** Instant one-touch toggle for the vehicle around-view monitoring system (`ISystemPolicy.showAVM`).
  - **HVAC Core Cluster (Center):**
    - Textual **AC** compressor toggle.
    - Textual **AUTO** climate mode toggle.
    - **Temperature Stepper Pill (`− 21° +`):** Supports click stepping and **horizontal swipe gestures**, with **dynamic thermal color feedback**: Ice Blue (`#00D2FF`) for cool (16°–21°C), Pure White (`#FFFFFF`) for moderate (22°–25°C), and Warm Amber (`#FF9F43`) for heat (26°–32°C).
    - **Fan Speed Stepper Pill (`− [Fan] 3 +`):** Supports tap and horizontal swipe gestures (Levels 1–8) with custom 4-blade aerodynamic fan vector.
  - **Vehicle Controls Cluster (Right):**
    - **Climate & Seats App Shortcut:** Directly opens the OEM vehicle climate and seat ventilation app (`com.qinggan.app.vehicle`, `TAB_ID = 2`).
    - **Front Defrost:** Illuminates amber (`#FF9F43`) when active.
    - **Cabin Air Recirculation:** Toggle between fresh air intake and cabin recirc.
    - **Display Standby:** Puts the screen into sleep mode via `IPowerManagerService.enterStandby`.

### 2. Permanent Dock & Screen Workspace Reservation
- **Anti-Immersive Policy:** Prevents media players and third-party apps from entering sticky immersive mode and hiding the dock.
- **Overscan Window Manager Inset:** Instructs the Android WindowManager to offset standard app windows by 88px, guaranteeing zero window overlap.

### 3. Recent Apps Overlay with Smart Bluetooth Protection
- **Pure Dark Charcoal Surface (`#0C1016`):** 860dp wide borderless floating container displaying 5 simultaneous app snapshot cards (`150×215dp`).
- **Live System RAM Telemetry:** Real-time RAM utilization bar (`Used / Total GB + %`) and per-app memory consumption badges.
- **Smart Bluetooth Protection:** When terminating apps via "Clear All", the Bluetooth phone dialer (`com.qinggan.bluetoothphone`) is gracefully dismissed without killing its background audio stack (`forceStopPackage` is bypassed for Bluetooth telephony).

### 4. All Apps Drawer
- **5-Column LazyVerticalGrid:** Clean layout presenting essential vehicle apps and user-installed applications.
- **OEM Package Sanitization:** Automatically filters and hides over 94 internal factory debug daemons, test tools, and system background processes.

### 5. Official App Icon ("The Aero Wing")
- Custom supersonic delta-wing vector artwork in Electric Cyan resting above the navigation dock line.
- Complete adaptive mipmap densities: `mdpi` (48px), `hdpi` (72px), `xhdpi` (96px), `xxhdpi` (144px), and `xxxhdpi` (192px) in squircle and circular configurations.

### 6. Silent 15-Second Inactivity Auto-Dismiss
- Overlays automatically dismiss after **15 seconds** of inactivity to minimize driver distraction.
- Completely silent background timer (clean, non-cluttered UI).
- Any touch event, drag, or scroll interaction immediately resets the timer back to 15 seconds.

### 7. Hardened Security & Anti-Reverse Engineering System
- **R8 Full-Mode Obfuscation & Repackaging:** Renames all classes, methods, and variables to unreadable single characters (`a.b.c.d`), flattens all internal packages into a single root (`-repackageclasses ''`), and strips line numbers, source file metadata, and debug logs.
- **Runtime Signature Verification (`SecurityGuard`):** Validates the APK's SHA-256 certificate fingerprint against an XOR-encrypted platform key hash on application start. If tampered with or resigned, the process immediately terminates (`killProcess`).
- **Anti-Debugging & Tracer Detection:** Blocks attached debuggers (JDWP / Frida) in production release builds.
- **82% Size Reduction:** Decreased release binary footprint from 11.2 MB to **1.94 MB**.

---

## 3. Project Directory Structure

```
Aero_Bar/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/aero/bar/
│   │   │   ├── AeroBarApplication.kt
│   │   │   ├── crash/CrashHandler.kt
│   │   │   ├── security/SecurityGuard.kt        # Anti-tamper & runtime signature checks
│   │   │   ├── di/AppModule.kt                 # Hilt dependency injection
│   │   │   ├── domain/                         # Pure Kotlin models & repository interfaces
│   │   │   ├── data/                           # Binder IPC, CAN-Bus & system service clients
│   │   │   ├── service/                        # WindowManager overlay & accessibility services
│   │   │   └── ui/                             # Jetpack Compose UI (Bar, Recents, Drawer, Icons)
│   │   └── res/                                # Strings, styles, accessibility XML, mipmaps
│   ├── proguard-rules.pro                      # Hardened R8 obfuscation rules
│   └── build.gradle.kts                        # Build configuration & signing configs
├── keystore/                                   # Platform keystore directory (gitignored)
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 4. Build & Deployment

### Production Release Build (Hardened & Signed)
```powershell
.\gradlew.bat assembleDongfeng_shineRelease
```
- Output binary: `app/build/outputs/apk/dongfeng_shine/release/app-dongfeng_shine-release.apk`
- Binary footprint: **1.94 MB** (Optimized, obfuscated, and dual-signed with APK Signature Scheme v2).

### Debug Build
```powershell
.\gradlew.bat assembleDongfeng_shineDebug
```

### Installation via ADB
```powershell
adb install -r app/build/outputs/apk/dongfeng_shine/release/app-dongfeng_shine-release.apk
adb shell appops set com.aero.bar.dongfeng_shine SYSTEM_ALERT_WINDOW allow
adb shell settings put secure enabled_accessibility_services com.aero.bar.dongfeng_shine/com.aero.bar.service.AeroAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell am startservice -n com.aero.bar.dongfeng_shine/com.aero.bar.service.AeroBarOverlayService
```

---

## 5. Author & License

- **Lead Engineer:** Abdelrhman Hamdy
- **Contact:** abdellrhman.hamdi@gmail.com | +201095449725
- **Vehicle Community:** Dongfeng Shine / Aeolus D53 Global Owners Club
- **License:** Proprietary. Developed specifically for the Aero Automotive ecosystem.

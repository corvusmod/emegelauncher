# Emegelauncher

A custom home screen launcher for the **MG Marvel R** electric vehicle, designed to provide deep vehicle telemetry, real-time graphs, cloud integration, live navigation, media control, and a modern UI on the car's 19.4" portrait infotainment display.

---

> **IMPORTANT DISCLAIMER**
>
> This software is provided **as-is**, without warranty of any kind. The authors and contributors of Emegelauncher assume **no responsibility or liability** for any damage, malfunction, or unintended behavior that may occur to your vehicle, its systems, or any connected devices as a result of installing or using this application.
>
> By using this software, you acknowledge that:
> - Modifying your vehicle's head unit software may void your warranty
> - This is an **independent community project**, not affiliated with, endorsed by, or supported by MG, SAIC Motor, or any of their subsidiaries
> - You install and use this application **entirely at your own risk**
> - Always ensure your vehicle is safe to operate after any software modification
>
> If you are unsure about any aspect of this software, **do not install it**. Consult a qualified professional.

> **SECURITY & PRIVACY NOTICE**
>
> This application runs with **system-level privileges** on your vehicle's head unit. By design, it has access to sensitive data including but not limited to:
> - Vehicle Identification Number (VIN), MAC addresses, and serial numbers
> - Authentication tokens, encryption keys, and server credentials
> - GPS location and driving history
> - All vehicle telemetry (speed, battery, charging sessions)
> - Bluetooth paired devices, WiFi credentials, and hotspot passwords
> - MG iSMART cloud account credentials (if cloud features are used)
>
> **Only install this application from the official source repository.** Modified versions obtained from third parties could silently exfiltrate this data to external servers. Before installing any build you did not compile yourself, **review the source code**.

> **BETA SOFTWARE**
>
> This application is currently **in active development**. Some features may not work as expected, and new functionality is being added regularly. If you encounter any bugs or unexpected behavior, please [open an issue](../../issues) on this repository. Your feedback helps improve the project for everyone.

---

## Screenshots

| Main Screen | Live Dashboard |
|---|---|
| ![Main Screen](example/main_screen.jpg) | ![Live Dashboard](example/live_dashboard.jpg) |

| Cloud Statistics | Trip Recorder |
|---|---|
| ![Cloud Statistics](example/cloud_statistics.jpg) | ![Trip Recorder](example/trip_recorder.jpg) |

---

## Features

### 5-Screen Swipeable Launcher
Horizontal ViewPager with 5 pages: **Charging ← Graphs ← Main → Apps → Other**

### Main Screen (default)
Six live data cards in a 3x2 grid layout:

- **Navigation card**: Live turn-by-turn guidance with 49 SAIC turn icons (straight, turns, U-turns, roundabouts, motorway exits, ferry, merge ramps). Shows distance to next turn, road name (marquee), remaining distance + time, speed limit badge. Home/Office quick-nav buttons when idle (via IGeneralService.goHome/goOffice). Stop navigation button. Data from IGeneralNotificationListener callbacks.
- **Battery card**: Dynamic battery fill icon showing SOC level (green >50%, orange 20-50%, red <20%), range, standby estimate, BMS raw data, 12V voltage from cloud.
- **Radio card**: Station name + frequency from IRadioAppService. Play/pause via srcPlayRadio/srcPauseRadio. Next/prev station seeking. Supports FM, AM, and DAB. Tap opens radio app.
- **Music card**: Track title, artist, cover art (dimmed background) from Android MediaSession. Play/pause/next/prev via MediaController.getTransportControls(). Works with BT audio, USB music, online streaming, CarPlay, and Android Auto. Tap opens music app.
- **Weather card**: Dynamic weather icon (7 types), forecast temp, outside sensor temp, cabin temp from cloud. Tap opens weather app.
- **Phone card**: Connected BT device name, last 3 calls with type icons, tap-to-dial. Pulsing green border on incoming call. Wireless charger status.
- **Drive Mode bar**: Current mode (Eco/Normal/Sport/Winter) + regen level
- **Auto Theme**: Follows car day/night mode, or manual dark/light override. Theme changes preserve all accumulated data (eco score, consumption averages, G-meter peaks).
- **Layout**: Row 1: Navigation | Battery. Row 2: Radio | Music. Row 3: Weather | Phone

### Charging Dashboard (swipe far left)
Live-only real-time charging monitor with session data retention.
- **Gauges**: SOC (0-100%) + Power (kW), auto-scaling
- **Multi-series chart**: Power (green), Voltage (teal), SOC (blue) on shared timeline
- **Info cards**: Time remaining, session energy (kWh), range gained, pack voltage/current, target SOC, peak power, BMS current limit, charge stop reason, cable status, scheduled charge time

### Live Dashboard (swipe left)
Real-time vehicle data updated every second:
- **4 gauges**: Speed, Battery SOC, Consumption (dual-dot: instant + session average), Eco Score with live behavior arrows
- **Power gauge**: Center-zero arc (orange=consumption, green=regen)
- **G-Meter**: 2D G-force visualization (1G max), longitudinal from speed derivative, lateral from steering angle. Peak tracking.
- **Info bar**: Range, gear, drive mode
- Tap opens full 8-tab GraphsActivity

### Vehicle Graphs (8 tabs)
| Tab | Content |
|---|---|
| **Dashboard** | Speed, SOC, Consumption, Eco Score gauges + energy flow + G-force + gear/range/mode |
| **Energy** | SOC (display + BMS raw), pack voltage, pack current, consumption (kWh/100km) |
| **Charges** | Cloud charge history from iSMART API + local live sessions, export JSON, SOH estimation |
| **Health** | Auto-calculated SOH estimation from cloud battery capacity, resting voltage analysis, session history |
| **Tires** | Top-down car silhouette with 4-corner pressure + temperature, color-coded (2.5-3.3 bar) |
| **Climate** | HVAC status, outside temp, PM2.5, air quality sensors |
| **Trip** | Trip Recorder with GPX/KML/JSON export, odometer, live consumption/power |
| **G-Meter** | 2D G-force circle with peak tracking, longitudinal/lateral forces |

### ABRP Integration (A Better Route Planner)
Live telemetry to ABRP for real-time trip planning. Configure in Settings with your ABRP user token.
- **Data sent every 5s**: SOC, speed, power, GPS (lat/lon/altitude/heading from TBox GNSS), range, odometer, voltage, current, temps, tire pressures, charging status, battery capacity from cloud
- **Singleton pattern**: Settings and MainActivity share the same instance — token changes take effect immediately

### CarPlay & Android Auto
Integrated via Allgo RemoteUIService (the car's native projection framework).
- **Auto-detection**: IRemoteDeviceCallback fires on USB device connection
- **Auto-launch**: Projection screen opens automatically when CarPlay iPhone or Android Auto phone is plugged in
- **Resume**: Tap CarPlay/AA button to return to projection after switching to other apps
- **Now Playing**: Music card shows CarPlay/AA track metadata via MediaSession (player type 5=CP, 6=AA)

### iSMART Cloud Integration
Connects to MG's cloud API for data not available locally. Auto re-login on token expiry. Uses event-id polling pattern for endpoints that require TBox wake.

- **Vehicle status**: Cabin temp, 12V battery, doors/windows/locks/lights/tyres, trip data, power mode
- **Driving statistics**: Interactive day/month/year charts (mileage, consumption, CO2, speed, travel time)
- **BT Digital Key management**: View, activate, deactivate, revoke
- **Geofence**: Create at GPS position with radius, delete
- **POI**: Send current position or custom coordinates to car navigation

### Apps Screen (swipe right)
- **Top half**: Scrollable 4-column app grid with auto-refresh on install/uninstall
- **Long-press menu**: Open, Floating Window (7 size presets), App Info, Uninstall
- **Floating windows**: Freeform windowing mode with size picker (small/medium/large/half-screen positions). Requires car reboot after first enable.
- **Bottom half**: CarPlay, Android Auto, Video, 360 View, Car/System/Launcher Settings, Rescue, MG Support, Manual

### Other Screen (swipe far right)
- **Tool buttons**: Diagnostics, Vehicle Info, Location, TBox, Cloud, USB Camera
- **Cloud status**: Login state, cabin temp, 12V battery, TBox status

### USB Camera (UVC)
- User-space UVC camera support via AndroidUSBCamera library
- Lists connected USB cameras with VID/PID details
- Preview with aspect ratio matched to camera resolution (4:3 for 640x480)

### Trip Recorder
Record GPS tracks while driving with full vehicle telemetry.
- **Live data per point**: GPS lat/lon/altitude (from TBox GNSS), speed, power, SOC, consumption, G-forces
- **Export**: GPX + KML (Google Earth) + JSON to USB drives or internal storage
- **Stored trips**: Last 5 with auto-pruning, per-trip export buttons

### Overlay Controls
Floating buttons on left edge of screen:
- **Back button** (◀): Simulates Android back key
- **Recent apps** (▣): Shows recent non-system apps
- **Top dock toggle** (▲/▼): Attempts to hide/show the MG status bar overlay

### Navigation Proxy
Registers as system handler for `geo:` and `google.navigation:` URI intents, forwarding to Telenav.

### Vehicle Info
100+ data points: Identity, Status, Battery, HVAC, ADAS, drive mode, Comfort, Doors/Windows, ECU status, Cloud data, security info.

### Diagnostics
- 943 VHAL properties + 252 SAIC methods with filtering
- AIDL TX code enumeration for all services
- Export: Logcat, diagnostics, charge data, custom debug log

---

## Architecture

Emegelauncher uses a **multi-layer service architecture** to access vehicle data, entirely through **Java reflection** — no proprietary libraries are compiled or bundled.

```
Layer 1: Android Car API
  |- CarPropertyManager --- 943 VHAL properties
  |- CarHvacManager / CarBMSManager

Layer 2: SAIC VehicleSettingsService (via DexClassLoader)
  |- 5 sub-services: Setting, Condition, Charging, Control, AirCondition

Layer 3: EngineerModeService (via DexClassLoader)
Layer 4: SaicAdapterService (3 sub-services)
Layer 5: SystemSettingsService (8 sub-services)
Layer 6: vehicleService_overseas

Layer 7: SAIC Media Services (via DexClassLoader)
  |- IRadioAppService (radio control + RadioBean metadata)
  |- IPlayStatusBinderInterface (music source management)
  |- MediaPlayControlManager SDK (in-process music callbacks)
  |- IRadioListener (real-time radio state via raw Binder)

Layer 8: Allgo RemoteUIService (CarPlay/Android Auto)
  |- IRemoteDeviceCallback for device detection
  |- launchApp / resumeRemoteSession for projection control

Layer 9: Navigation (IGeneralNotificationListener)
  |- Raw Binder callback for turn-by-turn data
  |- 49 SAIC navigation icons

Layer 10: MG iSMART Cloud API
  |- OAuth2 + AES/CBC + HMAC-SHA256
  |- Vehicle status, statistics, BT keys, geofence, POI

Layer 11: USB Camera (UVC)
  |- AndroidUSBCamera library (user-space UVC)
```

---

## No Proprietary Code

This project contains **zero proprietary MG/SAIC code**. All vehicle services are accessed through Java reflection. AIDL stub classes are loaded at runtime via DexClassLoader from APKs already installed on the car. Navigation icons are extracted from the system launcher for visual consistency.

**Dependencies:**
- `androidx.appcompat:1.1.0` and `androidx.constraintlayout:1.1.3`
- `AndroidUSBCamera:libausbc:3.2.3` + `libuvc:3.2.3` (open-source UVC camera)

---

## Building

**Requirements:** Java 11, Android SDK with Build Tools 28.0.3, AOSP platform signing key

```bash
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 ./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

---

## Installation

> **WARNING: Only download Emegelauncher from this official repository.**

### What you need
- The APK file (download from [Releases](../../releases) or build it yourself)
- A USB memory stick formatted as **FAT32**

### First-time installation

1. Copy the `.apk` to the root of a FAT32 USB drive. Insert into the **left USB port**.
2. Open **Amazon Music** → tap any text field → **long-press** the globe icon (🌐) → tap the **gear icon** → Android Settings opens.
3. In Settings, search **"Applications"** → find **"Files"** → open file manager.
4. Select the USB drive → tap the APK → install.
5. Press **HOME** → select **Emegelauncher**.

### Updating
Copy new APK to USB → Apps page → Files → select USB → tap new APK → install.

---

## Supported Languages

| Language | Status |
|---|---|
| English | Full support (default) |
| Spanish (Español) | Full support |

---

## Target Vehicle

| Spec | Value |
|---|---|
| Vehicle | MG Marvel R (EP21 platform) |
| Display | 19.4" portrait, 1024x1280px |
| OS | Android 9 Automotive (API 28) |
| Battery | 70 kWh nominal |

---

## License

Licensed under **Apache License 2.0** with **Commons Clause v1.0**.

**You may**: use, modify, share, fork, and accept donations.
**You may NOT**: sell this software or offer it as a paid service.

See [LICENSE](LICENSE) for full details.

---

## Support the Project

Emegelauncher is free and open-source. If you find it useful, donations are appreciated.

**[Donate via PayPal](https://paypal.me/corvusmod)**

---

## Contributing

Contributions welcome. By submitting a PR, you agree to the Apache 2.0 + Commons Clause terms.

## Acknowledgments

- Huseyin's DriveHub project for the reflection-based approach
- AOSP platform signing key from the Android Open Source Project
- Cloud API protocol from the NewMGRemote open-source project
- UVC camera support via [AndroidUSBCamera](https://github.com/jiangdongguo/AndroidUSBCamera)

# Emegelauncher

A custom home screen launcher for the **MG Marvel R** electric vehicle, designed to provide deep vehicle telemetry, real-time graphs, cloud integration, and a modern UI on the car's 19.4" portrait infotainment display.

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

---

## Features

### Home Screen
- **Battery & Range**: SOC percentage, BMS raw SOC, estimated range (cluster + BMS sources)
- **Drive Mode & Regen**: Live display of current drive mode (Eco/Normal/Sport/Winter) and regen level
- **Weather**: Temperature from SAIC weather service
- **Outside Temperature**: From air conditioning service with VHAL fallback
- **Driver Profile**: Save/restore drive mode and regen level, auto-restore on start
- **App Shortcuts**: Navigation, Radio, Music, Phone, 360 View, CarPlay, Android Auto, Video, Vehicle Settings, System Settings, Rescue, Touchpoint, Manual
- **Auto Theme**: Follows car display (day/night), or manual dark/light override

### Vehicle Graphs (8 tabs)
| Tab | Content |
|---|---|
| **Dashboard** | Speed, SOC, RPM, Efficiency gauges + energy flow chart + gear + range + drive mode |
| **Energy** | SOC (display + BMS raw), pack voltage, pack current, consumption (kWh/100km) |
| **Charging** | Live power/current/voltage charts when charging, stored session data when idle |
| **Health** | Auto-calculated SOH estimation, capacity tracking, charge session history |
| **Tires** | 4-corner pressure diagram with color-coded thresholds (2.5-3.3 bar) |
| **Climate** | Temperature monitoring, HVAC status |
| **Trip** | Odometer, avg consumption, total consumed, regen energy/range |
| **G-Meter** | 2D G-force visualization, longitudinal/lateral charts, regen level, one-pedal status |

### iSMART Cloud Integration
Connects to MG's cloud API (`gateway-mg-eu.soimt.com`) for data not available locally on the head unit.

**Read-only data:**
- Cabin temperature (interior temp sensor — not available via VHAL)
- 12V battery voltage
- Trip statistics with graphs (mileage, consumption kWh/100km, CO2 saved, avg speed, travel time)
- TBox online/offline/sleep status, SMS wake limits
- Vehicle feature support matrix, firmware versions (AVN MCU/MPU, TBox MCU/MPU)
- FOTA update campaigns with ECU details
- Notifications (alarm, command, news)

**Write controls:**
- BT Digital Key management (activate, deactivate, revoke keys)
- Air clean mode
- Scheduled charging (set time window, enable/disable)
- Geofence (create at current GPS position with radius, delete)
- Send POI to car navigation (current location or custom coordinates)
- Find My Car (horn + lights, or lights only)
- Force TBox wake + status refresh

All cloud features are **greyed out and disabled** when not logged in.

### Navigation Proxy
Registers as the system handler for standard `geo:` URI intents, forwarding them to the car's Telenav navigation. This allows **any third-party app** (e.g. charging station finders) to send addresses to the car's navigation without knowing about Telenav specifically.

Supported formats:
- `geo:lat,lon` — navigate to coordinates
- `geo:lat,lon?q=label` — navigate with label
- `geo:0,0?q=search+query` — search for destination
- `google.navigation:q=lat,lon` — Google Maps compatible

### Vehicle Info
100+ data points: Identity (VIN, MAC, HW/SW versions), Status, Battery, HVAC, ADAS configuration (AEB, FCW, BSD, LKA, TJA, RCTA), APA status, drive mode, Comfort settings, Doors/Windows, ECU status, Maintenance, Lights (SAIC-specific), System, Sensors, Cloud data (cabin temp, 12V, trip stats).

### Quick Controls
- **Doors & Windows**: Lock/unlock, individual window sliders, sunroof
- **Ambient Lighting**: On/off, brightness, color, breathing effect, drive mode link
- **Charging**: Battery pre-heat, charge port lock (local SAIC service)
- **Cloud Controls**: Air clean, scheduled charging, V2L (require iSMART login)
- **Privacy**: Privacy mode, map/voice/music data sharing, mobile data, remote control
- **Developer**: ADB debugging toggle

### TBox (Telematics)
EngMode vehicle status (speed, gear, parking brake, power type), hardware info beans (GNSS with lat/lon/signal/gyro, Bluetooth with connected device, Mobile network with IMEI/signal, WiFi), TBox network interface.

### Location & GPS
GPS position, altitude, accuracy, bearing, speed, satellites (used/visible), street address from navigation service, JSON snapshot, DMS/UTM coordinate formats.

### Diagnostics
- **VHAL Properties**: 943 vehicle properties with descriptions, live values, filtering
- **SAIC Services**: 252 methods across 21 connections, organized by service
- **AIDL TX Codes**: Enumerate transaction codes from all SAIC Stub classes
- **Export**: Logcat, diagnostics dump (including TX codes, binder info), or both to USB/internal storage

### Settings
- Theme selector (Auto/Dark/Light) with car night mode detection
- Default launcher (set/restore)
- Overlay toggle (floating back + recent apps buttons)
- Driver profile (save/restore drive mode + regen level)
- MG iSMART cloud login/logout with status display
- Key capture mode (VHAL 20Hz + Android KeyEvent interception)
- AIDL transaction code viewer (all services)
- Storage export

---

## Architecture

Emegelauncher uses a **7-layer service architecture** to access vehicle data, entirely through **Java reflection** — no proprietary libraries are compiled or bundled.

```
Layer 1: Android Car API
  |- CarPropertyManager --- 943 VHAL properties
  |- CarHvacManager / CarBMSManager

Layer 2: SAIC VehicleSettingsService (via DexClassLoader)
  |- IVehicleSettingService    (128 TX codes: ADAS, comfort, ambient)
  |- IVehicleConditionService  (27 methods: ECU status, maintenance)
  |- IVehicleChargingService   (25 methods: charging, battery, regen)
  |- IVehicleControlService    (12 methods: doors, windows, ESP)
  |- IAirConditionService      (26 methods: HVAC, seat heat)

Layer 3: EngineerModeService
  |- ISystemSettingsManager (ADB, speed, gear, power)
  |- ISystemHardwareManager (GNSS, BT, mobile, WiFi beans)

Layer 4: SaicAdapterService (3 sub-services)
Layer 5: SystemSettingsService (8 sub-services)
Layer 6: vehicleService_overseas

Layer 7: MG iSMART Cloud API
  |- OAuth2 + AES/CBC encryption + HMAC-SHA256 verification
  |- Vehicle status (cabin temp, 12V, trip data)
  |- Statistics (mileage, consumption, CO2, speed, time)
  |- BT digital key management
  |- Geofence, POI, Find My Car, FOTA, messages
```

---

## No Proprietary Code

This project contains **zero proprietary MG/SAIC code**. Verified by automated scan of all source files, imports, dependencies, and binary assets.

**Source code:**
- **Zero proprietary imports** — no `com.saicmotor.*`, `com.saicvehicleservice.*`, `com.yfve.*`, or `android.car.*` imports anywhere in the codebase
- All vehicle services are accessed entirely through **Java reflection** (`Class.forName()`, `Method.invoke()`, `DexClassLoader`)
- SAIC class names appear only as **string literals** for runtime reflection lookups
- AIDL stub classes are loaded at runtime via `DexClassLoader` from APKs already installed on the car's system partition — no stubs are compiled or bundled

**Dependencies:**
- Only **2 libraries**: `androidx.appcompat:1.1.0` and `androidx.constraintlayout:1.1.3` (standard Android Jetpack)
- **No proprietary JARs, AARs, or native .so libraries** in the project

**Cloud API:**
- The iSMART cloud encryption protocol (AES/CBC/PKCS5Padding with MD5-derived keys, HMAC-SHA256 request verification) is **reimplemented from scratch** using standard Java crypto libraries (`javax.crypto.Cipher`, `MessageDigest`, `Mac`)
- No code was copied from MG apps — the protocol was reverse-engineered from the open-source NewMGRemote project

**Resources:**
- **No proprietary images, icons, layouts, or assets** copied from any MG/SAIC application
- VHAL property IDs in `YFVehicleProperty.java` are numeric constants — factual hardware register addresses, not copyrightable code

---

## Building

### Requirements
- Java 11 (`openjdk-11-jdk`)
- Android SDK with Build Tools 28.0.3
- AOSP platform signing key (`platform.keystore`)

### Build

```bash
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 ./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

---

## Target Vehicle

| Spec | Value |
|---|---|
| Vehicle | MG Marvel R (EP21 platform) |
| Display | 19.4" portrait, 1024x1280px |
| OS | Android 9 Automotive (API 28) |
| Battery | 70 kWh nominal |
| Tire pressure | 2.9 bar recommended |

---

## Screens

| Screen | Activity | Description |
|---|---|---|
| Home | MainActivity | SOC, range, drive mode, regen, weather, profile, shortcuts |
| All Apps | AppsActivity | Grid of user-installed applications |
| Graphs | GraphsActivity | 8-tab live vehicle data with gauges and charts |
| Vehicle Info | VehicleInfoActivity | 100+ data points including cloud data |
| Location | LocationActivity | GPS, satellites, address, JSON snapshot |
| Cloud | CloudActivity | iSMART cloud: status, statistics graphs, BT keys, geofence, POI, FOTA |
| Controls | ControlsActivity | Doors, windows, ambient, charging, air clean, privacy |
| TBox | TboxActivity | EngMode hardware data, TBox network |
| Diagnostics | DebugActivity | 943 VHAL + 252 SAIC, filter, export |
| Settings | SettingsActivity | Theme, launcher, cloud login, profile, overlay, developer tools |

---

## Project Structure

```
app/src/main/java/com/emegelauncher/
|- MainActivity.java          # Home screen with profile card
|- AppsActivity.java          # App drawer
|- GraphsActivity.java        # 8-tab vehicle graphs
|- VehicleInfoActivity.java   # Vehicle data + cloud data display
|- LocationActivity.java      # GPS & location
|- CloudActivity.java         # iSMART cloud (6 tabs)
|- ControlsActivity.java      # Quick controls + cloud controls
|- TboxActivity.java          # TBox telematics
|- DebugActivity.java         # Diagnostics (VHAL + SAIC)
|- SettingsActivity.java      # Settings + cloud login + profile
|- OverlayService.java        # Floating back/recent buttons
|- NavProxyActivity.java      # geo: intent proxy to Telenav
|- ThemeHelper.java           # Dark/light theme management
|- vehicle/
|   |- VehicleServiceManager.java  # 6-layer local service architecture
|   |- SaicCloudManager.java      # iSMART cloud API client
|   |- WeatherManager.java        # Weather broadcast + polling
|   |- BatteryHealthTracker.java   # SOH estimation
|   |- YFVehicleProperty.java     # 943 VHAL property constants
|- widget/
    |- ArcGaugeView.java      # Arc gauge (speed, SOC, RPM)
    |- LineChartView.java     # Line chart with auto-scaling
    |- GMeterView.java        # Circular G-force meter
    |- TireDiagramView.java   # 4-corner tire pressure diagram
```

---

## License

This project is licensed under **GNU General Public License v3.0** with the **Commons Clause License Condition v1.0**.

**You may**: use, modify, share, fork, and accept donations.

**You may NOT**: sell this software, offer it as a paid service, or bundle it in commercial products.

See [LICENSE](LICENSE), [LICENSE-GPL3](LICENSE-GPL3), and [LICENSE-COMMONS-CLAUSE](LICENSE-COMMONS-CLAUSE) for full details.

---

## Contributing

Contributions are welcome. By submitting a pull request, you agree that your contribution will be licensed under the same GPLv3 + Commons Clause terms.

## Acknowledgments

- Built using knowledge from Huseyin's DriveHub project's reflection-based approach
- AOSP platform signing key from the Android Open Source Project
- Cloud API protocol reverse-engineered from the NewMGRemote open-source project

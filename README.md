# Emegelauncher

A custom home screen launcher for the **MG Marvel R** electric vehicle, designed to provide deep vehicle telemetry, real-time graphs, diagnostics, and a modern UI on the car's 19.4" portrait infotainment display.

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
>
> **Only install this application from the official source repository.** Modified versions obtained from third parties could silently exfiltrate this data to external servers. The authors of this project have designed it to operate **entirely offline** — it does not transmit any data over the network. However, a tampered version could easily add this capability without your knowledge.
>
> Before installing any build you did not compile yourself, **review the source code**. If someone offers you a pre-built APK, treat it with the same caution you would treat giving someone your car keys.

---

## Features

### Home Screen
- **Battery & Range**: Display SOC and BMS raw SOC with estimated range (multiple sources with fallback)
- **Weather**: Temperature from SAIC weather broadcasts and SharedPreferences polling
- **Inside/Outside Temperature**: Via SAIC IAirConditionService with VHAL fallback
- **App Shortcuts**: All original launcher buttons (Phone, Navigation, Music, Radio, 360 View, CarPlay, Android Auto, Video, Vehicle Settings, System Settings, Rescue Call, MG Touchpoint, User Manual)
- **Dark/Light Theme**: Toggle via Launcher Settings, persisted across restarts

### Vehicle Graphs (7 tabs)
| Tab | Content |
|---|---|
| **Dashboard** | Speed, SOC, RPM, Efficiency gauges + power flow bar + gear + range |
| **Energy** | SOC (display + BMS raw), pack voltage, pack current, consumption (kWh/100km) |
| **Charging** | Live charts when charging, stored session data when idle, AC/DC details, BMS limits, scheduled charge |
| **Health** | Auto-calculated SOH estimation, capacity tracking, resting voltage analysis, charge session history |
| **Tires** | 4-corner pressure (bar) + temperature diagram with color-coded thresholds |
| **Climate** | Temperature chart, PM2.5 inside/outside, ionizer, filter life, air quality |
| **Trip** | Odometer, avg consumption, total consumed, regen energy/range |

### Vehicle Info
100+ data points organized by category: Identity (VIN, MAC addresses, HW/SW versions), Status, Battery (including 12V voltage), HVAC, ADAS configuration, Comfort settings, Doors/Windows, ECU online status, Maintenance, Lights, System.

### Location & GPS
Real-time GPS position with satellite count, vehicle telemetry snapshot, JSON data export format, multiple coordinate formats (Decimal, DMS, UTM).

### Diagnostics
- **VHAL Properties**: 943 vehicle properties with descriptions, live values, and filtering
- **SAIC Services**: 252 methods across 21 service connections, organized by service
- **Export**: Logcat and full diagnostics dump to USB/storage

### Launcher Settings
- Dark/Light theme toggle
- Default launcher selector (set Emegelauncher or restore original)
- Storage export (Logcat, Diagnostics, or both)

---

## Architecture

Emegelauncher uses a **6-layer service architecture** to access vehicle data, entirely through **Java reflection** — no proprietary libraries are compiled or bundled.

```
Layer 1: Android Car API
  ├─ CarPropertyManager ─── 943 VHAL properties (STRING/INT/FLOAT/BOOL, area-aware)
  ├─ CarHvacManager
  └─ CarBMSManager

Layer 2: SAIC VehicleSettingsService
  ├─ Bind: com.saicmotor.service.vehicle.VehicleService
  ├─ Alt:  ServiceManager.getService("vehiclesetting")
  └─ IHubService → 5 sub-services:
      ├─ "aircondition"     → IAirConditionService     (26 methods)
      ├─ "vehiclecondition" → IVehicleConditionService  (27 methods)
      ├─ "vehiclesetting"   → IVehicleSettingService    (39 methods)
      ├─ "vehiclecontrol"   → IVehicleControlService    (12 methods)
      └─ "vehiclecharging"  → IVehicleChargingService   (25 methods)

Layer 3: EngineerModeService
  └─ Bind: com.saicmotor.service.engmode.EngineeringModeService
      └─ "system_setting" → ISystemSettingsManager (8 methods)
         └─ Includes: 12V battery voltage, firmware versions

Layer 4: SaicAdapterService
  └─ 3 separate service bindings:
      ├─ GeneralService  → IGeneralService   (24 methods: nav, road, speed limit)
      ├─ MapService      → IMapService       (22 methods: car type, EV port, TTS)
      └─ VoiceVuiService → IVoiceVuiService  (11 methods: location, map state)

Layer 5: SystemSettingsService
  └─ 8 sub-services via intent actions:
      ├─ IBtService         (6 methods: Bluetooth, CarPlay)
      ├─ IGeneralService    (8 methods: brightness, night mode)
      ├─ IMyCarService      (5 methods: MCU/MPU/TBox versions)
      ├─ ISmartSoundService (17 methods: EQ, Bose, balance, fader)
      ├─ IHotspotService    (3 methods: WiFi hotspot)
      ├─ IGdprService       (4 methods: privacy toggles)
      ├─ IWiFiService       (1 method)
      └─ IDataUsageService  (2 methods)

Layer 6: vehicleService_overseas
  └─ IVehicleAidlInterface (12 methods: AVN ID, auth, activation)

Additional:
  Weather ─── Broadcast receiver + SharedPreferences polling
  GPS ─────── Android LocationManager + GnssStatus.Callback
```

**Total: 21 service connections, ~1195 readable data points**

---

## Vehicle Properties

### Key VHAL Properties

| Property | ID | Type | Unit/Scale |
|---|---|---|---|
| BMS_PACK_SOC | 560002053 | FLOAT | % (BMS raw) |
| BMS_PACK_SOC_DSP | 560002052 | FLOAT | % (display) |
| BMS_PACK_VOL | 560002054 | FLOAT | V |
| BMS_PACK_CRNT | 560002055 | FLOAT | A |
| BMS_ESTD_ELEC_RNG | 557904918 | INT | km |
| CLSTR_ELEC_RNG | 557904966 | INT | km (cluster) |
| PERF_VEHICLE_SPEED | 291504647 | FLOAT | km/h |
| ENGINE_RPM | 291504901 | FLOAT | RPM |
| CURRENT_GEAR | 289408001 | INT | 1=D,2=N,3=R,4=P |
| SENSOR_TOTAL_MILEAGE | 557847910 | INT | km |
| SENSOR_TIRE_PRESURE_FL/FR/RL/RR | 557847891-894 | INT | kPa (÷100=bar) |
| ELEC_CSUMP_PERKM | 560002077 | FLOAT | ÷10=kWh/100km |
| INFO_VIN | 286261504 | STRING | VIN |

### Value Scaling

| Raw Value | Conversion | Example |
|---|---|---|
| Tire pressure | ÷100 = bar | 264 → 2.64 bar |
| Consumption | ÷10 = kWh/100km | 82.3 → 8.23 |
| Charge time | 1023 = N/A | sentinel value |
| Target SOC | BMS raw scale | 7% raw ≈ 100% display |
| Gear | 1=D, 2=N, 3=R, 4=P | |
| SOC gap | Display - Raw ≈ 7% | 65.8% raw → 73% display |

For the complete list of all 943 VHAL properties and 252 SAIC service methods, see [PROPERTIES_REFERENCE.md](PROPERTIES_REFERENCE.md).

---

## No Proprietary Code

This project contains **zero proprietary MG/SAIC code**.

- **No SAIC/MG source files** are included in the project
- **No proprietary JARs or libraries** are compiled or bundled (only `androidx.appcompat` and `androidx.constraintlayout`)
- **No direct imports** of `android.car.*`, `com.saicmotor.*`, or `com.saicvehicleservice.*`
- All vehicle services are accessed entirely through **Java reflection** (`Class.forName()`, `Method.invoke()`)
- SAIC class names appear only as **string literals** for reflection lookups
- `YFVehicleProperty.java` contains numeric constants (property IDs) — these are factual numeric values, not copyrightable code

---

## Building

### Requirements
- Java 11 (`openjdk-11-jdk`)
- Android SDK with Build Tools 28.0.3
- AOSP platform signing key (included as `platform.keystore`)

### Build

```bash
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 ./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Why Platform Signing?

The app uses `android:sharedUserId="android.uid.system"` to access car services that require system-level permissions. This means the APK must be signed with the same key as the vehicle's system partition. The MG Marvel R uses the **AOSP default platform test key**, which is publicly available.

---

## Installation

TBD

---

## Target Vehicle

| Spec | Value |
|---|---|
| Vehicle | MG Marvel R (EP21 platform) |
| Display | 19.4" portrait, 1024×1280px |
| Usable area | ~1024×760px (system overlays top/bottom) |
| OS | Android 9 Automotive (API 28) |
| Battery | 70 kWh nominal |
| Tire pressure | 2.9 bar recommended |

---

## Screens

| Screen | Description |
|---|---|
| Home | SOC, range, weather, temps, app shortcuts |
| All Apps | Grid of all installed applications |
| Graphs | 7-tab live vehicle data visualization |
| Vehicle Info | 100+ static data points |
| Location | GPS position, satellites, JSON snapshot |
| Diagnostics | 943 VHAL + 252 SAIC methods, filter, dual tabs |
| Settings | Theme, default launcher, export tools |

---

## Project Structure

```
app/src/main/java/com/emegelauncher/
├── MainActivity.java          # Home screen
├── AppsActivity.java          # App drawer
├── GraphsActivity.java        # 7-tab vehicle graphs
├── VehicleInfoActivity.java   # Vehicle data display
├── LocationActivity.java      # GPS & location
├── DebugActivity.java         # Diagnostics (VHAL + SAIC)
├── SettingsActivity.java      # Launcher settings
├── ThemeHelper.java           # Dark/light theme management
├── vehicle/
│   ├── VehicleServiceManager.java  # 6-layer service architecture
│   ├── WeatherManager.java         # Weather broadcast + polling
│   ├── BatteryHealthTracker.java   # SOH estimation
│   └── YFVehicleProperty.java     # 943 VHAL property constants
└── widget/
    ├── ArcGaugeView.java      # Arc gauge (speed, SOC, RPM)
    ├── LineChartView.java     # Line chart with auto-scaling
    ├── PowerFlowBar.java      # Bidirectional power flow
    └── TireDiagramView.java   # 4-corner tire diagram
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

- Built using knowledge from the [Huseyin's DriveHub] (Not released) project's reflection-based approach
- AOSP platform signing key from the Android Open Source Project

# Emegelauncher - Writable Methods Reference

All setter/action methods available through SAIC services on the MG Marvel R (EP21).
These methods can MODIFY vehicle settings and state. Use with extreme caution.

> **WARNING**: Writing incorrect values can affect vehicle safety systems (AEB, ESP, HDC, airbags).
> Only implement write operations after thorough testing. Some methods interact with CAN bus
> directly and could affect vehicle behavior while driving.

Updated: 2026-03-17

---

## IAirConditionService (HVAC Control)

### Power & Mode
| Method | Parameters | Description |
|---|---|---|
| `openHvacPower()` | none | Turn ON HVAC system |
| `closeHvacPower()` | none | Turn OFF HVAC system |
| `setHvacPowerStatus(int)` | 0=off, 1=on | Set HVAC power |
| `setAcStatus(int)` | 0=off, 1=on | AC compressor on/off |
| `setAutoStatus(int)` | 0=off, 1=on | Auto climate mode |
| `setEconStatus(int)` | 0=off, 1=on | Economy mode |
| `setTempDualZoneOn(int)` | 0=off, 1=on | Dual zone climate |

### Temperature
| Method | Parameters | Description |
|---|---|---|
| `setDrvTemp(int)` | 15-29°C | Driver zone temperature |
| `setPsgTemp(int)` | 15-29°C | Passenger zone temperature |
| `increaseTemp(int, bool)` | amount, isDrv | Increase temp (driver/passenger) |
| `reduceTemp(int, bool)` | amount, isDrv | Decrease temp (driver/passenger) |
| `setTempMax(bool)` | isDrv | Set HI (maximum) |
| `setTempMin(bool)` | isDrv | Set LO (minimum) |

### Fan & Air Direction
| Method | Parameters | Description |
|---|---|---|
| `setAirVolumeLevel(int)` | 1-11 | Fan speed level |
| `setAirVolumeMax()` | none | Fan to maximum |
| `setAirVolumeMin()` | none | Fan to minimum |
| `increaseAirVolumeLevel(int)` | amount | Increase fan speed |
| `reduceAirVolumeLevel(int)` | amount | Decrease fan speed |
| `setBlowerDirectionMode(int)` | mode enum | Air vent direction (face/feet/both/defrost) |
| `setLoopMode(int)` | mode enum | Recirculation mode |
| `openLoopAuto()` | none | Auto recirculation |
| `openLoopInner()` | none | Recirculate (inner) |
| `openLoopOutside()` | none | Fresh air (outer) |
| `setWindOutletSmartStatus(int)` | 0/1 | Smart vent outlet |

### Defroster
| Method | Parameters | Description |
|---|---|---|
| `openFrontWindowDefroster()` | none | Front defroster ON |
| `closeFrontWindowDefroster()` | none | Front defroster OFF |

### Seat Heating & Ventilation
| Method | Parameters | Description |
|---|---|---|
| `setDrvSeatHeatLevel(int)` | 0-3 | Driver seat heat level |
| `setPsgSeatHeatLevel(int)` | 0-3 | Passenger seat heat level |
| `openDrvSeatHeat()` | none | Driver seat heat ON |
| `closeDrvSeatHeat()` | none | Driver seat heat OFF |
| `openPsgSeatHeat()` | none | Passenger seat heat ON |
| `closePsgSeatHeat()` | none | Passenger seat heat OFF |
| `setDrvSeatWindLevel(int)` | 0-3 | Driver seat ventilation |
| `setPsgSeatWindLevel(int)` | 0-3 | Passenger seat ventilation |

### Wind Outlet Angles & Modes
| Method | Parameters | Description |
|---|---|---|
| `setDrvLeftWindOutletAngle(float)` | angle | Driver left vent angle |
| `setDrvLeftMiddleWindOutletAngle(float)` | angle | Driver left-middle vent angle |
| `setPsgRightWindOutletAngle(float)` | angle | Passenger right vent angle |
| `setPsgRightMiddleWindOutletAngle(float)` | angle | Passenger right-middle vent angle |
| `setDrvWindOutletAvoidPersonModeStatus(int)` | 0/1 | Driver avoid-person mode |
| `setDrvWindOutletBlowPersonModeStatus(int)` | 0/1 | Driver blow-person mode |
| `setDrvWindOutletOffModeStatus(int)` | 0/1 | Driver vent off |
| `setDrvWindOutletSwingModeStatus(int)` | 0/1 | Driver vent swing |
| `setPsgWindOutletAvoidPersonModeStatus(int)` | 0/1 | Passenger avoid-person mode |
| `setPsgWindOutletBlowPersonModeStatus(int)` | 0/1 | Passenger blow-person mode |
| `setPsgWindOutletOffModeStatus(int)` | 0/1 | Passenger vent off |
| `setPsgWindOutletSwingModeStatus(int)` | 0/1 | Passenger vent swing |

---

## IVehicleControlService (Physical Controls)

| Method | Parameters | Description |
|---|---|---|
| `setDoorLock(int)` | 0=unlock, 1=lock | Lock/unlock all doors |
| `setDriveWindow(float)` | 0.0-100.0 | Driver window position |
| `setPassengerWindow(float)` | 0.0-200.0 | Passenger window position (note: 0-200 range) |
| `setLeftRearWindow(float)` | 0.0-100.0 | Left rear window position |
| `setRightRearWindow(float)` | 0.0-100.0 | Right rear window position |
| `setSunroofSwitch(int)` | state enum | Open/close sunroof |
| `setSunroofVentilation(int)` | state enum | Sunroof tilt ventilation |
| `setElectricTailgateLock(float)` | position | Electric tailgate open/close |
| `setEspSwitch(int)` | 0=off, 1=on | **Electronic Stability Program** |
| `setHdcSwitch(int)` | 0=off, 1=on | **Hill Descent Control** |
| `setPdcSwitch(int)` | 0=off, 1=on | Park Distance Control on/off |
| `resetVehicleSettings(int)` | category | **Factory reset** settings by category |

---

## IVehicleSettingService (ADAS & Comfort Settings)

### ADAS / Safety
| Method | Parameters | Description |
|---|---|---|
| `setAutoEmergencyBraking(int)` | 0=off, 2=on | **AEB** on/off |
| `setFcwAlarmMode(int)` | mode | Forward collision warning mode |
| `setFcwSensitivity(int)` | level | FCW sensitivity |
| `setFcwAutoBrakeMode(int)` | mode | FCW auto brake |
| `setBlindSpotDetection(int)` | 0/1 | Blind spot detection |
| `setLaneKeepingAsstMode(int)` | mode | Lane keeping assist mode |
| `setLaneKeepingAsstSen(int)` | level | Lane keeping sensitivity |
| `setLaneKeepingVibration(int)` | 0/1 | Lane keeping vibration warning |
| `setLaneKeepingWarningSound(int)` | 0/1 | Lane keeping audible warning |
| `setLaneChangeAsst(int)` | 0/1 | Lane change assist |
| `setTrafficJamAsstOn(int)` | 0/1 | Traffic jam assist |
| `setRearDriveAsstSys(int)` | 0/1 | Rear drive assist |
| `setRearTrafficWarning(int)` | 0/1 | Rear cross-traffic alert |
| `setDrowsinessMonitorSysOn(int)` | 0/1 | Drowsiness monitoring |
| `setDrowsinessMonitorSysSen(int)` | level | Drowsiness sensitivity |
| `setSpeedAsstMode(int)` | mode | Speed assist mode |
| `setSpeedAsstSlifWarning(int)` | 0/1 | Speed limit warning |
| `setPsgSafetyAirbagOn(int)` | 0/1 | **Passenger airbag** on/off |
| `setParkingWarning(int)` | 0/1 | Parking sensor warning |

### Ambient Lighting
| Method | Parameters | Description |
|---|---|---|
| `setAmbtLightGlbOn(int)` | 0/1 | Ambient light master on/off |
| `setAmbtLightBrightness(int)` | 0-10 | Ambient light brightness |
| `setAmbtLightColor(int)` | color code | Ambient light color |
| `setAmbtLightDrvMode(int)` | mode | Ambient light drive mode link |
| `setAmbtLightBreathingOn(int)` | 0/1 | Breathing effect |
| `setAmbtLightOpenMode(int)` | mode | Open animation mode |
| `setAmbtLightWlcmMode(int)` | mode | Welcome animation mode |
| `setAmbtLightWlcmOn(int)` | 0/1 | Welcome animation on/off |

### Door & Lock Behavior
| Method | Parameters | Description |
|---|---|---|
| `setDrivingAutoLock(int)` | 0/1 | Auto lock when driving |
| `setStallingAutoUnlock(int)` | 0/1 | Auto unlock on park |
| `setKeyUnlockMode(int)` | mode | Key fob unlock (driver/all) |
| `setNearfieldUnlockMode(int)` | mode | NFC/phone key unlock |
| `setInductiveDoorHandle(int)` | 0/1 | Touch door handle |
| `setInductiveTailgate(int)` | 0/1 | Kick-to-open tailgate |
| `setElectricTailgatePos(float)` | 0.0-100.0 | Tailgate max open position |
| `setOuterRearviewFold(int)` | 0/1 | Auto-fold mirrors on lock |
| `setLeftRearviewDowndip(int)` | 0/1 | Left mirror dip on reverse |
| `setRightRearviewDowndip(int)` | 0/1 | Right mirror dip on reverse |

### Comfort & Welcome
| Method | Parameters | Description |
|---|---|---|
| `setHomeLightTime(int)` | seconds | Follow-me-home light duration |
| `setWelcomeLightTime(int)` | seconds | Welcome light duration |
| `setWelcomeSoundOn(int)` | 0/1 | Welcome sound |
| `setWelcomeSoundType(int)` | type | Welcome sound type |
| `setAutoMainBeamControl(int)` | 0/1 | Auto high beam |
| `setSeatAutoAdjust(int)` | 0/1 | Seat auto-adjust on entry |
| `setDriverSeatAutoWlcm(int)` | 0/1 | Driver seat welcome position |
| `setSeatHeatVentLinkage(int)` | 0/1 | Seat heat/vent auto with temp |
| `setDefrostLinkage(int)` | 0/1 | Defrost-AC linkage |
| `setHvacEconLinkage(int)` | 0/1 | HVAC econ linkage |
| `setTowingMode(int)` | 0/1 | Towing mode |
| `setSteeringWheelDefine(int)` | mode | Steering button customization |
| `setCarFeedbackMode(int)` | mode | Haptic feedback mode |
| `setCarFeedbackTime(int)` | time | Haptic feedback duration |

---

## IVehicleChargingService (EV Charging & V2L)

### Charging Control
| Method | Parameters | Description |
|---|---|---|
| `setChargingControlSwitch(int)` | 0/1 | Charging master switch |
| `setChargingCurrent(int)` | amps | Set max charging current |
| `setChargingCloseSoc(int)` | SOC value | Stop charging at this SOC |
| `setChargingLockSwitch(int)` | 0/1 | Charge port cable lock |
| `setDrivingBatteryHeat(int)` | 0/1 | Battery pre-heating |

### V2L (Vehicle-to-Load) Discharge
| Method | Parameters | Description |
|---|---|---|
| `setDischrgControlSwitch(int)` | 0/1 | V2L discharge on/off |
| `setDischrgCloseSoc(int)` | SOC value | Stop V2L at this SOC |

### Scheduled Charging
| Method | Parameters | Description |
|---|---|---|
| `setReserChrgControl(int)` | 0/1 | Scheduled charging on/off |
| `setReserChrgStartHour(int)` | 0-23 | Start hour |
| `setReserChrgStartMinute(int)` | 0-59 | Start minute |
| `setReserChrgStopHour(int)` | 0-23 | Stop hour |
| `setReserChrgStopMinute(int)` | 0-59 | Stop minute |
| `setReserChrgAdpPileType(int)` | type | Charger adapter type |

### BLE Charging Pile
| Method | Parameters | Description |
|---|---|---|
| `setBtEnabled(bool)` | true/false | BLE for charging on/off |
| `addChargingPile(String)` | BLE address | Add charging pile |
| `connectChargingPile(ChargingPile)` | pile object | Connect to pile |
| `disconnectChargingPile(ChargingPile)` | pile object | Disconnect from pile |
| `deleteChargingPile(ChargingPile)` | pile object | Remove saved pile |
| `startScan(bool)` | active scan | Start BLE scan |
| `stopScan()` | none | Stop BLE scan |

---

## IVehicleConditionService

| Method | Parameters | Description |
|---|---|---|
| `resetCarMileageInfo()` | none | Reset trip mileage |

---

## SystemSettingsService Sub-Services

### IBtService (Bluetooth)
| Method | Parameters | Description |
|---|---|---|
| `setBluetoothEnabled(bool)` | true/false | Bluetooth on/off |
| `setBtName(String)` | name | Set BT device name |
| `setAutoPairMode(bool)` | true/false | Auto-pair mode |
| `setDiscoverable(bool)` | true/false | BT discoverable |
| `connect(BluetoothDevice)` | device | Connect to device |
| `disconnect(BluetoothDevice)` | device | Disconnect device |
| `pair(BluetoothDevice)` | device | Pair with device |
| `unPair(BluetoothDevice)` | device | Unpair device |
| `startScanning()` | none | Start BT scan |
| `stopScanning()` | none | Stop BT scan |

### IGeneralService (Display & Time)
| Method | Parameters | Description |
|---|---|---|
| `setBrightness(int)` | level | Screen brightness |
| `setBrightnessAutoMode(bool)` | true/false | Auto brightness |
| `setIsNightMode(bool)` | true/false | Force night/dark mode |
| `setDayNightAutoMode(bool)` | true/false | Auto day/night |
| `set24Hour(bool)` | true/false | 24-hour clock format |
| `setTimeAuto(bool)` | true/false | Auto time sync |
| `setTime(long)` | millis | Set system time |
| `setTimeZone(String)` | timezone | Set timezone |
| `setSummerTimeMode(bool)` | true/false | Daylight saving time |
| `setDimClockEnabled(bool)` | true/false | Dim clock when screen off |
| `updateLocale(String)` | locale code | Change system language |

### ISmartSoundService (Audio)
| Method | Parameters | Description |
|---|---|---|
| `setVolume(int, int, int)` | stream, vol, flags | Set volume |
| `setVolumeUp()` | none | Volume up |
| `setVolumeDown()` | none | Volume down |
| `setVolumeMax()` | none | Maximum volume |
| `setVolumeMin()` | none | Minimum volume |
| `setMute()` | none | Mute all |
| `cancelMute()` | none | Unmute |
| `set3DEffectType(int)` | type | 3D sound effect |
| `setBoseSoundEffect(int)` | preset | Bose sound preset |
| `setEqualizerBand(int)` | band | EQ preset |
| `setLoudnessState(bool)` | true/false | Loudness enhancement |
| `setSubwooferState(bool)` | true/false | Subwoofer on/off |
| `setSoundField(int, int)` | x, y | Sound sweet spot |
| `setSpeedVolumeControlLevel(int)` | level | Speed-dependent volume |
| `setNaviDuckState(bool)` | true/false | Duck music for nav |
| `setRearQuietModeState(bool)` | true/false | Rear quiet mode |
| `setSystemBeepState(bool)` | true/false | System beeps |
| `setRingtoneState(bool)` | true/false | Ringtone on/off |
| `setVehicleInfoVolume(int)` | level | Vehicle info chime vol |
| `setVoiceVolume(int)` | level | Voice assistant vol |
| `setToneControl(SoundToneInfo)` | bass/mid/treble | EQ tone control |
| `setUserBand(SoundUserEqualizerInfo)` | custom EQ | Custom EQ bands |

### IHotspotService
| Method | Parameters | Description |
|---|---|---|
| `setTetheringEnabled(bool)` | true/false | WiFi hotspot on/off |
| `setApBand(int)` | 2.4/5 GHz | Hotspot frequency band |
| `setPassword(String)` | password | Hotspot password |

### IWiFiService
| Method | Parameters | Description |
|---|---|---|
| `setWifiEnabled(bool)` | true/false | WiFi on/off |
| `addNetwork(String, int, String)` | ssid, security, pw | Add WiFi network |
| `connect(SettingsWifiInfo)` | network info | Connect to WiFi |
| `disConnect(SettingsWifiInfo)` | network info | Disconnect WiFi |
| `startScanning()` | none | Start WiFi scan |
| `stopScanning()` | none | Stop WiFi scan |

### IGdprService (Privacy)
| Method | Parameters | Description |
|---|---|---|
| `setMapEnabled(bool)` | true/false | Map data consent |
| `setOnlineMusicEnabled(bool)` | true/false | Online music consent |
| `setPrivacyEnabled(bool)` | true/false | Privacy mode master |
| `setVoiceEnabled(bool)` | true/false | Voice data consent |

### IDataUsageService
| Method | Parameters | Description |
|---|---|---|
| `setDataEnabled(bool)` | true/false | Mobile data on/off |
| `setRemoteControlEnabled(bool)` | true/false | Remote control via app |

---

## EngineerModeService (ISystemSettingsManager)

| Method | Parameters | Description |
|---|---|---|
| `engModeStart(bool)` | start/stop | Enter/exit engineering mode |
| `setADBDebug(bool)` | true/false | **ADB debugging** on/off |
| `setAndroidAutoStatus(bool)` | true/false | Android Auto on/off |
| `setAutoSwitchStatus(bool)` | true/false | Auto switch status |
| `setShowRoom(bool)` | true/false | Showroom/demo mode |
| `setTempRunTime(int)` | minutes | Temp run time for ACC-off |
| `factoryReset()` | none | **FULL FACTORY RESET** |
| `rebootSystem()` | none | **REBOOT HEAD UNIT** |
| `enterAndroidAuto()` | none | Launch Android Auto |
| `sendUpgradeFile(String)` | path | Send firmware file |
| `upgradeMCU()` | none | **Flash MCU firmware** |
| `upgradeDSP()` | none | **Flash DSP firmware** |
| `upgradeTBox()` | none | **Flash TBox firmware** |
| `upgradeTouch()` | none | **Flash touch controller** |
| `upgradeRL78()` | none | **Flash RL78 firmware** |
| `upgradeDAB()` | none | **Flash DAB radio firmware** |

---

## TBox Communication

The TBox is a separate hardware module (YFVE) that communicates via:
- TCP socket port 8888 (binary protocol, 7-byte header)
- UDP port 9999
- JNI native library `TboxNative`
- Azure IoT Hub MQTT for cloud

### TBox API (via tbox.jar)
| Method | Description |
|---|---|
| `getGPSInfo()` | Get GPS from TBox |
| `queryCurGPSInfo()` | Query current GPS |
| `getCSQ()` | Signal quality |
| `getIPAddress()` | TBox IP |
| `getIMSI()` | SIM IMSI |
| `getMCUVersion()` | TBox MCU version |
| `getMPUVersion()` | TBox MPU version |
| `getModemVersion()` | TBox modem version |
| `queryAllInfo()` | Query all TBox info |
| `resetTbox(int)` | Reset (1=modem, 2=MCU, 3=MPU, 4=BT) |

---

## Summary by Risk Level

### Safe (display/comfort only)
Ambient lighting, welcome sounds/lights, clock format, language, brightness, WiFi, BT, hotspot, sound settings, GDPR toggles

### Moderate (affects driving comfort)
HVAC temperature/fan/mode, seat heating/ventilation, windows, sunroof, door locks, mirror fold/dip, tailgate, PDC

### High (affects vehicle safety — DO NOT modify while driving)
AEB, FCW, lane keeping, blind spot detection, ESP, HDC, traffic jam assist, drowsiness monitor, speed assist, passenger airbag

### Critical (destructive — can brick the system)
Factory reset, firmware upgrades (MCU/DSP/TBox/Touch/RL78/DAB), reboot system, engineering mode

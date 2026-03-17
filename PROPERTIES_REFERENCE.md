# Emegelauncher - Vehicle Properties Reference

This document lists all known data sources for the MG Marvel R (EP21) head unit.
Updated: 2026-03-17

## Data Sources Architecture

```
Layer 1: Android Car API (CarPropertyManager, CarHvacManager, CarBMSManager)
  └─ 943 VHAL properties via YFVehicleProperty.java
  └─ Property types: STRING(0x1), BOOLEAN(0x2), INT32(0x4), FLOAT(0x6)
  └─ Area types: GLOBAL(0x1), WINDOW(0x2), MIRROR(0x3), SEAT(0x5), WHEEL(0x6), DOOR(0x7)

Layer 2: SAIC VehicleSettingsService (IHubService → 5 sub-services)
  └─ Bind: com.saicmotor.service.vehicle.VehicleService
  └─ Alt: ServiceManager.getService("vehiclesetting")
  └─ Keys: aircondition, vehiclecondition, vehiclesetting, vehiclecontrol, vehiclecharging
  └─ 129 read methods

Layer 3: EngineerModeService (IEngineeringMode → sub-services)
  └─ Bind: com.saicmotor.service.engmode.EngineeringModeService
  └─ Key: "system_setting" → ISystemSettingsManager
  └─ 8 read methods (inc. 12V battery voltage)

Layer 4: SaicAdapterService (3 separate service bindings)
  └─ Bind by component: com.saicmotor.adapterservice.services.{GeneralService,MapService,VoiceVuiService}
  └─ 57 read methods (nav state, road info, car type, location)

Layer 5: SystemSettingsService (8 sub-services via intent actions)
  └─ Bind by action: com.saicmotor.service.systemsettings.I{Bt,General,MyCar,SmartSound,Hotspot,Gdpr,WiFi,DataUsage}Service
  └─ 46 read methods (BT, sound, display, versions, WiFi, GDPR)

Layer 6: vehicleService_overseas (IVehicleAidlInterface)
  └─ Bind by action: com.saic.vehicle.VehicleService (pkg: com.saicvehicleservice)
  └─ 12 read methods (AVN ID, auth, activation, server URL)

Total: 21 service connections, ~1195 readable data points
```

---

## SAIC Sub-Services (Layer 2)

### IAirConditionService (key: "aircondition")
| Method | Returns | Description |
|---|---|---|
| getOutCarTemp | float | Outside temperature (°C) |
| getDrvTemp | int | Driver zone set temp |
| getPsgTemp | int | Passenger zone set temp |
| getMinTemp | int | Min temp available |
| getMaxTemp | int | Max temp available |
| getHvacPowerStatus | int | HVAC power (0=off,1=on) |
| getAcSwitch | int | AC compressor (0=off,1=on) |
| getAutoStatus | int | Auto climate mode |
| getEconStatus | int | Econ mode |
| getAirVolumeLevel | int | Fan speed level |
| getMinAirVolume | int | Min fan speed |
| getMaxAirVolume | int | Max fan speed |
| getLoopMode | int | Recirc (0=fresh,1=recirc,2=auto) |
| getBlowerDirectionMode | int | Air direction |
| getFrontWindowDefroster | int | Front defroster |
| getBackWindowDefroster | int | Rear defroster |
| getDrvSeatHeatLevel | int | Driver seat heat (0-3) |
| getPsgSeatHeatLevel | int | Passenger seat heat (0-3) |
| getDrvSeatWindLevel | int | Driver seat vent (0-3) |
| getPsgSeatWindLevel | int | Passenger seat vent (0-3) |
| getAnionStatus | int | Ionizer (0=off,1=on) |
| getPm25Concentration | int | Interior PM2.5 (µg/m³) |
| getPm25Filter | int | PM2.5 filter status |
| getTempDualZoneOn | int | Dual zone (0=off,1=on) |
| getSmartBlowerStatus | int | Smart air distribution |
| getWindOutletCanStatus | int | Wind outlet status |
| getAirConditionStatus | AirConditionBean | All HVAC in one object |

### IVehicleConditionService (key: "vehiclecondition")
| Method | Returns | Description |
|---|---|---|
| getCarSpeed | float | Speed (km/h) |
| getCarGear | int | Gear (1=D,2=N,3=R,4=P) |
| getVehicleIgnition | int | Ignition state |
| getEngineState | int | Motor state |
| getVinNumber | String | VIN |
| getVehicleType | int | Vehicle type code |
| getVehicleExteriorColor | int | Exterior color code |
| getDistanceUnit | int | km/miles |
| getMileageUnit | int | Mileage unit |
| getEcallState | int | Emergency call state |
| getMaintenanceStatus | int | Service due |
| getNextResetDay | int | Days to next service |
| getNextResetMileage | int | km to next service |
| getAcAvlbly | int | AC ECU online |
| getBcmAvlbly | int | BCM online |
| getBmsAvlbly | int | BMS online |
| getHcuAvlbly | int | HCU online |
| getMsmAvlbly | int | MSM online |
| getPepsAvlbly | int | PEPS online |
| getRadarAvlbly | int | Radar online |
| getScsAvlbly | int | SCS online |
| getApaAvlbly | int | APA online |
| getFvcmAvlbly | int | Forward camera online |
| getPlcmAvlbly | int | Parking camera online |
| getConfig360 | int | 360 camera config |
| getEp21CarConfigCode | int | Car config (EP21) |
| getEngModeStackStatus | boolean | Eng mode active |
| getVehicleCondition | VehicleConditionBean | All condition data |

### IVehicleSettingService (key: "vehiclesetting")
| Method | Returns | Description |
|---|---|---|
| getAutoEmergencyBraking | int | AEB on/off |
| getAutoMainBeamControl | int | Auto high beam |
| getBlindSpotDetection | int | BSD on/off |
| getFcwAlarmMode | int | FCW alarm mode |
| getFcwSensitivity | int | FCW sensitivity |
| getFcwAutoBrakeMode | int | FCW auto brake |
| getLaneKeepingAsstMode | int | Lane keeping mode |
| getLaneKeepingAsstSen | int | Lane keeping sensitivity |
| getLaneChangeAsst | int | Lane change assist |
| getTrafficJamAsstOn | int | TJA on/off |
| getDrowsinessMonitorSysOn | int | Drowsiness on/off |
| getDrowsinessMonitorSysSen | int | Drowsiness sensitivity |
| getSpeedAsstMode | int | Speed assist mode |
| getRearDriveAsstSys | int | RDA on/off |
| getRearTrafficWarning | int | RCTA on/off |
| getAmbtLightGlbOn | int | Ambient light on/off |
| getAmbtLightBrightness | int | Ambient brightness |
| getAmbtLightColor | int | Ambient color code |
| getAmbtLightDrvMode | int | Ambient drive linkage |
| getDrivingAutoLock | int | Auto lock driving |
| getStallingAutoUnlock | int | Auto unlock park |
| getKeyUnlockMode | int | Key unlock mode |
| getInductiveDoorHandle | int | Touch door handle |
| getInductiveTailgate | int | Hands-free tailgate |
| getHomeLightTime | int | Home light (seconds) |
| getWelcomeLightTime | int | Welcome light (seconds) |
| getWelcomeSoundOn | int | Welcome sound |
| getWelcomeSoundType | int | Welcome sound type |
| getDefrostLinkage | int | Defrost-AC linkage |
| getHvacEconLinkage | int | HVAC econ linkage |
| getSeatHeatVentLinkage | int | Seat heat/vent auto |
| getTowingMode | int | Towing mode |
| getPsgSafetyAirbagOn | int | Passenger airbag |
| getElectricTailgatePos | float | Tailgate position (%) |
| getOuterRearviewFold | int | Mirror auto fold |
| getVehicleSettingStatus | VehicleSettingBean | All settings |

### IVehicleControlService (key: "vehiclecontrol")
| Method | Returns | Description |
|---|---|---|
| getDoorLock | int | Lock (0=unlock,1=lock) |
| getEspSwitch | int | ESP on/off |
| getHdcSwitch | int | HDC on/off |
| getPdcSwitch | int | PDC on/off |
| getSunroofSwitch | int | Sunroof state |
| getSunroofVentilation | int | Sunroof vent |
| getDriveWindow | float | Driver window (%) |
| getPassengerWindow | float | Passenger window (%) |
| getLeftRearWindow | float | Left rear window (%) |
| getRightRearWindow | float | Right rear window (%) |
| getElectricTailgateEnable | int | Tailgate enable |
| getElectricTailgateOpenStatus | int | Tailgate open |
| getVehicleControlStatus | VehicleControlBean | All controls |

### IVehicleChargingService (key: "vehiclecharging")
| Method | Returns | Description |
|---|---|---|
| getCurrentElectricQuantity | float | Display SOC (%) |
| getCurrentEnduranceMileage | int | Display range (km) |
| getChargingStatus | int | 0=none,1=AC,2=DC,3=done |
| getChargingCurrent | int | Current setting (A) |
| getActualChargingCurrent | float | Actual current (A) |
| getExpectedCurrent | float | Expected current (A) |
| getChargingStopReason | int | Stop reason code |
| getChargingControlSwitch | int | Control switch |
| getChargingLockSwitch | int | Cable lock |
| getChargingCloseSoc | int | Charge limit SOC |
| getPredictChargingTime | int | Time to full (min) |
| getDrivingBatteryHeat | int | Battery heater |
| getDischrgControlStatus | int | V2L status |
| getDischrgControlSwitch | int | V2L on/off |
| getDischrgCloseSoc | int | V2L min SOC |
| getPredictDischrgTime | int | V2L time remain |
| getDischrgClosePredictMileage | int | V2L range impact |
| getReserChrgControl | int | Scheduled on/off |
| getReserChrgStartHour | int | Schedule start H |
| getReserChrgStartMinute | int | Schedule start M |
| getReserChrgStopHour | int | Schedule stop H |
| getReserChrgStopMinute | int | Schedule stop M |
| getReserChrgAdpPileType | int | Charger type |
| getIsLowLimit | int | Low battery limit |
| getVehicleChargingStatus | VehicleChargingBean | All charging data |

### IScreenManagerService (key: "vehiclescreen")
| Method | Returns | Description |
|---|---|---|
| screenSleep | void | Put screen to sleep |
| screenWakeup | void | Wake screen |
| resumeScreenSleep | void | Resume sleep timer |
| Not useful for data reading | | |

---

## VHAL Properties — Key Categories

### Battery & Energy
| Property | ID | Type | Unit/Scale | Notes |
|---|---|---|---|---|
| BMS_PACK_SOC | 560002053 | FLOAT | % | Raw BMS SOC |
| BMS_PACK_SOC_DSP | 560002052 | FLOAT | % | Display SOC |
| BMS_PACK_VOL | 560002054 | FLOAT | V | Pack voltage |
| BMS_PACK_CRNT | 560002055 | FLOAT | A | Pack current |
| BMS_ESTD_ELEC_RNG | 557904918 | INT | km | BMS range |
| BMS_DIS_ESTD_ELEC_RNG | 557904941 | INT | km | Display range |
| CLSTR_ELEC_RNG | 557904966 | INT | km | Cluster range |
| VEH_ELEC_RNG | 557904924 | INT | km | Vehicle range |
| BMS_ODO_FCT | 557904967 | INT | | BMS correction factor |
| EV_BATTERY_LEVEL | 291504905 | FLOAT | % | Standard EV SOC |
| INFO_EV_BATTERY_CAPACITY | 291504390 | FLOAT | kWh | Nominal capacity |
| EV_BATTERY_INSTANTANEOUS_CHARGE_RATE | 291504908 | FLOAT | kW | Charge rate |

### Charging
| Property | ID | Type | Unit/Scale | Notes |
|---|---|---|---|---|
| BMS_CHRG_STS | 557904905 | INT | enum | 0=none,1=AC,2=DC,3=done |
| CHRGNG_RMNNG_TIME | 557904919 | INT | min | 1023=N/A |
| CHRG_TRGT_SOC | 557904908 | INT | % | BMS raw target |
| BMS_CHRG_OPT_CRNT | 560002058 | FLOAT | A | BMS optimal current |
| BMS_CHRG_SP_RSN | 557904917 | INT | code | Stop reason |
| BMS_CHRG_DOOR_POS_STS | 557904955 | INT | enum | 0=closed,1=open |
| CCU_ONBD_CHRG_PLUG_ON | 557904958 | INT | bool | AC plug |
| CCU_OFFBD_CHRG_PLUG_ON | 557904959 | INT | bool | DC plug |
| ONBD_CHRG_ALT_CRNT_LNPT_VOL | 560002108 | FLOAT | V | AC input voltage |
| ONBD_CHRG_ALT_CRNT_LNPT_CRNT | 560002109 | FLOAT | A | AC input current |
| EV_CHARGE_PORT_CONNECTED | 287310603 | BOOL | | Plug connected |
| EV_CHARGE_PORT_OPEN | 287310602 | BOOL | | Port open |
| ALTNG_CHRG_CRNT | 557904907 | INT | A | Alternating current |
| SPR_CHRG | 557904916 | INT | | Super charge |
| DIS_CHRG_RMNNG_TIME | 557904939 | INT | min | V2L time |

### Consumption & Regen
| Property | ID | Type | Unit/Scale | Notes |
|---|---|---|---|---|
| ELEC_CSUMP_PERKM | 560002077 | FLOAT | ÷10=kWh/100km | Consumption |
| CRNT_AVG_ELEC_CSUMP | 560002075 | FLOAT | ÷10=kWh/100km | Avg consumption |
| BAT_ELEC_ENRG_AVG_RATE | 560002081 | FLOAT | | Avg energy rate |
| TOTAL_CONSUMPTION_AFTER_CHARGE | 559980953 | FLOAT | Wh | Since charge |
| TOTAL_CONSUMPTION_AFTER_START | 559980954 | FLOAT | Wh | Since ignition |
| ACC_CONSUMPTION_AFTER_CHARGE | 559980951 | FLOAT | Wh | Accumulated |
| ACC_CONSUMPTION_AFTER_START | 559980952 | FLOAT | Wh | Accumulated |
| TOTAL_REGEN_ENRG_AFTER_CHARGE | 559980962 | FLOAT | Wh | Regen since charge |
| TOTAL_REGEN_ENRG_AFTER_START | 559980961 | FLOAT | Wh | Regen since ignition |
| TOTAL_REGEN_RNG_AFTER_CHARGE | 559980964 | FLOAT | km | Regen range |
| TOTAL_REGEN_RNG_AFTER_START | 559980963 | FLOAT | km | Regen range |
| INCREASED_VEHICLE_ELECTRIC_RANGE | 559980955 | FLOAT | km | Range increase |

### Driving
| Property | ID | Type | Unit/Scale | Notes |
|---|---|---|---|---|
| PERF_VEHICLE_SPEED | 291504647 | FLOAT | km/h | Speed |
| PERF_ODOMETER | 291504644 | FLOAT | km | Odometer |
| ENGINE_RPM | 291504901 | FLOAT | RPM | Motor RPM |
| CURRENT_GEAR | 289408001 | INT | 1=D,2=N,3=R,4=P | Gear |
| GEAR_SELECTION | 289408000 | INT | | Gear selector |
| SENSOR_TOTAL_MILEAGE | 557847910 | INT | km | Odometer |
| SENSOR_ELECTRIC_DRIVER_MODE | 557847914 | INT | | Drive mode |
| SENSOR_GEAR_STS | 557847918 | INT | | Gear sensor |
| SENSOR_RPM_STS | 557847917 | INT | | RPM sensor |
| SENSOR_WHEEL_ANGLE | 557847903 | INT | degrees | Steering angle |
| SENSOR_WHEEL_FEEL | 557847903 | INT | | Steering weight |
| SENSOR_DRIVE_EFFICIENCY_INDICATION | 557847964 | INT | | Efficiency score |
| SENSOR_BRAKE_PEDAL_DRIVER_APPLIED_PRESSURE | 559945059 | FLOAT | | Brake pressure |
| SENSOR_VEHICLE_LATERAL_ACCELERATION | 559945125 | FLOAT | | Lateral G |
| SENSOR_ACCELERATION_PORTRAIT | 559945057 | FLOAT | | Longitudinal G |
| REGENERATIVE_LEVEL | 557883793 | INT | | Regen level |
| REGENERATIVE_BRAKING_FUNCTION | 557883791 | INT | | Regen on/off |
| SIGNAL_PEDAL_ON | 557883795 | INT | | One pedal |
| BRAKE_PEDAL_MODE | 557883790 | INT | | Brake mode |
| AUTO_HOLD_SWITCH | 557883808 | INT | | Auto hold |
| LONGER_ENDURANCE_MODE | 557883797 | INT | | Range mode |

### Tires (raw values in kPa, ÷100 for bar)
| Property | ID | Type | Notes |
|---|---|---|---|
| SENSOR_TIRE_PRESURE_FL | 557847891 | INT | Front-left |
| SENSOR_TIRE_PRESURE_FR | 557847892 | INT | Front-right |
| SENSOR_TIRE_PRESURE_RL | 557847893 | INT | Rear-left |
| SENSOR_TIRE_PRESURE_RR | 557847894 | INT | Rear-right |
| SENSOR_TIRE_TEMP_FL | 557847899 | INT | °C |
| SENSOR_TIRE_TEMP_FR | 557847900 | INT | °C |
| SENSOR_TIRE_TEMP_RL | 557847901 | INT | °C |
| SENSOR_TIRE_TEMP_RR | 557847902 | INT | °C |
| SENSOR_TIRE_IN_FL_STATE | 557847887 | INT | Sensor state |
| SENSOR_TIRE_IN_FR_STATE | 557847888 | INT | Sensor state |
| SENSOR_TIRE_IN_RL_STATE | 557847889 | INT | Sensor state |
| SENSOR_TIRE_IN_RR_STATE | 557847890 | INT | Sensor state |

### Vehicle Identity (STRING type — 0x1)
| Property | ID | Type | Notes |
|---|---|---|---|
| INFO_VIN | 286261504 | STRING | VIN |
| INFO_MAKE | 286261505 | STRING | Manufacturer |
| INFO_MODEL | 286261506 | STRING | Model name |
| INFO_MODEL_YEAR | 289407235 | INT | Year |
| CAL_Vehicle_VIN | 554713683 | STRING | Calibration VIN |
| CAL_ECU_S_N | 554713682 | STRING | ECU serial number |
| CONFIG_VEHICLEID | 554713704 | STRING | Vehicle ID |
| CONFIG_YEAR | 554713705 | INT | Config year |
| CAL_Vehicle_BT_MAC | 554713686 | STRING | BT MAC address |
| CAL_Vehicle_WiFi_MAC | 554713687 | STRING | WiFi MAC address |
| CAL_Vehicle_HWVer | 554713685 | STRING | Hardware version |
| CAL_Vehicle_BT_Lisence | 554713684 | STRING | BT license |
| CAL_HW_VARIANT | 561005164 | STRING | HW variant |
| CAL_APP_SW_NUMBER | 561005165 | STRING | App SW number |
| CAL_ECU_PART_NUMBER | 561005166 | STRING | ECU part number |
| CAL_ECU_H_N | 561005167 | STRING | ECU HW number |

### Safety / ADAS Status
| Property | ID | Type | Notes |
|---|---|---|---|
| ABS_ACTIVE | 287310858 | BOOL | ABS active |
| TRACTION_CONTROL_ACTIVE | 287310859 | BOOL | TCS active |
| VEHICLE_ESP | 289421317 | INT | ESP on/off |
| PARKING_BRAKE_ON | 287310850 | BOOL | Parking brake |
| CRASH_SIGNAL | 289421316 | INT | Crash detected |
| ECALL_STATE | 289421315 | INT | eCall state |
| IGNITION_STATE | 289408009 | INT | Ignition |
| SEAT_BELT_BUCKLED | 356518144 | INT | Seatbelt |
| SDM_FRT_PSNG_AIRBAG_ENABLE | 557883752 | INT | Airbag enabled |
| DMS_SYSTEM_STATUS | 557883651 | INT | Driver monitoring |

### Lights
| Property | ID | Type | Notes |
|---|---|---|---|
| HEADLIGHTS_STATE | 289410560 | INT | Headlights |
| HIGH_BEAM_LIGHTS_STATE | 289410561 | INT | High beam |
| FOG_LIGHTS_STATE | 289410562 | INT | Fog lights |
| TURN_SIGNAL_STATE | 289408008 | INT | Turn signal |
| HAZARD_LIGHTS_STATE | 289410563 | INT | Hazards |
| NIGHT_MODE | 289408012 | INT | Night mode |
| DISPLAY_BRIGHTNESS | 289409539 | INT | Screen brightness |

### Parking Sensors (8-zone PDC)
| Property | ID | Type | Notes |
|---|---|---|---|
| SENSOR_PDC_OBSTACLE_FL | 557847919 | INT | Front-left |
| SENSOR_PDC_OBSTACLE_FLM | 557847920 | INT | Front-left-mid |
| SENSOR_PDC_OBSTACLE_FRM | 557847921 | INT | Front-right-mid |
| SENSOR_PDC_OBSTACLE_FR | 557847922 | INT | Front-right |
| SENSOR_PDC_OBSTACLE_RL | 557847923 | INT | Rear-left |
| SENSOR_PDC_OBSTACLE_RLM | 557847924 | INT | Rear-left-mid |
| SENSOR_PDC_OBSTACLE_RRM | 557847925 | INT | Rear-right-mid |
| SENSOR_PDC_OBSTACLE_RR | 557847926 | INT | Rear-right |
| SENSOR_PDC_OBSTACTE_DISTANCE_F | 557847927 | INT | Front min dist |
| SENSOR_PDC_OBSTACTE_DISTANCE_R | 557847928 | INT | Rear min dist |

### Doors & Windows
| Property | ID | Type | Notes |
|---|---|---|---|
| DOOR_LOCK | 289421312 | INT | Lock state |
| DOOR_POS | 289421315 | INT | Open/closed |
| DLOCK_DOOR_OPEN_STS | 557893120 | INT | Door open status |
| WINDOW_POS | 322964416 | INT | Window position |
| WINDOW_LOCK | 320867268 | INT | Child lock |
| VEHICLE_DRIVERWINDOW | 291518465 | INT | Driver window |
| VEHICLE_PSGWINDOW | 291518466 | INT | Passenger window |
| VEHICLE_RLWINDOW | 291518467 | INT | Rear left |
| VEHICLE_RRWINDOW | 291518468 | INT | Rear right |

---

## EngineerModeService Sub-services (Layer 3)

Requires binding to: `com.saicmotor.service.engmode.EngineeringModeService`

| Sub-service | Interface | Key Methods |
|---|---|---|
| DID | IDIDManager | getAssemblyPartNum, getHardwarePartNum, getProductSerialNum, getSoftwarePartNum |
| Log | ILogManager | exportDTCInfo, exportFICMLog, getErrorCode, getVehicleConfigure, getSupplierConfigure |
| SystemHardware | ISystemHardwareManager | getBluetoothInfoBean, getGNSSInfoBean, getMobileNetworkInfoBean, getWifiInfoBean, getVehicleConfig |
| SystemSettings | ISystemSettingsManager | getWholeVersions, getCarSpeed, getBatteryPower, rebootSystem |
| AVM | IAVMManager | getAvmStatus |
| Tuner | ITunerManager | Radio register read/write |

---

## Adapter Service (IGeneralService)

Available via VehicleSettingsService or ServiceManager.

| Method | Description |
|---|---|
| getCarMode | Car operation mode |
| getDisplayMode | Day/night display |
| getDistanceUnit | km/miles |
| getDrivingPosition | Left/right hand drive |
| getNavCountryCode | Navigation country |
| getSubjectID | Vehicle subject ID |
| getNetworkIsAvailable | Network connectivity |
| getMapAppPkgName | Map app package |
| getMapAppVersion | Map app version |

---

## Value Scaling Notes

| Raw Value | Conversion | Example |
|---|---|---|
| Tire pressure | ÷100 = bar | 264 → 2.64 bar |
| Consumption (ELEC_CSUMP_PERKM) | ÷10 = kWh/100km | 82.3 → 8.23 |
| Avg consumption (CRNT_AVG_ELEC_CSUMP) | ÷10 = kWh/100km | 82.3 → 8.23 |
| Charge time (CHRGNG_RMNNG_TIME) | 1023 = N/A | |
| Target SOC (CHRG_TRGT_SOC) | BMS raw scale | 7 ≈ 100% display |
| Gear (CURRENT_GEAR) | 1=D, 2=N, 3=R, 4=P | |
| SOC: BMS_PACK_SOC vs DSP | Raw vs display | 65.8 vs 73% |

---

## Layer 3: EngineerModeService

**Bind:** `com.saicmotor.service.engmode.EngineeringModeService`
**Hub:** `IEngineeringMode.getService("system_setting")` → `ISystemSettingsManager`

### ISystemSettingsManager
| Method | Returns | Description |
|---|---|---|
| getBatteryPower | float | **12V auxiliary battery voltage (V)** |
| getCarSpeed | float | Vehicle speed (independent source) |
| getChargeStatus | int | Charging status |
| getGearStatus | int | Gear position |
| getParkingBrakeStatus | int | Parking brake state |
| getPowerRunType | int | Power run type |
| getPowerSystemStatus | int | Power system status |
| getShowRoom | int | Showroom/demo mode |
| getWholeVersions | WholeVersionBean | All firmware versions (see below) |
| getADBDebugStatus | boolean | ADB debugging enabled |
| getAutoSwitchStatus | boolean | Auto switch status |
| getEngModeStackStatus | boolean | Engineering mode active |
| getTempRunTime | int | Temp run time |
| getUpgradeEnvironmentStatus | boolean | Upgrade environment ready |

### WholeVersionBean fields
| Field | Type | Description |
|---|---|---|
| mAppVersion | String | Application version |
| mBluetoothFirmware | String | Bluetooth firmware |
| mDspVersion | String | DSP version |
| mGPSFirmware | String | GPS firmware |
| mMCUVersion | String | MCU firmware |
| mMPUMajorVersion | String | MPU major version |
| mMPUVersion | String | MPU version |
| mNavigationEngineVersion | String | Navigation engine |
| mNavigationMapVersion | String | Navigation map data |
| mTBoxBTVersion | String | TBox Bluetooth |
| mTBoxMCUVersion | String | TBox MCU |
| mTBoxMPUVersion | String | TBox MPU |
| mTBoxModemVersion | String | TBox modem/4G |
| mVRVersion | String | Voice recognition |
| mVinVersion | String | VIN-linked version |
| mWifiFirmware | String | WiFi firmware |

---

## Layer 4: SaicAdapterService

**Package:** `com.saicmotor.adapterservice`
**Binding:** Each sub-service is a separate Android Service, bind by component name.

### IGeneralService (component: `services.GeneralService`)
| Method | Returns | Description |
|---|---|---|
| geCurTimeZone | String | Current timezone |
| getCarMode | String | Car operating mode |
| getDisplayMode | int | Display mode |
| getDistanceUnit | int | Distance unit (km/miles) |
| getDrivingPosition | String | Left/right hand drive |
| getGuideInfos | List | Navigation guide info list |
| getGuideStatus | int | Navigation guide status |
| getIsMapHasProjection | boolean | Map has cluster projection |
| getIsSetHomeAddress | boolean | Home address configured |
| getIsSetOfficeAddress | boolean | Office address configured |
| getLocationProvider | String | GPS location provider |
| getMapAppPkgName | String | Map app package name |
| getMapAppVersion | String | Map app version |
| getMapResPkgVersion | String | Map resource version |
| getNavCountryCode | String | Navigation country code |
| getNetworkIsAvailable | boolean | Network connectivity |
| getRemainingDistance | int | Nav remaining distance (m) |
| getRemainingRedLightNumber | int | Traffic lights remaining |
| getRemainingTimes | int | Nav remaining time (s) |
| getRoadName | String | Current road name |
| getSpeedLimitValue | int | Speed limit (km/h) |
| getSubjectID | String | Vehicle subject ID |
| getVehicleLaneInfo | String | Lane guidance info |
| isMapNavigating | boolean | Navigation active |
| isNavAppHasActivated | boolean | Nav app activated |

### IMapService (component: `services.MapService`)
| Method | Returns | Description |
|---|---|---|
| getBatteryPercentage | int | Battery SOC for map |
| getCarMode | String | Car mode |
| getCarType | String | Car type (EV/Fuel/PHEV) |
| getChargingFinishTime | int | Charging finish time |
| getChargingStatus | boolean | Currently charging |
| getClusterIsReady | boolean | Instrument cluster ready |
| getCurTtsLang | String | TTS language code |
| getDayNightMode | int | Day/night display mode |
| getDrivingPosition | String | Driving side |
| getEnduranceMileage | int | Range for map routing |
| getEvPortType | String | EV connector type |
| getFuelType | String | Fuel type |
| getGdprMapStatus | int | GDPR map consent |
| getIsSupportGetDayNightMode | boolean | Day/night mode supported |
| getLaunchDisplayId | int | Display ID |
| getLocationProvider | String | Location provider |
| getLowBatteryStatus | int | Low battery level (0-4) |
| getLowFuelStatus | int | Low fuel level (0=OK,1=Low,2=Critical) |
| getNetworkIsAvailable | boolean | Network available |
| getSubjectID | String | Subject ID |
| getTotalMileage | int | Total odometer for map |
| getUnitTypeFromAvn | int | Unit type from AVN |

### IVoiceVuiService (component: `services.VoiceVuiService`)
| Method | Returns | Description |
|---|---|---|
| getCurLocationDesc | String | Current location description text |
| getNavAppStatus | int | Navigation app status |
| getNetworkIsAvailable | boolean | Network available |
| isMapMaxSize | boolean | Map at maximum zoom |
| isMapMinSize | boolean | Map at minimum zoom |
| isMapNavigating | boolean | Currently navigating |
| isMapOriginalZoom | boolean | Map at original zoom |
| isMapPlanningRoute | boolean | Route planning in progress |
| isMapReCenter | boolean | Map re-centered |
| isSetHomeAddress | boolean | Home address set |
| isSetOfficeAddress | boolean | Office address set |

---

## Layer 5: SystemSettingsService

**Package:** `com.saicmotor.service.systemsettings`
**Binding:** Each sub-service via intent action `com.saicmotor.service.systemsettings.I<Name>Service`

### IBtService
| Method | Returns | Description |
|---|---|---|
| getAutoPairMode | boolean | Auto-pair enabled |
| getBluetoothEnabled | boolean | Bluetooth on/off |
| getBondedDevices | List | Paired BT devices |
| getCarPlayConnected | boolean | Apple CarPlay connected |
| getConnectedDevice | BluetoothDevice | Currently connected device |
| getDiscoverable | boolean | BT discoverable |
| getFoundDevices | List | Discovered BT devices |
| getLocalDeviceName | String | BT device name |
| getScreenOperable | boolean | Screen operable (not locked) |

### IGeneralService (SystemSettings)
| Method | Returns | Description |
|---|---|---|
| get24Hour | boolean | 24-hour time format |
| getAutoTimeState | boolean | Auto time sync |
| getBrightness | int | Screen brightness level |
| getBrightnessAutoState | boolean | Auto brightness |
| getDayNightAutoMode | boolean | Auto day/night |
| getDimClockEnabled | boolean | Dim clock display |
| getIsNightMode | boolean | Night mode active |
| getLocaleList | Map | Available locales |
| getSummerTimeMode | boolean | Daylight saving time |

### IMyCarService
| Method | Returns | Description |
|---|---|---|
| getDeviceName | String | Head unit device name |
| getMcuVersion | String | MCU firmware version |
| getMpuVersion | String | MPU firmware version |
| getTboxVersion | String | TBox firmware version |
| getVehicleType | String | Vehicle type string |

### ISmartSoundService
| Method | Returns | Description |
|---|---|---|
| get3DEffectType | int | 3D sound effect type |
| getBoseSoundEffect | int | Bose sound effect preset |
| getEqualizerBand | int | Active EQ band |
| getLoudnessState | boolean | Loudness enhancement |
| getMaxVolume | int | Maximum volume level |
| getMinVolume | int | Minimum volume level |
| getMuteState | boolean | Muted |
| getNaviDuckState | boolean | Nav audio ducking |
| getRearQuietModeState | boolean | Rear quiet mode |
| getRingtoneState | boolean | Ringtone enabled |
| getSoundFieldBalance | int | L/R balance |
| getSoundFieldFader | int | Front/rear fader |
| getSpeedVolumeControlLevel | int | Speed-dependent volume |
| getSubwooferState | boolean | Subwoofer on/off |
| getSystemBeepState | boolean | System beeps |
| getVehicleInfoVolume | int | Vehicle info volume |
| getVoiceVolume | int | Voice volume |
| isVolumeMax | boolean | At max volume |
| isVolumeMin | boolean | At min volume |

### IHotspotService
| Method | Returns | Description |
|---|---|---|
| getApBand | int | AP band (2.4/5 GHz) |
| getHotspotList | List | Connected hotspot clients |
| getLocalDeviceName | String | Hotspot SSID |
| getPassword | String | Hotspot password |
| getTetheringEnabled | boolean | Tethering active |

### IGdprService
| Method | Returns | Description |
|---|---|---|
| getMapEnabled | boolean | Map data consent |
| getOnlineMusicEnabled | boolean | Online music consent |
| getPrivacyEnabled | boolean | Privacy mode |
| getVoiceEnabled | boolean | Voice data consent |

### IWiFiService
| Method | Returns | Description |
|---|---|---|
| getWifiEnabled | boolean | WiFi on/off |

### IDataUsageService
| Method | Returns | Description |
|---|---|---|
| getDataEnabled | int | Mobile data enabled |
| getRemoteControlEnabled | boolean | Remote control allowed |

---

## Layer 6: vehicleService_overseas

**Package:** `com.saicvehicleservice`
**Bind action:** `com.saic.vehicle.VehicleService`
**Interface:** `IVehicleAidlInterface`

| Method | Returns | Description |
|---|---|---|
| getAvnId | String | AVN (Audio-Video-Navigation) ID |
| getBaseUrl | String | Cloud server base URL |
| getToken | String | Authentication token |
| getUserName | String | Logged-in user name |
| getUserId | String | User ID |
| getTrueId | String | True identity ID |
| getPhotoPath | String | User photo path |
| getSecurityKey | String | Security key |
| isVehicleActivated | boolean | Vehicle activated with cloud |
| isVehicleActivating | boolean | Activation in progress |
| isNaviActivated | boolean | Navigation activated |
| isNaviActivating | boolean | Nav activation in progress |

Also has crypto methods (not exposed in diagnostics):
- `signMessage(String)`, `encryptMessage(String)`, `decryptMessage(String)`
- `aesEncryptString(String)`, `aesClipe(String)` (AES decrypt)
- `getAesKey()` — returns AES key (security risk, logged to logcat by OEM)

---

## Services Investigated but NOT Integrated

| Service | Package | Reason |
|---|---|---|
| CarOtherService_yfve | com.yfve.carotherservice | Internal daemon, no AIDL exposed |
| EOL | com.saicmotor.hmi.eol | Factory tool, onBind returns null |
| MapService_overseas | com.saicmotor.mapservice | Async-only callbacks, no sync getters |
| 360CameraService | com.saicmotor.service.aroundview | Camera control only |
| BTCallService | com.saicmotor.service.btcall | Call management only |
| RadioService | com.saicmotor.service.radio | Tuner control only |
| MediaService | com.saicmotor.service.media | USB media playback only |

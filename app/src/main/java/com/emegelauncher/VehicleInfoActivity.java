/*
 * Emegelauncher - Custom Launcher for MG Marvel R
 * Copyright (C) 2026 Emegelauncher Contributors
 *
 * Licensed under the Apache License, Version 2.0 with the
 * Commons Clause License Condition v1.0 (see LICENSE files).
 *
 * You may NOT sell this software. Donations are welcome.
 */

package com.emegelauncher;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.emegelauncher.vehicle.VehicleServiceManager;
import com.emegelauncher.vehicle.YFVehicleProperty;

/**
 * Read-only vehicle information display.
 * Shows identity, ECU status, ADAS config, maintenance, doors/windows, and more.
 */
public class VehicleInfoActivity extends Activity {
    private VehicleServiceManager mVehicle;
    private final Handler mHandler = new Handler(android.os.Looper.getMainLooper());
    private LinearLayout mContent;
    private int cBg, cCard, cText, cTextSec, cTextTert, cDivider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        resolveColors();

        mVehicle = VehicleServiceManager.getInstance(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);
        root.setPadding(20, 8, 20, 8);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(0, 4, 0, 8);
        TextView title = new TextView(this);
        title.setText("Vehicle Information");
        title.setTextSize(22);
        title.setTextColor(cText);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(title);
        TextView back = new TextView(this);
        back.setText(getString(R.string.back));
        back.setTextSize(13);
        back.setTextColor(ThemeHelper.accentBlue(this));
        back.setPadding(20, 12, 20, 12);
        back.setOnClickListener(v -> finish());
        header.addView(back);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        mContent = new LinearLayout(this);
        mContent.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(mContent);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        buildContent();
        startPolling();
    }

    private void resolveColors() {
        cBg = ThemeHelper.resolveColor(this, R.attr.colorBgPrimary);
        cCard = ThemeHelper.resolveColor(this, R.attr.colorBgCard);
        cText = ThemeHelper.resolveColor(this, R.attr.colorTextPrimary);
        cTextSec = ThemeHelper.resolveColor(this, R.attr.colorTextSecondary);
        cTextTert = ThemeHelper.resolveColor(this, R.attr.colorTextTertiary);
        cDivider = ThemeHelper.resolveColor(this, R.attr.colorDivider);
    }

    private void buildContent() {
        // Identity
        addSection("VEHICLE IDENTITY");
        addRow("vin", "VIN");
        addRow("make", "Make");
        addRow("model", "Model");
        addRow("year", "Model Year");
        addRow("config_vin", "Calibration VIN");
        addRow("ecu_sn", "ECU Serial Number");
        addRow("vehicle_id", "Vehicle ID");
        addRow("hw_ver", "Hardware Version");
        addRow("bt_mac", "Bluetooth MAC");
        addRow("wifi_mac", "WiFi MAC");
        addRow("hw_variant", "HW Variant");
        addRow("sw_number", "App SW Number");
        addRow("ecu_part", "ECU Part Number");
        addRow("ecu_hw", "ECU HW Number");
        addRow("vehicle_type", "Vehicle Type");
        addRow("ext_color", "Exterior Color Code");
        addRow("car_config", "Car Config Code (EP21)");
        addRow("config_year", "Config Year");

        // Status
        addSection("VEHICLE STATUS");
        addRow("ignition", "Ignition");
        addRow("gear", "Gear");
        addRow("speed", "Speed");
        addRow("odometer", "Odometer");
        addRow("door_lock", "Door Lock");
        addRow("parking_brake", "Parking Brake");
        addRow("drive_mode", "Drive Mode");
        addRow("regen_level", "Regen Level");
        addRow("one_pedal", "One Pedal Mode");
        addRow("auto_hold", "Auto Hold");
        addRow("esp", "ESP");
        addRow("hdc", "HDC");
        addRow("steering_feel", "Steering Feel");
        addRow("brake_mode", "Brake Pedal Mode");

        // Battery
        addSection("BATTERY & CHARGING");
        addRow("soc_display", "SOC (Display)");
        addRow("soc_bms", "SOC (BMS Raw)");
        addRow("range_display", "Range (Display)");
        addRow("range_bms", "Range (BMS)");
        addRow("pack_volt", "Pack Voltage (V)");
        addRow("pack_crnt", "Pack Current (A)");
        addRow("charge_status", "Charge Status");
        addRow("charge_limit", "Charge Limit SOC");
        addRow("battery_heater", "Battery Heater");
        addRow("endurance_mode", "Extended Range Mode");
        addRow("bms_warning", "BMS Warning");
        // 12V battery not accessible from head unit (TBox only)

        // HVAC
        addSection("CLIMATE");
        addRow("hvac_power", "HVAC Power");
        addRow("ac_switch", "AC Compressor");
        addRow("auto_mode", "Auto Mode");
        addRow("econ_mode", "Econ Mode");
        addRow("drv_temp", "Driver HVAC Set Temp");
        addRow("psg_temp", "Passenger HVAC Set Temp");
        addRow("outside_temp", "Outside Temp");
        addRow("fan_speed", "Fan Speed");
        addRow("loop_mode", "Recirc Mode");
        addRow("blower_dir", "Blower Direction");
        addRow("front_defrost", "Front Defroster");
        addRow("rear_defrost", "Rear Defroster");
        addRow("drv_seat_heat", "Driver Seat Heat");
        addRow("psg_seat_heat", "Passenger Seat Heat");
        addRow("drv_seat_vent", "Driver Seat Ventilation");
        addRow("psg_seat_vent", "Passenger Seat Ventilation");
        addRow("pm25_inside", "PM2.5 Inside (µg/m³)");
        addRow("ionizer", "Ionizer");
        addRow("dual_zone", "Dual Zone");

        // ADAS
        addSection("ADAS CONFIGURATION");
        addRow("aeb", "AEB");
        addRow("fcw_mode", "FCW Alarm Mode");
        addRow("fcw_sens", "FCW Sensitivity");
        addRow("bsd", "Blind Spot Detection");
        addRow("lka_mode", "Lane Keeping Mode");
        addRow("lca", "Lane Change Assist");
        addRow("tja", "Traffic Jam Assist");
        addRow("rcta", "Rear Cross Traffic Alert");
        addRow("rda", "Rear Drive Assist");
        addRow("drowsiness", "Drowsiness Monitor");
        addRow("speed_assist", "Speed Assist Mode");
        addRow("auto_beam", "Auto High Beam");

        addRow("apa_sts", "APA Status");
        addRow("apa_mode", "APA Parking Mode");
        addRow("apa_avlbly", "APA Available (VHAL)");
        addRow("cal_apa", "CAL Auto Parking");
        addRow("cal_avp", "CAL Auto Valet Parking");
        addRow("drive_mode", "Drive Mode");
        addRow("drv_mode_switch", "Drive Mode Switch");

        // Comfort
        addSection("COMFORT & CONVENIENCE");
        addRow("ambient_on", "Ambient Light");
        addRow("ambient_brightness", "Ambient Brightness");
        addRow("ambient_color", "Ambient Color");
        addRow("welcome_sound", "Welcome Sound");
        addRow("welcome_light", "Welcome Light Time");
        addRow("home_light", "Home Light Time");
        addRow("auto_lock", "Auto Lock (Driving)");
        addRow("auto_unlock", "Auto Unlock (Park)");
        addRow("key_unlock", "Key Unlock Mode");
        addRow("inductive_handle", "Touch Door Handle");
        addRow("inductive_tailgate", "Hands-Free Tailgate");
        addRow("mirror_fold", "Mirror Auto Fold");
        addRow("psg_airbag", "Passenger Airbag");
        addRow("nearfield_unlock", "Nearfield Unlock Mode");
        addRow("bt_key_learn", "BT Key Learn Status");

        // Doors & Windows
        addSection("DOORS & WINDOWS");
        addRow("door_status", "Door Open Status");
        addRow("drv_window", "Driver Window");
        addRow("psg_window", "Passenger Window");
        addRow("rl_window", "Rear Left Window");
        addRow("rr_window", "Rear Right Window");
        addRow("sunroof", "Sunroof");
        addRow("sunroof_vent", "Sunroof Vent");
        addRow("tailgate_enable", "Tailgate Enable");
        addRow("tailgate_open", "Tailgate Open");
        addRow("tailgate_pos", "Tailgate Position");

        // ECU Availability
        addSection("ECU ONLINE STATUS");
        addRow("ecu_ac", "AC ECU");
        addRow("ecu_bcm", "BCM");
        addRow("ecu_bms", "BMS");
        addRow("ecu_hcu", "HCU");
        addRow("ecu_peps", "PEPS");
        addRow("ecu_radar", "Radar");
        addRow("ecu_apa", "APA");
        addRow("ecu_fvcm", "Forward Camera");
        addRow("ecu_plcm", "Parking Camera");
        addRow("ecu_360", "360 Camera Config");

        // Maintenance
        addSection("MAINTENANCE");
        addRow("maintenance", "Maintenance Status");
        addRow("service_days", "Days to Service");
        addRow("service_km", "km to Service");
        addRow("ecall", "eCall State");

        // Lights (SAIC-specific VHAL properties — Android standard ones return 0)
        addSection("LIGHTS");
        addRow("low_beam", "Low Beam (Dipped)");
        addRow("high_beam", "High Beam (Main)");
        addRow("front_fog", "Front Fog Light");
        addRow("rear_fog", "Rear Fog Light");
        addRow("left_indicator", "Left Indicator");
        addRow("right_indicator", "Right Indicator");
        addRow("side_lights", "Side Lights / DRL");
        addRow("parking_lights", "Parking Lights");
        addRow("auto_high_beam", "Auto High Beam");

        // Display & System (Layer 5 SystemSettingsService + VHAL)
        addSection("DISPLAY & SYSTEM");
        addRow("sys_night_mode", "Night Mode");
        addRow("sys_auto_daynight", "Auto Day/Night");
        addRow("sys_brightness", "Screen Brightness");
        addRow("sys_24hour", "24h Time Format");
        addRow("sys_bt_enabled", "Bluetooth");
        addRow("sys_bt_name", "BT Device Name");
        addRow("sys_carplay", "CarPlay Connected");
        addRow("sys_wifi", "WiFi");
        addRow("sys_mcu_ver", "MCU Version");
        addRow("sys_mpu_ver", "MPU Version");
        addRow("sys_tbox_ver", "TBox Version");
        addRow("sys_device_name", "Device Name");
        addRow("sys_vehicle_type", "Vehicle Type");

        // Additional sensors
        addSection("SENSORS");
        addRow("g_force", "G-Force (longitudinal)");
        addRow("seatbelt_psg", "Passenger Seatbelt");
        addRow("tailgate_pos_vhal", "Tailgate Position");
        addRow("dtc_indicator", "DTC (Diagnostic Codes)");

        addSection("CLOUD (iSMART)");
        addRow("cloud_cabin_temp", "Cabin Temperature");
        addRow("cloud_12v", "12V Battery");
        addRow("cloud_trip_today", "Mileage Today");
        addRow("cloud_trip_charge", "Mileage Since Charge");
        addRow("cloud_journey", "Current Journey");
        addRow("cloud_age", "Cloud Data Age");
    }

    private void update() {
        // Identity — STRING properties + SAIC condition service
        updateTag("vin", readStr(YFVehicleProperty.INFO_VIN, "getVinNumber"));
        updateTag("make", readProp(YFVehicleProperty.INFO_MAKE));
        updateTag("model", readProp(YFVehicleProperty.INFO_MODEL));
        updateTag("year", readProp(YFVehicleProperty.INFO_MODEL_YEAR));
        updateTag("config_vin", readProp(YFVehicleProperty.CAL_Vehicle_VIN));
        updateTag("ecu_sn", readProp(YFVehicleProperty.CAL_ECU_S_N));
        updateTag("vehicle_id", readProp(YFVehicleProperty.CONFIG_VEHICLEID));
        updateTag("hw_ver", readProp(YFVehicleProperty.CAL_Vehicle_HWVer));
        updateTag("bt_mac", readProp(YFVehicleProperty.CAL_Vehicle_BT_MAC));
        updateTag("wifi_mac", readProp(YFVehicleProperty.CAL_Vehicle_WiFi_MAC));
        updateTag("hw_variant", readProp(YFVehicleProperty.CAL_HW_VARIANT));
        updateTag("sw_number", readProp(YFVehicleProperty.CAL_APP_SW_NUMBER));
        updateTag("ecu_part", readProp(YFVehicleProperty.CAL_ECU_PART_NUMBER));
        updateTag("ecu_hw", readProp(YFVehicleProperty.CAL_ECU_H_N));
        updateTag("vehicle_type", saic("condition", "getVehicleType"));
        updateTag("ext_color", saic("condition", "getVehicleExteriorColor"));
        updateTag("car_config", saic("condition", "getEp21CarConfigCode"));
        updateTag("config_year", readProp(YFVehicleProperty.CONFIG_YEAR));

        // Status
        updateTag("ignition", saic("condition", "getVehicleIgnition"));
        updateTag("gear", decodeGear(saicInt("condition", "getCarGear")));
        updateTag("speed", saic("condition", "getCarSpeed") + " km/h");
        updateTag("odometer", readProp(YFVehicleProperty.SENSOR_TOTAL_MILEAGE) + " km");
        updateTag("door_lock", saic("control", "getDoorLock"));
        updateTag("parking_brake", readProp(YFVehicleProperty.PARKING_BRAKE_ON));
        updateTag("regen_level", readProp(YFVehicleProperty.AAD_EPTRGTNLVL));
        updateTag("one_pedal", readProp(YFVehicleProperty.SIGNAL_PEDAL_ON));
        updateTag("auto_hold", readProp(YFVehicleProperty.AUTO_HOLD_SWITCH));
        updateTag("esp", saic("control", "getEspSwitch"));
        updateTag("hdc", saic("control", "getHdcSwitch"));
        updateTag("steering_feel", readProp(YFVehicleProperty.SENSOR_WHEEL_FEEL));
        updateTag("brake_mode", readProp(YFVehicleProperty.BRAKE_PEDAL_MODE));

        // Battery
        updateTag("soc_display", saic("charging", "getCurrentElectricQuantity") + "%");
        updateTag("soc_bms", readProp(YFVehicleProperty.BMS_PACK_SOC) + "%");
        updateTag("range_display", saic("charging", "getCurrentEnduranceMileage") + " km");
        updateTag("range_bms", readProp(YFVehicleProperty.BMS_ESTD_ELEC_RNG) + " km");
        updateTag("pack_volt", readProp(YFVehicleProperty.BMS_PACK_VOL));
        updateTag("pack_crnt", readProp(YFVehicleProperty.BMS_PACK_CRNT));
        updateTag("charge_status", saic("charging", "getChargingStatus"));
        updateTag("charge_limit", saic("charging", "getChargingCloseSoc"));
        updateTag("battery_heater", saic("charging", "getDrivingBatteryHeat"));
        updateTag("endurance_mode", readProp(YFVehicleProperty.LONGER_ENDURANCE_MODE));
        updateTag("bms_warning", readProp(YFVehicleProperty.BMS_WRNNG_INFO));

        // HVAC
        updateTag("hvac_power", saic("aircondition", "getHvacPowerStatus"));
        updateTag("ac_switch", saic("aircondition", "getAcSwitch"));
        updateTag("auto_mode", saic("aircondition", "getAutoStatus"));
        updateTag("econ_mode", saic("aircondition", "getEconStatus"));
        updateTag("drv_temp", hvacVal(saic("aircondition", "getDrvTemp"), "°C"));
        updateTag("psg_temp", hvacVal(saic("aircondition", "getPsgTemp"), "°C"));
        updateTag("outside_temp", saic("aircondition", "getOutCarTemp") + "°C");
        updateTag("fan_speed", hvacVal(saic("aircondition", "getAirVolumeLevel"), ""));
        updateTag("loop_mode", saic("aircondition", "getLoopMode"));
        updateTag("blower_dir", hvacVal(saic("aircondition", "getBlowerDirectionMode"), ""));
        updateTag("front_defrost", saic("aircondition", "getFrontWindowDefroster"));
        updateTag("rear_defrost", saic("aircondition", "getBackWindowDefroster"));
        updateTag("drv_seat_heat", saic("aircondition", "getDrvSeatHeatLevel"));
        updateTag("psg_seat_heat", saic("aircondition", "getPsgSeatHeatLevel"));
        updateTag("drv_seat_vent", saic("aircondition", "getDrvSeatWindLevel"));
        updateTag("psg_seat_vent", saic("aircondition", "getPsgSeatWindLevel"));
        String pm25val = saic("aircondition", "getPm25Concentration"); try { if (Float.parseFloat(pm25val) >= 250) pm25val = "No sensor (raw: " + pm25val + ")"; } catch (Exception ignored) {} updateTag("pm25_inside", pm25val);
        updateTag("ionizer", saic("aircondition", "getAnionStatus"));
        updateTag("dual_zone", saic("aircondition", "getTempDualZoneOn"));

        // ADAS
        updateTag("aeb", saic("setting", "getAutoEmergencyBraking"));
        updateTag("fcw_mode", saic("setting", "getFcwAlarmMode"));
        updateTag("fcw_sens", saic("setting", "getFcwSensitivity"));
        updateTag("bsd", saic("setting", "getBlindSpotDetection"));
        updateTag("lka_mode", saic("setting", "getLaneKeepingAsstMode"));
        updateTag("lca", saic("setting", "getLaneChangeAsst"));
        updateTag("tja", saic("setting", "getTrafficJamAsstOn"));
        updateTag("rcta", saic("setting", "getRearTrafficWarning"));
        updateTag("rda", saic("setting", "getRearDriveAsstSys"));
        updateTag("drowsiness", saic("setting", "getDrowsinessMonitorSysOn"));
        updateTag("speed_assist", saic("setting", "getSpeedAsstMode"));
        updateTag("auto_beam", saic("setting", "getAutoMainBeamControl"));
        updateTag("apa_sts", readProp(YFVehicleProperty.APA_STS));
        updateTag("apa_mode", readProp(YFVehicleProperty.APA_AUTO_PARKING_MODE));
        updateTag("apa_avlbly", readProp(YFVehicleProperty.SENSOR_APAAVLBLY));
        updateTag("cal_apa", readProp(YFVehicleProperty.CAL_AUTOMATED_PARKING_SYSTEM));
        updateTag("cal_avp", readProp(YFVehicleProperty.CAL_AUTOMATED_VALET_PARKING));
        // Drive mode decode: 0=Eco, 1=Normal, 2=Sport, 6=Winter
        String drvModeRaw = readProp(YFVehicleProperty.SENSOR_ELECTRIC_DRIVER_MODE);
        String drvModeLabel = drvModeRaw;
        try {
            int dm = Integer.parseInt(drvModeRaw);
            switch (dm) { case 0: drvModeLabel = "Eco (0)"; break; case 1: drvModeLabel = "Normal (1)"; break; case 2: drvModeLabel = "Sport (2)"; break; case 6: drvModeLabel = "Winter (6)"; break; default: drvModeLabel = drvModeRaw + " (unknown)"; }
        } catch (Exception ignored) {}
        updateTag("drive_mode", drvModeLabel);
        updateTag("drv_mode_switch", readProp(YFVehicleProperty.AAD_DRV_MODE_SWITCH));

        // Comfort
        updateTag("ambient_on", saic("setting", "getAmbtLightGlbOn"));
        updateTag("ambient_brightness", saic("setting", "getAmbtLightBrightness"));
        updateTag("ambient_color", saic("setting", "getAmbtLightColor"));
        updateTag("welcome_sound", saic("setting", "getWelcomeSoundOn"));
        updateTag("welcome_light", saic("setting", "getWelcomeLightTime") + "s");
        updateTag("home_light", saic("setting", "getHomeLightTime") + "s");
        updateTag("auto_lock", saic("setting", "getDrivingAutoLock"));
        updateTag("auto_unlock", saic("setting", "getStallingAutoUnlock"));
        updateTag("key_unlock", saic("setting", "getKeyUnlockMode"));
        updateTag("inductive_handle", saic("setting", "getInductiveDoorHandle"));
        updateTag("inductive_tailgate", saic("setting", "getInductiveTailgate"));
        updateTag("mirror_fold", saic("setting", "getOuterRearviewFold"));
        updateTag("psg_airbag", saic("setting", "getPsgSafetyAirbagOn"));
        updateTag("nearfield_unlock", saic("setting", "getNearfieldUnlockMode"));
        updateTag("bt_key_learn", saic("setting", "getBtKeyLearnStatus"));

        // Doors & Windows
        updateTag("door_status", readProp(YFVehicleProperty.DLOCK_DOOR_OPEN_STS));
        updateTag("drv_window", saic("control", "getDriveWindow"));
        String psgWin = readProp(YFVehicleProperty.VEHICLE_PSGWINDOW); try { float pv = Float.parseFloat(psgWin); psgWin = String.format("%.0f", pv / 2.0f); } catch (Exception ignored) {} updateTag("psg_window", psgWin);
        updateTag("rl_window", saic("control", "getLeftRearWindow"));
        updateTag("rr_window", saic("control", "getRightRearWindow"));
        updateTag("sunroof", saic("control", "getSunroofSwitch"));
        updateTag("sunroof_vent", saic("control", "getSunroofVentilation"));
        updateTag("tailgate_enable", saic("control", "getElectricTailgateEnable"));
        updateTag("tailgate_open", saic("control", "getElectricTailgateOpenStatus"));
        updateTag("tailgate_pos", saic("setting", "getElectricTailgatePos"));

        // ECU
        updateTag("ecu_ac", saic("condition", "getAcAvlbly"));
        updateTag("ecu_bcm", saic("condition", "getBcmAvlbly"));
        updateTag("ecu_bms", saic("condition", "getBmsAvlbly"));
        updateTag("ecu_hcu", saic("condition", "getHcuAvlbly"));
        updateTag("ecu_peps", saic("condition", "getPepsAvlbly"));
        updateTag("ecu_radar", saic("condition", "getRadarAvlbly"));
        updateTag("ecu_apa", saic("condition", "getApaAvlbly"));
        updateTag("ecu_fvcm", saic("condition", "getFvcmAvlbly"));
        updateTag("ecu_plcm", saic("condition", "getPlcmAvlbly"));
        updateTag("ecu_360", saic("condition", "getConfig360"));

        // Maintenance
        updateTag("maintenance", saic("condition", "getMaintenanceStatus"));
        updateTag("service_days", saic("condition", "getNextResetDay") + " days");
        updateTag("service_km", saic("condition", "getNextResetMileage") + " km");
        updateTag("ecall", saic("condition", "getEcallState"));

        // Lights (SAIC-specific VHAL properties)
        updateTag("low_beam", decodeBool(readProp(YFVehicleProperty.DIPD_BEAM_LGHT)));
        updateTag("high_beam", decodeBool(readProp(YFVehicleProperty.MAIN_BEAM_LGHT)));
        updateTag("front_fog", decodeBool(readProp(YFVehicleProperty.FRT_FOG_LGHT)));
        updateTag("rear_fog", decodeBool(readProp(YFVehicleProperty.RR_FOG_LGHT)));
        updateTag("left_indicator", decodeBool(readProp(YFVehicleProperty.LDIRCN_IO)));
        updateTag("right_indicator", decodeBool(readProp(YFVehicleProperty.RDIRCN_IO)));
        updateTag("side_lights", readProp(YFVehicleProperty.VEH_SIDE_LGHT));
        updateTag("parking_lights", readProp(YFVehicleProperty.PARK_LAMP_OPT));
        updateTag("auto_high_beam", readProp(YFVehicleProperty.LAMP_AUTO_MAIN_BEAM));

        // Display & System (Layer 5 SystemSettingsService)
        updateTag("sys_night_mode", saic("sysgeneral", "getIsNightMode"));
        updateTag("sys_auto_daynight", saic("sysgeneral", "getDayNightAutoMode"));
        updateTag("sys_brightness", saic("sysgeneral", "getBrightness"));
        updateTag("sys_24hour", saic("sysgeneral", "get24Hour"));
        updateTag("sys_bt_enabled", saic("sysbt", "getBluetoothEnabled"));
        updateTag("sys_bt_name", saic("sysbt", "getLocalDeviceName"));
        updateTag("sys_carplay", saic("sysbt", "getCarPlayConnected"));
        updateTag("sys_wifi", saic("syswifi", "getWifiEnabled"));
        updateTag("sys_mcu_ver", saic("sysmycar", "getMcuVersion"));
        updateTag("sys_mpu_ver", saic("sysmycar", "getMpuVersion"));
        updateTag("sys_tbox_ver", saic("sysmycar", "getTboxVersion"));
        updateTag("sys_device_name", saic("sysmycar", "getDeviceName"));
        updateTag("sys_vehicle_type", saic("sysmycar", "getVehicleType"));

        // Sensors
        updateTag("g_force", readProp(YFVehicleProperty.SENSOR_ACCELERATION_PORTRAIT) + " G");
        updateTag("seatbelt_psg", readProp(YFVehicleProperty.SENSOR_SEAT_BELT_PSNG_STATE).equals("1") ? "Unbuckled" : "Buckled");
        updateTag("tailgate_pos_vhal", readProp(YFVehicleProperty.DLOCK_LDSPC_OPEN_STS));
        String dtcRaw = readProp(YFVehicleProperty.DTC_PROPID_GETDTCLOG);
        updateTag("dtc_indicator", (dtcRaw != null && !dtcRaw.equals("N/A") && !dtcRaw.isEmpty()) ? "DTC data present: " + dtcRaw : "No DTCs");

        // Cloud data
        com.emegelauncher.vehicle.SaicCloudManager cloud = new com.emegelauncher.vehicle.SaicCloudManager(this);
        if (cloud.hasData()) {
            String cabin = cloud.getInteriorTempStr();
            String batt = cloud.getBatteryVoltageStr();
            updateTag("cloud_cabin_temp", cabin != null ? cabin + "°C" : "N/A");
            updateTag("cloud_12v", batt != null ? batt + "V" : "N/A");
            updateTag("cloud_trip_today", cloud.getMileageOfDay() >= 0 ? (cloud.getMileageOfDay() / 10.0) + " km" : "N/A");
            updateTag("cloud_trip_charge", cloud.getMileageSinceLastCharge() >= 0 ? (cloud.getMileageSinceLastCharge() / 10.0) + " km" : "N/A");
            updateTag("cloud_journey", cloud.getCurrentJourneyDistance() >= 0 ? (cloud.getCurrentJourneyDistance() / 10.0) + " km" : "N/A");
            long age = (System.currentTimeMillis() - cloud.getLastQueryTime()) / 1000;
            updateTag("cloud_age", age < 60 ? age + "s ago" : (age / 60) + "m ago");
        } else {
            updateTag("cloud_cabin_temp", cloud.isLoggedIn() ? "Pending..." : "Login in Settings");
            updateTag("cloud_12v", "");
        }
    }

    // ==================== Helpers ====================

    private String saic(String svc, String method) {
        return mVehicle.callSaicMethod(svc, method);
    }

    private int saicInt(String svc, String method) {
        try { return (int) Float.parseFloat(saic(svc, method)); } catch (Exception e) { return 0; }
    }

    private String readProp(int propId) {
        return mVehicle.getPropertyValue(propId);
    }

    private String readStr(int propId, String saicFallback) {
        String val = readProp(propId);
        if (val == null || val.equals("N/A") || val.equals("0") || val.equals("0.00"))
            val = saic("condition", saicFallback);
        return val;
    }

    private String decodeGear(int raw) {
        switch (raw) { case 1: return "P"; case 2: return "R"; case 3: return "N"; case 4: return "D"; default: return String.valueOf(raw); }
    }

    private String decodeBool(String val) {
        if ("true".equalsIgnoreCase(val) || "1".equals(val)) return "ON";
        if ("false".equalsIgnoreCase(val) || "0".equals(val)) return "OFF";
        return val;
    }

    /** Format HVAC value: -1 means off/not set */
    private String hvacVal(String val, String unit) {
        if (val == null || val.equals("N/A")) return "N/A";
        if (val.equals("-1") || val.equals("-1.0")) return "Off";
        return val + unit;
    }

    private void addSection(String title) {
        android.view.View div = new android.view.View(this);
        div.setBackgroundColor(cDivider);
        mContent.addView(div, new LinearLayout.LayoutParams(-1, 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 12, 0, 6);

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(12);
        tv.setTextColor(cTextTert);
        tv.setPadding(4, 0, 0, 0);
        mContent.addView(tv, lp);
    }

    private void addRow(String tag, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(cCard);
        row.setPadding(16, 10, 16, 10);

        TextView nameView = new TextView(this);
        nameView.setText(label);
        nameView.setTextSize(13);
        nameView.setTextColor(cText);
        row.addView(nameView, new LinearLayout.LayoutParams(-1, -2));

        TextView valView = new TextView(this);
        valView.setText("--");
        valView.setTextSize(13);
        valView.setTextColor(cTextSec);
        valView.setTag(tag);
        // Wrap text for long values (VIN, Vehicle ID, MACs, etc.)
        valView.setMaxLines(3);
        valView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(valView);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 1, 0, 1);
        mContent.addView(row, lp);
    }

    private void updateTag(String tag, String value) {
        TextView tv = mContent.findViewWithTag(tag);
        if (tv != null && value != null) tv.setText(value);
    }

    private void startPolling() {
        mHandler.post(new Runnable() {
            @Override public void run() {
                update();
                mHandler.postDelayed(this, 3000);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
    }
}

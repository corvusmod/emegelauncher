/*
 * Emegelauncher - Custom Launcher for MG Marvel R
 * Copyright (C) 2026 Emegelauncher Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v3.0 with the
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
        back.setText("BACK");
        back.setTextSize(13);
        back.setTextColor(0xFF0A84FF);
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
        addRow("12v_battery", "12V Battery Voltage");

        // HVAC
        addSection("CLIMATE");
        addRow("hvac_power", "HVAC Power");
        addRow("ac_switch", "AC Compressor");
        addRow("auto_mode", "Auto Mode");
        addRow("econ_mode", "Econ Mode");
        addRow("drv_temp", "Driver Temp");
        addRow("psg_temp", "Passenger Temp");
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

        // Lights
        addSection("LIGHTS");
        addRow("headlights", "Headlights");
        addRow("highbeam", "High Beam");
        addRow("foglight", "Fog Lights");
        addRow("turn", "Turn Signals");
        addRow("hazard", "Hazard Lights");
        addRow("night_mode", "Night Mode");
        addRow("brightness", "Display Brightness");

        // Misc
        addSection("SYSTEM");
        addRow("wireless_charger", "Wireless Charger");
        addRow("bluetooth", "Bluetooth Status");
        addRow("power_mode", "Power Mode");
        addRow("nav_speed_limit", "Nav Speed Limit");
        addRow("dms_status", "Driver Monitoring");
        addRow("seatbelt_drv", "Driver Seatbelt");
        addRow("seatbelt_psg", "Passenger Seatbelt");
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
        updateTag("drive_mode", readProp(YFVehicleProperty.SENSOR_ELECTRIC_DRIVER_MODE));
        updateTag("regen_level", readProp(YFVehicleProperty.REGENERATIVE_LEVEL));
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
        String v12 = mVehicle.get12VBatteryVoltage();
        updateTag("12v_battery", v12.equals("N/A") ? v12 : v12 + " V");

        // HVAC
        updateTag("hvac_power", saic("aircondition", "getHvacPowerStatus"));
        updateTag("ac_switch", saic("aircondition", "getAcSwitch"));
        updateTag("auto_mode", saic("aircondition", "getAutoStatus"));
        updateTag("econ_mode", saic("aircondition", "getEconStatus"));
        updateTag("drv_temp", saic("aircondition", "getDrvTemp"));
        updateTag("psg_temp", saic("aircondition", "getPsgTemp"));
        updateTag("outside_temp", saic("aircondition", "getOutCarTemp") + "°C");
        updateTag("fan_speed", saic("aircondition", "getAirVolumeLevel"));
        updateTag("loop_mode", saic("aircondition", "getLoopMode"));
        updateTag("blower_dir", saic("aircondition", "getBlowerDirectionMode"));
        updateTag("front_defrost", saic("aircondition", "getFrontWindowDefroster"));
        updateTag("rear_defrost", saic("aircondition", "getBackWindowDefroster"));
        updateTag("drv_seat_heat", saic("aircondition", "getDrvSeatHeatLevel"));
        updateTag("psg_seat_heat", saic("aircondition", "getPsgSeatHeatLevel"));
        updateTag("drv_seat_vent", saic("aircondition", "getDrvSeatWindLevel"));
        updateTag("psg_seat_vent", saic("aircondition", "getPsgSeatWindLevel"));
        updateTag("pm25_inside", saic("aircondition", "getPm25Concentration"));
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

        // Doors & Windows
        updateTag("door_status", readProp(YFVehicleProperty.DLOCK_DOOR_OPEN_STS));
        updateTag("drv_window", saic("control", "getDriveWindow"));
        updateTag("psg_window", saic("control", "getPassengerWindow"));
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

        // Lights
        updateTag("headlights", readProp(YFVehicleProperty.HEADLIGHTS_STATE));
        updateTag("highbeam", readProp(YFVehicleProperty.HIGH_BEAM_LIGHTS_STATE));
        updateTag("foglight", readProp(YFVehicleProperty.FOG_LIGHTS_STATE));
        updateTag("turn", readProp(YFVehicleProperty.TURN_SIGNAL_STATE));
        updateTag("hazard", readProp(YFVehicleProperty.HAZARD_LIGHTS_STATE));
        updateTag("night_mode", readProp(YFVehicleProperty.NIGHT_MODE));
        updateTag("brightness", readProp(YFVehicleProperty.DISPLAY_BRIGHTNESS));

        // System
        updateTag("wireless_charger", readProp(YFVehicleProperty.PHONE_WIRELESS_CHAEGER_WORKING_STATE));
        updateTag("bluetooth", readProp(YFVehicleProperty.BLUETOOTH_STATUS));
        updateTag("power_mode", readProp(YFVehicleProperty.PMS_PWR_MODE));
        updateTag("nav_speed_limit", readProp(YFVehicleProperty.NAVIGATION_SPEED_LIMIT_VALUE));
        updateTag("dms_status", readProp(YFVehicleProperty.DMS_SYSTEM_STATUS));
        updateTag("seatbelt_drv", readProp(YFVehicleProperty.SENSOR_SEAT_BELT_DRVR_STATE));
        updateTag("seatbelt_psg", readProp(YFVehicleProperty.SENSOR_SEAT_BELT_PSNG_STATE));
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
        switch (raw) { case 1: return "D"; case 2: return "N"; case 3: return "R"; case 4: return "P"; default: return String.valueOf(raw); }
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
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(cCard);
        row.setPadding(16, 10, 16, 10);

        TextView nameView = new TextView(this);
        nameView.setText(label);
        nameView.setTextSize(13);
        nameView.setTextColor(cText);
        nameView.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(nameView);

        TextView valView = new TextView(this);
        valView.setText("--");
        valView.setTextSize(13);
        valView.setTextColor(0xFF64D2FF); // teal
        valView.setTag(tag);
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

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
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.emegelauncher.vehicle.VehicleServiceManager;

/**
 * TBox (Telematics Box) data screen.
 * Uses EngMode ISystemSettingsManager and ISystemHardwareManager to access TBox data.
 * The TBox hardware communicates via JNI native library - we access it through
 * the EngMode service which already has the native library loaded.
 */
public class TboxActivity extends Activity {
    private static final String TAG = "TboxActivity";
    private final Handler mHandler = new Handler(android.os.Looper.getMainLooper());
    private LinearLayout mContent;
    private VehicleServiceManager mVehicle;
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

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(0, 4, 0, 8);
        TextView title = new TextView(this);
        title.setText(getString(R.string.tbox_title));
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
        addSection(getString(R.string.engmode_vehicle));
        addRow("eng_battery", "Battery Power (EngMode)");
        addRow("eng_speed", "Speed (EngMode)");
        addRow("eng_charge", "Charge Status (EngMode)");
        addRow("eng_gear", "Gear (EngMode)");
        addRow("eng_parking", "Parking Brake (EngMode)");
        addRow("eng_power_run", "Power Run Type");
        addRow("eng_power_sys", "Power System Status");
        addRow("eng_showroom", "Showroom Mode");

        addSection(getString(R.string.engmode_hardware));
        addRow("hw_gnss", "GNSS Info");
        addRow("hw_bt", "Bluetooth Info");
        addRow("hw_mobile", "Mobile Network Info");
        addRow("hw_wifi", "WiFi Info");
        addRow("hw_vehicle_config", "Vehicle Config");

        addSection(getString(R.string.tbox_network));
        addRow("tbox_avlbly", "TBox Available");
        addRow("tbox_ip", "TBox Network Interface");

        addSection(getString(R.string.tbox_features));
        addRow("note", getString(R.string.tbox_note));

        addSection("VEHICLE OVERSEAS (Security)");
        addRow("vso_base_url", "Server Base URL");
        addRow("vso_token", "Auth Token");
        addRow("vso_avn_id", "AVN ID");
        addRow("vso_user_id", "User ID");
        addRow("vso_activated", "Vehicle Activated");
        addRow("vso_security_key", "Security Key");
    }

    private void updateAll() {
        // EngMode SystemSettings data
        updateTag("eng_battery", call("engmode", "getBatteryPower"));
        updateTag("eng_speed", call("engmode", "getCarSpeed") + " km/h");
        updateTag("eng_charge", call("engmode", "getChargeStatus"));
        updateTag("eng_gear", call("engmode", "getGearStatus"));
        updateTag("eng_parking", call("engmode", "getParkingBrakeStatus"));
        updateTag("eng_power_run", call("engmode", "getPowerRunType"));
        updateTag("eng_power_sys", call("engmode", "getPowerSystemStatus"));
        updateTag("eng_showroom", call("engmode", "getShowRoom"));

        // EngMode SystemHardware data (beans returned as objects - try toString)
        updateTag("hw_gnss", call("enghardware", "getGNSSInfoBean"));
        updateTag("hw_bt", call("enghardware", "getBluetoothInfoBean"));
        updateTag("hw_mobile", call("enghardware", "getMobileNetworkInfoBean"));
        updateTag("hw_wifi", call("enghardware", "getWifiInfoBean"));
        updateTag("hw_vehicle_config", call("enghardware", "getVehicleConfig"));

        // VHAL TBox availability
        updateTag("tbox_avlbly", mVehicle.getPropertyValue(
            com.emegelauncher.vehicle.YFVehicleProperty.SENSOR_TBOXAVLBLY));
        updateTag("tbox_ip", "tbox0 @ 192.168.225.47 (from logcat)");

        // Vehicle Overseas data
        updateTag("vso_base_url", call("overseas", "getBaseUrl"));
        updateTag("vso_token", call("overseas", "getToken"));
        updateTag("vso_avn_id", call("overseas", "getAvnId"));
        updateTag("vso_user_id", call("overseas", "getUserId"));
        updateTag("vso_activated", call("overseas", "isVehicleActivated"));
        updateTag("vso_security_key", call("overseas", "getSecurityKey"));
    }

    private String call(String svc, String method) {
        return mVehicle.callSaicMethod(svc, method);
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
        row.setPadding(16, 8, 16, 8);
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
        valView.setMaxLines(15);
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
                updateAll();
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

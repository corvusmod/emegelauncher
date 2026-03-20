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
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.emegelauncher.vehicle.SaicCloudManager;
import com.emegelauncher.widget.LineChartView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Cloud data screen — shows iSMART cloud data with statistics graphs.
 * Tabs: Status | Stats | Keys | Info
 */
public class CloudActivity extends Activity {
    private static final String TAG = "CloudActivity";

    private SaicCloudManager mCloud;
    private LinearLayout mContent;
    private int mCurrentTab = 0;
    private int cBg, cCard, cText, cTextSec, cTextTert, cDivider;
    private int C_BLUE, C_GREEN, C_ORANGE, C_TEAL, C_PURPLE, C_RED;
    private String[] TAB_NAMES;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        resolveColors();

        mCloud = new SaicCloudManager(this);
        TAB_NAMES = new String[]{
            getString(R.string.cloud_tab_status), getString(R.string.cloud_tab_stats),
            getString(R.string.cloud_tab_keys), getString(R.string.cloud_tab_geofence),
            getString(R.string.cloud_tab_poi)
        };

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);
        root.setPadding(20, 8, 20, 8);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(0, 4, 0, 8);
        TextView title = new TextView(this);
        title.setText(getString(R.string.cloud_title));
        title.setTextSize(22);
        title.setTextColor(cText);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(title);

        // Refresh button
        TextView refreshBtn = new TextView(this);
        refreshBtn.setText(getString(R.string.cloud_refresh_btn));
        refreshBtn.setTextSize(13);
        refreshBtn.setTextColor(C_TEAL);
        refreshBtn.setPadding(20, 12, 10, 12);
        refreshBtn.setOnClickListener(v -> refreshCurrentTab());
        header.addView(refreshBtn);

        TextView back = new TextView(this);
        back.setText(getString(R.string.back));
        back.setTextSize(13);
        back.setTextColor(ThemeHelper.accentBlue(this));
        back.setPadding(10, 12, 20, 12);
        back.setOnClickListener(v -> finish());
        header.addView(back);
        root.addView(header);

        // Tab bar
        root.addView(createTabBar(), new LinearLayout.LayoutParams(-1, -2));

        // Content scroll
        ScrollView scroll = new ScrollView(this);
        mContent = new LinearLayout(this);
        mContent.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(mContent);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);

        if (!mCloud.isLoggedIn()) {
            showMessage(getString(R.string.cloud_login_msg));
        } else {
            switchTab(0);
        }
    }

    private LinearLayout createTabBar() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(0, 4, 0, 8);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int idx = i;
            TextView tab = new TextView(this);
            tab.setText(TAB_NAMES[i]);
            tab.setTextSize(14);
            tab.setTextColor(i == 0 ? C_BLUE : cTextSec);
            tab.setPadding(24, 10, 24, 10);
            tab.setTag("tab_" + i);
            tab.setOnClickListener(v -> switchTab(idx));
            bar.addView(tab);
        }
        hsv.addView(bar);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.addView(hsv);
        return wrapper;
    }

    private void switchTab(int idx) {
        mCurrentTab = idx;
        // Update tab colors
        for (int i = 0; i < TAB_NAMES.length; i++) {
            TextView tab = mContent.getRootView().findViewWithTag("tab_" + i);
            if (tab != null) tab.setTextColor(i == idx ? C_BLUE : cTextSec);
        }
        mContent.removeAllViews();
        switch (idx) {
            case 0: buildStatus(); break;
            case 1: buildStats(); break;
            case 2: buildKeys(); break;
            case 3: buildGeofence(); break;
            case 4: buildPoi(); break;
        }
    }

    private void refreshCurrentTab() {
        if (!mCloud.isLoggedIn()) {
            Toast.makeText(this, getString(R.string.cloud_not_logged_toast), Toast.LENGTH_SHORT).show();
            return;
        }
        switch (mCurrentTab) {
            case 0:
                Toast.makeText(this, getString(R.string.cloud_refreshing), Toast.LENGTH_SHORT).show();
                mCloud.forceRefresh((ok, msg) -> {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    if (ok) switchTab(0);
                });
                break;
            case 1:
                fetchAndShowStats();
                break;
            case 2:
                Toast.makeText(this, getString(R.string.cloud_fetching_keys), Toast.LENGTH_SHORT).show();
                mCloud.queryBtKeys((ok, msg) -> { if (ok) switchTab(2); else Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); });
                break;
            case 3:
                Toast.makeText(this, getString(R.string.cloud_fetching_geofence), Toast.LENGTH_SHORT).show();
                mCloud.queryGeofence((ok, msg) -> switchTab(3));
                break;
            case 4:
                Toast.makeText(this, getString(R.string.cloud_fetching_pois), Toast.LENGTH_SHORT).show();
                mCloud.queryFavoritePois((ok, msg) -> switchTab(4));
                break;
            // Info tab removed — data fetched on startup
        }
    }

    // ==================== Status Tab ====================

    private void buildStatus() {
        if (!mCloud.hasData()) {
            showMessage(getString(R.string.cloud_no_data));
            return;
        }
        try {
            String json = mCloud.getLastFullStatusJson();
            if (json == null) { showMessage(getString(R.string.cloud_no_cached)); return; }
            JSONObject data = new JSONObject(json);
            JSONObject bvs = data.optJSONObject("basicVehicleStatus");
            if (bvs == null) { showMessage(getString(R.string.cloud_no_status)); return; }

            addSection(getString(R.string.cloud_temperatures));
            addRow(getString(R.string.cloud_cabin), formatTemp(bvs.optInt("interiorTemperature", -999)));
            addRow(getString(R.string.cloud_outside), formatTemp(bvs.optInt("exteriorTemperature", -999)));

            addSection(getString(R.string.cloud_battery_power));
            addRow(getString(R.string.cloud_12v_battery), String.format("%.1fV", bvs.optInt("batteryVoltage", 0) / 10.0));
            addRow(getString(R.string.cloud_power_mode), decodePowerMode(bvs.optInt("powerMode", -1)));
            addRow(getString(R.string.cloud_engine_status), bvs.optInt("engineStatus", 0) == 1 ? getString(R.string.cloud_running) : getString(R.string.cloud_off));
            addRow(getString(R.string.cloud_ev_range), bvs.optInt("fuelRangeElec", 0) / 10.0 + " km");
            addRow(getString(R.string.cloud_range_display), bvs.optInt("vehElecRngDsp", 0) / 10.0 + " km");

            addSection(getString(R.string.cloud_trip));
            addRow(getString(R.string.cloud_mileage_today), bvs.optInt("mileageOfDay", 0) / 10.0 + " km");
            addRow(getString(R.string.cloud_since_charge), bvs.optInt("mileageSinceLastCharge", 0) / 10.0 + " km");
            addRow(getString(R.string.cloud_current_journey), bvs.optInt("currentJourneyDistance", 0) / 10.0 + " km");
            addRow(getString(R.string.cloud_total_odometer), bvs.optLong("mileage", 0) / 10.0 + " km");

            addSection(getString(R.string.cloud_doors_locks));
            addRow(getString(R.string.cloud_lock), bvs.optInt("lockStatus", 0) == 1 ? getString(R.string.cloud_locked) : getString(R.string.cloud_unlocked));
            addRow(getString(R.string.cloud_driver_door), bvs.optInt("driverDoor", 0) == 0 ? getString(R.string.cloud_closed) : getString(R.string.cloud_open));
            addRow(getString(R.string.cloud_passenger_door), bvs.optInt("passengerDoor", 0) == 0 ? getString(R.string.cloud_closed) : getString(R.string.cloud_open));
            addRow(getString(R.string.cloud_rear_left), bvs.optInt("rearLeftDoor", 0) == 0 ? getString(R.string.cloud_closed) : getString(R.string.cloud_open));
            addRow(getString(R.string.cloud_rear_right), bvs.optInt("rearRightDoor", 0) == 0 ? getString(R.string.cloud_closed) : getString(R.string.cloud_open));
            addRow(getString(R.string.cloud_bonnet), bvs.optInt("bonnetStatus", 0) == 0 ? getString(R.string.cloud_closed) : getString(R.string.cloud_open));
            addRow(getString(R.string.cloud_boot), bvs.optInt("bootStatus", 0) == 0 ? getString(R.string.cloud_closed) : getString(R.string.cloud_open));
            addRow(getString(R.string.cloud_sunroof), bvs.optInt("sunroofStatus", 0) == 0 ? getString(R.string.cloud_closed) : getString(R.string.cloud_open));

            addSection(getString(R.string.cloud_windows));
            addRow(getString(R.string.cloud_driver), bvs.optInt("driverWindow", 0) == 0 ? getString(R.string.cloud_closed) : getString(R.string.cloud_open));
            addRow(getString(R.string.cloud_passenger), bvs.optInt("passengerWindow", 0) == 0 ? getString(R.string.cloud_closed) : getString(R.string.cloud_open));
            addRow(getString(R.string.cloud_rear_left), bvs.optInt("rearLeftWindow", 0) == 0 ? getString(R.string.cloud_closed) : getString(R.string.cloud_open));
            addRow(getString(R.string.cloud_rear_right), bvs.optInt("rearRightWindow", 0) == 0 ? getString(R.string.cloud_closed) : getString(R.string.cloud_open));

            addSection(getString(R.string.cloud_tyres));
            addRow(getString(R.string.cloud_front_left), String.format("%.2f bar", bvs.optInt("frontLeftTyrePressure", 0) * 0.04));
            addRow(getString(R.string.cloud_front_right), String.format("%.2f bar", bvs.optInt("frontRightTyrePressure", 0) * 0.04));
            addRow(getString(R.string.cloud_rear_left), String.format("%.2f bar", bvs.optInt("rearLeftTyrePressure", 0) * 0.04));
            addRow(getString(R.string.cloud_rear_right), String.format("%.2f bar", bvs.optInt("rearRightTyrePressure", 0) * 0.04));

            addSection(getString(R.string.cloud_lights));
            addRow(getString(R.string.cloud_side_lights), bvs.optInt("sideLightStatus", 0) == 1 ? getString(R.string.cloud_on) : getString(R.string.cloud_off));
            addRow(getString(R.string.cloud_dipped_beam), bvs.optInt("dippedBeamStatus", 0) == 1 ? getString(R.string.cloud_on) : getString(R.string.cloud_off));
            addRow(getString(R.string.cloud_main_beam), bvs.optInt("mainBeamStatus", 0) == 1 ? getString(R.string.cloud_on) : getString(R.string.cloud_off));

            addSection(getString(R.string.cloud_comfort));
            addRow(getString(R.string.cloud_handbrake), bvs.optInt("handBrake", bvs.optInt("handbrake", 0)) == 1 ? getString(R.string.cloud_engaged) : getString(R.string.cloud_released));
            addRow(getString(R.string.cloud_remote_climate), bvs.optInt("remoteClimateStatus", 0) == 1 ? getString(R.string.cloud_active) : getString(R.string.cloud_off));
            addRow(getString(R.string.cloud_drv_seat_heat), decodeSeatHeat(bvs.optInt("frontLeftSeatHeatLevel", 0)));
            addRow(getString(R.string.cloud_psg_seat_heat), decodeSeatHeat(bvs.optInt("frontRightSeatHeatLevel", 0)));
            addRow(getString(R.string.cloud_steering_heat), bvs.optInt("steeringHeatLevel", 0) > 0 ? getString(R.string.cloud_on) : getString(R.string.cloud_off));
            addRow(getString(R.string.cloud_alarm), bvs.optInt("vehicleAlarmStatus", 0) == 0 ? getString(R.string.cloud_off) : getString(R.string.cloud_active));

            addSection(getString(R.string.cloud_misc));
            addRow(getString(R.string.cloud_can_bus), bvs.optInt("canBusActive", 0) == 1 ? getString(R.string.yes) : getString(R.string.no));
            addRow(getString(R.string.cloud_last_key), String.valueOf(bvs.optInt("lastKeySeen", 0)));
            long age = (System.currentTimeMillis() - mCloud.getLastQueryTime()) / 1000;
            addRow(getString(R.string.cloud_data_age), age < 60 ? age + "s" : (age / 60) + " min");

        } catch (Exception e) {
            showMessage("Error: " + e.getMessage());
        }
    }

    // ==================== Stats Tab (graphs) ====================

    // ==================== Stats Tab (interactive) ====================

    private String mStatsRangeType = "3"; // "1"=day, "2"=month, "3"=year
    private java.util.Calendar mStatsDate = java.util.Calendar.getInstance();

    private void buildStats() {
        if (!mCloud.isLoggedIn()) { showDisabled("Login required"); return; }

        // Period selector: Day | Month | Year
        LinearLayout periodRow = new LinearLayout(this);
        periodRow.setGravity(Gravity.CENTER);
        periodRow.setPadding(0, 8, 0, 8);
        String[][] periods = {{"1", "Day"}, {"2", "Month"}, {"3", "Year"}};
        for (String[] p : periods) {
            TextView btn = new TextView(this);
            btn.setText(p[1]);
            btn.setTextSize(15);
            btn.setTextColor(p[0].equals(mStatsRangeType) ? C_BLUE : cTextTert);
            btn.setPadding(24, 8, 24, 8);
            btn.setOnClickListener(v -> { mStatsRangeType = p[0]; fetchAndShowStats(); });
            periodRow.addView(btn);
        }
        mContent.addView(periodRow);

        // Date navigation: < date >
        LinearLayout navRow = new LinearLayout(this);
        navRow.setGravity(Gravity.CENTER);
        navRow.setPadding(0, 4, 0, 12);

        TextView prevBtn = new TextView(this);
        prevBtn.setText("  \u25C0  ");
        prevBtn.setTextSize(18);
        prevBtn.setTextColor(C_BLUE);
        prevBtn.setOnClickListener(v -> {
            switch (mStatsRangeType) {
                case "1": mStatsDate.add(java.util.Calendar.DAY_OF_YEAR, -1); break;
                case "2": mStatsDate.add(java.util.Calendar.MONTH, -1); break;
                case "3": mStatsDate.add(java.util.Calendar.YEAR, -1); break;
            }
            fetchAndShowStats();
        });
        navRow.addView(prevBtn);

        TextView dateLabel = new TextView(this);
        String dateFmt;
        switch (mStatsRangeType) {
            case "1": dateFmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(mStatsDate.getTime()); break;
            case "2": dateFmt = new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(mStatsDate.getTime()); break;
            default: dateFmt = new SimpleDateFormat("yyyy", Locale.getDefault()).format(mStatsDate.getTime()); break;
        }
        dateLabel.setText(dateFmt);
        dateLabel.setTextSize(16);
        dateLabel.setTextColor(cText);
        dateLabel.setPadding(20, 0, 20, 0);
        navRow.addView(dateLabel);

        TextView nextBtn = new TextView(this);
        nextBtn.setText("  \u25B6  ");
        nextBtn.setTextSize(18);
        nextBtn.setTextColor(C_BLUE);
        nextBtn.setOnClickListener(v -> {
            switch (mStatsRangeType) {
                case "1": mStatsDate.add(java.util.Calendar.DAY_OF_YEAR, 1); break;
                case "2": mStatsDate.add(java.util.Calendar.MONTH, 1); break;
                case "3": mStatsDate.add(java.util.Calendar.YEAR, 1); break;
            }
            fetchAndShowStats();
        });
        navRow.addView(nextBtn);
        mContent.addView(navRow);

        // Show cached data for current rangeType
        String cached = mCloud.getCachedStats(mStatsRangeType);
        if (cached != null) {
            renderStats(cached);
        } else {
            showMessage(getString(R.string.cloud_no_data_refresh));
        }
    }

    private void fetchAndShowStats() {
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(mStatsDate.getTime());
        Toast.makeText(this, getString(R.string.cloud_fetching_stats), Toast.LENGTH_SHORT).show();
        mCloud.queryStatistics(mStatsRangeType, dateStr, (ok, msg) -> {
            if (ok) runOnUiThread(() -> switchTab(1));
            else Toast.makeText(this, "Stats: " + msg, Toast.LENGTH_SHORT).show();
        });
    }

    private void renderStats(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            JSONObject data = json.optJSONObject("data");
            if (data == null) { showMessage("No statistics data"); return; }

            String[][] charts = {
                {"mileageList", "Mileage", "km"},
                {"powerConsumptionList", "Consumption", "kWh/100km"},
                {"co2List", "CO\u2082 Saved", "kg"},
                {"averageSpeedList", "Avg Speed", "km/h"},
                {"travelTimeList", "Travel Time", "h"}
            };
            int[] colors = {C_BLUE, C_ORANGE, C_GREEN, C_TEAL, C_PURPLE};

            for (int c = 0; c < charts.length; c++) {
                JSONArray items = data.optJSONArray(charts[c][0]);
                if (items == null || items.length() == 0) continue;

                // Chart
                LineChartView chart = new LineChartView(this);
                chart.setLabel(charts[c][1]);
                chart.setUnit(charts[c][2]);
                chart.setLineColor(colors[c]);
                chart.setGridColor(cDivider);
                chart.setTextColor(cTextSec);
                chart.setBackgroundColor(cCard);

                double total = 0, max = 0;
                int nonZero = 0;
                for (int i = 0; i < items.length(); i++) {
                    double v = items.getJSONObject(i).optDouble("value", 0);
                    chart.addPoint((float) v);
                    total += v;
                    if (v > max) max = v;
                    if (v > 0) nonZero++;
                }

                LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(-1, 200);
                chartLp.setMargins(0, 8, 0, 4);
                mContent.addView(chart, chartLp);

                // Summary row
                double avg = nonZero > 0 ? total / nonZero : 0;
                String summary;
                if (charts[c][0].equals("travelTimeList")) {
                    int totalH = (int) total;
                    int totalM = (int) ((total - totalH) * 60);
                    summary = String.format("Total: %dh %dm | Avg: %.1fh | Max: %.1fh", totalH, totalM, avg, max);
                } else {
                    summary = String.format("Total: %.1f | Avg: %.1f | Max: %.1f %s", total, avg, max, charts[c][2]);
                }
                addRow(charts[c][1], summary);
            }

            // If no charts were rendered
            if (data.optJSONArray("mileageList") == null) {
                showMessage("No data for this period");
            }
        } catch (Exception e) {
            showMessage("Parse error: " + e.getMessage());
        }
    }

    // ==================== Keys Tab ====================

    private void buildKeys() {
        if (!mCloud.isLoggedIn()) { showDisabled("Login required"); return; }

        addSection(getString(R.string.cloud_bt_keys));
        String cached = mCloud.getCachedBtKeys();
        if (cached == null) {
            showMessage(getString(R.string.cloud_no_key_data));
            return;
        }
        try {
            JSONObject json = new JSONObject(cached);
            Object dataObj = json.opt("data");
            JSONArray keys = null;
            if (dataObj instanceof JSONArray) {
                keys = (JSONArray) dataObj;
            } else if (dataObj instanceof JSONObject) {
                keys = ((JSONObject) dataObj).optJSONArray("keyList");
                if (keys == null) {
                    // Try single key object
                    JSONObject singleKey = (JSONObject) dataObj;
                    if (singleKey.has("keyNum")) {
                        keys = singleKey.optJSONArray("keyList");
                    }
                }
            }
            if (keys == null || keys.length() == 0) {
                showMessage(getString(R.string.cloud_no_keys_found));
                return;
            }
            for (int i = 0; i < keys.length(); i++) {
                JSONObject key = keys.getJSONObject(i);
                long keyRef = key.optLong("keyReference", key.optLong("customerReference", -1));
                String keyTag = key.optString("keyTag", "Key " + (i + 1));
                int keyStatus = key.optInt("keyStatus", -1);

                addSection("Key " + (i + 1) + ": " + keyTag);
                addRow("Model", key.optString("modelName", "N/A"));
                addRow("Status", decodeKeyStatus(keyStatus));
                addRow("Type", key.optInt("keyType", 0) == 1 ? "Owner" : "Shared");
                addRow("BT MAC", key.optString("btMacAddress", "N/A"));
                addRow("User", key.optString("userAccount", key.optString("userName", "N/A")));
                addRow("Authority", String.valueOf(key.optInt("userAuthority", 0)));
                long start = key.optLong("keyValidityStartTime", 0);
                long end = key.optLong("keyValidityEndTime", 0);
                if (start > 0) addRow("Valid From", new Date(start * 1000).toString());
                if (end > 0) addRow("Valid Until", new Date(end * 1000).toString());

                // Management buttons
                final long fKeyRef = keyRef;
                final String fKeyTag = keyTag;
                if (keyStatus != 1) {
                    addCloudAction(getString(R.string.cloud_activate_key), () ->
                        mCloud.activateBtKey(fKeyRef, fKeyTag, 1, (ok, msg) -> {
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                            if (ok) { mCloud.queryBtKeys((ok2, m2) -> switchTab(2)); }
                        }));
                }
                if (keyStatus == 1) {
                    addCloudAction(getString(R.string.cloud_deactivate_key), () ->
                        mCloud.activateBtKey(fKeyRef, fKeyTag, 0, (ok, msg) -> {
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                            if (ok) { mCloud.queryBtKeys((ok2, m2) -> switchTab(2)); }
                        }));
                }
                if (keyStatus != 3) {
                    addCloudAction(getString(R.string.cloud_revoke_key), () ->
                        new android.app.AlertDialog.Builder(this)
                            .setTitle(getString(R.string.cloud_revoke_key))
                            .setMessage(String.format(getString(R.string.cloud_revoke_confirm), fKeyTag))
                            .setPositiveButton(getString(R.string.cloud_revoke_key), (d, w) ->
                                mCloud.activateBtKey(fKeyRef, fKeyTag, 3, (ok, msg) -> {
                                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                                    if (ok) { mCloud.queryBtKeys((ok2, m2) -> switchTab(2)); }
                                }))
                            .setNegativeButton("Cancel", null)
                            .show());
                }
            }
        } catch (Exception e) {
            showMessage("Parse error: " + e.getMessage());
        }
    }

    // ==================== Geofence Tab ====================

    private void buildGeofence() {
        if (!mCloud.isLoggedIn()) { showDisabled("Login required"); return; }

        addSection(getString(R.string.cloud_geofence_settings));

        String cached = mCloud.getCachedGeofence();
        if (cached != null) {
            try {
                JSONObject json = new JSONObject(cached);
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    addRow("Region ID", data.optString("regionId", "None"));
                    int sw = data.optInt("regionSettingSwitch", 0);
                    addRow("Enabled", sw == 1 ? "Yes" : "No");
                    addRow("Type", data.optInt("regionType", 0) == 1 ? "Circle" : "Rectangle");
                    addRow("Radius", data.optInt("radius", 0) + " m");
                    double lat = data.optLong("centerLatitude", 0) / 1000000.0;
                    double lon = data.optLong("centerLongitude", 0) / 1000000.0;
                    if (lat != 0) addRow("Center", String.format("%.6f, %.6f", lat, lon));
                }
            } catch (Exception e) { addRow("Error", e.getMessage()); }
        } else {
            addRow("Geofence", "No data — tap REFRESH");
        }

        addSection(getString(R.string.cloud_set_geofence));

        addActionButton(getString(R.string.cloud_set_geofence_current), () -> {
            // Use SAIC navigation service for GPS
            try {
                String locJson = com.emegelauncher.vehicle.VehicleServiceManager.getInstance(this)
                    .callSaicMethod("adaptervoice", "getCurLocationDesc");
                double tmpLat = 0, tmpLon = 0;
                if (locJson != null && locJson.startsWith("{")) {
                    org.json.JSONObject locObj = new org.json.JSONObject(locJson);
                    tmpLat = locObj.optDouble("lat", 0);
                    tmpLon = locObj.optDouble("lon", 0);
                }
                if (tmpLat == 0 && tmpLon == 0) { Toast.makeText(this, "No GPS", Toast.LENGTH_SHORT).show(); return; }
                final double lat = tmpLat, lon = tmpLon;

                LinearLayout layout = new LinearLayout(this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(50, 20, 50, 10);

                TextView posLabel = new TextView(this);
                posLabel.setText(String.format("Position: %.6f, %.6f", lat, lon));
                posLabel.setTextColor(cText);
                layout.addView(posLabel);

                TextView radLabel = new TextView(this);
                radLabel.setText("Radius (meters):");
                radLabel.setTextColor(cText);
                layout.addView(radLabel);
                android.widget.EditText radInput = new android.widget.EditText(this);
                radInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                radInput.setText("500");
                layout.addView(radInput);

                String[] alertTypes = {"Enter zone", "Exit zone", "Both"};

                new android.app.AlertDialog.Builder(this)
                    .setTitle("Set Geofence")
                    .setView(layout)
                    .setPositiveButton("Set (alert on exit)", (d, w) -> {
                        int radius = Integer.parseInt(radInput.getText().toString());
                        mCloud.setGeofence(lat, lon, radius, 2, true, (ok, msg) -> {
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                            if (ok) { mCloud.queryGeofence((ok2, m2) -> switchTab(3)); }
                        });
                    })
                    .setNeutralButton("Delete", (d, w) ->
                        mCloud.setGeofence(0, 0, 0, 0, false, (ok, msg) -> {
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                            if (ok) switchTab(3);
                        }))
                    .setNegativeButton("Cancel", null)
                    .show();
            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==================== POI Tab ====================

    private void buildPoi() {
        if (!mCloud.isLoggedIn()) { showDisabled("Login required"); return; }

        addSection(getString(R.string.cloud_send_poi));

        addActionButton(getString(R.string.cloud_send_gps), () -> {
            try {
                // Use SAIC navigation service for GPS (works on head unit without LocationManager)
                String locJson = com.emegelauncher.vehicle.VehicleServiceManager.getInstance(this)
                    .callSaicMethod("adaptervoice", "getCurLocationDesc");
                double lat = 0, lon = 0;
                if (locJson != null && locJson.startsWith("{")) {
                    org.json.JSONObject loc = new org.json.JSONObject(locJson);
                    lat = loc.optDouble("lat", 0);
                    lon = loc.optDouble("lon", 0);
                }
                if (lat == 0 && lon == 0) { Toast.makeText(this, "No GPS", Toast.LENGTH_SHORT).show(); return; }
                mCloud.sendPoiToCar("Current Location", lat, lon, null,
                    (ok, msg) -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        addActionButton(getString(R.string.cloud_send_custom), () -> {
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(50, 20, 50, 10);

            android.widget.EditText nameInput = new android.widget.EditText(this);
            nameInput.setHint("Name");
            layout.addView(nameInput);

            android.widget.EditText latInput = new android.widget.EditText(this);
            latInput.setHint("Latitude (e.g. 40.4168)");
            latInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
            layout.addView(latInput);

            android.widget.EditText lonInput = new android.widget.EditText(this);
            lonInput.setHint("Longitude (e.g. -3.7038)");
            lonInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
            layout.addView(lonInput);

            android.widget.EditText addrInput = new android.widget.EditText(this);
            addrInput.setHint("Address (optional)");
            layout.addView(addrInput);

            new android.app.AlertDialog.Builder(this)
                .setTitle("Send POI to Car")
                .setView(layout)
                .setPositiveButton("Send", (d, w) -> {
                    String name = nameInput.getText().toString();
                    double lat = Double.parseDouble(latInput.getText().toString());
                    double lon = Double.parseDouble(lonInput.getText().toString());
                    String addr = addrInput.getText().toString();
                    mCloud.sendPoiToCar(name, lat, lon, addr.isEmpty() ? null : addr,
                        (ok, msg) -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        // Show favorites
        addSection(getString(R.string.cloud_fav_pois));
        String cached = mCloud.getCachedPois();
        if (cached != null) {
            try {
                JSONObject json = new JSONObject(cached);
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    JSONArray list = data.optJSONArray("poiFavoriteList");
                    if (list != null && list.length() > 0) {
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject fav = list.getJSONObject(i);
                            JSONObject poi = fav.optJSONObject("poi");
                            if (poi == null) continue;
                            String name = poi.optString("name", "POI " + (i + 1));
                            double lat = poi.optDouble("latitude", 0);
                            double lon = poi.optDouble("longitude", 0);
                            String addr = poi.optString("address", "");
                            addRow(name, addr.isEmpty() ? String.format("%.4f, %.4f", lat, lon) : addr);
                            // Send button
                            final double fLat = lat;
                            final double fLon = lon;
                            final String fName = name;
                            final String fAddr = addr;
                            addActionButton("  Send \"" + name + "\" to car", () ->
                                mCloud.sendPoiToCar(fName, fLat, fLon, fAddr,
                                    (ok, msg) -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()));
                        }
                    } else {
                        addRow("Favorites", "No favorites saved");
                    }
                }
            } catch (Exception e) { addRow("Error", e.getMessage()); }
        } else {
            addRow("POIs", "No data — tap REFRESH");
        }
    }

    // ==================== Info Tab ====================

    private void buildInfo() {
        // TBox status
        addSection(getString(R.string.cloud_tbox_status));
        String tbox = mCloud.getCachedTboxStatus();
        if (tbox != null) {
            try {
                JSONObject json = new JSONObject(tbox);
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    int status = data.optInt("status", -1);
                    addRow("TBox Status", status == 0 ? "Online" : status == 1 ? "Offline" : status == 2 ? "Sleep" : "Unknown (" + status + ")");
                    JSONObject sms = data.optJSONObject("awakenSmsInfo");
                    if (sms != null) {
                        addRow("SMS Wake Limit", sms.optInt("sendNum", 0) + "/" + sms.optInt("dailyLimit", 0) + " today");
                    }
                }
            } catch (Exception ignored) {}
        } else {
            addRow("TBox", "No data — tap REFRESH");
        }

        // Features
        addSection(getString(R.string.cloud_vehicle_features));
        String feat = mCloud.getCachedFeatures();
        if (feat != null) {
            try {
                JSONObject json = new JSONObject(feat);
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    addRow("Platform", data.optString("platform", "N/A"));
                    addRow("AVN MCU", data.optString("avnMcuVersion", "N/A"));
                    addRow("AVN MPU", data.optString("avnMpuVersion", "N/A"));
                    addRow("TBox MCU", data.optString("tboxMcuVersion", "N/A"));
                    addRow("TBox MPU", data.optString("tboxMpuVersion", "N/A"));
                    JSONArray features = data.optJSONArray("featureList");
                    if (features != null) {
                        for (int i = 0; i < features.length(); i++) {
                            JSONObject f = features.getJSONObject(i);
                            String name = f.optString("featureName", "Feature " + f.optInt("featureId"));
                            boolean supported = f.optInt("isSupported", 0) == 1;
                            addRow(name, supported ? "Supported" : "Not supported");
                        }
                    }
                }
            } catch (Exception ignored) {}
        } else {
            addRow("Features", "No data — tap REFRESH");
        }

        // FOTA
        addSection(getString(R.string.cloud_fota));
        String fota = mCloud.getCachedFota();
        if (fota != null) {
            try {
                JSONObject json = new JSONObject(fota);
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    int campaignStatus = data.optInt("campaignStatus", -1);
                    addRow("Campaign Status", campaignStatus == 0 ? "No update" : campaignStatus == 1 ? "Available" : "Status " + campaignStatus);
                    JSONObject info = data.optJSONObject("campaignInfo");
                    if (info != null) {
                        addRow("Target Version", info.optString("targetMajorVersion", "N/A"));
                        addRow("Release Notes", info.optString("upgradeNote", "N/A"));
                        JSONArray ecus = info.optJSONArray("ecuInfoList");
                        if (ecus != null) {
                            for (int i = 0; i < ecus.length(); i++) {
                                JSONObject ecu = ecus.getJSONObject(i);
                                addRow(ecu.optString("ecuName", "ECU"), ecu.optString("srcVer", "?") + " → " + ecu.optString("dstVer", "?"));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        } else {
            addRow("FOTA", "No data — tap REFRESH");
        }

        // Messages
        addSection(getString(R.string.cloud_recent_messages));
        for (String group : new String[]{"ALARM", "COMMAND", "NEWS"}) {
            String msgs = mCloud.getCachedMessages(group);
            if (msgs != null) {
                try {
                    JSONObject json = new JSONObject(msgs);
                    JSONObject data = json.optJSONObject("data");
                    if (data != null) {
                        JSONArray messages = data.optJSONArray("messages");
                        if (messages != null && messages.length() > 0) {
                            for (int i = 0; i < Math.min(messages.length(), 3); i++) {
                                JSONObject m = messages.getJSONObject(i);
                                addRow("[" + group + "] " + m.optString("title", ""), m.optString("content", "").substring(0, Math.min(80, m.optString("content", "").length())));
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // Find My Car button
        addSection(getString(R.string.cloud_actions));
        addActionButton(getString(R.string.cloud_find_horn_lights), () ->
            mCloud.findMyCar(1, (ok, msg) -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()));
        addActionButton(getString(R.string.cloud_find_lights), () ->
            mCloud.findMyCar(2, (ok, msg) -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()));
        addActionButton(getString(R.string.cloud_force_wake), () ->
            mCloud.forceRefresh((ok, msg) -> {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                if (ok) switchTab(0);
            }));
    }

    // ==================== Helpers ====================

    private String formatTemp(int raw) {
        if (raw == -999 || raw == 0) return "N/A";
        return raw + "°C (raw)";
    }

    private String decodePowerMode(int mode) {
        switch (mode) {
            case 0: return "Off";
            case 1: return "ACC";
            case 2: return "ON";
            case 3: return "Start";
            default: return "Unknown (" + mode + ")";
        }
    }

    private String decodeSeatHeat(int level) {
        switch (level) {
            case 0: return "Off";
            case 1: return "Low";
            case 2: return "Medium";
            case 3: return "High";
            default: return String.valueOf(level);
        }
    }

    private String decodeKeyStatus(int status) {
        switch (status) {
            case 0: return "Inactive";
            case 1: return "Active";
            case 2: return "Expired";
            case 3: return "Revoked";
            default: return "Unknown (" + status + ")";
        }
    }

    private void showDisabled(String msg) {
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextSize(14);
        tv.setTextColor(0xFF666666);
        tv.setPadding(20, 40, 20, 40);
        tv.setGravity(Gravity.CENTER);
        mContent.addView(tv);
    }

    /** Cloud action button — greyed out if not logged in */
    private void addCloudAction(String label, Runnable action) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextSize(14);
        btn.setBackgroundColor(cCard);
        btn.setPadding(20, 14, 20, 14);
        if (mCloud.isLoggedIn()) {
            btn.setTextColor(C_BLUE);
            btn.setOnClickListener(v -> action.run());
        } else {
            btn.setTextColor(0xFF666666);
            btn.setEnabled(false);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 2, 0, 2);
        mContent.addView(btn, lp);
    }

    private void showMessage(String msg) {
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextSize(14);
        tv.setTextColor(cTextSec);
        tv.setPadding(20, 40, 20, 40);
        tv.setGravity(Gravity.CENTER);
        mContent.addView(tv);
    }

    private void addStatsNote(String note) {
        TextView tv = new TextView(this);
        tv.setText(note);
        tv.setTextSize(11);
        tv.setTextColor(cTextTert);
        tv.setPadding(4, 4, 4, 8);
        mContent.addView(tv);
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

    private void addRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(cCard);
        row.setPadding(16, 8, 16, 8);

        TextView nameView = new TextView(this);
        nameView.setText(label);
        nameView.setTextSize(13);
        nameView.setTextColor(cText);
        nameView.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(nameView);

        TextView valView = new TextView(this);
        valView.setText(value);
        valView.setTextSize(13);
        valView.setTextColor(C_TEAL);
        valView.setMaxLines(3);
        row.addView(valView);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 1, 0, 1);
        mContent.addView(row, lp);
    }

    private void addActionButton(String label, Runnable action) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextSize(14);
        btn.setTextColor(C_BLUE);
        btn.setBackgroundColor(cCard);
        btn.setPadding(20, 16, 20, 16);
        btn.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 2, 0, 2);
        mContent.addView(btn, lp);
    }

    private void resolveColors() {
        cBg = ThemeHelper.resolveColor(this, R.attr.colorBgPrimary);
        cCard = ThemeHelper.resolveColor(this, R.attr.colorBgCard);
        cText = ThemeHelper.resolveColor(this, R.attr.colorTextPrimary);
        cTextSec = ThemeHelper.resolveColor(this, R.attr.colorTextSecondary);
        cTextTert = ThemeHelper.resolveColor(this, R.attr.colorTextTertiary);
        cDivider = ThemeHelper.resolveColor(this, R.attr.colorDivider);
        C_BLUE = ThemeHelper.accentBlue(this);
        C_GREEN = ThemeHelper.accentGreen(this);
        C_ORANGE = ThemeHelper.accentOrange(this);
        C_TEAL = ThemeHelper.accentTeal(this);
        C_PURPLE = ThemeHelper.accentPurple(this);
        C_RED = ThemeHelper.accentRed(this);
    }
}

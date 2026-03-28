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
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.emegelauncher.vehicle.BatteryHealthTracker;
import com.emegelauncher.vehicle.VehicleServiceManager;
import com.emegelauncher.vehicle.YFVehicleProperty;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import com.emegelauncher.widget.ArcGaugeView;
import com.emegelauncher.widget.LineChartView;
import com.emegelauncher.widget.GMeterView;
import com.emegelauncher.widget.TireDiagramView;

/**
 * Single activity with tab-based navigation for all graph screens.
 * Tabs: Dashboard | Energy | Charging | Tires | Climate | Trip
 */
public class GraphsActivity extends Activity {
    private static final String TAG = "GraphsActivity";

    private VehicleServiceManager mVehicle;
    private final Handler mHandler = new Handler(android.os.Looper.getMainLooper());
    private LinearLayout mContent;
    private int mCurrentTab = 0;
    private boolean mChargeHistoryRefreshed = false;
    private double mEnergyAccumKwh = 0;
    private double mDistanceAccumKm = 0;
    private float mPrevSpeedMs = 0;
    private long mPrevSpeedTimeMs = 0;
    private float mEcoScoreAvg = 100f;
    private boolean mEcoScoreInit = false;

    // Dashboard widgets
    private ArcGaugeView mSpeedGauge, mSocGauge, mRpmGauge, mEfficiencyGauge;
    private LineChartView mEnergyFlowChart, mDashGChart;
    private TextView mGearText, mRangeText;

    // Energy widgets
    private LineChartView mSocChart, mVoltChart, mCurrentChart, mConsumptionChart;

    // Charging widgets
    private ArcGaugeView mChargeRateGauge;
    private LineChartView mChargePowerChart, mChargeCurrentChart, mChargeVoltChart;
    private TextView mChargeStatus, mChargeTime, mTargetSoc, mPlugStatus, mDoorStatus;

    // Tire widget
    private TireDiagramView mTireDiagram;

    // Climate widgets
    private LineChartView mTempChart, mPm25Chart;

    // Trip widgets
    private TextView mOdometer, mAvgConsumption, mTotalConsumed, mRegenEnergy, mRegenRange;

    // Health
    private BatteryHealthTracker mHealthTracker;

    // Theme colors
    private int cBg, cCard, cText, cTextSec, cTextTert, cDivider;
    private int C_BLUE, C_GREEN, C_RED;
    private int C_ORANGE, C_TEAL, C_PURPLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        resolveColors();
        TAB_NAMES = new String[]{
            getString(R.string.tab_dashboard), getString(R.string.tab_energy),
            getString(R.string.tab_charge_history), getString(R.string.tab_health),
            getString(R.string.tab_tires), getString(R.string.tab_climate),
            getString(R.string.tab_trip), getString(R.string.tab_gmeter)
        };

        mVehicle = VehicleServiceManager.getInstance(this);
        mHealthTracker = new BatteryHealthTracker(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);
        root.setPadding(20, 8, 20, 8);

        // Tab bar
        LinearLayout tabBar = createTabBar();
        root.addView(tabBar, new LinearLayout.LayoutParams(-1, -2));

        // Content scroll
        ScrollView scroll = new ScrollView(this);
        mContent = new LinearLayout(this);
        mContent.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(mContent, new LinearLayout.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);

        int tab = getIntent().getIntExtra("tab", 0);
        switchTab(tab);
        startPolling();
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
        C_RED = ThemeHelper.accentRed(this);
        C_ORANGE = ThemeHelper.accentOrange(this);
        C_TEAL = ThemeHelper.accentTeal(this);
        C_PURPLE = ThemeHelper.accentPurple(this);
    }

    // ==================== Tab Bar ====================

    private String[] TAB_NAMES;
    private TextView[] tabViews;

    private LinearLayout createTabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(0, 4, 0, 8);
        tabViews = new TextView[TAB_NAMES.length];

        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int idx = i;
            TextView tv = new TextView(this);
            tv.setText(TAB_NAMES[i]);
            tv.setTextSize(13);
            tv.setPadding(16, 12, 16, 12);
            tv.setOnClickListener(v -> switchTab(idx));
            tabViews[i] = tv;
            bar.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
        }

        // Back button
        TextView back = new TextView(this);
        back.setText(getString(R.string.back));
        back.setTextSize(13);
        back.setTextColor(C_BLUE);
        back.setPadding(20, 12, 20, 12);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(-2, -2));

        return bar;
    }

    private void switchTab(int tab) {
        mCurrentTab = tab;
        for (int i = 0; i < tabViews.length; i++) {
            tabViews[i].setTextColor(i == tab ? C_BLUE : cTextSec);
        }
        mContent.removeAllViews();
        switch (tab) {
            case 0: buildDashboard(); break;
            case 1: buildEnergy(); break;
            case 2: buildChargeHistory(); break;
            case 3: buildHealth(); break;
            case 4: buildTires(); break;
            case 5: buildClimate(); break;
            case 6: buildTrip(); break;
            case 7: buildGMeter(); break;
        }
    }

    // ==================== Dashboard ====================

    private void buildDashboard() {
        // Speed + SOC gauges side by side
        LinearLayout row1 = newRow();
        mSpeedGauge = newGauge(getString(R.string.graph_speed), "km/h", 220, C_BLUE);
        mSocGauge = newGauge(getString(R.string.graph_battery), "%", 100, C_GREEN);
        row1.addView(mSpeedGauge, gaugeLP());
        row1.addView(mSocGauge, gaugeLP());
        mContent.addView(row1);

        // RPM + Efficiency gauges
        LinearLayout row2 = newRow();
        mRpmGauge = newGauge("kWh/100km", "kWh/100km", 50, C_ORANGE);
        mEfficiencyGauge = newGauge("Eco", "", 100, C_GREEN);
        row2.addView(mRpmGauge, gaugeLP());
        row2.addView(mEfficiencyGauge, gaugeLP());
        mContent.addView(row2);

        // Energy flow chart (positive = consumption, negative = regen)
        mEnergyFlowChart = newChart(getString(R.string.graph_energy_flow), "kW", C_TEAL);
        mContent.addView(mEnergyFlowChart, chartLP());

        // Gear + Range info row
        LinearLayout row3 = newRow();
        row3.setPadding(20, 16, 20, 16);

        mGearText = newInfoLabel(getString(R.string.graph_gear, "--"));
        mRangeText = newInfoLabel(getString(R.string.graph_range_none));
        row3.addView(mGearText, new LinearLayout.LayoutParams(0, -2, 1f));
        row3.addView(mRangeText, new LinearLayout.LayoutParams(0, -2, 1f));
        mContent.addView(row3);

        // G-Force chart (compact, from G-Meter data)
        mDashGChart = newChart(getString(R.string.graph_gforce), "G", C_RED);
        mContent.addView(mDashGChart, chartLP());

        // Drive mode
        TextView drvMode = newInfoLabel(getString(R.string.graph_drive_mode, "--"));
        drvMode.setTag("dash_drive_mode");
        mContent.addView(drvMode, infoLP());
    }

    // ==================== Energy ====================

    private LineChartView mSocBmsChart;

    private void buildEnergy() {
        mSocChart = newChart(getString(R.string.graph_soc_display), "%", C_GREEN);
        mContent.addView(mSocChart, chartLP());

        mSocBmsChart = newChart(getString(R.string.graph_soc_bms), "%", C_TEAL);
        mContent.addView(mSocBmsChart, chartLP());

        mVoltChart = newChart(getString(R.string.graph_pack_voltage), "V", C_ORANGE);
        mContent.addView(mVoltChart, chartLP());

        mCurrentChart = newChart(getString(R.string.graph_pack_current), "A", C_BLUE);
        mContent.addView(mCurrentChart, chartLP());

        mConsumptionChart = newChart(getString(R.string.graph_consumption), "kWh/100km", C_PURPLE);
        mContent.addView(mConsumptionChart, chartLP());
    }

    // ==================== Charging ====================

    private void buildCharging() {
        // Live power gauge
        mChargeRateGauge = newGauge(getString(R.string.graph_charge_power), "kW", 150, C_GREEN);
        mContent.addView(mChargeRateGauge, new LinearLayout.LayoutParams(-1, 250));

        // Live charts
        mChargePowerChart = newChart(getString(R.string.graph_charge_power_live), "kW", C_GREEN);
        mContent.addView(mChargePowerChart, chartLP());

        mChargeCurrentChart = newChart(getString(R.string.graph_current_batt_charger), "A", C_ORANGE);
        mContent.addView(mChargeCurrentChart, chartLP());

        mChargeVoltChart = newChart(getString(R.string.graph_voltage_pack_charger), "V", C_BLUE);
        mContent.addView(mChargeVoltChart, chartLP());

        addDivider();

        // Status info
        mChargeStatus = newInfoLabel(getString(R.string.graph_status, "--"));
        mContent.addView(mChargeStatus, infoLP());

        mChargeTime = newInfoLabel(getString(R.string.graph_time_remaining, "--"));
        mContent.addView(mChargeTime, infoLP());

        mTargetSoc = newInfoLabel(getString(R.string.graph_target_soc_none));
        mContent.addView(mTargetSoc, infoLP());

        mPlugStatus = newInfoLabel(getString(R.string.graph_plug, "--"));
        mContent.addView(mPlugStatus, infoLP());

        mDoorStatus = newInfoLabel(getString(R.string.graph_charge_door, "--"));
        mContent.addView(mDoorStatus, infoLP());

        addDivider();

        // Technical details (AC/DC)
        TextView secHeader = newInfoLabel(getString(R.string.graph_charger_details));
        secHeader.setTextColor(cTextTert);
        secHeader.setTextSize(12);
        mContent.addView(secHeader, infoLP());

        // AC onboard charger info
        TextView onbdPlug = newInfoLabel(getString(R.string.graph_ac_plug_onboard, "--"));
        onbdPlug.setTag("chrg_onbd_plug");
        mContent.addView(onbdPlug, infoLP());

        TextView onbdVolt = newInfoLabel(getString(R.string.graph_ac_input_voltage, "--"));
        onbdVolt.setTag("chrg_ac_volt");
        mContent.addView(onbdVolt, infoLP());

        TextView onbdCrnt = newInfoLabel(getString(R.string.graph_ac_input_current, "--"));
        onbdCrnt.setTag("chrg_ac_crnt");
        mContent.addView(onbdCrnt, infoLP());

        // DC off-board charger info
        TextView offbdPlug = newInfoLabel(getString(R.string.graph_dc_plug_offboard, "--"));
        offbdPlug.setTag("chrg_offbd_plug");
        mContent.addView(offbdPlug, infoLP());

        // BMS limits
        TextView optCrnt = newInfoLabel(getString(R.string.graph_bms_optimal_current, "--"));
        optCrnt.setTag("chrg_opt_crnt");
        mContent.addView(optCrnt, infoLP());

        TextView stopReason = newInfoLabel(getString(R.string.graph_charge_stop_reason, "--"));
        stopReason.setTag("chrg_stop_reason");
        mContent.addView(stopReason, infoLP());

        TextView altCrnt = newInfoLabel(getString(R.string.graph_alternating_current, "--"));
        altCrnt.setTag("chrg_alt_crnt");
        mContent.addView(altCrnt, infoLP());

        // Session energy
        TextView sessionEnergy = newInfoLabel(getString(R.string.graph_session_energy, "--"));
        sessionEnergy.setTag("chrg_session_energy");
        mContent.addView(sessionEnergy, infoLP());

        // Scheduled charging
        TextView schedInfo = newInfoLabel(getString(R.string.graph_scheduled_off));
        schedInfo.setTag("chrg_schedule");
        mContent.addView(schedInfo, infoLP());
    }

    // ==================== Charge History (from Cloud) ====================

    private void buildChargeHistory() {
        com.emegelauncher.vehicle.SaicCloudManager cloud = new com.emegelauncher.vehicle.SaicCloudManager(this);

        // Check if currently charging — disable tab
        String chrgStsStr = mVehicle.getPropertyValue(YFVehicleProperty.BMS_CHRG_STS);
        int chrgSts = 0;
        try { chrgSts = (int) Float.parseFloat(chrgStsStr); } catch (Exception ignored) {}
        boolean isCharging = chrgSts == 1 || chrgSts == 2;

        if (isCharging) {
            TextView msg = newInfoLabel(getString(R.string.charge_history_while_charging));
            msg.setTextColor(C_ORANGE);
            mContent.addView(msg, infoLP());
            return;
        }

        // Auto-refresh from cloud in background (rebuild tab once with new data)
        if (cloud.isLoggedIn() && !mChargeHistoryRefreshed) {
            mChargeHistoryRefreshed = true;
            cloud.queryCharging((ok, msg) -> {
                if (ok) runOnUiThread(() -> {
                    if (mCurrentTab == 2) switchTab(2);
                });
            });
        }

        // Refresh button — fetches latest from cloud
        TextView refreshBtn = new TextView(this);
        refreshBtn.setText(getString(R.string.charge_history_refresh));
        refreshBtn.setTextSize(15);
        refreshBtn.setTextColor(C_BLUE);
        refreshBtn.setPadding(20, 14, 20, 14);
        refreshBtn.setBackgroundColor(cCard);
        refreshBtn.setGravity(android.view.Gravity.CENTER);
        refreshBtn.setOnClickListener(v -> {
            refreshBtn.setText(getString(R.string.charge_history_refreshing));
            refreshBtn.setTextColor(cTextTert);
            mChargeHistoryRefreshed = true; // prevent auto-refresh loop on rebuild
            cloud.queryCharging((ok, msg) -> runOnUiThread(() -> {
                if (ok) {
                    int count = cloud.getChargeHistory().length();
                    android.widget.Toast.makeText(this, getString(R.string.charge_hist_cloud_ok, count), android.widget.Toast.LENGTH_SHORT).show();
                    switchTab(2);
                } else {
                    refreshBtn.setText(getString(R.string.charge_history_refresh));
                    refreshBtn.setTextColor(C_BLUE);
                    android.widget.Toast.makeText(this, "Cloud: " + msg, android.widget.Toast.LENGTH_SHORT).show();
                }
            }));
        });
        mContent.addView(refreshBtn, infoLP());

        addDivider();

        // Export all sessions button
        TextView exportAllBtn = new TextView(this);
        exportAllBtn.setText(getString(R.string.charge_history_export));
        exportAllBtn.setTextSize(14);
        exportAllBtn.setTextColor(C_TEAL);
        exportAllBtn.setPadding(20, 10, 20, 10);
        exportAllBtn.setBackgroundColor(cCard);
        exportAllBtn.setGravity(android.view.Gravity.CENTER);
        exportAllBtn.setOnClickListener(v -> exportChargeHistory(cloud));
        mContent.addView(exportAllBtn, infoLP());

        addDivider();

        // Load stored sessions from SharedPreferences
        org.json.JSONArray history = cloud.getChargeHistory();
        if (history.length() == 0) {
            TextView empty = newInfoLabel(getString(R.string.charge_history_empty));
            empty.setTextColor(cTextTert);
            mContent.addView(empty, infoLP());
            return;
        }

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault());

        for (int i = 0; i < history.length(); i++) {
            try {
                org.json.JSONObject sess = history.getJSONObject(i);
                long startTime = sess.optLong("startTime", 0) * 1000; // seconds to ms
                long endTime = sess.optLong("endTime", 0) * 1000;
                int duration = sess.optInt("chargingDuration", 0);
                int chargingType = sess.optInt("chargingType", 0);
                int soc = sess.optInt("bmsPackSOCDsp", 0);
                int range = sess.optInt("bmsEstdElecRng", 0);
                int addedRange = sess.optInt("chrgngAddedElecRng", 0);
                double powerSinceCharge = sess.optDouble("powerUsageSinceLastCharge", 0);
                double powerOfDay = sess.optDouble("powerUsageOfDay", 0);
                int targetSoc = sess.optInt("bmsOnBdChrgTrgtSOCDspCmd", 0);
                int stopReason = sess.optInt("bmsChrgSpRsn", 0);
                double packVol = sess.optDouble("bmsPackVol", 0);
                double packCrnt = sess.optDouble("bmsPackCrnt", 0);
                int totalCapacity = sess.optInt("totalBatteryCapacity", 0);
                int realTimePower = sess.optInt("realTimePower", 0);

                String typeStr = chargingType == 2 ? "DC" : chargingType == 1 ? "AC" : "?";
                String dateStr = startTime > 0 ? sdf.format(new java.util.Date(startTime)) : "--";
                int durMin = duration > 0 ? duration : (endTime > startTime ? (int)((endTime - startTime) / 60000) : 0);

                // Session card
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundColor(cCard);
                card.setPadding(16, 12, 16, 12);
                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
                cardLp.setMargins(0, 6, 0, 6);
                card.setLayoutParams(cardLp);

                // Header: date + type
                TextView header = new TextView(this);
                header.setText(dateStr + " | " + typeStr);
                header.setTextSize(16);
                header.setTextColor(cText);
                header.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
                card.addView(header);

                // Details
                StringBuilder details = new StringBuilder();
                details.append(getString(R.string.charge_hist_soc, String.valueOf(soc)));
                if (targetSoc > 0) {
                    int displayTarget = Math.min(100, (targetSoc + 3) * 10);
                    details.append(" \u2192 ").append(getString(R.string.charge_hist_target, displayTarget));
                }
                details.append("\n");
                if (durMin > 0) details.append(getString(R.string.charge_hist_duration, durMin > 60 ? (durMin/60) + "h " + (durMin%60) + "min" : durMin + " min")).append("\n");
                if (addedRange > 0) details.append(getString(R.string.charge_hist_range_added, addedRange)).append("\n");
                if (range > 0) details.append(getString(R.string.charge_hist_range, range)).append("\n");
                if (packVol > 0) details.append(getString(R.string.charge_hist_pack, String.format("%.0fV / %.1fA", packVol, Math.abs(packCrnt)))).append("\n");
                if (realTimePower > 0) details.append(getString(R.string.charge_hist_power, String.format("%.1f kW", realTimePower / 10.0))).append("\n");
                if (powerSinceCharge > 0) details.append(getString(R.string.charge_hist_energy_since, String.format("%.1f kWh", powerSinceCharge / 10.0))).append("\n");
                if (powerOfDay > 0) details.append(getString(R.string.charge_hist_energy_today, String.format("%.1f kWh", powerOfDay / 10.0))).append("\n");
                if (totalCapacity > 0) details.append(getString(R.string.charge_hist_capacity, String.format("%.1f kWh", totalCapacity / 10.0))).append("\n");
                if (stopReason > 0) details.append(getString(R.string.charge_hist_stop_reason, String.valueOf(stopReason))).append("\n");

                TextView detailsTv = new TextView(this);
                detailsTv.setText(details.toString().trim());
                detailsTv.setTextSize(13);
                detailsTv.setTextColor(cTextSec);
                detailsTv.setPadding(0, 4, 0, 0);
                card.addView(detailsTv);

                mContent.addView(card);
            } catch (Exception e) {
                Log.e(TAG, "Parse charge history item " + i, e);
            }
        }

        // SOH estimation from latest session
        try {
            org.json.JSONObject latest = history.getJSONObject(0);
            int totalCap = latest.optInt("totalBatteryCapacity", 0);
            if (totalCap > 0) {
                float capKwh = totalCap / 10.0f;
                float soh = capKwh / 70.0f * 100f; // 70 kWh nominal
                TextView sohLabel = newInfoLabel(getString(R.string.charge_hist_soh, soh, capKwh));
                sohLabel.setTextColor(soh > 90 ? C_GREEN : soh > 80 ? C_ORANGE : C_RED);
                mContent.addView(sohLabel, infoLP());
            }
        } catch (Exception ignored) {}
    }

    private void exportChargeHistory(com.emegelauncher.vehicle.SaicCloudManager cloud) {
        org.json.JSONArray history = cloud.getChargeHistory();
        if (history.length() == 0) {
            android.widget.Toast.makeText(this, getString(R.string.charge_history_empty), android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        // Use same storage picker pattern
        java.util.List<StorageVol> volumes = findAllVolumes();
        if (volumes.isEmpty()) {
            android.widget.Toast.makeText(this, getString(R.string.no_storage_found), android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[volumes.size()];
        for (int i = 0; i < volumes.size(); i++) {
            StorageVol vi = volumes.get(i);
            long free = vi.path.getFreeSpace() / (1024 * 1024);
            names[i] = vi.description + "\n" + vi.path.getAbsolutePath() + " (" + free + " MB free)";
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_storage))
            .setItems(names, (d, idx) -> {
                try {
                    String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date());
                    java.io.File dest = new java.io.File(volumes.get(idx).path, "charge_history_" + ts + ".json");
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(dest);
                    fos.write(history.toString(2).getBytes("UTF-8"));
                    fos.close();
                    android.widget.Toast.makeText(this, getString(R.string.exported_to, dest.getAbsolutePath()), android.widget.Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    android.widget.Toast.makeText(this, getString(R.string.export_failed), android.widget.Toast.LENGTH_SHORT).show();
                }
            }).show();
    }

    // ==================== Battery Health ====================

    private void buildHealth() {
        // Disclaimer
        TextView disclaimer = newInfoLabel(getString(R.string.graph_health_disclaimer));
        disclaimer.setTextColor(C_ORANGE);
        disclaimer.setTextSize(13);
        disclaimer.setPadding(20, 20, 20, 20);
        mContent.addView(disclaimer, infoLP());

        addDivider();

        // SOH estimate
        float soh = mHealthTracker.getEstimatedSoh();
        float cap = mHealthTracker.getEstimatedCapacity();

        ArcGaugeView sohGauge = newGauge(getString(R.string.graph_est_soh), "%", 100,
            soh >= 90 ? C_GREEN : soh >= 75 ? C_ORANGE : C_RED);
        if (soh > 0) sohGauge.setValue(soh);
        mContent.addView(sohGauge, new LinearLayout.LayoutParams(-1, 280));

        TextView capLabel = newInfoLabel(getString(R.string.graph_est_capacity,
            cap > 0 ? String.format("%.1f kWh", cap) : getString(R.string.graph_no_data_yet)));
        capLabel.setTag("health_capacity");
        mContent.addView(capLabel, infoLP());

        TextView sessionsCount = newInfoLabel(getString(R.string.graph_sessions_recorded,
            mHealthTracker.getSessions().length()));
        mContent.addView(sessionsCount, infoLP());

        // Tracking status
        if (mHealthTracker.isCurrentlyTracking()) {
            TextView tracking = newInfoLabel(getString(R.string.graph_tracking_session));
            tracking.setTextColor(C_GREEN);
            tracking.setTag("health_tracking");
            mContent.addView(tracking, infoLP());

            TextView energySoFar = newInfoLabel(getString(R.string.graph_energy_accumulated,
                String.format("%.2f kWh", mHealthTracker.getSessionEnergySoFar())));
            energySoFar.setTag("health_energy_sofar");
            mContent.addView(energySoFar, infoLP());
        }

        // Resting voltage analysis
        addDivider();
        TextView voltHeader = newInfoLabel(getString(R.string.graph_resting_voltage));
        voltHeader.setTextColor(cTextTert);
        voltHeader.setTextSize(12);
        mContent.addView(voltHeader, infoLP());

        TextView restVolt = newInfoLabel(getString(R.string.graph_pack_voltage_val, "--"));
        restVolt.setTag("health_rest_volt");
        mContent.addView(restVolt, infoLP());

        TextView restSoc = newInfoLabel(getString(R.string.graph_bms_soc, "--"));
        restSoc.setTag("health_rest_soc");
        mContent.addView(restSoc, infoLP());

        TextView restCrnt = newInfoLabel(getString(R.string.graph_standby_current, "--"));
        restCrnt.setTag("health_rest_crnt");
        mContent.addView(restCrnt, infoLP());

        TextView socGap = newInfoLabel(getString(R.string.graph_soc_gap, "--"));
        socGap.setTag("health_soc_gap");
        mContent.addView(socGap, infoLP());

        // Session history
        addDivider();
        TextView histHeader = newInfoLabel(getString(R.string.graph_session_history));
        histHeader.setTextColor(cTextTert);
        histHeader.setTextSize(12);
        mContent.addView(histHeader, infoLP());

        JSONArray sessions = mHealthTracker.getSessions();
        if (sessions.length() == 0) {
            mContent.addView(newInfoLabel(getString(R.string.graph_no_sessions_hint)), infoLP());
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
            // Show last 10 sessions, newest first
            for (int i = sessions.length() - 1; i >= Math.max(0, sessions.length() - 10); i--) {
                try {
                    JSONObject s = sessions.getJSONObject(i);
                    String date = sdf.format(new Date(s.getLong("ts")));
                    String line = getString(R.string.graph_session_line,
                        date,
                        String.valueOf(s.getDouble("s0")),
                        String.valueOf(s.getDouble("s1")),
                        String.valueOf(s.getDouble("e")),
                        String.valueOf(s.getDouble("cap")),
                        String.valueOf(s.getDouble("soh")));
                    TextView tv = newInfoLabel(line);
                    tv.setTextSize(11);
                    mContent.addView(tv, infoLP());
                } catch (Exception ignored) {}
            }
        }
    }

    // ==================== Tires ====================

    private void buildTires() {
        mTireDiagram = new TireDiagramView(this);
        mTireDiagram.setTextColor(cText);
        mTireDiagram.setLabelColor(cTextSec);
        mTireDiagram.setCarColor(cDivider);
        mContent.addView(mTireDiagram, new LinearLayout.LayoutParams(-1, 600));
    }

    // ==================== Climate ====================

    private void buildClimate() {
        // No temperature chart — getDrvTemp only returns HVAC set temp, not measured cabin temp
        // Cabin temp sensor exists but is only accessible via TBox (cloud), not head unit

        // Temp info labels
        LinearLayout row = newRow();
        row.setPadding(20, 12, 20, 12);
        TextView insideLabel = newInfoLabel(getString(R.string.graph_hvac_set, "--"));
        TextView outsideLabel = newInfoLabel(getString(R.string.graph_outside_temp, "--"));
        row.addView(insideLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(outsideLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        mContent.addView(row);
        insideLabel.setTag("climate_inside");
        outsideLabel.setTag("climate_outside");

        // Air Quality section
        addDivider();
        TextView aqHeader = newInfoLabel(getString(R.string.graph_air_quality));
        aqHeader.setTextColor(cTextTert);
        aqHeader.setTextSize(12);
        mContent.addView(aqHeader, infoLP());

        // PM2.5 chart removed — car has dust filter but no active PM2.5 sensor (CAL_AQS=00, value=253=sentinel)

        // Air quality detail labels
        TextView pm25InLabel = newInfoLabel(getString(R.string.graph_pm25_inside, "--"));
        pm25InLabel.setTag("aq_pm25_in");
        mContent.addView(pm25InLabel, infoLP());

        TextView pm25OutLabel = newInfoLabel(getString(R.string.graph_pm25_outside, "--"));
        pm25OutLabel.setTag("aq_pm25_out");
        mContent.addView(pm25OutLabel, infoLP());

        TextView pm25FilterLabel = newInfoLabel(getString(R.string.graph_pm25_filter, "--"));
        pm25FilterLabel.setTag("aq_pm25_filter");
        mContent.addView(pm25FilterLabel, infoLP());

        TextView aqsLabel = newInfoLabel(getString(R.string.graph_aqs, "--"));
        aqsLabel.setTag("aq_aqs");
        mContent.addView(aqsLabel, infoLP());

        TextView ionLabel = newInfoLabel(getString(R.string.graph_ionizer, "--"));
        ionLabel.setTag("aq_ionizer");
        mContent.addView(ionLabel, infoLP());

        TextView anionLabel = newInfoLabel(getString(R.string.graph_anion_status, "--"));
        anionLabel.setTag("aq_anion");
        mContent.addView(anionLabel, infoLP());

        TextView filterLifeLabel = newInfoLabel(getString(R.string.graph_filter_life, "--"));
        filterLifeLabel.setTag("aq_filter_life");
        mContent.addView(filterLifeLabel, infoLP());

        TextView filterLevelLabel = newInfoLabel(getString(R.string.graph_filter_usage, "--"));
        filterLevelLabel.setTag("aq_filter_level");
        mContent.addView(filterLevelLabel, infoLP());

        TextView airPurifierLabel = newInfoLabel(getString(R.string.graph_air_purifier, "--"));
        airPurifierLabel.setTag("aq_purifier_sw");
        mContent.addView(airPurifierLabel, infoLP());

        TextView autoRecircLabel = newInfoLabel(getString(R.string.graph_auto_recirc, "--"));
        autoRecircLabel.setTag("aq_auto_recirc");
        mContent.addView(autoRecircLabel, infoLP());
    }

    // ==================== Trip ====================

    private com.emegelauncher.vehicle.TripRecorder mTripRecorder;

    private void buildTrip() {
        mTripRecorder = com.emegelauncher.vehicle.TripRecorder.getInstance(this);

        // Record button
        TextView recBtn = new TextView(this);
        recBtn.setTag("trip_rec_btn");
        recBtn.setText(mTripRecorder.isRecording() ? "\u23F9  STOP RECORDING" : "\u23FA  START RECORDING");
        recBtn.setTextSize(16);
        recBtn.setTextColor(mTripRecorder.isRecording() ? cText : ThemeHelper.accentRed(this));
        recBtn.setBackgroundColor(mTripRecorder.isRecording() ? ThemeHelper.accentRed(this) : cCard);
        recBtn.setGravity(android.view.Gravity.CENTER);
        recBtn.setPadding(20, 16, 20, 16);
        recBtn.setOnClickListener(v -> {
            if (mTripRecorder.isRecording()) {
                mTripRecorder.stop();
            } else {
                mTripRecorder.start();
            }
            switchTab(6); // rebuild
        });
        mContent.addView(recBtn, infoLP());

        // Recording status
        TextView recStatus = newInfoLabel(mTripRecorder.isRecording()
            ? getString(R.string.recording_points, mTripRecorder.getPointCount())
            : getString(R.string.not_recording));
        recStatus.setTag("trip_rec_status");
        mContent.addView(recStatus, infoLP());

        // Trip summary (live or last trip)
        TextView summary = newInfoLabel(mTripRecorder.getSummary());
        summary.setTag("trip_summary");
        mContent.addView(summary, infoLP());

        addDivider();

        // Live data
        mOdometer = newInfoLabel(getString(R.string.graph_odometer, "--"));
        mContent.addView(mOdometer, infoLP());

        mAvgConsumption = newInfoLabel(getString(R.string.graph_avg_consumption, "--"));
        mContent.addView(mAvgConsumption, infoLP());

        mTotalConsumed = newInfoLabel(getString(R.string.trip_instant, "--"));
        mContent.addView(mTotalConsumed, infoLP());

        mRegenEnergy = newInfoLabel(getString(R.string.trip_power, "--"));
        mContent.addView(mRegenEnergy, infoLP());

        mRegenRange = newInfoLabel("SOC: --");
        mContent.addView(mRegenRange, infoLP());

        // Stored trips list
        java.util.List<com.emegelauncher.vehicle.TripRecorder.TripInfo> storedTrips = mTripRecorder.getStoredTrips();
        if (!storedTrips.isEmpty()) {
            addDivider();
            TextView histHeader = newInfoLabel("STORED TRIPS (" + storedTrips.size() + "/" + 5 + ")");
            histHeader.setTextColor(cTextTert);
            histHeader.setTextSize(12);
            mContent.addView(histHeader, infoLP());

            for (com.emegelauncher.vehicle.TripRecorder.TripInfo trip : storedTrips) {
                // Trip summary row
                TextView tripRow = newInfoLabel(trip.getSummary());
                tripRow.setTextSize(12);
                mContent.addView(tripRow, infoLP());

                // Export buttons for this trip
                LinearLayout exportRow = new LinearLayout(this);
                exportRow.setOrientation(LinearLayout.HORIZONTAL);
                exportRow.setPadding(0, 0, 0, 8);

                final String tripName = trip.filename;

                TextView gpxBtn = new TextView(this);
                gpxBtn.setText("GPX");
                gpxBtn.setTextSize(13);
                gpxBtn.setTextColor(ThemeHelper.accentBlue(this));
                gpxBtn.setPadding(16, 8, 16, 8);
                gpxBtn.setOnClickListener(v -> exportStoredTrip(tripName, "gpx"));
                exportRow.addView(gpxBtn);

                TextView jsonBtn = new TextView(this);
                jsonBtn.setText("JSON");
                jsonBtn.setTextSize(13);
                jsonBtn.setTextColor(ThemeHelper.accentTeal(this));
                jsonBtn.setPadding(16, 8, 16, 8);
                jsonBtn.setOnClickListener(v -> exportStoredTrip(tripName, "json"));
                exportRow.addView(jsonBtn);

                TextView kmlBtn = new TextView(this);
                kmlBtn.setText("KML");
                kmlBtn.setTextSize(13);
                kmlBtn.setTextColor(ThemeHelper.accentOrange(this));
                kmlBtn.setPadding(16, 8, 16, 8);
                kmlBtn.setOnClickListener(v -> exportStoredTrip(tripName, "kml"));
                exportRow.addView(kmlBtn);

                mContent.addView(exportRow, infoLP());
            }
        }
    }

    private void exportStoredTrip(String tripName, String format) {
        java.util.List<StorageVol> volumes = findAllVolumes();
        if (volumes.isEmpty()) {
            android.widget.Toast.makeText(this, getString(R.string.no_storage_found), android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[volumes.size()];
        for (int i = 0; i < volumes.size(); i++) {
            StorageVol vi = volumes.get(i);
            long free = vi.path.getFreeSpace() / (1024 * 1024);
            names[i] = vi.description + "\n" + vi.path.getAbsolutePath() + " (" + free + " MB free)";
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_storage))
            .setItems(names, (d, idx) -> {
                java.io.File result = mTripRecorder.exportStoredTrip(tripName, format, volumes.get(idx).path);
                if (result != null) {
                    android.widget.Toast.makeText(this, getString(R.string.exported_to, result.getAbsolutePath()), android.widget.Toast.LENGTH_LONG).show();
                } else {
                    android.widget.Toast.makeText(this, getString(R.string.export_failed), android.widget.Toast.LENGTH_SHORT).show();
                }
            }).show();
    }

    private static class StorageVol {
        java.io.File path;
        String description;
        StorageVol(java.io.File path, String description) { this.path = path; this.description = description; }
    }

    private java.util.List<StorageVol> findAllVolumes() {
        java.util.List<StorageVol> results = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        try {
            android.os.storage.StorageManager sm =
                (android.os.storage.StorageManager) getSystemService(STORAGE_SERVICE);
            java.lang.reflect.Method getVolumes = sm.getClass().getMethod("getVolumeList");
            Object[] volumes = (Object[]) getVolumes.invoke(sm);
            if (volumes != null) {
                for (Object vol : volumes) {
                    try {
                        java.io.File path = (java.io.File) vol.getClass().getMethod("getPathFile").invoke(vol);
                        String desc = (String) vol.getClass().getMethod("getDescription", android.content.Context.class).invoke(vol, this);
                        boolean removable = false;
                        try { removable = (boolean) vol.getClass().getMethod("isRemovable").invoke(vol); }
                        catch (Exception ignored) {}
                        if (path != null && path.exists()) {
                            String canon = path.getCanonicalPath();
                            if (!seen.contains(canon)) {
                                seen.add(canon);
                                String label = (desc != null ? desc : path.getName());
                                if (removable) label += " (USB)";
                                results.add(new StorageVol(path, label));
                                if (removable) {
                                    java.io.File mediaRw = new java.io.File("/mnt/media_rw/" + path.getName());
                                    if (mediaRw.exists() && mediaRw.isDirectory()) {
                                        String mrc = mediaRw.getCanonicalPath();
                                        if (!seen.contains(mrc)) {
                                            seen.add(mrc);
                                            results.add(new StorageVol(mediaRw, label + " [media_rw]"));
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        java.io.File mediaRw = new java.io.File("/mnt/media_rw");
        if (mediaRw.exists()) {
            java.io.File[] children = mediaRw.listFiles();
            if (children != null) {
                for (java.io.File f : children) {
                    if (!f.isDirectory()) continue;
                    try {
                        String canon = f.getCanonicalPath();
                        if (!seen.contains(canon)) {
                            seen.add(canon);
                            results.add(new StorageVol(f, f.getName() + " (media_rw)"));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return results;
    }

    // ==================== Polling ====================

    private void startPolling() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                updateCurrentTab();
                mHandler.postDelayed(this, mCurrentTab == 0 ? 500 : 2000);
            }
        });
    }

    private void updateCurrentTab() {
        try {
            switch (mCurrentTab) {
                case 0: updateDashboard(); break;
                case 1: updateEnergy(); break;
                case 2: break; // Charge history: static, no polling
                case 3: updateHealth(); break;
                case 4: updateTires(); break;
                case 5: updateClimate(); break;
                case 6: updateTrip(); break;
                case 7: updateGMeter(); break;
            }
            // Always track health in background
            trackHealth();
        } catch (Exception e) {
            Log.e(TAG, "Update error", e);
        }
    }

    private void updateDashboard() {
        if (mSpeedGauge == null) return;

        // Speed: SAIC condition → VHAL
        float speed = readSaicFloat("condition", "getCarSpeed");
        if (speed == 0) speed = readFloat(YFVehicleProperty.PERF_VEHICLE_SPEED);
        mSpeedGauge.setValue(speed);

        // SOC: SAIC charging → VHAL display SOC
        float soc = readSaicFloat("charging", "getCurrentElectricQuantity");
        if (soc == 0) soc = readFloat(YFVehicleProperty.BMS_PACK_SOC_DSP);
        mSocGauge.setValue(soc);

        // Consumption gauge (RPM always 0 on Marvel R)
        long now = System.currentTimeMillis();
        float speedMs = speed / 3.6f;
        float packV = readFloat(YFVehicleProperty.BMS_PACK_VOL);
        float packI = readFloat(YFVehicleProperty.BMS_PACK_CRNT);
        float powerKw = packV * packI / 1000f;
        // Instant consumption from power/speed (independent of VHAL)
        float instantCons = speed > 1 ? powerKw / speed * 100f : 0;
        if (mPrevSpeedTimeMs > 0) {
            float dt = (now - mPrevSpeedTimeMs) / 1000f;
            if (dt > 0 && dt < 5f && speed > 5) {
                mEnergyAccumKwh += powerKw * dt / 3600.0;
                mDistanceAccumKm += speed * dt / 3600.0;
            }
        }
        float displayAvg = mDistanceAccumKm > 0.05 ? (float)(mEnergyAccumKwh / mDistanceAccumKm * 100.0) : 0;
        mRpmGauge.setValue(instantCons);
        mRpmGauge.setSecondaryValue(displayAvg);
        mRpmGauge.setSecondaryColor(C_TEAL);
        mRpmGauge.setLabel(String.format("%.1f inst", instantCons));
        mRpmGauge.setLabelColor(C_ORANGE);
        mRpmGauge.setLabel2(String.format("%.1f avg", displayAvg), C_TEAL);

        // Eco Score — freeze when stopped
        float ecoD = mEcoScoreAvg;
        if (speed > 3) {
            float ecoInstant = 100f;
            float consRef = displayAvg > 0.1f ? displayAvg : instantCons;
            if (consRef > 14) ecoInstant -= (consRef - 14) * 2.5f;
            if (powerKw > 30) ecoInstant -= (powerKw - 30) * 1.0f;
            if (powerKw < -5) ecoInstant += Math.min(10, Math.abs(powerKw + 5) * 0.5f);
            if (speed > 110) ecoInstant -= (speed - 110) * 1.5f;
            ecoInstant = Math.max(0, Math.min(100, ecoInstant));
            if (!mEcoScoreInit) { mEcoScoreAvg = ecoInstant; mEcoScoreInit = true; }
            else { mEcoScoreAvg = mEcoScoreAvg * 0.97f + ecoInstant * 0.03f; }
            ecoD = Math.max(0, Math.min(100, mEcoScoreAvg));
        }
        mEfficiencyGauge.setValue(ecoD);
        String indicator;
        int indicatorColor;
        if (speed <= 3) {
            indicator = getString(R.string.eco_steady);
            indicatorColor = 0xFF636366;
        } else if (powerKw > 50) {
            indicator = getString(R.string.eco_hard_accel);
            indicatorColor = 0xFFFF3B30;
        } else if (powerKw > 30) {
            indicator = getString(R.string.eco_accelerating);
            indicatorColor = 0xFFFF9500;
        } else if (powerKw < -10) {
            indicator = getString(R.string.eco_strong_regen);
            indicatorColor = 0xFF30D158;
        } else if (powerKw < -5) {
            indicator = getString(R.string.eco_regen);
            indicatorColor = 0xFF26A69A;
        } else if (speed > 110) {
            indicator = getString(R.string.eco_high_speed);
            indicatorColor = 0xFFFF9500;
        } else if (speed > 3 && Math.abs(powerKw) < 5) {
            indicator = getString(R.string.eco_coasting);
            indicatorColor = 0xFF30D158;
        } else {
            indicator = getString(R.string.eco_steady);
            indicatorColor = 0xFF636366;
        }
        mEfficiencyGauge.setLabel(indicator);
        mEfficiencyGauge.setLabelColor(indicatorColor);
        if (ecoD >= 70) mEfficiencyGauge.setFgColor(0xFF30D158);
        else if (ecoD >= 40) mEfficiencyGauge.setFgColor(0xFFFF9500);
        else mEfficiencyGauge.setFgColor(0xFFFF3B30);

        // Energy flow: V*I/1000 = kW, positive = discharge, negative = regen
        mEnergyFlowChart.addPoint(powerKw);

        // Gear: SAIC condition → VHAL
        int gearVal = (int) readSaicFloat("condition", "getCarGear");
        if (gearVal == 0) gearVal = (int) readFloat(YFVehicleProperty.SENSOR_GEAR_STS);
        mGearText.setText(getString(R.string.graph_gear, decodeGear(gearVal)));

        // Range: display value only (SAIC or cluster) — NO fallback to BMS
        float rangeSaic = readSaicFloat("charging", "getCurrentEnduranceMileage");
        String clstrRange = mVehicle.getPropertyValue(YFVehicleProperty.CLSTR_ELEC_RNG);
        String bmsRange = mVehicle.getPropertyValue(YFVehicleProperty.BMS_ESTD_ELEC_RNG);
        String displayRange = null;
        if (rangeSaic > 0) displayRange = fmt(rangeSaic);
        else if (isValidVal(clstrRange)) displayRange = clstrRange;

        String rangeLabel;
        if (displayRange != null) {
            if (isValidVal(bmsRange)) {
                rangeLabel = getString(R.string.graph_range_bms, displayRange, bmsRange);
            } else {
                rangeLabel = getString(R.string.graph_range, displayRange);
            }
        } else if (isValidVal(bmsRange)) {
            rangeLabel = getString(R.string.graph_range_bms_only, bmsRange);
        } else {
            rangeLabel = getString(R.string.graph_range_none);
        }
        mRangeText.setText(rangeLabel);

        // G-force on dashboard (from speed derivative — VHAL sensors have VALID=0)
        if (mDashGChart != null) {
            float longG = 0;
            if (mPrevSpeedTimeMs > 0) {
                float dtG = (now - mPrevSpeedTimeMs) / 1000f;
                if (dtG > 0.1f && dtG < 5f) {
                    longG = (speedMs - mPrevSpeedMs) / dtG / 9.81f;
                }
            }
            mDashGChart.addPoint(longG);
        }
        mPrevSpeedMs = speedMs;
        mPrevSpeedTimeMs = now;

        // Drive mode
        int drvMode = (int) readFloat(YFVehicleProperty.SENSOR_ELECTRIC_DRIVER_MODE);
        updateTaggedLabel("dash_drive_mode", getString(R.string.graph_drive_mode, decodeDriveMode(drvMode)));
    }

    private String decodeDriveMode(int raw) {
        switch (raw) {
            case 0: return getString(R.string.graph_drive_eco);
            case 1: return getString(R.string.graph_drive_normal);
            case 2: return getString(R.string.graph_drive_sport);
            case 6: return getString(R.string.graph_drive_winter);
            default: return getString(R.string.graph_drive_unknown, raw);
        }
    }

    private boolean isValidVal(String v) {
        return v != null && !v.equals("N/A") && !v.equals("Connecting...") && !v.equals("0") && !v.equals("0.00");
    }

    private void updateEnergy() {
        if (mSocChart == null) return;
        mSocChart.addPoint(readFloat(YFVehicleProperty.BMS_PACK_SOC_DSP));
        mSocBmsChart.addPoint(readFloat(YFVehicleProperty.BMS_PACK_SOC));
        mVoltChart.addPoint(readFloat(YFVehicleProperty.BMS_PACK_VOL));
        mCurrentChart.addPoint(readFloat(YFVehicleProperty.BMS_PACK_CRNT));
        // Consumption from power/speed (same calc as dashboard)
        float eSpeed = readSaicFloat("condition", "getCarSpeed");
        if (eSpeed == 0) eSpeed = readFloat(YFVehicleProperty.PERF_VEHICLE_SPEED);
        float ePower = readFloat(YFVehicleProperty.BMS_PACK_VOL) * readFloat(YFVehicleProperty.BMS_PACK_CRNT) / 1000f;
        mConsumptionChart.addPoint(eSpeed > 1 ? ePower / eSpeed * 100f : 0);
    }

    private void updateCharging() {
        if (mChargeRateGauge == null) return;

        // Charging status: try SAIC service → VHAL
        int chrgSts = (int) readSaicFloat("charging", "getChargingStatus");
        if (chrgSts == 0) chrgSts = (int) readFloat(YFVehicleProperty.BMS_CHRG_STS);
        boolean isCharging = (chrgSts == 1 || chrgSts == 2);

        if (isCharging) {
            updateChargingLive(chrgSts);
        } else {
            updateChargingStored(chrgSts);
        }
    }

    private void updateChargingLive(int chrgSts) {
        float packVolt = readFloat(YFVehicleProperty.BMS_PACK_VOL);
        float packCrnt = readFloat(YFVehicleProperty.BMS_PACK_CRNT);
        float chargeRate = readFloat(YFVehicleProperty.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE);
        float acVolt = readFloat(YFVehicleProperty.ONBD_CHRG_ALT_CRNT_LNPT_VOL);
        float acCrnt = readFloat(YFVehicleProperty.ONBD_CHRG_ALT_CRNT_LNPT_CRNT);
        // SAIC actual charging current (may be more accurate)
        float saicActualCrnt = readSaicFloat("charging", "getActualChargingCurrent");
        float saicExpectedCrnt = readSaicFloat("charging", "getExpectedCurrent");
        float socRaw = readFloat(YFVehicleProperty.BMS_PACK_SOC);
        float socDsp = readSaicFloat("charging", "getCurrentElectricQuantity");
        if (socDsp == 0) socDsp = readFloat(YFVehicleProperty.BMS_PACK_SOC_DSP);
        float optCrnt = readFloat(YFVehicleProperty.BMS_CHRG_OPT_CRNT);
        float timeRemain = readFloat(YFVehicleProperty.CHRGNG_RMNNG_TIME);
        boolean onbdPlug = readFloat(YFVehicleProperty.CCU_ONBD_CHRG_PLUG_ON) > 0;
        boolean offbdPlug = readFloat(YFVehicleProperty.CCU_OFFBD_CHRG_PLUG_ON) > 0;

        float powerKw = chargeRate;
        if (powerKw == 0 && Math.abs(packCrnt) > 1) {
            powerKw = packVolt * Math.abs(packCrnt) / 1000f;
        }

        mChargeRateGauge.setValue(powerKw);
        mChargePowerChart.addPoint(powerKw);
        mChargeCurrentChart.addPoint(Math.abs(packCrnt));
        mChargeVoltChart.addPoint(packVolt);

        mChargeStatus.setText(getString(R.string.graph_status_live, decodeChargeStatus(chrgSts)));
        mChargeTime.setText(getString(R.string.graph_time_remaining, timeRemain >= 1023 ? "N/A" : getString(R.string.graph_time_remaining_min, (int) timeRemain)));

        float targetSoc = readFloat(YFVehicleProperty.BMS_DISCHRG_TRGT_SOC_RESP);
        if (targetSoc <= 0) targetSoc = readFloat(YFVehicleProperty.CHRG_TRGT_SOC);
        // Target SOC mapping: 5=80%, 6=90%, 7=100% → (value+3)*10
        if (targetSoc > 0 && targetSoc <= 10) {
            int displayTarget = (int)((targetSoc + 3) * 10);
            displayTarget = Math.min(100, displayTarget);
            mTargetSoc.setText(getString(R.string.graph_target_soc, displayTarget, (int) targetSoc));
        } else if (targetSoc > 10) {
            mTargetSoc.setText(getString(R.string.graph_target_soc_pct, (int) targetSoc));
        } else {
            mTargetSoc.setText(getString(R.string.graph_target_soc_none));
        }

        mPlugStatus.setText(getString(R.string.graph_plug, readFloat(YFVehicleProperty.EV_CHARGE_PORT_CONNECTED) > 0 ? getString(R.string.graph_connected) : getString(R.string.graph_disconnected)));
        mDoorStatus.setText(getString(R.string.graph_charge_door, decodeDoorStatus((int) readFloat(YFVehicleProperty.BMS_CHRG_DOOR_POS_STS))));

        updateTaggedLabel("chrg_onbd_plug", getString(R.string.graph_ac_plug_onboard, onbdPlug ? getString(R.string.graph_connected) : "No"));
        updateTaggedLabel("chrg_offbd_plug", getString(R.string.graph_dc_plug_offboard, offbdPlug ? getString(R.string.graph_connected) : "No"));
        updateTaggedLabel("chrg_ac_volt", getString(R.string.graph_ac_input_voltage, fmt(acVolt) + " V"));
        updateTaggedLabel("chrg_ac_crnt", getString(R.string.graph_ac_input_current, fmt(acCrnt) + " A"));
        updateTaggedLabel("chrg_opt_crnt", getString(R.string.graph_bms_optimal_current, fmt(optCrnt) + " A") +
            (optCrnt > 0 ? getString(R.string.graph_bms_limits_rate) : ""));
        updateTaggedLabel("chrg_stop_reason", getString(R.string.graph_charge_stop_reason, mVehicle.getPropertyValue(YFVehicleProperty.BMS_CHRG_SP_RSN)));
        updateTaggedLabel("chrg_alt_crnt", getString(R.string.graph_alternating_current, mVehicle.getPropertyValue(YFVehicleProperty.ALTNG_CHRG_CRNT) + " A"));
        updateTaggedLabel("chrg_session_energy", getString(R.string.graph_session_energy,
            String.format("%.2f kWh", mHealthTracker.getSessionEnergySoFar())));

        int stH = (int) readFloat(YFVehicleProperty.RESER_ST_HOUR);
        int stM = (int) readFloat(YFVehicleProperty.RESER_ST_MIN);
        int spH = (int) readFloat(YFVehicleProperty.RESER_SP_HOUR);
        int spM = (int) readFloat(YFVehicleProperty.RESER_SP_MIN);
        int reserCtrl = (int) readFloat(YFVehicleProperty.RESER_CTRL);
        if (reserCtrl > 0) {
            updateTaggedLabel("chrg_schedule", getString(R.string.graph_scheduled_charge, String.format("%02d:%02d → %02d:%02d", stH, stM, spH, spM)));
        } else {
            updateTaggedLabel("chrg_schedule", getString(R.string.graph_scheduled_off));
        }

        // Save snapshot for later viewing
        try {
            JSONObject snap = new JSONObject();
            snap.put("ts", System.currentTimeMillis());
            snap.put("status", chrgSts);
            snap.put("powerKw", Math.round(powerKw * 100) / 100f);
            snap.put("packVolt", Math.round(packVolt * 10) / 10f);
            snap.put("packCrnt", Math.round(packCrnt * 10) / 10f);
            snap.put("acVolt", Math.round(acVolt * 10) / 10f);
            snap.put("acCrnt", Math.round(acCrnt * 10) / 10f);
            snap.put("socRaw", Math.round(socRaw * 10) / 10f);
            snap.put("socDsp", Math.round(socDsp * 10) / 10f);
            snap.put("optCrnt", Math.round(optCrnt * 10) / 10f);
            snap.put("onbdPlug", onbdPlug);
            snap.put("offbdPlug", offbdPlug);
            snap.put("energy", Math.round(mHealthTracker.getSessionEnergySoFar() * 100) / 100f);
            snap.put("startSoc", Math.round(mHealthTracker.getSessionStartSoc() * 10) / 10f);
            mHealthTracker.saveChargeSnapshot(snap);
        } catch (Exception ignored) {}
    }

    private void updateChargingStored(int chrgSts) {
        mChargeStatus.setText(getString(R.string.graph_status, decodeChargeStatus(chrgSts)));

        JSONObject snap = mHealthTracker.getLastChargeSnapshot();
        if (snap == null) {
            mChargeRateGauge.setValue(0);
            mChargeTime.setText(getString(R.string.graph_time_remaining_na));
            mTargetSoc.setText(getString(R.string.graph_target_soc_none));
            mPlugStatus.setText(getString(R.string.graph_plug, getString(R.string.graph_disconnected)));
            mDoorStatus.setText(getString(R.string.graph_charge_door, decodeDoorStatus((int) readFloat(YFVehicleProperty.BMS_CHRG_DOOR_POS_STS))));
            updateTaggedLabel("chrg_session_energy", getString(R.string.graph_no_session));
            return;
        }

        try {
            // Show stored data with timestamp
            long ts = snap.getLong("ts");
            String when = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(new Date(ts));
            mChargeStatus.setText(getString(R.string.graph_last_charge, decodeChargeStatus(snap.getInt("status")), when));
            mChargeRateGauge.setValue((float) snap.getDouble("powerKw"));
            mChargeTime.setText(getString(R.string.graph_time_remaining_ended));

            float startSoc = (float) snap.getDouble("startSoc");
            float endSoc = (float) snap.getDouble("socRaw");
            float socDsp = (float) snap.getDouble("socDsp");
            mTargetSoc.setText(getString(R.string.graph_charged_range, fmt(startSoc), fmt(endSoc), fmt(socDsp)));

            mPlugStatus.setText(getString(R.string.graph_plug, getString(R.string.graph_disconnected)));
            mDoorStatus.setText(getString(R.string.graph_charge_door, decodeDoorStatus((int) readFloat(YFVehicleProperty.BMS_CHRG_DOOR_POS_STS))));

            boolean onbd = snap.optBoolean("onbdPlug", false);
            boolean offbd = snap.optBoolean("offbdPlug", false);
            String chargeType = offbd ? getString(R.string.graph_dc_fast) : (onbd ? getString(R.string.graph_ac_onboard) : getString(R.string.graph_unknown));
            updateTaggedLabel("chrg_onbd_plug", getString(R.string.graph_charge_type, chargeType));
            updateTaggedLabel("chrg_offbd_plug", getString(R.string.graph_dc_plug, offbd ? getString(R.string.graph_was_connected) : "No"));
            updateTaggedLabel("chrg_ac_volt", getString(R.string.graph_ac_input_voltage, snap.optDouble("acVolt", 0) + " V"));
            updateTaggedLabel("chrg_ac_crnt", getString(R.string.graph_ac_input_current, snap.optDouble("acCrnt", 0) + " A"));
            updateTaggedLabel("chrg_opt_crnt", getString(R.string.graph_bms_optimal_current, snap.optDouble("optCrnt", 0) + " A"));
            updateTaggedLabel("chrg_stop_reason", getString(R.string.graph_charge_stop_reason, mVehicle.getPropertyValue(YFVehicleProperty.BMS_CHRG_SP_RSN)));
            updateTaggedLabel("chrg_alt_crnt", getString(R.string.graph_peak_power, snap.optDouble("powerKw", 0)));
            updateTaggedLabel("chrg_session_energy", getString(R.string.graph_session_energy, snap.optDouble("energy", 0) + " kWh"));
            updateTaggedLabel("chrg_schedule", getString(R.string.graph_pack_volt_last, String.valueOf(snap.optDouble("packVolt", 0)), String.valueOf(snap.optDouble("packCrnt", 0))));
        } catch (Exception ignored) {
            mChargeRateGauge.setValue(0);
        }
    }

    private void trackHealth() {
        float chrgSts = readSaicFloat("charging", "getChargingStatus");
        if (chrgSts == 0) chrgSts = readFloat(YFVehicleProperty.BMS_CHRG_STS);
        boolean isCharging = chrgSts == 1 || chrgSts == 2; // 1=AC, 2=DC
        float socRaw = readFloat(YFVehicleProperty.BMS_PACK_SOC);
        float packVolt = readFloat(YFVehicleProperty.BMS_PACK_VOL);
        float packCrnt = readFloat(YFVehicleProperty.BMS_PACK_CRNT);
        mHealthTracker.update(isCharging, socRaw, packVolt, packCrnt);
    }

    private void updateHealth() {
        // Update resting voltage analysis
        float volt = readFloat(YFVehicleProperty.BMS_PACK_VOL);
        float socRaw = readFloat(YFVehicleProperty.BMS_PACK_SOC);
        float socDsp = readFloat(YFVehicleProperty.BMS_PACK_SOC_DSP);
        float crnt = readFloat(YFVehicleProperty.BMS_PACK_CRNT);

        updateTaggedLabel("health_rest_volt", getString(R.string.graph_pack_voltage_val, fmt(volt)));
        updateTaggedLabel("health_rest_soc", getString(R.string.graph_bms_soc, fmt(socRaw)));
        updateTaggedLabel("health_rest_crnt", getString(R.string.graph_standby_current, fmt(crnt)));
        updateTaggedLabel("health_soc_gap", getString(R.string.graph_soc_gap,
            String.format("%.1f%%", socDsp - socRaw)));

        if (mHealthTracker.isCurrentlyTracking()) {
            updateTaggedLabel("health_tracking", getString(R.string.graph_tracking_energy,
                String.format("%.2f kWh", mHealthTracker.getSessionEnergySoFar())));
            updateTaggedLabel("health_energy_sofar", getString(R.string.graph_start_soc_current,
                fmt(mHealthTracker.getSessionStartSoc()), fmt(socRaw)));
        }

        // Update capacity label
        float cap = mHealthTracker.getEstimatedCapacity();
        updateTaggedLabel("health_capacity", getString(R.string.graph_est_capacity,
            cap > 0 ? String.format("%.1f kWh", cap) : getString(R.string.graph_no_data_yet)));
    }

    private void updateTires() {
        if (mTireDiagram == null) return;
        // Raw values are in kPa, convert to bar (/100)
        mTireDiagram.setPressures(
            readFloat(YFVehicleProperty.SENSOR_TIRE_PRESURE_FL) / 100f,
            readFloat(YFVehicleProperty.SENSOR_TIRE_PRESURE_FR) / 100f,
            readFloat(YFVehicleProperty.SENSOR_TIRE_PRESURE_RL) / 100f,
            readFloat(YFVehicleProperty.SENSOR_TIRE_PRESURE_RR) / 100f);
        mTireDiagram.setTemps(
            readFloat(YFVehicleProperty.SENSOR_TIRE_TEMP_FL),
            readFloat(YFVehicleProperty.SENSOR_TIRE_TEMP_FR),
            readFloat(YFVehicleProperty.SENSOR_TIRE_TEMP_RL),
            readFloat(YFVehicleProperty.SENSOR_TIRE_TEMP_RR));
    }

    private void updateClimate() {
        // Temperature chart removed - HVAC set temp only
        // getDrvTemp returns HVAC SET temp (-1 when HVAC off)
        float inside = readServiceFloat("getDriverTemp");
        float outside = readServiceFloat("getOutsideTemp");
        if (outside == 0) outside = readFloat(YFVehicleProperty.ENV_OUTSIDE_TEMPERATURE);

        updateTaggedLabel("climate_inside", getString(R.string.graph_hvac_set, inside > 0 ? fmt(inside) + "°C" : getString(R.string.graph_hvac_off)));
        updateTaggedLabel("climate_outside", getString(R.string.graph_outside_temp, fmt(outside) + "°C"));

        // PM2.5: value 253 = no sensor (CAN sentinel 0xFD)
        float pm25inSaic = readSaicFloat("aircondition", "getPm25Concentration");
        String pm25InStr = (pm25inSaic >= 253 || pm25inSaic < 0) ? getString(R.string.graph_no_sensor) :
            (pm25inSaic > 0 ? fmt(pm25inSaic) : "0");
        updateTaggedLabel("aq_pm25_in", getString(R.string.graph_pm25_inside, pm25InStr));
        updateTaggedLabel("aq_pm25_out", getString(R.string.graph_pm25_outside, fmt(readFloat(YFVehicleProperty.HVAC_PM25_OUTCAR))));

        String pm25f = mVehicle.getPm25Filter();
        if (pm25f.equals("N/A")) pm25f = mVehicle.getPropertyValue(YFVehicleProperty.HVAC_PM25_FILTER);
        updateTaggedLabel("aq_pm25_filter", getString(R.string.graph_pm25_filter, pm25f));

        String anion = mVehicle.getAnionStatus();
        if (anion.equals("N/A")) anion = mVehicle.getPropertyValue(YFVehicleProperty.HVAC_ANION_STATUS);
        updateTaggedLabel("aq_anion", getString(R.string.graph_anion_status, decodeOnOff(anion)));

        updateTaggedLabel("aq_aqs", getString(R.string.graph_aqs, mVehicle.getPropertyValue(YFVehicleProperty.HVAC_CUST_AIR_QULT)));
        updateTaggedLabel("aq_ionizer", getString(R.string.graph_ionizer, decodeOnOff(mVehicle.getPropertyValue(YFVehicleProperty.HVAC_AIRCLNR_IONIZER))));
        updateTaggedLabel("aq_filter_life", getString(R.string.graph_filter_life, mVehicle.getPropertyValue(YFVehicleProperty.HVAC_AIRCLNR_FLTR_LIFE)));
        updateTaggedLabel("aq_filter_level", getString(R.string.graph_filter_usage, mVehicle.getPropertyValue(YFVehicleProperty.HVAC_AIRCLNR_FLTR_CSUMLVL)));
        updateTaggedLabel("aq_purifier_sw", getString(R.string.graph_air_purifier, decodeOnOff(mVehicle.getPropertyValue(YFVehicleProperty.HVAC_AIRCLNR_ONOFF_SW))));
        updateTaggedLabel("aq_auto_recirc", getString(R.string.graph_auto_recirc, decodeOnOff(mVehicle.getPropertyValue(YFVehicleProperty.HVAC_AUTO_RECIRC_ON))));
    }

    private void updateTrip() {
        if (mOdometer == null) return;
        mOdometer.setText(getString(R.string.graph_odometer, mVehicle.getPropertyValue(YFVehicleProperty.SENSOR_TOTAL_MILEAGE)));

        float avgRaw = readFloat(YFVehicleProperty.CRNT_AVG_ELEC_CSUMP);
        mAvgConsumption.setText(getString(R.string.graph_avg_consumption, String.format("%.1f", avgRaw)));

        float pV = readFloat(YFVehicleProperty.BMS_PACK_VOL);
        float pI = readFloat(YFVehicleProperty.BMS_PACK_CRNT);
        float tripSpeed = readSaicFloat("condition", "getCarSpeed");
        if (tripSpeed == 0) tripSpeed = readFloat(YFVehicleProperty.PERF_VEHICLE_SPEED);
        float tripPower = pV * pI / 1000f;
        float instantCons = tripSpeed > 1 ? tripPower / tripSpeed * 100f : 0;
        mTotalConsumed.setText(getString(R.string.trip_instant, String.format("%.1f", instantCons)));
        float pKw = pV * pI / 1000f;
        mRegenEnergy.setText(pKw < 0
            ? getString(R.string.trip_power_regen, String.format("%.1f", pKw))
            : getString(R.string.trip_power, String.format("%.1f", pKw)));

        float soc = readFloat(YFVehicleProperty.BMS_PACK_SOC_DSP);
        mRegenRange.setText("SOC: " + String.format("%.1f%%", soc)
            + " | Pack: " + String.format("%.0fV %.1fA", pV, pI));

        // Feed trip recorder if recording
        if (mTripRecorder != null && mTripRecorder.isRecording()) {
            // Get GPS from SAIC nav service
            double lat = 0, lon = 0, alt = 0;
            try {
                String locJson = mVehicle.callSaicMethod("adaptervoice", "getCurLocationDesc");
                if (locJson != null && locJson.startsWith("{")) {
                    org.json.JSONObject loc = new org.json.JSONObject(locJson);
                    lat = loc.optDouble("lat", 0);
                    lon = loc.optDouble("lon", 0);
                }
            } catch (Exception ignored) {}

            float speed = readSaicFloat("condition", "getCarSpeed");
            if (speed == 0) speed = readFloat(YFVehicleProperty.PERF_VEHICLE_SPEED);

            mTripRecorder.addPoint(lat, lon, alt, speed, pKw, soc, instantCons, 0, 0);

            // Update recording status
            updateTaggedLabel("trip_rec_status",
                getString(R.string.recording_pts, mTripRecorder.getPointCount(), mTripRecorder.getSummary()));
            updateTaggedLabel("trip_summary", mTripRecorder.getSummary());
        }
    }

    // ==================== G-Meter ====================

    private GMeterView mGMeter;
    private LineChartView mLongGChart, mLatGChart;

    private void buildGMeter() {
        // G-Meter circle
        mGMeter = new GMeterView(this);
        mGMeter.setMaxG(1.0f);
        mGMeter.setDotColor(C_BLUE);
        mGMeter.setBgColor(cCard);
        mGMeter.setRingColor(cDivider);
        mGMeter.setTextColor(cText);
        mGMeter.setLabelColor(cTextTert);
        mGMeter.setAxisLabels(getString(R.string.gmeter_accel), getString(R.string.gmeter_brake));
        mContent.addView(mGMeter, new LinearLayout.LayoutParams(-1, 400));

        // Longitudinal G chart (accel/brake over time)
        mLongGChart = newChart(getString(R.string.graph_long_g), "G", C_GREEN);
        mContent.addView(mLongGChart, chartLP());

        // Lateral G chart (cornering over time)
        mLatGChart = newChart(getString(R.string.graph_lat_g), "G", C_ORANGE);
        mContent.addView(mLatGChart, chartLP());

        // Aggressive driving indicator
        addDivider();
        TextView aggressiveLabel = newInfoLabel(getString(R.string.graph_aggressive_driving, "--"));
        aggressiveLabel.setTag("gmeter_aggressive");
        mContent.addView(aggressiveLabel, infoLP());

        TextView peakLongG = newInfoLabel(getString(R.string.graph_peak_long, "--"));
        peakLongG.setTag("gmeter_peak_long");
        mContent.addView(peakLongG, infoLP());

        TextView peakLatG = newInfoLabel(getString(R.string.graph_peak_lat, "--"));
        peakLatG.setTag("gmeter_peak_lat");
        mContent.addView(peakLatG, infoLP());

        addDivider();

        TextView regenVal = newInfoLabel(getString(R.string.graph_regen_level, 0).replace("0", "--"));
        regenVal.setTag("gmeter_regen");
        mContent.addView(regenVal, infoLP());

        TextView onePedalVal = newInfoLabel(getString(R.string.graph_one_pedal, "--"));
        onePedalVal.setTag("gmeter_one_pedal");
        mContent.addView(onePedalVal, infoLP());
    }

    private float mPeakLongG = 0f;
    private float mPeakLatG = 0f;

    private void updateGMeter() {
        if (mGMeter == null) return;

        // Speed derivative for longitudinal G (VHAL acceleration sensors have VALID=0)
        float speed = readSaicFloat("condition", "getCarSpeed");
        if (speed == 0) speed = readFloat(YFVehicleProperty.PERF_VEHICLE_SPEED);
        float speedMs = speed / 3.6f;
        long now = System.currentTimeMillis();
        float longG = 0, latG = 0;
        if (mPrevSpeedTimeMs > 0) {
            float dt = (now - mPrevSpeedTimeMs) / 1000f;
            if (dt > 0.1f && dt < 5f) {
                longG = (speedMs - mPrevSpeedMs) / dt / 9.81f;
                // Lateral G from steering angle: latG = v² × tan(angle/ratio) / (wheelbase × g)
                float steerAngle = readFloat(YFVehicleProperty.SENSOR_WHEEL_ANGLE);
                if (Math.abs(steerAngle) > 1f && speed > 3f) {
                    float wheelbase = 2.8f;
                    float steerRatio = 14.5f;
                    float roadAngle = steerAngle / steerRatio;
                    latG = (float)(speedMs * speedMs * Math.tan(Math.toRadians(roadAngle)) / (wheelbase * 9.81f));
                }
            }
        }
        mPrevSpeedMs = speedMs;
        mPrevSpeedTimeMs = now;
        float aggressive = readFloat(YFVehicleProperty.SENSOR_FAST_ACCELERATION_DECELERATION);

        mGMeter.setValues(latG, longG);
        mLongGChart.addPoint(longG);
        mLatGChart.addPoint(latG);

        // Track peaks
        if (Math.abs(longG) > Math.abs(mPeakLongG)) mPeakLongG = longG;
        if (Math.abs(latG) > Math.abs(mPeakLatG)) mPeakLatG = latG;

        // Aggressive driving: value meaning TBD, show raw
        String aggressiveStr;
        if (aggressive > 0.5) aggressiveStr = getString(R.string.graph_aggressive_yes, fmt(aggressive));
        else if (aggressive > 0) aggressiveStr = getString(R.string.graph_aggressive_mild, fmt(aggressive));
        else aggressiveStr = getString(R.string.graph_aggressive_no);
        updateTaggedLabel("gmeter_aggressive", getString(R.string.graph_aggressive_driving, aggressiveStr));

        updateTaggedLabel("gmeter_peak_long", getString(R.string.graph_peak_long, String.format("%.2f G", mPeakLongG)));
        updateTaggedLabel("gmeter_peak_lat", getString(R.string.graph_peak_lat, String.format("%.2f G", mPeakLatG)));

        // Regen level (+1 for user-facing display) and one-pedal
        int regenLvl = (int) readFloat(YFVehicleProperty.AAD_EPTRGTNLVL);
        updateTaggedLabel("gmeter_regen", getString(R.string.graph_regen_level, regenLvl + 1));

        float onePedal = readFloat(YFVehicleProperty.SIGNAL_PEDAL_ON);
        updateTaggedLabel("gmeter_one_pedal", getString(R.string.graph_one_pedal, onePedal > 0 ? "ON" : "OFF"));
    }

    // ==================== Value decoders ====================

    private static String decodeGear(int raw) {
        switch (raw) {
            case 1: return "P";
            case 2: return "R";
            case 3: return "N";
            case 4: return "D";
            default: return String.valueOf(raw);
        }
    }

    private static String decodeChargeStatus(int raw) {
        switch (raw) {
            case 0: return "Not Charging";
            case 1: return "AC Charging";
            case 2: return "DC Charging";
            case 3: return "Charge Complete";
            case 4: return "Charge Error";
            default: return "Status " + raw;
        }
    }

    private static String decodeDoorStatus(int raw) {
        switch (raw) {
            case 0: return "Closed";
            case 1: return "Open";
            default: return "Unknown (" + raw + ")";
        }
    }

    private static String decodeOnOff(String val) {
        if (val == null || val.equals("N/A")) return "N/A";
        if (val.equals("1") || val.equals("1.00") || val.equalsIgnoreCase("true")) return "On";
        if (val.equals("0") || val.equals("0.00") || val.equalsIgnoreCase("false")) return "Off";
        return val;
    }

    // ==================== Helpers ====================

    private float readFloat(int propId) {
        try {
            String val = mVehicle.getPropertyValue(propId);
            if (val == null || val.equals("N/A") || val.equals("Connecting...")) return 0f;
            return Float.parseFloat(val);
        } catch (Exception e) { return 0f; }
    }

    private float readSaicFloat(String serviceName, String methodName) {
        try {
            String val = mVehicle.callSaicMethod(serviceName, methodName);
            if (val != null && !val.equals("N/A") && !val.isEmpty()) return Float.parseFloat(val);
        } catch (Exception ignored) {}
        return 0f;
    }

    private float readServiceFloat(String methodName) {
        try {
            java.lang.reflect.Method m = mVehicle.getClass().getMethod(methodName);
            Object r = m.invoke(mVehicle);
            if (r instanceof String) {
                String s = (String) r;
                if (s.equals("N/A") || s.isEmpty()) return 0f;
                return Float.parseFloat(s);
            }
        } catch (Exception e) { }
        return 0f;
    }

    private String fmt(float v) {
        if (v == (int) v) return String.valueOf((int) v);
        return String.format("%.1f", v);
    }

    private void updateTaggedLabel(String tag, String text) {
        TextView tv = mContent.findViewWithTag(tag);
        if (tv != null) tv.setText(text);
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private ArcGaugeView newGauge(String label, String unit, float max, int color) {
        ArcGaugeView g = new ArcGaugeView(this);
        g.setLabel(label);
        g.setUnit(unit);
        g.setMaxValue(max);
        g.setFgColor(color);
        g.setBgArcColor(cCard);
        g.setTextColor(cText);
        g.setLabelColor(cTextSec);
        return g;
    }

    private LineChartView newChart(String label, String unit, int color) {
        LineChartView c = new LineChartView(this);
        c.setLabel(label);
        c.setUnit(unit);
        c.setLineColor(color);
        c.setGridColor(cDivider);
        c.setTextColor(cTextSec);
        c.setBackgroundColor(cCard);
        c.setPadding(8, 8, 8, 8);
        return c;
    }

    private TextView newInfoLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(cText);
        tv.setTextSize(16);
        tv.setPadding(20, 14, 20, 14);
        tv.setBackgroundColor(cCard);
        return tv;
    }

    private void addDivider() {
        android.view.View div = new android.view.View(this);
        div.setBackgroundColor(cDivider);
        mContent.addView(div, new LinearLayout.LayoutParams(-1, 2));
    }

    private LinearLayout.LayoutParams gaugeLP() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 340, 1f);
        lp.setMargins(4, 4, 4, 4);
        return lp;
    }

    private LinearLayout.LayoutParams chartLP() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 160);
        lp.setMargins(0, 4, 0, 4);
        return lp;
    }

    private LinearLayout.LayoutParams infoLP() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 3, 0, 3);
        return lp;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
    }
}

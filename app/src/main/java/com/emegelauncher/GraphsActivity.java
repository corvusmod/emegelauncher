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
import com.emegelauncher.widget.PowerFlowBar;
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

    // Dashboard widgets
    private ArcGaugeView mSpeedGauge, mSocGauge, mRpmGauge, mEfficiencyGauge;
    private PowerFlowBar mPowerFlow;
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
    private static final int C_BLUE = 0xFF0A84FF, C_GREEN = 0xFF30D158, C_RED = 0xFFFF453A;
    private static final int C_ORANGE = 0xFFFF9F0A, C_TEAL = 0xFF64D2FF, C_PURPLE = 0xFFBF5AF2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        resolveColors();

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
    }

    // ==================== Tab Bar ====================

    private static final String[] TAB_NAMES = {"Dashboard", "Energy", "Charging", "Health", "Tires", "Climate", "Trip"};
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
        back.setText("BACK");
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
            case 2: buildCharging(); break;
            case 3: buildHealth(); break;
            case 4: buildTires(); break;
            case 5: buildClimate(); break;
            case 6: buildTrip(); break;
        }
    }

    // ==================== Dashboard ====================

    private void buildDashboard() {
        // Speed + SOC gauges side by side
        LinearLayout row1 = newRow();
        mSpeedGauge = newGauge("Speed", "km/h", 220, C_BLUE);
        mSocGauge = newGauge("Battery", "%", 100, C_GREEN);
        row1.addView(mSpeedGauge, gaugeLP());
        row1.addView(mSocGauge, gaugeLP());
        mContent.addView(row1);

        // RPM + Efficiency gauges
        LinearLayout row2 = newRow();
        mRpmGauge = newGauge("Motor", "RPM", 12000, C_ORANGE);
        mEfficiencyGauge = newGauge("Efficiency", "", 100, C_TEAL);
        row2.addView(mRpmGauge, gaugeLP());
        row2.addView(mEfficiencyGauge, gaugeLP());
        mContent.addView(row2);

        // Power flow bar
        mPowerFlow = new PowerFlowBar(this);
        mPowerFlow.setMaxValue(300);
        mPowerFlow.setBgColor(cCard);
        mPowerFlow.setTextColor(cText);
        mPowerFlow.setLabelColor(cTextSec);
        mContent.addView(mPowerFlow, new LinearLayout.LayoutParams(-1, 200));

        // Gear + Range info row
        LinearLayout row3 = newRow();
        row3.setPadding(20, 16, 20, 16);

        mGearText = newInfoLabel("Gear: --");
        mRangeText = newInfoLabel("Range: --");
        row3.addView(mGearText, new LinearLayout.LayoutParams(0, -2, 1f));
        row3.addView(mRangeText, new LinearLayout.LayoutParams(0, -2, 1f));
        mContent.addView(row3);
    }

    // ==================== Energy ====================

    private LineChartView mSocBmsChart;

    private void buildEnergy() {
        mSocChart = newChart("SOC (Display)", "%", C_GREEN);
        mContent.addView(mSocChart, chartLP());

        mSocBmsChart = newChart("SOC (BMS Raw)", "%", C_TEAL);
        mContent.addView(mSocBmsChart, chartLP());

        mVoltChart = newChart("Pack Voltage", "V", C_ORANGE);
        mContent.addView(mVoltChart, chartLP());

        mCurrentChart = newChart("Pack Current", "A", C_BLUE);
        mContent.addView(mCurrentChart, chartLP());

        mConsumptionChart = newChart("Consumption", "kWh/100km", C_PURPLE);
        mContent.addView(mConsumptionChart, chartLP());
    }

    // ==================== Charging ====================

    private void buildCharging() {
        // Live power gauge
        mChargeRateGauge = newGauge("Charge Power", "kW", 150, C_GREEN);
        mContent.addView(mChargeRateGauge, new LinearLayout.LayoutParams(-1, 250));

        // Live charts
        mChargePowerChart = newChart("Charge Power (live)", "kW", C_GREEN);
        mContent.addView(mChargePowerChart, chartLP());

        mChargeCurrentChart = newChart("Current (Battery vs Charger)", "A", C_ORANGE);
        mContent.addView(mChargeCurrentChart, chartLP());

        mChargeVoltChart = newChart("Voltage (Pack vs Charger Input)", "V", C_BLUE);
        mContent.addView(mChargeVoltChart, chartLP());

        addDivider();

        // Status info
        mChargeStatus = newInfoLabel("Status: --");
        mContent.addView(mChargeStatus, infoLP());

        mChargeTime = newInfoLabel("Time Remaining: --");
        mContent.addView(mChargeTime, infoLP());

        mTargetSoc = newInfoLabel("Target SOC: --");
        mContent.addView(mTargetSoc, infoLP());

        mPlugStatus = newInfoLabel("Plug: --");
        mContent.addView(mPlugStatus, infoLP());

        mDoorStatus = newInfoLabel("Charge Door: --");
        mContent.addView(mDoorStatus, infoLP());

        addDivider();

        // Technical details (AC/DC)
        TextView secHeader = newInfoLabel("CHARGER DETAILS");
        secHeader.setTextColor(cTextTert);
        secHeader.setTextSize(12);
        mContent.addView(secHeader, infoLP());

        // AC onboard charger info
        TextView onbdPlug = newInfoLabel("AC Plug (Onboard): --");
        onbdPlug.setTag("chrg_onbd_plug");
        mContent.addView(onbdPlug, infoLP());

        TextView onbdVolt = newInfoLabel("AC Input Voltage: --");
        onbdVolt.setTag("chrg_ac_volt");
        mContent.addView(onbdVolt, infoLP());

        TextView onbdCrnt = newInfoLabel("AC Input Current: --");
        onbdCrnt.setTag("chrg_ac_crnt");
        mContent.addView(onbdCrnt, infoLP());

        // DC off-board charger info
        TextView offbdPlug = newInfoLabel("DC Plug (Off-board): --");
        offbdPlug.setTag("chrg_offbd_plug");
        mContent.addView(offbdPlug, infoLP());

        // BMS limits
        TextView optCrnt = newInfoLabel("BMS Optimal Current: --");
        optCrnt.setTag("chrg_opt_crnt");
        mContent.addView(optCrnt, infoLP());

        TextView stopReason = newInfoLabel("Charge Stop Reason: --");
        stopReason.setTag("chrg_stop_reason");
        mContent.addView(stopReason, infoLP());

        TextView altCrnt = newInfoLabel("Alternating Current: --");
        altCrnt.setTag("chrg_alt_crnt");
        mContent.addView(altCrnt, infoLP());

        // Session energy
        TextView sessionEnergy = newInfoLabel("Session Energy: --");
        sessionEnergy.setTag("chrg_session_energy");
        mContent.addView(sessionEnergy, infoLP());

        // Scheduled charging
        TextView schedInfo = newInfoLabel("Scheduled Charge: --");
        schedInfo.setTag("chrg_schedule");
        mContent.addView(schedInfo, infoLP());
    }

    // ==================== Battery Health ====================

    private void buildHealth() {
        // Disclaimer
        TextView disclaimer = newInfoLabel(
            "Battery health (SOH) is AUTO-CALCULATED by tracking charge sessions. " +
            "This is an estimation based on energy input vs SOC change — not a value " +
            "from the car's BMS. Accuracy improves with more charge sessions recorded. " +
            "For an official battery health assessment, contact an MG service center.");
        disclaimer.setTextColor(C_ORANGE);
        disclaimer.setTextSize(13);
        disclaimer.setPadding(20, 20, 20, 20);
        mContent.addView(disclaimer, infoLP());

        addDivider();

        // SOH estimate
        float soh = mHealthTracker.getEstimatedSoh();
        float cap = mHealthTracker.getEstimatedCapacity();

        ArcGaugeView sohGauge = newGauge("Est. SOH", "%", 100,
            soh >= 90 ? C_GREEN : soh >= 75 ? C_ORANGE : C_RED);
        if (soh > 0) sohGauge.setValue(soh);
        mContent.addView(sohGauge, new LinearLayout.LayoutParams(-1, 280));

        TextView capLabel = newInfoLabel("Est. Capacity: " +
            (cap > 0 ? String.format("%.1f kWh", cap) : "No data yet") +
            " (Nominal: 70 kWh)");
        capLabel.setTag("health_capacity");
        mContent.addView(capLabel, infoLP());

        TextView sessionsCount = newInfoLabel("Charge sessions recorded: " +
            mHealthTracker.getSessions().length());
        mContent.addView(sessionsCount, infoLP());

        // Tracking status
        if (mHealthTracker.isCurrentlyTracking()) {
            TextView tracking = newInfoLabel("Currently tracking charge session...");
            tracking.setTextColor(C_GREEN);
            tracking.setTag("health_tracking");
            mContent.addView(tracking, infoLP());

            TextView energySoFar = newInfoLabel("Energy accumulated: " +
                String.format("%.2f kWh", mHealthTracker.getSessionEnergySoFar()));
            energySoFar.setTag("health_energy_sofar");
            mContent.addView(energySoFar, infoLP());
        }

        // Resting voltage analysis
        addDivider();
        TextView voltHeader = newInfoLabel("RESTING VOLTAGE ANALYSIS");
        voltHeader.setTextColor(cTextTert);
        voltHeader.setTextSize(12);
        mContent.addView(voltHeader, infoLP());

        TextView restVolt = newInfoLabel("Pack Voltage: --");
        restVolt.setTag("health_rest_volt");
        mContent.addView(restVolt, infoLP());

        TextView restSoc = newInfoLabel("BMS SOC: --");
        restSoc.setTag("health_rest_soc");
        mContent.addView(restSoc, infoLP());

        TextView restCrnt = newInfoLabel("Standby Current: --");
        restCrnt.setTag("health_rest_crnt");
        mContent.addView(restCrnt, infoLP());

        TextView socGap = newInfoLabel("SOC Gap (Display - Raw): --");
        socGap.setTag("health_soc_gap");
        mContent.addView(socGap, infoLP());

        // Session history
        addDivider();
        TextView histHeader = newInfoLabel("CHARGE SESSION HISTORY");
        histHeader.setTextColor(cTextTert);
        histHeader.setTextSize(12);
        mContent.addView(histHeader, infoLP());

        JSONArray sessions = mHealthTracker.getSessions();
        if (sessions.length() == 0) {
            mContent.addView(newInfoLabel("No charge sessions recorded yet. Plug in your car to start tracking."), infoLP());
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
            // Show last 10 sessions, newest first
            for (int i = sessions.length() - 1; i >= Math.max(0, sessions.length() - 10); i--) {
                try {
                    JSONObject s = sessions.getJSONObject(i);
                    String date = sdf.format(new Date(s.getLong("ts")));
                    String line = date +
                        " | " + s.getDouble("s0") + "→" + s.getDouble("s1") + "%" +
                        " | " + s.getDouble("e") + " kWh" +
                        " | Cap: " + s.getDouble("cap") + " kWh" +
                        " | SOH: " + s.getDouble("soh") + "%";
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
        mTempChart = newChart("Temperature", "°C", C_ORANGE);
        mContent.addView(mTempChart, chartLP());

        // Temp info labels
        LinearLayout row = newRow();
        row.setPadding(20, 12, 20, 12);
        TextView insideLabel = newInfoLabel("Inside: --");
        TextView outsideLabel = newInfoLabel("Outside: --");
        TextView coolantLabel = newInfoLabel("Coolant: --");
        row.addView(insideLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(outsideLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(coolantLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        mContent.addView(row);
        insideLabel.setTag("climate_inside");
        outsideLabel.setTag("climate_outside");
        coolantLabel.setTag("climate_coolant");

        // Air Quality section
        addDivider();
        TextView aqHeader = newInfoLabel("AIR QUALITY");
        aqHeader.setTextColor(cTextTert);
        aqHeader.setTextSize(12);
        mContent.addView(aqHeader, infoLP());

        mPm25Chart = newChart("PM2.5 Inside", "µg/m³", C_TEAL);
        mContent.addView(mPm25Chart, chartLP());

        // Air quality detail labels
        TextView pm25InLabel = newInfoLabel("PM2.5 Inside: --");
        pm25InLabel.setTag("aq_pm25_in");
        mContent.addView(pm25InLabel, infoLP());

        TextView pm25OutLabel = newInfoLabel("PM2.5 Outside: --");
        pm25OutLabel.setTag("aq_pm25_out");
        mContent.addView(pm25OutLabel, infoLP());

        TextView pm25FilterLabel = newInfoLabel("PM2.5 Filter: --");
        pm25FilterLabel.setTag("aq_pm25_filter");
        mContent.addView(pm25FilterLabel, infoLP());

        TextView aqsLabel = newInfoLabel("Air Quality Sensor (AQS): --");
        aqsLabel.setTag("aq_aqs");
        mContent.addView(aqsLabel, infoLP());

        TextView ionLabel = newInfoLabel("Ionizer: --");
        ionLabel.setTag("aq_ionizer");
        mContent.addView(ionLabel, infoLP());

        TextView anionLabel = newInfoLabel("Anion Status: --");
        anionLabel.setTag("aq_anion");
        mContent.addView(anionLabel, infoLP());

        TextView filterLifeLabel = newInfoLabel("Filter Life: --");
        filterLifeLabel.setTag("aq_filter_life");
        mContent.addView(filterLifeLabel, infoLP());

        TextView filterLevelLabel = newInfoLabel("Filter Usage Level: --");
        filterLevelLabel.setTag("aq_filter_level");
        mContent.addView(filterLevelLabel, infoLP());

        TextView airPurifierLabel = newInfoLabel("Air Purifier Switch: --");
        airPurifierLabel.setTag("aq_purifier_sw");
        mContent.addView(airPurifierLabel, infoLP());

        TextView autoRecircLabel = newInfoLabel("Auto Recirculation: --");
        autoRecircLabel.setTag("aq_auto_recirc");
        mContent.addView(autoRecircLabel, infoLP());
    }

    // ==================== Trip ====================

    private void buildTrip() {
        mOdometer = newInfoLabel("Odometer: --");
        mContent.addView(mOdometer, infoLP());

        mAvgConsumption = newInfoLabel("Avg Consumption: --");
        mContent.addView(mAvgConsumption, infoLP());

        mTotalConsumed = newInfoLabel("Total Consumed (trip): --");
        mContent.addView(mTotalConsumed, infoLP());

        mRegenEnergy = newInfoLabel("Regen Energy (trip): --");
        mContent.addView(mRegenEnergy, infoLP());

        mRegenRange = newInfoLabel("Regen Range (trip): --");
        mContent.addView(mRegenRange, infoLP());

        // Since-charge stats
        addDivider();
        TextView header = newInfoLabel("SINCE LAST CHARGE");
        header.setTextColor(cTextTert);
        header.setTextSize(12);
        mContent.addView(header, infoLP());

        TextView chargeConsumed = newInfoLabel("Consumed: --");
        chargeConsumed.setTag("trip_charge_consumed");
        mContent.addView(chargeConsumed, infoLP());

        TextView chargeRegen = newInfoLabel("Regen Energy: --");
        chargeRegen.setTag("trip_charge_regen");
        mContent.addView(chargeRegen, infoLP());

        TextView chargeRegenRange = newInfoLabel("Regen Range: --");
        chargeRegenRange.setTag("trip_charge_regen_range");
        mContent.addView(chargeRegenRange, infoLP());
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
                case 2: updateCharging(); break;
                case 3: updateHealth(); break;
                case 4: updateTires(); break;
                case 5: updateClimate(); break;
                case 6: updateTrip(); break;
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

        mRpmGauge.setValue(readFloat(YFVehicleProperty.ENGINE_RPM));
        mEfficiencyGauge.setValue(readFloat(YFVehicleProperty.SENSOR_DRIVE_EFFICIENCY_INDICATION));
        mPowerFlow.setValue(readFloat(YFVehicleProperty.BMS_PACK_CRNT));

        // Gear: SAIC condition → VHAL
        int gearVal = (int) readSaicFloat("condition", "getCarGear");
        if (gearVal == 0) gearVal = (int) readFloat(YFVehicleProperty.CURRENT_GEAR);
        mGearText.setText("Gear: " + decodeGear(gearVal));

        // Range: SAIC charging → cluster → BMS
        float rangeSaic = readSaicFloat("charging", "getCurrentEnduranceMileage");
        String clstrRange = mVehicle.getPropertyValue(YFVehicleProperty.CLSTR_ELEC_RNG);
        String bmsRange = mVehicle.getPropertyValue(YFVehicleProperty.BMS_ESTD_ELEC_RNG);
        String displayRange;
        if (rangeSaic > 0) displayRange = fmt(rangeSaic);
        else if (isValidVal(clstrRange)) displayRange = clstrRange;
        else displayRange = bmsRange;
        String rangeLabel = "Range: " + displayRange + " km";
        if (isValidVal(bmsRange) && !bmsRange.equals(displayRange)) {
            rangeLabel += " (BMS: " + bmsRange + ")";
        }
        mRangeText.setText(rangeLabel);
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
        // Raw value is ~82.3 for real ~8.23 kWh/100km → divide by 10
        mConsumptionChart.addPoint(readFloat(YFVehicleProperty.ELEC_CSUMP_PERKM) / 10f);
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

        mChargeStatus.setText("Status: " + decodeChargeStatus(chrgSts) + " (LIVE)");
        mChargeTime.setText("Time Remaining: " + (timeRemain >= 1023 ? "N/A" : (int) timeRemain + " min"));

        float targetSoc = readFloat(YFVehicleProperty.BMS_DISCHRG_TRGT_SOC_RESP);
        if (targetSoc <= 0) targetSoc = readFloat(YFVehicleProperty.CHRG_TRGT_SOC);
        if (socRaw > 0 && socDsp > 0 && targetSoc > 0) {
            float ratio = socDsp / socRaw;
            int estDisplay = Math.min(100, Math.round(targetSoc * ratio));
            mTargetSoc.setText("Target SOC: ~" + estDisplay + "% (BMS raw: " + (int) targetSoc + "%)");
        } else {
            mTargetSoc.setText("Target SOC: " + (int) targetSoc + "% (BMS raw)");
        }

        mPlugStatus.setText("Plug: " + (readFloat(YFVehicleProperty.EV_CHARGE_PORT_CONNECTED) > 0 ? "Connected" : "Disconnected"));
        mDoorStatus.setText("Charge Door: " + decodeDoorStatus((int) readFloat(YFVehicleProperty.BMS_CHRG_DOOR_POS_STS)));

        updateTaggedLabel("chrg_onbd_plug", "AC Plug (Onboard): " + (onbdPlug ? "Connected" : "No"));
        updateTaggedLabel("chrg_offbd_plug", "DC Plug (Off-board): " + (offbdPlug ? "Connected" : "No"));
        updateTaggedLabel("chrg_ac_volt", "AC Input Voltage: " + fmt(acVolt) + " V");
        updateTaggedLabel("chrg_ac_crnt", "AC Input Current: " + fmt(acCrnt) + " A");
        updateTaggedLabel("chrg_opt_crnt", "BMS Optimal Current: " + fmt(optCrnt) + " A" +
            (optCrnt > 0 ? " (BMS limits charge rate)" : ""));
        updateTaggedLabel("chrg_stop_reason", "Charge Stop Reason: " + mVehicle.getPropertyValue(YFVehicleProperty.BMS_CHRG_SP_RSN));
        updateTaggedLabel("chrg_alt_crnt", "Alternating Current: " + mVehicle.getPropertyValue(YFVehicleProperty.ALTNG_CHRG_CRNT) + " A");
        updateTaggedLabel("chrg_session_energy", "Session Energy: " +
            String.format("%.2f kWh", mHealthTracker.getSessionEnergySoFar()));

        int stH = (int) readFloat(YFVehicleProperty.RESER_ST_HOUR);
        int stM = (int) readFloat(YFVehicleProperty.RESER_ST_MIN);
        int spH = (int) readFloat(YFVehicleProperty.RESER_SP_HOUR);
        int spM = (int) readFloat(YFVehicleProperty.RESER_SP_MIN);
        int reserCtrl = (int) readFloat(YFVehicleProperty.RESER_CTRL);
        if (reserCtrl > 0) {
            updateTaggedLabel("chrg_schedule", String.format("Scheduled: %02d:%02d → %02d:%02d", stH, stM, spH, spM));
        } else {
            updateTaggedLabel("chrg_schedule", "Scheduled Charge: Off");
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
        mChargeStatus.setText("Status: " + decodeChargeStatus(chrgSts));

        JSONObject snap = mHealthTracker.getLastChargeSnapshot();
        if (snap == null) {
            mChargeRateGauge.setValue(0);
            mChargeTime.setText("Time Remaining: N/A");
            mTargetSoc.setText("Target SOC: --");
            mPlugStatus.setText("Plug: Disconnected");
            mDoorStatus.setText("Charge Door: " + decodeDoorStatus((int) readFloat(YFVehicleProperty.BMS_CHRG_DOOR_POS_STS)));
            updateTaggedLabel("chrg_session_energy", "No charge session recorded yet");
            return;
        }

        try {
            // Show stored data with timestamp
            long ts = snap.getLong("ts");
            String when = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(new Date(ts));
            mChargeStatus.setText("Last Charge: " + decodeChargeStatus(snap.getInt("status")) + " (" + when + ")");
            mChargeRateGauge.setValue((float) snap.getDouble("powerKw"));
            mChargeTime.setText("Time Remaining: N/A (session ended)");

            float startSoc = (float) snap.getDouble("startSoc");
            float endSoc = (float) snap.getDouble("socRaw");
            float socDsp = (float) snap.getDouble("socDsp");
            mTargetSoc.setText("Charged: " + fmt(startSoc) + "% → " + fmt(endSoc) + "% BMS (" + fmt(socDsp) + "% display)");

            mPlugStatus.setText("Plug: Disconnected");
            mDoorStatus.setText("Charge Door: " + decodeDoorStatus((int) readFloat(YFVehicleProperty.BMS_CHRG_DOOR_POS_STS)));

            boolean onbd = snap.optBoolean("onbdPlug", false);
            boolean offbd = snap.optBoolean("offbdPlug", false);
            String chargeType = offbd ? "DC (fast)" : (onbd ? "AC (onboard)" : "Unknown");
            updateTaggedLabel("chrg_onbd_plug", "Charge Type: " + chargeType);
            updateTaggedLabel("chrg_offbd_plug", "DC Plug: " + (offbd ? "Was connected" : "No"));
            updateTaggedLabel("chrg_ac_volt", "AC Input Voltage: " + snap.optDouble("acVolt", 0) + " V");
            updateTaggedLabel("chrg_ac_crnt", "AC Input Current: " + snap.optDouble("acCrnt", 0) + " A");
            updateTaggedLabel("chrg_opt_crnt", "BMS Optimal Current: " + snap.optDouble("optCrnt", 0) + " A");
            updateTaggedLabel("chrg_stop_reason", "Charge Stop Reason: " + mVehicle.getPropertyValue(YFVehicleProperty.BMS_CHRG_SP_RSN));
            updateTaggedLabel("chrg_alt_crnt", "Peak Power: " + snap.optDouble("powerKw", 0) + " kW");
            updateTaggedLabel("chrg_session_energy", "Session Energy: " + snap.optDouble("energy", 0) + " kWh");
            updateTaggedLabel("chrg_schedule", "Pack Voltage (last): " + snap.optDouble("packVolt", 0) + " V | Current: " + snap.optDouble("packCrnt", 0) + " A");
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

        updateTaggedLabel("health_rest_volt", "Pack Voltage: " + fmt(volt) + " V");
        updateTaggedLabel("health_rest_soc", "BMS SOC: " + fmt(socRaw) + "%");
        updateTaggedLabel("health_rest_crnt", "Standby Current: " + fmt(crnt) + " A");
        updateTaggedLabel("health_soc_gap", "SOC Gap (Display - Raw): " +
            String.format("%.1f%%", socDsp - socRaw));

        if (mHealthTracker.isCurrentlyTracking()) {
            updateTaggedLabel("health_tracking", "Tracking... Energy: " +
                String.format("%.2f kWh", mHealthTracker.getSessionEnergySoFar()));
            updateTaggedLabel("health_energy_sofar", "Start SOC: " +
                fmt(mHealthTracker.getSessionStartSoc()) + "% → Current: " + fmt(socRaw) + "%");
        }

        // Update capacity label
        float cap = mHealthTracker.getEstimatedCapacity();
        updateTaggedLabel("health_capacity", "Est. Capacity: " +
            (cap > 0 ? String.format("%.1f kWh", cap) : "No data yet") +
            " (Nominal: 70 kWh)");
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
        if (mTempChart == null) return;
        // Use SAIC AirCondition service (most reliable for HVAC data)
        float inside = readServiceFloat("getDriverTemp");
        if (inside == 0) inside = readFloat(YFVehicleProperty.HVAC_TEMPERATURE_CURRENT);
        float outside = readServiceFloat("getOutsideTemp");
        if (outside == 0) outside = readFloat(YFVehicleProperty.ENV_OUTSIDE_TEMPERATURE);
        float coolant = readFloat(YFVehicleProperty.ENGINE_COOLANT_TEMP);
        mTempChart.addPoint(inside);

        updateTaggedLabel("climate_inside", "Inside: " + fmt(inside) + "°C");
        updateTaggedLabel("climate_outside", "Outside: " + fmt(outside) + "°C");
        updateTaggedLabel("climate_coolant", "Coolant: " + fmt(coolant) + "°C");

        float pm25in = readFloat(YFVehicleProperty.HVAC_PM25_CONCENTRATION);
        mPm25Chart.addPoint(pm25in);

        // Air quality — try SAIC service first, fallback to VHAL
        String pm25InStr = mVehicle.getPm25Concentration();
        if (pm25InStr.equals("N/A")) pm25InStr = fmt(readFloat(YFVehicleProperty.HVAC_PM25_CONCENTRATION));
        updateTaggedLabel("aq_pm25_in", "PM2.5 Inside: " + pm25InStr + " µg/m³");
        updateTaggedLabel("aq_pm25_out", "PM2.5 Outside: " + fmt(readFloat(YFVehicleProperty.HVAC_PM25_OUTCAR)) + " µg/m³");

        String pm25f = mVehicle.getPm25Filter();
        if (pm25f.equals("N/A")) pm25f = mVehicle.getPropertyValue(YFVehicleProperty.HVAC_PM25_FILTER);
        updateTaggedLabel("aq_pm25_filter", "PM2.5 Filter: " + pm25f);

        String anion = mVehicle.getAnionStatus();
        if (anion.equals("N/A")) anion = mVehicle.getPropertyValue(YFVehicleProperty.HVAC_ANION_STATUS);
        updateTaggedLabel("aq_anion", "Anion Status: " + decodeOnOff(anion));

        updateTaggedLabel("aq_aqs", "Air Quality Sensor (AQS): " + mVehicle.getPropertyValue(YFVehicleProperty.HVAC_CUST_AIR_QULT));
        updateTaggedLabel("aq_ionizer", "Ionizer: " + decodeOnOff(mVehicle.getPropertyValue(YFVehicleProperty.HVAC_AIRCLNR_IONIZER)));
        updateTaggedLabel("aq_filter_life", "Filter Life: " + mVehicle.getPropertyValue(YFVehicleProperty.HVAC_AIRCLNR_FLTR_LIFE) + "%");
        updateTaggedLabel("aq_filter_level", "Filter Usage Level: " + mVehicle.getPropertyValue(YFVehicleProperty.HVAC_AIRCLNR_FLTR_CSUMLVL));
        updateTaggedLabel("aq_purifier_sw", "Air Purifier Switch: " + decodeOnOff(mVehicle.getPropertyValue(YFVehicleProperty.HVAC_AIRCLNR_ONOFF_SW)));
        updateTaggedLabel("aq_auto_recirc", "Auto Recirculation: " + decodeOnOff(mVehicle.getPropertyValue(YFVehicleProperty.HVAC_AUTO_RECIRC_ON)));
    }

    private void updateTrip() {
        if (mOdometer == null) return;
        mOdometer.setText("Odometer: " + mVehicle.getPropertyValue(YFVehicleProperty.SENSOR_TOTAL_MILEAGE) + " km");

        // Avg consumption: raw value is in Wh/km, convert to kWh/100km (/10)
        float avgRaw = readFloat(YFVehicleProperty.CRNT_AVG_ELEC_CSUMP);
        mAvgConsumption.setText("Avg Consumption: " + String.format("%.1f", avgRaw / 10f) + " kWh/100km");

        mTotalConsumed.setText("Total Consumed (trip): " + mVehicle.getPropertyValue(YFVehicleProperty.TOTAL_CONSUMPTION_AFTER_START) + " Wh");
        mRegenEnergy.setText("Regen Energy (trip): " + mVehicle.getPropertyValue(YFVehicleProperty.TOTAL_REGEN_ENRG_AFTER_START) + " Wh");
        mRegenRange.setText("Regen Range (trip): " + mVehicle.getPropertyValue(YFVehicleProperty.TOTAL_REGEN_RNG_AFTER_START) + " km");

        updateTaggedLabel("trip_charge_consumed", "Consumed: " + mVehicle.getPropertyValue(YFVehicleProperty.TOTAL_CONSUMPTION_AFTER_CHARGE) + " Wh");
        updateTaggedLabel("trip_charge_regen", "Regen Energy: " + mVehicle.getPropertyValue(YFVehicleProperty.TOTAL_REGEN_ENRG_AFTER_CHARGE) + " Wh");
        updateTaggedLabel("trip_charge_regen_range", "Regen Range: " + mVehicle.getPropertyValue(YFVehicleProperty.TOTAL_REGEN_RNG_AFTER_CHARGE) + " km");
    }

    // ==================== Value decoders ====================

    private static String decodeGear(int raw) {
        switch (raw) {
            case 1: return "D";
            case 2: return "N";
            case 3: return "R";
            case 4: return "P";
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 200, 1f);
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

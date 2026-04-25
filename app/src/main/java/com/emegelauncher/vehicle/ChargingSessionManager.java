/*
 * Emegelauncher - Custom Launcher for MG Marvel R
 * Copyright (C) 2026 Emegelauncher Contributors
 *
 * Licensed under the Apache License, Version 2.0 with the
 * Commons Clause License Condition v1.0 (see LICENSE files).
 *
 * You may NOT sell this software. Donations are welcome.
 */

package com.emegelauncher.vehicle;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Live-only charging session monitor. Records data points while charging
 * for real-time display. No persistent storage — cloud history is used instead.
 */
public class ChargingSessionManager {
    private static final String TAG = "ChargingSession";
    private static volatile ChargingSessionManager sInstance;

    private final List<DataPoint> mCurrentPoints = new ArrayList<>();
    private boolean mWasCharging = false;
    private long mSessionStartTime = 0;
    private float mStartSoc = 0, mStartRange = 0;
    private float mEnergyAccKwh = 0;
    private float mPeakPowerKw = 0;
    private int mChargeType = 0; // 1=AC, 2=DC
    private long mLastUpdateTime = 0;
    private static final long UPDATE_INTERVAL_MS = 5000;

    /** A single telemetry data point during charging */
    public static class DataPoint {
        public long timestamp;
        public float socDisplay;
        public float socRaw;
        public float voltage;
        public float current;
        public float powerKw;
        public float acVoltage;
        public float acCurrent;
        public float efficiency;
        public float timeRemaining;
        public float rangeKm;
        public float energyAccKwh;
        public int chargeType;

        public static DataPoint fromJson(JSONObject j) {
            DataPoint dp = new DataPoint();
            dp.timestamp = j.optLong("t", 0);
            dp.socDisplay = (float) j.optDouble("sD", 0);
            dp.socRaw = (float) j.optDouble("sR", 0);
            dp.voltage = (float) j.optDouble("v", 0);
            dp.current = (float) j.optDouble("i", 0);
            dp.powerKw = (float) j.optDouble("p", 0);
            dp.acVoltage = (float) j.optDouble("aV", 0);
            dp.acCurrent = (float) j.optDouble("aC", 0);
            dp.efficiency = (float) j.optDouble("eff", 0);
            dp.timeRemaining = (float) j.optDouble("tr", 0);
            dp.rangeKm = (float) j.optDouble("r", 0);
            dp.energyAccKwh = (float) j.optDouble("e", 0);
            return dp;
        }
    }

    public static ChargingSessionManager getInstance(Context ctx) {
        if (sInstance == null) {
            synchronized (ChargingSessionManager.class) {
                if (sInstance == null) sInstance = new ChargingSessionManager();
            }
        }
        return sInstance;
    }

    private ChargingSessionManager() {}

    public boolean isCharging() { return mWasCharging; }
    public int getChargeType() { return mChargeType; }
    public float getEnergyAccKwh() { return mEnergyAccKwh; }
    public float getPeakPowerKw() { return mPeakPowerKw; }
    public long getSessionDuration() { return mSessionStartTime > 0 ? System.currentTimeMillis() - mSessionStartTime : 0; }
    public float getStartSoc() { return mStartSoc; }
    public float getStartRange() { return mStartRange; }
    public List<DataPoint> getCurrentPoints() { return mCurrentPoints; }

    /**
     * Called every polling cycle from MainActivity. Handles rate limiting,
     * session detection, and data point recording.
     */
    public void update(VehicleServiceManager vehicle) {
        long now = System.currentTimeMillis();
        if (now - mLastUpdateTime < UPDATE_INTERVAL_MS) return;
        mLastUpdateTime = now;

        try {
            // Detect charging status
            String chrgStsStr = vehicle.getPropertyValue(YFVehicleProperty.BMS_CHRG_STS);
            int chrgSts = 0;
            try { chrgSts = (int) Float.parseFloat(chrgStsStr); } catch (Exception ignored) {}
            boolean isCharging = (chrgSts > 0 && chrgSts != 4);
            // Fallback: SAIC service
            if (!isCharging) {
                int saicSts = 0;
                try { saicSts = (int) Float.parseFloat(vehicle.callSaicMethod("charging", "getChargingStatus")); } catch (Exception ignored) {}
                if (saicSts > 0 && saicSts != 4) { isCharging = true; if (chrgSts == 0) chrgSts = saicSts; }
            }
            Log.d(TAG, "Charge detect: BMS_STS=" + chrgStsStr + " isCharging=" + isCharging + " type=" + chrgSts);

            // Session start
            if (isCharging && !mWasCharging) {
                mSessionStartTime = now;
                mCurrentPoints.clear();
                mEnergyAccKwh = 0;
                mPeakPowerKw = 0;
                mChargeType = chrgSts;
                mStartSoc = readDisplaySoc(vehicle);
                mStartRange = readRange(vehicle);
                Log.d(TAG, "Charge session started: type=" + chrgSts + " soc=" + mStartSoc);
            }

            // Record data point
            if (isCharging) {
                DataPoint dp = readDataPoint(vehicle, now);
                if (!mCurrentPoints.isEmpty()) {
                    DataPoint prev = mCurrentPoints.get(mCurrentPoints.size() - 1);
                    float dtH = (now - prev.timestamp) / 3600000f;
                    mEnergyAccKwh += dp.powerKw * dtH;
                }
                dp.energyAccKwh = mEnergyAccKwh;
                if (dp.powerKw > mPeakPowerKw) mPeakPowerKw = dp.powerKw;
                mCurrentPoints.add(dp);
            }

            // Session end — just clear live data
            if (!isCharging && mWasCharging) {
                Log.d(TAG, "Charge session ended: " + mCurrentPoints.size() + " points, " +
                    String.format("%.1f kWh", mEnergyAccKwh));
                // Don't clear points yet — let the UI show the last session until next one starts
            }

            mWasCharging = isCharging;
        } catch (Exception e) {
            Log.e(TAG, "Update error", e);
        }
    }

    private DataPoint readDataPoint(VehicleServiceManager v, long now) {
        DataPoint dp = new DataPoint();
        dp.timestamp = now;
        dp.socDisplay = readDisplaySoc(v);
        dp.socRaw = readFloat(v, YFVehicleProperty.BMS_PACK_SOC);
        dp.voltage = readFloat(v, YFVehicleProperty.BMS_PACK_VOL);
        dp.current = readFloat(v, YFVehicleProperty.BMS_PACK_CRNT);
        dp.powerKw = Math.abs(dp.voltage * dp.current / 1000f);
        dp.acVoltage = readFloat(v, YFVehicleProperty.ONBD_CHRG_ALT_CRNT_LNPT_VOL);
        dp.acCurrent = readFloat(v, YFVehicleProperty.ONBD_CHRG_ALT_CRNT_LNPT_CRNT);
        dp.chargeType = mChargeType;
        dp.efficiency = 0; // AC input properties don't work on Marvel R
        dp.timeRemaining = readFloat(v, YFVehicleProperty.CHRGNG_RMNNG_TIME);
        if (dp.timeRemaining >= 1023) dp.timeRemaining = -1;
        dp.rangeKm = readRange(v);
        return dp;
    }

    private float readDisplaySoc(VehicleServiceManager v) {
        float soc = readFloatSaic(v, "getCurrentElectricQuantity");
        if (soc == 0) soc = readFloat(v, YFVehicleProperty.BMS_PACK_SOC_DSP);
        return soc;
    }

    private float readRange(VehicleServiceManager v) {
        float r = readFloatSaic(v, "getCurrentEnduranceMileage");
        if (r == 0) r = readFloat(v, YFVehicleProperty.CLSTR_ELEC_RNG);
        return r;
    }

    private float readFloat(VehicleServiceManager v, int propId) {
        try {
            String val = v.getPropertyValue(propId);
            if (val == null || val.equals("N/A")) return 0;
            return Float.parseFloat(val);
        } catch (Exception e) { return 0; }
    }

    private float readFloatSaic(VehicleServiceManager v, String method) {
        try {
            String val = v.callSaicMethod("charging", method);
            if (val == null || val.equals("N/A")) return 0;
            return Float.parseFloat(val);
        } catch (Exception e) { return 0; }
    }
}

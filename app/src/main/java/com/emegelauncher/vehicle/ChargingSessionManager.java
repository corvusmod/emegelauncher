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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * Singleton that records charging sessions with full telemetry.
 * Persists across screen changes. Stores last 5 sessions as JSON.
 */
public class ChargingSessionManager {
    private static final String TAG = "ChargingSession";
    private static final int MAX_SESSIONS = 5;
    private static volatile ChargingSessionManager sInstance;

    private final Context mContext;
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
        public float socDisplay;    // display SOC %
        public float socRaw;        // BMS raw SOC %
        public float voltage;       // pack voltage V
        public float current;       // pack current A
        public float powerKw;       // charge power kW
        public float acVoltage;     // AC input voltage V (AC only)
        public float acCurrent;     // AC input current A (AC only)
        public float efficiency;    // charger efficiency % (AC only)
        public float timeRemaining; // minutes, 1023=N/A
        public float rangeKm;       // estimated range
        public float energyAccKwh;  // accumulated energy since start
        public float extTemp;       // outside temperature
        public int chargeType;      // 1=AC, 2=DC

        public JSONObject toJson() throws Exception {
            JSONObject j = new JSONObject();
            j.put("t", timestamp);
            j.put("sD", socDisplay);
            j.put("sR", socRaw);
            j.put("v", voltage);
            j.put("i", current);
            j.put("p", powerKw);
            j.put("aV", acVoltage);
            j.put("aC", acCurrent);
            j.put("eff", efficiency);
            j.put("tr", timeRemaining);
            j.put("r", rangeKm);
            j.put("e", energyAccKwh);
            return j;
        }

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

    /** Summary info for a stored session */
    public static class SessionInfo {
        public String filename;
        public long startTime;
        public long durationMs;
        public float startSoc, endSoc;
        public float totalEnergyKwh;
        public float peakPowerKw;
        public int chargeType;
        public int pointCount;
        public float startRange, endRange;
    }

    public static ChargingSessionManager getInstance(Context ctx) {
        if (sInstance == null) {
            synchronized (ChargingSessionManager.class) {
                if (sInstance == null) sInstance = new ChargingSessionManager(ctx.getApplicationContext());
            }
        }
        return sInstance;
    }

    private ChargingSessionManager(Context ctx) { mContext = ctx; }

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
            boolean isCharging = (chrgSts == 1 || chrgSts == 2);

            // Session start
            if (isCharging && !mWasCharging) {
                mSessionStartTime = now;
                mCurrentPoints.clear();
                mEnergyAccKwh = 0;
                mPeakPowerKw = 0;
                mChargeType = chrgSts;
                // Read start values
                mStartSoc = readDisplaySoc(vehicle);
                mStartRange = readRange(vehicle);
                Log.d(TAG, "Charge session started: type=" + chrgSts);
            }

            // Record data point
            if (isCharging) {
                DataPoint dp = readDataPoint(vehicle, now);
                // Accumulate energy (power × time)
                if (!mCurrentPoints.isEmpty()) {
                    DataPoint prev = mCurrentPoints.get(mCurrentPoints.size() - 1);
                    float dtH = (now - prev.timestamp) / 3600000f;
                    mEnergyAccKwh += Math.abs(dp.powerKw) * dtH;
                }
                dp.energyAccKwh = mEnergyAccKwh;
                if (dp.powerKw > mPeakPowerKw) mPeakPowerKw = dp.powerKw;
                mCurrentPoints.add(dp);
            }

            // Session end
            if (!isCharging && mWasCharging && !mCurrentPoints.isEmpty()) {
                Log.d(TAG, "Charge session ended: " + mCurrentPoints.size() + " points, " +
                    String.format("%.1f kWh", mEnergyAccKwh));
                saveSession();
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
        dp.powerKw = dp.voltage * dp.current / 1000f;
        dp.acVoltage = readFloat(v, YFVehicleProperty.ONBD_CHRG_ALT_CRNT_LNPT_VOL);
        dp.acCurrent = readFloat(v, YFVehicleProperty.ONBD_CHRG_ALT_CRNT_LNPT_CRNT);
        dp.chargeType = mChargeType;
        // Efficiency: DC pack power / AC input power × 100
        float acPower = dp.acVoltage * dp.acCurrent / 1000f;
        dp.efficiency = (acPower > 0.5f) ? (dp.powerKw / acPower * 100f) : 0;
        dp.timeRemaining = readFloat(v, YFVehicleProperty.CHRGNG_RMNNG_TIME);
        if (dp.timeRemaining >= 1023) dp.timeRemaining = -1; // sentinel
        dp.rangeKm = readRange(v);
        dp.extTemp = readFloatSaic(v, "getOutCarTemp");
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

    // ==================== Storage ====================

    private File getSessionDir() {
        File dir = new File(mContext.getFilesDir(), "charge_sessions");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void saveSession() {
        try {
            if (mCurrentPoints.isEmpty()) return;
            DataPoint first = mCurrentPoints.get(0);
            DataPoint last = mCurrentPoints.get(mCurrentPoints.size() - 1);

            JSONObject session = new JSONObject();
            session.put("startTime", mSessionStartTime);
            session.put("endTime", System.currentTimeMillis());
            session.put("durationMs", System.currentTimeMillis() - mSessionStartTime);
            session.put("startSoc", mStartSoc);
            session.put("endSoc", last.socDisplay);
            session.put("totalEnergyKwh", mEnergyAccKwh);
            session.put("peakPowerKw", mPeakPowerKw);
            session.put("chargeType", mChargeType);
            session.put("startRange", mStartRange);
            session.put("endRange", last.rangeKm);
            session.put("pointCount", mCurrentPoints.size());

            JSONArray points = new JSONArray();
            for (DataPoint dp : mCurrentPoints) points.put(dp.toJson());
            session.put("points", points);

            String filename = "charge_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",
                java.util.Locale.US).format(new java.util.Date(mSessionStartTime)) + ".json";
            File file = new File(getSessionDir(), filename);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(session.toString().getBytes("UTF-8"));
            fos.close();
            Log.d(TAG, "Saved session: " + filename);
            pruneOldSessions();
        } catch (Exception e) {
            Log.e(TAG, "Save session failed", e);
        }
    }

    private void pruneOldSessions() {
        File dir = getSessionDir();
        File[] files = dir.listFiles();
        if (files == null || files.length <= MAX_SESSIONS) return;
        TreeSet<String> sorted = new TreeSet<>();
        for (File f : files) sorted.add(f.getName());
        while (sorted.size() > MAX_SESSIONS) {
            String oldest = sorted.first();
            new File(dir, oldest).delete();
            sorted.remove(oldest);
        }
    }

    /** Get list of stored sessions (newest first) */
    public List<SessionInfo> getStoredSessions() {
        List<SessionInfo> result = new ArrayList<>();
        File dir = getSessionDir();
        File[] files = dir.listFiles();
        if (files == null) return result;
        Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName()));
        for (File f : files) {
            try {
                InputStream is = new FileInputStream(f);
                byte[] buf = new byte[(int) f.length()];
                is.read(buf);
                is.close();
                JSONObject json = new JSONObject(new String(buf, "UTF-8"));
                SessionInfo si = new SessionInfo();
                si.filename = f.getName();
                si.startTime = json.optLong("startTime", 0);
                si.durationMs = json.optLong("durationMs", 0);
                si.startSoc = (float) json.optDouble("startSoc", 0);
                si.endSoc = (float) json.optDouble("endSoc", 0);
                si.totalEnergyKwh = (float) json.optDouble("totalEnergyKwh", 0);
                si.peakPowerKw = (float) json.optDouble("peakPowerKw", 0);
                si.chargeType = json.optInt("chargeType", 0);
                si.pointCount = json.optInt("pointCount", 0);
                si.startRange = (float) json.optDouble("startRange", 0);
                si.endRange = (float) json.optDouble("endRange", 0);
                result.add(si);
            } catch (Exception e) {
                Log.d(TAG, "Read session failed: " + f.getName());
            }
        }
        return result;
    }

    /** Load full data points from a stored session */
    public List<DataPoint> loadSession(String filename) {
        List<DataPoint> points = new ArrayList<>();
        try {
            File f = new File(getSessionDir(), filename);
            InputStream is = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            is.read(buf);
            is.close();
            JSONObject json = new JSONObject(new String(buf, "UTF-8"));
            JSONArray arr = json.optJSONArray("points");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    points.add(DataPoint.fromJson(arr.getJSONObject(i)));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Load session failed: " + filename, e);
        }
        return points;
    }

    /** Export a stored session JSON to a destination directory */
    public File exportSession(String filename, File destination) {
        try {
            File source = new File(getSessionDir(), filename);
            if (!source.exists()) return null;
            File dest = new File(destination, filename);
            InputStream is = new FileInputStream(source);
            OutputStream os = new FileOutputStream(dest);
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
            is.close();
            os.close();
            return dest;
        } catch (Exception e) {
            Log.e(TAG, "Export session failed", e);
            return null;
        }
    }
}

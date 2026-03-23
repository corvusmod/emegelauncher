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
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Estimates battery SOH by tracking charge sessions.
 * Stores minimal data: one record per charge session (start/end SOC, energy in, voltage).
 * Max 50 sessions stored (~5KB). Oldest are pruned.
 *
 * SOH = (estimated_capacity / nominal_capacity) * 100
 * Estimated capacity = energy_charged_kWh / (soc_delta / 100)
 */
public class BatteryHealthTracker {
    private static final String TAG = "BatteryHealth";
    private static final String PREFS = "battery_health";
    private static final String KEY_SESSIONS = "charge_sessions";
    private static final String KEY_CHARGING = "is_charging";
    private static final String KEY_START_SOC = "start_soc";
    private static final String KEY_START_VOLT = "start_volt";
    private static final String KEY_ENERGY_ACC = "energy_acc";
    private static final String KEY_LAST_VOLT = "last_volt";
    private static final String KEY_LAST_CRNT = "last_crnt";
    private static final String KEY_LAST_TIME = "last_time_ms";
    private static final int MAX_SESSIONS = 50;
    private static final float NOMINAL_CAPACITY_KWH = 70.0f; // MG Marvel R

    private final SharedPreferences mPrefs;

    public BatteryHealthTracker(Context context) {
        mPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Call every poll cycle with current charging data.
     * Automatically detects charge start/stop and accumulates energy.
     */
    public void update(boolean isCharging, float socRaw, float packVolt, float packCrnt) {
        boolean wasCharging = mPrefs.getBoolean(KEY_CHARGING, false);

        if (isCharging && !wasCharging) {
            // Charge session started
            mPrefs.edit()
                .putBoolean(KEY_CHARGING, true)
                .putFloat(KEY_START_SOC, socRaw)
                .putFloat(KEY_START_VOLT, packVolt)
                .putFloat(KEY_ENERGY_ACC, 0f)
                .putLong(KEY_LAST_TIME, System.currentTimeMillis())
                .putFloat(KEY_LAST_VOLT, packVolt)
                .putFloat(KEY_LAST_CRNT, packCrnt)
                .apply();
            Log.d(TAG, "Charge session started at SOC=" + socRaw + "%");

        } else if (isCharging) {
            // Accumulate energy: P = V * I, E = P * dt
            long now = System.currentTimeMillis();
            long lastTime = mPrefs.getLong(KEY_LAST_TIME, now);
            float dt = (now - lastTime) / 1000f / 3600f; // hours

            if (dt > 0 && dt < 1) { // sanity: skip if >1h gap (sleep/restart)
                float avgVolt = (mPrefs.getFloat(KEY_LAST_VOLT, packVolt) + packVolt) / 2f;
                float avgCrnt = (mPrefs.getFloat(KEY_LAST_CRNT, packCrnt) + packCrnt) / 2f;
                float energyKwh = avgVolt * Math.abs(avgCrnt) * dt / 1000f;
                float total = mPrefs.getFloat(KEY_ENERGY_ACC, 0f) + energyKwh;
                mPrefs.edit()
                    .putFloat(KEY_ENERGY_ACC, total)
                    .putFloat(KEY_LAST_VOLT, packVolt)
                    .putFloat(KEY_LAST_CRNT, packCrnt)
                    .putLong(KEY_LAST_TIME, now)
                    .apply();
            }

        } else if (wasCharging) {
            // Charge session ended — save record
            float startSoc = mPrefs.getFloat(KEY_START_SOC, 0f);
            float energyKwh = mPrefs.getFloat(KEY_ENERGY_ACC, 0f);
            float socDelta = socRaw - startSoc;

            if (socDelta > 5 && energyKwh > 0.5f) {
                // Valid session (>5% SOC change, >0.5 kWh)
                float estimatedCapacity = energyKwh / (socDelta / 100f);
                float soh = (estimatedCapacity / NOMINAL_CAPACITY_KWH) * 100f;
                saveSession(startSoc, socRaw, energyKwh, estimatedCapacity, soh, packVolt);
                Log.d(TAG, "Charge session saved: " + startSoc + "→" + socRaw +
                    "%, energy=" + energyKwh + "kWh, capacity=" + estimatedCapacity +
                    "kWh, SOH=" + soh + "%");
            }

            mPrefs.edit().putBoolean(KEY_CHARGING, false).apply();
        }
    }

    private void saveSession(float startSoc, float endSoc, float energyKwh,
                             float estCapacity, float soh, float endVolt) {
        try {
            JSONArray sessions = getSessions();
            JSONObject session = new JSONObject();
            session.put("ts", System.currentTimeMillis());
            session.put("s0", Math.round(startSoc * 10) / 10f);
            session.put("s1", Math.round(endSoc * 10) / 10f);
            session.put("e", Math.round(energyKwh * 100) / 100f);
            session.put("cap", Math.round(estCapacity * 10) / 10f);
            session.put("soh", Math.round(soh * 10) / 10f);
            session.put("v", Math.round(endVolt * 10) / 10f);
            sessions.put(session);

            // Prune old sessions
            while (sessions.length() > MAX_SESSIONS) {
                sessions.remove(0);
            }

            mPrefs.edit().putString(KEY_SESSIONS, sessions.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving session", e);
        }
    }

    public JSONArray getSessions() {
        try {
            String json = mPrefs.getString(KEY_SESSIONS, "[]");
            return new JSONArray(json);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** Average SOH from last N sessions. Returns -1 if no data. */
    public float getEstimatedSoh() {
        JSONArray sessions = getSessions();
        if (sessions.length() == 0) return -1;
        int window = Math.min(sessions.length(), 5);
        float sum = 0;
        int parsed = 0;
        for (int i = sessions.length() - window; i < sessions.length(); i++) {
            try { sum += (float) sessions.getJSONObject(i).getDouble("soh"); parsed++; }
            catch (Exception ignored) {}
        }
        return parsed > 0 ? sum / parsed : -1;
    }

    /** Estimated capacity in kWh from last N sessions. Returns -1 if no data. */
    public float getEstimatedCapacity() {
        JSONArray sessions = getSessions();
        if (sessions.length() == 0) return -1;
        int window = Math.min(sessions.length(), 5);
        float sum = 0;
        int parsed = 0;
        for (int i = sessions.length() - window; i < sessions.length(); i++) {
            try { sum += (float) sessions.getJSONObject(i).getDouble("cap"); parsed++; }
            catch (Exception ignored) {}
        }
        return parsed > 0 ? sum / parsed : -1;
    }

    public boolean isCurrentlyTracking() {
        return mPrefs.getBoolean(KEY_CHARGING, false);
    }

    public float getSessionEnergySoFar() {
        return mPrefs.getFloat(KEY_ENERGY_ACC, 0f);
    }

    public float getSessionStartSoc() {
        return mPrefs.getFloat(KEY_START_SOC, 0f);
    }

    // ==================== Latest charge snapshot ====================

    private static final String KEY_LAST_CHARGE = "last_charge_snapshot";

    /** Save a snapshot of current charging state (call periodically while charging) */
    public void saveChargeSnapshot(JSONObject snapshot) {
        mPrefs.edit().putString(KEY_LAST_CHARGE, snapshot.toString()).apply();
    }

    /** Get the latest charge snapshot (for display when not charging) */
    public JSONObject getLastChargeSnapshot() {
        try {
            String json = mPrefs.getString(KEY_LAST_CHARGE, null);
            if (json != null) return new JSONObject(json);
        } catch (Exception ignored) {}
        return null;
    }
}

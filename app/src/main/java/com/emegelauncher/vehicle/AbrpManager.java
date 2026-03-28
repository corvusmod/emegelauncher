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

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * ABRP (A Better Route Planner) telemetry integration.
 * Sends live vehicle data to Iternio's telemetry API so users can
 * plan and drive with real-time data in the ABRP app.
 *
 * API docs: https://documenter.getpostman.com/view/7396339/SWTK5a8w
 * Endpoint: https://api.iternio.com/1/tlm/send
 * Auth: api_key (app) + token (user)
 */
public class AbrpManager {
    private static final String TAG = "AbrpManager";
    private static final String PREFS_NAME = "emegelauncher";
    private static final String BASE_URL = "https://api.iternio.com/1/tlm/send";

    // API key from Iternio (Telemetry-Only key for Emegelauncher)
    private static final String API_KEY = "32b2162f-9599-4647-8139-66e9f9528370";

    private static volatile AbrpManager sInstance;

    private final SharedPreferences mPrefs;
    private volatile boolean mEnabled = false;
    private volatile String mUserToken = null;
    private long mLastSendTime = 0;
    private static final long SEND_INTERVAL_MS = 5000; // send every 5 seconds max

    // Cached telemetry values (updated by polling loop)
    private double mLat, mLon;
    private float mSpeed, mSoc, mSoh;
    private float mPowerKw, mVoltage, mCurrent;
    private float mExtTemp, mBattTemp, mCabinTemp;
    private float mElevation, mHeading;
    private float mOdometer, mEstRange;
    private boolean mIsCharging, mIsDcfc, mIsParked;
    private float mTirePressureFl, mTirePressureFr, mTirePressureRl, mTirePressureRr;
    private float mBatteryCapacity = 70.0f; // updated from cloud

    public static AbrpManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (AbrpManager.class) {
                if (sInstance == null) sInstance = new AbrpManager(context.getApplicationContext());
            }
        }
        return sInstance;
    }

    private Context mContext;

    private AbrpManager(Context context) {
        mContext = context;
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mUserToken = mPrefs.getString("abrp_token", null);
        mEnabled = mPrefs.getBoolean("abrp_enabled", false);
        mBatteryCapacity = mPrefs.getFloat("battery_capacity_kwh", 70.0f);
    }

    /** Check if ABRP integration is configured and enabled */
    public boolean isEnabled() {
        return mEnabled && mUserToken != null && !mUserToken.isEmpty() && !API_KEY.isEmpty();
    }

    /** Check if API key is configured (not empty placeholder) */
    public boolean hasApiKey() {
        return !API_KEY.isEmpty();
    }

    /** Get send statistics for UI display */
    public int getSendCount() { return mSendCount; }
    public int getFailCount() { return mFailCount; }
    public long getLastSendTime() { return mLastSendTime; }
    private int mFailCount = 0;

    /** Enable/disable ABRP telemetry */
    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        mPrefs.edit().putBoolean("abrp_enabled", enabled).apply();
        Log.i(TAG, "ABRP enabled=" + enabled + " isEnabled()=" + isEnabled());
    }

    /** Set user token (from ABRP app Live Data settings) */
    public void setUserToken(String token) {
        mUserToken = token;
        Log.i(TAG, "ABRP token set: " + (token != null ? token.substring(0, Math.min(8, token.length())) + "..." : "null"));
        mPrefs.edit().putString("abrp_token", token).apply();
    }

    /** Get current user token */
    public String getUserToken() {
        return mUserToken;
    }

    /** Update all telemetry values at once from the polling loop */
    public void updateTelemetry(double lat, double lon, float speed, float soc,
                                 float powerKw, float voltage, float current,
                                 float extTemp, float battTemp, float cabinTemp,
                                 float elevation, float heading, float odometer,
                                 float estRange, float soh, boolean isCharging,
                                 boolean isDcfc, boolean isParked, float tpFl,
                                 float tpFr, float tpRl, float tpRr) {
        mLat = lat;
        mLon = lon;
        mSpeed = speed;
        mSoc = soc;
        mPowerKw = powerKw;
        mVoltage = voltage;
        mCurrent = current;
        mExtTemp = extTemp;
        mBattTemp = battTemp;
        mCabinTemp = cabinTemp;
        mElevation = elevation;
        mHeading = heading;
        mOdometer = odometer;
        mEstRange = estRange;
        mSoh = soh;
        mIsCharging = isCharging;
        mIsDcfc = isDcfc;
        mIsParked = isParked;
        mTirePressureFl = tpFl;
        mTirePressureFr = tpFr;
        mTirePressureRl = tpRl;
        mTirePressureRr = tpRr;
    }

    /**
     * Send telemetry to ABRP if enough time has passed since last send.
     * Called from the polling loop — handles rate limiting internally.
     * Runs the HTTP request on a background thread.
     */
    private int mSendCount = 0;

    public void trySend() {
        if (!isEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - mLastSendTime < SEND_INTERVAL_MS) return;
        mLastSendTime = now;
        mSendCount++;

        // Build telemetry JSON
        try {
            JSONObject tlm = new JSONObject();
            tlm.put("utc", now / 1000);
            tlm.put("soc", mSoc);
            tlm.put("speed", mSpeed);
            tlm.put("power", mPowerKw);
            tlm.put("lat", mLat);
            tlm.put("lon", mLon);
            tlm.put("is_charging", mIsCharging);
            tlm.put("is_dcfc", mIsDcfc);
            tlm.put("is_parked", mIsParked);
            tlm.put("capacity", mBatteryCapacity);
            tlm.put("voltage", mVoltage);
            tlm.put("current", mCurrent);
            if (mSoh > 0) tlm.put("soh", mSoh);
            if (mExtTemp != 0) tlm.put("ext_temp", mExtTemp);
            if (mBattTemp != 0) tlm.put("batt_temp", mBattTemp);
            if (mCabinTemp != 0) tlm.put("cabin_temp", mCabinTemp);
            if (mElevation != 0) tlm.put("elevation", mElevation);
            if (mHeading != 0) tlm.put("heading", mHeading);
            if (mOdometer > 0) tlm.put("odometer", mOdometer);
            if (mEstRange > 0) tlm.put("est_battery_range", mEstRange);
            // Tire pressures already in kPa from VHAL
            if (mTirePressureFl > 0) tlm.put("tire_pressure_fl", mTirePressureFl);
            if (mTirePressureFr > 0) tlm.put("tire_pressure_fr", mTirePressureFr);
            if (mTirePressureRl > 0) tlm.put("tire_pressure_rl", mTirePressureRl);
            if (mTirePressureRr > 0) tlm.put("tire_pressure_rr", mTirePressureRr);

            String tlmStr = tlm.toString();
            sendAsync(tlmStr);
        } catch (Exception e) {
            Log.e(TAG, "Build telemetry failed", e);
        }
    }

    private void sendAsync(String tlmJson) {
        new Thread(() -> {
            try {
                String urlStr = BASE_URL
                    + "?api_key=" + URLEncoder.encode(API_KEY, "UTF-8")
                    + "&token=" + URLEncoder.encode(mUserToken, "UTF-8")
                    + "&tlm=" + URLEncoder.encode(tlmJson, "UTF-8");

                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    String body = sb.toString();
                    JSONObject resp = new JSONObject(body);
                    String status = resp.optString("status", "unknown");
                    FileLogger fl = FileLogger.getInstance(mContext);
                    if (!"ok".equals(status)) {
                        fl.w(TAG, "ABRP error: " + body);
                        mFailCount++;
                    } else {
                        fl.i(TAG, "ABRP OK #" + mSendCount + " soc=" + mSoc + " spd=" + mSpeed + " lat=" + mLat);
                    }
                    mPrefs.edit().putInt("abrp_send_count", mSendCount)
                        .putInt("abrp_fail_count", mFailCount).apply();
                } else {
                    // Read error body too
                    String errBody = "";
                    try {
                        BufferedReader er = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                        StringBuilder esb = new StringBuilder();
                        String el;
                        while ((el = er.readLine()) != null) esb.append(el);
                        er.close();
                        errBody = esb.toString();
                    } catch (Exception ignored) {}
                    FileLogger.getInstance(mContext).w(TAG, "ABRP HTTP " + code + ": " + errBody);
                    mFailCount++;
                }
                conn.disconnect();
            } catch (Exception e) {
                FileLogger.getInstance(mContext).e(TAG, "ABRP send failed: " + e.getMessage());
                mFailCount++;
            }
        }).start();
    }
}

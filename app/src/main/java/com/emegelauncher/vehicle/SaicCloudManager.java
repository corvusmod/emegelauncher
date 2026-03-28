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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Queries MG iSMART cloud API for data not available locally on head unit.
 * Uses same auth/encryption as official MG app (NewMGRemote).
 * Queries ONCE on car start, then only on manual refresh.
 */
public class SaicCloudManager {
    private static final String TAG = "SaicCloudManager";
    private static final String BASE_URL = "https://gateway-mg-eu.soimt.com/api.app/v1/";
    private static final String TENANT_ID = "459771";
    private static final String USER_TYPE = "app";
    private static final String BASIC_AUTH = "Basic c3dvcmQ6c3dvcmRfc2VjcmV0";
    private static final String CONTENT_ENCRYPTED = "1";
    private static final String PREFS = "saic_cloud";

    private final Context mContext;
    private final SharedPreferences mPrefs;
    private String mToken;
    private String mVin;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // Cached cloud data
    private volatile int mInteriorTemp = Integer.MIN_VALUE;
    private volatile int mBatteryVoltage = Integer.MIN_VALUE;
    private volatile int mExteriorTemp = Integer.MIN_VALUE;
    private volatile int mMileageOfDay = -1;
    private volatile int mMileageSinceLastCharge = -1;
    private volatile int mCurrentJourneyDistance = -1;
    private volatile long mLastQueryTime = 0;
    private volatile boolean mQuerying = false;

    public interface CloudCallback {
        void onResult(boolean success, String message);
    }

    public SaicCloudManager(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = mContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        mToken = mPrefs.getString("token", null);
        mVin = mPrefs.getString("vin", null);
    }

    public boolean isLoggedIn() { return mToken != null && !mToken.isEmpty(); }
    public boolean hasData() { return mLastQueryTime > 0; }
    public int getInteriorTemp() { return mInteriorTemp; }
    public int getBatteryVoltage() { return mBatteryVoltage; }
    public int getExteriorTemp() { return mExteriorTemp; }
    public int getMileageOfDay() { return mMileageOfDay; }
    public int getMileageSinceLastCharge() { return mMileageSinceLastCharge; }
    public int getCurrentJourneyDistance() { return mCurrentJourneyDistance; }
    public long getLastQueryTime() { return mLastQueryTime; }
    public String getVin() { return mVin; }

    public void login(String username, String password, CloudCallback cb) {
        new Thread(() -> {
            try {
                String passwordHash = sha1(password.trim());
                long ts = System.currentTimeMillis() / 1000;
                String deviceId = "emegelauncher*****************************************" + ts + "###com.saicmotor.europecar";
                int loginType = username.contains("@") ? 2 : 1;

                String body = "grant_type=password"
                    + "&username=" + URLEncoder.encode(username.trim(), "UTF-8")
                    + "&password=" + passwordHash
                    + "&scope=all"
                    + "&deviceId=" + URLEncoder.encode(deviceId, "UTF-8")
                    + "&deviceType=0&language=EN&loginType=" + loginType;

                String resp = doPost("/oauth/token", body, "application/x-www-form-urlencoded", true);
                if (resp == null) { post(cb, false, "Network error"); return; }

                JSONObject json = new JSONObject(resp);
                if (json.optInt("code", -1) != 0) {
                    post(cb, false, json.optString("message", "Login failed"));
                    return;
                }
                JSONObject data = json.optJSONObject("data");
                if (data == null || !data.has("access_token")) {
                    post(cb, false, "No token in response");
                    return;
                }
                mToken = data.getString("access_token");
                mPrefs.edit().putString("token", mToken)
                    .putString("login_user", username.trim())
                    .putString("login_pass", password.trim())
                    .apply();

                // Get VIN
                fetchVin();
                post(cb, true, "Logged in" + (mVin != null ? " (VIN: ..." + mVin.substring(Math.max(0, mVin.length() - 4)) + ")" : ""));
            } catch (Exception e) {
                Log.e(TAG, "Login failed", e);
                post(cb, false, "Error: " + e.getMessage());
            }
        }).start();
    }

    private void fetchVin() {
        try {
            String resp = doGet("/vehicle/list");
            if (resp == null) return;
            JSONObject json = new JSONObject(resp);
            JSONObject data = json.optJSONObject("data");
            if (data == null) return;
            org.json.JSONArray vinList = data.optJSONArray("vinList");
            if (vinList != null && vinList.length() > 0) {
                mVin = vinList.getJSONObject(0).getString("vin");
                mPrefs.edit().putString("vin", mVin).apply();
                Log.d(TAG, "VIN: " + mVin);
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchVin failed", e);
        }
    }

    public void queryVehicleStatus(CloudCallback cb) {
        if (!isLoggedIn()) { if (cb != null) post(cb, false, "Not logged in"); return; }
        if (mVin == null) { if (cb != null) post(cb, false, "No VIN"); return; }
        if (mQuerying) { if (cb != null) post(cb, false, "Query in progress"); return; }
        mQuerying = true;

        new Thread(() -> {
            try {
                String hashedVin = sha256(mVin);
                String path = "/vehicle/status?vin=" + URLEncoder.encode(hashedVin, "UTF-8")
                    + "&vehStatusReqType=2";

                // First request — may return event-id for polling (NewMGRemote sends "0" initially)
                String[] respAndEventId = doRequestWithEventId("GET", path, null, "application/json", "0", true);
                if (respAndEventId == null || respAndEventId[0] == null) {
                    mQuerying = false; post(cb, false, "Network error"); return;
                }

                String resp = respAndEventId[0];
                String returnedEventId = respAndEventId[1];

                // If response has event-id, poll with it up to 10 times
                if (returnedEventId != null && !returnedEventId.isEmpty()) {
                    JSONObject firstJson = new JSONObject(resp);
                    JSONObject firstData = firstJson.optJSONObject("data");
                    if (firstData == null || !firstData.has("basicVehicleStatus")) {
                        for (int retry = 0; retry < 10; retry++) {
                            Thread.sleep(1000);
                            String[] retryResp = doRequestWithEventId("GET", path, null, "application/json", returnedEventId, true);
                            if (retryResp != null && retryResp[0] != null) {
                                JSONObject rj = new JSONObject(retryResp[0]);
                                JSONObject rd = rj.optJSONObject("data");
                                if (rd != null && rd.has("basicVehicleStatus")) {
                                    resp = retryResp[0];
                                    break;
                                }
                            }
                        }
                    }
                }

                JSONObject json = new JSONObject(resp);
                int apiCode = json.optInt("code", -1);
                String apiMsg = json.optString("message", "");
                Log.d(TAG, "API response code=" + apiCode + " msg=" + apiMsg + " eventId=" + returnedEventId);
                if (apiCode != 0) {
                    mQuerying = false;
                    post(cb, false, "API error " + apiCode + ": " + apiMsg);
                    return;
                }
                JSONObject data = json.optJSONObject("data");
                if (data == null) { mQuerying = false; post(cb, false, "No data in response"); return; }

                JSONObject bvs = data.optJSONObject("basicVehicleStatus");
                if (bvs != null) {
                    if (bvs.has("interiorTemperature")) mInteriorTemp = bvs.getInt("interiorTemperature");
                    if (bvs.has("batteryVoltage")) mBatteryVoltage = bvs.getInt("batteryVoltage");
                    if (bvs.has("exteriorTemperature")) mExteriorTemp = bvs.getInt("exteriorTemperature");
                    if (bvs.has("mileageOfDay")) mMileageOfDay = bvs.getInt("mileageOfDay");
                    if (bvs.has("mileageSinceLastCharge")) mMileageSinceLastCharge = bvs.getInt("mileageSinceLastCharge");
                    if (bvs.has("currentJourneyDistance")) mCurrentJourneyDistance = bvs.getInt("currentJourneyDistance");
                }
                mLastQueryTime = System.currentTimeMillis();
                mQuerying = false;
                mPrefs.edit().putString("last_full_status", data.toString()).apply();

                StringBuilder msg = new StringBuilder("Cloud data received");
                if (mInteriorTemp != Integer.MIN_VALUE) msg.append(", cabin ").append(mInteriorTemp).append("°C");
                if (mBatteryVoltage != Integer.MIN_VALUE) msg.append(", 12V ").append(String.format("%.1f", mBatteryVoltage * 0.25)).append("V");
                post(cb, true, msg.toString());
            } catch (Exception e) {
                mQuerying = false;
                Log.e(TAG, "queryVehicleStatus failed", e);
                post(cb, false, "Error: " + e.getMessage());
            }
        }).start();
    }

    // ==================== Statistics ====================

    /**
     * Query driving statistics from cloud.
     * @param rangeType "1"=day, "2"=month, "3"=year
     * @param rangeTime "yyyy-MM-dd" format date
     */
    public void queryStatistics(String rangeType, String rangeTime, CloudCallback cb) {
        if (!isLoggedIn() || mVin == null) { post(cb, false, "Not connected"); return; }
        new Thread(() -> {
            try {
                // type "1,2,3,4,5" = all stats (mileage, consumption, co2, speed, travel time)
                String path = "/vehicle/statisticsInfo?vin=" + ue(sha256(mVin))
                    + "&type=" + ue("1,2,3,4,5")
                    + "&rangeType=" + ue(rangeType)
                    + "&rangeTime=" + ue(rangeTime);
                Log.d(TAG, "queryStatistics path=" + path);
                String resp = doGet(path);
                if (resp == null) { post(cb, false, "Network error"); return; }
                Log.d(TAG, "queryStatistics response length=" + resp.length());
                // Cache by rangeType
                mPrefs.edit().putString("stats_" + rangeType, resp)
                    .putLong("stats_time", System.currentTimeMillis()).apply();
                post(cb, true, resp);
            } catch (Exception e) { post(cb, false, e.getMessage()); }
        }).start();
    }

    /** Get cached stats JSON for a range type ("1"=day, "2"=month, "3"=year) */
    public String getCachedStats(String rangeType) {
        return mPrefs.getString("stats_" + rangeType, null);
    }

    // ==================== BT Digital Keys ====================

    public void queryBtKeys(CloudCallback cb) {
        if (!isLoggedIn()) { post(cb, false, "Not logged in"); return; }
        new Thread(() -> {
            try {
                String resp = doGet("/vehicle/btkey/userKeyList");
                if (resp == null) { post(cb, false, "Network error"); return; }
                mPrefs.edit().putString("bt_keys", resp).apply();
                post(cb, true, resp);
            } catch (Exception e) { post(cb, false, e.getMessage()); }
        }).start();
    }

    public String getCachedBtKeys() { return mPrefs.getString("bt_keys", null); }

    // ==================== Messages ====================

    public void queryMessages(String group, CloudCallback cb) {
        if (!isLoggedIn()) { post(cb, false, "Not logged in"); return; }
        new Thread(() -> {
            try {
                String resp = doGet("/message/list?messageGroup=" + ue(group) + "&pageNum=1&pageSize=20");
                if (resp == null) { post(cb, false, "Network error"); return; }
                mPrefs.edit().putString("messages_" + group, resp).apply();
                post(cb, true, resp);
            } catch (Exception e) { post(cb, false, e.getMessage()); }
        }).start();
    }

    public String getCachedMessages(String group) { return mPrefs.getString("messages_" + group, null); }

    // ==================== FOTA ====================

    public void queryFota(CloudCallback cb) {
        if (!isLoggedIn() || mVin == null) { post(cb, false, "Not connected"); return; }
        new Thread(() -> {
            try {
                String resp = doGet("/fota/campaign?vin=" + ue(sha256(mVin)));
                if (resp == null) { post(cb, false, "Network error"); return; }
                mPrefs.edit().putString("fota", resp).apply();
                post(cb, true, resp);
            } catch (Exception e) { post(cb, false, e.getMessage()); }
        }).start();
    }

    public String getCachedFota() { return mPrefs.getString("fota", null); }

    // ==================== TBox Status ====================

    public void queryTboxStatus(CloudCallback cb) {
        if (!isLoggedIn() || mVin == null) { post(cb, false, "Not connected"); return; }
        new Thread(() -> {
            try {
                String resp = doGet("/vehicle/tbox/onlineStatus?vin=" + ue(sha256(mVin)));
                if (resp == null) { post(cb, false, "Network error"); return; }
                mPrefs.edit().putString("tbox_status", resp).apply();
                post(cb, true, resp);
            } catch (Exception e) { post(cb, false, e.getMessage()); }
        }).start();
    }

    public String getCachedTboxStatus() { return mPrefs.getString("tbox_status", null); }

    // ==================== Vehicle Features ====================

    public void queryFeatures(CloudCallback cb) {
        if (!isLoggedIn() || mVin == null) { post(cb, false, "Not connected"); return; }
        new Thread(() -> {
            try {
                String resp = doGet("/vehicle/featureList?vin=" + ue(sha256(mVin)));
                if (resp == null) { post(cb, false, "Network error"); return; }
                mPrefs.edit().putString("features", resp).apply();
                post(cb, true, resp);
            } catch (Exception e) { post(cb, false, e.getMessage()); }
        }).start();
    }

    public String getCachedFeatures() { return mPrefs.getString("features", null); }

    // ==================== Charging Data ====================

    public void queryCharging(CloudCallback cb) {
        if (!isLoggedIn() || mVin == null) { post(cb, false, "Not connected"); return; }
        new Thread(() -> {
            try {
                String path = "/vehicle/charging/mgmtData?vin=" + ue(sha256(mVin));
                FileLogger fl = FileLogger.getInstance(mContext);

                // First request with event-id "0" (same pattern as queryVehicleStatus)
                String[] firstResp = doRequestWithEventId("GET", path, null, "application/json", "0", true);
                if (firstResp == null || firstResp[0] == null) {
                    fl.w(TAG, "queryCharging: network error on first request");
                    post(cb, false, "Network error"); return;
                }

                String resp = firstResp[0];
                String returnedEventId = firstResp[1];

                // Check if we got data immediately
                JSONObject json = new JSONObject(resp);
                int code = json.optInt("code", -1);
                JSONObject data = json.optJSONObject("data");

                // If no data yet, poll with returned event-id (up to 10 retries)
                if ((code != 0 || data == null) && returnedEventId != null && !returnedEventId.isEmpty()) {
                    fl.d(TAG, "queryCharging: polling with event-id=" + returnedEventId + " (code=" + code + ")");
                    for (int retry = 0; retry < 10; retry++) {
                        Thread.sleep(2000);
                        String[] retryResp = doRequestWithEventId("GET", path, null, "application/json", returnedEventId, true);
                        if (retryResp != null && retryResp[0] != null) {
                            JSONObject retryJson = new JSONObject(retryResp[0]);
                            int retryCode = retryJson.optInt("code", -1);
                            JSONObject retryData = retryJson.optJSONObject("data");
                            if (retryCode == 0 && retryData != null) {
                                resp = retryResp[0];
                                code = retryCode;
                                fl.i(TAG, "queryCharging: got data on retry " + (retry + 1));
                                break;
                            }
                            // Update event-id if new one returned
                            if (retryResp[1] != null && !retryResp[1].isEmpty()) {
                                returnedEventId = retryResp[1];
                            }
                        }
                    }
                    // Re-parse final response
                    json = new JSONObject(resp);
                    code = json.optInt("code", -1);
                }

                if (code != 0) {
                    String msg = json.optString("message", "Unknown error");
                    fl.w(TAG, "queryCharging failed: code=" + code + " msg=" + msg);
                    post(cb, false, "Cloud: " + msg + " (code " + code + ")");
                    return;
                }

                fl.i(TAG, "queryCharging OK, processing session data");
                mPrefs.edit().putString("charging", resp).apply();
                processChargeSession(resp);
                post(cb, true, resp);
            } catch (Exception e) {
                FileLogger.getInstance(mContext).e(TAG, "queryCharging exception: " + e.getMessage());
                post(cb, false, e.getMessage());
            }
        }).start();
    }

    public String getCachedCharging() { return mPrefs.getString("charging", null); }

    /**
     * Parse the latest cloud charge session and store it in local history.
     * Called after queryCharging succeeds. Extracts RvsChargeStatus + ChrgMgmtData.
     * Only stores if startTime is new (not already in history).
     */
    public void processChargeSession(String chargingJson) {
        try {
            Log.d(TAG, "processChargeSession: response length=" + (chargingJson != null ? chargingJson.length() : 0));
            JSONObject json = new JSONObject(chargingJson);
            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                Log.w(TAG, "processChargeSession: no 'data' in response. Keys: " + json.keys());
                return;
            }
            JSONObject rvsData = data.optJSONObject("rvsChargeStatus");
            JSONObject mgmtData = data.optJSONObject("chrgMgmtData");
            Log.d(TAG, "processChargeSession: rvsChargeStatus=" + (rvsData != null) + " chrgMgmtData=" + (mgmtData != null));
            FileLogger.getInstance(mContext).i(TAG, "processChargeSession: rvs=" + (rvsData != null) + " mgmt=" + (mgmtData != null));
            if (rvsData == null && mgmtData == null) {
                Log.w(TAG, "processChargeSession: no rvsChargeStatus or chrgMgmtData. Data keys: " + data.keys());
                return;
            }

            // Build a session record from cloud data — all values stored as raw
            JSONObject session = new JSONObject();
            if (rvsData != null) {
                session.put("startTime", rvsData.optLong("startTime", 0));
                session.put("endTime", rvsData.optLong("endTime", 0));
                session.put("chargingDuration", rvsData.optInt("chargingDuration", 0));
                session.put("chargingType", rvsData.optInt("chargingType", 0));
                session.put("chargingCurrent", rvsData.optInt("chargingCurrent", 0));
                session.put("chargingVoltage", rvsData.optInt("chargingVoltage", 0));
                session.put("realTimePower", rvsData.optInt("realTimePower", 0));
                session.put("totalBatteryCapacity", rvsData.optInt("totalBatteryCapacity", 0));
                session.put("lastChargeEndingPower", rvsData.optInt("lastChargeEndingPower", 0));
                session.put("workingVoltage", rvsData.optInt("workingVoltage", 0));
                session.put("workingCurrent", rvsData.optInt("workingCurrent", 0));
                session.put("mileageSinceLastCharge", rvsData.optInt("mileageSinceLastCharge", 0));
                session.put("mileageOfDay", rvsData.optInt("mileageOfDay", 0));
                session.put("mileage", rvsData.optInt("mileage", 0));
                session.put("powerUsageSinceLastCharge", rvsData.optInt("powerUsageSinceLastCharge", 0));
                session.put("powerUsageOfDay", rvsData.optInt("powerUsageOfDay", 0));
                session.put("fuelRangeElec", rvsData.optInt("fuelRangeElec", 0));
                session.put("chargingGunState", rvsData.optInt("chargingGunState", 0));

                // Save real battery capacity for SOH and ABRP
                int rawCap = rvsData.optInt("totalBatteryCapacity", 0);
                if (rawCap > 0) {
                    float realCapKwh = rawCap / 10.0f;
                    mPrefs.edit().putFloat("battery_capacity_kwh", realCapKwh).apply();
                    Log.d(TAG, "Battery capacity from cloud: " + realCapKwh + " kWh");
                }
            }
            if (mgmtData != null) {
                // BMS data — store decoded values
                int rawPackCrnt = mgmtData.optInt("bmsPackCrnt", 0);
                int rawPackVol = mgmtData.optInt("bmsPackVol", 0);
                session.put("bmsPackCrnt", rawPackCrnt * 0.05 - 1000);
                session.put("bmsPackVol", rawPackVol * 0.25);
                session.put("bmsPackSOCDsp", mgmtData.optInt("bmsPackSOCDsp", 0) / 10.0);
                session.put("bmsEstdElecRng", mgmtData.optInt("bmsEstdElecRng", 0));
                session.put("bmsChrgSts", mgmtData.optInt("bmsChrgSts", 0));
                session.put("chrgngRmnngTime", mgmtData.optInt("chrgngRmnngTime", 0));
                session.put("bmsChrgSpRsn", mgmtData.optInt("bmsChrgSpRsn", 0));
                session.put("chrgngAddedElecRng", mgmtData.optInt("chrgngAddedElecRng", 0));
                session.put("bmsOnBdChrgTrgtSOCDspCmd", mgmtData.optInt("bmsOnBdChrgTrgtSOCDspCmd", 0));
                session.put("bmsReserCtrlDspCmd", mgmtData.optInt("bmsReserCtrlDspCmd", 0));
            }
            session.put("fetchTime", System.currentTimeMillis());

            // Store in history if new (check startTime uniqueness)
            storeChargeSession(session);
        } catch (Exception e) {
            Log.e(TAG, "processChargeSession failed", e);
        }
    }

    private void storeChargeSession(JSONObject session) {
        try {
            String historyStr = mPrefs.getString("charge_history", "[]");
            org.json.JSONArray history = new org.json.JSONArray(historyStr);

            long startTime = session.optLong("startTime", 0);
            long fetchTime = session.optLong("fetchTime", 0);

            // Check for duplicate (same startTime)
            for (int i = 0; i < history.length(); i++) {
                JSONObject existing = history.getJSONObject(i);
                if (existing.optLong("startTime", -1) == startTime && startTime > 0) {
                    // Update existing with newer data
                    history.put(i, session);
                    mPrefs.edit().putString("charge_history", history.toString()).apply();
                    Log.d(TAG, "Updated existing charge session: startTime=" + startTime);
                    return;
                }
            }

            // Add new session (at the beginning for newest-first)
            org.json.JSONArray newHistory = new org.json.JSONArray();
            newHistory.put(session);
            for (int i = 0; i < Math.min(history.length(), 149); i++) { // keep max 150
                newHistory.put(history.getJSONObject(i));
            }
            mPrefs.edit().putString("charge_history", newHistory.toString()).apply();
            Log.d(TAG, "Stored new charge session: startTime=" + startTime + " total=" + newHistory.length());
        } catch (Exception e) {
            Log.e(TAG, "storeChargeSession failed", e);
        }
    }

    /** Get stored charge session history (from cloud snapshots, newest first) */
    public org.json.JSONArray getChargeHistory() {
        try {
            return new org.json.JSONArray(mPrefs.getString("charge_history", "[]"));
        } catch (Exception e) { return new org.json.JSONArray(); }
    }

    /** Get real battery capacity from cloud (÷10 already applied), or default 70 kWh */
    public float getBatteryCapacityKwh() {
        return mPrefs.getFloat("battery_capacity_kwh", 70.0f);
    }

    // ==================== Force Status Refresh (wake TBox) ====================

    /** Try to re-login with stored credentials (called on 401) */
    private boolean tryReLogin() {
        String user = mPrefs.getString("login_user", null);
        String pass = mPrefs.getString("login_pass", null);
        if (user == null || pass == null) {
            Log.w(TAG, "No stored credentials for re-login");
            mToken = null;
            mPrefs.edit().remove("token").apply();
            return false;
        }
        try {
            Log.d(TAG, "Re-logging in as: " + user);
            String passwordHash = sha1(pass);
            long ts = System.currentTimeMillis() / 1000;
            String deviceId = "emegelauncher*****************************************" + ts + "###com.saicmotor.europecar";
            int loginType = user.contains("@") ? 2 : 1;
            String body = "grant_type=password"
                + "&username=" + URLEncoder.encode(user, "UTF-8")
                + "&password=" + passwordHash
                + "&scope=all"
                + "&deviceId=" + URLEncoder.encode(deviceId, "UTF-8")
                + "&deviceType=0&language=EN&loginType=" + loginType;

            // Use a direct HTTP call (not doPost which would recurse on 401)
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(BASE_URL + "oauth/token").openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            String sendDate = String.valueOf(System.currentTimeMillis());
            String resourcePath = "/oauth/token";
            conn.setRequestProperty("tenant-id", TENANT_ID);
            conn.setRequestProperty("user-type", USER_TYPE);
            conn.setRequestProperty("app-send-date", sendDate);
            conn.setRequestProperty("app-content-encrypted", CONTENT_ENCRYPTED);
            conn.setRequestProperty("original-content-type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Authorization", BASIC_AUTH);
            String encBody = encryptRequest(resourcePath, sendDate, "", body, "application/x-www-form-urlencoded");
            String verification = calculateVerification(resourcePath, sendDate, "application/x-www-form-urlencoded", encBody, "");
            conn.setRequestProperty("app-verification-string", verification);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(encBody.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                String respBody = sb.toString();
                // Decrypt
                String respEnc = conn.getHeaderField("app-content-encrypted");
                if ("1".equals(respEnc)) {
                    String respDate = conn.getHeaderField("app-send-date");
                    String respType = conn.getHeaderField("original-content-type");
                    if (respDate == null) respDate = sendDate;
                    if (respType == null) respType = "application/json";
                    String dec = decryptResponse(respDate, respType, respBody);
                    if (dec != null) respBody = dec;
                }
                JSONObject json = new JSONObject(respBody);
                if (json.optInt("code", -1) == 0) {
                    JSONObject data = json.optJSONObject("data");
                    if (data != null && data.has("access_token")) {
                        mToken = data.getString("access_token");
                        mPrefs.edit().putString("token", mToken).apply();
                        Log.i(TAG, "Re-login successful, new token obtained");
                        conn.disconnect();
                        return true;
                    }
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Re-login failed", e);
        }
        mToken = null;
        mPrefs.edit().remove("token").apply();
        return false;
    }

    /** Refresh data — in-car mode, no TBox wake needed, just query directly */
    public void forceRefresh(CloudCallback cb) {
        queryVehicleStatus(cb);
    }

    // ==================== Find My Car ====================

    public void findMyCar(int mode, CloudCallback cb) {
        // mode: 1=horn+lights, 2=lights only
        if (!isLoggedIn() || mVin == null) { post(cb, false, "Not connected"); return; }
        new Thread(() -> {
            try {
                String eventId = String.valueOf(System.currentTimeMillis());
                JSONObject body = new JSONObject();
                body.put("rvcReqType", "0");
                body.put("vin", sha256(mVin));
                org.json.JSONArray params = new org.json.JSONArray();
                JSONObject p = new JSONObject();
                p.put("paramId", 10);
                p.put("paramValue", android.util.Base64.encodeToString(new byte[]{(byte) mode}, android.util.Base64.NO_WRAP));
                params.put(p);
                body.put("rvcParams", params);
                String resp = doPostWithEvent("/vehicle/control", body.toString(), "application/json", eventId);
                post(cb, resp != null, resp != null ? "Find My Car sent" : "Failed");
            } catch (Exception e) { post(cb, false, e.getMessage()); }
        }).start();
    }

    // ==================== Air Clean (RVC type 7) ====================

    public void controlAirClean(boolean on, CloudCallback cb) {
        sendRvc("7", null, cb, on ? "Air clean started" : "Air clean stopped");
    }

    // ==================== Cloud Charging Controls ====================

    public void controlCharging(int chrgCtrlReq, CloudCallback cb) {
        // 1=start, 2=stop
        if (!isLoggedIn() || mVin == null) { post(cb, false, "Not connected"); return; }
        new Thread(() -> {
            try {
                String eventId = String.valueOf(System.currentTimeMillis());
                JSONObject body = new JSONObject();
                body.put("chrgCtrlReq", chrgCtrlReq);
                body.put("tboxV2XReq", 0);
                body.put("tboxEleccLckCtrlReq", 0);
                body.put("vin", sha256(mVin));
                String resp = doPostWithEvent("/vehicle/charging/control", body.toString(), "application/json", eventId);
                post(cb, resp != null, resp != null ? "Charging control sent" : "Failed");
            } catch (Exception e) { post(cb, false, e.getMessage()); }
        }).start();
    }

    public void setChargingSettings(int currentLimit, int targetSoc, CloudCallback cb) {
        if (!isLoggedIn() || mVin == null) { post(cb, false, "Not connected"); return; }
        new Thread(() -> {
            try {
                String eventId = String.valueOf(System.currentTimeMillis());
                JSONObject body = new JSONObject();
                body.put("altngChrgCrntReq", currentLimit);
                body.put("onBdChrgTrgtSOCReq", targetSoc);
                body.put("tboxV2XSpSOCReq", 0);
                body.put("vin", sha256(mVin));
                String resp = doPostWithEvent("/vehicle/charging/setting", body.toString(), "application/json", eventId);
                post(cb, resp != null, resp != null ? "Charging settings updated" : "Failed");
            } catch (Exception e) { post(cb, false, e.getMessage()); }
        }).start();
    }

    public void setScheduledCharging(int startH, int startM, int stopH, int stopM, boolean enable, CloudCallback cb) {
        if (!isLoggedIn() || mVin == null) { post(cb, false, "Not connected"); return; }
        new Thread(() -> {
            try {
                String eventId = String.valueOf(System.currentTimeMillis());
                JSONObject body = new JSONObject();
                body.put("rsvanStHour", startH);
                body.put("rsvanStMintue", startM);
                body.put("rsvanSpHour", stopH);
                body.put("rsvanSpMintue", stopM);
                body.put("tboxReserCtrlReq", enable ? 1 : 2);
                body.put("tboxAdpPubChrgSttnReq", 0);
                body.put("vin", sha256(mVin));
                String resp = doPostWithEvent("/vehicle/charging/reservation", body.toString(), "application/json", eventId);
                post(cb, resp != null, resp != null ? "Scheduled charging updated" : "Failed");
            } catch (Exception e) { post(cb, false, e.getMessage()); }
        }).start();
    }

    public void controlV2L(int chrgCtrlReq, CloudCallback cb) {
        // Same endpoint, tboxV2XReq controls V2L
        if (!isLoggedIn() || mVin == null) { post(cb, false, "Not connected"); return; }
        new Thread(() -> {
            try {
                String eventId = String.valueOf(System.currentTimeMillis());
                JSONObject body = new JSONObject();
                body.put("chrgCtrlReq", 0);
                body.put("tboxV2XReq", chrgCtrlReq);
                body.put("tboxEleccLckCtrlReq", 0);
                body.put("vin", sha256(mVin));
                String resp = doPostWithEvent("/vehicle/charging/control", body.toString(), "application/json", eventId);
                post(cb, resp != null, resp != null ? "V2L control sent" : "Failed");
            } catch (Exception e) { post(cb, false, e.getMessage()); }
        }).start();
    }

    // ==================== BT Digital Key Management ====================

    public void activateBtKey(long keyRef, String keyTag, int keyStatus, CloudCallback cb) {
        // keyStatus: 1=activate, 0=deactivate, 3=revoke
        if (!isLoggedIn()) { post(cb, false, "Not logged in"); return; }
        new Thread(() -> {
            try {
                String eventId = String.valueOf(System.currentTimeMillis());
                JSONObject body = new JSONObject();
                body.put("keyReference", keyRef);
                body.put("keyTag", keyTag);
                body.put("keyStatus", keyStatus);
                body.put("vin", sha256(mVin));
                String resp = doPostWithEvent("/vehicle/btkey/activation", body.toString(), "application/json", eventId);
                post(cb, resp != null, resp != null ? "Key updated" : "Failed");
            } catch (Exception e) { post(cb, false, e.getMessage()); }
        }).start();
    }

    // ==================== Send POI to Car Navigation ====================

    public void sendPoiToCar(String name, double lat, double lon, String address, CloudCallback cb) {
        if (!isLoggedIn()) { post(cb, false, "Not logged in"); return; }
        new Thread(() -> {
            try {
                JSONObject poi = new JSONObject();
                poi.put("name", name);
                poi.put("latitude", lat);
                poi.put("longitude", lon);
                poi.put("address", address != null ? address : "");
                String resp = doPost("/navi/poi/send", poi.toString(), "application/json", true);
                post(cb, resp != null, resp != null ? "POI sent to car" : "Failed");
            } catch (Exception e) { post(cb, false, e.getMessage()); }
        }).start();
    }

    public void queryFavoritePois(CloudCallback cb) {
        if (!isLoggedIn()) { post(cb, false, "Not logged in"); return; }
        new Thread(() -> {
            try {
                String resp = doGet("/navi/poi/favorite/list?pageNum=1&pageSize=50");
                if (resp == null) { post(cb, false, "Network error"); return; }
                mPrefs.edit().putString("pois", resp).apply();
                post(cb, true, resp);
            } catch (Exception e) { post(cb, false, e.getMessage()); }
        }).start();
    }

    public String getCachedPois() { return mPrefs.getString("pois", null); }

    // ==================== Geofence ====================

    public void setGeofence(double lat, double lon, int radiusMeters, int alertType, boolean enable, CloudCallback cb) {
        // alertType: 1=enter, 2=exit, 3=both
        if (!isLoggedIn() || mVin == null) { post(cb, false, "Not connected"); return; }
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("alertType", alertType);
                body.put("maxDist", radiusMeters);
                body.put("operationType", enable ? 1 : 3); // 1=create/update, 3=delete
                JSONObject pos = new JSONObject();
                pos.put("latitude", (int)(lat * 1000000));
                pos.put("longitude", (int)(lon * 1000000));
                body.put("position", pos);
                body.put("vin", sha256(mVin));
                String resp = doPost("/vehicle/geofence/setting", body.toString(), "application/json", true);
                post(cb, resp != null, resp != null ? "Geofence updated" : "Failed");
            } catch (Exception e) { post(cb, false, e.getMessage()); }
        }).start();
    }

    public void queryGeofence(CloudCallback cb) {
        if (!isLoggedIn() || mVin == null) { post(cb, false, "Not connected"); return; }
        new Thread(() -> {
            try {
                String resp = doGet("/vehicle/geofence/setting?vin=" + ue(sha256(mVin)));
                if (resp == null) { post(cb, false, "Network error"); return; }
                mPrefs.edit().putString("geofence", resp).apply();
                post(cb, true, resp);
            } catch (Exception e) { post(cb, false, e.getMessage()); }
        }).start();
    }

    public String getCachedGeofence() { return mPrefs.getString("geofence", null); }

    // ==================== Generic RVC ====================

    private void sendRvc(String rvcType, org.json.JSONArray params, CloudCallback cb, String successMsg) {
        if (!isLoggedIn() || mVin == null) { post(cb, false, "Not connected"); return; }
        new Thread(() -> {
            try {
                String eventId = String.valueOf(System.currentTimeMillis());
                JSONObject body = new JSONObject();
                body.put("rvcReqType", rvcType);
                body.put("vin", sha256(mVin));
                if (params != null) body.put("rvcParams", params);
                String resp = doPostWithEvent("/vehicle/control", body.toString(), "application/json", eventId);
                post(cb, resp != null, resp != null ? successMsg : "Failed");
            } catch (Exception e) { post(cb, false, e.getMessage()); }
        }).start();
    }

    // ==================== Full status JSON (for CloudActivity) ====================

    public String getLastFullStatusJson() { return mPrefs.getString("last_full_status", null); }

    public void logout() {
        mToken = null;
        mVin = null;
        mLastQueryTime = 0;
        mInteriorTemp = Integer.MIN_VALUE;
        mBatteryVoltage = Integer.MIN_VALUE;
        mPrefs.edit().clear().apply();
    }

    /** Interior temp in °C — display raw value, conversion TBD based on real data */
    public String getInteriorTempStr() {
        if (mInteriorTemp == Integer.MIN_VALUE) return null;
        return String.valueOf(mInteriorTemp);
    }

    /** 12V battery voltage (raw value ÷ 10) */
    public String getBatteryVoltageStr() {
        if (mBatteryVoltage == Integer.MIN_VALUE) return null;
        return String.format("%.1f", mBatteryVoltage / 10.0);
    }

    // ==================== HTTP ====================

    private String doGet(String path) {
        return doRequest("GET", path, null, "application/json", null, true);
    }

    private String doGetWithEvent(String path, String eventId) {
        return doRequest("GET", path, null, "application/json", eventId, true);
    }

    private String doPost(String path, String body, String contentType, boolean encrypt) {
        return doRequest("POST", path, body, contentType, null, encrypt);
    }

    private String doPostWithEvent(String path, String body, String contentType, String eventId) {
        return doRequest("POST", path, body, contentType, eventId, true);
    }

    private static String ue(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    /**
     * Like doRequest but returns [body, event-id header] so caller can poll with event-id.
     */
    private String[] doRequestWithEventId(String method, String path, String body, String contentType, String eventId, boolean encrypt) {
        HttpURLConnection conn = null;
        try {
            String fullUrl = BASE_URL + (path.startsWith("/") ? path.substring(1) : path);
            Log.d(TAG, "HTTP " + method + " " + fullUrl + " event-id=" + eventId);
            conn = (HttpURLConnection) new URL(fullUrl).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            String sendDate = String.valueOf(System.currentTimeMillis());
            String token = mToken != null ? mToken : "";
            String resourcePath = path;
            if (!resourcePath.startsWith("/")) resourcePath = "/" + resourcePath;

            conn.setRequestProperty("tenant-id", TENANT_ID);
            conn.setRequestProperty("user-type", USER_TYPE);
            conn.setRequestProperty("app-send-date", sendDate);
            conn.setRequestProperty("app-content-encrypted", CONTENT_ENCRYPTED);
            conn.setRequestProperty("original-content-type", contentType);
            conn.setRequestProperty("Authorization", BASIC_AUTH);
            if (!token.isEmpty()) conn.setRequestProperty("blade-auth", token);
            if (eventId != null) conn.setRequestProperty("event-id", eventId);

            String encryptedBody = "";
            if (body != null && !body.isEmpty() && encrypt) {
                encryptedBody = encryptRequest(resourcePath, sendDate, token, body, contentType);
            } else if (body != null) {
                encryptedBody = body;
            }

            String verification = calculateVerification(resourcePath, sendDate, contentType, encryptedBody, token);
            conn.setRequestProperty("app-verification-string", verification);

            if (body != null && !body.isEmpty()) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", contentType);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write((encrypt ? encryptedBody : body).getBytes(StandardCharsets.UTF_8));
                }
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "HTTP " + code + " " + method + " " + path);

            // Handle 401 — token expired, auto re-login with stored credentials
            if (code == 401) {
                Log.w(TAG, "HTTP 401 Unauthorized — attempting auto re-login");
                if (tryReLogin()) {
                    Log.i(TAG, "Re-login successful, but caller must retry the request");
                }
                return null;
            }

            java.io.InputStream responseStream = (code >= 200 && code < 300)
                ? conn.getInputStream() : conn.getErrorStream();
            if (responseStream == null) {
                Log.e(TAG, "No response stream for HTTP " + code);
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            String responseBody = sb.toString();

            // Extract event-id from response header
            String respEventId = conn.getHeaderField("event-id");
            Log.d(TAG, "HTTP response body(" + responseBody.length() + ") event-id=" + respEventId);

            String respEncrypted = conn.getHeaderField("app-content-encrypted");
            if ("1".equals(respEncrypted) && !responseBody.isEmpty()) {
                String respDate = conn.getHeaderField("app-send-date");
                String respType = conn.getHeaderField("original-content-type");
                if (respDate == null) respDate = sendDate;
                if (respType == null) respType = "application/json";
                String decrypted = decryptResponse(respDate, respType, responseBody);
                if (decrypted != null) return new String[]{decrypted, respEventId};
            }
            return new String[]{responseBody, respEventId};
        } catch (Exception e) {
            Log.e(TAG, "HTTP error: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String doRequest(String method, String path, String body, String contentType, String eventId, boolean encrypt) {
        HttpURLConnection conn = null;
        try {
            String fullUrl = BASE_URL + (path.startsWith("/") ? path.substring(1) : path);
            Log.d(TAG, "HTTP " + method + " " + fullUrl + " event-id=" + eventId);
            conn = (HttpURLConnection) new URL(fullUrl).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            String sendDate = String.valueOf(System.currentTimeMillis());
            String token = mToken != null ? mToken : "";
            String resourcePath = path;
            if (!resourcePath.startsWith("/")) resourcePath = "/" + resourcePath;

            // Headers
            conn.setRequestProperty("tenant-id", TENANT_ID);
            conn.setRequestProperty("user-type", USER_TYPE);
            conn.setRequestProperty("app-send-date", sendDate);
            conn.setRequestProperty("app-content-encrypted", CONTENT_ENCRYPTED);
            conn.setRequestProperty("original-content-type", contentType);
            conn.setRequestProperty("Authorization", BASIC_AUTH);
            if (!token.isEmpty()) conn.setRequestProperty("blade-auth", token);
            if (eventId != null) conn.setRequestProperty("event-id", eventId);

            String encryptedBody = "";
            if (body != null && !body.isEmpty() && encrypt) {
                encryptedBody = encryptRequest(resourcePath, sendDate, token, body, contentType);
            } else if (body != null) {
                encryptedBody = body;
            }

            // Verification
            String verification = calculateVerification(resourcePath, sendDate, contentType, encryptedBody, token);
            conn.setRequestProperty("app-verification-string", verification);

            // Body
            if (body != null && !body.isEmpty()) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", contentType);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write((encrypt ? encryptedBody : body).getBytes(StandardCharsets.UTF_8));
                }
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "HTTP response code=" + code + " for " + method + " " + path);

            // Handle 401 — token expired
            if (code == 401) {
                Log.w(TAG, "HTTP 401 Unauthorized — token expired, clearing credentials");
                mToken = null;
                mPrefs.edit().remove("token").apply();
                return null;
            }

            // Handle error responses
            if (code < 200 || code >= 300) {
                java.io.InputStream errStream = conn.getErrorStream();
                if (errStream != null) {
                    BufferedReader errReader = new BufferedReader(new InputStreamReader(errStream, StandardCharsets.UTF_8));
                    StringBuilder errSb = new StringBuilder();
                    String errLine;
                    while ((errLine = errReader.readLine()) != null) errSb.append(errLine);
                    errReader.close();
                    String errBody = errSb.toString();
                    Log.e(TAG, "HTTP error " + code + " body(" + errBody.length() + "): " + errBody.substring(0, Math.min(500, errBody.length())));
                    return null;
                } else {
                    Log.e(TAG, "HTTP error " + code + " (no error stream)");
                    return null;
                }
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(
                conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            String responseBody = sb.toString();
            Log.d(TAG, "HTTP response body(" + responseBody.length() + ")");

            // Decrypt response if encrypted
            String respEncrypted = conn.getHeaderField("app-content-encrypted");
            if ("1".equals(respEncrypted) && !responseBody.isEmpty()) {
                String respDate = conn.getHeaderField("app-send-date");
                String respType = conn.getHeaderField("original-content-type");
                if (respDate == null) respDate = sendDate;
                if (respType == null) respType = "application/json";
                String decrypted = decryptResponse(respDate, respType, responseBody);
                if (decrypted != null) return decrypted;
            }
            return responseBody;
        } catch (Exception e) {
            Log.e(TAG, "HTTP error: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ==================== Crypto (ported from NewMGRemote CryptoUtils.kt) ====================

    private String encryptRequest(String resourcePath, String sendDate, String token, String body, String contentType) {
        String part1 = md5(resourcePath + TENANT_ID + token + USER_TYPE);
        String key = md5(part1 + sendDate + CONTENT_ENCRYPTED + contentType);
        String iv = md5(sendDate);
        return aesEncrypt(body, key, iv);
    }

    private String decryptResponse(String sendDate, String contentType, String cipherText) {
        String key = md5(sendDate + CONTENT_ENCRYPTED + contentType);
        String iv = md5(sendDate);
        return aesDecrypt(cipherText, key, iv);
    }

    private String calculateVerification(String resourcePath, String sendDate, String contentType, String bodyEncrypted, String token) {
        String part1 = md5(resourcePath + TENANT_ID + token + USER_TYPE);
        String part2 = md5(part1 + sendDate + CONTENT_ENCRYPTED + contentType);
        String fullString = resourcePath + TENANT_ID + token + USER_TYPE + sendDate + CONTENT_ENCRYPTED + contentType + bodyEncrypted;
        String hmacKey = md5(part2 + sendDate);
        return hmacSha256(hmacKey.getBytes(StandardCharsets.UTF_8), fullString);
    }

    private String aesEncrypt(String plainText, String hexKey, String hexIv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(hexToBytes(hexKey), "AES"),
                new IvParameterSpec(hexToBytes(hexIv)));
            return bytesToHex(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { Log.e(TAG, "AES encrypt failed", e); return ""; }
    }

    private String aesDecrypt(String cipherText, String hexKey, String hexIv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(hexToBytes(hexKey), "AES"),
                new IvParameterSpec(hexToBytes(hexIv)));
            return new String(cipher.doFinal(hexToBytes(cipherText)), StandardCharsets.UTF_8);
        } catch (Exception e) { Log.e(TAG, "AES decrypt failed", e); return null; }
    }

    private static String md5(String input) {
        try {
            byte[] bytes = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(bytes);
        } catch (Exception e) { return ""; }
    }

    private static String sha1(String input) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-1").digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(bytes);
        } catch (Exception e) { return ""; }
    }

    private static String sha256(String input) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(bytes);
        } catch (Exception e) { return input; }
    }

    private static String hmacSha256(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return bytesToHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { return ""; }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        return data;
    }

    private void post(CloudCallback cb, boolean success, String msg) {
        if (cb != null) mHandler.post(() -> cb.onResult(success, msg));
    }
}

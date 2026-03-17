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

package com.emegelauncher.vehicle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

/**
 * Weather data manager.
 * - Listens for "com.saicmotor.weather" broadcasts (sent by weather app when open)
 * - Also reads weather app's SharedPreferences directly (system UID access)
 * - Periodically triggers the weather app to refresh via broadcast
 */
public class WeatherManager {
    private static final String TAG = "WeatherManager";
    private static final String WEATHER_ACTION = "com.saicmotor.weather";
    private static final String WEATHER_PKG = "com.saicmotor.weathers";

    private WeatherListener mListener;
    private String mLastWeather = "";
    private String mLastTemp = "";

    public interface WeatherListener {
        void onWeatherUpdated(String weather, String temp);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WEATHER_ACTION.equals(intent.getAction())) {
                try {
                    String jsonStr = intent.getStringExtra("weather");
                    if (jsonStr != null) {
                        parseAndNotify(jsonStr);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing weather broadcast", e);
                }
            }
        }
    };

    public void register(Context context, WeatherListener listener) {
        this.mListener = listener;
        // Register with null permission (broadcast is from system app, not sensitive)
        context.registerReceiver(mReceiver, new IntentFilter(WEATHER_ACTION),
                null, null);
        readCachedWeather(context);
    }

    /** Call periodically from the main activity's polling loop */
    public void poll(Context context) {
        readCachedWeather(context);
        // Try to trigger the weather app's service to refresh and send broadcast
        triggerWeatherRefresh(context);
    }

    private long mLastTrigger = 0;
    private void triggerWeatherRefresh(Context context) {
        long now = System.currentTimeMillis();
        if (now - mLastTrigger < 60000) return; // At most once per minute
        mLastTrigger = now;
        try {
            // Start the weather service which triggers a broadcast
            Intent svc = new Intent();
            svc.setComponent(new android.content.ComponentName(WEATHER_PKG,
                "com.saicmotor.weathers.service.WeathersService"));
            context.startService(svc);
        } catch (Exception e) {
            Log.d(TAG, "Cannot trigger weather service: " + e.getMessage());
        }
    }

    public void unregister(Context context) {
        try { context.unregisterReceiver(mReceiver); } catch (Exception ignored) {}
    }

    private void parseAndNotify(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            String temp = json.optString("temperature", "");
            String desc = json.optString("temperatureDescribe", "");
            if (desc.isEmpty()) desc = json.optString("type", "");
            if (desc.isEmpty()) desc = json.optString("today_weather", "");
            if (desc.isEmpty()) desc = "Weather";
            if (!temp.isEmpty()) {
                mLastWeather = desc;
                mLastTemp = temp;
                if (mListener != null) mListener.onWeatherUpdated(desc, temp);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing weather JSON", e);
        }
    }

    /**
     * Read weather data from the weather app's SharedPreferences.
     * Since we have android.uid.system, we can access other system apps' prefs.
     */
    private void readCachedWeather(Context context) {
        // Try reading from weather app's shared prefs
        try {
            Context weatherCtx = context.createPackageContext(WEATHER_PKG,
                Context.CONTEXT_IGNORE_SECURITY);
            SharedPreferences prefs = weatherCtx.getSharedPreferences(
                WEATHER_PKG + "_preferences", Context.MODE_PRIVATE);
            // Try common keys
            String json = prefs.getString("today_weather", null);
            if (json == null) json = prefs.getString("weather_data", null);
            if (json == null) json = prefs.getString("weather", null);
            if (json != null && !json.isEmpty()) {
                parseAndNotify(json);
                return;
            }
            // Also try individual keys
            String temp = prefs.getString("temperature", null);
            if (temp == null) temp = prefs.getString("tv_weather_temperature", null);
            String desc = prefs.getString("temperatureDescribe", null);
            if (desc == null) desc = prefs.getString("weather_desc", null);
            if (temp != null && !temp.isEmpty()) {
                mLastTemp = temp;
                mLastWeather = (desc != null && !desc.isEmpty()) ? desc : "Weather";
                if (mListener != null) mListener.onWeatherUpdated(mLastWeather, mLastTemp);
                return;
            }
        } catch (Exception e) {
            Log.d(TAG, "Cannot read weather app prefs: " + e.getMessage());
        }

        // Fallback: try the original SAIC launcher's SharedPreferences
        try {
            Context launcherCtx = context.createPackageContext(
                "com.saicmotor.hmi.launcher", Context.CONTEXT_IGNORE_SECURITY);
            SharedPreferences prefs = launcherCtx.getSharedPreferences(
                "com.saicmotor.hmi.launcher_preferences", Context.MODE_PRIVATE);
            String json = prefs.getString("today_weather", null);
            if (json != null && !json.isEmpty()) {
                parseAndNotify(json);
                return;
            }
        } catch (Exception e) {
            Log.d(TAG, "Cannot read launcher prefs: " + e.getMessage());
        }

        // Fallback: try global settings provider
        try {
            String json = android.provider.Settings.Global.getString(
                context.getContentResolver(), "today_weather");
            if (json != null && !json.isEmpty()) {
                parseAndNotify(json);
            }
        } catch (Exception ignored) {}
    }
}

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
import android.content.Context;
import android.location.GnssStatus;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.emegelauncher.vehicle.VehicleServiceManager;
import com.emegelauncher.vehicle.YFVehicleProperty;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Location & GPS information screen.
 * Uses Android LocationManager (GPS receiver built into head unit).
 * Also shows vehicle telemetry alongside GPS data.
 */
public class LocationActivity extends Activity {
    private static final String TAG = "LocationActivity";

    private LocationManager mLocationManager;
    private VehicleServiceManager mVehicle;
    private final Handler mHandler = new Handler(android.os.Looper.getMainLooper());
    private LinearLayout mContent;
    private int cBg, cCard, cText, cTextSec, cTextTert, cDivider;

    private Location mLastLocation;
    private int mSatellitesUsed = 0;
    private int mSatellitesVisible = 0;
    private GnssStatus.Callback mGnssCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        resolveColors();

        mVehicle = VehicleServiceManager.getInstance(this);
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);
        root.setPadding(20, 8, 20, 8);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(0, 4, 0, 8);
        TextView title = new TextView(this);
        title.setText(getString(R.string.location_gps));
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
        startLocationUpdates();
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
        // JSON-style snapshot
        addSection(getString(R.string.vehicle_snapshot));
        addRow("json_snapshot", getString(R.string.json_data));

        // Address (from navigation service)
        addSection(getString(R.string.address));
        addRow("street_address", getString(R.string.street));
        addRow("city", getString(R.string.city));
        addRow("country", getString(R.string.country));

        // Position
        addSection(getString(R.string.gps_position));
        addRow("latitude", getString(R.string.latitude));
        addRow("longitude", getString(R.string.longitude));
        addRow("altitude", getString(R.string.altitude_m));
        addRow("accuracy", getString(R.string.accuracy_m));
        addRow("bearing", getString(R.string.bearing_deg));
        addRow("gps_speed", getString(R.string.gps_speed_kmh));
        addRow("gps_time", getString(R.string.gps_time_utc));
        addRow("provider", getString(R.string.provider));
        addRow("fix_age", getString(R.string.fix_age));

        // Satellites
        addSection(getString(R.string.gnss_satellites));
        addRow("sats_used", getString(R.string.satellites_used));
        addRow("sats_visible", getString(R.string.satellites_visible));
        addRow("gps_enabled", getString(R.string.gps_enabled));
        addRow("network_enabled", getString(R.string.network_provider));

        // Coordinate formats
        addSection(getString(R.string.coordinates_other));
        addRow("coord_dms", getString(R.string.dms_format));
        addRow("coord_decimal", getString(R.string.decimal_degrees));
        addRow("coord_utm", getString(R.string.approx_utm));
    }

    // ==================== Location Updates ====================

    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            mLastLocation = location;
        }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
    };

    @SuppressWarnings("MissingPermission")
    private void startLocationUpdates() {
        try {
            // Request GPS updates
            if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, mLocationListener);
                // Get last known as initial
                Location last = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (last != null) mLastLocation = last;
            }
            // Also try network provider
            if (mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0, mLocationListener);
                if (mLastLocation == null) {
                    mLastLocation = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
            }

            // Satellite count (API 24+)
            try {
                mGnssCallback = new GnssStatus.Callback() {
                    @Override
                    public void onSatelliteStatusChanged(GnssStatus status) {
                        mSatellitesVisible = status.getSatelliteCount();
                        int used = 0;
                        for (int i = 0; i < status.getSatelliteCount(); i++) {
                            if (status.usedInFix(i)) used++;
                        }
                        mSatellitesUsed = used;
                    }
                };
                mLocationManager.registerGnssStatusCallback(mGnssCallback);
            } catch (Exception e) {
                // Fallback for older API
                try {
                    @SuppressWarnings("deprecation")
                    GpsStatus gpsStatus = mLocationManager.getGpsStatus(null);
                    if (gpsStatus != null) {
                        int visible = 0, used = 0;
                        for (GpsSatellite sat : gpsStatus.getSatellites()) {
                            visible++;
                            if (sat.usedInFix()) used++;
                        }
                        mSatellitesVisible = visible;
                        mSatellitesUsed = used;
                    }
                } catch (Exception ignored) {}
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission denied", e);
        } catch (Exception e) {
            Log.e(TAG, "Location init failed", e);
        }
    }

    // ==================== Update Display ====================

    private void updateDisplay() {
        boolean gpsEnabled = false, networkEnabled = false;
        try {
            gpsEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            networkEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ignored) {}

        updateTag("gps_enabled", gpsEnabled ? getString(R.string.yes) : getString(R.string.no));
        updateTag("network_enabled", networkEnabled ? getString(R.string.yes) : getString(R.string.no));
        updateTag("sats_used", String.valueOf(mSatellitesUsed));
        updateTag("sats_visible", String.valueOf(mSatellitesVisible));

        // Street address from navigation service (works without active navigation)
        try {
            String locDesc = mVehicle.callSaicMethod("adaptervoice", "getCurLocationDesc");
            if (locDesc != null && !locDesc.equals("N/A") && locDesc.startsWith("{")) {
                JSONObject loc = new JSONObject(locDesc);
                String street = loc.optString("streetName", "");
                String house = loc.optString("houseNumber", "");
                if (!street.isEmpty()) {
                    updateTag("street_address", street + (house.isEmpty() ? "" : " " + house));
                }
                updateTag("city", loc.optString("cityName", "--"));
                updateTag("country", loc.optString("countryName", "--"));
            }
        } catch (Exception ignored) {}

        if (mLastLocation != null) {
            double lat = mLastLocation.getLatitude();
            double lon = mLastLocation.getLongitude();
            double alt = mLastLocation.getAltitude();
            float acc = mLastLocation.getAccuracy();
            float bearing = mLastLocation.getBearing();
            float speedMs = mLastLocation.getSpeed();
            float speedKmh = speedMs * 3.6f;
            long time = mLastLocation.getTime();
            String provider = mLastLocation.getProvider();

            updateTag("latitude", String.format("%.6f", lat));
            updateTag("longitude", String.format("%.6f", lon));
            updateTag("altitude", String.format("%.1f", alt));
            updateTag("accuracy", String.format("%.1f", acc));
            updateTag("bearing", String.format("%.1f", bearing));
            updateTag("gps_speed", String.format("%.1f", speedKmh));
            updateTag("provider", provider != null ? provider : "N/A");

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            updateTag("gps_time", sdf.format(new Date(time)) + " UTC");

            long age = (System.currentTimeMillis() - time) / 1000;
            updateTag("fix_age", age + "s ago");

            // Coordinate formats
            updateTag("coord_decimal", String.format("%.6f, %.6f", lat, lon));
            updateTag("coord_dms", toDMS(lat, "N", "S") + "  " + toDMS(lon, "E", "W"));
            int utmZone = (int) Math.floor((lon + 180) / 6) + 1;
            updateTag("coord_utm", "Zone " + utmZone + (lat >= 0 ? "N" : "S"));

            // JSON snapshot (location only)
            try {
                JSONObject json = new JSONObject();
                json.put("utc", time / 1000);
                json.put("lat", Math.round(lat * 1000000) / 1000000.0);
                json.put("lon", Math.round(lon * 1000000) / 1000000.0);
                json.put("alt", Math.round(alt));
                json.put("speed_kmh", Math.round(speedKmh));
                json.put("accuracy_m", Math.round(acc));
                json.put("bearing", Math.round(bearing));
                json.put("satellites", mSatellitesUsed);
                json.put("provider", provider);
                updateTag("json_snapshot", json.toString(2));
            } catch (Exception e) {
                updateTag("json_snapshot", "Error: " + e.getMessage());
            }
        } else {
            updateTag("latitude", getString(R.string.waiting_gps));
            updateTag("longitude", "--");
            updateTag("json_snapshot", getString(R.string.no_gps_fix));
        }
    }

    // ==================== Helpers ====================

    private String toDMS(double coord, String pos, String neg) {
        String dir = coord >= 0 ? pos : neg;
        coord = Math.abs(coord);
        int d = (int) coord;
        int m = (int) ((coord - d) * 60);
        double s = (coord - d - m / 60.0) * 3600;
        return String.format("%d°%02d'%05.2f\"%s", d, m, s, dir);
    }

    private String decodeGear(int raw) {
        switch (raw) { case 1: return "P"; case 2: return "R"; case 3: return "N"; case 4: return "D"; default: return String.valueOf(raw); }
    }

    private float readSaicFloat(String svc, String method) {
        try {
            String v = mVehicle.callSaicMethod(svc, method);
            if (v != null && !v.equals("N/A")) return Float.parseFloat(v);
        } catch (Exception ignored) {}
        return 0f;
    }

    private float readVhalFloat(int propId) {
        try {
            String v = mVehicle.getPropertyValue(propId);
            if (v != null && !v.equals("N/A") && !v.equals("Connecting...")) return Float.parseFloat(v);
        } catch (Exception ignored) {}
        return 0f;
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
        valView.setText("--");
        valView.setTextSize(13);
        valView.setTextColor(ThemeHelper.accentTeal(this));
        valView.setTag(tag);
        // JSON snapshot needs wrapping
        if (tag.equals("json_snapshot")) {
            valView.setTextSize(10);
            valView.setMaxLines(15);
        }
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
                updateDisplay();
                mHandler.postDelayed(this, 2000);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        try { mLocationManager.removeUpdates(mLocationListener); } catch (Exception ignored) {}
        try { if (mGnssCallback != null) mLocationManager.unregisterGnssStatusCallback(mGnssCallback); } catch (Exception ignored) {}
    }
}

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
        title.setText("Location & GPS");
        title.setTextSize(22);
        title.setTextColor(cText);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(title);
        TextView back = new TextView(this);
        back.setText("BACK");
        back.setTextSize(13);
        back.setTextColor(0xFF0A84FF);
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
        addSection("VEHICLE SNAPSHOT");
        addRow("json_snapshot", "JSON Data");

        // Position
        addSection("GPS POSITION");
        addRow("latitude", "Latitude");
        addRow("longitude", "Longitude");
        addRow("altitude", "Altitude (m)");
        addRow("accuracy", "Accuracy (m)");
        addRow("bearing", "Bearing (°)");
        addRow("gps_speed", "GPS Speed (km/h)");
        addRow("gps_time", "GPS Time (UTC)");
        addRow("provider", "Provider");
        addRow("fix_age", "Fix Age");

        // Satellites
        addSection("GNSS SATELLITES");
        addRow("sats_used", "Satellites Used");
        addRow("sats_visible", "Satellites Visible");
        addRow("gps_enabled", "GPS Enabled");
        addRow("network_enabled", "Network Provider");

        // Vehicle telemetry alongside
        addSection("VEHICLE TELEMETRY");
        addRow("veh_speed", "Vehicle Speed (km/h)");
        addRow("veh_soc", "Battery SOC (%)");
        addRow("veh_range", "Range (km)");
        addRow("veh_gear", "Gear");
        addRow("veh_odometer", "Odometer (km)");
        addRow("veh_charging", "Charging");
        addRow("veh_ignition", "Ignition");

        // Coordinate formats
        addSection("COORDINATES (OTHER FORMATS)");
        addRow("coord_dms", "DMS Format");
        addRow("coord_decimal", "Decimal Degrees");
        addRow("coord_utm", "Approx. UTM Zone");
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

        updateTag("gps_enabled", gpsEnabled ? "Yes" : "No");
        updateTag("network_enabled", networkEnabled ? "Yes" : "No");
        updateTag("sats_used", String.valueOf(mSatellitesUsed));
        updateTag("sats_visible", String.valueOf(mSatellitesVisible));

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

            // Vehicle telemetry
            float vehSpeed = readSaicFloat("condition", "getCarSpeed");
            if (vehSpeed == 0) vehSpeed = readVhalFloat(YFVehicleProperty.PERF_VEHICLE_SPEED);
            float soc = readSaicFloat("charging", "getCurrentElectricQuantity");
            if (soc == 0) soc = readVhalFloat(YFVehicleProperty.BMS_PACK_SOC_DSP);
            float range = readSaicFloat("charging", "getCurrentEnduranceMileage");
            if (range == 0) range = readVhalFloat(YFVehicleProperty.BMS_ESTD_ELEC_RNG);
            int gear = (int) readSaicFloat("condition", "getCarGear");
            if (gear == 0) gear = (int) readVhalFloat(YFVehicleProperty.CURRENT_GEAR);
            float odometer = readVhalFloat(YFVehicleProperty.SENSOR_TOTAL_MILEAGE);
            int chrgSts = (int) readSaicFloat("charging", "getChargingStatus");
            int ignition = (int) readVhalFloat(YFVehicleProperty.IGNITION_STATE);

            updateTag("veh_speed", String.format("%.1f", vehSpeed));
            updateTag("veh_soc", String.format("%.1f", soc));
            updateTag("veh_range", String.format("%.0f", range));
            updateTag("veh_gear", decodeGear(gear));
            updateTag("veh_odometer", String.format("%.0f", odometer));
            updateTag("veh_charging", chrgSts == 1 ? "AC" : chrgSts == 2 ? "DC" : "No");
            updateTag("veh_ignition", String.valueOf(ignition));

            // JSON snapshot
            try {
                JSONObject json = new JSONObject();
                json.put("utc", time / 1000);
                json.put("soc", Math.round(soc * 10) / 10.0);
                json.put("lat", Math.round(lat * 1000000) / 1000000.0);
                json.put("lon", Math.round(lon * 1000000) / 1000000.0);
                json.put("alt", Math.round(alt));
                json.put("speed", Math.round(vehSpeed));
                json.put("gps_speed", Math.round(speedKmh));
                json.put("is_charging", chrgSts > 0 ? 1 : 0);
                json.put("is_parked", gear == 4 ? 1 : 0);
                json.put("accuracy", Math.round(acc));
                json.put("bearing", Math.round(bearing));
                json.put("satellites", mSatellitesUsed);
                json.put("odometer", Math.round(odometer));
                json.put("range", Math.round(range));
                updateTag("json_snapshot", json.toString(2));
            } catch (Exception e) {
                updateTag("json_snapshot", "Error: " + e.getMessage());
            }
        } else {
            updateTag("latitude", "Waiting for GPS fix...");
            updateTag("longitude", "--");
            updateTag("json_snapshot", "No GPS fix yet");
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
        switch (raw) { case 1: return "D"; case 2: return "N"; case 3: return "R"; case 4: return "P"; default: return String.valueOf(raw); }
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
        valView.setTextColor(0xFF64D2FF);
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

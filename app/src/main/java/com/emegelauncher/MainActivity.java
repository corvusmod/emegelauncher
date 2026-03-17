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
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;

import com.emegelauncher.vehicle.VehicleServiceManager;
import com.emegelauncher.vehicle.WeatherManager;
import com.emegelauncher.vehicle.YFVehicleProperty;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private VehicleServiceManager mVehicleManager;
    private WeatherManager mWeatherManager;

    private TextView mTempText;
    private TextView mTempInsideText;
    private TextView mBatteryText;
    private TextView mRangeText;
    private TextView mBmsRawText;
    private TextView mWeatherText;
    private TextView mWeatherTemp;

    private final Handler mHandler = new Handler(android.os.Looper.getMainLooper());
    private boolean mLastDarkMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mLastDarkMode = ThemeHelper.isDarkMode(this);

        mVehicleManager = VehicleServiceManager.getInstance(this);
        mVehicleManager.bindService();

        mWeatherManager = new WeatherManager();
        mWeatherManager.register(this, (weather, temp) -> {
            if (mWeatherText != null) mWeatherText.setText(weather);
            if (mWeatherTemp != null) mWeatherTemp.setText(temp + "\u00B0C");
        });

        mTempText = findViewById(R.id.temp_outside);
        mTempInsideText = findViewById(R.id.temp_inside);
        mBatteryText = findViewById(R.id.battery_lvl);
        mRangeText = findViewById(R.id.range_text);
        mBmsRawText = findViewById(R.id.bms_raw_text);
        mWeatherText = findViewById(R.id.weather_text);
        mWeatherTemp = findViewById(R.id.weather_temp);

        updateUI();
        startPolling();

        findViewById(R.id.btn_weather).setOnClickListener(v -> launchCarApp("com.saicmotor.weathers", "com.saicmotor.weathers.activity.MainActivity"));
        findViewById(R.id.btn_nav).setOnClickListener(v -> launchCarApp("com.telenav.app.arp", "com.telenav.arp.module.map.MainActivity"));
        findViewById(R.id.btn_phone).setOnClickListener(v -> launchCarApp("com.saicmotor.hmi.btcall", "com.saicmotor.hmi.btcall.BtCallActivity"));
        findViewById(R.id.btn_radio).setOnClickListener(v -> launchCarApp("com.saicmotor.hmi.radio", "com.saicmotor.hmi.radio.app.RadioHomeActivity"));
        findViewById(R.id.btn_music).setOnClickListener(v -> launchCarApp("com.saicmotor.hmi.music", "com.saicmotor.hmi.music.ui.activity.MusicActivity"));

        findViewById(R.id.btn_360view).setOnClickListener(v -> launchCarApp("com.saicmotor.hmi.aroundview", "com.saicmotor.hmi.aroundview.aroundviewconfig.ui.AroundViewActivity"));
        findViewById(R.id.btn_carplay).setOnClickListener(v -> launchCarApp("com.allgo.carplay.service", "com.allgo.carplay.service.CarPlayActivity"));
        findViewById(R.id.btn_androidauto).setOnClickListener(v -> launchCarApp("com.allgo.app.androidauto", "com.allgo.app.androidauto.ProjectionActivity"));
        findViewById(R.id.btn_video).setOnClickListener(v -> launchCarApp("com.saicmotor.hmi.video", "com.saicmotor.hmi.video.ui.activity.UsbVideoActivity"));
        findViewById(R.id.btn_vehicle).setOnClickListener(v -> launchCarApp("com.saicmotor.hmi.vehiclesettings", "com.saicmotor.hmi.vehiclesettings.vehicleconfig.ui.VehicleSettingsActivity"));
        findViewById(R.id.btn_syssettings).setOnClickListener(v -> launchCarApp("com.saicmotor.hmi.systemsettings", "com.saicmotor.hmi.systemsettings.SettingsActivity"));
        findViewById(R.id.btn_rescue).setOnClickListener(v -> launchCarApp("com.saicmotor.rescuecall", "com.saicmotor.rescuecall.module.ICallCenterAct"));
        findViewById(R.id.btn_touchpoint).setOnClickListener(v -> launchCarApp("com.saic.saicmaintenance", "com.saic.saicmaintenance.module.maintaince.MaintaiceEp21Activity"));
        findViewById(R.id.btn_manual).setOnClickListener(v -> launchCarApp("com.saicmotor.hmi.pdfreader", "com.saicmotor.hmi.pdfreader.PdfReaderActivity"));
        findViewById(R.id.btn_graphs).setOnClickListener(v -> startActivity(new Intent(this, GraphsActivity.class)));
        findViewById(R.id.btn_vehicle_info).setOnClickListener(v -> startActivity(new Intent(this, VehicleInfoActivity.class)));
        findViewById(R.id.btn_location).setOnClickListener(v -> startActivity(new Intent(this, LocationActivity.class)));
        findViewById(R.id.btn_apps).setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        findViewById(R.id.btn_debug).setOnClickListener(v -> startActivity(new Intent(this, DebugActivity.class)));
        findViewById(R.id.btn_launcher_settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (mLastDarkMode != ThemeHelper.isDarkMode(this)) {
            recreate();
        }
    }

    private void launchCarApp(String pkg, String cls) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(pkg, cls));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
                if (intent != null) startActivity(intent);
                else Toast.makeText(this, "App not found", Toast.LENGTH_SHORT).show();
            } catch (Exception e2) {
                Toast.makeText(this, "Launch error", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateUI() {
        // SOC: try SAIC charging service first (matches display), then VHAL
        String socSaic = mVehicleManager.callSaicMethod("charging", "getCurrentElectricQuantity");
        String socDsp = mVehicleManager.getPropertyValue(YFVehicleProperty.BMS_PACK_SOC_DSP);
        String socDisplay = isValid(socSaic) ? socSaic : socDsp;
        if (isValid(socDisplay)) mBatteryText.setText(socDisplay);

        // Range: try SAIC service first, then cluster, then BMS
        String rangeSaic = mVehicleManager.callSaicMethod("charging", "getCurrentEnduranceMileage");
        String clstrRange = mVehicleManager.getPropertyValue(YFVehicleProperty.CLSTR_ELEC_RNG);
        String bmsRange = mVehicleManager.getPropertyValue(YFVehicleProperty.BMS_ESTD_ELEC_RNG);
        String displayRange = isValid(rangeSaic) ? rangeSaic : (isValid(clstrRange) ? clstrRange : bmsRange);
        if (isValid(displayRange)) mRangeText.setText(displayRange + " km");

        // BMS raw as secondary
        String bmsSoc = mVehicleManager.getPropertyValue(YFVehicleProperty.BMS_PACK_SOC);
        StringBuilder rawInfo = new StringBuilder("BMS: ");
        if (isValid(bmsSoc)) rawInfo.append(bmsSoc).append("% SOC");
        if (isValid(bmsRange)) rawInfo.append(" | ").append(bmsRange).append(" km");
        mBmsRawText.setText(rawInfo.toString());

        // Outside temp: SAIC AirCondition → VHAL
        String outsideTemp = mVehicleManager.getOutsideTemp();
        if (!isValid(outsideTemp)) outsideTemp = mVehicleManager.getPropertyValue(YFVehicleProperty.ENV_OUTSIDE_TEMPERATURE);
        if (isValid(outsideTemp)) mTempText.setText(outsideTemp + "\u00B0C");

        // Inside temp: SAIC driver temp → VHAL
        String insideTemp = mVehicleManager.getDriverTemp();
        if (!isValid(insideTemp)) insideTemp = mVehicleManager.getPropertyValue(YFVehicleProperty.HVAC_TEMPERATURE_CURRENT);
        if (isValid(insideTemp)) mTempInsideText.setText(insideTemp + "\u00B0C");
    }

    private boolean isValid(String val) {
        return val != null && !val.equals("N/A") && !val.equals("Connecting...") && !val.equals("0.00") && !val.equals("0");
    }

    private void startPolling() {
        mHandler.postDelayed(new Runnable() {
            @Override public void run() {
                updateUI();
                mWeatherManager.poll(MainActivity.this);
                mHandler.postDelayed(this, 5000);
            }
        }, 5000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        mWeatherManager.unregister(this);
        mVehicleManager.unbindService();
    }
}

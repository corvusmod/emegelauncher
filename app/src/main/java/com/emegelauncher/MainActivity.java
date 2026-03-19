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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.emegelauncher.vehicle.SaicCloudManager;
import com.emegelauncher.vehicle.VehicleServiceManager;
import com.emegelauncher.vehicle.WeatherManager;
import com.emegelauncher.vehicle.YFVehicleProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int PAGE_GRAPHS = 0, PAGE_MAIN = 1, PAGE_APPS = 2, PAGE_OTHER = 3;

    private VehicleServiceManager mVehicle;
    private WeatherManager mWeather;
    private SaicCloudManager mCloud;
    private final Handler mHandler = new Handler(android.os.Looper.getMainLooper());
    private boolean mLastDarkMode;
    private ViewPager mPager;

    // Main page widgets (updated by polling)
    private TextView mWeatherDesc, mWeatherOutTemp, mWeatherCabinTemp;
    private TextView mBatteryPct, mBatteryRange, mBatteryEta, mBatteryBms;
    private TextView mDriveMode, mRegenLevel;

    // Theme colors
    private int cBg, cCard, cText, cTextSec, cTextTert, cDivider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        mLastDarkMode = ThemeHelper.isDarkMode(this);
        resolveColors();

        mVehicle = VehicleServiceManager.getInstance(this);
        mVehicle.bindService();
        mCloud = new SaicCloudManager(this);
        mWeather = new WeatherManager();
        mWeather.register(this, (weather, temp) -> {
            if (mWeatherDesc != null) mWeatherDesc.setText(weather);
            if (mWeatherOutTemp != null) mWeatherOutTemp.setText(temp + "\u00B0C");
        });

        // ViewPager fills the screen
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);

        mPager = new ViewPager(this);
        mPager.setAdapter(new HomePagerAdapter());
        mPager.setCurrentItem(PAGE_MAIN);
        mPager.setOffscreenPageLimit(3);
        root.addView(mPager, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Page indicator dots
        root.addView(buildPageIndicator(), new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);

        startPolling();

        if (getSharedPreferences("emegelauncher", MODE_PRIVATE).getBoolean("overlay_enabled", true)) {
            startService(new Intent(this, OverlayService.class));
        }
        autoRestoreProfile();
        queryCloudOnce();
    }

    // ==================== ViewPager Adapter ====================

    private class HomePagerAdapter extends PagerAdapter {
        @Override public int getCount() { return 4; }
        @Override public boolean isViewFromObject(View v, Object o) { return v == o; }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View page;
            switch (position) {
                case PAGE_GRAPHS: page = buildGraphsPage(); break;
                case PAGE_MAIN:   page = buildMainPage(); break;
                case PAGE_APPS:   page = buildAppsPage(); break;
                case PAGE_OTHER:  page = buildOtherPage(); break;
                default: page = new View(MainActivity.this);
            }
            container.addView(page);
            return page;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }
    }

    // ==================== Page Indicator ====================

    private LinearLayout buildPageIndicator() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(0, 6, 0, 8);
        bar.setBackgroundColor(cBg);

        String[] labels = {"Graphs", "\u2022", "Apps", "Other"};
        for (int i = 0; i < 4; i++) {
            TextView dot = new TextView(this);
            dot.setTag("dot_" + i);
            dot.setText("\u2022");
            dot.setTextSize(18);
            dot.setTextColor(i == PAGE_MAIN ? ThemeHelper.accentBlue(this) : cTextTert);
            dot.setPadding(12, 0, 12, 0);
            bar.addView(dot);
        }

        mPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int pos) {
                for (int i = 0; i < 4; i++) {
                    TextView d = bar.findViewWithTag("dot_" + i);
                    if (d != null) d.setTextColor(i == pos ? ThemeHelper.accentBlue(MainActivity.this) : cTextTert);
                }
            }
        });
        return bar;
    }

    // ==================== Page 0: Graphs (live dashboard) ====================

    private com.emegelauncher.widget.ArcGaugeView mGpSpeed, mGpSoc, mGpRpm, mGpEcon;
    private com.emegelauncher.widget.PowerGaugeView mGpPowerGauge;
    private com.emegelauncher.widget.GMeterView mGpGMeter;
    private TextView mGpRangeText;

    private View buildGraphsPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(cBg);
        page.setPadding(16, 4, 16, 4);
        page.setOnClickListener(v -> startActivity(new Intent(this, GraphsActivity.class)));

        // 4 gauges in 2x2
        LinearLayout gaugeRow1 = new LinearLayout(this);
        mGpSpeed = newGauge(getString(R.string.speed), "km/h", 220, ThemeHelper.accentBlue(this));
        mGpSoc = newGauge(getString(R.string.battery), "%", 100, ThemeHelper.accentGreen(this));
        gaugeRow1.addView(mGpSpeed, new LinearLayout.LayoutParams(0, 200, 1f));
        gaugeRow1.addView(mGpSoc, new LinearLayout.LayoutParams(0, 200, 1f));
        page.addView(gaugeRow1);

        LinearLayout gaugeRow2 = new LinearLayout(this);
        mGpRpm = newGauge(getString(R.string.motor), "RPM", 12000, ThemeHelper.accentOrange(this));
        mGpEcon = newGauge(getString(R.string.efficiency), "", 100, ThemeHelper.accentTeal(this));
        gaugeRow2.addView(mGpRpm, new LinearLayout.LayoutParams(0, 200, 1f));
        gaugeRow2.addView(mGpEcon, new LinearLayout.LayoutParams(0, 200, 1f));
        page.addView(gaugeRow2);

        // Power gauge + G-Meter side by side
        LinearLayout chartsRow = new LinearLayout(this);
        chartsRow.setOrientation(LinearLayout.HORIZONTAL);

        mGpPowerGauge = new com.emegelauncher.widget.PowerGaugeView(this);
        mGpPowerGauge.setMaxKw(150);
        mGpPowerGauge.setLabel("Power");
        mGpPowerGauge.setUnit("kW");
        mGpPowerGauge.setConsumeColor(ThemeHelper.accentOrange(this));
        mGpPowerGauge.setRegenColor(ThemeHelper.accentGreen(this));
        mGpPowerGauge.setBgColor(cCard);
        mGpPowerGauge.setTextColor(cText);
        mGpPowerGauge.setLabelColor(cTextSec);
        LinearLayout.LayoutParams powerLp = new LinearLayout.LayoutParams(0, 200, 1f);
        powerLp.setMargins(0, 6, 4, 4);
        chartsRow.addView(mGpPowerGauge, powerLp);

        mGpGMeter = new com.emegelauncher.widget.GMeterView(this);
        mGpGMeter.setDotColor(ThemeHelper.accentRed(this));
        mGpGMeter.setBgColor(cCard);
        mGpGMeter.setRingColor(cDivider);
        mGpGMeter.setTextColor(cText);
        mGpGMeter.setLabelColor(cTextSec);
        LinearLayout.LayoutParams gmeterLp = new LinearLayout.LayoutParams(0, 200, 1f);
        gmeterLp.setMargins(4, 6, 0, 4);
        chartsRow.addView(mGpGMeter, gmeterLp);

        page.addView(chartsRow);

        // Range + Gear + Mode info row
        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setBackgroundColor(cCard);
        infoRow.setPadding(16, 10, 16, 10);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);

        mGpRangeText = new TextView(this);
        mGpRangeText.setText("Range: -- km  |  Gear: --  |  Mode: --");
        mGpRangeText.setTextSize(13);
        mGpRangeText.setTextColor(cText);
        infoRow.addView(mGpRangeText);

        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
        infoLp.setMargins(0, 4, 0, 4);
        page.addView(infoRow, infoLp);

        // Tap hint
        TextView hint = new TextView(this);
        hint.setText(getString(R.string.tap_full_graphs));
        hint.setTextSize(12);
        hint.setTextColor(ThemeHelper.accentTeal(this));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 6, 0, 0);
        page.addView(hint);

        scroll.addView(page);
        return scroll;
    }

    private com.emegelauncher.widget.ArcGaugeView newGauge(String label, String unit, float max, int color) {
        com.emegelauncher.widget.ArcGaugeView g = new com.emegelauncher.widget.ArcGaugeView(this);
        g.setLabel(label);
        g.setUnit(unit);
        g.setMaxValue(max);
        g.setFgColor(color);
        g.setBgArcColor(cCard);
        g.setTextColor(cText);
        return g;
    }

    // ==================== Page 1: Main ====================

    private View buildMainPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(cBg);
        page.setPadding(16, 8, 16, 0);

        // 3x2 button grid
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);

        // Row 1: Weather | Battery
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(buildWeatherCard(), bigBtnLP(true));
        row1.addView(buildBatteryCard(), bigBtnLP(false));
        grid.addView(row1, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Row 2: Map | Music
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(buildBigButton(getString(R.string.map), "\uD83D\uDDFA", ThemeHelper.accentBlue(this),
            () -> launchCarApp("com.telenav.app.arp", "com.telenav.arp.module.map.MainActivity")), bigBtnLP(true));
        row2.addView(buildBigButton(getString(R.string.music), "\u266B", ThemeHelper.accentPurple(this),
            () -> launchCarApp("com.saicmotor.hmi.music", "com.saicmotor.hmi.music.ui.activity.MusicActivity")), bigBtnLP(false));
        grid.addView(row2, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Row 3: Radio | Phone
        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.addView(buildBigButton(getString(R.string.radio), "\uD83D\uDCFB", ThemeHelper.accentOrange(this),
            () -> launchCarApp("com.saicmotor.hmi.radio", "com.saicmotor.hmi.radio.app.RadioHomeActivity")), bigBtnLP(true));
        row3.addView(buildBigButton(getString(R.string.phone), "\uD83D\uDCDE", ThemeHelper.accentGreen(this),
            () -> launchCarApp("com.saicmotor.hmi.btcall", "com.saicmotor.hmi.btcall.BtCallActivity")), bigBtnLP(false));
        grid.addView(row3, new LinearLayout.LayoutParams(-1, 0, 1f));

        page.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Bottom bar: Drive Mode + Regen
        page.addView(buildDriveModeBar());

        return page;
    }

    private View buildWeatherCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(cCard);
        card.setPadding(24, 20, 24, 20);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setOnClickListener(v -> launchCarApp("com.saicmotor.weathers", "com.saicmotor.weathers.activity.MainActivity"));

        mWeatherDesc = new TextView(this);
        mWeatherDesc.setText(getString(R.string.weather));
        mWeatherDesc.setTextSize(16);
        mWeatherDesc.setTextColor(cText);
        card.addView(mWeatherDesc);

        mWeatherOutTemp = new TextView(this);
        mWeatherOutTemp.setText("--\u00B0C");
        mWeatherOutTemp.setTextSize(32);
        mWeatherOutTemp.setTextColor(ThemeHelper.accentOrange(this));
        card.addView(mWeatherOutTemp);

        mWeatherCabinTemp = new TextView(this);
        mWeatherCabinTemp.setTextSize(13);
        mWeatherCabinTemp.setTextColor(cTextSec);
        mWeatherCabinTemp.setVisibility(View.GONE);
        card.addView(mWeatherCabinTemp);

        return card;
    }

    private View buildBatteryCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(cCard);
        card.setPadding(24, 20, 24, 20);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setOnClickListener(v -> launchCarApp(
            "com.saicmotor.hmi.vehiclesettings",
            "com.saicmotor.hmi.vehiclesettings.chargemanagement.ui.ChargeManagementActivity"));

        TextView label = new TextView(this);
        label.setText(getString(R.string.energy));
        label.setTextSize(12);
        label.setTextColor(cTextTert);
        card.addView(label);

        mBatteryPct = new TextView(this);
        mBatteryPct.setText("--%");
        mBatteryPct.setTextSize(32);
        mBatteryPct.setTextColor(cText);
        card.addView(mBatteryPct);

        mBatteryRange = new TextView(this);
        mBatteryRange.setText("-- km");
        mBatteryRange.setTextSize(14);
        mBatteryRange.setTextColor(ThemeHelper.accentTeal(this));
        card.addView(mBatteryRange);

        mBatteryEta = new TextView(this);
        mBatteryEta.setTextSize(11);
        mBatteryEta.setTextColor(cTextSec);
        card.addView(mBatteryEta);

        mBatteryBms = new TextView(this);
        mBatteryBms.setTextSize(10);
        mBatteryBms.setTextColor(cTextTert);
        card.addView(mBatteryBms);

        return card;
    }

    private View buildBigButton(String text, String icon, int accentColor, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(cCard);
        card.setGravity(Gravity.CENTER);
        card.setOnClickListener(v -> action.run());

        TextView iconTv = new TextView(this);
        iconTv.setText(icon);
        iconTv.setTextSize(36);
        iconTv.setTextColor(accentColor);
        iconTv.setGravity(Gravity.CENTER);
        card.addView(iconTv);

        TextView labelTv = new TextView(this);
        labelTv.setText(text);
        labelTv.setTextSize(16);
        labelTv.setTextColor(cText);
        labelTv.setGravity(Gravity.CENTER);
        labelTv.setPadding(0, 8, 0, 0);
        card.addView(labelTv);

        return card;
    }

    private View buildDriveModeBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(cCard);
        bar.setPadding(20, 10, 20, 10);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        mDriveMode = new TextView(this);
        mDriveMode.setText("Mode: --");
        mDriveMode.setTextSize(14);
        mDriveMode.setTextColor(cText);
        mDriveMode.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(mDriveMode);

        mRegenLevel = new TextView(this);
        mRegenLevel.setText("Regen: --");
        mRegenLevel.setTextSize(14);
        mRegenLevel.setTextColor(ThemeHelper.accentTeal(this));
        mRegenLevel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        mRegenLevel.setGravity(Gravity.CENTER);
        bar.addView(mRegenLevel);

        // Profile restore button
        TextView restoreBtn = new TextView(this);
        restoreBtn.setText("\u21BB");
        restoreBtn.setTextSize(20);
        restoreBtn.setTextColor(ThemeHelper.accentGreen(this));
        restoreBtn.setPadding(16, 0, 0, 0);
        restoreBtn.setOnClickListener(v -> {
            android.content.SharedPreferences prefs = getSharedPreferences("emegelauncher", MODE_PRIVATE);
            int savedRegen = prefs.getInt("profile_regen_level", -1);
            if (savedRegen >= 0 && mVehicle.hasSettingBinder()) {
                boolean ok = mVehicle.transactSettingInt(0xA1, savedRegen);
                Toast.makeText(this, getString(ok ? R.string.profile_restored_ok : R.string.transact_failed), Toast.LENGTH_SHORT).show();
            }
        });
        bar.addView(restoreBtn);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 4, 0, 0);
        bar.setLayoutParams(lp);
        return bar;
    }

    private LinearLayout.LayoutParams bigBtnLP(boolean isLeft) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.setMargins(isLeft ? 0 : 4, 4, isLeft ? 4 : 0, 4);
        return lp;
    }

    // ==================== Page 2: Apps ====================

    private View buildAppsPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(cBg);
        page.setPadding(16, 8, 16, 0);

        // Top half: app grid
        GridView appGrid = new GridView(this);
        appGrid.setNumColumns(4);
        appGrid.setVerticalSpacing(8);
        appGrid.setHorizontalSpacing(8);
        appGrid.setPadding(4, 4, 4, 4);
        List<AppInfo> apps = loadApps();
        appGrid.setAdapter(new AppGridAdapter(apps));
        appGrid.setOnItemClickListener((p, v, pos, id) -> {
            Intent intent = getPackageManager().getLaunchIntentForPackage(apps.get(pos).packageName);
            if (intent != null) startActivity(intent);
        });
        page.addView(appGrid, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Divider
        View div = new View(this);
        div.setBackgroundColor(cDivider);
        page.addView(div, new LinearLayout.LayoutParams(-1, 2));

        // Bottom half: 4-3-3 button grid
        LinearLayout btnGrid = new LinearLayout(this);
        btnGrid.setOrientation(LinearLayout.VERTICAL);
        btnGrid.setPadding(0, 4, 0, 0);

        // Row 1: 4 buttons
        LinearLayout r1 = new LinearLayout(this);
        r1.addView(appBtn(getString(R.string.carplay), () -> launchCarApp("com.allgo.carplay.service", "com.allgo.carplay.service.CarPlayActivity")), gridLP());
        r1.addView(appBtn(getString(R.string.android_auto), () -> launchCarApp("com.allgo.app.androidauto", "com.allgo.app.androidauto.ProjectionActivity")), gridLP());
        r1.addView(appBtn(getString(R.string.video), () -> launchCarApp("com.saicmotor.hmi.video", "com.saicmotor.hmi.video.ui.activity.UsbVideoActivity")), gridLP());
        r1.addView(appBtn(getString(R.string.view_360), () -> launchCarApp("com.saicmotor.hmi.aroundview", "com.saicmotor.hmi.aroundview.aroundviewconfig.ui.AroundViewActivity")), gridLP());
        btnGrid.addView(r1, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Row 2: 3 buttons
        LinearLayout r2 = new LinearLayout(this);
        r2.addView(appBtn(getString(R.string.car_settings), () -> launchCarApp("com.saicmotor.hmi.vehiclesettings", "com.saicmotor.hmi.vehiclesettings.vehicleconfig.ui.VehicleSettingsActivity")), gridLP());
        r2.addView(appBtn(getString(R.string.system_settings), () -> launchCarApp("com.saicmotor.hmi.systemsettings", "com.saicmotor.hmi.systemsettings.SettingsActivity")), gridLP());
        r2.addView(appBtn(getString(R.string.launcher_settings_short), () -> startActivity(new Intent(this, SettingsActivity.class))), gridLP());
        btnGrid.addView(r2, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Row 3: 3 buttons
        LinearLayout r3 = new LinearLayout(this);
        r3.addView(appBtn(getString(R.string.rescue), () -> launchCarApp("com.saicmotor.rescuecall", "com.saicmotor.rescuecall.module.ICallCenterAct")), gridLP());
        r3.addView(appBtn(getString(R.string.touchpoint), () -> launchCarApp("com.saic.saicmaintenance", "com.saic.saicmaintenance.module.maintaince.MaintaiceEp21Activity")), gridLP());
        r3.addView(appBtn(getString(R.string.manual), () -> launchCarApp("com.saicmotor.hmi.pdfreader", "com.saicmotor.hmi.pdfreader.PdfReaderActivity")), gridLP());
        btnGrid.addView(r3, new LinearLayout.LayoutParams(-1, 0, 1f));

        page.addView(btnGrid, new LinearLayout.LayoutParams(-1, 0, 1f));

        return page;
    }

    private View appBtn(String label, Runnable action) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextSize(13);
        btn.setTextColor(cText);
        btn.setBackgroundColor(cCard);
        btn.setGravity(Gravity.CENTER);
        btn.setOnClickListener(v -> action.run());
        return btn;
    }

    private LinearLayout.LayoutParams gridLP() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.setMargins(3, 3, 3, 3);
        return lp;
    }

    // ==================== Page 3: Other ====================

    private View buildOtherPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(cBg);
        page.setPadding(16, 8, 16, 8);

        // 2x3 button grid
        LinearLayout btnGrid = new LinearLayout(this);
        btnGrid.setOrientation(LinearLayout.VERTICAL);

        LinearLayout r1 = new LinearLayout(this);
        r1.addView(otherBtn(getString(R.string.diagnostics), ThemeHelper.accentRed(this), () -> startActivity(new Intent(this, DebugActivity.class))), gridLP());
        r1.addView(otherBtn(getString(R.string.vehicle_info), ThemeHelper.accentPurple(this), () -> startActivity(new Intent(this, VehicleInfoActivity.class))), gridLP());
        btnGrid.addView(r1, new LinearLayout.LayoutParams(-1, 200));

        LinearLayout r2 = new LinearLayout(this);
        r2.addView(otherBtn(getString(R.string.location), ThemeHelper.accentGreen(this), () -> startActivity(new Intent(this, LocationActivity.class))), gridLP());
        r2.addView(otherBtn(getString(R.string.tbox), ThemeHelper.accentOrange(this), () -> startActivity(new Intent(this, TboxActivity.class))), gridLP());
        btnGrid.addView(r2, new LinearLayout.LayoutParams(-1, 200));

        LinearLayout r3 = new LinearLayout(this);
        r3.addView(otherBtn(getString(R.string.cloud), ThemeHelper.accentBlue(this), () -> startActivity(new Intent(this, CloudActivity.class))), gridLP());
        btnGrid.addView(r3, new LinearLayout.LayoutParams(-1, 200));

        LinearLayout r4 = new LinearLayout(this);
        r4.addView(otherBtn(getString(R.string.find_my_car), ThemeHelper.accentOrange(this), () -> {
            if (mCloud.isLoggedIn()) {
                mCloud.findMyCar(1, (ok, msg) -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
            } else {
                Toast.makeText(this, getString(R.string.cloud_login_required), Toast.LENGTH_SHORT).show();
            }
        }), gridLP());
        btnGrid.addView(r4, new LinearLayout.LayoutParams(-1, 200));

        page.addView(btnGrid);

        // Info section
        addInfoSection(page, getString(R.string.cloud_status_label));
        addInfoRow(page, "cloud_status", mCloud.isLoggedIn() ? getString(R.string.cloud_info_logged) : getString(R.string.cloud_info_not_logged));
        String cabinTemp = mCloud.getInteriorTempStr();
        if (cabinTemp != null) addInfoRow(page, "cloud_cabin", "Cabin: " + cabinTemp + "\u00B0C");
        String batt12v = mCloud.getBatteryVoltageStr();
        if (batt12v != null) addInfoRow(page, "cloud_12v", "12V Battery: " + batt12v + "V");

        String tboxJson = mCloud.getCachedTboxStatus();
        if (tboxJson != null) {
            try {
                org.json.JSONObject tj = new org.json.JSONObject(tboxJson);
                org.json.JSONObject td = tj.optJSONObject("data");
                if (td != null) {
                    int st = td.optInt("status", -1);
                    String stStr = st == 0 ? "Online" : st == 1 ? "Offline" : st == 2 ? "Sleep" : "Unknown";
                    addInfoRow(page, "tbox_status", "TBox: " + stStr);
                }
            } catch (Exception ignored) {}
        }

        addInfoSection(page, getString(R.string.quick_actions_label));
        TextView wakeBtn = new TextView(this);
        wakeBtn.setText(getString(R.string.force_tbox_wake));
        wakeBtn.setTextSize(14);
        wakeBtn.setTextColor(mCloud.isLoggedIn() ? ThemeHelper.accentBlue(this) : 0xFF666666);
        wakeBtn.setBackgroundColor(cCard);
        wakeBtn.setPadding(20, 14, 20, 14);
        wakeBtn.setEnabled(mCloud.isLoggedIn());
        if (mCloud.isLoggedIn()) {
            wakeBtn.setOnClickListener(v -> mCloud.forceRefresh((ok, msg) ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()));
        }
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, -2);
        wlp.setMargins(0, 4, 0, 4);
        page.addView(wakeBtn, wlp);

        scroll.addView(page);
        return scroll;
    }

    private View otherBtn(String label, int accentColor, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(cCard);
        card.setGravity(Gravity.CENTER);
        card.setOnClickListener(v -> action.run());

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(16);
        labelTv.setTextColor(accentColor);
        labelTv.setGravity(Gravity.CENTER);
        card.addView(labelTv);

        return card;
    }

    private void addInfoSection(LinearLayout parent, String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(12);
        tv.setTextColor(cTextTert);
        tv.setPadding(4, 20, 0, 6);
        parent.addView(tv);
    }

    private void addInfoRow(LinearLayout parent, String tag, String value) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTag(tag);
        tv.setTextSize(13);
        tv.setTextColor(cTextSec);
        tv.setBackgroundColor(cCard);
        tv.setPadding(20, 8, 20, 8);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 2, 0, 2);
        parent.addView(tv, lp);
    }

    // ==================== App List ====================

    private static class AppInfo {
        String label, packageName;
        Drawable icon;
    }

    private List<AppInfo> loadApps() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        Set<String> hidden = new HashSet<>(Arrays.asList(
            "com.emegelauncher",
            "com.saicmotor.service.vehicle", "com.saicmotor.service.engmode",
            "com.saicmotor.service.btcall", "com.saicmotor.service.radio",
            "com.saicmotor.service.media", "com.saicmotor.service.systemsettings",
            "com.saicmotor.service.aroundview", "com.saicmotor.adapterservice",
            "com.saicmotor.mapservice", "com.saicmotor.voiceservice",
            "com.saicmotor.voicetts", "com.saicmotor.voicevui", "com.saicmotor.voiceagent",
            "com.saicvehicleservice",
            "com.saicmotor.hmi.eol", "com.saicmotor.hmi.hvac",
            "com.saicmotor.hmi.clusterprojection", "com.saicmotor.hmi.engmode",
            "com.saicmotor.hmi.launcher", "com.saicmotor.hmi.aroundview",
            "com.saicmotor.hmi.btcall", "com.saicmotor.hmi.radio",
            "com.saicmotor.hmi.music", "com.saicmotor.hmi.video",
            "com.saicmotor.hmi.vehiclesettings", "com.saicmotor.hmi.systemsettings",
            "com.saicmotor.hmi.pdfreader", "com.saicmotor.weathers",
            "com.saicmotor.rescuecall", "com.saicmotor.saicinbox",
            "com.saicmotor.update", "com.saicmotor.onlinemedia",
            "com.saic.saicmaintenance",
            "com.saicmotor.hmi.ep21avnlogin", "com.saicmotor.ep21avnlogin",
            "com.yfve.carotherservice", "com.yfve.usbupdate", "com.yfve.fileservice",
            "com.yfve.server.devicemanager",
            "com.allgo.carplay.service", "com.allgo.app.androidauto",
            "com.allgo.rui", "com.allgo.remoteui.mediabrowserservice",
            "com.allgo.mirroring.control.service", "com.allgo.service.mirroringcontrol",
            "android.car.usb.handler", "com.android.shell",
            "com.android.settings.intelligence", "com.android.statementservice",
            "com.android.systemui", "com.android.car.trust",
            "com.android.camera2", "com.android.camera",
            "com.telenav.app.arp", "com.abupdate.ota"
        ));

        List<AppInfo> apps = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(intent, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (hidden.contains(pkg)) continue;
            if (pkg.toLowerCase().contains("ep21avnlogin")) continue;
            AppInfo info = new AppInfo();
            info.label = ri.loadLabel(pm).toString();
            info.packageName = pkg;
            info.icon = ri.activityInfo.loadIcon(pm);
            apps.add(info);
        }
        return apps;
    }

    private class AppGridAdapter extends BaseAdapter {
        private final List<AppInfo> apps;
        AppGridAdapter(List<AppInfo> apps) { this.apps = apps; }
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int p) { return apps.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            LinearLayout cell = new LinearLayout(MainActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(4, 8, 4, 8);

            ImageView icon = new ImageView(MainActivity.this);
            icon.setImageDrawable(apps.get(pos).icon);
            icon.setLayoutParams(new LinearLayout.LayoutParams(64, 64));
            cell.addView(icon);

            TextView label = new TextView(MainActivity.this);
            label.setText(apps.get(pos).label);
            label.setTextSize(11);
            label.setTextColor(cText);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            cell.addView(label);
            return cell;
        }
    }

    // ==================== Polling & Updates ====================

    private void updateUI() {
        // SOC
        String socSaic = mVehicle.callSaicMethod("charging", "getCurrentElectricQuantity");
        String socDsp = mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_SOC_DSP);
        String soc = isValid(socSaic) ? socSaic : (isValid(socDsp) ? socDsp : null);
        if (soc != null && mBatteryPct != null) mBatteryPct.setText(soc + "%");

        // Range
        String rangeSaic = mVehicle.callSaicMethod("charging", "getCurrentEnduranceMileage");
        String clstrRange = mVehicle.getPropertyValue(YFVehicleProperty.CLSTR_ELEC_RNG);
        String range = isValid(rangeSaic) ? rangeSaic : (isValid(clstrRange) ? clstrRange : null);
        if (range != null && mBatteryRange != null) mBatteryRange.setText(range + " km");

        // BMS raw
        String bmsSoc = mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_SOC);
        String bmsRange = mVehicle.getPropertyValue(YFVehicleProperty.BMS_ESTD_ELEC_RNG);
        if (mBatteryBms != null) {
            StringBuilder raw = new StringBuilder("BMS: ");
            if (isValid(bmsSoc)) raw.append(bmsSoc).append("%");
            if (isValid(bmsRange)) raw.append(" | ").append(bmsRange).append(" km");
            mBatteryBms.setText(raw.toString());
        }

        // Battery ETA
        updateBatteryEta();

        // Outside temp
        String outTemp = mVehicle.getOutsideTemp();
        if (!isValid(outTemp)) outTemp = mVehicle.getPropertyValue(YFVehicleProperty.ENV_OUTSIDE_TEMPERATURE);
        if (isValid(outTemp) && mWeatherOutTemp != null) mWeatherOutTemp.setText(outTemp + "\u00B0C");

        // Cabin temp (from cloud)
        if (mWeatherCabinTemp != null) {
            String cabin = mCloud.getInteriorTempStr();
            if (cabin != null) {
                mWeatherCabinTemp.setText("Cabin: " + cabin + "\u00B0C");
                mWeatherCabinTemp.setVisibility(View.VISIBLE);
            }
        }

        // Drive mode + regen
        if (mDriveMode != null) {
            String drvRaw = mVehicle.getPropertyValue(YFVehicleProperty.SENSOR_ELECTRIC_DRIVER_MODE);
            int dm = 1;
            try { dm = (int) Float.parseFloat(drvRaw); } catch (Exception ignored) {}
            String modeName;
            switch (dm) {
                case 0: modeName = "Eco"; break;
                case 2: modeName = "Sport"; break;
                case 6: modeName = "Winter"; break;
                default: modeName = "Normal";
            }
            mDriveMode.setText(String.format(getString(R.string.mode_label), modeName));
        }
        if (mRegenLevel != null) {
            String rgRaw = mVehicle.getPropertyValue(YFVehicleProperty.AAD_EPTRGTNLVL);
            int rg = 0;
            try { rg = (int) Float.parseFloat(rgRaw); } catch (Exception ignored) {}
            mRegenLevel.setText(String.format(getString(R.string.regen_label), rg + 1));
        }

        // Update graphs dashboard gauges and charts
        updateGraphsDashboard();
    }

    private void updateGraphsDashboard() {
        // Speed
        float speed = parseFloat(mVehicle.callSaicMethod("condition", "getCarSpeed"));
        if (speed == 0) speed = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.PERF_VEHICLE_SPEED));
        if (mGpSpeed != null) mGpSpeed.setValue(speed);

        // SOC
        float soc = parseFloat(mVehicle.callSaicMethod("charging", "getCurrentElectricQuantity"));
        if (soc == 0) soc = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_SOC_DSP));
        if (mGpSoc != null) mGpSoc.setValue(soc);

        // RPM
        if (mGpRpm != null) mGpRpm.setValue(parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.ENGINE_RPM)));

        // Efficiency
        if (mGpEcon != null) mGpEcon.setValue(parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.SENSOR_DRIVE_EFFICIENCY_INDICATION)));

        // Power gauge: V*I/1000 = kW (positive = consume, negative = regen)
        float packV = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_VOL));
        float packI = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_CRNT));
        float powerKw = packV * packI / 1000f;
        if (mGpPowerGauge != null) mGpPowerGauge.setValue(powerKw);

        // G-meter
        float longG = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.SENSOR_ACCELERATION_PORTRAIT));
        float latG = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.SENSOR_VEHICLE_LATERAL_ACCELERATION));
        if (mGpGMeter != null) mGpGMeter.setValues(latG, longG);

        // Range + Gear + Mode info
        if (mGpRangeText != null) {
            String range = mVehicle.getPropertyValue(YFVehicleProperty.CLSTR_ELEC_RNG);
            int gearVal = (int) parseFloat(mVehicle.callSaicMethod("condition", "getCarGear"));
            String gear;
            switch (gearVal) { case 1: gear = "P"; break; case 2: gear = "R"; break; case 3: gear = "N"; break; case 4: gear = "D"; break; default: gear = String.valueOf(gearVal); }
            int dm = (int) parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.SENSOR_ELECTRIC_DRIVER_MODE));
            String mode;
            switch (dm) { case 0: mode = "Eco"; break; case 2: mode = "Sport"; break; case 6: mode = "Winter"; break; default: mode = "Normal"; }
            mGpRangeText.setText("Range: " + (isValid(range) ? range : "--") + " km  |  Gear: " + gear + "  |  " + mode);
        }
    }

    private static final float NOMINAL_CAPACITY_KWH = 70.0f;

    private void updateBatteryEta() {
        if (mBatteryEta == null) return;
        try {
            float soc = parseFloat(mVehicle.callSaicMethod("charging", "getCurrentElectricQuantity"));
            if (soc <= 0) soc = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_SOC_DSP));
            if (soc <= 0) soc = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_SOC));
            if (soc <= 0) { mBatteryEta.setText(""); return; }

            float packV = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_VOL));
            float packI = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_CRNT));
            float speed = parseFloat(mVehicle.callSaicMethod("condition", "getCarSpeed"));

            float chrgSts = parseFloat(mVehicle.callSaicMethod("charging", "getChargingStatus"));
            if (chrgSts == 1 || chrgSts == 2) { mBatteryEta.setText(getString(R.string.charging_active)); return; }

            float energyKwh = soc * NOMINAL_CAPACITY_KWH / 100f;
            float powerKw = packV * Math.abs(packI) / 1000f;
            if (powerKw < 0.01f) { mBatteryEta.setText(getString(R.string.minimal_power)); return; }

            float hours = energyKwh / powerKw;
            if (speed > 5) {
                if (hours < 1) mBatteryEta.setText(String.format(getString(R.string.min_driving), (int)(hours * 60)));
                else mBatteryEta.setText(String.format(getString(R.string.hours_driving), (int)hours, (int)((hours % 1) * 60)));
            } else {
                float days = hours / 24f;
                if (days < 1) mBatteryEta.setText(String.format(getString(R.string.hours_standby), (int)hours, (int)((hours % 1) * 60)));
                else mBatteryEta.setText(String.format(getString(R.string.days_standby), days));
            }
        } catch (Exception e) { mBatteryEta.setText(""); }
    }

    private static float parseFloat(String s) {
        if (s == null || s.equals("N/A") || s.equals("Connecting...") || s.isEmpty()) return 0f;
        try { return Float.parseFloat(s); } catch (Exception e) { return 0f; }
    }

    private boolean isValid(String val) {
        return val != null && !val.equals("N/A") && !val.equals("Connecting...") && !val.equals("0.00") && !val.equals("0");
    }

    private void startPolling() {
        mHandler.postDelayed(new Runnable() {
            @Override public void run() {
                updateUI();
                mWeather.poll(MainActivity.this);
                if (ThemeHelper.hasCarThemeChanged(MainActivity.this)) recreate();
                mHandler.postDelayed(this, 2000);
            }
        }, 2000);
    }

    // ==================== Lifecycle ====================

    private void queryCloudOnce() {
        if (!mCloud.isLoggedIn()) return;
        mHandler.postDelayed(() -> mCloud.queryVehicleStatus((ok, msg) -> Log.d(TAG, "Cloud: " + msg)), 8000);
    }

    private void autoRestoreProfile() {
        android.content.SharedPreferences prefs = getSharedPreferences("emegelauncher", MODE_PRIVATE);
        if (!prefs.getBoolean("profile_auto_restore", false)) return;
        int savedRegen = prefs.getInt("profile_regen_level", -1);
        if (savedRegen < 0) return;
        mHandler.postDelayed(() -> {
            if (mVehicle.hasSettingBinder()) {
                boolean ok = mVehicle.transactSettingInt(0xA1, savedRegen);
                Log.d(TAG, "Auto-restore regen " + savedRegen + ": " + ok);
            }
        }, 5000);
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
                else Toast.makeText(this, getString(R.string.app_not_found), Toast.LENGTH_SHORT).show();
            } catch (Exception e2) {
                Toast.makeText(this, getString(R.string.launch_error), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // HOME button pressed while launcher is running — go back to Main page
        if (mPager != null) mPager.setCurrentItem(PAGE_MAIN, true);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (mLastDarkMode != ThemeHelper.isDarkMode(this)) recreate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        mWeather.unregister(this);
        mVehicle.unbindService();
    }

    private void resolveColors() {
        cBg = ThemeHelper.resolveColor(this, R.attr.colorBgPrimary);
        cCard = ThemeHelper.resolveColor(this, R.attr.colorBgCard);
        cText = ThemeHelper.resolveColor(this, R.attr.colorTextPrimary);
        cTextSec = ThemeHelper.resolveColor(this, R.attr.colorTextSecondary);
        cTextTert = ThemeHelper.resolveColor(this, R.attr.colorTextTertiary);
        cDivider = ThemeHelper.resolveColor(this, R.attr.colorDivider);
    }
}

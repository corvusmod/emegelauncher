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
import android.graphics.Typeface;
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
    private boolean mCloudQueried = false;
    private final Handler mHandler = new Handler(android.os.Looper.getMainLooper());
    private boolean mLastDarkMode;
    private ViewPager mPager;

    // Main page widgets (updated by polling)
    private TextView mWeatherDesc, mWeatherForecastTemp, mWeatherSensorTemp, mWeatherCabinTemp;
    private ImageView mWeatherIcon;
    private TextView mBatteryPct, mBatteryRange, mBatteryEta, mBatteryBms;
    private TextView mDriveMode, mRegenLevel;
    private LinearLayout mDriveModeBar;

    // Theme colors
    private int cBg, cCard, cText, cTextSec, cTextTert, cDivider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        mLastDarkMode = ThemeHelper.isDarkMode(this);
        resolveColors();

        // Show disclaimer on first run — cannot be bypassed
        if (!getSharedPreferences("emegelauncher", MODE_PRIVATE).getBoolean("disclaimer_accepted", false)) {
            showDisclaimer();
            return;
        }

        initLauncher();
    }

    private void initLauncher() {
        mVehicle = VehicleServiceManager.getInstance(this);
        mVehicle.bindService();
        mCloud = new SaicCloudManager(this);
        mWeather = new WeatherManager();
        mWeather.register(this, (weather, temp) -> {
            if (mWeatherDesc != null) mWeatherDesc.setText(weather);
            if (mWeatherForecastTemp != null) mWeatherForecastTemp.setText(temp + "\u00B0C");
        });

        // ViewPager fills the screen
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(ThemeHelper.isDarkMode(this) ? R.drawable.bg_gradient_dark : R.drawable.bg_gradient_light);

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
    }

    private void showDisclaimer() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(cBg);
        layout.setPadding(40, 40, 40, 40);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText(getString(R.string.disclaimer_title));
        title.setTextSize(22);
        title.setTextColor(cText);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(0, 0, 0, 24);
        layout.addView(title);

        TextView body = new TextView(this);
        body.setText(getString(R.string.disclaimer_text));
        body.setTextSize(14);
        body.setTextColor(cTextSec);
        body.setLineSpacing(4, 1.1f);
        body.setPadding(0, 0, 0, 32);
        layout.addView(body);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        TextView declineBtn = new TextView(this);
        declineBtn.setText(getString(R.string.disclaimer_decline));
        declineBtn.setTextSize(16);
        declineBtn.setTextColor(ThemeHelper.accentRed(this));
        declineBtn.setPadding(40, 16, 40, 16);
        declineBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        declineBtn.setOnClickListener(v -> {
            finishAffinity();
            System.exit(0);
        });
        buttons.addView(declineBtn);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(40, 1));
        buttons.addView(spacer);

        TextView acceptBtn = new TextView(this);
        acceptBtn.setText(getString(R.string.disclaimer_accept));
        acceptBtn.setTextSize(16);
        acceptBtn.setTextColor(ThemeHelper.accentGreen(this));
        acceptBtn.setPadding(40, 16, 40, 16);
        acceptBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        acceptBtn.setOnClickListener(v -> {
            getSharedPreferences("emegelauncher", MODE_PRIVATE).edit()
                .putBoolean("disclaimer_accepted", true).apply();
            initLauncher();
        });
        buttons.addView(acceptBtn);

        layout.addView(buttons);
        scroll.addView(layout);
        setContentView(scroll);
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
        bar.setPadding(0, 4, 0, 6);

        int accent = ThemeHelper.accentBlue(this);
        for (int i = 0; i < 4; i++) {
            TextView dot = new TextView(this);
            dot.setTag("dot_" + i);
            dot.setText("\u2022");
            dot.setTextSize(i == PAGE_MAIN ? 14 : 10);
            dot.setTextColor(i == PAGE_MAIN ? accent : cTextTert);
            dot.setPadding(8, 0, 8, 0);
            bar.addView(dot);
        }

        mPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int pos) {
                int accentColor = ThemeHelper.accentBlue(MainActivity.this);
                for (int i = 0; i < 4; i++) {
                    TextView d = bar.findViewWithTag("dot_" + i);
                    if (d != null) {
                        d.setTextColor(i == pos ? accentColor : cTextTert);
                        d.setTextSize(i == pos ? 14 : 10);
                    }
                }
            }
        });
        return bar;
    }

    // ==================== Page 0: Graphs (live dashboard) ====================

    private com.emegelauncher.widget.ArcGaugeView mGpSpeed, mGpSoc, mGpRpm, mGpEcon;
    // G-meter: calculate G from speed changes
    private float mPrevSpeedMs = 0;
    private long mPrevSpeedTimeMs = 0;
    // Running average consumption: energy-based (power × time / distance)
    private double mEnergyAccumKwh = 0;  // total kWh consumed
    private double mDistanceAccumKm = 0; // total km driven
    // Eco score: session aggregate (exponential moving average)
    private float mEcoScoreAvg = 100f;
    private boolean mEcoScoreInit = false;
    private com.emegelauncher.widget.PowerGaugeView mGpPowerGauge;
    private com.emegelauncher.widget.GMeterView mGpGMeter;
    private TextView mGpRangeText;

    private View buildGraphsPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundResource(ThemeHelper.isDarkMode(this) ? R.drawable.bg_gradient_dark : R.drawable.bg_gradient_light);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(16, 4, 16, 4);
        page.setOnClickListener(v -> startActivity(new Intent(this, GraphsActivity.class)));

        // 4 gauges in 2x2
        LinearLayout gaugeRow1 = new LinearLayout(this);
        gaugeRow1.setBackgroundResource(R.drawable.card_bg_ripple);
        gaugeRow1.setPadding(8, 8, 8, 8);
        mGpSpeed = newGauge(getString(R.string.speed), "km/h", 220, ThemeHelper.accentBlue(this));
        mGpSoc = newGauge(getString(R.string.battery), "%", 100, ThemeHelper.accentGreen(this));
        gaugeRow1.addView(mGpSpeed, new LinearLayout.LayoutParams(0, 340, 1f));
        gaugeRow1.addView(mGpSoc, new LinearLayout.LayoutParams(0, 340, 1f));
        page.addView(gaugeRow1);

        LinearLayout gaugeRow2 = new LinearLayout(this);
        gaugeRow2.setBackgroundResource(R.drawable.card_bg_ripple);
        gaugeRow2.setPadding(8, 8, 8, 8);
        mGpRpm = newGauge("kWh/100km", "kWh/100km", 50, ThemeHelper.accentOrange(this));
        mGpEcon = newGauge("Eco", "", 100, ThemeHelper.accentGreen(this));
        gaugeRow2.addView(mGpRpm, new LinearLayout.LayoutParams(0, 340, 1f));
        gaugeRow2.addView(mGpEcon, new LinearLayout.LayoutParams(0, 340, 1f));
        page.addView(gaugeRow2);

        // Power gauge + G-Meter side by side
        LinearLayout chartsRow = new LinearLayout(this);
        chartsRow.setOrientation(LinearLayout.HORIZONTAL);
        chartsRow.setBackgroundResource(R.drawable.card_bg_ripple);
        chartsRow.setPadding(8, 8, 8, 8);

        mGpPowerGauge = new com.emegelauncher.widget.PowerGaugeView(this);
        mGpPowerGauge.setMaxKw(150);
        mGpPowerGauge.setLabel(getString(R.string.power_label));
        mGpPowerGauge.setUnit("kW");
        mGpPowerGauge.setConsumeColor(ThemeHelper.accentOrange(this));
        mGpPowerGauge.setRegenColor(ThemeHelper.accentGreen(this));
        mGpPowerGauge.setBgColor(cCard);
        mGpPowerGauge.setTextColor(cText);
        mGpPowerGauge.setLabelColor(cTextSec);
        LinearLayout.LayoutParams powerLp = new LinearLayout.LayoutParams(0, 340, 1f);
        powerLp.setMargins(0, 6, 4, 4);
        chartsRow.addView(mGpPowerGauge, powerLp);

        mGpGMeter = new com.emegelauncher.widget.GMeterView(this);
        mGpGMeter.setMaxG(1.0f);
        mGpGMeter.setDotColor(ThemeHelper.accentRed(this));
        mGpGMeter.setBgColor(cCard);
        mGpGMeter.setRingColor(cDivider);
        mGpGMeter.setTextColor(cText);
        mGpGMeter.setLabelColor(cTextSec);
        mGpGMeter.setAxisLabels(getString(R.string.gmeter_accel), getString(R.string.gmeter_brake));
        LinearLayout.LayoutParams gmeterLp = new LinearLayout.LayoutParams(0, 340, 1f);
        gmeterLp.setMargins(4, 6, 0, 4);
        chartsRow.addView(mGpGMeter, gmeterLp);

        page.addView(chartsRow);

        // Range + Gear + Mode info row
        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setBackgroundColor(cCard);
        infoRow.setPadding(16, 10, 16, 10);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);

        mGpRangeText = new TextView(this);
        mGpRangeText.setText(String.format(getString(R.string.range_gear_mode), "--", "--", "--"));
        mGpRangeText.setTextSize(18);
        mGpRangeText.setTextColor(cText);
        infoRow.addView(mGpRangeText);
        infoRow.setGravity(Gravity.CENTER);

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
        g.setLabelColor(cTextSec);
        return g;
    }

    // ==================== Page 1: Main ====================

    private View buildMainPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundResource(ThemeHelper.isDarkMode(this) ? R.drawable.bg_gradient_dark : R.drawable.bg_gradient_light);
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
        row2.addView(buildBigButton(getString(R.string.map), R.drawable.ic_map, ThemeHelper.accentBlue(this),
            () -> launchCarApp("com.telenav.app.arp", "com.telenav.arp.module.map.MainActivity")), bigBtnLP(true));
        row2.addView(buildBigButton(getString(R.string.music), R.drawable.ic_music, ThemeHelper.accentPurple(this),
            () -> launchCarApp("com.saicmotor.hmi.music", "com.saicmotor.hmi.music.ui.activity.MusicActivity")), bigBtnLP(false));
        grid.addView(row2, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Row 3: Radio | Phone
        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.addView(buildBigButton(getString(R.string.radio), R.drawable.ic_radio, ThemeHelper.accentOrange(this),
            () -> launchCarApp("com.saicmotor.hmi.radio", "com.saicmotor.hmi.radio.app.RadioHomeActivity")), bigBtnLP(true));
        row3.addView(buildBigButton(getString(R.string.phone), R.drawable.ic_phone, ThemeHelper.accentGreen(this),
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
        card.setBackgroundResource(R.drawable.card_bg_ripple);
        card.setPadding(24, 20, 24, 20);
        card.setGravity(Gravity.CENTER);
        card.setElevation(6f);
        card.setOnClickListener(v -> launchCarApp("com.saicmotor.weathers", "com.saicmotor.weathers.activity.MainActivity"));

        // Weather icon
        mWeatherIcon = new ImageView(this);
        mWeatherIcon.setImageResource(R.drawable.ic_weather_partly_cloudy);
        mWeatherIcon.setColorFilter(ThemeHelper.accentOrange(this));
        mWeatherIcon.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
        card.addView(mWeatherIcon);

        // Weather description (from weather app broadcast)
        mWeatherDesc = new TextView(this);
        mWeatherDesc.setText(getString(R.string.weather));
        mWeatherDesc.setTextSize(15);
        mWeatherDesc.setTextColor(cText);
        mWeatherDesc.setGravity(Gravity.CENTER);
        mWeatherDesc.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(mWeatherDesc);

        // Forecast temperature (from weather app)
        mWeatherForecastTemp = new TextView(this);
        mWeatherForecastTemp.setText("--\u00B0C");
        mWeatherForecastTemp.setTextSize(32);
        mWeatherForecastTemp.setTextColor(ThemeHelper.accentOrange(this));
        mWeatherForecastTemp.setGravity(Gravity.CENTER);
        mWeatherForecastTemp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        card.addView(mWeatherForecastTemp);

        // Outside sensor temperature (from SAIC AirCondition / VHAL)
        mWeatherSensorTemp = new TextView(this);
        mWeatherSensorTemp.setText(getString(R.string.outside) + ": --\u00B0C");
        mWeatherSensorTemp.setTextSize(13);
        mWeatherSensorTemp.setTextColor(cTextSec);
        mWeatherSensorTemp.setGravity(Gravity.CENTER);
        card.addView(mWeatherSensorTemp);

        // Cabin temperature (from cloud, hidden if unavailable)
        mWeatherCabinTemp = new TextView(this);
        mWeatherCabinTemp.setTextSize(13);
        mWeatherCabinTemp.setTextColor(cTextSec);
        mWeatherCabinTemp.setGravity(Gravity.CENTER);
        mWeatherCabinTemp.setVisibility(View.GONE);
        card.addView(mWeatherCabinTemp);

        return card;
    }

    private com.emegelauncher.widget.BatteryView mBatteryIcon;

    private View buildBatteryCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackgroundResource(R.drawable.card_bg_ripple);
        card.setPadding(16, 16, 20, 16);
        card.setGravity(Gravity.CENTER);
        card.setElevation(6f);
        card.setOnClickListener(v -> launchCarApp(
            "com.saicmotor.hmi.vehiclesettings",
            "com.saicmotor.hmi.vehiclesettings.chargemanagement.ui.ChargeManagementActivity"));

        // Battery icon with dynamic fill (left side)
        mBatteryIcon = new com.emegelauncher.widget.BatteryView(this);
        mBatteryIcon.setOutlineColor(cTextTert);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(120, -1);
        iconLp.setMargins(4, 12, 20, 12);
        card.addView(mBatteryIcon, iconLp);

        // Text column (right side, centered)
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setGravity(Gravity.CENTER);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        // SOC% — large and prominent
        mBatteryPct = new TextView(this);
        mBatteryPct.setText("--%");
        mBatteryPct.setTextSize(42);
        mBatteryPct.setTextColor(cText);
        mBatteryPct.setGravity(Gravity.CENTER);
        mBatteryPct.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        textCol.addView(mBatteryPct);

        // Range — equally prominent
        mBatteryRange = new TextView(this);
        mBatteryRange.setText("-- km");
        mBatteryRange.setTextSize(26);
        mBatteryRange.setTextColor(ThemeHelper.accentTeal(this));
        mBatteryRange.setGravity(Gravity.CENTER);
        mBatteryRange.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textCol.addView(mBatteryRange);

        mBatteryEta = new TextView(this);
        mBatteryEta.setTextSize(15);
        mBatteryEta.setTextColor(cTextSec);
        mBatteryEta.setGravity(Gravity.CENTER);
        textCol.addView(mBatteryEta);

        mBatteryBms = new TextView(this);
        mBatteryBms.setTextSize(14);
        mBatteryBms.setTextColor(cTextTert);
        mBatteryBms.setGravity(Gravity.CENTER);
        textCol.addView(mBatteryBms);

        card.addView(textCol);

        return card;
    }

    private View buildBigButton(String text, int iconRes, int accentColor, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg_ripple);
        card.setGravity(Gravity.CENTER);
        card.setElevation(6f);
        card.setOnClickListener(v -> action.run());

        ImageView iconView = new ImageView(this);
        iconView.setImageResource(iconRes);
        iconView.setColorFilter(accentColor);
        iconView.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
        card.addView(iconView);

        TextView labelTv = new TextView(this);
        labelTv.setText(text);
        labelTv.setTextSize(22);
        labelTv.setTextColor(cText);
        labelTv.setGravity(Gravity.CENTER);
        labelTv.setPadding(0, 8, 0, 0);
        labelTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(labelTv);

        return card;
    }

    private View buildDriveModeBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundResource(R.drawable.card_bg_ripple);
        bar.setPadding(20, 10, 20, 10);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        mDriveModeBar = bar;

        // Drive mode icon
        ImageView modeIcon = new ImageView(this);
        modeIcon.setImageResource(R.drawable.ic_speed);
        modeIcon.setColorFilter(cTextSec);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(28, 28);
        iconLp.setMargins(0, 0, 12, 0);
        modeIcon.setLayoutParams(iconLp);
        bar.addView(modeIcon);

        mDriveMode = new TextView(this);
        mDriveMode.setText(String.format(getString(R.string.mode_label), "--"));
        mDriveMode.setTextSize(14);
        mDriveMode.setTextColor(cText);
        mDriveMode.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mDriveMode.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(mDriveMode);

        mRegenLevel = new TextView(this);
        mRegenLevel.setText(String.format(getString(R.string.regen_label), 0));
        mRegenLevel.setTextSize(14);
        mRegenLevel.setTextColor(ThemeHelper.accentTeal(this));
        mRegenLevel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mRegenLevel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        mRegenLevel.setGravity(Gravity.CENTER);
        bar.addView(mRegenLevel);

        // Profile restore button
        TextView restoreBtn = new TextView(this);
        restoreBtn.setText("\u21BB");
        restoreBtn.setTextSize(20);
        restoreBtn.setTextColor(ThemeHelper.accentGreen(this));
        restoreBtn.setPadding(16, 0, 0, 0);
        restoreBtn.setVisibility(View.GONE); // No regen/drive mode setters on Marvel R
        bar.addView(restoreBtn);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 4, 0, 0);
        bar.setLayoutParams(lp);
        return bar;
    }

    private LinearLayout.LayoutParams bigBtnLP(boolean isLeft) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.setMargins(isLeft ? 0 : 8, 8, isLeft ? 8 : 0, 8);
        return lp;
    }

    // ==================== Page 2: Apps ====================

    private View buildAppsPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundResource(ThemeHelper.isDarkMode(this) ? R.drawable.bg_gradient_dark : R.drawable.bg_gradient_light);
        page.setPadding(16, 8, 16, 0);

        // Top half: app grid
        GridView appGrid = new GridView(this);
        appGrid.setNumColumns(4);
        appGrid.setVerticalSpacing(16);
        appGrid.setHorizontalSpacing(12);
        appGrid.setPadding(8, 12, 8, 12);
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

        // Row 1: 4 buttons (CarPlay/360 greyed out if unavailable)
        LinearLayout r1 = new LinearLayout(this);
        View cpBtn = appBtn(getString(R.string.carplay), R.drawable.ic_carplay, () -> launchCarApp("com.allgo.carplay.service", "com.allgo.carplay.service.CarPlayActivity"));
        String cpStatus = mVehicle.callSaicMethod("sysbt", "getCarPlayConnected");
        if (!"true".equalsIgnoreCase(cpStatus) && !"1".equals(cpStatus)) {
            cpBtn.setAlpha(0.3f); cpBtn.setOnClickListener(null);
        }
        r1.addView(cpBtn, gridLP());

        View aaBtn = appBtn(getString(R.string.android_auto), R.drawable.ic_carplay, () -> launchCarApp("com.allgo.app.androidauto", "com.allgo.app.androidauto.ProjectionActivity"));
        // Android Auto uses same BT service — check if connected
        if (!"true".equalsIgnoreCase(cpStatus) && !"1".equals(cpStatus)) {
            aaBtn.setAlpha(0.3f); aaBtn.setOnClickListener(null);
        }
        r1.addView(aaBtn, gridLP());
        r1.addView(appBtn(getString(R.string.video), R.drawable.ic_video, () -> launchCarApp("com.saicmotor.hmi.video", "com.saicmotor.hmi.video.ui.activity.UsbVideoActivity")), gridLP());
        View view360Btn = appBtn(getString(R.string.view_360), R.drawable.ic_360, () -> launchCarApp("com.saicmotor.hmi.aroundview", "com.saicmotor.hmi.aroundview.aroundviewconfig.ui.AroundViewActivity"));
        // Check AVM_CAL_MD (360 calibration) — 0 means no cameras installed/calibrated
        String avmCal = mVehicle.getPropertyValue(com.emegelauncher.vehicle.YFVehicleProperty.AVM_CAL_MD);
        if ("0".equals(avmCal) || "N/A".equals(avmCal) || avmCal == null) {
            view360Btn.setAlpha(0.3f);
            view360Btn.setOnClickListener(null);
        }
        r1.addView(view360Btn, gridLP());
        btnGrid.addView(r1, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Row 2: 3 buttons
        LinearLayout r2 = new LinearLayout(this);
        r2.addView(appBtn(getString(R.string.car_settings), R.drawable.ic_settings, () -> launchCarApp("com.saicmotor.hmi.vehiclesettings", "com.saicmotor.hmi.vehiclesettings.vehicleconfig.ui.VehicleSettingsActivity")), gridLP());
        r2.addView(appBtn(getString(R.string.system_settings), R.drawable.ic_settings, () -> launchCarApp("com.saicmotor.hmi.systemsettings", "com.saicmotor.hmi.systemsettings.SettingsActivity")), gridLP());
        r2.addView(appBtn(getString(R.string.launcher_settings_short), R.drawable.ic_settings, () -> startActivity(new Intent(this, SettingsActivity.class))), gridLP());
        btnGrid.addView(r2, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Row 3: 3 buttons
        LinearLayout r3 = new LinearLayout(this);
        r3.addView(appBtn(getString(R.string.rescue), R.drawable.ic_rescue, () -> launchCarApp("com.saicmotor.rescuecall", "com.saicmotor.rescuecall.module.ICallCenterAct")), gridLP());
        r3.addView(appBtn(getString(R.string.touchpoint), R.drawable.ic_touchpoint, () -> launchCarApp("com.saic.saicmaintenance", "com.saic.saicmaintenance.module.maintaince.MaintaiceEp21Activity")), gridLP());
        r3.addView(appBtn(getString(R.string.manual), R.drawable.ic_manual, () -> launchCarApp("com.saicmotor.hmi.pdfreader", "com.saicmotor.hmi.pdfreader.PdfReaderActivity")), gridLP());
        btnGrid.addView(r3, new LinearLayout.LayoutParams(-1, 0, 1f));

        page.addView(btnGrid, new LinearLayout.LayoutParams(-1, 0, 1f));

        return page;
    }

    private View appBtn(String label, int iconRes, Runnable action) {
        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.VERTICAL);
        btn.setBackgroundResource(R.drawable.card_bg_ripple);
        btn.setGravity(Gravity.CENTER);
        btn.setElevation(4f);
        btn.setOnClickListener(v -> action.run());

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(cText);
        icon.setLayoutParams(new LinearLayout.LayoutParams(40, 40));
        btn.addView(icon);

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(11);
        labelTv.setTextColor(cText);
        labelTv.setGravity(Gravity.CENTER);
        labelTv.setPadding(0, 4, 0, 0);
        labelTv.setMaxLines(2);
        labelTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.addView(labelTv);

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
        scroll.setBackgroundResource(ThemeHelper.isDarkMode(this) ? R.drawable.bg_gradient_dark : R.drawable.bg_gradient_light);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(16, 8, 16, 8);

        // 2x3 button grid
        LinearLayout btnGrid = new LinearLayout(this);
        btnGrid.setOrientation(LinearLayout.VERTICAL);

        LinearLayout r1 = new LinearLayout(this);
        r1.addView(otherBtn(getString(R.string.diagnostics), R.drawable.ic_diagnostics, ThemeHelper.accentRed(this), () -> startActivity(new Intent(this, DebugActivity.class))), gridLP());
        r1.addView(otherBtn(getString(R.string.vehicle_info), R.drawable.ic_vehicle_info, ThemeHelper.accentPurple(this), () -> startActivity(new Intent(this, VehicleInfoActivity.class))), gridLP());
        btnGrid.addView(r1, new LinearLayout.LayoutParams(-1, 200));

        LinearLayout r2 = new LinearLayout(this);
        r2.addView(otherBtn(getString(R.string.location), R.drawable.ic_location, ThemeHelper.accentGreen(this), () -> startActivity(new Intent(this, LocationActivity.class))), gridLP());
        r2.addView(otherBtn(getString(R.string.tbox), R.drawable.ic_tbox, ThemeHelper.accentOrange(this), () -> startActivity(new Intent(this, TboxActivity.class))), gridLP());
        btnGrid.addView(r2, new LinearLayout.LayoutParams(-1, 200));

        LinearLayout r3 = new LinearLayout(this);
        r3.addView(otherBtn(getString(R.string.cloud), R.drawable.ic_cloud, ThemeHelper.accentBlue(this), () -> startActivity(new Intent(this, CloudActivity.class))), gridLP());
        r3.addView(otherBtn("USB Camera", R.drawable.ic_video, ThemeHelper.accentOrange(this), () -> startActivity(new Intent(this, UvcCameraActivity.class))), gridLP());
        btnGrid.addView(r3, new LinearLayout.LayoutParams(-1, 200));

        page.addView(btnGrid);

        // Info section
        addInfoSection(page, getString(R.string.cloud_status_label));
        addInfoRow(page, "cloud_status", mCloud.isLoggedIn() ? getString(R.string.cloud_info_logged) : getString(R.string.cloud_info_not_logged));
        String cabinTemp = mCloud.getInteriorTempStr();
        if (cabinTemp != null) addInfoRow(page, "cloud_cabin", getString(R.string.cloud_cabin) + ": " + cabinTemp + "\u00B0C");
        String batt12v = mCloud.getBatteryVoltageStr();
        if (batt12v != null) addInfoRow(page, "cloud_12v", getString(R.string.cloud_12v_battery) + ": " + batt12v + "V");

        String tboxJson = mCloud.getCachedTboxStatus();
        if (tboxJson != null) {
            try {
                org.json.JSONObject tj = new org.json.JSONObject(tboxJson);
                org.json.JSONObject td = tj.optJSONObject("data");
                if (td != null) {
                    int st = td.optInt("status", -1);
                    String stStr = st == 0 ? "Online" : st == 1 ? "Offline" : st == 2 ? "Sleep" : "Unknown";
                    addInfoRow(page, "tbox_status", getString(R.string.tbox) + ": " + stStr);
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

    private View otherBtn(String label, int iconRes, int accentColor, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg_ripple);
        card.setGravity(Gravity.CENTER);
        card.setElevation(4f);
        card.setOnClickListener(v -> action.run());

        ImageView iconView = new ImageView(this);
        iconView.setImageResource(iconRes);
        iconView.setColorFilter(accentColor);
        iconView.setLayoutParams(new LinearLayout.LayoutParams(56, 56));
        card.addView(iconView);

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(16);
        labelTv.setTextColor(accentColor);
        labelTv.setGravity(Gravity.CENTER);
        labelTv.setPadding(0, 8, 0, 0);
        labelTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
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
            "com.saicmotor.hmi.ep21avnlogin", "com.saicmotor.ep21avnlogin", "com.saic.avnlogin",
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
            cell.setPadding(4, 16, 4, 16);

            // Icon with rounded background wrapper
            LinearLayout iconWrapper = new LinearLayout(MainActivity.this);
            iconWrapper.setGravity(Gravity.CENTER);
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setShape(GradientDrawable.RECTANGLE);
            iconBg.setCornerRadius(12);
            iconBg.setColor(0x15FFFFFF);
            iconWrapper.setBackground(iconBg);
            iconWrapper.setPadding(8, 8, 8, 8);
            iconWrapper.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
            ImageView icon = new ImageView(MainActivity.this);
            icon.setImageDrawable(apps.get(pos).icon);
            icon.setLayoutParams(new LinearLayout.LayoutParams(72, 72));
            iconWrapper.addView(icon);
            cell.addView(iconWrapper);

            TextView label = new TextView(MainActivity.this);
            label.setText(apps.get(pos).label);
            label.setTextSize(13);
            label.setTextColor(cText);
            if (!ThemeHelper.isDarkMode(MainActivity.this)) {
                label.setShadowLayer(2, 0, 0, 0x40000000);
            }
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

        // Battery icon fill level
        if (mBatteryIcon != null && soc != null) {
            mBatteryIcon.setSoc(parseFloat(soc));
        }

        // Range
        String rangeSaic = mVehicle.callSaicMethod("charging", "getCurrentEnduranceMileage");
        String clstrRange = mVehicle.getPropertyValue(YFVehicleProperty.CLSTR_ELEC_RNG);
        String range = isValid(rangeSaic) ? rangeSaic : (isValid(clstrRange) ? clstrRange : null);
        if (range != null && mBatteryRange != null) mBatteryRange.setText(range + " km");

        // BMS raw + 12V battery from cloud
        String bmsSoc = mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_SOC);
        String bmsRange = mVehicle.getPropertyValue(YFVehicleProperty.BMS_ESTD_ELEC_RNG);
        if (mBatteryBms != null) {
            StringBuilder raw = new StringBuilder("BMS: ");
            if (isValid(bmsSoc)) raw.append(bmsSoc).append("%");
            if (isValid(bmsRange)) raw.append(" | ").append(bmsRange).append(" km");
            // Add 12V battery from cloud
            String batt12v = mCloud.getBatteryVoltageStr();
            if (batt12v != null) raw.append("\n12V: ").append(batt12v).append("V");
            mBatteryBms.setText(raw.toString());
        }

        // Battery ETA
        updateBatteryEta();

        // Outside sensor temp (from SAIC AirCondition / VHAL)
        String outTemp = mVehicle.getOutsideTemp();
        if (!isValid(outTemp)) outTemp = mVehicle.getPropertyValue(YFVehicleProperty.ENV_OUTSIDE_TEMPERATURE);
        if (isValid(outTemp) && mWeatherSensorTemp != null) {
            mWeatherSensorTemp.setText(getString(R.string.outside) + ": " + outTemp + "\u00B0C");
        }

        // Weather condition icon
        if (mWeatherIcon != null && mWeatherDesc != null) {
            String desc = mWeatherDesc.getText().toString().toLowerCase();
            if (desc.contains("storm") || desc.contains("tormenta") || desc.contains("thunder") || desc.contains("trueno") || desc.contains("lightning") || desc.contains("relámpago")) {
                mWeatherIcon.setImageResource(R.drawable.ic_weather_storm);
                mWeatherIcon.setColorFilter(0xFFFFD600);
            } else if (desc.contains("rain") || desc.contains("lluvia") || desc.contains("drizzle") || desc.contains("llovizna") || desc.contains("shower") || desc.contains("chubasco")) {
                mWeatherIcon.setImageResource(R.drawable.ic_weather_rain);
                mWeatherIcon.setColorFilter(ThemeHelper.accentBlue(this));
            } else if (desc.contains("snow") || desc.contains("nieve") || desc.contains("ice") || desc.contains("hielo") || desc.contains("sleet")) {
                mWeatherIcon.setImageResource(R.drawable.ic_weather_snow);
                mWeatherIcon.setColorFilter(0xFFFFFFFF);
            } else if (desc.contains("fog") || desc.contains("niebla") || desc.contains("mist") || desc.contains("bruma") || desc.contains("haze") || desc.contains("calima")) {
                mWeatherIcon.setImageResource(R.drawable.ic_weather_fog);
                mWeatherIcon.setColorFilter(cTextSec);
            } else if (desc.contains("partly") || desc.contains("parcial") || desc.contains("mostly") || desc.contains("mayormente")) {
                mWeatherIcon.setImageResource(R.drawable.ic_weather_partly_cloudy);
                mWeatherIcon.setColorFilter(ThemeHelper.accentOrange(this));
            } else if (desc.contains("cloud") || desc.contains("nube") || desc.contains("overcast") || desc.contains("nublado") || desc.contains("cubierto")) {
                mWeatherIcon.setImageResource(R.drawable.ic_weather_cloudy);
                mWeatherIcon.setColorFilter(cTextSec);
            } else if (desc.contains("sunny") || desc.contains("soleado") || desc.contains("clear") || desc.contains("despejado")) {
                mWeatherIcon.setImageResource(R.drawable.ic_weather_sunny);
                mWeatherIcon.setColorFilter(ThemeHelper.accentOrange(this));
            } else {
                mWeatherIcon.setImageResource(R.drawable.ic_weather_partly_cloudy);
                mWeatherIcon.setColorFilter(ThemeHelper.accentOrange(this));
            }
        }

        // Cabin temp (from cloud)
        if (mWeatherCabinTemp != null) {
            String cabin = mCloud.getInteriorTempStr();
            if (cabin != null) {
                mWeatherCabinTemp.setText(getString(R.string.cloud_cabin) + ": " + cabin + "\u00B0C");
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

            // Color-code drive mode bar
            if (mDriveModeBar != null) {
                int tint;
                switch (dm) {
                    case 0: tint = 0x1A30D158; break;  // Eco: green tint
                    case 2: tint = 0x1AFF453A; break;  // Sport: red tint
                    case 6: tint = 0x1A26A69A; break;  // Winter: cyan tint
                    default: tint = 0x1A2979FF; break;  // Normal: blue tint
                }
                // Blend tint with card color: alpha-composite the tint over cCard
                int tintA = (tint >> 24) & 0xFF;
                float tintF = tintA / 255f;
                int blendR = (int)(((tint >> 16) & 0xFF) * tintF + ((cCard >> 16) & 0xFF) * (1 - tintF));
                int blendG = (int)(((tint >> 8) & 0xFF) * tintF + ((cCard >> 8) & 0xFF) * (1 - tintF));
                int blendB = (int)((tint & 0xFF) * tintF + (cCard & 0xFF) * (1 - tintF));
                int blended = 0xFF000000 | (blendR << 16) | (blendG << 8) | blendB;
                GradientDrawable barBg = new GradientDrawable();
                barBg.setShape(GradientDrawable.RECTANGLE);
                barBg.setCornerRadius(16);
                barBg.setColor(blended);
                mDriveModeBar.setBackground(barBg);
            }
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

        // Power: V*I/1000 = kW
        float packV = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_VOL));
        float packI = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_CRNT));
        float powerKw = packV * packI / 1000f;
        if (mGpPowerGauge != null) mGpPowerGauge.setValue(powerKw);

        // Consumption gauge (replaces RPM — motor RPM always 0 on Marvel R)
        float consumption = Math.abs(parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.ELEC_CSUMP_PERKM)));
        // Calculate our own average from BMS power × time ÷ distance (independent of car's calculation)
        long now = System.currentTimeMillis();
        float speedMs = speed / 3.6f; // km/h to m/s
        if (mPrevSpeedTimeMs > 0) {
            float dt = (now - mPrevSpeedTimeMs) / 1000f;
            if (dt > 0 && dt < 5f && speed > 5) { // only accumulate when moving
                mEnergyAccumKwh += powerKw * dt / 3600.0; // kW × hours (regen subtracts)
                mDistanceAccumKm += speed * dt / 3600.0; // km/h × hours
            }
        }
        float displayAvg = mDistanceAccumKm > 0.05 ? (float)(mEnergyAccumKwh / mDistanceAccumKm * 100.0) : 0;
        if (mGpRpm != null) {
            mGpRpm.setValue(consumption);
            mGpRpm.setSecondaryValue(displayAvg);
            mGpRpm.setSecondaryColor(ThemeHelper.accentTeal(this));
            mGpRpm.setLabel(getString(R.string.consumption_label, consumption, displayAvg));
        }

        // Eco Score — session aggregate with live behavior indicator
        // Instantaneous score based on current driving factors
        float ecoInstant = 100f;
        float consRef = displayAvg > 0.1f ? displayAvg : consumption;
        if (consRef > 12) ecoInstant -= (consRef - 12) * 2.5f;
        if (powerKw > 30) ecoInstant -= (powerKw - 30) * 0.8f;
        if (powerKw < -5) ecoInstant += Math.min(10, Math.abs(powerKw + 5) * 0.5f);
        if (speed > 110) ecoInstant -= (speed - 110) * 0.5f;
        ecoInstant = Math.max(0, Math.min(100, ecoInstant));
        // Blend into session average (EMA: ~30s time constant at 1Hz polling)
        if (!mEcoScoreInit) { mEcoScoreAvg = ecoInstant; mEcoScoreInit = true; }
        else { mEcoScoreAvg = mEcoScoreAvg * 0.97f + ecoInstant * 0.03f; }
        float ecoScore = Math.max(0, Math.min(100, mEcoScoreAvg));
        if (mGpEcon != null) {
            mGpEcon.setValue(ecoScore);
            // Driving behavior indicator: colored arrows (shows live behavior)
            String indicator;
            int indicatorColor;
            if (powerKw > 50) {
                indicator = getString(R.string.eco_hard_accel);
                indicatorColor = 0xFFFF3B30;
            } else if (powerKw > 30) {
                indicator = getString(R.string.eco_accelerating);
                indicatorColor = 0xFFFF9500;
            } else if (powerKw < -10) {
                indicator = getString(R.string.eco_strong_regen);
                indicatorColor = 0xFF30D158;
            } else if (powerKw < -5) {
                indicator = getString(R.string.eco_regen);
                indicatorColor = 0xFF26A69A;
            } else if (speed > 110) {
                indicator = getString(R.string.eco_high_speed);
                indicatorColor = 0xFFFF9500;
            } else if (speed > 3 && Math.abs(powerKw) < 5) {
                indicator = getString(R.string.eco_coasting);
                indicatorColor = 0xFF30D158;
            } else {
                indicator = getString(R.string.eco_steady);
                indicatorColor = 0xFF636366;
            }
            mGpEcon.setLabel(indicator);
            mGpEcon.setLabelColor(indicatorColor);
            if (ecoScore >= 70) mGpEcon.setFgColor(0xFF30D158);
            else if (ecoScore >= 40) mGpEcon.setFgColor(0xFFFF9500);
            else mGpEcon.setFgColor(0xFFFF3B30);
        }

        // G-meter: longitudinal from speed derivative, lateral from steering angle
        float longG = 0, latG = 0;
        if (mPrevSpeedTimeMs > 0) {
            float dt = (now - mPrevSpeedTimeMs) / 1000f;
            if (dt > 0.1f && dt < 5f) {
                longG = (speedMs - mPrevSpeedMs) / dt / 9.81f;
                // Lateral G from steering wheel angle: latG = v² × tan(angle/ratio) / (wheelbase × g)
                float steerAngle = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.SENSOR_WHEEL_ANGLE));
                if (Math.abs(steerAngle) > 1f && speed > 3f) {
                    float wheelbase = 2.8f;   // Marvel R wheelbase in meters
                    float steerRatio = 14.5f; // steering ratio
                    float roadAngle = steerAngle / steerRatio;
                    latG = (float)(speedMs * speedMs * Math.tan(Math.toRadians(roadAngle)) / (wheelbase * 9.81f));
                }
            }
        }
        mPrevSpeedMs = speedMs;
        mPrevSpeedTimeMs = now;
        if (mGpGMeter != null) mGpGMeter.setValues(latG, longG);

        // Range + Gear + Mode info
        if (mGpRangeText != null) {
            // Range: try SAIC first, then VHAL cluster (same logic as GraphsActivity)
            float rangeSaicF = parseFloat(mVehicle.callSaicMethod("charging", "getCurrentEnduranceMileage"));
            String clstrRangeStr = mVehicle.getPropertyValue(YFVehicleProperty.CLSTR_ELEC_RNG);
            String displayRange;
            if (rangeSaicF > 0) displayRange = String.valueOf((int) rangeSaicF);
            else if (isValid(clstrRangeStr)) displayRange = clstrRangeStr;
            else displayRange = "--";

            int gearVal = (int) parseFloat(mVehicle.callSaicMethod("condition", "getCarGear"));
            String gear;
            switch (gearVal) { case 1: gear = "P"; break; case 2: gear = "R"; break; case 3: gear = "N"; break; case 4: gear = "D"; break; default: gear = String.valueOf(gearVal); }
            int dm = (int) parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.SENSOR_ELECTRIC_DRIVER_MODE));
            String mode;
            switch (dm) { case 0: mode = "Eco"; break; case 2: mode = "Sport"; break; case 6: mode = "Winter"; break; default: mode = "Normal"; }
            mGpRangeText.setText(String.format(getString(R.string.range_gear_mode), displayRange, gear, mode));
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
                feedTripRecorder();
                checkTboxAndCloud();
                mWeather.poll(MainActivity.this);
                if (ThemeHelper.hasCarThemeChanged(MainActivity.this)) recreate();
                mHandler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    /** Trigger cloud query once TBox is online and head unit has internet */
    private void checkTboxAndCloud() {
        if (mCloudQueried) return;
        float tboxAvail = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.SENSOR_TBOXAVLBLY));
        if (tboxAvail <= 0) return;
        // Check Android network connectivity (head unit shares TBox's connection)
        android.net.ConnectivityManager cm =
            (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        android.net.NetworkInfo ni = cm != null ? cm.getActiveNetworkInfo() : null;
        if (ni == null || !ni.isConnected()) return;
        Log.d(TAG, "TBox online + internet available (network=" + ni.getTypeName() + "), starting cloud query");
        mCloudQueried = true;
        queryCloudOnce();
    }

    /** Feed data to trip recorder if recording (runs on every poll cycle regardless of active screen) */
    private void feedTripRecorder() {
        com.emegelauncher.vehicle.TripRecorder rec = com.emegelauncher.vehicle.TripRecorder.getInstance(this);
        if (!rec.isRecording()) return;
        try {
            double lat = 0, lon = 0;
            String locJson = mVehicle.callSaicMethod("adaptervoice", "getCurLocationDesc");
            if (locJson != null && locJson.startsWith("{")) {
                org.json.JSONObject loc = new org.json.JSONObject(locJson);
                lat = loc.optDouble("lat", 0);
                lon = loc.optDouble("lon", 0);
            }
            float speed = parseFloat(mVehicle.callSaicMethod("condition", "getCarSpeed"));
            if (speed == 0) speed = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.PERF_VEHICLE_SPEED));
            float pV = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_VOL));
            float pI = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_CRNT));
            float powerKw = pV * pI / 1000f;
            float soc = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_SOC_DSP));
            float consumption = Math.abs(parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.ELEC_CSUMP_PERKM)));
            rec.addPoint(lat, lon, 0, speed, powerKw, soc, consumption, 0, 0);
        } catch (Exception ignored) {}
    }

    // ==================== Lifecycle ====================

    private void queryCloudOnce() {
        if (!mCloud.isLoggedIn()) return;
        mCloud.queryVehicleStatus((ok, msg) -> {
            Log.d(TAG, "Cloud status: " + msg);
            // Fetch all other cloud data
            mCloud.queryCharging(null);
            mCloud.queryBtKeys(null);
            mCloud.queryFeatures(null);
            mCloud.queryTboxStatus(null);
            mCloud.queryFota(null);
            mCloud.queryFavoritePois(null);
            // Statistics — fetch year view on startup
            String now = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
            mCloud.queryStatistics("3", now, null); // "3" = year
        });
    }

    private void autoRestoreProfile() {
        // Regen/drive mode setters do not exist on Marvel R firmware
        // TX code scan of all 5 SAIC services confirmed no setter available
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

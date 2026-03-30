/*
 * Emegelauncher - Custom Launcher for MG Marvel R
 * Copyright (C) 2026 Emegelauncher Contributors
 *
 * Licensed under the Apache License, Version 2.0 with the
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
import android.widget.FrameLayout;
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
    private static final int PAGE_CHARGING = 0, PAGE_GRAPHS = 1, PAGE_MAIN = 2, PAGE_APPS = 3, PAGE_OTHER = 4;

    private VehicleServiceManager mVehicle;
    private WeatherManager mWeather;
    private SaicCloudManager mCloud;
    private com.emegelauncher.vehicle.AbrpManager mAbrp;
    private com.emegelauncher.vehicle.FileLogger mLog;
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

    // Dynamic cards
    // Radio card
    private TextView mRadioTitle, mRadioSubtitle;
    private ImageView mRadioPlayPause;
    // Music card
    private TextView mMusicTitle, mMusicSubtitle, mMusicTime;
    private ImageView mMusicArt, mMusicPlayPause;
    private android.media.session.MediaController mMediaController;
    private ImageView mNavTurnArrow;
    private TextView mNavDist, mNavRoad, mNavRemaining, mNavSpeedLimit;
    private TextView mNavStopBtn;
    private LinearLayout mNavQuickBtns, mNavActiveInfo;
    private TextView mPhoneDevice, mPhoneStatus;

    // App grid (refreshable)
    private GridView mAppGrid;
    private List<AppInfo> mAppList;

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
        mAbrp = com.emegelauncher.vehicle.AbrpManager.getInstance(this);
        mLog = com.emegelauncher.vehicle.FileLogger.getInstance(this);
        mLog.i(TAG, "=== Launcher started v2.0 ===");
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
        mPager.setOffscreenPageLimit(4);
        root.addView(mPager, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Page indicator dots
        root.addView(buildPageIndicator(), new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);

        startPolling();
        // Enable freeform window support system-wide (for floating app windows)
        enableFreeformSupport();

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
        @Override public int getCount() { return 5; }
        @Override public boolean isViewFromObject(View v, Object o) { return v == o; }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View page;
            switch (position) {
                case PAGE_CHARGING: page = buildChargingPage(); break;
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
        for (int i = 0; i < 5; i++) {
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
                for (int i = 0; i < 5; i++) {
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

        // Row 1: Navigation | Battery
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(buildNavCard(), bigBtnLP(true));
        row1.addView(buildBatteryCard(), bigBtnLP(false));
        grid.addView(row1, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Row 2: Radio | Music (two independent cards, like original SAIC launcher)
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(buildRadioCard(), bigBtnLP(true));
        row2.addView(buildMusicCard(), bigBtnLP(false));
        grid.addView(row2, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Row 3: Weather | Phone
        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.addView(buildWeatherCard(), bigBtnLP(true));
        row3.addView(buildPhoneCard(), bigBtnLP(false));
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
        mWeatherIcon.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
        card.addView(mWeatherIcon);

        // Weather description (from weather app broadcast)
        mWeatherDesc = new TextView(this);
        mWeatherDesc.setText(getString(R.string.weather));
        mWeatherDesc.setTextSize(30);
        mWeatherDesc.setTextColor(cText);
        mWeatherDesc.setGravity(Gravity.CENTER);
        mWeatherDesc.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(mWeatherDesc);

        // Forecast temperature (from weather app)
        mWeatherForecastTemp = new TextView(this);
        mWeatherForecastTemp.setText("--\u00B0C");
        mWeatherForecastTemp.setTextSize(48);
        mWeatherForecastTemp.setTextColor(ThemeHelper.accentOrange(this));
        mWeatherForecastTemp.setGravity(Gravity.CENTER);
        mWeatherForecastTemp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        card.addView(mWeatherForecastTemp);

        // Outside sensor temperature (from SAIC AirCondition / VHAL)
        mWeatherSensorTemp = new TextView(this);
        mWeatherSensorTemp.setText(getString(R.string.outside) + ": --\u00B0C");
        mWeatherSensorTemp.setTextSize(24);
        mWeatherSensorTemp.setTextColor(cTextSec);
        mWeatherSensorTemp.setGravity(Gravity.CENTER);
        card.addView(mWeatherSensorTemp);

        // Cabin temperature (from cloud, hidden if unavailable)
        mWeatherCabinTemp = new TextView(this);
        mWeatherCabinTemp.setTextSize(24);
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

    // ==================== Dynamic Cards ====================

    // ==================== Now Playing Card (merged Music + Radio) ====================

    // ==================== Radio Card (independent, like original SAIC launcher) ====================

    private View buildRadioCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg_ripple);
        card.setPadding(16, 12, 16, 10);
        card.setElevation(6f);
        card.setGravity(Gravity.CENTER);
        card.setOnClickListener(v -> launchCarApp("com.saicmotor.hmi.radio", "com.saicmotor.hmi.radio.app.RadioHomeActivity"));

        // Source label
        TextView label = new TextView(this);
        label.setText(getString(R.string.radio));
        label.setTextSize(16);
        label.setTextColor(ThemeHelper.accentOrange(this));
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(label);

        // Station name / frequency
        mRadioTitle = new TextView(this);
        mRadioTitle.setTextSize(26);
        mRadioTitle.setTextColor(cText);
        mRadioTitle.setGravity(Gravity.CENTER);
        mRadioTitle.setSingleLine(true);
        mRadioTitle.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        mRadioTitle.setMarqueeRepeatLimit(-1);
        mRadioTitle.setSelected(true);
        mRadioTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mRadioTitle.setText(getString(R.string.radio_off));
        card.addView(mRadioTitle);

        // Frequency / band
        mRadioSubtitle = new TextView(this);
        mRadioSubtitle.setTextSize(20);
        mRadioSubtitle.setTextColor(cTextSec);
        mRadioSubtitle.setGravity(Gravity.CENTER);
        card.addView(mRadioSubtitle);

        // Transport controls
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, 6, 0, 0);

        ImageView prev = new ImageView(this);
        prev.setImageResource(android.R.drawable.ic_media_previous);
        prev.setColorFilter(cText);
        prev.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
        prev.setPadding(12, 12, 12, 12);
        prev.setOnClickListener(v -> { if (mLog != null) mLog.d(TAG, "TAP radio prev"); mVehicle.radioPrev(); });
        controls.addView(prev);

        mRadioPlayPause = new ImageView(this);
        mRadioPlayPause.setImageResource(android.R.drawable.ic_media_play);
        mRadioPlayPause.setColorFilter(ThemeHelper.accentOrange(this));
        LinearLayout.LayoutParams ppLp = new LinearLayout.LayoutParams(96, 96);
        ppLp.setMargins(12, 0, 12, 0);
        mRadioPlayPause.setLayoutParams(ppLp);
        mRadioPlayPause.setPadding(12, 12, 12, 12);
        mRadioPlayPause.setOnClickListener(v -> { if (mLog != null) mLog.d(TAG, "TAP radio play/pause"); mVehicle.radioPlayPause(); });
        controls.addView(mRadioPlayPause);

        ImageView next = new ImageView(this);
        next.setImageResource(android.R.drawable.ic_media_next);
        next.setColorFilter(cText);
        next.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
        next.setPadding(12, 12, 12, 12);
        next.setOnClickListener(v -> { if (mLog != null) mLog.d(TAG, "TAP radio next"); mVehicle.radioNext(); });
        controls.addView(next);

        card.addView(controls);
        return card;
    }

    // ==================== Music Card (independent, like original SAIC launcher) ====================

    private View buildMusicCard() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundResource(R.drawable.card_bg_ripple);
        frame.setElevation(6f);
        frame.setOnClickListener(v -> launchCarApp("com.saicmotor.hmi.music", "com.saicmotor.hmi.music.ui.activity.MusicActivity"));

        // Album art as dimmed background
        mMusicArt = new ImageView(this);
        mMusicArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mMusicArt.setAlpha(0.25f);
        mMusicArt.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        frame.addView(mMusicArt);

        // Content overlay
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(16, 10, 16, 8);
        card.setGravity(Gravity.CENTER);
        card.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

        // Source label
        TextView label = new TextView(this);
        label.setText(getString(R.string.music));
        label.setTextSize(16);
        label.setTextColor(ThemeHelper.accentPurple(this));
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(label);

        // Title (track name)
        mMusicTitle = new TextView(this);
        mMusicTitle.setText(getString(R.string.now_playing_idle));
        mMusicTitle.setTextSize(22);
        mMusicTitle.setTextColor(cText);
        mMusicTitle.setGravity(Gravity.CENTER);
        mMusicTitle.setSingleLine(true);
        mMusicTitle.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        mMusicTitle.setMarqueeRepeatLimit(-1);
        mMusicTitle.setSelected(true);
        mMusicTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(mMusicTitle);

        // Subtitle (artist)
        mMusicSubtitle = new TextView(this);
        mMusicSubtitle.setTextSize(18);
        mMusicSubtitle.setTextColor(cTextSec);
        mMusicSubtitle.setGravity(Gravity.CENTER);
        mMusicSubtitle.setSingleLine(true);
        card.addView(mMusicSubtitle);

        // Transport controls
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, 4, 0, 0);

        ImageView prev = new ImageView(this);
        prev.setImageResource(android.R.drawable.ic_media_previous);
        prev.setColorFilter(cText);
        prev.setLayoutParams(new LinearLayout.LayoutParams(72, 72));
        prev.setPadding(10, 10, 10, 10);
        prev.setOnClickListener(v -> {
            if (mLog != null) mLog.d(TAG, "TAP music prev");
            if (mMediaController != null) mMediaController.getTransportControls().skipToPrevious();
        });
        controls.addView(prev);

        mMusicPlayPause = new ImageView(this);
        mMusicPlayPause.setImageResource(android.R.drawable.ic_media_play);
        mMusicPlayPause.setColorFilter(ThemeHelper.accentPurple(this));
        LinearLayout.LayoutParams ppLp = new LinearLayout.LayoutParams(88, 88);
        ppLp.setMargins(10, 0, 10, 0);
        mMusicPlayPause.setLayoutParams(ppLp);
        mMusicPlayPause.setPadding(10, 10, 10, 10);
        mMusicPlayPause.setOnClickListener(v -> {
            if (mLog != null) mLog.d(TAG, "TAP music play/pause");
            if (mMediaController == null) return;
            android.media.session.PlaybackState state = mMediaController.getPlaybackState();
            if (state != null && state.getState() == android.media.session.PlaybackState.STATE_PLAYING) {
                mMediaController.getTransportControls().pause();
            } else {
                mMediaController.getTransportControls().play();
            }
        });
        controls.addView(mMusicPlayPause);

        ImageView next = new ImageView(this);
        next.setImageResource(android.R.drawable.ic_media_next);
        next.setColorFilter(cText);
        next.setLayoutParams(new LinearLayout.LayoutParams(72, 72));
        next.setPadding(10, 10, 10, 10);
        next.setOnClickListener(v -> {
            if (mLog != null) mLog.d(TAG, "TAP music next");
            if (mMediaController != null) mMediaController.getTransportControls().skipToNext();
        });
        controls.addView(next);
        card.addView(controls);

        // Time
        mMusicTime = new TextView(this);
        mMusicTime.setTextSize(14);
        mMusicTime.setTextColor(cTextSec);
        mMusicTime.setGravity(Gravity.CENTER);
        card.addView(mMusicTime);

        frame.addView(card);
        return frame;
    }

    private View buildNavCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg_ripple);
        card.setPadding(20, 12, 20, 10);
        card.setElevation(6f);
        card.setOnClickListener(v -> launchCarApp("com.telenav.app.arp", "com.telenav.arp.module.map.MainActivity"));

        // Active navigation info (shown when navigating)
        mNavActiveInfo = new LinearLayout(this);
        mNavActiveInfo.setOrientation(LinearLayout.VERTICAL);
        mNavActiveInfo.setGravity(Gravity.CENTER);
        mNavActiveInfo.setVisibility(View.GONE);
        mNavActiveInfo.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));

        // Top row: turn arrow + distance to turn
        LinearLayout turnRow = new LinearLayout(this);
        turnRow.setOrientation(LinearLayout.HORIZONTAL);
        turnRow.setGravity(Gravity.CENTER);

        mNavTurnArrow = new ImageView(this);
        mNavTurnArrow.setImageResource(R.drawable.ic_map);
        mNavTurnArrow.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mNavTurnArrow.setLayoutParams(new LinearLayout.LayoutParams(130, 110));
        turnRow.addView(mNavTurnArrow);

        mNavDist = new TextView(this);
        mNavDist.setTextSize(42);
        mNavDist.setTextColor(cText);
        mNavDist.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mNavDist.setPadding(12, 0, 0, 0);
        mNavDist.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        turnRow.addView(mNavDist);

        // Stop button (top right)
        mNavStopBtn = new TextView(this);
        mNavStopBtn.setText(getString(R.string.nav_stop));
        mNavStopBtn.setTextSize(16);
        mNavStopBtn.setTextColor(0xFFFF3B30);
        mNavStopBtn.setGravity(Gravity.CENTER);
        mNavStopBtn.setPadding(12, 6, 12, 6);
        GradientDrawable stopBg = new GradientDrawable();
        stopBg.setCornerRadius(8f);
        stopBg.setStroke(1, 0xFFFF3B30);
        mNavStopBtn.setBackground(stopBg);
        mNavStopBtn.setOnClickListener(v -> { if (mLog != null) mLog.i(TAG, "TAP stop navigation"); mVehicle.stopNavigation(); });
        turnRow.addView(mNavStopBtn);

        mNavActiveInfo.addView(turnRow);

        // Road name
        mNavRoad = new TextView(this);
        mNavRoad.setTextSize(34);
        mNavRoad.setTextColor(cTextSec);
        mNavRoad.setGravity(Gravity.CENTER);
        mNavRoad.setSingleLine(true);
        mNavRoad.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        mNavRoad.setMarqueeRepeatLimit(-1);
        mNavRoad.setSelected(true);
        mNavRoad.setPadding(0, 4, 0, 0);
        mNavActiveInfo.addView(mNavRoad);

        // Spacer
        View navSpacer = new View(this);
        navSpacer.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        mNavActiveInfo.addView(navSpacer);

        // Bottom row: remaining distance/time | speed limit badge
        LinearLayout navBottom = new LinearLayout(this);
        navBottom.setOrientation(LinearLayout.HORIZONTAL);
        navBottom.setGravity(Gravity.CENTER_VERTICAL);

        mNavRemaining = new TextView(this);
        mNavRemaining.setTextSize(30);
        mNavRemaining.setTextColor(ThemeHelper.accentBlue(this));
        mNavRemaining.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        navBottom.addView(mNavRemaining);

        // Speed limit badge (red circle with number)
        mNavSpeedLimit = new TextView(this);
        mNavSpeedLimit.setTextSize(32);
        mNavSpeedLimit.setTextColor(0xFFFF3B30);
        mNavSpeedLimit.setTypeface(Typeface.DEFAULT_BOLD);
        mNavSpeedLimit.setGravity(Gravity.CENTER);
        mNavSpeedLimit.setVisibility(View.GONE);
        GradientDrawable limitBg = new GradientDrawable();
        limitBg.setShape(GradientDrawable.OVAL);
        limitBg.setStroke(3, 0xFFFF3B30);
        limitBg.setColor(cCard);
        mNavSpeedLimit.setBackground(limitBg);
        mNavSpeedLimit.setLayoutParams(new LinearLayout.LayoutParams(90, 90));
        navBottom.addView(mNavSpeedLimit);

        mNavActiveInfo.addView(navBottom);
        card.addView(mNavActiveInfo);

        // Quick nav buttons (shown when idle)
        mNavQuickBtns = new LinearLayout(this);
        mNavQuickBtns.setOrientation(LinearLayout.VERTICAL);
        mNavQuickBtns.setGravity(Gravity.CENTER);
        mNavQuickBtns.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));

        // Header
        LinearLayout idleHeader = new LinearLayout(this);
        idleHeader.setOrientation(LinearLayout.HORIZONTAL);
        idleHeader.setGravity(Gravity.CENTER_VERTICAL);
        ImageView navIco = new ImageView(this);
        navIco.setImageResource(R.drawable.ic_map);
        navIco.setColorFilter(ThemeHelper.accentBlue(this));
        navIco.setLayoutParams(new LinearLayout.LayoutParams(24, 24));
        idleHeader.addView(navIco);
        TextView navLbl = new TextView(this);
        navLbl.setText(getString(R.string.navigation));
        navLbl.setTextSize(28);
        navLbl.setTextColor(ThemeHelper.accentBlue(this));
        navLbl.setPadding(8, 0, 0, 0);
        idleHeader.addView(navLbl);
        mNavQuickBtns.addView(idleHeader);

        // Spacer
        View idleSpacer = new View(this);
        idleSpacer.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        mNavQuickBtns.addView(idleSpacer);

        // Home / Office buttons row
        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickRow.setGravity(Gravity.CENTER);
        quickRow.setPadding(0, 8, 0, 8);

        TextView homeBtn = new TextView(this);
        homeBtn.setText("\uD83C\uDFE0 " + getString(R.string.nav_home));
        homeBtn.setTextSize(34);
        homeBtn.setTextColor(cText);
        homeBtn.setPadding(32, 20, 32, 20);
        homeBtn.setGravity(Gravity.CENTER);
        homeBtn.setBackgroundResource(R.drawable.card_bg_ripple);
        homeBtn.setOnClickListener(v -> {
            try {
                // IGeneralService.goHome() via AIDL — same as original launcher
                String result = mVehicle.callSaicMethod("adaptergeneral", "goHome");
                Log.d(TAG, "Nav goHome result: " + result);
            } catch (Exception e) { Log.d(TAG, "Nav goHome failed: " + e.getMessage()); }
        });
        quickRow.addView(homeBtn);

        View btnSpacer = new View(this);
        btnSpacer.setLayoutParams(new LinearLayout.LayoutParams(16, 1));
        quickRow.addView(btnSpacer);

        TextView officeBtn = new TextView(this);
        officeBtn.setText("\uD83C\uDFE2 " + getString(R.string.nav_office));
        officeBtn.setTextSize(34);
        officeBtn.setTextColor(cText);
        officeBtn.setPadding(32, 20, 32, 20);
        officeBtn.setGravity(Gravity.CENTER);
        officeBtn.setBackgroundResource(R.drawable.card_bg_ripple);
        officeBtn.setOnClickListener(v -> {
            try {
                // IGeneralService.goOffice() via AIDL — same as original launcher
                String result = mVehicle.callSaicMethod("adaptergeneral", "goOffice");
                Log.d(TAG, "Nav goOffice result: " + result);
            } catch (Exception e) { Log.d(TAG, "Nav goOffice failed: " + e.getMessage()); }
        });
        quickRow.addView(officeBtn);

        mNavQuickBtns.addView(quickRow);

        // Idle hint
        TextView idleHint = new TextView(this);
        idleHint.setText(getString(R.string.nav_idle));
        idleHint.setTextSize(22);
        idleHint.setTextColor(cTextTert);
        idleHint.setGravity(Gravity.CENTER);
        mNavQuickBtns.addView(idleHint);

        card.addView(mNavQuickBtns);

        return card;
    }

    private LinearLayout mPhoneCard;

    private LinearLayout mPhoneCallList;

    private View buildPhoneCard() {
        mPhoneCard = new LinearLayout(this);
        mPhoneCard.setOrientation(LinearLayout.VERTICAL);
        mPhoneCard.setBackgroundResource(R.drawable.card_bg_ripple);
        mPhoneCard.setPadding(20, 8, 20, 8);
        mPhoneCard.setElevation(6f);
        mPhoneCard.setGravity(Gravity.CENTER);
        mPhoneCard.setOnClickListener(v -> launchCarApp("com.saicmotor.hmi.btcall", "com.saicmotor.hmi.btcall.BtCallActivity"));

        // Device name header
        mPhoneDevice = new TextView(this);
        mPhoneDevice.setText(getString(R.string.phone_no_device));
        mPhoneDevice.setTextSize(30);
        mPhoneDevice.setTextColor(ThemeHelper.accentGreen(this));
        mPhoneDevice.setGravity(Gravity.CENTER);
        mPhoneDevice.setSingleLine(true);
        mPhoneDevice.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        mPhoneDevice.setMarqueeRepeatLimit(-1);
        mPhoneDevice.setSelected(true);
        mPhoneCard.addView(mPhoneDevice);

        // Recent calls list (populated by updatePhoneCard)
        mPhoneCallList = new LinearLayout(this);
        mPhoneCallList.setOrientation(LinearLayout.VERTICAL);
        mPhoneCallList.setGravity(Gravity.CENTER);
        mPhoneCallList.setPadding(0, 8, 0, 4);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        mPhoneCallList.setLayoutParams(listLp);
        mPhoneCard.addView(mPhoneCallList);

        // Status line (call status / charger)
        mPhoneStatus = new TextView(this);
        mPhoneStatus.setTextSize(24);
        mPhoneStatus.setTextColor(cTextSec);
        mPhoneStatus.setGravity(Gravity.CENTER);
        mPhoneCard.addView(mPhoneStatus);

        return mPhoneCard;
    }

    private void loadRecentCalls() {
        if (mPhoneCallList == null) return;
        mPhoneCallList.removeAllViews();
        try {
            android.database.Cursor cursor = getContentResolver().query(
                android.provider.CallLog.Calls.CONTENT_URI,
                new String[]{
                    android.provider.CallLog.Calls.CACHED_NAME,
                    android.provider.CallLog.Calls.NUMBER,
                    android.provider.CallLog.Calls.TYPE,
                    android.provider.CallLog.Calls.DATE
                },
                null, null,
                android.provider.CallLog.Calls.DATE + " DESC"
            );
            if (cursor == null) return;
            int count = 0;
            while (cursor.moveToNext() && count < 3) {
                String name = cursor.getString(0);
                String number = cursor.getString(1);
                int type = cursor.getInt(2);
                if (number == null || number.isEmpty()) continue;

                String display = (name != null && !name.isEmpty()) ? name : number;
                String typeIcon;
                int typeColor;
                switch (type) {
                    case android.provider.CallLog.Calls.INCOMING_TYPE:
                        typeIcon = "\u2199"; typeColor = ThemeHelper.accentGreen(this); break; // ↙
                    case android.provider.CallLog.Calls.OUTGOING_TYPE:
                        typeIcon = "\u2197"; typeColor = ThemeHelper.accentBlue(this); break; // ↗
                    case android.provider.CallLog.Calls.MISSED_TYPE:
                        typeIcon = "\u2199"; typeColor = 0xFFFF3B30; break; // ↙ red
                    default:
                        typeIcon = "\u2022"; typeColor = cTextSec; break; // •
                }

                final String callNumber = number;
                TextView row = new TextView(this);
                row.setText(typeIcon + " " + display);
                row.setTextSize(30);
                row.setTextColor(cText);
                row.setSingleLine(true);
                row.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
                row.setMarqueeRepeatLimit(-1);
                row.setSelected(true);
                row.setGravity(Gravity.CENTER);
                row.setPadding(0, 8, 0, 8);
                row.setCompoundDrawablePadding(4);
                row.setOnClickListener(v -> {
                    try {
                        Intent callIntent = new Intent(Intent.ACTION_CALL, android.net.Uri.parse("tel:" + callNumber));
                        startActivity(callIntent);
                    } catch (Exception ignored) {}
                });
                mPhoneCallList.addView(row);
                count++;
            }
            cursor.close();
        } catch (Exception e) {
            Log.d(TAG, "Call log read: " + e.getMessage());
        }
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
        mDriveMode.setTextSize(26);
        mDriveMode.setTextColor(cText);
        mDriveMode.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mDriveMode.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(mDriveMode);

        mRegenLevel = new TextView(this);
        mRegenLevel.setText(String.format(getString(R.string.regen_label), 0));
        mRegenLevel.setTextSize(26);
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
        mAppGrid = new GridView(this);
        mAppGrid.setNumColumns(4);
        mAppGrid.setVerticalSpacing(16);
        mAppGrid.setHorizontalSpacing(12);
        mAppGrid.setPadding(8, 12, 8, 12);
        mAppList = loadApps();
        mAppGrid.setAdapter(new AppGridAdapter(mAppList));
        mAppGrid.setOnItemClickListener((p, v, pos, id) -> {
            Intent intent = getPackageManager().getLaunchIntentForPackage(mAppList.get(pos).packageName);
            if (intent != null) startActivity(intent);
        });
        mAppGrid.setOnItemLongClickListener((p, v, pos, id) -> {
            showAppContextMenu(mAppList.get(pos));
            return true;
        });
        page.addView(mAppGrid, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Register for package install/uninstall broadcasts to refresh grid
        registerPackageReceiver();

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
        View cpBtn = appBtn(getString(R.string.carplay), R.drawable.ic_carplay, () -> {
            if (mVehicle.isCarPlayConnected()) {
                mVehicle.resumeProjection();
            } else {
                mVehicle.launchProjection(VehicleServiceManager.REMOTE_DEVICE_CARPLAY);
            }
        });
        r1.addView(cpBtn, gridLP());

        View aaBtn = appBtn(getString(R.string.android_auto), R.drawable.ic_carplay, () -> {
            if (mVehicle.isAndroidAutoConnected()) {
                mVehicle.resumeProjection();
            } else {
                mVehicle.launchProjection(VehicleServiceManager.REMOTE_DEVICE_AA);
            }
        });
        r1.addView(aaBtn, gridLP());
        r1.addView(appBtn(getString(R.string.video), R.drawable.ic_video, () -> launchCarApp("com.saicmotor.hmi.video", "com.saicmotor.hmi.video.ui.activity.UsbVideoActivity")), gridLP());
        View view360Btn = appBtn(getString(R.string.view_360), R.drawable.ic_360, () -> launchCarApp("com.saicmotor.hmi.aroundview", "com.saicmotor.hmi.aroundview.aroundviewconfig.ui.AroundViewActivity"));
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
                    String stStr = st == 0 ? getString(R.string.tbox_online) : st == 1 ? getString(R.string.tbox_offline) : st == 2 ? getString(R.string.tbox_sleep) : getString(R.string.tbox_unknown);
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

    // ==================== App grid: refresh, context menu, floating window ====================

    private android.content.BroadcastReceiver mPackageReceiver;

    private void registerPackageReceiver() {
        if (mPackageReceiver != null) return;
        mPackageReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                Log.d(TAG, "Package changed: " + intent.getAction() + " " + intent.getDataString());
                refreshAppGrid();
            }
        };
        android.content.IntentFilter filter = new android.content.IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(mPackageReceiver, filter);
    }

    private void refreshAppGrid() {
        if (mAppGrid == null) return;
        mHandler.post(() -> {
            mAppList = loadApps();
            mAppGrid.setAdapter(new AppGridAdapter(mAppList));
        });
    }

    private void showAppContextMenu(AppInfo app) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(app.label);

        String[] options = {
            getString(R.string.app_open),
            getString(R.string.app_open_floating),
            getString(R.string.app_info),
            getString(R.string.app_uninstall),
        };
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // Open
                    Intent launch = getPackageManager().getLaunchIntentForPackage(app.packageName);
                    if (launch != null) startActivity(launch);
                    break;
                case 1: // Floating window — show size picker
                    showFloatingWindowSizePicker(app.packageName);
                    break;
                case 2: // App info
                    Intent info = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    info.setData(android.net.Uri.parse("package:" + app.packageName));
                    info.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(info);
                    break;
                case 3: // Uninstall
                    Intent uninstall = new Intent(Intent.ACTION_DELETE);
                    uninstall.setData(android.net.Uri.parse("package:" + app.packageName));
                    startActivity(uninstall);
                    break;
            }
        });
        builder.show();
    }

    /**
     * Launch an app in freeform (floating window) mode.
     * Uses ActivityOptions with launch bounds to create a movable/resizable window.
     * Requires freeform multi-window support (enabled on this car's Android 9).
     */
    private boolean mFreeformEnabled = false;

    /** Enable freeform window support system-wide. Call once on startup. */
    private void enableFreeformSupport() {
        if (mFreeformEnabled) return;
        try {
            android.content.ContentResolver cr = getContentResolver();
            // Global settings (WindowManagerService reads these at boot)
            android.provider.Settings.Global.putInt(cr, "enable_freeform_support", 1);
            android.provider.Settings.Global.putInt(cr, "force_resizable_activities", 1);
            android.provider.Settings.Global.putInt(cr, "development_enable_freeform_windows_support", 1);
            // Developer options settings
            android.provider.Settings.Global.putInt(cr, "development_settings_enabled", 1);
            // Secure settings (some devices check these instead)
            try {
                android.provider.Settings.Secure.putInt(cr, "enable_freeform_support", 1);
                android.provider.Settings.Secure.putInt(cr, "force_resizable_activities", 1);
            } catch (Exception ignored) {}

            mFreeformEnabled = true;
            // Log diagnostics
            boolean hasFreeform = getPackageManager().hasSystemFeature("android.software.freeform_window_management");
            int freeformVal = android.provider.Settings.Global.getInt(cr, "enable_freeform_support", 0);
            int forceResize = android.provider.Settings.Global.getInt(cr, "force_resizable_activities", 0);
            if (mLog != null) mLog.i(TAG, "Freeform: feature=" + hasFreeform
                + " enabled=" + freeformVal + " forceResize=" + forceResize);

            // Show one-time reboot notice if this is the first time enabling
            String prefKey = "freeform_reboot_shown";
            if (!getSharedPreferences("emegelauncher", MODE_PRIVATE).getBoolean(prefKey, false)) {
                getSharedPreferences("emegelauncher", MODE_PRIVATE).edit()
                    .putBoolean(prefKey, true).apply();
                Toast.makeText(this,
                    "Floating windows enabled. Restart the car for changes to take effect.",
                    Toast.LENGTH_LONG).show();
                if (mLog != null) mLog.i(TAG, "Freeform: first enable, reboot required");
            }
        } catch (Exception e) {
            if (mLog != null) mLog.d(TAG, "enableFreeform: " + e.getMessage());
        }
    }

    private void showFloatingWindowSizePicker(String packageName) {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int sw = dm.widthPixels;
        int sh = dm.heightPixels;

        String[] sizes = {
            getString(R.string.float_size_small, "40%"),
            getString(R.string.float_size_medium, "60%"),
            getString(R.string.float_size_large, "80%"),
            getString(R.string.float_size_top_half),
            getString(R.string.float_size_bottom_half),
            getString(R.string.float_size_left_half),
            getString(R.string.float_size_right_half),
        };
        // Each entry: {widthRatio, heightRatio, leftRatio, topRatio}
        float[][] params = {
            {0.40f, 0.35f, 0.30f, 0.30f},  // Small centered
            {0.60f, 0.50f, 0.20f, 0.25f},  // Medium centered
            {0.85f, 0.75f, 0.075f, 0.10f}, // Large centered
            {1.00f, 0.50f, 0.00f, 0.00f},  // Top half
            {1.00f, 0.50f, 0.00f, 0.50f},  // Bottom half
            {0.50f, 1.00f, 0.00f, 0.00f},  // Left half
            {0.50f, 1.00f, 0.50f, 0.00f},  // Right half
        };

        new android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.float_size_title))
            .setItems(sizes, (d, which) -> {
                float[] p = params[which];
                if (mLog != null) mLog.i(TAG, "Float size picked: " + sizes[which]
                    + " w=" + p[0] + " h=" + p[1] + " l=" + p[2] + " t=" + p[3]
                    + " for " + packageName);
                launchInWindowModeWithPosition(packageName, p[0], p[1], p[2], p[3]);
            })
            .show();
    }

    private void launchInWindowModeWithPosition(String packageName, float wRatio, float hRatio, float leftRatio, float topRatio) {
        enableFreeformSupport();
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) { Toast.makeText(this, "App not found", Toast.LENGTH_SHORT).show(); return; }

        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = (int) (dm.widthPixels * wRatio);
        int h = (int) (dm.heightPixels * hRatio);
        int left = (int) (dm.widthPixels * leftRatio);
        int top = (int) (dm.heightPixels * topRatio);

        if (mLog != null) mLog.i(TAG, "Float launch: " + packageName + " " + w + "x" + h
            + " at " + left + "," + top + " screen=" + dm.widthPixels + "x" + dm.heightPixels);

        // Primary: ActivityOptions with setLaunchBounds + setLaunchWindowingMode
        // This is Taskbar's approach — the only reliable way to set freeform bounds
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            android.app.ActivityOptions opts = android.app.ActivityOptions.makeBasic();
            android.graphics.Rect bounds = new android.graphics.Rect(left, top, left + w, top + h);
            opts.getClass().getMethod("setLaunchBounds", android.graphics.Rect.class).invoke(opts, bounds);
            opts.getClass().getMethod("setLaunchWindowingMode", int.class).invoke(opts, 5);
            startActivity(intent, opts.toBundle());
            if (mLog != null) mLog.i(TAG, "Float OK (ActivityOptions) bounds=" + bounds);
            return;
        } catch (Exception e) {
            if (mLog != null) mLog.d(TAG, "Float ActivityOptions: " + e.getMessage());
        }

        // Fallback: am start --windowingMode 5 (no custom bounds, but at least freeform)
        try {
            android.content.ComponentName cn = intent.resolveActivity(getPackageManager());
            if (cn != null) {
                String cmd = "am start --windowingMode 5 -n " + cn.flattenToShortString();
                Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
                int exit = p.waitFor();
                if (mLog != null) mLog.i(TAG, "Float OK (am cmd) exit=" + exit);
                if (exit == 0) return;
            }
        } catch (Exception e) {
            if (mLog != null) mLog.d(TAG, "Float am start: " + e.getMessage());
        }

        // Last fallback
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void launchInWindowMode(String packageName, int windowingMode) {
        launchInWindowMode(packageName, windowingMode, 0.85f, 0.65f);
    }

    /**
     * Launch app in a specific windowing mode.
     * @param windowingMode 5=freeform (floating), 3=split_screen_primary
     * @param widthRatio window width as fraction of screen (0.0-1.0)
     * @param heightRatio window height as fraction of screen (0.0-1.0)
     */
    private void launchInWindowMode(String packageName, int windowingMode, float widthRatio, float heightRatio) {
        enableFreeformSupport();

        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            Toast.makeText(this, "App not found", Toast.LENGTH_SHORT).show();
            return;
        }
        String modeName = windowingMode == 5 ? "freeform" : windowingMode == 3 ? "split" : "mode" + windowingMode;

        // Primary approach: am start --windowingMode N (most reliable on car head units)
        try {
            android.content.ComponentName cn = intent.resolveActivity(getPackageManager());
            if (cn != null) {
                String cmd = "am start --windowingMode " + windowingMode + " -n " + cn.flattenToShortString();
                Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
                int exit = p.waitFor();
                if (mLog != null) mLog.i(TAG, "am start " + modeName + " " + packageName + " exit=" + exit);
                if (exit == 0) return;
            }
        } catch (Exception e) {
            if (mLog != null) mLog.d(TAG, "am start " + modeName + ": " + e.getMessage());
        }

        // Secondary: ActivityOptions with setLaunchWindowingMode
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            android.app.ActivityOptions opts = android.app.ActivityOptions.makeBasic();

            if (windowingMode == 5) {
                // Freeform: set bounds for window position and size
                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                int w = (int) (dm.widthPixels * widthRatio);
                int h = (int) (dm.heightPixels * heightRatio);
                int left = (dm.widthPixels - w) / 2;
                int top = (dm.heightPixels - h) / 2;
                android.graphics.Rect bounds = new android.graphics.Rect(left, top, left + w, top + h);
                opts.getClass().getMethod("setLaunchBounds", android.graphics.Rect.class).invoke(opts, bounds);
            }

            try {
                opts.getClass().getMethod("setLaunchWindowingMode", int.class).invoke(opts, windowingMode);
            } catch (Exception ignored) {}

            startActivity(intent, opts.toBundle());
            if (mLog != null) mLog.i(TAG, "Launched " + packageName + " " + modeName + " (ActivityOptions)");
            return;
        } catch (Exception e) {
            if (mLog != null) mLog.d(TAG, "ActivityOptions " + modeName + ": " + e.getMessage());
        }

        // Fallback: normal launch
        try {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e2) {
            Toast.makeText(this, "Launch failed", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== Page: Charging ====================

    private com.emegelauncher.widget.ChargingChartView mChargingChart;
    private com.emegelauncher.widget.ArcGaugeView mChargeSocGauge, mChargePowerGauge;
    private TextView mChargeStatus, mChargeTimeRemaining, mChargeEnergy, mChargeRangeGained;
    private TextView mChargePackInfo, mChargeAcInfo, mChargeEfficiency, mChargePlugInfo;
    private TextView mChargeTargetSoc, mChargePeakPower;
    private TextView mChargeConnected, mChargeOptCurrent, mChargeStopReason, mChargeSchedule;
    private boolean mChargeWasCharging = false;

    private View buildChargingPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundResource(ThemeHelper.isDarkMode(this) ? R.drawable.bg_gradient_dark : R.drawable.bg_gradient_light);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(16, 8, 16, 8);

        // Status header
        mChargeStatus = new TextView(this);
        mChargeStatus.setText(getString(R.string.charge_not_charging));
        mChargeStatus.setTextSize(20);
        mChargeStatus.setTextColor(cText);
        mChargeStatus.setGravity(Gravity.CENTER);
        mChargeStatus.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mChargeStatus.setPadding(0, 8, 0, 8);
        page.addView(mChargeStatus);

        // SOC + Power gauges side by side
        LinearLayout gaugeRow = new LinearLayout(this);
        gaugeRow.setOrientation(LinearLayout.HORIZONTAL);
        mChargeSocGauge = newGauge("SOC", "%", 100, ThemeHelper.accentGreen(this));
        mChargePowerGauge = newGauge(getString(R.string.charge_power), "kW", 100, ThemeHelper.accentBlue(this));
        LinearLayout.LayoutParams gaugeLp = new LinearLayout.LayoutParams(0, 240, 1f);
        gaugeLp.setMargins(4, 4, 4, 4);
        gaugeRow.addView(mChargeSocGauge, gaugeLp);
        gaugeRow.addView(mChargePowerGauge, new LinearLayout.LayoutParams(gaugeLp));
        page.addView(gaugeRow);

        // Multi-series chart
        mChargingChart = new com.emegelauncher.widget.ChargingChartView(this);
        mChargingChart.setTextColor(cTextSec);
        mChargingChart.setGridColor(cDivider);
        mChargingChart.setBgColor(cCard);
        mChargingChart.setBackgroundColor(cCard);
        LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(-1, 350);
        chartLp.setMargins(0, 8, 0, 8);
        page.addView(mChargingChart, chartLp);

        // Info cards
        mChargeTimeRemaining = newInfoTv(getString(R.string.charge_time_remaining, "--"));
        page.addView(mChargeTimeRemaining);
        mChargeEnergy = newInfoTv(getString(R.string.charge_energy, "0.0"));
        page.addView(mChargeEnergy);
        mChargeRangeGained = newInfoTv(getString(R.string.charge_range_gained, "0"));
        page.addView(mChargeRangeGained);
        mChargePackInfo = newInfoTv(getString(R.string.charge_pack_info, "--", "--"));
        page.addView(mChargePackInfo);
        mChargeAcInfo = newInfoTv(getString(R.string.charge_ac_input, "--", "--"));
        page.addView(mChargeAcInfo);
        mChargeEfficiency = newInfoTv(getString(R.string.charge_efficiency, "--"));
        page.addView(mChargeEfficiency);
        mChargePlugInfo = newInfoTv(getString(R.string.charge_plug_status, "--"));
        page.addView(mChargePlugInfo);
        mChargeTargetSoc = newInfoTv(getString(R.string.charge_target_soc, "--"));
        page.addView(mChargeTargetSoc);
        mChargePeakPower = newInfoTv(getString(R.string.charge_peak_power, "--"));
        page.addView(mChargePeakPower);
        mChargeConnected = newInfoTv(getString(R.string.charge_cable_status, "--"));
        page.addView(mChargeConnected);
        mChargeOptCurrent = newInfoTv(getString(R.string.charge_bms_opt_current, "--"));
        page.addView(mChargeOptCurrent);
        mChargeStopReason = newInfoTv(getString(R.string.charge_stop_reason, "--"));
        page.addView(mChargeStopReason);
        mChargeSchedule = newInfoTv(getString(R.string.charge_schedule_off));
        page.addView(mChargeSchedule);

        scroll.addView(page);
        return scroll;
    }

    private TextView newInfoTv(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(cText);
        tv.setPadding(8, 6, 8, 6);
        tv.setBackgroundColor(cCard);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 2, 0, 2);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void updateChargingPage() {
        if (mChargeStatus == null) return;
        com.emegelauncher.vehicle.ChargingSessionManager mgr = com.emegelauncher.vehicle.ChargingSessionManager.getInstance(this);
        boolean isCharging = mgr.isCharging();

        mChargeWasCharging = isCharging;

        if (isCharging) {
            String type = mgr.getChargeType() == 2 ? "DC" : "AC";
            mChargeStatus.setText("\u26A1 " + type + " " + getString(R.string.charge_charging));
            mChargeStatus.setTextColor(ThemeHelper.accentGreen(this));

            List<com.emegelauncher.vehicle.ChargingSessionManager.DataPoint> pts = mgr.getCurrentPoints();
            if (!pts.isEmpty()) {
                com.emegelauncher.vehicle.ChargingSessionManager.DataPoint last = pts.get(pts.size() - 1);

                // Gauges
                if (mChargeSocGauge != null) mChargeSocGauge.setValue(last.socDisplay);
                if (mChargePowerGauge != null) {
                    mChargePowerGauge.setValue(Math.abs(last.powerKw));
                    mChargePowerGauge.setMaxValue(Math.max(50, mgr.getPeakPowerKw() * 1.2f));
                }

                // Live chart
                if (mChargingChart != null) mChargingChart.setData(pts);

                // Time remaining
                if (last.timeRemaining > 0) {
                    int h = (int) (last.timeRemaining / 60);
                    int m = (int) (last.timeRemaining % 60);
                    mChargeTimeRemaining.setText(getString(R.string.charge_time_remaining,
                        h > 0 ? h + "h " + m + " min" : m + " min"));
                } else {
                    mChargeTimeRemaining.setText(getString(R.string.charge_time_remaining, "--"));
                }

                // Session energy
                mChargeEnergy.setText(getString(R.string.charge_energy, String.format("%.1f", mgr.getEnergyAccKwh())));

                // Range gained (from start of session)
                float startRange = mgr.getStartRange();
                float rangeGained = startRange > 0 && last.rangeKm > 0 ? last.rangeKm - startRange : 0;
                mChargeRangeGained.setText(getString(R.string.charge_range_gained, String.format("+%.0f", rangeGained)));

                // Pack voltage / current
                mChargePackInfo.setText(getString(R.string.charge_pack_info,
                    String.format("%.0f", last.voltage), String.format("%.1f", last.current)));

                // AC input (properties don't report on Marvel R — hide if 0)
                if (last.acVoltage > 0) {
                    mChargeAcInfo.setText(getString(R.string.charge_ac_input,
                        String.format("%.0f", last.acVoltage), String.format("%.1f", last.acCurrent)));
                    mChargeAcInfo.setVisibility(View.VISIBLE);
                } else {
                    mChargeAcInfo.setVisibility(View.GONE);
                }
                // Efficiency not available on Marvel R (AC input sensors don't report)
                mChargeEfficiency.setVisibility(View.GONE);

                // Plug info — show charge type
                mChargePlugInfo.setText(getString(R.string.charge_plug_status,
                    (mgr.getChargeType() == 2 ? "DC" : "AC") + " " + getString(R.string.charge_connected)));

                // Target SOC
                String targetRaw = mVehicle.getPropertyValue(YFVehicleProperty.CHRG_TRGT_SOC);
                String targetStr = "--";
                if (targetRaw != null && !targetRaw.equals("N/A")) {
                    try {
                        int raw = (int) Float.parseFloat(targetRaw);
                        targetStr = ((raw + 3) * 10) + "%"; // 5=80%, 6=90%, 7=100%
                    } catch (Exception ignored) {}
                }
                mChargeTargetSoc.setText(getString(R.string.charge_target_soc, targetStr));

                // Peak power
                mChargePeakPower.setText(getString(R.string.charge_peak_power, String.format("%.1f kW", mgr.getPeakPowerKw())));

                // BMS optimal current limit
                String optCrnt = mVehicle.getPropertyValue(YFVehicleProperty.BMS_CHRG_OPT_CRNT);
                if (optCrnt != null && !optCrnt.equals("N/A") && !optCrnt.equals("0")) {
                    mChargeOptCurrent.setText(getString(R.string.charge_bms_opt_current, optCrnt + " A"));
                } else {
                    mChargeOptCurrent.setText(getString(R.string.charge_bms_opt_current, "--"));
                }

                // Charge stop reason (useful even mid-session to see last reason)
                String stopReason = mVehicle.getPropertyValue(YFVehicleProperty.BMS_CHRG_SP_RSN);
                if (stopReason != null && !stopReason.equals("N/A") && !stopReason.equals("0")) {
                    mChargeStopReason.setText(getString(R.string.charge_stop_reason, stopReason));
                } else {
                    mChargeStopReason.setText(getString(R.string.charge_stop_reason, "--"));
                }

                // Duration in status
                long durMs = mgr.getSessionDuration();
                int durMin = (int) (durMs / 60000);
                if (durMin > 0) {
                    String durStr = durMin > 60 ? (durMin / 60) + "h " + (durMin % 60) + "min" : durMin + " min";
                    mChargeStatus.setText("\u26A1 " + type + " " + getString(R.string.charge_charging) + " | " + durStr);
                }
            }
        } else {
            mChargeStatus.setText(getString(R.string.charge_not_charging));
            mChargeStatus.setTextColor(cTextSec);

            // Show stop reason when not charging
            String stopReason = mVehicle.getPropertyValue(YFVehicleProperty.BMS_CHRG_SP_RSN);
            if (stopReason != null && !stopReason.equals("N/A") && !stopReason.equals("0")) {
                mChargeStopReason.setText(getString(R.string.charge_stop_reason, stopReason));
            }
        }

        // Cable connection + charging type
        boolean plugConnected = false;
        try { plugConnected = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_CHRG_PLUG_CNCTNIO)) > 0; } catch (Exception ignored) {}
        String cableStr;
        if (plugConnected && isCharging) {
            cableStr = getString(R.string.charge_charging_by, mgr.getChargeType() == 2 ? "DC" : "AC");
        } else if (plugConnected) {
            cableStr = getString(R.string.charge_cable_connected);
        } else {
            cableStr = getString(R.string.charge_cable_none);
        }
        mChargeConnected.setText(getString(R.string.charge_cable_status, cableStr));

        // Scheduled charging (always show)
        try {
            int reserCtrl = (int) parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.RESER_CTRL));
            if (reserCtrl == 1) { // 1=ON, 2=OFF
                int stH = (int) parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.RESER_ST_HOUR));
                int stM = (int) parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.RESER_ST_MIN));
                int spH = (int) parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.RESER_SP_HOUR));
                int spM = (int) parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.RESER_SP_MIN));
                mChargeSchedule.setText(getString(R.string.charge_schedule_active,
                    String.format("%02d:%02d \u2192 %02d:%02d", stH, stM, spH, spM)));
            } else {
                mChargeSchedule.setText(getString(R.string.charge_schedule_off));
            }
        } catch (Exception ignored) {}
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
                case 0: modeName = getString(R.string.graph_drive_eco); break;
                case 2: modeName = getString(R.string.graph_drive_sport); break;
                case 6: modeName = getString(R.string.graph_drive_winter); break;
                default: modeName = getString(R.string.graph_drive_normal);
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

    }

    private void updateDynamicCards() {
        mVehicle.pollMediaState(); // Polls radio, music, nav, and RemoteUI state
        updateRadioCard();
        updateMusicCard();
        updateNavCard();
        updatePhoneCard();
    }

    /** Update radio card — always shows radio data regardless of active source */
    private void updateRadioCard() {
        if (mRadioTitle == null) return;
        String title = mVehicle.getRadioTitle();
        String subtitle = mVehicle.getRadioSubtitle();
        if (title != null && !title.isEmpty()) {
            mRadioTitle.setText(title);
        } else {
            mRadioTitle.setText(getString(R.string.radio_off));
        }
        mRadioSubtitle.setText(subtitle != null ? subtitle : "");
        // Update play/pause icon
        if (mRadioPlayPause != null) {
            mRadioPlayPause.setImageResource(mVehicle.isRadioManualPlaying()
                ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        }
    }

    /**
     * Update music card.
     * Primary: SAIC MediaPlayControlManager SDK (IAudioStatusListener callbacks) — same as original launcher.
     * Fallback: Android MediaSession (works for BT audio).
     */
    private int mMusicCardLogCount = 0;

    private void updateMusicCard() {
        if (mMusicTitle == null) return;
        try {
            String title = null;
            String artist = null;
            android.graphics.Bitmap art = null;
            boolean playing = false;
            long posMs = 0;
            String source = "none";

            // ALWAYS get MediaSession controller for transport controls (play/pause/next/prev)
            // This is the exact approach from the last working committed version (bf7007f)
            android.media.session.MediaSessionManager msm =
                (android.media.session.MediaSessionManager) getSystemService(android.content.Context.MEDIA_SESSION_SERVICE);
            java.util.List<android.media.session.MediaController> controllers = msm.getActiveSessions(null);
            if (controllers != null && !controllers.isEmpty()) {
                mMediaController = controllers.get(0);
                android.media.session.PlaybackState state = mMediaController.getPlaybackState();
                playing = state != null && state.getState() == android.media.session.PlaybackState.STATE_PLAYING;
                android.media.MediaMetadata meta = mMediaController.getMetadata();
                if (meta != null) {
                    title = meta.getString(android.media.MediaMetadata.METADATA_KEY_TITLE);
                    artist = meta.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST);
                    art = meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);
                    if (art == null) art = meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART);
                }
                if (state != null) posMs = state.getPosition();
                source = "MediaSession(" + mMediaController.getPackageName() + ")";
            } else {
                mMediaController = null;
            }

            // Update UI
            if (title != null && !title.isEmpty()) {
                mMusicTitle.setText(title);
                mMusicSubtitle.setText(artist != null ? artist : "");
                if (art != null) {
                    mMusicArt.setImageBitmap(art);
                    mMusicArt.setAlpha(0.2f);
                    mMusicArt.setVisibility(View.VISIBLE);
                } else {
                    mMusicArt.setVisibility(View.GONE);
                }
                mMusicPlayPause.setImageResource(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
                if (posMs > 0) {
                    long pos = posMs / 1000;
                    mMusicTime.setText(String.format("%d:%02d", pos / 60, pos % 60));
                } else {
                    mMusicTime.setText("");
                }
            } else {
                mMusicTitle.setText(getString(R.string.now_playing_idle));
                mMusicSubtitle.setText("");
                mMusicArt.setVisibility(View.GONE);
                mMusicPlayPause.setImageResource(android.R.drawable.ic_media_play);
                mMusicTime.setText("");
            }

            // Log periodically
            if (mMusicCardLogCount++ % 10 == 0 && mLog != null) {
                mLog.d(TAG, "MusicCard: src=" + source + " title=" + title + " artist=" + artist
                    + " art=" + (art != null) + " playing=" + playing + " pos=" + posMs);
            }
        } catch (Exception e) {
            Log.d(TAG, "Music card update: " + e.getMessage());
        }
    }

    // Navigation data now comes from IGeneralNotificationListener callbacks registered
    // in VehicleServiceManager.registerNavListener().
    // Radio data from IRadioListener callback + pollRadioState.
    // Music data from Android MediaSession (polling).

    // Dummy line to close old block
    /** Map Telenav guide icon index (0-39) to our drawable resource (copied from SAIC launcher) */
    private int getNavIconResource(int index) {
        // Icons 9-16 have left/right variants; use left (standard for right-hand traffic)
        switch (index) {
            case 0: return R.drawable.guide_info_icon_0;
            case 1: return R.drawable.guide_info_icon_1;
            case 2: return R.drawable.guide_info_icon_2;
            case 3: return R.drawable.guide_info_icon_3;
            case 4: return R.drawable.guide_info_icon_4;
            case 5: return R.drawable.guide_info_icon_5;
            case 6: return R.drawable.guide_info_icon_6;
            case 7: return R.drawable.guide_info_icon_7;
            case 8: return R.drawable.guide_info_icon_8;
            case 9: return R.drawable.guide_info_icon_9_left;
            case 10: return R.drawable.guide_info_icon_10_left;
            case 11: return R.drawable.guide_info_icon_11_left;
            case 12: return R.drawable.guide_info_icon_12_left;
            case 13: return R.drawable.guide_info_icon_13_left;
            case 14: return R.drawable.guide_info_icon_14_left;
            case 15: return R.drawable.guide_info_icon_15_left;
            case 16: return R.drawable.guide_info_icon_16_left;
            case 17: return R.drawable.guide_info_icon_17;
            case 18: return R.drawable.guide_info_icon_18;
            case 19: return R.drawable.guide_info_icon_19;
            case 20: return R.drawable.guide_info_icon_20;
            case 21: return R.drawable.guide_info_icon_21;
            case 22: return R.drawable.guide_info_icon_22;
            case 23: return R.drawable.guide_info_icon_23;
            case 24: return R.drawable.guide_info_icon_24;
            case 25: return R.drawable.guide_info_icon_25;
            case 26: return R.drawable.guide_info_icon_26;
            case 27: return R.drawable.guide_info_icon_27;
            case 28: return R.drawable.guide_info_icon_28;
            case 29: return R.drawable.guide_info_icon_29;
            case 30: return R.drawable.guide_info_icon_30;
            case 31: return R.drawable.guide_info_icon_31;
            case 32: return R.drawable.guide_info_icon_32;
            case 33: return R.drawable.guide_info_icon_33;
            case 34: return R.drawable.guide_info_icon_34;
            case 35: return R.drawable.guide_info_icon_35;
            case 36: return R.drawable.guide_info_icon_36;
            case 37: return R.drawable.guide_info_icon_37;
            case 38: return R.drawable.guide_info_icon_38;
            case 39: return R.drawable.guide_info_icon_39;
            default: return R.drawable.ic_map; // fallback
        }
    }

    // Navigation data comes from IGeneralNotificationListener + polling.
    // Radio data from IRadioListener callback + pollRadioState.
    // Music data from Android MediaSession (polling).

    // Navigation data now comes from IGeneralNotificationListener callbacks registered
    // in VehicleServiceManager.registerNavListener() — no broadcast receiver needed.

    private int mNavCardLogCount = 0;

    private void updateNavCard() {
        if (mNavActiveInfo == null) return;
        try {
            boolean isNav = mVehicle.isNavigating();

            if (isNav) {
                mNavActiveInfo.setVisibility(View.VISIBLE);
                mNavQuickBtns.setVisibility(View.GONE);

                int iconRes = getNavIconResource(mVehicle.getNavIconIndex());
                mNavTurnArrow.setImageResource(iconRes);

                String distStr = mVehicle.getNavDistanceStr();
                mNavDist.setText(distStr.isEmpty() ? getString(R.string.navigation) : distStr);

                String road = mVehicle.getNavRoadName();
                mNavRoad.setText(road != null && !road.equals("N/A") ? road : "");

                String remaining = mVehicle.getNavRemainingStr();
                mNavRemaining.setText(remaining);

                int sl = mVehicle.getNavSpeedLimit();
                if (sl == 0) {
                    float navSpd = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.NAVIGATION_SPEED_LIMIT_VALUE));
                    if (navSpd > 0) sl = (int) navSpd;
                }
                if (sl > 0) {
                    mNavSpeedLimit.setText(String.valueOf(sl));
                    mNavSpeedLimit.setVisibility(View.VISIBLE);
                } else {
                    mNavSpeedLimit.setVisibility(View.GONE);
                }

                // Log nav card content every 5 cycles
                if (mNavCardLogCount++ % 5 == 0 && mLog != null) {
                    mLog.d(TAG, "NavCard SHOW: icon=" + mVehicle.getNavIconIndex() + " dist=" + distStr
                        + " road=" + road + " remain=" + remaining + " speed=" + sl);
                }
            } else {
                mNavActiveInfo.setVisibility(View.GONE);
                mNavQuickBtns.setVisibility(View.VISIBLE);
                if (mNavCardLogCount++ % 15 == 0 && mLog != null) {
                    mLog.d(TAG, "NavCard IDLE (not navigating)");
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Nav card update: " + e.getMessage());
        }
    }

    private boolean mPhoneCallPulse = false;
    private int mPhoneCallLogTimer = 0;

    private void updatePhoneCard() {
        if (mPhoneDevice == null) return;
        try {
            // Get connected BT device name via Android BluetoothAdapter
            String deviceName = null;
            try {
                android.bluetooth.BluetoothAdapter btAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                if (btAdapter != null && btAdapter.isEnabled()) {
                    for (android.bluetooth.BluetoothDevice dev : btAdapter.getBondedDevices()) {
                        // Check if device is connected (profile state)
                        if (dev.getBondState() == android.bluetooth.BluetoothDevice.BOND_BONDED) {
                            String name = dev.getName();
                            if (name != null && !name.isEmpty()) {
                                deviceName = name;
                                break; // take first bonded device with a name
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            if (deviceName != null) {
                mPhoneDevice.setText(getString(R.string.phone_connected, deviceName));
                mPhoneDevice.setTextColor(cText);
            } else {
                mPhoneDevice.setText(getString(R.string.phone_no_device));
                mPhoneDevice.setTextColor(cTextSec);
            }
            // Call status
            String callSts = mVehicle.getPropertyValue(YFVehicleProperty.FICM_CALL_STS);
            String charger = mVehicle.getPropertyValue(YFVehicleProperty.PHONE_WIRELESS_CHAEGER_WORKING_STATE);
            StringBuilder status = new StringBuilder();
            boolean inCall = false;
            if ("1".equals(callSts)) {
                status.append(getString(R.string.phone_call_ringing));
                mPhoneStatus.setTextColor(ThemeHelper.accentGreen(this));
                inCall = true;
            } else if ("2".equals(callSts)) {
                status.append(getString(R.string.phone_call_active));
                mPhoneStatus.setTextColor(ThemeHelper.accentGreen(this));
                inCall = true;
            } else {
                mPhoneStatus.setTextColor(cTextSec);
            }
            if ("1".equals(charger)) {
                if (status.length() > 0) status.append(" | ");
                status.append("\u26A1 " + getString(R.string.phone_charger_active));
            }
            mPhoneStatus.setText(status.toString());
            // Pulsing green border on call
            if (inCall && mPhoneCard != null) {
                mPhoneCallPulse = !mPhoneCallPulse;
                android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
                border.setCornerRadius(12);
                border.setColor(cCard);
                border.setStroke(mPhoneCallPulse ? 4 : 2, ThemeHelper.accentGreen(this));
                mPhoneCard.setBackground(border);
            } else if (mPhoneCard != null) {
                mPhoneCard.setBackgroundResource(R.drawable.card_bg_ripple);
            }
            // Refresh call log every 30 seconds
            mPhoneCallLogTimer++;
            if (mPhoneCallLogTimer >= 60 || mPhoneCallLogTimer == 1) {
                mPhoneCallLogTimer = 1;
                loadRecentCalls();
            }
        } catch (Exception e) {
            Log.d(TAG, "Phone card update: " + e.getMessage());
        }
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
        // Instant consumption calculated from power/speed (independent of VHAL)
        float consumption = speed > 1 ? powerKw / speed * 100f : 0;
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
            mGpRpm.setLabel(String.format("%.1f inst", consumption));
            mGpRpm.setLabelColor(ThemeHelper.accentOrange(this));
            mGpRpm.setLabel2(String.format("%.1f avg", displayAvg), ThemeHelper.accentTeal(this));
        }

        // Eco Score — session aggregate with live behavior indicator
        // Only update eco when car is moving — freeze when stopped
        float ecoScore = mEcoScoreAvg;
        if (speed > 3) {
            float ecoInstant = 100f;
            float consRef = displayAvg > 0.1f ? displayAvg : consumption;
            if (consRef > 14) ecoInstant -= (consRef - 14) * 2.5f;
            if (powerKw > 30) ecoInstant -= (powerKw - 30) * 1.0f;
            if (powerKw < -5) ecoInstant += Math.min(10, Math.abs(powerKw + 5) * 0.5f);
            if (speed > 110) ecoInstant -= (speed - 110) * 1.5f;
            ecoInstant = Math.max(0, Math.min(100, ecoInstant));
            if (!mEcoScoreInit) { mEcoScoreAvg = ecoInstant; mEcoScoreInit = true; }
            else { mEcoScoreAvg = mEcoScoreAvg * 0.97f + ecoInstant * 0.03f; }
            ecoScore = Math.max(0, Math.min(100, mEcoScoreAvg));
        }
        if (mGpEcon != null) {
            mGpEcon.setValue(ecoScore);
            // Driving behavior indicator: colored arrows (shows live behavior)
            String indicator;
            int indicatorColor;
            if (speed <= 3) {
                indicator = getString(R.string.eco_steady);
                indicatorColor = 0xFF636366;
            } else if (powerKw > 50) {
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
            switch (dm) { case 0: mode = getString(R.string.graph_drive_eco); break; case 2: mode = getString(R.string.graph_drive_sport); break; case 6: mode = getString(R.string.graph_drive_winter); break; default: mode = getString(R.string.graph_drive_normal); }
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

    private int mPollTick = 0;

    private void startPolling() {
        mHandler.postDelayed(new Runnable() {
            @Override public void run() {
                mPollTick++;
                // Every 1s: core UI + graphs dashboard
                updateUI();
                updateGraphsDashboard();
                // Every 2s: dynamic cards (music/radio/nav/phone)
                if (mPollTick % 2 == 0) updateDynamicCards();
                // Every 5s: ABRP, trip recorder, charging
                if (mPollTick % 5 == 0) {
                    feedTripRecorder();
                    feedAbrp();
                    com.emegelauncher.vehicle.ChargingSessionManager.getInstance(MainActivity.this).update(mVehicle);
                    updateChargingPage();
                }
                // Every 10s: cloud check
                if (mPollTick % 10 == 0) checkTboxAndCloud();
                // Every 30s: theme check
                if (mPollTick % 30 == 0) {
                    if (ThemeHelper.hasCarThemeChanged(MainActivity.this)) applyThemeInPlace();
                }
                // Every 120s: weather (also on first tick)
                if (mPollTick == 1 || mPollTick % 120 == 0) mWeather.poll(MainActivity.this);
                mHandler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private volatile boolean mInternetCheckRunning = false;

    /** Trigger cloud query once TBox is online and internet is confirmed */
    private void checkTboxAndCloud() {
        if (mCloudQueried || mInternetCheckRunning) return;
        float tboxAvail = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.SENSOR_TBOXAVLBLY));
        if (tboxAvail <= 0) return;
        // TBox available — verify actual internet connectivity on a background thread
        mInternetCheckRunning = true;
        new Thread(() -> {
            boolean online = false;
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL("https://clients3.google.com/generate_204").openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setUseCaches(false);
                int code = conn.getResponseCode();
                conn.disconnect();
                online = (code == 204 || code == 200);
            } catch (Exception ignored) {}
            final com.emegelauncher.vehicle.FileLogger fl = com.emegelauncher.vehicle.FileLogger.getInstance(MainActivity.this);
            if (online) {
                fl.i(TAG, "Internet check OK, starting cloud query");
                mHandler.post(() -> {
                    mCloudQueried = true;
                    queryCloudOnce();
                });
            } else {
                fl.d(TAG, "Internet check FAILED (generate_204)");
            }
            mInternetCheckRunning = false;
        }).start();
    }

    /** Feed data to trip recorder if recording (runs on every poll cycle regardless of active screen) */
    private void feedTripRecorder() {
        com.emegelauncher.vehicle.TripRecorder rec = com.emegelauncher.vehicle.TripRecorder.getInstance(this);
        if (!rec.isRecording()) return;
        try {
            // Primary: GNSS bean from TBox (has altitude)
            double lat = 0, lon = 0;
            float alt = 0;
            String gnssBean = mVehicle.callSaicMethod("enghardware", "getGNSSInfoBean");
            if (gnssBean != null && !gnssBean.equals("N/A")) {
                try {
                    for (String line : gnssBean.split("\n")) {
                        String l = line.trim();
                        int ci = l.indexOf(':');
                        if (ci < 0) continue;
                        String name = l.substring(0, ci).trim();
                        String val = l.substring(ci + 1).trim();
                        if ("mLatitude".equals(name)) lat = Double.parseDouble(val);
                        else if ("mLongitude".equals(name)) lon = Double.parseDouble(val);
                        else if ("mAltitude".equals(name)) alt = Float.parseFloat(val);
                    }
                } catch (Exception ignored) {}
            }
            // Fallback: SAIC nav service
            if (lat == 0 && lon == 0) {
                String locJson = mVehicle.callSaicMethod("adaptervoice", "getCurLocationDesc");
                if (locJson != null && locJson.startsWith("{")) {
                    org.json.JSONObject loc = new org.json.JSONObject(locJson);
                    lat = loc.optDouble("lat", 0);
                    lon = loc.optDouble("lon", 0);
                }
            }
            float speed = parseFloat(mVehicle.callSaicMethod("condition", "getCarSpeed"));
            if (speed == 0) speed = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.PERF_VEHICLE_SPEED));
            float pV = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_VOL));
            float pI = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_CRNT));
            float powerKw = pV * pI / 1000f;
            float soc = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_SOC_DSP));
            float consumption = speed > 1 ? powerKw / speed * 100f : 0;
            rec.addPoint(lat, lon, alt, speed, powerKw, soc, consumption, 0, 0);
        } catch (Exception ignored) {}
    }

    /** Feed telemetry to ABRP if enabled */
    private void feedAbrp() {
        if (mAbrp == null) return;
        if (!mAbrp.isEnabled()) {
            // Log once when first checked
            return;
        }
        try {
            // GPS: primary from GNSS bean (has lat/lon/alt/heading)
            double lat = 0, lon = 0;
            float elevation = 0, heading = 0;
            String gnssBean = mVehicle.callSaicMethod("enghardware", "getGNSSInfoBean");
            if (gnssBean != null && !gnssBean.equals("N/A")) {
                try {
                    for (String line : gnssBean.split("\n")) {
                        String l = line.trim();
                        int ci = l.indexOf(':');
                        if (ci < 0) continue;
                        String name = l.substring(0, ci).trim();
                        String val = l.substring(ci + 1).trim();
                        if ("mLatitude".equals(name)) lat = Double.parseDouble(val);
                        else if ("mLongitude".equals(name)) lon = Double.parseDouble(val);
                        else if ("mAltitude".equals(name)) elevation = Float.parseFloat(val);
                        else if ("mHeading".equals(name)) heading = Float.parseFloat(val);
                    }
                } catch (Exception e) { Log.d(TAG, "GNSS bean parse: " + e.getMessage()); }
            }
            // Fallback to SAIC nav service for lat/lon
            if (lat == 0 && lon == 0) {
                String locJson = mVehicle.callSaicMethod("adaptervoice", "getCurLocationDesc");
                if (locJson != null && locJson.startsWith("{")) {
                    org.json.JSONObject loc = new org.json.JSONObject(locJson);
                    lat = loc.optDouble("lat", 0);
                    lon = loc.optDouble("lon", 0);
                }
            }
            // Speed
            float speed = parseFloat(mVehicle.callSaicMethod("condition", "getCarSpeed"));
            if (speed == 0) speed = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.PERF_VEHICLE_SPEED));
            // Battery
            float pV = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_VOL));
            float pI = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_CRNT));
            float powerKw = pV * pI / 1000f;
            // Use display SOC (same as driver sees) — SAIC first, then VHAL display fallback
            float soc = parseFloat(mVehicle.callSaicMethod("charging", "getCurrentElectricQuantity"));
            if (soc == 0) soc = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.BMS_PACK_SOC_DSP));
            // Temps
            float extTemp = parseFloat(mVehicle.getOutsideTemp());
            float battTemp = 0; // BMS temp not exposed via VHAL on Marvel R
            String cabinTempStr = mCloud.getInteriorTempStr();
            float cabinTemp = cabinTempStr != null ? parseFloat(cabinTempStr) : 0;
            // Range + odometer
            float estRange = parseFloat(mVehicle.callSaicMethod("charging", "getCurrentEnduranceMileage"));
            if (estRange == 0) estRange = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.CLSTR_ELEC_RNG));
            float odometer = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.SENSOR_TOTAL_MILEAGE));
            // SOH (from BatteryHealthTracker or 0 if unavailable)
            float soh = 0;
            // Charging / parked
            int gearVal = (int) parseFloat(mVehicle.callSaicMethod("condition", "getCarGear"));
            boolean isParked = gearVal == 1; // 1=P
            String chrgSts = mVehicle.getPropertyValue(YFVehicleProperty.BMS_CHRG_STS);
            boolean isCharging = "1".equals(chrgSts) || "2".equals(chrgSts); // 1=AC, 2=DC
            boolean isDcfc = "2".equals(chrgSts); // DC fast charging
            // Tire pressures (raw values are in kPa, ABRP expects kPa)
            float tpFl = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.SENSOR_TIRE_PRESURE_FL));
            float tpFr = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.SENSOR_TIRE_PRESURE_FR));
            float tpRl = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.SENSOR_TIRE_PRESURE_RL));
            float tpRr = parseFloat(mVehicle.getPropertyValue(YFVehicleProperty.SENSOR_TIRE_PRESURE_RR));

            mAbrp.updateTelemetry(lat, lon, speed, soc, powerKw, pV, pI,
                extTemp, battTemp, cabinTemp, elevation, heading, odometer,
                estRange, soh, isCharging, isDcfc, isParked, tpFl, tpFr, tpRl, tpRr);
            mAbrp.trySend();
        } catch (Exception e) {
            Log.e(TAG, "ABRP feed error: " + e.getMessage(), e);
        }
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
        if (mLastDarkMode != ThemeHelper.isDarkMode(this)) applyThemeInPlace();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        mWeather.unregister(this);
        mVehicle.unbindService();
        if (mPackageReceiver != null) {
            try { unregisterReceiver(mPackageReceiver); } catch (Exception ignored) {}
        }
    }

    private void resolveColors() {
        cBg = ThemeHelper.resolveColor(this, R.attr.colorBgPrimary);
        cCard = ThemeHelper.resolveColor(this, R.attr.colorBgCard);
        cText = ThemeHelper.resolveColor(this, R.attr.colorTextPrimary);
        cTextSec = ThemeHelper.resolveColor(this, R.attr.colorTextSecondary);
        cTextTert = ThemeHelper.resolveColor(this, R.attr.colorTextTertiary);
        cDivider = ThemeHelper.resolveColor(this, R.attr.colorDivider);
    }

    /**
     * Apply theme change without recreating the Activity.
     * Preserves all accumulated state (eco score, avg consumption, G-meter peaks, etc.)
     * by only rebuilding the UI views while keeping instance variables.
     */
    private void applyThemeInPlace() {
        boolean newDark = ThemeHelper.isDarkMode(this);
        if (newDark == mLastDarkMode) return;
        mLastDarkMode = newDark;
        if (mLog != null) mLog.i(TAG, "Theme changed to " + (newDark ? "dark" : "light") + " (in-place)");

        // Re-apply theme to Activity context so resolveColor uses new values
        ThemeHelper.applyTheme(this);
        resolveColors();

        // Save current page
        int currentPage = mPager != null ? mPager.getCurrentItem() : PAGE_MAIN;

        // Rebuild the entire content view with new colors — all state preserved
        if (mLog != null) mLog.i(TAG, "Theme rebuild: page=" + currentPage
            + " ecoScore=" + mEcoScoreAvg + " dark=" + newDark);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(newDark ? R.drawable.bg_gradient_dark : R.drawable.bg_gradient_light);

        mPager = new ViewPager(this);
        mPager.setAdapter(new HomePagerAdapter());
        mPager.setCurrentItem(currentPage);
        mPager.setOffscreenPageLimit(4);
        root.addView(mPager, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(buildPageIndicator(), new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        // Polling continues — handler and all accumulated state are untouched
    }
}

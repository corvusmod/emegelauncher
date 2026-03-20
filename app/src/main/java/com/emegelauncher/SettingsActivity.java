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
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.emegelauncher.vehicle.VehicleServiceManager;
import com.emegelauncher.vehicle.YFVehicleProperty;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends Activity {
    private static final String TAG = "SettingsActivity";
    private static final String MY_PACKAGE = "com.emegelauncher";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Theme selector: Auto (follow car) / Dark / Light
        TextView themeLabel = findViewById(R.id.theme_current);
        updateThemeLabel(themeLabel);
        findViewById(R.id.row_theme).setOnClickListener(v -> {
            String[] options = {getString(R.string.theme_auto), getString(R.string.theme_dark), getString(R.string.theme_light)};
            new AlertDialog.Builder(this)
                .setTitle("Theme")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: ThemeHelper.setThemeMode(this, ThemeHelper.MODE_AUTO); break;
                        case 1: ThemeHelper.setThemeMode(this, ThemeHelper.MODE_DARK); break;
                        case 2: ThemeHelper.setThemeMode(this, ThemeHelper.MODE_LIGHT); break;
                    }
                    recreate();
                }).show();
        });

        // Default launcher
        updateLauncherStatus();
        findViewById(R.id.btn_set_default).setOnClickListener(v -> setAsDefaultLauncher());
        findViewById(R.id.btn_restore_default).setOnClickListener(v -> restoreOriginalLauncher());

        // Donation section
        addDonationSection();

        // Save logs
        findViewById(R.id.row_save_logs).setOnClickListener(v -> showStorageSelectionDialog());

        // Overlay toggle
        addOverlayToggle();

        // Cloud API (iSMART)
        addCloudSection();

        // Driver profile (drive mode + regen level)
        addDriverProfile();

        // ADB Debug toggle (via EngMode ISystemSettingsManager)
        addAdbToggle();

        // Key Capture mode
        addKeyCaptureButton();

        // TX Code viewer
        addTxCodeViewer();

        // About section
        addAboutSection();
    }

    // ==================== Cloud API ====================

    private com.emegelauncher.vehicle.SaicCloudManager mCloud;

    private void addCloudSection() {
        mCloud = new com.emegelauncher.vehicle.SaicCloudManager(this);
        LinearLayout parent = (LinearLayout) findViewById(R.id.row_save_logs).getParent();

        TextView header = new TextView(this);
        header.setText(getString(R.string.cloud_section));
        header.setTextSize(12);
        header.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextTertiary));
        header.setPadding(4, 40, 0, 8);
        parent.addView(header);

        // Status + login/logout row
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(R.drawable.card_bg_selector);
        row.setPadding(20, 12, 20, 12);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView label = new TextView(this);
        label.setText(getString(R.string.cloud_login));
        label.setTextSize(16);
        label.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextPrimary));
        textCol.addView(label);

        TextView desc = new TextView(this);
        desc.setText(getString(R.string.cloud_login_desc));
        desc.setTextSize(11);
        desc.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextTertiary));
        textCol.addView(desc);

        TextView statusLabel = new TextView(this);
        statusLabel.setTag("cloud_status");
        statusLabel.setTextSize(12);
        statusLabel.setTextColor(ThemeHelper.accentGreen(this));
        statusLabel.setText(mCloud.isLoggedIn() ? getString(R.string.cloud_logged_in) : getString(R.string.cloud_not_logged));
        textCol.addView(statusLabel);
        row.addView(textCol);

        // Login/Logout button
        TextView loginBtn = new TextView(this);
        loginBtn.setText(mCloud.isLoggedIn() ? getString(R.string.cloud_logout) : "LOGIN");
        loginBtn.setTextSize(13);
        loginBtn.setTextColor(ThemeHelper.accentBlue(this));
        loginBtn.setPadding(16, 8, 16, 8);
        loginBtn.setTag("cloud_login_btn");
        loginBtn.setOnClickListener(v -> {
            if (mCloud.isLoggedIn()) {
                mCloud.logout();
                updateCloudUI(parent);
                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            } else {
                showCloudLoginDialog(parent);
            }
        });
        row.addView(loginBtn);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 2, 0, 2);
        parent.addView(row, lp);

        // Refresh button + cloud data row
        LinearLayout dataRow = new LinearLayout(this);
        dataRow.setOrientation(LinearLayout.HORIZONTAL);
        dataRow.setBackgroundResource(R.drawable.card_bg_selector);
        dataRow.setPadding(20, 12, 20, 12);
        dataRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView cloudData = new TextView(this);
        cloudData.setTag("cloud_data");
        cloudData.setTextSize(12);
        cloudData.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextSecondary));
        cloudData.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        updateCloudDataLabel(cloudData);
        dataRow.addView(cloudData);

        TextView refreshBtn = new TextView(this);
        refreshBtn.setText(getString(R.string.cloud_refresh));
        refreshBtn.setTextSize(13);
        refreshBtn.setTextColor(ThemeHelper.accentTeal(this));
        refreshBtn.setPadding(16, 8, 16, 8);
        refreshBtn.setOnClickListener(v -> {
            if (!mCloud.isLoggedIn()) {
                Toast.makeText(this, getString(R.string.cloud_not_logged), Toast.LENGTH_SHORT).show();
                return;
            }
            refreshBtn.setText("...");
            mCloud.queryVehicleStatus((ok, msg) -> {
                refreshBtn.setText(getString(R.string.cloud_refresh));
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                updateCloudDataLabel((TextView) parent.findViewWithTag("cloud_data"));
            });
        });
        dataRow.addView(refreshBtn);

        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.setMargins(0, 2, 0, 2);
        parent.addView(dataRow, dlp);
    }

    private void showCloudLoginDialog(LinearLayout parent) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 10);

        android.widget.EditText userInput = new android.widget.EditText(this);
        userInput.setHint(getString(R.string.cloud_username));
        userInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        layout.addView(userInput);

        android.widget.EditText passInput = new android.widget.EditText(this);
        passInput.setHint(getString(R.string.cloud_password));
        passInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passInput);

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.cloud_login))
            .setView(layout)
            .setPositiveButton("LOGIN", (d, w) -> {
                String user = userInput.getText().toString();
                String pass = passInput.getText().toString();
                if (user.isEmpty() || pass.isEmpty()) return;
                Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show();
                mCloud.login(user, pass, (ok, msg) -> {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    updateCloudUI(parent);
                    if (ok) {
                        mCloud.queryVehicleStatus((ok2, msg2) -> {
                            Toast.makeText(this, msg2, Toast.LENGTH_SHORT).show();
                            updateCloudDataLabel((TextView) parent.findViewWithTag("cloud_data"));
                        });
                    }
                });
            })
            .setNegativeButton(getString(R.string.cancel), null)
            .show();
    }

    private void updateCloudUI(LinearLayout parent) {
        TextView status = parent.findViewWithTag("cloud_status");
        if (status != null) status.setText(mCloud.isLoggedIn() ? getString(R.string.cloud_logged_in) : getString(R.string.cloud_not_logged));
        TextView btn = parent.findViewWithTag("cloud_login_btn");
        if (btn != null) btn.setText(mCloud.isLoggedIn() ? getString(R.string.cloud_logout) : "LOGIN");
    }

    private void updateCloudDataLabel(TextView tv) {
        if (tv == null) return;
        if (!mCloud.hasData()) {
            tv.setText(mCloud.isLoggedIn() ? "No data yet — tap Refresh" : "Login to access cloud data");
            return;
        }
        StringBuilder sb = new StringBuilder();
        String cabinTemp = mCloud.getInteriorTempStr();
        String battV = mCloud.getBatteryVoltageStr();
        if (cabinTemp != null) sb.append("Cabin: ").append(cabinTemp).append("°C");
        if (battV != null) { if (sb.length() > 0) sb.append("  |  "); sb.append("12V: ").append(battV).append("V"); }
        if (mCloud.getMileageOfDay() >= 0) { if (sb.length() > 0) sb.append("\n"); sb.append("Today: ").append(mCloud.getMileageOfDay() / 10.0).append(" km"); }
        if (mCloud.getMileageSinceLastCharge() >= 0) { sb.append("  |  Since charge: ").append(mCloud.getMileageSinceLastCharge() / 10.0).append(" km"); }
        tv.setText(sb.toString());
    }

    // ==================== Driver Profile ====================

    private static final int TX_SET_REGEN_LEVEL = 0xA1;
    // Drive mode TX code unknown for Marvel R — needs testing. DriveHub MG4 uses different codes.
    // For now we only support regen level via transact. Drive mode is read-only display.

    private static String decodeDriveMode(int raw) {
        switch (raw) {
            case 0: return "Eco";
            case 1: return "Normal";
            case 2: return "Sport";
            case 6: return "Winter";
            default: return "Unknown (" + raw + ")";
        }
    }

    private void addDriverProfile() {
        LinearLayout parent = (LinearLayout) findViewById(R.id.row_save_logs).getParent();

        // Section header
        TextView header = new TextView(this);
        header.setText(getString(R.string.driver_profile));
        header.setTextSize(12);
        header.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextTertiary));
        header.setPadding(4, 40, 0, 8);
        parent.addView(header);

        // Status row
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.VERTICAL);
        statusRow.setBackgroundResource(R.drawable.card_bg_selector);
        statusRow.setPadding(20, 12, 20, 12);

        TextView profileDesc = new TextView(this);
        profileDesc.setText(getString(R.string.profile_desc));
        profileDesc.setTextSize(11);
        profileDesc.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextTertiary));
        statusRow.addView(profileDesc);

        // Current values
        VehicleServiceManager vm = VehicleServiceManager.getInstance(this);
        int curDriveMode = readIntProp(vm, YFVehicleProperty.SENSOR_ELECTRIC_DRIVER_MODE);
        int curRegen = readIntProp(vm, YFVehicleProperty.AAD_EPTRGTNLVL);

        TextView currentLabel = new TextView(this);
        currentLabel.setText(String.format(getString(R.string.profile_current),
            decodeDriveMode(curDriveMode), curRegen + 1));
        currentLabel.setTextSize(14);
        currentLabel.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextPrimary));
        currentLabel.setPadding(0, 8, 0, 4);
        statusRow.addView(currentLabel);

        // Saved profile label
        android.content.SharedPreferences prefs = getSharedPreferences("emegelauncher", MODE_PRIVATE);
        int savedDriveMode = prefs.getInt("profile_drive_mode", -1);
        int savedRegen = prefs.getInt("profile_regen_level", -1);

        TextView savedLabel = new TextView(this);
        if (savedDriveMode >= 0) {
            savedLabel.setText(String.format(getString(R.string.profile_saved_label),
                decodeDriveMode(savedDriveMode), savedRegen + 1));
        } else {
            savedLabel.setText(getString(R.string.profile_none));
        }
        savedLabel.setTextSize(13);
        savedLabel.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextSecondary));
        savedLabel.setTag("profile_saved_label");
        statusRow.addView(savedLabel);

        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(0, 2, 0, 2);
        parent.addView(statusRow, slp);

        // Button row
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setBackgroundResource(R.drawable.card_bg_selector);
        btnRow.setPadding(20, 12, 20, 12);
        btnRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Save button
        TextView saveBtn = new TextView(this);
        saveBtn.setText(getString(R.string.save_profile));
        saveBtn.setTextSize(14);
        saveBtn.setTextColor(ThemeHelper.accentBlue(this));
        saveBtn.setPadding(16, 10, 16, 10);
        saveBtn.setOnClickListener(v -> {
            VehicleServiceManager vm2 = VehicleServiceManager.getInstance(this);
            int dm = readIntProp(vm2, YFVehicleProperty.SENSOR_ELECTRIC_DRIVER_MODE);
            int rg = readIntProp(vm2, YFVehicleProperty.AAD_EPTRGTNLVL);
            prefs.edit()
                .putInt("profile_drive_mode", dm)
                .putInt("profile_regen_level", rg)
                .apply();
            Toast.makeText(this, String.format(getString(R.string.profile_saved),
                decodeDriveMode(dm), rg + 1), Toast.LENGTH_SHORT).show();
            // Update saved label
            TextView sl = (TextView) parent.findViewWithTag("profile_saved_label");
            if (sl != null) sl.setText(String.format(getString(R.string.profile_saved_label),
                decodeDriveMode(dm), rg + 1));
        });
        btnRow.addView(saveBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        // Restore button
        TextView restoreBtn = new TextView(this);
        restoreBtn.setText(getString(R.string.restore_profile));
        restoreBtn.setTextSize(14);
        restoreBtn.setTextColor(ThemeHelper.accentGreen(this));
        restoreBtn.setPadding(16, 10, 16, 10);
        restoreBtn.setOnClickListener(v -> restoreProfile());
        btnRow.addView(restoreBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.setMargins(0, 2, 0, 2);
        parent.addView(btnRow, blp);

        // Auto-restore toggle
        LinearLayout autoRow = new LinearLayout(this);
        autoRow.setOrientation(LinearLayout.HORIZONTAL);
        autoRow.setBackgroundResource(R.drawable.card_bg_selector);
        autoRow.setPadding(20, 12, 20, 12);
        autoRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView autoLabel = new TextView(this);
        autoLabel.setText(getString(R.string.auto_restore));
        autoLabel.setTextSize(14);
        autoLabel.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextPrimary));
        autoLabel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        autoRow.addView(autoLabel);

        android.widget.Switch autoToggle = new android.widget.Switch(this);
        autoToggle.setChecked(prefs.getBoolean("profile_auto_restore", false));
        autoToggle.setOnCheckedChangeListener((btn, checked) ->
            prefs.edit().putBoolean("profile_auto_restore", checked).apply());
        autoRow.addView(autoToggle);

        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.setMargins(0, 2, 0, 2);
        parent.addView(autoRow, alp);
    }

    private void restoreProfile() {
        android.content.SharedPreferences prefs = getSharedPreferences("emegelauncher", MODE_PRIVATE);
        int savedRegen = prefs.getInt("profile_regen_level", -1);
        int savedDriveMode = prefs.getInt("profile_drive_mode", -1);
        if (savedRegen < 0) {
            Toast.makeText(this, getString(R.string.profile_none), Toast.LENGTH_SHORT).show();
            return;
        }

        VehicleServiceManager vm = VehicleServiceManager.getInstance(this);
        if (!vm.hasSettingBinder()) {
            Toast.makeText(this, "Setting service not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.restore_profile))
            .setMessage(String.format(getString(R.string.profile_saved_label),
                decodeDriveMode(savedDriveMode), savedRegen + 1)
                + "\n\nRegen level will be set via Binder.transact(0xA1)."
                + "\nDrive mode is read-only (change via physical button)."
                + "\n\nProceed?")
            .setPositiveButton(getString(R.string.confirm), (d, w) -> {
                boolean ok = vm.transactSettingInt(TX_SET_REGEN_LEVEL, savedRegen);
                if (ok) {
                    Toast.makeText(this, String.format(getString(R.string.profile_restored),
                        decodeDriveMode(savedDriveMode), savedRegen + 1), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Regen transact failed — TX code may differ on this firmware",
                        Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton(getString(R.string.cancel), null)
            .show();
    }

    private int readIntProp(VehicleServiceManager vm, int propId) {
        try {
            String v = vm.getPropertyValue(propId);
            if (v != null && !v.equals("N/A")) return (int) Float.parseFloat(v);
        } catch (Exception ignored) {}
        return 0;
    }

    // ==================== UI Toggles ====================

    private void addDonationSection() {
        LinearLayout parent = (LinearLayout) findViewById(R.id.row_save_logs).getParent();

        // Card with QR code and text
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg_ripple);
        card.setPadding(24, 20, 24, 20);
        card.setGravity(android.view.Gravity.CENTER);
        card.setElevation(4f);

        TextView header = new TextView(this);
        header.setText(getString(R.string.donate_title));
        header.setTextSize(13);
        header.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextTertiary));
        header.setGravity(android.view.Gravity.CENTER);
        header.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        card.addView(header);

        TextView desc = new TextView(this);
        desc.setText(getString(R.string.donate_text));
        desc.setTextSize(13);
        desc.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextSecondary));
        desc.setGravity(android.view.Gravity.CENTER);
        desc.setPadding(0, 8, 0, 12);
        card.addView(desc);

        // QR code image
        android.widget.ImageView qr = new android.widget.ImageView(this);
        qr.setImageResource(R.drawable.qr_donate);
        qr.setBackgroundColor(0xFFFFFFFF);
        qr.setPadding(12, 12, 12, 12);
        LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(180, 180);
        qrLp.gravity = android.view.Gravity.CENTER;
        card.addView(qr, qrLp);

        TextView scanText = new TextView(this);
        scanText.setText(getString(R.string.donate_scan));
        scanText.setTextSize(11);
        scanText.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextTertiary));
        scanText.setGravity(android.view.Gravity.CENTER);
        scanText.setPadding(0, 10, 0, 0);
        card.addView(scanText);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 8, 0, 16);
        parent.addView(card, 0, lp); // Add at position 0 (top of the settings list)
    }

    // ==================== UI Toggles ====================

    private void addOverlayToggle() {
        LinearLayout parent = (LinearLayout) findViewById(R.id.row_save_logs).getParent();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(R.drawable.card_bg_selector);
        row.setPadding(20, 16, 20, 16);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView label = new TextView(this);
        label.setText(getString(R.string.overlay_label));
        label.setTextSize(16);
        label.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextPrimary));
        textCol.addView(label);

        TextView desc = new TextView(this);
        desc.setText(getString(R.string.overlay_desc));
        desc.setTextSize(11);
        desc.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextTertiary));
        textCol.addView(desc);
        row.addView(textCol);

        android.widget.Switch toggle = new android.widget.Switch(this);
        boolean enabled = getSharedPreferences("emegelauncher", MODE_PRIVATE)
            .getBoolean("overlay_enabled", true);
        toggle.setChecked(enabled);
        toggle.setOnCheckedChangeListener((btn, checked) -> {
            getSharedPreferences("emegelauncher", MODE_PRIVATE).edit()
                .putBoolean("overlay_enabled", checked).apply();
            if (checked) {
                startService(new Intent(this, OverlayService.class));
            } else {
                stopService(new Intent(this, OverlayService.class));
            }
        });
        row.addView(toggle);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 2, 0, 2);
        parent.addView(row, lp);
    }

    private void addAdbToggle() {
        // Add ADB toggle row dynamically after the export row
        LinearLayout parent = (LinearLayout) findViewById(R.id.row_save_logs).getParent();

        TextView devHeader = new TextView(this);
        devHeader.setText(getString(R.string.developer));
        devHeader.setTextSize(12);
        devHeader.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextTertiary));
        devHeader.setPadding(4, 40, 0, 8);
        parent.addView(devHeader);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(R.drawable.card_bg_selector);
        row.setPadding(20, 16, 20, 16);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView label = new TextView(this);
        label.setText(getString(R.string.adb_debugging));
        label.setTextSize(16);
        label.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextPrimary));
        label.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(label);

        android.widget.Switch toggle = new android.widget.Switch(this);
        String adbStatus = VehicleServiceManager.getInstance(this).callSaicMethod("engmode", "getADBDebugStatus");
        toggle.setChecked("true".equalsIgnoreCase(adbStatus) || "1".equals(adbStatus));
        toggle.setOnCheckedChangeListener((btn, checked) -> {
            new AlertDialog.Builder(this)
                .setTitle("ADB Debug")
                .setMessage((checked ? "Enable" : "Disable") + " ADB debugging?\nThis allows connecting to the head unit via USB/WiFi for development.")
                .setPositiveButton("Yes", (d, w) -> {
                    try {
                        Object svc = null;
                        java.lang.reflect.Field f = VehicleServiceManager.class.getDeclaredField("mEngSystemSettings");
                        f.setAccessible(true);
                        svc = f.get(VehicleServiceManager.getInstance(this));
                        if (svc != null) {
                            java.lang.reflect.Method m = svc.getClass().getMethod("setADBDebug", boolean.class);
                            m.invoke(svc, checked);
                            Toast.makeText(this, "ADB " + (checked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "EngMode not connected", Toast.LENGTH_SHORT).show();
                            btn.setChecked(!checked);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "ADB toggle failed: " + e.getMessage());
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        btn.setChecked(!checked);
                    }
                })
                .setNegativeButton("Cancel", (d, w) -> btn.setChecked(!checked))
                .show();
        });
        row.addView(toggle);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 2, 0, 2);
        parent.addView(row, lp);
    }

    private void addKeyCaptureButton() {
        LinearLayout parent = (LinearLayout) findViewById(R.id.row_save_logs).getParent();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(R.drawable.card_bg_selector);
        row.setPadding(20, 16, 20, 16);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView label = new TextView(this);
        label.setText(getString(R.string.key_capture));
        label.setTextSize(16);
        label.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextPrimary));
        textCol.addView(label);

        TextView desc = new TextView(this);
        desc.setText(getString(R.string.key_capture_desc));
        desc.setTextSize(11);
        desc.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextTertiary));
        textCol.addView(desc);
        row.addView(textCol);

        TextView startBtn = new TextView(this);
        startBtn.setText(getString(R.string.start));
        startBtn.setTextSize(13);
        startBtn.setTextColor(ThemeHelper.accentBlue(this));
        startBtn.setPadding(16, 8, 16, 8);
        startBtn.setOnClickListener(v -> startKeyCapture());
        row.addView(startBtn);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 2, 0, 2);
        parent.addView(row, lp);
    }

    private android.os.Handler mKeyCaptureHandler;
    private boolean mCapturing = false;
    private volatile String mLastKeyEvent = "";

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (mCapturing && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            mLastKeyEvent = "KeyCode " + event.getKeyCode()
                + " (" + android.view.KeyEvent.keyCodeToString(event.getKeyCode()) + ")"
                + " scan=" + event.getScanCode()
                + " device=" + (event.getDevice() != null ? event.getDevice().getName() : "?");
        }
        return super.dispatchKeyEvent(event);
    }

    private void startKeyCapture() {
        if (mCapturing) {
            mCapturing = false;
            Toast.makeText(this, "Key capture stopped", Toast.LENGTH_SHORT).show();
            return;
        }

        mCapturing = true;
        mLastKeyEvent = "";
        mKeyCaptureHandler = new android.os.Handler(android.os.Looper.getMainLooper());

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Key Capture Mode")
            .setMessage("Monitoring VHAL + Android KeyEvents...\nPress any steering wheel button.")
            .setNegativeButton("Stop", (d, w) -> mCapturing = false)
            .setCancelable(false)
            .create();
        // Intercept key events on the dialog too
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                mLastKeyEvent = "KeyCode " + keyCode
                    + " (" + android.view.KeyEvent.keyCodeToString(keyCode) + ")"
                    + " scan=" + event.getScanCode()
                    + " device=" + (event.getDevice() != null ? event.getDevice().getName() : "?");
            }
            return false;
        });
        dialog.show();

        final TextView msgView = dialog.findViewById(android.R.id.message);
        final VehicleServiceManager vm = VehicleServiceManager.getInstance(this);
        // Track previous values to detect changes
        final java.util.Map<String, String> prev = new java.util.HashMap<>();
        final java.util.List<String> history = new java.util.ArrayList<>();

        mKeyCaptureHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!mCapturing) return;
                try {
                    // Read all button-related VHAL properties
                    String[][] props = {
                        {"HW_KEY_INPUT", vm.getPropertyValue(YFVehicleProperty.HW_KEY_INPUT)},
                        {"SWC_FUNCTION", vm.getPropertyValue(YFVehicleProperty.SWC_FUNCTION_CHANGE_SWA)},
                        {"UP", vm.getPropertyValue(YFVehicleProperty.IPK_CLSTR_UP_BTN_STS)},
                        {"DOWN", vm.getPropertyValue(YFVehicleProperty.IPK_CLSTR_DOWN_BTN_STS)},
                        {"LEFT", vm.getPropertyValue(YFVehicleProperty.IPK_CLSTR_LEFT_BTN_STS)},
                        {"RIGHT", vm.getPropertyValue(YFVehicleProperty.IPK_CLSTR_RIGHT_BTN_STS)},
                        {"ENTER", vm.getPropertyValue(YFVehicleProperty.IPK_CLSTR_ENTER_BTN_STS)},
                        {"TAB(✱)", vm.getPropertyValue(YFVehicleProperty.IPK_CLUSTER_TAB_BTN_STS)},
                    };

                    // Detect changes
                    for (String[] p : props) {
                        String old = prev.get(p[0]);
                        if (old != null && !old.equals(p[1])) {
                            String change = p[0] + ": " + old + " → " + p[1];
                            history.add(0, change);
                            if (history.size() > 8) history.remove(history.size() - 1);
                        }
                        prev.put(p[0], p[1]);
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("VHAL Properties (50ms poll):\n");
                    for (String[] p : props) {
                        sb.append("  ").append(p[0]).append(": ").append(p[1]).append("\n");
                    }

                    sb.append("\nAndroid KeyEvent:\n  ").append(
                        mLastKeyEvent.isEmpty() ? "(none yet)" : mLastKeyEvent);

                    if (!history.isEmpty()) {
                        sb.append("\n\nChanges detected:");
                        for (String h : history) sb.append("\n  ").append(h);
                    }

                    if (msgView != null) msgView.setText(sb.toString());
                } catch (Exception e) {
                    if (msgView != null) msgView.setText("Error: " + e.getMessage());
                }
                mKeyCaptureHandler.postDelayed(this, 50); // 20Hz polling
            }
        });
    }

    private void addAboutSection() {
        LinearLayout parent = (LinearLayout) findViewById(R.id.row_save_logs).getParent();

        TextView header = new TextView(this);
        header.setText(getString(R.string.about_section));
        header.setTextSize(12);
        header.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextTertiary));
        header.setPadding(4, 40, 0, 8);
        parent.addView(header);

        // Version info
        LinearLayout versionRow = new LinearLayout(this);
        versionRow.setOrientation(LinearLayout.VERTICAL);
        versionRow.setBackgroundResource(R.drawable.card_bg_selector);
        versionRow.setPadding(20, 14, 20, 14);

        String versionName = "?";
        int versionCode = 0;
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pi.versionName;
            versionCode = pi.versionCode;
        } catch (Exception ignored) {}

        TextView versionLabel = new TextView(this);
        versionLabel.setText("Emegelauncher");
        versionLabel.setTextSize(16);
        versionLabel.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextPrimary));
        versionRow.addView(versionLabel);

        TextView versionValue = new TextView(this);
        versionValue.setText(String.format(getString(R.string.version_label), versionName, versionCode));
        versionValue.setTextSize(13);
        versionValue.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextSecondary));
        versionRow.addView(versionValue);

        TextView target = new TextView(this);
        target.setText("MG Marvel R (EP21) · Android 9 Automotive · API 28");
        target.setTextSize(11);
        target.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextTertiary));
        target.setPadding(0, 4, 0, 0);
        versionRow.addView(target);

        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2);
        vlp.setMargins(0, 2, 0, 2);
        parent.addView(versionRow, vlp);

        // License button
        LinearLayout licenseRow = new LinearLayout(this);
        licenseRow.setBackgroundResource(R.drawable.card_bg_selector);
        licenseRow.setPadding(20, 16, 20, 16);

        TextView licenseBtn = new TextView(this);
        licenseBtn.setText(getString(R.string.licenses));
        licenseBtn.setTextSize(15);
        licenseBtn.setTextColor(ThemeHelper.accentBlue(this));
        licenseBtn.setOnClickListener(v -> showLicenseDialog());
        licenseRow.addView(licenseBtn);

        parent.addView(licenseRow, vlp);
    }

    private void showLicenseDialog() {
        StringBuilder sb = new StringBuilder();

        sb.append("EMEGELAUNCHER\n");
        sb.append("GNU General Public License v3.0\n");
        sb.append("with Commons Clause License Condition v1.0\n\n");
        sb.append("You may use, modify, share, fork, and accept donations.\n");
        sb.append("You may NOT sell this software.\n\n");

        sb.append("────────────────────\n\n");

        sb.append("DEPENDENCIES\n\n");
        sb.append("androidx.appcompat:appcompat:1.1.0\n");
        sb.append("  Apache License 2.0\n");
        sb.append("  Copyright Google LLC\n\n");
        sb.append("androidx.constraintlayout:constraintlayout:1.1.3\n");
        sb.append("  Apache License 2.0\n");
        sb.append("  Copyright Google LLC\n\n");
        sb.append("androidx.viewpager:viewpager:1.0.0\n");
        sb.append("  Apache License 2.0\n");
        sb.append("  Copyright Google LLC\n\n");

        sb.append("────────────────────\n\n");

        sb.append("ACKNOWLEDGMENTS\n\n");
        sb.append("DriveHub by Huseyin\n");
        sb.append("  Reflection-based vehicle service access pattern\n");
        sb.append("  (DexClassLoader + asInterface approach)\n\n");
        sb.append("NewMGRemote (open source)\n");
        sb.append("  iSMART cloud API protocol reference\n");
        sb.append("  (AES/CBC encryption, HMAC-SHA256 verification,\n");
        sb.append("   OAuth2 flow, event-id polling pattern)\n\n");
        sb.append("AOSP Platform Key\n");
        sb.append("  Android Open Source Project default test key\n");
        sb.append("  Used for system-level signing on MG Marvel R\n\n");

        sb.append("────────────────────\n\n");

        sb.append("NO PROPRIETARY CODE\n\n");
        sb.append("This project contains zero proprietary MG/SAIC code.\n");
        sb.append("All vehicle services are accessed via Java reflection.\n");
        sb.append("No SAIC source, libraries, or assets are bundled.\n");
        sb.append("Cloud API encryption is reimplemented from scratch\n");
        sb.append("using standard javax.crypto libraries.\n");

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.licenses))
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show();
    }

    private void addTxCodeViewer() {
        LinearLayout parent = (LinearLayout) findViewById(R.id.row_save_logs).getParent();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(R.drawable.card_bg_selector);
        row.setPadding(20, 16, 20, 16);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView label = new TextView(this);
        label.setText("AIDL Transaction Codes");
        label.setTextSize(16);
        label.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextPrimary));
        textCol.addView(label);

        TextView desc = new TextView(this);
        desc.setText("Enumerate IVehicleSettingService TX codes from Stub");
        desc.setTextSize(11);
        desc.setTextColor(ThemeHelper.resolveColor(this, R.attr.colorTextTertiary));
        textCol.addView(desc);
        row.addView(textCol);

        TextView showBtn = new TextView(this);
        showBtn.setText("SHOW");
        showBtn.setTextSize(13);
        showBtn.setTextColor(ThemeHelper.accentBlue(this));
        showBtn.setPadding(16, 8, 16, 8);
        showBtn.setOnClickListener(v -> {
            java.util.LinkedHashMap<String, Integer> codes =
                VehicleServiceManager.getInstance(this).enumerateAllTransactionCodes();
            if (codes.isEmpty()) {
                Toast.makeText(this, "No TX codes found (services not connected?)", Toast.LENGTH_SHORT).show();
                return;
            }
            StringBuilder sb = new StringBuilder();
            String lastSvc = "";
            for (java.util.Map.Entry<String, Integer> e : codes.entrySet()) {
                String[] parts = e.getKey().split("\\.", 2);
                String svc = parts[0];
                String method = parts.length > 1 ? parts[1] : e.getKey();
                if (!svc.equals(lastSvc)) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append("--- ").append(svc).append(" ---\n");
                    lastSvc = svc;
                }
                sb.append(String.format("0x%02X (%d) = %s\n", e.getValue(), e.getValue(), method));
            }
            new AlertDialog.Builder(this)
                .setTitle("TX Codes (all services: " + codes.size() + ")")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
        });
        row.addView(showBtn);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 2, 0, 2);
        parent.addView(row, lp);
    }

    // ==================== Default Launcher ====================

    private void updateThemeLabel(TextView label) {
        String mode = ThemeHelper.getThemeMode(this);
        switch (mode) {
            case ThemeHelper.MODE_AUTO: label.setText(getString(R.string.auto_follow_car)); break;
            case ThemeHelper.MODE_DARK: label.setText(getString(R.string.theme_dark)); break;
            case ThemeHelper.MODE_LIGHT: label.setText(getString(R.string.theme_light)); break;
            default: label.setText(mode); break;
        }
    }

    private void updateLauncherStatus() {
        TextView txt = findViewById(R.id.txt_current_launcher);
        String current = getCurrentHomeLauncher();
        if (MY_PACKAGE.equals(current)) {
            txt.setText("Current: Emegelauncher (this app)");
        } else if (current != null) {
            txt.setText("Current: " + current);
        } else {
            txt.setText("Current: system default");
        }
    }

    private String getCurrentHomeLauncher() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = getPackageManager().resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri != null && ri.activityInfo != null) {
            String pkg = ri.activityInfo.packageName;
            if (!"android".equals(pkg)) return pkg;
        }
        return null;
    }

    private void setAsDefaultLauncher() {
        try {
            PackageManager pm = getPackageManager();

            // Clear existing preferred for all HOME launchers
            List<ResolveInfo> launchers = getHomeLaunchers();
            for (ResolveInfo ri : launchers) {
                pm.clearPackagePreferredActivities(ri.activityInfo.packageName);
            }

            // Build the set of HOME-capable components
            int n = launchers.size();
            ComponentName[] set = new ComponentName[n];
            for (int i = 0; i < n; i++) {
                set[i] = new ComponentName(launchers.get(i).activityInfo.packageName,
                        launchers.get(i).activityInfo.name);
            }

            // Set emegelauncher as preferred
            IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
            filter.addCategory(Intent.CATEGORY_HOME);
            filter.addCategory(Intent.CATEGORY_DEFAULT);
            ComponentName me = new ComponentName(MY_PACKAGE, MY_PACKAGE + ".MainActivity");
            pm.addPreferredActivity(filter, IntentFilter.MATCH_CATEGORY_EMPTY, set, me);

            Toast.makeText(this, "Emegelauncher set as default", Toast.LENGTH_SHORT).show();
            updateLauncherStatus();
        } catch (Exception e) {
            Log.e(TAG, "Failed to set default launcher", e);
            // Fallback: open system home settings
            try {
                startActivity(new Intent("android.settings.HOME_SETTINGS"));
            } catch (Exception e2) {
                Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void restoreOriginalLauncher() {
        try {
            PackageManager pm = getPackageManager();

            // Clear emegelauncher preferred
            pm.clearPackagePreferredActivities(MY_PACKAGE);

            // Find the OEM launcher
            List<ResolveInfo> launchers = getHomeLaunchers();
            ComponentName oemLauncher = null;
            for (ResolveInfo ri : launchers) {
                if (!MY_PACKAGE.equals(ri.activityInfo.packageName)) {
                    oemLauncher = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
                    break;
                }
            }

            if (oemLauncher != null) {
                int n = launchers.size();
                ComponentName[] set = new ComponentName[n];
                for (int i = 0; i < n; i++) {
                    set[i] = new ComponentName(launchers.get(i).activityInfo.packageName,
                            launchers.get(i).activityInfo.name);
                }
                IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
                filter.addCategory(Intent.CATEGORY_HOME);
                filter.addCategory(Intent.CATEGORY_DEFAULT);
                pm.addPreferredActivity(filter, IntentFilter.MATCH_CATEGORY_EMPTY, set, oemLauncher);
                Toast.makeText(this, "Original launcher restored", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "No other launcher found", Toast.LENGTH_SHORT).show();
            }
            updateLauncherStatus();
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore launcher", e);
            try {
                startActivity(new Intent("android.settings.HOME_SETTINGS"));
            } catch (Exception e2) {
                Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private List<ResolveInfo> getHomeLaunchers() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        return getPackageManager().queryIntentActivities(intent, 0);
    }

    // ==================== Storage & Export ====================

    private void showStorageSelectionDialog() {
        List<VolumeInfo> volumes = findAllVolumes();
        if (volumes.isEmpty()) {
            Toast.makeText(this, "No storage found", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[volumes.size()];
        for (int i = 0; i < volumes.size(); i++) {
            VolumeInfo vi = volumes.get(i);
            long free = vi.path.getFreeSpace() / (1024 * 1024);
            names[i] = vi.description + "\n" + vi.path.getAbsolutePath() + " (" + free + " MB free)";
        }

        String[] actions = {
            getString(R.string.export_logcat),
            getString(R.string.export_diag),
            getString(R.string.export_charge),
            getString(R.string.export_all)
        };
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_storage))
            .setItems(names, (d1, volIdx) -> {
                File vol = volumes.get(volIdx).path;
                new AlertDialog.Builder(this)
                    .setTitle("Export to " + volumes.get(volIdx).description)
                    .setItems(actions, (d2, actIdx) -> {
                        if (actIdx == 0 || actIdx == 3) exportLogcat(vol);
                        if (actIdx == 1 || actIdx == 3) exportDiagnostics(vol);
                        if (actIdx == 2 || actIdx == 3) exportChargeData(vol);
                    }).show();
            }).show();
    }

    /** Storage volume with path and human-readable description */
    private static class VolumeInfo {
        File path;
        String description;
        VolumeInfo(File path, String description) { this.path = path; this.description = description; }
    }

    private List<VolumeInfo> findAllVolumes() {
        List<VolumeInfo> results = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();

        // StorageManager — list ALL volumes with their device names
        try {
            android.os.storage.StorageManager sm =
                (android.os.storage.StorageManager) getSystemService(STORAGE_SERVICE);
            java.lang.reflect.Method getVolumes = sm.getClass().getMethod("getVolumeList");
            Object[] volumes = (Object[]) getVolumes.invoke(sm);
            if (volumes != null) {
                for (Object vol : volumes) {
                    try {
                        File path = (File) vol.getClass().getMethod("getPathFile").invoke(vol);
                        String desc = (String) vol.getClass().getMethod("getDescription", Context.class).invoke(vol, this);
                        boolean removable = false;
                        try { removable = (boolean) vol.getClass().getMethod("isRemovable").invoke(vol); }
                        catch (Exception ignored) {}
                        if (path != null && path.exists()) {
                            String canon = path.getCanonicalPath();
                            if (!seen.contains(canon)) {
                                seen.add(canon);
                                String label = (desc != null ? desc : path.getName());
                                if (removable) label += " (USB)";
                                results.add(new VolumeInfo(path, label));
                                Log.d(TAG, "Volume: " + path + " desc=" + label + " removable=" + removable);

                                // For removable volumes, also try /mnt/media_rw equivalent (writable on SAIC)
                                if (removable) {
                                    String uuid = path.getName();
                                    File mediaRw = new File("/mnt/media_rw/" + uuid);
                                    if (mediaRw.exists() && mediaRw.isDirectory()) {
                                        String mrc = mediaRw.getCanonicalPath();
                                        if (!seen.contains(mrc)) {
                                            seen.add(mrc);
                                            results.add(new VolumeInfo(mediaRw, label + " [media_rw]"));
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) { Log.d(TAG, "Volume enum error: " + e.getMessage()); }
                }
            }
        } catch (Exception e) { Log.d(TAG, "StorageManager: " + e.getMessage()); }

        // Also scan /mnt/media_rw directly for any volumes not found via StorageManager
        File mediaRw = new File("/mnt/media_rw");
        if (mediaRw.exists()) {
            File[] children = mediaRw.listFiles();
            if (children != null) {
                for (File f : children) {
                    if (!f.isDirectory()) continue;
                    try {
                        String canon = f.getCanonicalPath();
                        if (!seen.contains(canon)) {
                            seen.add(canon);
                            results.add(new VolumeInfo(f, f.getName() + " (media_rw)"));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        return results;
    }

    private void exportLogcat(File destination) {
        Toast.makeText(this, "Exporting logcat...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Process proc = null;
            try {
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File logFile = new File(destination, "emegelauncher_log_" + ts + ".txt");
                proc = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time"});
                try (InputStream is = proc.getInputStream();
                     InputStream es = proc.getErrorStream();
                     FileOutputStream fos = new FileOutputStream(logFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                    fos.flush();
                }
                // Drain error stream to prevent process hang
                proc.waitFor();
                runOnUiThread(() -> Toast.makeText(this, "Logcat saved: " + logFile.getAbsolutePath(), Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                Log.e(TAG, "Logcat export failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Logcat failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                if (proc != null) proc.destroy();
            }
        }).start();
    }

    private void exportDiagnostics(File destination) {
        Toast.makeText(this, "Exporting diagnostics...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File diagFile = new File(destination, "emegelauncher_diag_" + ts + ".txt");
                VehicleServiceManager vm = VehicleServiceManager.getInstance(this);
                List<YFVehicleProperty.PropertyEntry> props = YFVehicleProperty.getAllProperties();

                try (PrintWriter pw = new PrintWriter(new FileOutputStream(diagFile))) {
                    pw.println("=== Emegelauncher Diagnostic Dump ===");
                    pw.println("Date: " + new Date().toString());
                    pw.println("SAIC service connected: " + vm.isSaicConnected());
                    pw.println("HVAC service available: " + vm.hasAirCondition());
                    pw.println("Total properties: " + props.size());
                    pw.println("");

                    pw.println("--- SAIC Services (ALL sub-services) ---");
                    java.util.LinkedHashMap<String, String> saicData = vm.getAllSaicData();
                    for (java.util.Map.Entry<String, String> e : saicData.entrySet()) {
                        pw.println(e.getKey() + " = " + e.getValue());
                    }
                    pw.println("");

                    pw.println("--- All VHAL Properties ---");
                    for (YFVehicleProperty.PropertyEntry p : props) {
                        String val = vm.getPropertyValue(p.id);
                        String desc = (p.description != null && !p.description.isEmpty()) ? " // " + p.description : "";
                        pw.println(p.name + " = " + val + desc);
                    }
                    pw.println("");

                    pw.println("--- AIDL Transaction Codes (ALL services) ---");
                    java.util.LinkedHashMap<String, Integer> txCodes = vm.enumerateAllTransactionCodes();
                    if (txCodes.isEmpty()) {
                        pw.println("(none found — service not connected or Stub class not loaded)");
                    } else {
                        for (java.util.Map.Entry<String, Integer> tx : txCodes.entrySet()) {
                            pw.println(String.format("0x%02X (%d) = %s", tx.getValue(), tx.getValue(), tx.getKey()));
                        }
                    }
                    pw.println("");

                    pw.println("--- Setting Service Binder Info ---");
                    pw.println("hasSettingBinder: " + vm.hasSettingBinder());
                    // Also dump AIDL descriptor if available
                    try {
                        java.lang.reflect.Field sf = VehicleServiceManager.class.getDeclaredField("mSettingService");
                        sf.setAccessible(true);
                        Object svc = sf.get(vm);
                        if (svc != null) {
                            pw.println("Service class: " + svc.getClass().getName());
                            Class<?> enclosing = svc.getClass().getEnclosingClass();
                            if (enclosing != null) {
                                pw.println("Stub class: " + enclosing.getName());
                                try {
                                    java.lang.reflect.Field df = enclosing.getDeclaredField("DESCRIPTOR");
                                    df.setAccessible(true);
                                    pw.println("DESCRIPTOR: " + df.get(null));
                                } catch (Exception ignored) {}
                            }
                        } else {
                            pw.println("mSettingService: null");
                        }
                    } catch (Exception ex) { pw.println("Error: " + ex.getMessage()); }
                }

                runOnUiThread(() -> Toast.makeText(this, "Diagnostics saved: " + diagFile.getAbsolutePath(), Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                Log.e(TAG, "Diagnostics export failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Diagnostics failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void exportChargeData(File destination) {
        Toast.makeText(this, "Exporting charge data...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File chargeFile = new File(destination, "emegelauncher_charge_" + ts + ".txt");
                com.emegelauncher.vehicle.BatteryHealthTracker tracker =
                    new com.emegelauncher.vehicle.BatteryHealthTracker(this);

                try (PrintWriter pw = new PrintWriter(new FileOutputStream(chargeFile))) {
                    pw.println("=== Emegelauncher Charge Data Export ===");
                    pw.println("Date: " + new Date().toString());
                    pw.println("");

                    pw.println("--- SOH Summary ---");
                    float soh = tracker.getEstimatedSoh();
                    float cap = tracker.getEstimatedCapacity();
                    pw.println("Average SOH: " + (soh >= 0 ? String.format("%.1f%%", soh) : "N/A"));
                    pw.println("Average Capacity: " + (cap >= 0 ? String.format("%.1f kWh", cap) : "N/A"));
                    pw.println("");

                    pw.println("--- Charge Sessions ---");
                    org.json.JSONArray sessions = tracker.getSessions();
                    pw.println("Total sessions: " + sessions.length());
                    pw.println("");

                    for (int i = 0; i < sessions.length(); i++) {
                        try {
                            org.json.JSONObject s = sessions.getJSONObject(i);
                            pw.println("Session " + (i + 1) + ":");
                            pw.println("  Time: " + new Date(s.optLong("time", 0)).toString());
                            pw.println("  Start SOC: " + String.format("%.1f%%", s.optDouble("startSoc", 0)));
                            pw.println("  End SOC: " + String.format("%.1f%%", s.optDouble("endSoc", 0)));
                            pw.println("  Energy: " + String.format("%.2f kWh", s.optDouble("energy", 0)));
                            pw.println("  Capacity: " + String.format("%.1f kWh", s.optDouble("cap", 0)));
                            pw.println("  SOH: " + String.format("%.1f%%", s.optDouble("soh", 0)));
                            pw.println("  Voltage: " + String.format("%.1f V", s.optDouble("volt", 0)));
                            pw.println("");
                        } catch (Exception ignored) {}
                    }

                    pw.println("--- Last Charge Snapshot ---");
                    org.json.JSONObject snapshot = tracker.getLastChargeSnapshot();
                    if (snapshot != null) {
                        pw.println(snapshot.toString(2));
                    } else {
                        pw.println("No snapshot available");
                    }

                    pw.println("");
                    pw.println("--- Raw JSON ---");
                    pw.println(sessions.toString(2));
                }

                runOnUiThread(() -> Toast.makeText(this, "Charge data saved: " + chargeFile.getAbsolutePath(), Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                Log.e(TAG, "Charge data export failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}

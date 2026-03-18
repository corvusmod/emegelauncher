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

        // Save logs
        findViewById(R.id.row_save_logs).setOnClickListener(v -> showStorageSelectionDialog());
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

        String[] actions = {getString(R.string.export_logcat), getString(R.string.export_diag), getString(R.string.export_both)};
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_storage))
            .setItems(names, (d1, volIdx) -> {
                File vol = volumes.get(volIdx).path;
                new AlertDialog.Builder(this)
                    .setTitle("Export to " + volumes.get(volIdx).description)
                    .setItems(actions, (d2, actIdx) -> {
                        if (actIdx == 0 || actIdx == 2) exportLogcat(vol);
                        if (actIdx == 1 || actIdx == 2) exportDiagnostics(vol);
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
                }

                runOnUiThread(() -> Toast.makeText(this, "Diagnostics saved: " + diagFile.getAbsolutePath(), Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                Log.e(TAG, "Diagnostics export failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Diagnostics failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}

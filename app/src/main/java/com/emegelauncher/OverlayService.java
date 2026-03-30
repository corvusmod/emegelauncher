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

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Floating overlay on the left edge of the screen.
 * Provides a Back button and a Recent Apps button.
 * Runs as a system service since the app has android.uid.system.
 */
public class OverlayService extends Service {
    private static final String TAG = "OverlayService";

    private WindowManager mWindowManager;
    private View mOverlayView;
    private View mRecentPanel;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mStatusBarHidden = false;
    private TextView mStatusBarToggle;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createOverlay();
    }

    private void createOverlay() {
        // Compact tab at bottom-left edge (vertical layout)
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setBackgroundColor(0xAA1A1A1E);
        tab.setPadding(6, 10, 6, 10);

        // Back button
        TextView backBtn = new TextView(this);
        backBtn.setText("\u25C0"); // ◀
        backBtn.setTextSize(16);
        backBtn.setTextColor(0xDDF5F5F7);
        backBtn.setPadding(8, 8, 8, 8);
        backBtn.setOnClickListener(v -> goBack());
        tab.addView(backBtn);

        // Recent apps button
        TextView recentBtn = new TextView(this);
        recentBtn.setText("\u25A3"); // ▣
        recentBtn.setTextSize(16);
        recentBtn.setTextColor(0xDDF5F5F7);
        recentBtn.setPadding(8, 8, 8, 8);
        recentBtn.setOnClickListener(v -> toggleRecentApps());
        tab.addView(recentBtn);

        // Top dock toggle button (hide/show MG overlay bar)
        mStatusBarToggle = new TextView(this);
        mStatusBarToggle.setText("\u25B2"); // ▲ (tap to hide — push up)
        mStatusBarToggle.setTextSize(14);
        mStatusBarToggle.setTextColor(0xFF40C8FF); // Bright cyan — visible on dark backgrounds
        mStatusBarToggle.setPadding(8, 8, 8, 8);
        mStatusBarToggle.setOnClickListener(v -> toggleStatusBar());
        tab.addView(mStatusBarToggle);

        mOverlayView = tab;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.LEFT | Gravity.BOTTOM;
        params.x = 0;
        params.y = 120; // Above the system bar

        try {
            mWindowManager.addView(mOverlayView, params);
            Log.d(TAG, "Overlay added to window");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add overlay: " + e.getMessage());
        }
    }

    /**
     * Toggle the SAIC top dock bar (MG button + app shortcuts overlay) on/off.
     * The SystemUI's DockControlManager hides the top dock when a package in
     * "top_dock_hide_package_info" is in the foreground. We can't modify that list,
     * but we CAN use wm commands to manipulate the window, or use the IStatusBar
     * service to collapse/disable the top area.
     * Multiple approaches are tried — at least one should work on this car.
     */
    private void toggleStatusBar() {
        mStatusBarHidden = !mStatusBarHidden;
        setTopDockHidden(mStatusBarHidden);
        if (mStatusBarToggle != null) {
            mStatusBarToggle.setText(mStatusBarHidden ? "\u25BC" : "\u25B2"); // ▼ show / ▲ hide
            mStatusBarToggle.setTextColor(mStatusBarHidden ? 0xFFFF453A : 0xFF40C8FF); // Red when hidden, cyan when visible
        }
        Log.d(TAG, "TAP top dock toggle → " + (mStatusBarHidden ? "HIDE" : "SHOW"));
        com.emegelauncher.vehicle.FileLogger.getInstance(this).i(TAG, "TAP top dock toggle → " + (mStatusBarHidden ? "HIDE" : "SHOW"));
    }

    private void setTopDockHidden(boolean hidden) {
        com.emegelauncher.vehicle.FileLogger fl = com.emegelauncher.vehicle.FileLogger.getInstance(this);
        fl.i(TAG, "=== setTopDockHidden(" + hidden + ") START ===");

        // Method 1: Use IWindowManager to find and hide the top dock window by name
        // The SAIC top dock is added via WindowManager.addView with title "StatusBar"
        // We can use wm commands or IWindowManager to manipulate it
        try {
            // First, dump all windows to identify the top dock
            if (!hidden) {
                // Only dump on show (to keep log small)
            }
            // Use "input" to find window positions — dumpsys window windows is large
            // Instead, try to set the StatusBar window to 0 height via wm command
            String cmd;
            if (hidden) {
                // Move the top dock window offscreen by setting negative y position
                // Use IWindowManager.setForcedDisplaySize or manipulate the window
                cmd = "wm overscan 0,-200,0,0";  // Push content up by 200px, hiding top area
            } else {
                cmd = "wm overscan 0,0,0,0";  // Reset overscan
            }
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            int exit = p.waitFor();
            fl.i(TAG, "M1: " + cmd + " exit=" + exit);
        } catch (Exception e) {
            fl.d(TAG, "M1: wm overscan: " + e.getMessage());
        }

        // Method 2: Use dumpsys to identify window tokens, then manipulate via IWindowSession
        try {
            if (hidden) {
                // Dump windows to log for analysis
                Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                    "dumpsys window windows | grep -E 'Window #|mAttrs|title|StatusBar|TopDock|Dock'"});
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()));
                StringBuilder sb = new StringBuilder("M2 windows:\n");
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count++ < 30) {
                    sb.append(line.trim()).append("\n");
                }
                reader.close();
                fl.d(TAG, sb.toString());
            }
        } catch (Exception e) {
            fl.d(TAG, "M2: dumpsys: " + e.getMessage());
        }

        // Method 3: Set system property that might control dock visibility
        try {
            String val = hidden ? "1" : "0";
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                "setprop persist.sys.topdock.hidden " + val});
            int exit = p.waitFor();
            fl.d(TAG, "M3: setprop persist.sys.topdock.hidden " + val + " exit=" + exit);
        } catch (Exception e) {
            fl.d(TAG, "M3: " + e.getMessage());
        }

        // Method 4: Try to resize the StatusBar window to 0 height
        try {
            if (hidden) {
                Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                    "wm size 1200x1500"});  // Increase usable area to push dock offscreen
                int exit = p.waitFor();
                fl.d(TAG, "M4: wm size 1200x1500 exit=" + exit);
            } else {
                Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                    "wm size reset"});
                int exit = p.waitFor();
                fl.d(TAG, "M4: wm size reset exit=" + exit);
            }
        } catch (Exception e) {
            fl.d(TAG, "M4: " + e.getMessage());
        }

        fl.i(TAG, "=== setTopDockHidden(" + hidden + ") END ===");
    }

    private void goBack() {
        // Send BACK key event via Instrumentation on background thread
        new Thread(() -> {
            try {
                new Instrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            } catch (Exception e) {
                Log.e(TAG, "Back key failed: " + e.getMessage());
            }
        }).start();
    }

    private void toggleRecentApps() {
        if (mRecentPanel != null) {
            hideRecentPanel();
            return;
        }
        showRecentPanel();
    }

    private void showRecentPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xEE1A1A1E);
        panel.setPadding(20, 20, 20, 20);

        // Title
        TextView title = new TextView(this);
        title.setText(getString(R.string.recent_apps));
        title.setTextSize(16);
        title.setTextColor(0xFFF5F5F7);
        title.setPadding(0, 0, 0, 16);
        panel.addView(title);

        // Get running tasks (recent apps)
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RecentTaskInfo> recentTasks = am.getRecentTasks(10, ActivityManager.RECENT_WITH_EXCLUDED);
        PackageManager pm = getPackageManager();

        java.util.Set<String> seen = new java.util.HashSet<>();
        int count = 0;
        for (ActivityManager.RecentTaskInfo task : recentTasks) {
            if (task.baseIntent == null || task.baseIntent.getComponent() == null) continue;
            String pkg = task.baseIntent.getComponent().getPackageName();

            // Skip system/service apps (same as AppsActivity filter)
            if (seen.contains(pkg)) continue;
            if (pkg.equals("com.emegelauncher")) continue;
            if (pkg.contains(".service")) continue;
            if (pkg.startsWith("com.saicmotor.hmi.")) continue;
            if (pkg.startsWith("com.android.")) continue;
            if (pkg.startsWith("com.yfve.")) continue;
            if (pkg.startsWith("com.allgo.")) continue;
            if (pkg.startsWith("com.saicmotor.voice")) continue;
            if (pkg.startsWith("com.saicmotor.adapter")) continue;
            if (pkg.equals("com.saicvehicleservice")) continue;
            if (pkg.equals("com.abupdate.ota")) continue;
            if (pkg.equals("android.car.usb.handler")) continue;
            seen.add(pkg);

            String label;
            try {
                label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            } catch (Exception e) {
                label = pkg;
            }

            TextView appBtn = new TextView(this);
            appBtn.setText(label);
            appBtn.setTextSize(14);
            appBtn.setTextColor(0xFF26A69A);
            appBtn.setPadding(12, 12, 12, 12);
            final Intent launchIntent = task.baseIntent;
            appBtn.setOnClickListener(v -> {
                try {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
                    startActivity(launchIntent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch: " + e.getMessage());
                }
                hideRecentPanel();
            });
            panel.addView(appBtn);

            if (++count >= 8) break;
        }

        // Close button
        TextView closeBtn = new TextView(this);
        closeBtn.setText(getString(R.string.close));
        closeBtn.setTextSize(13);
        closeBtn.setTextColor(0xFF8E8E93);
        closeBtn.setPadding(12, 16, 12, 8);
        closeBtn.setOnClickListener(v -> hideRecentPanel());
        panel.addView(closeBtn);

        mRecentPanel = panel;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            300,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.LEFT | Gravity.BOTTOM;
        params.x = 0;
        params.y = 160;

        try {
            mWindowManager.addView(mRecentPanel, params);
        } catch (Exception e) {
            Log.e(TAG, "Failed to show recent panel: " + e.getMessage());
            mRecentPanel = null;
        }
    }

    private void hideRecentPanel() {
        if (mRecentPanel != null) {
            try { mWindowManager.removeView(mRecentPanel); } catch (Exception ignored) {}
            mRecentPanel = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Restore top dock if it was hidden
        if (mStatusBarHidden) {
            mStatusBarHidden = false;
            setTopDockHidden(false);
        }
        hideRecentPanel();
        if (mOverlayView != null) {
            try { mWindowManager.removeView(mOverlayView); } catch (Exception ignored) {}
        }
    }
}

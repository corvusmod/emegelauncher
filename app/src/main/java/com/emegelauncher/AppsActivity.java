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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class AppsActivity extends Activity {

    private List<AppInfo> mApps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps);

        loadApps();

        GridView gridView = findViewById(R.id.apps_grid);
        gridView.setAdapter(new AppsAdapter());

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo info = mApps.get(position);
            Intent intent = getPackageManager().getLaunchIntentForPackage(info.packageName);
            if (intent != null) {
                startActivity(intent);
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        // Hide system/service/OEM apps. User-installed apps always show.
        java.util.Set<String> hidden = new java.util.HashSet<>(java.util.Arrays.asList(
            // This launcher
            "com.emegelauncher",
            // SAIC services (background, no UI for user)
            "com.saicmotor.service.vehicle", "com.saicmotor.service.engmode",
            "com.saicmotor.service.btcall", "com.saicmotor.service.radio",
            "com.saicmotor.service.media", "com.saicmotor.service.systemsettings",
            "com.saicmotor.service.aroundview", "com.saicmotor.adapterservice",
            "com.saicmotor.mapservice", "com.saicmotor.voiceservice",
            "com.saicmotor.voicetts", "com.saicmotor.voicevui", "com.saicmotor.voiceagent",
            "com.saicvehicleservice",
            // SAIC HMI apps (already accessible from launcher shortcuts)
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
            // SAIC system utilities
            "com.saicmotor.hmi.ep21avnlogin", "com.saicmotor.ep21avnlogin", "com.saic.avnlogin",
            // YFVE system services
            "com.yfve.carotherservice", "com.yfve.usbupdate", "com.yfve.fileservice",
            "com.yfve.server.devicemanager",
            // Allgo (CarPlay/Android Auto — accessible from shortcuts)
            "com.allgo.carplay.service", "com.allgo.app.androidauto",
            "com.allgo.rui", "com.allgo.remoteui.mediabrowserservice",
            "com.allgo.mirroring.control.service", "com.allgo.service.mirroringcontrol",
            // Android system apps
            "android.car.usb.handler", "com.android.shell",
            "com.android.settings.intelligence", "com.android.statementservice",
            "com.android.systemui", "com.android.car.trust",
            "com.android.camera2", "com.android.camera",
            // Navigation (accessible from shortcut)
            "com.telenav.app.arp",
            // ABUpdate (OTA service)
            "com.abupdate.ota"
        ));

        List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
        for (ResolveInfo ri : activities) {
            String pkg = ri.activityInfo.packageName;
            if (hidden.contains(pkg)) continue;

            AppInfo info = new AppInfo();
            info.label = ri.loadLabel(pm).toString();
            info.packageName = pkg;
            info.icon = ri.activityInfo.loadIcon(pm);
            mApps.add(info);
        }
    }

    private static class AppInfo {
        String label;
        String packageName;
        Drawable icon;
    }

    private class AppsAdapter extends BaseAdapter {
        @Override public int getCount() { return mApps.size(); }
        @Override public Object getItem(int i) { return mApps.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            LinearLayout layout;
            if (convertView == null) {
                layout = new LinearLayout(AppsActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setGravity(android.view.Gravity.CENTER);
                layout.setPadding(16, 16, 16, 16);
                
                ImageView icon = new ImageView(AppsActivity.this);
                layout.addView(icon, new LinearLayout.LayoutParams(120, 120));

                TextView label = new TextView(AppsActivity.this);
                label.setTextColor(ThemeHelper.resolveColor(AppsActivity.this, R.attr.colorTextPrimary));
                label.setGravity(android.view.Gravity.CENTER);
                label.setTextSize(16);
                layout.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                layout = (LinearLayout) convertView;
            }

            AppInfo info = mApps.get(i);
            ImageView iv = (ImageView) layout.getChildAt(0);
            TextView tv = (TextView) layout.getChildAt(1);

            iv.setImageDrawable(info.icon);
            tv.setText(info.label);

            return layout;
        }
    }
}

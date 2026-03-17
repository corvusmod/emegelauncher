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
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.emegelauncher.vehicle.VehicleServiceManager;
import com.emegelauncher.vehicle.YFVehicleProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DebugActivity extends Activity {

    private VehicleServiceManager mVehicleManager;
    private List<YFVehicleProperty.PropertyEntry> mAllProperties;
    private List<YFVehicleProperty.PropertyEntry> mFilteredProperties;
    private DiagnosticsAdapter mVhalAdapter;

    // SAIC service data
    private List<String[]> mSaicData = new ArrayList<>(); // [name, value]
    private List<String[]> mFilteredSaicData = new ArrayList<>();
    private SaicAdapter mSaicAdapter;

    private final Handler mHandler = new Handler(android.os.Looper.getMainLooper());
    private boolean mShowSaic = false;
    private ListView mListView;
    private TextView mTabVhal, mTabSaic;
    private String mCurrentFilter = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);

        mVehicleManager = VehicleServiceManager.getInstance(this);
        mAllProperties = YFVehicleProperty.getAllProperties();
        mFilteredProperties = new ArrayList<>(mAllProperties);

        mListView = findViewById(R.id.diagnostics_list);
        mVhalAdapter = new DiagnosticsAdapter();
        mSaicAdapter = new SaicAdapter();
        mListView.setAdapter(mVhalAdapter);

        EditText filterInput = findViewById(R.id.filter_input);
        filterInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                mCurrentFilter = s.toString();
                applyFilter(mCurrentFilter);
            }
        });

        // Tab buttons
        mTabVhal = findViewById(R.id.tab_vhal);
        mTabSaic = findViewById(R.id.tab_saic);
        int blue = 0xFF0A84FF;
        int gray = ThemeHelper.resolveColor(this, R.attr.colorTextSecondary);

        mTabVhal.setTextColor(blue);
        mTabSaic.setTextColor(gray);

        mTabVhal.setOnClickListener(v -> {
            mShowSaic = false;
            mTabVhal.setTextColor(blue);
            mTabSaic.setTextColor(gray);
            mListView.setAdapter(mVhalAdapter);
            applyFilter(mCurrentFilter);
        });
        mTabSaic.setOnClickListener(v -> {
            mShowSaic = true;
            mTabSaic.setTextColor(blue);
            mTabVhal.setTextColor(gray);
            refreshSaicData();
            mListView.setAdapter(mSaicAdapter);
            applyFilter(mCurrentFilter);
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        startLiveUpdates();
    }

    private void refreshSaicData() {
        LinkedHashMap<String, String> data = mVehicleManager.getAllSaicData();
        mSaicData.clear();
        for (Map.Entry<String, String> e : data.entrySet()) {
            mSaicData.add(new String[]{e.getKey(), e.getValue()});
        }
        applyFilter(mCurrentFilter);
    }

    private void applyFilter(String query) {
        if (!mShowSaic) {
            mFilteredProperties.clear();
            if (query.isEmpty()) {
                mFilteredProperties.addAll(mAllProperties);
            } else {
                String upper = query.toUpperCase();
                for (YFVehicleProperty.PropertyEntry entry : mAllProperties) {
                    if (entry.name.toUpperCase().contains(upper) ||
                        (entry.description != null && entry.description.toUpperCase().contains(upper))) {
                        mFilteredProperties.add(entry);
                    }
                }
            }
            mVhalAdapter.notifyDataSetChanged();
        } else {
            mFilteredSaicData.clear();
            if (query.isEmpty()) {
                mFilteredSaicData.addAll(mSaicData);
            } else {
                String upper = query.toUpperCase();
                for (String[] entry : mSaicData) {
                    if (entry[0].toUpperCase().contains(upper)) {
                        mFilteredSaicData.add(entry);
                    }
                }
            }
            mSaicAdapter.notifyDataSetChanged();
        }
    }

    private void startLiveUpdates() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mShowSaic) {
                    refreshSaicData();
                } else {
                    mVhalAdapter.notifyDataSetChanged();
                }
                mHandler.postDelayed(this, 2000);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
    }

    // ==================== VHAL Adapter ====================

    private class DiagnosticsAdapter extends BaseAdapter {
        @Override public int getCount() { return mFilteredProperties.size(); }
        @Override public Object getItem(int position) { return mFilteredProperties.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(DebugActivity.this).inflate(R.layout.item_diagnostic, parent, false);

            YFVehicleProperty.PropertyEntry info = mFilteredProperties.get(position);
            TextView nameText = convertView.findViewById(R.id.prop_name);
            TextView descText = convertView.findViewById(R.id.prop_desc);
            TextView valueText = convertView.findViewById(R.id.prop_value);

            nameText.setText(info.name);
            if (info.description != null && !info.description.isEmpty()) {
                descText.setText(info.description);
                descText.setVisibility(View.VISIBLE);
            } else {
                descText.setVisibility(View.GONE);
            }
            valueText.setText("Value: " + mVehicleManager.getPropertyValue(info.id));
            return convertView;
        }
    }

    // ==================== SAIC Service Adapter ====================

    private class SaicAdapter extends BaseAdapter {
        @Override public int getCount() { return mFilteredSaicData.size(); }
        @Override public Object getItem(int position) { return mFilteredSaicData.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(DebugActivity.this).inflate(R.layout.item_diagnostic, parent, false);

            String[] entry = mFilteredSaicData.get(position);
            TextView nameText = convertView.findViewById(R.id.prop_name);
            TextView descText = convertView.findViewById(R.id.prop_desc);
            TextView valueText = convertView.findViewById(R.id.prop_value);

            nameText.setText(entry[0]);
            descText.setVisibility(View.GONE);
            valueText.setText("Value: " + entry[1]);
            return convertView;
        }
    }
}

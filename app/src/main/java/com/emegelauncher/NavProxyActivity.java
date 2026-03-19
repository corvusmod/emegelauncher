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
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/**
 * Navigation proxy — bridges standard Android geo: intents to Telenav.
 *
 * Any app (e.g. a charging station finder) can send:
 *   Intent(ACTION_VIEW, Uri.parse("geo:40.4168,-3.7038?q=Charger+Name"))
 *
 * This activity intercepts it and forwards to the car's Telenav navigation
 * using its proprietary intent action, so third-party apps don't need to
 * know about Telenav specifically.
 *
 * Supported URI formats:
 *   geo:lat,lon
 *   geo:lat,lon?q=label
 *   geo:0,0?q=search+query
 *   google.navigation:q=lat,lon&mode=d
 */
public class NavProxyActivity extends Activity {
    private static final String TAG = "NavProxyActivity";
    private static final String TELENAV_PKG = "com.telenav.app.arp";
    private static final String TELENAV_SEARCH = "com.telenav.arp.activity.NAVIGATION_SEARCH";
    private static final String TELENAV_NAV = "com.telenav.arp.activity.NAVIGATION";
    private static final String TELENAV_MAIN = "com.telenav.arp.module.map.MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent incoming = getIntent();
        if (incoming == null || incoming.getData() == null) {
            finish();
            return;
        }

        Uri uri = incoming.getData();
        String scheme = uri.getScheme();
        Log.d(TAG, "Received: " + uri.toString());

        try {
            if ("geo".equals(scheme)) {
                handleGeoIntent(uri);
            } else if ("google.navigation".equals(scheme)) {
                handleGoogleNavIntent(uri);
            } else {
                // Fallback: just open Telenav
                launchTelenav(null, null, null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to forward to nav: " + e.getMessage());
            Toast.makeText(this, "Navigation error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        finish();
    }

    private void handleGeoIntent(Uri uri) {
        String ssp = uri.getSchemeSpecificPart(); // "lat,lon" or "lat,lon?q=query"
        String query = uri.getQueryParameter("q");

        double lat = 0, lon = 0;
        boolean hasCoords = false;

        // Parse "lat,lon" from scheme-specific part
        String coordPart = ssp;
        if (coordPart != null && coordPart.contains("?")) {
            coordPart = coordPart.substring(0, coordPart.indexOf("?"));
        }
        if (coordPart != null && coordPart.contains(",")) {
            try {
                String[] parts = coordPart.split(",");
                lat = Double.parseDouble(parts[0].trim());
                lon = Double.parseDouble(parts[1].trim());
                if (lat != 0 || lon != 0) hasCoords = true;
            } catch (Exception ignored) {}
        }

        if (hasCoords) {
            launchTelenav(lat, lon, query);
        } else if (query != null && !query.isEmpty()) {
            launchTelenavSearch(query);
        } else {
            launchTelenav(null, null, null);
        }
    }

    private void handleGoogleNavIntent(Uri uri) {
        String query = uri.getQueryParameter("q");
        if (query != null && query.contains(",")) {
            try {
                String[] parts = query.split(",");
                double lat = Double.parseDouble(parts[0].trim());
                double lon = Double.parseDouble(parts[1].trim());
                launchTelenav(lat, lon, null);
                return;
            } catch (Exception ignored) {}
        }
        if (query != null) {
            launchTelenavSearch(query);
        } else {
            launchTelenav(null, null, null);
        }
    }

    private void launchTelenav(Double lat, Double lon, String label) {
        Intent nav = new Intent(TELENAV_NAV);
        nav.addCategory(Intent.CATEGORY_DEFAULT);
        nav.setPackage(TELENAV_PKG);
        if (lat != null && lon != null) {
            nav.putExtra("latitude", lat);
            nav.putExtra("longitude", lon);
            nav.putExtra("lat", lat);
            nav.putExtra("lon", lon);
            // Also try geo URI as data
            String geoUri = "geo:" + lat + "," + lon;
            if (label != null) geoUri += "?q=" + Uri.encode(label);
            nav.setData(Uri.parse(geoUri));
        }
        if (label != null) {
            nav.putExtra("query", label);
            nav.putExtra("q", label);
            nav.putExtra("name", label);
        }
        nav.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(nav);
            String msg = "Navigating";
            if (label != null) msg += " to " + label;
            else if (lat != null) msg += String.format(" to %.4f, %.4f", lat, lon);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.d(TAG, "NAVIGATION action failed, trying direct launch: " + e.getMessage());
            // Fallback: open Telenav main activity directly
            try {
                Intent fallback = new Intent();
                fallback.setComponent(new ComponentName(TELENAV_PKG, TELENAV_MAIN));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (lat != null && lon != null) {
                    fallback.putExtra("latitude", lat);
                    fallback.putExtra("longitude", lon);
                }
                startActivity(fallback);
            } catch (Exception e2) {
                Toast.makeText(this, "Navigation app not found", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void launchTelenavSearch(String query) {
        Intent search = new Intent(TELENAV_SEARCH);
        search.addCategory(Intent.CATEGORY_DEFAULT);
        search.setPackage(TELENAV_PKG);
        search.putExtra("query", query);
        search.putExtra("q", query);
        search.putExtra(android.app.SearchManager.QUERY, query);
        search.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(search);
            Toast.makeText(this, "Searching: " + query, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.d(TAG, "NAVIGATION_SEARCH failed, falling back: " + e.getMessage());
            launchTelenav(null, null, query);
        }
    }
}

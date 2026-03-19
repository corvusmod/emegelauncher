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
import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;

import com.emegelauncher.vehicle.VehicleServiceManager;
import com.emegelauncher.vehicle.YFVehicleProperty;

public class ThemeHelper {
    private static final String PREFS = "emegelauncher_prefs";
    private static final String KEY_THEME_MODE = "theme_mode"; // "auto", "dark", "light"

    // Theme modes
    public static final String MODE_AUTO = "auto";
    public static final String MODE_DARK = "dark";
    public static final String MODE_LIGHT = "light";

    public static void applyTheme(Activity activity) {
        activity.setTheme(isDarkMode(activity)
                ? R.style.Theme_Emegelauncher
                : R.style.Theme_Emegelauncher_Light);
    }

    /** Get the current theme mode setting ("auto", "dark", "light") */
    public static String getThemeMode(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_THEME_MODE, MODE_AUTO);
    }

    public static void setThemeMode(Context context, String mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_THEME_MODE, mode).apply();
    }

    /**
     * Determine if dark mode should be active.
     * - "auto": follow the car's night mode setting
     * - "dark": always dark
     * - "light": always light
     */
    public static boolean isDarkMode(Context context) {
        String mode = getThemeMode(context);
        if (MODE_DARK.equals(mode)) return true;
        if (MODE_LIGHT.equals(mode)) return false;
        // Auto: read from car
        return isCarNightMode(context);
    }

    /**
     * Read the car's current night mode from multiple sources.
     * Returns true if the car is in night/dark mode.
     */
    private static boolean isCarNightMode(Context context) {
        try {
            VehicleServiceManager vm = VehicleServiceManager.getInstance(context);

            // Source 1: SystemSettings IGeneralService — getIsNightMode()
            String nightMode = vm.callSaicMethod("sysgeneral", "getIsNightMode");
            if (nightMode != null && !nightMode.equals("N/A")) {
                return "true".equalsIgnoreCase(nightMode) || "1".equals(nightMode);
            }

            // Source 2: VHAL NIGHT_MODE property
            String vhalNight = vm.getPropertyValue(YFVehicleProperty.NIGHT_MODE);
            if (vhalNight != null && !vhalNight.equals("N/A")) {
                return "true".equalsIgnoreCase(vhalNight) || "1".equals(vhalNight);
            }

            // Source 3: PMS day/night mode
            String pmsNight = vm.getPropertyValue(YFVehicleProperty.PMS_SYSTEM_DAY_NIGHT_MODE);
            if (pmsNight != null && !pmsNight.equals("N/A")) {
                // Typically 1=day, 2=night
                return "2".equals(pmsNight);
            }

            // Source 4: Adapter MapService getDayNightMode()
            String mapNight = vm.callSaicMethod("adaptermap", "getDayNightMode");
            if (mapNight != null && !mapNight.equals("N/A")) {
                return "2".equals(mapNight) || "1".equals(mapNight); // depends on encoding
            }
        } catch (Exception ignored) {}

        // Default to dark if car state unknown
        return true;
    }

    /** Check if car night mode changed since last theme apply (for auto mode) */
    private static Boolean sLastCarNight = null;
    public static boolean hasCarThemeChanged(Context context) {
        if (!MODE_AUTO.equals(getThemeMode(context))) return false;
        boolean current = isCarNightMode(context);
        if (sLastCarNight == null) { sLastCarNight = current; return false; }
        boolean changed = (current != sLastCarNight);
        sLastCarNight = current;
        return changed;
    }

    public static int resolveColor(Context context, int attrId) {
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(attrId, tv, true);
        return tv.data;
    }

    /** Get accent color adjusted for current theme (darker on light bg) */
    public static int accentBlue(Context c) { return isDarkMode(c) ? 0xFF2979FF : 0xFF0066CC; }
    public static int accentGreen(Context c) { return isDarkMode(c) ? 0xFF30D158 : 0xFF1B8A3A; }
    public static int accentRed(Context c) { return isDarkMode(c) ? 0xFFFF453A : 0xFFD32F2F; }
    public static int accentOrange(Context c) { return isDarkMode(c) ? 0xFFFF9F0A : 0xFFCC7A00; }
    public static int accentTeal(Context c) { return isDarkMode(c) ? 0xFF26A69A : 0xFF00796B; }
    public static int accentPurple(Context c) { return isDarkMode(c) ? 0xFFBF5AF2 : 0xFF7B1FA2; }
}

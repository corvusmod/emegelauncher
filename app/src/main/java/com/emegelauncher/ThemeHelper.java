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

public class ThemeHelper {
    private static final String PREFS = "emegelauncher_prefs";
    private static final String KEY_DARK = "dark_mode";

    public static void applyTheme(Activity activity) {
        activity.setTheme(isDarkMode(activity)
                ? R.style.Theme_Emegelauncher
                : R.style.Theme_Emegelauncher_Light);
    }

    public static boolean isDarkMode(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_DARK, true);
    }

    public static void setDarkMode(Context context, boolean dark) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DARK, dark).apply();
    }

    public static int resolveColor(Context context, int attrId) {
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(attrId, tv, true);
        return tv.data;
    }

    /** Get accent color adjusted for current theme (darker on light bg) */
    public static int accentBlue(Context c) { return isDarkMode(c) ? 0xFF0A84FF : 0xFF0066CC; }
    public static int accentGreen(Context c) { return isDarkMode(c) ? 0xFF30D158 : 0xFF1B8A3A; }
    public static int accentRed(Context c) { return isDarkMode(c) ? 0xFFFF453A : 0xFFD32F2F; }
    public static int accentOrange(Context c) { return isDarkMode(c) ? 0xFFFF9F0A : 0xFFCC7A00; }
    public static int accentTeal(Context c) { return isDarkMode(c) ? 0xFF64D2FF : 0xFF0077AA; }
    public static int accentPurple(Context c) { return isDarkMode(c) ? 0xFFBF5AF2 : 0xFF7B1FA2; }
}

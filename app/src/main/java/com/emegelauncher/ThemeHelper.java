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
}

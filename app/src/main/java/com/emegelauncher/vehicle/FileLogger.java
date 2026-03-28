/*
 * Emegelauncher - Custom Launcher for MG Marvel R
 * Copyright (C) 2026 Emegelauncher Contributors
 *
 * Licensed under the Apache License, Version 2.0 with the
 * Commons Clause License Condition v1.0 (see LICENSE files).
 *
 * You may NOT sell this software. Donations are welcome.
 */

package com.emegelauncher.vehicle;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * File-based logger that survives logcat buffer eviction.
 * Writes to internal storage with automatic rotation.
 * Only logs what we explicitly write — not system logcat.
 */
public class FileLogger {
    private static final String TAG = "FileLogger";
    private static final String FILENAME = "emegelauncher_debug.log";
    private static final long MAX_SIZE_BYTES = 512 * 1024; // 500 KB (~2 hours)

    private static volatile FileLogger sInstance;
    private final File mLogFile;
    private final SimpleDateFormat mDateFmt = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    public static FileLogger getInstance(Context ctx) {
        if (sInstance == null) {
            synchronized (FileLogger.class) {
                if (sInstance == null) sInstance = new FileLogger(ctx.getApplicationContext());
            }
        }
        return sInstance;
    }

    private FileLogger(Context ctx) {
        mLogFile = new File(ctx.getFilesDir(), FILENAME);
    }

    /** Log a debug message */
    public void d(String tag, String msg) {
        write("D", tag, msg);
    }

    /** Log an info message */
    public void i(String tag, String msg) {
        write("I", tag, msg);
    }

    /** Log a warning */
    public void w(String tag, String msg) {
        write("W", tag, msg);
    }

    /** Log an error */
    public void e(String tag, String msg) {
        write("E", tag, msg);
    }

    private void write(String level, String tag, String msg) {
        final String line = mDateFmt.format(new Date()) + " " + level + "/" + tag + ": " + msg;
        // Write on background thread to avoid blocking main thread
        new Thread(() -> {
            synchronized (mLogFile) {
                try {
                    if (mLogFile.exists() && mLogFile.length() > MAX_SIZE_BYTES) rotate();
                    PrintWriter pw = new PrintWriter(new FileOutputStream(mLogFile, true));
                    pw.println(line);
                    pw.flush();
                    pw.close();
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void rotate() {
        try {
            // Keep last ~half of the file
            if (mLogFile.length() <= MAX_SIZE_BYTES) return;
            byte[] all = new byte[(int) mLogFile.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(mLogFile);
            fis.read(all);
            fis.close();
            // Keep last 250 KB
            int keep = 256 * 1024;
            int start = all.length - keep;
            if (start < 0) start = 0;
            // Find next newline after start
            while (start < all.length && all[start] != '\n') start++;
            if (start < all.length) start++;
            FileOutputStream fos = new FileOutputStream(mLogFile, false);
            fos.write(all, start, all.length - start);
            fos.close();
        } catch (Exception e) {
            // If rotation fails, just truncate
            try { new FileOutputStream(mLogFile, false).close(); } catch (Exception ignored) {}
        }
    }

    /** Get the log file for export */
    public File getLogFile() { return mLogFile; }
}

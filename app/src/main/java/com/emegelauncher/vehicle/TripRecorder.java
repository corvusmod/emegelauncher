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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Records trip telemetry data points and exports to GPX/JSON.
 * Data points: timestamp, lat, lon, alt, speed, power, soc, consumption, g-force.
 */
public class TripRecorder {
    private static final String TAG = "TripRecorder";

    private boolean mRecording = false;
    private long mStartTime = 0;
    private final List<DataPoint> mPoints = new ArrayList<>();
    private final Context mContext;

    // Trip summary
    private float mMaxSpeed = 0;
    private float mTotalDistance = 0; // meters
    private double mConsumptionSum = 0;
    private int mConsumptionCount = 0;

    // Singleton
    private static TripRecorder sInstance;

    public static synchronized TripRecorder getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new TripRecorder(context.getApplicationContext());
        }
        return sInstance;
    }

    public static class DataPoint {
        public long timestamp;
        public double lat, lon, alt;
        public float speed; // km/h
        public float powerKw;
        public float soc;
        public float consumption; // kWh/100km
        public float longG, latG;
    }

    private static final int MAX_STORED_TRIPS = 5;
    private static final String PREFS = "trip_recorder";

    public TripRecorder(Context context) {
        mContext = context;
    }

    public boolean isRecording() { return mRecording; }
    public int getPointCount() { return mPoints.size(); }
    public long getStartTime() { return mStartTime; }
    public float getMaxSpeed() { return mMaxSpeed; }
    public float getTotalDistanceKm() { return mTotalDistance / 1000f; }
    public float getAvgConsumption() { return mConsumptionCount > 0 ? (float)(mConsumptionSum / mConsumptionCount) : 0; }
    public long getDurationMs() { return mRecording ? System.currentTimeMillis() - mStartTime : 0; }

    public void start() {
        mRecording = true;
        mStartTime = System.currentTimeMillis();
        mPoints.clear();
        mMaxSpeed = 0;
        mTotalDistance = 0;
        mConsumptionSum = 0;
        mConsumptionCount = 0;
        Log.i(TAG, "Trip recording started");
    }

    public void stop() {
        mRecording = false;
        Log.i(TAG, "Trip recording stopped. Points: " + mPoints.size()
            + " Distance: " + String.format("%.1f km", getTotalDistanceKm())
            + " MaxSpeed: " + String.format("%.0f km/h", mMaxSpeed));
        // Save to history
        if (mPoints.size() > 0) {
            saveTripToHistory();
        }
    }

    /** Save current trip to internal storage and prune old trips beyond MAX_STORED_TRIPS */
    private void saveTripToHistory() {
        try {
            File tripDir = getTripDir();
            if (tripDir == null) return;

            // Save as JSON
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date(mStartTime));
            File jsonFile = new File(tripDir, "trip_" + ts + ".json");
            exportJsonToFile(jsonFile);

            // Also save GPX
            File gpxFile = new File(tripDir, "trip_" + ts + ".gpx");
            exportGpxToFile(gpxFile);

            // Prune: keep only latest MAX_STORED_TRIPS
            pruneOldTrips(tripDir);

            Log.i(TAG, "Trip saved to history: " + ts);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save trip to history", e);
        }
    }

    private File getTripDir() {
        File dir = new File(mContext.getFilesDir(), "trips");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void pruneOldTrips(File tripDir) {
        File[] files = tripDir.listFiles();
        if (files == null) return;
        // Collect unique trip timestamps (each trip has .json + .gpx)
        java.util.TreeSet<String> tripNames = new java.util.TreeSet<>();
        for (File f : files) {
            String name = f.getName();
            if (name.startsWith("trip_") && (name.endsWith(".json") || name.endsWith(".gpx"))) {
                tripNames.add(name.replaceAll("\\.(json|gpx)$", ""));
            }
        }
        // Remove oldest trips if more than MAX_STORED_TRIPS
        while (tripNames.size() > MAX_STORED_TRIPS) {
            String oldest = tripNames.first();
            tripNames.remove(oldest);
            new File(tripDir, oldest + ".json").delete();
            new File(tripDir, oldest + ".gpx").delete();
            Log.d(TAG, "Pruned old trip: " + oldest);
        }
    }

    /** Get list of stored trips (newest first) */
    public List<TripInfo> getStoredTrips() {
        List<TripInfo> trips = new ArrayList<>();
        File tripDir = getTripDir();
        if (tripDir == null) return trips;
        File[] files = tripDir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return trips;
        java.util.Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName())); // newest first
        for (File f : files) {
            try {
                String content = new java.util.Scanner(f).useDelimiter("\\A").next();
                JSONObject json = new JSONObject(content);
                TripInfo info = new TripInfo();
                info.filename = f.getName().replace(".json", "");
                info.startTime = json.optLong("startTime", 0);
                info.durationMs = json.optLong("duration", 0);
                info.distanceKm = (float) json.optDouble("distanceKm", 0);
                info.maxSpeed = (float) json.optDouble("maxSpeedKmh", 0);
                info.avgConsumption = (float) json.optDouble("avgConsumption", 0);
                info.pointCount = json.optInt("pointCount", 0);
                trips.add(info);
            } catch (Exception e) {
                Log.d(TAG, "Failed to read trip: " + f.getName());
            }
        }
        return trips;
    }

    public static class TripInfo {
        public String filename;
        public long startTime;
        public long durationMs;
        public float distanceKm;
        public float maxSpeed;
        public float avgConsumption;
        public int pointCount;

        public String getSummary() {
            String date = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(new Date(startTime));
            long min = durationMs / 60000;
            return String.format("%s | %.1f km | Max %.0f km/h | %.1f kWh/100km | %dm | %d pts",
                date, distanceKm, maxSpeed, avgConsumption, min, pointCount);
        }
    }

    /** Export a stored trip by filename to a destination */
    public File exportStoredTrip(String tripName, String format, File destination) {
        File tripDir = getTripDir();
        File source = new File(tripDir, tripName + "." + format);
        if (!source.exists()) return null;
        try {
            File dest = new File(destination, source.getName());
            java.io.InputStream is = new java.io.FileInputStream(source);
            java.io.OutputStream os = new java.io.FileOutputStream(dest);
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
            is.close();
            os.close();
            return dest;
        } catch (Exception e) {
            Log.e(TAG, "Export stored trip failed", e);
            return null;
        }
    }

    public void addPoint(double lat, double lon, double alt, float speed,
                          float powerKw, float soc, float consumption,
                          float longG, float latG) {
        if (!mRecording) return;

        DataPoint p = new DataPoint();
        p.timestamp = System.currentTimeMillis();
        p.lat = lat;
        p.lon = lon;
        p.alt = alt;
        p.speed = speed;
        p.powerKw = powerKw;
        p.soc = soc;
        p.consumption = consumption;
        p.longG = longG;
        p.latG = latG;

        // Update stats
        if (speed > mMaxSpeed) mMaxSpeed = speed;
        if (consumption > 0.1f) {
            mConsumptionSum += consumption;
            mConsumptionCount++;
        }

        // Calculate distance from previous point
        if (!mPoints.isEmpty()) {
            DataPoint prev = mPoints.get(mPoints.size() - 1);
            if (prev.lat != 0 && lat != 0) {
                mTotalDistance += haversine(prev.lat, prev.lon, lat, lon);
            }
        }

        mPoints.add(p);
    }

    /** Export current trip to GPX in given directory */
    public File exportGpx(File destination) {
        if (mPoints.isEmpty()) return null;
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date(mStartTime));
        File gpxFile = new File(destination, "trip_" + ts + ".gpx");
        return exportGpxToFile(gpxFile) ? gpxFile : null;
    }

    /** Export to GPX file (standard format for Google Earth, Strava, etc.) */
    private boolean exportGpxToFile(File gpxFile) {
        if (mPoints.isEmpty()) return false;
        try {
            SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            isoFmt.setTimeZone(TimeZone.getTimeZone("UTC"));

            try (PrintWriter pw = new PrintWriter(new FileOutputStream(gpxFile))) {
                pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                pw.println("<gpx version=\"1.1\" creator=\"Emegelauncher\"");
                pw.println("  xmlns=\"http://www.topografix.com/GPX/1/1\">");
                pw.println("  <metadata>");
                pw.println("    <desc>Distance: " + String.format("%.1f km", getTotalDistanceKm())
                    + " | Max: " + String.format("%.0f km/h", mMaxSpeed)
                    + " | Avg: " + String.format("%.1f kWh/100km", getAvgConsumption()) + "</desc>");
                pw.println("    <time>" + isoFmt.format(new Date(mStartTime)) + "</time>");
                pw.println("  </metadata>");
                pw.println("  <trk><name>MG Marvel R Trip</name><trkseg>");

                for (DataPoint p : mPoints) {
                    if (p.lat == 0 && p.lon == 0) continue;
                    pw.println("    <trkpt lat=\"" + p.lat + "\" lon=\"" + p.lon + "\">");
                    pw.println("      <ele>" + p.alt + "</ele>");
                    pw.println("      <time>" + isoFmt.format(new Date(p.timestamp)) + "</time>");
                    pw.println("      <extensions><power>" + p.powerKw + "</power><soc>" + p.soc
                        + "</soc><consumption>" + p.consumption + "</consumption></extensions>");
                    pw.println("    </trkpt>");
                }

                pw.println("  </trkseg></trk>");
                pw.println("</gpx>");
            }
            Log.i(TAG, "GPX saved: " + gpxFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "GPX export failed", e);
            return false;
        }
    }

    /** Export to JSON file with full telemetry */
    /** Export current trip to JSON in given directory */
    public File exportJson(File destination) {
        if (mPoints.isEmpty()) return null;
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date(mStartTime));
        File jsonFile = new File(destination, "trip_" + ts + ".json");
        return exportJsonToFile(jsonFile) ? jsonFile : null;
    }

    private boolean exportJsonToFile(File jsonFile) {
        if (mPoints.isEmpty()) return false;
        try {
            JSONObject trip = new JSONObject();
            trip.put("startTime", mStartTime);
            trip.put("duration", getDurationMs() > 0 ? getDurationMs() : System.currentTimeMillis() - mStartTime);
            trip.put("distanceKm", getTotalDistanceKm());
            trip.put("maxSpeedKmh", mMaxSpeed);
            trip.put("avgConsumption", getAvgConsumption());
            trip.put("pointCount", mPoints.size());

            JSONArray points = new JSONArray();
            for (DataPoint p : mPoints) {
                JSONObject pt = new JSONObject();
                pt.put("t", p.timestamp);
                pt.put("lat", p.lat);
                pt.put("lon", p.lon);
                pt.put("spd", p.speed);
                pt.put("pwr", p.powerKw);
                pt.put("soc", p.soc);
                pt.put("cons", p.consumption);
                points.put(pt);
            }
            trip.put("points", points);

            try (PrintWriter pw = new PrintWriter(new FileOutputStream(jsonFile))) {
                pw.print(trip.toString(2));
            }
            Log.i(TAG, "JSON saved: " + jsonFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "JSON export failed", e);
            return false;
        }
    }

    /** Get trip summary as formatted string */
    public String getSummary() {
        if (mPoints.isEmpty()) return "No data recorded";
        return String.format("%.1f km | Max %.0f km/h | Avg %.1f kWh/100km | %s | %d pts",
            getTotalDistanceKm(), mMaxSpeed, getAvgConsumption(),
            formatDuration(System.currentTimeMillis() - mStartTime), mPoints.size());
    }

    /** Haversine distance in meters */
    private static float haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return (float) (R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    private static String formatDuration(long ms) {
        long sec = ms / 1000;
        long min = sec / 60;
        long hr = min / 60;
        if (hr > 0) return String.format("%dh %dm", hr, min % 60);
        return String.format("%dm %ds", min, sec % 60);
    }
}

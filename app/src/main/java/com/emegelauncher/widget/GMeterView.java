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

package com.emegelauncher.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * G-Meter: circular display showing lateral (X) and longitudinal (Y) G-forces.
 * Center = 0G. Dot moves based on accel/brake (up/down) and cornering (left/right).
 * Includes concentric rings at 0.25G, 0.5G, 0.75G, 1.0G.
 */
public class GMeterView extends View {
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path trailPath = new Path();

    private float lateralG = 0f;   // X: left(-) / right(+)
    private float longitudinalG = 0f; // Y: brake(-) / accel(+)
    private float maxG = 1.0f;

    // Peak tracking
    private float peakAccelG = 0f;   // max positive longitudinal (accel)
    private float peakBrakeG = 0f;   // max negative longitudinal (brake)
    private float peakLeftG = 0f;    // max negative lateral (left)
    private float peakRightG = 0f;   // max positive lateral (right)

    // Trail history
    private final float[] trailX = new float[30];
    private final float[] trailY = new float[30];
    private int trailIndex = 0;
    private int trailCount = 0;

    private int dotColor = 0xFF2979FF;
    private int bgColor = 0xFF1A1A1E;
    private String labelAccel = "ACCEL";
    private String labelBrake = "BRAKE";

    public GMeterView(Context context) { super(context); init(); }
    public GMeterView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(0xFF2C2C2E);
        ringPaint.setStrokeWidth(1f);
        axisPaint.setStyle(Paint.Style.STROKE);
        axisPaint.setColor(0xFF3A3A3C);
        axisPaint.setStrokeWidth(0.5f);
        dotPaint.setStyle(Paint.Style.FILL);
        dotGlowPaint.setStyle(Paint.Style.FILL);
        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setStrokeWidth(2f);
        trailPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFF5F5F7);
        textPaint.setTextSize(24f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(0xFF636366);
        labelPaint.setTextSize(18f);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setValues(float lateral, float longitudinal) {
        this.lateralG = lateral;
        this.longitudinalG = longitudinal;
        // Track peaks
        if (longitudinal > peakAccelG) peakAccelG = longitudinal;
        if (longitudinal < peakBrakeG) peakBrakeG = longitudinal;
        if (lateral < peakLeftG) peakLeftG = lateral;
        if (lateral > peakRightG) peakRightG = lateral;
        // Add to trail
        trailX[trailIndex] = lateral;
        trailY[trailIndex] = longitudinal;
        trailIndex = (trailIndex + 1) % trailX.length;
        if (trailCount < trailX.length) trailCount++;
        invalidate();
    }

    public void resetPeaks() {
        peakAccelG = 0; peakBrakeG = 0; peakLeftG = 0; peakRightG = 0;
        invalidate();
    }

    public void setMaxG(float max) { this.maxG = max; }
    public void setDotColor(int c) { this.dotColor = c; }
    public void setBgColor(int c) { this.bgColor = c; }
    public void setRingColor(int c) { ringPaint.setColor(c); }
    public void setTextColor(int c) { textPaint.setColor(c); }
    public void setLabelColor(int c) { labelPaint.setColor(c); }
    public void setAxisLabels(String accel, String brake) { this.labelAccel = accel; this.labelBrake = brake; }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float textSpace = 60; // space for current G + peak values below circle
        float size = Math.min(w, h - textSpace);
        float cx = w / 2, cy = size / 2 + 10;
        float radius = size / 2 - 20;

        // Background circle
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(bgColor);
        bgPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius + 2, bgPaint);

        // Concentric rings at 0.25, 0.5, 0.75, 1.0 G
        for (int i = 1; i <= 4; i++) {
            float r = radius * i / 4f;
            ringPaint.setStrokeWidth(i == 4 ? 1.5f : 0.8f);
            canvas.drawCircle(cx, cy, r, ringPaint);
        }

        // Cross axes
        canvas.drawLine(cx - radius, cy, cx + radius, cy, axisPaint);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, axisPaint);

        // Axis labels
        labelPaint.setTextSize(14f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(labelAccel, cx, cy - radius + 16, labelPaint);
        canvas.drawText(labelBrake, cx, cy + radius - 6, labelPaint);
        labelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("L", cx - radius + 6, cy - 4, labelPaint);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("R", cx + radius - 6, cy - 4, labelPaint);

        // Ring value labels
        labelPaint.setTextAlign(Paint.Align.LEFT);
        labelPaint.setTextSize(12f);
        for (int i = 1; i <= 4; i++) {
            float r = radius * i / 4f;
            String val = String.format("%.2f", maxG * i / 4f);
            canvas.drawText(val, cx + 4, cy - r + 12, labelPaint);
        }

        // Trail (fading line showing recent G history)
        if (trailCount > 1) {
            for (int i = 1; i < trailCount; i++) {
                int idx1 = (trailIndex - trailCount + i - 1 + trailX.length) % trailX.length;
                int idx2 = (trailIndex - trailCount + i + trailX.length) % trailX.length;
                float x1 = cx + (trailX[idx1] / maxG) * radius;
                float y1 = cy - (trailY[idx1] / maxG) * radius;
                float x2 = cx + (trailX[idx2] / maxG) * radius;
                float y2 = cy - (trailY[idx2] / maxG) * radius;
                int alpha = 20 + (i * 180 / trailCount);
                trailPaint.setColor((dotColor & 0x00FFFFFF) | (alpha << 24));
                canvas.drawLine(x1, y1, x2, y2, trailPaint);
            }
        }

        // Current G-force dot
        float dotX = cx + (lateralG / maxG) * radius;
        float dotY = cy - (longitudinalG / maxG) * radius;
        // Clamp to circle
        float dist = (float) Math.sqrt((dotX - cx) * (dotX - cx) + (dotY - cy) * (dotY - cy));
        if (dist > radius) {
            dotX = cx + (dotX - cx) * radius / dist;
            dotY = cy + (dotY - cy) * radius / dist;
        }

        // Glow
        dotGlowPaint.setColor(dotColor & 0x40FFFFFF);
        dotGlowPaint.setShadowLayer(12, 0, 0, dotColor & 0x80FFFFFF);
        canvas.drawCircle(dotX, dotY, 12, dotGlowPaint);

        // Dot
        dotPaint.setColor(dotColor);
        canvas.drawCircle(dotX, dotY, 7, dotPaint);
        dotPaint.setColor(0xFFFFFFFF);
        canvas.drawCircle(dotX, dotY, 3, dotPaint);

        // Current value + peak values below the circle
        float totalG = (float) Math.sqrt(lateralG * lateralG + longitudinalG * longitudinalG);
        float bottomY = cy + radius + 18;

        // Current G centered
        textPaint.setTextSize(18f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(String.format("%.2f G", totalG), cx, bottomY, textPaint);

        // Peak values below current G
        labelPaint.setTextSize(13f);
        float peakY = bottomY + 18;
        // Longitudinal peaks on the left
        labelPaint.setTextAlign(Paint.Align.LEFT);
        labelPaint.setColor(0xFF30D158); // green for accel
        canvas.drawText(String.format("\u25B2 %.2f G", peakAccelG), cx - radius, peakY, labelPaint);
        labelPaint.setColor(0xFFFF3B30); // red for brake
        canvas.drawText(String.format("\u25BC %.2f G", Math.abs(peakBrakeG)), cx - radius, peakY + 16, labelPaint);
        // Lateral peaks on the right
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        labelPaint.setColor(0xFF2979FF); // blue for lateral
        canvas.drawText(String.format("L %.2f G", Math.abs(peakLeftG)), cx + radius, peakY, labelPaint);
        canvas.drawText(String.format("R %.2f G", peakRightG), cx + radius, peakY + 16, labelPaint);
    }
}

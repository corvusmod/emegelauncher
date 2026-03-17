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
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Top-down car silhouette with pressure + temp at each corner.
 */
public class TireDiagramView extends View {
    private final Paint carPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tirePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tireRect = new RectF();

    private float[] pressures = {0, 0, 0, 0}; // FL, FR, RL, RR
    private float[] temps = {0, 0, 0, 0};

    public TireDiagramView(Context context) { super(context); init(); }
    public TireDiagramView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        carPaint.setColor(0xFF3A3A3C);
        carPaint.setStyle(Paint.Style.STROKE);
        carPaint.setStrokeWidth(3f);
        tirePaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0xFFF5F5F7);
        textPaint.setTextSize(28f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(0xFF8E8E93);
        labelPaint.setTextSize(22f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setPressures(float fl, float fr, float rl, float rr) {
        pressures = new float[]{fl, fr, rl, rr};
        invalidate();
    }

    public void setTemps(float fl, float fr, float rl, float rr) {
        temps = new float[]{fl, fr, rl, rr};
        invalidate();
    }

    public void setTextColor(int c) { textPaint.setColor(c); invalidate(); }
    public void setLabelColor(int c) { labelPaint.setColor(c); invalidate(); }
    public void setCarColor(int c) { carPaint.setColor(c); invalidate(); }

    private int pressureColor(float bar) {
        // MG Marvel R recommended: 2.9 bar
        if (bar < 2.5f) return 0xFFFF453A; // low - red
        if (bar > 3.3f) return 0xFFFF9F0A; // high - orange
        return 0xFF30D158; // normal - green (2.5 - 3.3 bar)
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2, cy = h / 2;
        float carW = w * 0.30f, carH = h * 0.60f;

        // Car body
        RectF carRect = new RectF(cx - carW / 2, cy - carH / 2, cx + carW / 2, cy + carH / 2);
        canvas.drawRoundRect(carRect, carW * 0.3f, carW * 0.2f, carPaint);

        // Tire positions
        float[][] tirePos = {
            {cx - carW / 2 - 30, cy - carH * 0.30f}, // FL
            {cx + carW / 2 + 30, cy - carH * 0.30f}, // FR
            {cx - carW / 2 - 30, cy + carH * 0.30f}, // RL
            {cx + carW / 2 + 30, cy + carH * 0.30f}, // RR
        };
        String[] labels = {"FL", "FR", "RL", "RR"};

        for (int i = 0; i < 4; i++) {
            float tx = tirePos[i][0], ty = tirePos[i][1];
            tirePaint.setColor(pressureColor(pressures[i]));
            tireRect.set(tx - 18, ty - 36, tx + 18, ty + 36);
            canvas.drawRoundRect(tireRect, 8, 8, tirePaint);

            // Pressure text
            boolean isLeft = (i == 0 || i == 2);
            float textX = isLeft ? tx - 60 : tx + 60;
            textPaint.setTextAlign(isLeft ? Paint.Align.RIGHT : Paint.Align.LEFT);
            canvas.drawText(String.format("%.1f", pressures[i]), textX, ty - 4, textPaint);

            // Temp text
            labelPaint.setTextAlign(isLeft ? Paint.Align.RIGHT : Paint.Align.LEFT);
            canvas.drawText(String.format("%.0f°C", temps[i]), textX, ty + 24, labelPaint);
        }

        // Title
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(24f);
        canvas.drawText("TIRE PRESSURE (bar) / TEMP", cx, cy - carH / 2 - 40, textPaint);

        // Legend
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(18f);
        int savedColor = labelPaint.getColor();

        labelPaint.setColor(0xFF30D158);
        canvas.drawText("Normal: 2.5-3.3", cx - w * 0.25f, cy + carH / 2 + 50, labelPaint);
        labelPaint.setColor(0xFFFF453A);
        canvas.drawText("Low: <2.5", cx, cy + carH / 2 + 50, labelPaint);
        labelPaint.setColor(0xFFFF9F0A);
        canvas.drawText("High: >3.3", cx + w * 0.25f, cy + carH / 2 + 50, labelPaint);

        labelPaint.setColor(savedColor);
        textPaint.setTextSize(28f);
    }
}

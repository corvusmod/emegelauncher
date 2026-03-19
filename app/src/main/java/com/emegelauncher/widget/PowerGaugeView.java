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
import android.view.View;

/**
 * Power gauge with 0 at top, positive (consumption) sweeps right, negative (regen) sweeps left.
 * Full arc = 270°, center (0 kW) at top (135° from start).
 * Range: -maxKw (full left) to +maxKw (full right).
 */
public class PowerGaugeView extends View {
    private float value = 0f;
    private float maxKw = 150f;
    private String label = "Power";
    private String unit = "kW";

    private final Paint bgArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fgArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint regenArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zeroMarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private static final float START_ANGLE = 135f;  // arc starts at bottom-left
    private static final float SWEEP_TOTAL = 270f;  // total arc span
    private static final float ZERO_ANGLE = START_ANGLE + SWEEP_TOTAL / 2f; // 0 kW at top (270°)

    private int consumeColor = 0xFFFF9F0A; // orange for consumption
    private int regenColor = 0xFF30D158;    // green for regen

    public PowerGaugeView(Context context) {
        super(context);
        init();
    }

    private void init() {
        bgArcPaint.setStyle(Paint.Style.STROKE);
        bgArcPaint.setStrokeWidth(12);
        bgArcPaint.setStrokeCap(Paint.Cap.ROUND);
        bgArcPaint.setColor(0x33FFFFFF);

        fgArcPaint.setStyle(Paint.Style.STROKE);
        fgArcPaint.setStrokeWidth(14);
        fgArcPaint.setStrokeCap(Paint.Cap.ROUND);
        fgArcPaint.setColor(consumeColor);

        regenArcPaint.setStyle(Paint.Style.STROKE);
        regenArcPaint.setStrokeWidth(14);
        regenArcPaint.setStrokeCap(Paint.Cap.ROUND);
        regenArcPaint.setColor(regenColor);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(28);
        textPaint.setColor(0xFFFFFFFF);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(12);
        labelPaint.setColor(0xFF8E8E93);

        zeroMarkPaint.setStyle(Paint.Style.STROKE);
        zeroMarkPaint.setStrokeWidth(3);
        zeroMarkPaint.setColor(0x88FFFFFF);
    }

    public void setValue(float kw) {
        this.value = Math.max(-maxKw, Math.min(maxKw, kw));
        invalidate();
    }

    public void setMaxKw(float max) { this.maxKw = max; }
    public void setLabel(String l) { this.label = l; }
    public void setUnit(String u) { this.unit = u; }
    public void setConsumeColor(int c) { consumeColor = c; fgArcPaint.setColor(c); }
    public void setRegenColor(int c) { regenColor = c; regenArcPaint.setColor(c); }
    public void setBgColor(int c) { bgArcPaint.setColor(darken(c, 0.3f)); }
    public void setTextColor(int c) { textPaint.setColor(c); }
    public void setLabelColor(int c) { labelPaint.setColor(c); }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float pad = 20;
        float size = Math.min(w, h) - pad * 2;
        float cx = w / 2f;
        float cy = h / 2f;
        arcRect.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);

        // Background arc
        canvas.drawArc(arcRect, START_ANGLE, SWEEP_TOTAL, false, bgArcPaint);

        // Zero mark at top
        float zeroRad = (float) Math.toRadians(ZERO_ANGLE);
        float markR1 = size / 2 - 20;
        float markR2 = size / 2 + 2;
        canvas.drawLine(
            cx + markR1 * (float) Math.cos(zeroRad), cy + markR1 * (float) Math.sin(zeroRad),
            cx + markR2 * (float) Math.cos(zeroRad), cy + markR2 * (float) Math.sin(zeroRad),
            zeroMarkPaint);

        // Value arc: from zero point, sweep proportional to value
        float halfSweep = SWEEP_TOTAL / 2f;
        float ratio = value / maxKw; // -1 to +1

        if (value > 0.1f) {
            // Consumption: sweep right from zero
            float sweep = ratio * halfSweep;
            fgArcPaint.setColor(consumeColor);
            canvas.drawArc(arcRect, ZERO_ANGLE, sweep, false, fgArcPaint);
        } else if (value < -0.1f) {
            // Regen: sweep left from zero (negative sweep)
            float sweep = ratio * halfSweep; // negative
            regenArcPaint.setColor(regenColor);
            canvas.drawArc(arcRect, ZERO_ANGLE + sweep, -sweep, false, regenArcPaint);
        }

        // Value text
        String valStr = String.format("%.1f", value);
        textPaint.setTextSize(Math.min(w, h) * 0.16f);
        canvas.drawText(valStr, cx, cy + textPaint.getTextSize() * 0.15f, textPaint);

        // Unit
        labelPaint.setTextSize(Math.min(w, h) * 0.09f);
        canvas.drawText(unit, cx, cy + textPaint.getTextSize() * 0.8f, labelPaint);

        // Label at bottom
        canvas.drawText(label, cx, cy + size / 2 - 4, labelPaint);

        // Min/Max labels
        labelPaint.setTextSize(Math.min(w, h) * 0.07f);
        float lblY = cy + size / 2 * 0.7f;
        labelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("-" + (int) maxKw, pad + 4, lblY, labelPaint);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("+" + (int) maxKw, w - pad - 4, lblY, labelPaint);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    private static int darken(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * (1 - factor));
        int g = (int) (((color >> 8) & 0xFF) * (1 - factor));
        int b = (int) ((color & 0xFF) * (1 - factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

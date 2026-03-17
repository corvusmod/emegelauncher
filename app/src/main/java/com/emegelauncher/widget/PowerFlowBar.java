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
 * Horizontal bar showing power flow: left = regen (green), right = discharge (red).
 * Center = zero. Value can be negative (regen) or positive (discharge).
 */
public class PowerFlowBar extends View {
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();

    private float value = 0f;
    private float maxValue = 200f; // max amps either direction
    private String label = "Power Flow";

    private int regenColor = 0xFF30D158;
    private int dischargeColor = 0xFFFF453A;

    public PowerFlowBar(Context context) { super(context); init(); }
    public PowerFlowBar(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        bgPaint.setColor(0xFF2C2C2E);
        centerPaint.setColor(0xFF636366);
        centerPaint.setStrokeWidth(2f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFF5F5F7);
        textPaint.setTextSize(28f);
        labelPaint.setColor(0xFF8E8E93);
        labelPaint.setTextSize(24f);
    }

    public void setValue(float v) { this.value = Math.max(-maxValue, Math.min(v, maxValue)); invalidate(); }
    public void setMaxValue(float m) { this.maxValue = m; invalidate(); }
    public void setLabel(String l) { this.label = l; invalidate(); }
    public void setBgColor(int c) { bgPaint.setColor(c); invalidate(); }
    public void setTextColor(int c) { textPaint.setColor(c); invalidate(); }
    public void setLabelColor(int c) { labelPaint.setColor(c); invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float barTop = h * 0.45f, barBottom = h * 0.75f;
        float barH = barBottom - barTop;
        float pad = 12f;

        // Label
        canvas.drawText(label, pad, h * 0.25f, labelPaint);

        // Value
        String valStr = String.format("%.1f A", value);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(valStr, w - pad, h * 0.25f, textPaint);

        // Background bar
        barRect.set(pad, barTop, w - pad, barBottom);
        canvas.drawRoundRect(barRect, barH / 2, barH / 2, bgPaint);

        // Center line
        float center = w / 2;
        canvas.drawLine(center, barTop - 4, center, barBottom + 4, centerPaint);

        // Fill bar
        float ratio = value / maxValue;
        float barW = (w - 2 * pad) / 2 * Math.abs(ratio);
        if (value < 0) { // regen
            barPaint.setColor(regenColor);
            barRect.set(center - barW, barTop + 2, center, barBottom - 2);
        } else { // discharge
            barPaint.setColor(dischargeColor);
            barRect.set(center, barTop + 2, center + barW, barBottom - 2);
        }
        if (barW > 0) canvas.drawRoundRect(barRect, barH / 2, barH / 2, barPaint);

        // Labels
        labelPaint.setTextSize(20f);
        canvas.drawText("REGEN", pad + 40, barBottom + 30, labelPaint);
        float tw = labelPaint.measureText("DISCHARGE");
        canvas.drawText("DISCHARGE", w - pad - tw, barBottom + 30, labelPaint);
    }
}

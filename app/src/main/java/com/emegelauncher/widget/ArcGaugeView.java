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

public class ArcGaugeView extends View {
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private float value = 0f;
    private float maxValue = 100f;
    private String unit = "%";
    private String label = "";
    private int fgColor = 0xFF0A84FF;

    public ArcGaugeView(Context context) { super(context); init(); }
    public ArcGaugeView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setColor(0xFF2C2C2E);
        fgPaint.setStyle(Paint.Style.STROKE);
        fgPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFF5F5F7);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(0xFF8E8E93);
    }

    public void setValue(float val) { this.value = Math.max(0, Math.min(val, maxValue)); invalidate(); }
    public void setMaxValue(float max) { this.maxValue = max; invalidate(); }
    public void setUnit(String u) { this.unit = u; invalidate(); }
    public void setLabel(String l) { this.label = l; invalidate(); }
    public void setFgColor(int c) { this.fgColor = c; fgPaint.setColor(c); invalidate(); }
    public void setBgArcColor(int c) { bgPaint.setColor(c); invalidate(); }
    public void setTextColor(int c) { textPaint.setColor(c); invalidate(); }
    public void setLabelColor(int c) { labelPaint.setColor(c); invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float size = Math.min(w, h);
        float strokeW = size * 0.08f;
        bgPaint.setStrokeWidth(strokeW);
        fgPaint.setStrokeWidth(strokeW);
        fgPaint.setColor(fgColor);

        float pad = strokeW + 4;
        arcRect.set(pad, pad, w - pad, h - pad);

        float startAngle = 135f;
        float sweepTotal = 270f;
        canvas.drawArc(arcRect, startAngle, sweepTotal, false, bgPaint);

        float ratio = maxValue > 0 ? value / maxValue : 0;
        canvas.drawArc(arcRect, startAngle, sweepTotal * ratio, false, fgPaint);

        textPaint.setTextSize(size * 0.22f);
        String valStr;
        if (value == (int) value) valStr = String.valueOf((int) value);
        else valStr = String.format("%.1f", value);
        canvas.drawText(valStr, w / 2, h / 2 + size * 0.04f, textPaint);

        labelPaint.setTextSize(size * 0.10f);
        canvas.drawText(unit, w / 2, h / 2 + size * 0.18f, labelPaint);

        if (!label.isEmpty()) {
            labelPaint.setTextSize(size * 0.09f);
            canvas.drawText(label, w / 2, h * 0.15f, labelPaint);
        }
    }
}

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

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class PowerFlowBar extends View {
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();

    private float value = 0f;
    private float displayValue = 0f;
    private float maxValue = 200f;
    private String label = "Power Flow";

    private static final int REGEN_COLOR = 0xFF30D158;
    private static final int DISCHARGE_COLOR = 0xFFFF453A;

    public PowerFlowBar(Context context) { super(context); init(); }
    public PowerFlowBar(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        bgPaint.setColor(0xFF1C1C1E);
        centerPaint.setColor(0xFF636366);
        centerPaint.setStrokeWidth(1.5f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFF5F5F7);
        textPaint.setTextSize(28f);
        textPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        labelPaint.setColor(0xFF8E8E93);
        labelPaint.setTextSize(22f);
        labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        glowPaint.setStyle(Paint.Style.FILL);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setValue(float v) {
        float newVal = Math.max(-maxValue, Math.min(v, maxValue));
        if (newVal != this.value) {
            float old = this.displayValue;
            this.value = newVal;
            ValueAnimator anim = ValueAnimator.ofFloat(old, newVal);
            anim.setDuration(350);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.addUpdateListener(a -> { displayValue = (float) a.getAnimatedValue(); invalidate(); });
            anim.start();
        }
    }
    public void setMaxValue(float m) { this.maxValue = m; }
    public void setLabel(String l) { this.label = l; }
    public void setBgColor(int c) { bgPaint.setColor(c); }
    public void setTextColor(int c) { textPaint.setColor(c); }
    public void setLabelColor(int c) { labelPaint.setColor(c); }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float barTop = h * 0.40f, barBottom = h * 0.65f;
        float barH = barBottom - barTop;
        float pad = 16f;
        float center = w / 2;

        // Label
        labelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(label, pad, h * 0.22f, labelPaint);

        // Value
        String valStr = String.format("%.1f A", displayValue);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(26f);
        int valColor = displayValue < 0 ? REGEN_COLOR : (displayValue > 1 ? DISCHARGE_COLOR : 0xFFF5F5F7);
        textPaint.setColor(valColor);
        canvas.drawText(valStr, w - pad, h * 0.22f, textPaint);
        textPaint.setColor(0xFFF5F5F7);

        // Background bar with rounded ends
        barRect.set(pad, barTop, w - pad, barBottom);
        canvas.drawRoundRect(barRect, barH / 2, barH / 2, bgPaint);

        // Center line
        canvas.drawLine(center, barTop - 3, center, barBottom + 3, centerPaint);

        // Fill bar with gradient
        float ratio = displayValue / maxValue;
        float barW = (w - 2 * pad) / 2 * Math.abs(ratio);

        if (barW > 1) {
            int color = displayValue < 0 ? REGEN_COLOR : DISCHARGE_COLOR;
            float barLeft, barRight;
            if (displayValue < 0) {
                barLeft = center - barW;
                barRight = center;
                barPaint.setShader(new LinearGradient(barLeft, 0, barRight, 0,
                    color & 0x80FFFFFF, color, Shader.TileMode.CLAMP));
            } else {
                barLeft = center;
                barRight = center + barW;
                barPaint.setShader(new LinearGradient(barLeft, 0, barRight, 0,
                    color, color & 0x80FFFFFF, Shader.TileMode.CLAMP));
            }

            // Glow
            glowPaint.setColor(color & 0x20FFFFFF);
            glowPaint.setShadowLayer(8, 0, 0, color & 0x40FFFFFF);
            barRect.set(barLeft, barTop + 1, barRight, barBottom - 1);
            canvas.drawRoundRect(barRect, barH / 2, barH / 2, glowPaint);

            // Bar
            canvas.drawRoundRect(barRect, barH / 2, barH / 2, barPaint);
            barPaint.setShader(null);
        }

        // Labels
        labelPaint.setTextSize(18f);
        labelPaint.setTextAlign(Paint.Align.LEFT);
        labelPaint.setColor(REGEN_COLOR & 0x80FFFFFF);
        canvas.drawText("REGEN", pad + 8, barBottom + 24, labelPaint);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        labelPaint.setColor(DISCHARGE_COLOR & 0x80FFFFFF);
        canvas.drawText("DISCHARGE", w - pad - 8, barBottom + 24, labelPaint);
        labelPaint.setColor(0xFF8E8E93);

        // Scale marks
        Paint scalePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scalePaint.setColor(0xFF3A3A3C);
        scalePaint.setStrokeWidth(1f);
        for (int i = 1; i <= 3; i++) {
            float x1 = center - (w - 2 * pad) / 2 * i / 4;
            float x2 = center + (w - 2 * pad) / 2 * i / 4;
            canvas.drawLine(x1, barTop - 2, x1, barTop + 3, scalePaint);
            canvas.drawLine(x2, barTop - 2, x2, barTop + 3, scalePaint);
        }
    }
}

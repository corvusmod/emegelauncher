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
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class ArcGaugeView extends View {
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private final RectF glowRect = new RectF();

    private float value = 0f;
    private float displayValue = 0f;
    private float maxValue = 100f;
    private String unit = "%";
    private String label = "";
    private String label2 = "";
    private int label2Color = 0xFF636366;
    private int fgColor = 0xFF2979FF;
    private int fgColorEnd = 0xFF30D158;
    private boolean useGradient = true;
    // Secondary value (optional second dot on the arc)
    private float secondaryValue = -1f;
    private int secondaryColor = 0xFF64D2FF;
    private final Paint secondaryDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ArcGaugeView(Context context) { super(context); init(); }
    public ArcGaugeView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setColor(0xFF1C1C1E);
        bgPaint.setStrokeCap(Paint.Cap.ROUND);
        fgPaint.setStyle(Paint.Style.STROKE);
        fgPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFF5F5F7);
        textPaint.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(0xFF8E8E93);
        labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        tickPaint.setColor(0xFF3A3A3C);
        tickPaint.setStrokeWidth(1.5f);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setValue(float val) {
        float newVal = Math.max(0, Math.min(val, maxValue));
        if (newVal != this.value) {
            float old = this.displayValue;
            this.value = newVal;
            ValueAnimator anim = ValueAnimator.ofFloat(old, newVal);
            anim.setDuration(400);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.addUpdateListener(a -> { displayValue = (float) a.getAnimatedValue(); invalidate(); });
            anim.start();
        }
    }
    public void setMaxValue(float max) { this.maxValue = max; }
    public void setUnit(String u) { this.unit = u; }
    public void setLabel(String l) { this.label = l; }
    public void setLabel2(String l, int color) { this.label2 = l; this.label2Color = color; }
    public void setFgColor(int c) { this.fgColor = c; this.fgColorEnd = lightenColor(c, 0.4f); }
    public void setBgArcColor(int c) { bgPaint.setColor(darkenColor(c, 0.15f)); }
    public void setTextColor(int c) { textPaint.setColor(c); }
    public void setLabelColor(int c) { labelPaint.setColor(c); }
    /** Set a secondary value shown as a second dot on the arc (e.g. average alongside instant) */
    public void setSecondaryValue(float val) { this.secondaryValue = Math.min(val, maxValue); invalidate(); }
    public void setSecondaryColor(int c) { this.secondaryColor = c; }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float size = Math.min(w, h);
        float strokeW = size * 0.06f;
        float glowStrokeW = strokeW + 8;

        bgPaint.setStrokeWidth(strokeW);
        fgPaint.setStrokeWidth(strokeW);
        glowPaint.setStrokeWidth(glowStrokeW);

        float pad = glowStrokeW + 6;
        // Force square arc area, shifted up to leave room for label below
        float labelSpace = size * 0.14f;
        float arcSize = size - pad * 2 - labelSpace;
        float ox = (w - arcSize) / 2;
        float oy = (h - arcSize - labelSpace) / 2;
        arcRect.set(ox, oy, ox + arcSize, oy + arcSize);
        glowRect.set(ox - 2, oy - 2, ox + arcSize + 2, oy + arcSize + 2);

        float startAngle = 135f;
        float sweepTotal = 270f;
        float ratio = maxValue > 0 ? displayValue / maxValue : 0;
        float sweepFg = sweepTotal * ratio;

        // Background arc
        canvas.drawArc(arcRect, startAngle, sweepTotal, false, bgPaint);

        // Tick marks (use arc center, not view center)
        int numTicks = 20;
        float cx = ox + arcSize / 2, cy = oy + arcSize / 2;
        float tickInner = arcSize / 2 - strokeW / 2 - 8;
        float tickOuter = tickInner + 6;
        for (int i = 0; i <= numTicks; i++) {
            float angle = (float) Math.toRadians(startAngle + sweepTotal * i / numTicks);
            float x1 = cx + tickInner * (float) Math.cos(angle);
            float y1 = cy + tickInner * (float) Math.sin(angle);
            float x2 = cx + tickOuter * (float) Math.cos(angle);
            float y2 = cy + tickOuter * (float) Math.sin(angle);
            tickPaint.setStrokeWidth(i % 5 == 0 ? 2f : 1f);
            tickPaint.setColor(i % 5 == 0 ? 0xFF636366 : 0xFF3A3A3C);
            canvas.drawLine(x1, y1, x2, y2, tickPaint);
        }

        if (sweepFg > 0) {
            // Glow effect
            glowPaint.setColor(fgColor & 0x30FFFFFF);
            glowPaint.setShadowLayer(12, 0, 0, fgColor & 0x60FFFFFF);
            canvas.drawArc(glowRect, startAngle, sweepFg, false, glowPaint);

            // Gradient arc
            if (useGradient) {
                fgPaint.setShader(new SweepGradient(cx, cy, new int[]{fgColor, fgColorEnd, fgColor}, null));
            } else {
                fgPaint.setColor(fgColor);
                fgPaint.setShader(null);
            }
            fgPaint.setShadowLayer(6, 0, 0, fgColor & 0x80FFFFFF);
            canvas.drawArc(arcRect, startAngle, sweepFg, false, fgPaint);

            // End dot
            float endAngle = (float) Math.toRadians(startAngle + sweepFg);
            float dotR = arcSize / 2;
            float dotX = cx + dotR * (float) Math.cos(endAngle);
            float dotY = cy + dotR * (float) Math.sin(endAngle);
            Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dotPaint.setColor(0xFFFFFFFF);
            dotPaint.setShadowLayer(8, 0, 0, fgColor);
            canvas.drawCircle(dotX, dotY, strokeW * 0.6f, dotPaint);
        }

        // Secondary dot (e.g. average value)
        if (secondaryValue >= 0 && maxValue > 0) {
            float secRatio = secondaryValue / maxValue;
            float secSweep = sweepTotal * secRatio;
            float secAngle = (float) Math.toRadians(startAngle + secSweep);
            float secR = arcSize / 2;
            float secX = cx + secR * (float) Math.cos(secAngle);
            float secY = cy + secR * (float) Math.sin(secAngle);
            secondaryDotPaint.setColor(secondaryColor);
            secondaryDotPaint.setShadowLayer(6, 0, 0, secondaryColor);
            canvas.drawCircle(secX, secY, strokeW * 0.5f, secondaryDotPaint);
        }

        // Value text
        textPaint.setTextSize(size * 0.24f);
        String valStr;
        if (displayValue == (int) displayValue) valStr = String.valueOf((int) displayValue);
        else valStr = String.format("%.1f", displayValue);
        canvas.drawText(valStr, cx, cy + size * 0.06f, textPaint);

        // Unit
        labelPaint.setTextSize(size * 0.09f);
        canvas.drawText(unit, cx, cy + size * 0.18f, labelPaint);

        // Label (just below the arc)
        if (!label.isEmpty()) {
            labelPaint.setTextSize(size * 0.1f);
            labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            float labelY = oy + arcSize + labelPaint.getTextSize() + 6;
            canvas.drawText(label, cx, labelY, labelPaint);
            labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            // Second label line (different color)
            if (!label2.isEmpty()) {
                int savedColor = labelPaint.getColor();
                labelPaint.setColor(label2Color);
                canvas.drawText(label2, cx, labelY + labelPaint.getTextSize() + 2, labelPaint);
                labelPaint.setColor(savedColor);
            }
        }
    }

    private static int lightenColor(int color, float factor) {
        int r = Math.min(255, (int) (Color.red(color) + (255 - Color.red(color)) * factor));
        int g = Math.min(255, (int) (Color.green(color) + (255 - Color.green(color)) * factor));
        int b = Math.min(255, (int) (Color.blue(color) + (255 - Color.blue(color)) * factor));
        return Color.argb(Color.alpha(color), r, g, b);
    }

    private static int darkenColor(int color, float factor) {
        return Color.argb(Color.alpha(color),
            (int) (Color.red(color) * (1 - factor)),
            (int) (Color.green(color) * (1 - factor)),
            (int) (Color.blue(color) * (1 - factor)));
    }
}

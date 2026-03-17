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
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class LineChartView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint minMaxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    private final List<Float> dataPoints = new ArrayList<>();
    private int maxPoints = 60;
    private float minVal = Float.MAX_VALUE;
    private float maxVal = Float.MIN_VALUE;
    private String label = "";
    private String unit = "";
    private int lineColor = 0xFF0A84FF;

    public LineChartView(Context context) { super(context); init(); }
    public LineChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.5f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        gridPaint.setColor(0xFF2C2C2E);
        gridPaint.setStrokeWidth(0.5f);
        gridPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{4, 6}, 0));
        textPaint.setColor(0xFF8E8E93);
        textPaint.setTextSize(22f);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        dotPaint.setStyle(Paint.Style.FILL);
        dotGlowPaint.setStyle(Paint.Style.FILL);
        minMaxPaint.setTextSize(18f);
        minMaxPaint.setColor(0xFF636366);
        minMaxPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setLineColor(int c) { lineColor = c; linePaint.setColor(c); }
    public void setLabel(String l) { this.label = l; }
    public void setUnit(String u) { this.unit = u; }
    public void setMaxPoints(int n) { this.maxPoints = n; }
    public void setGridColor(int c) { gridPaint.setColor(c); }
    public void setTextColor(int c) { textPaint.setColor(c); }

    public void addPoint(float val) {
        dataPoints.add(val);
        if (dataPoints.size() > maxPoints) dataPoints.remove(0);
        minVal = Float.MAX_VALUE;
        maxVal = Float.MIN_VALUE;
        for (float v : dataPoints) {
            if (v < minVal) minVal = v;
            if (v > maxVal) maxVal = v;
        }
        if (maxVal == minVal) { maxVal = minVal + 1; }
        invalidate();
    }

    public void clear() { dataPoints.clear(); minVal = Float.MAX_VALUE; maxVal = Float.MIN_VALUE; invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float top = 32f, bottom = h - 6f, left = 8f, right = w - 8f;
        float chartH = bottom - top;
        float chartW = right - left;

        linePaint.setColor(lineColor);

        // Grid lines (dashed)
        for (int i = 0; i <= 4; i++) {
            float y = top + chartH * i / 4f;
            canvas.drawLine(left, y, right, y, gridPaint);
        }

        // Label
        if (!label.isEmpty()) {
            textPaint.setTextSize(22f);
            textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            canvas.drawText(label, left + 4, top - 8, textPaint);
            textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }

        // Current value
        if (!dataPoints.isEmpty()) {
            float last = dataPoints.get(dataPoints.size() - 1);
            String valTxt;
            if (last == (int) last) valTxt = (int) last + unit;
            else valTxt = String.format("%.1f", last) + unit;
            textPaint.setTextSize(22f);
            float tw = textPaint.measureText(valTxt);
            textPaint.setColor(lineColor);
            canvas.drawText(valTxt, right - tw, top - 8, textPaint);
            textPaint.setColor(0xFF8E8E93);
        }

        if (dataPoints.size() < 2) return;

        int n = dataPoints.size();
        float step = chartW / (maxPoints - 1);
        float startX = right - (n - 1) * step;

        // Build cubic bezier path for smooth curves
        float[] xPoints = new float[n];
        float[] yPoints = new float[n];
        for (int i = 0; i < n; i++) {
            xPoints[i] = startX + i * step;
            float ratio = (dataPoints.get(i) - minVal) / (maxVal - minVal);
            yPoints[i] = bottom - ratio * chartH;
        }

        linePath.reset();
        fillPath.reset();
        linePath.moveTo(xPoints[0], yPoints[0]);
        fillPath.moveTo(xPoints[0], bottom);
        fillPath.lineTo(xPoints[0], yPoints[0]);

        for (int i = 1; i < n; i++) {
            float cp1x = (xPoints[i - 1] + xPoints[i]) / 2;
            float cp1y = yPoints[i - 1];
            float cp2x = cp1x;
            float cp2y = yPoints[i];
            linePath.cubicTo(cp1x, cp1y, cp2x, cp2y, xPoints[i], yPoints[i]);
            fillPath.cubicTo(cp1x, cp1y, cp2x, cp2y, xPoints[i], yPoints[i]);
        }

        fillPath.lineTo(xPoints[n - 1], bottom);
        fillPath.close();

        // Gradient fill
        fillPaint.setShader(new LinearGradient(0, top, 0, bottom,
            lineColor & 0x40FFFFFF, lineColor & 0x05FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fillPaint);

        // Line with subtle glow
        linePaint.setShadowLayer(4, 0, 0, lineColor & 0x60FFFFFF);
        canvas.drawPath(linePath, linePaint);
        linePaint.setShadowLayer(0, 0, 0, 0);

        // Current value dot (last point)
        float lastX = xPoints[n - 1], lastY = yPoints[n - 1];
        dotGlowPaint.setColor(lineColor & 0x40FFFFFF);
        canvas.drawCircle(lastX, lastY, 8, dotGlowPaint);
        dotPaint.setColor(lineColor);
        canvas.drawCircle(lastX, lastY, 4, dotPaint);
        dotPaint.setColor(0xFFFFFFFF);
        canvas.drawCircle(lastX, lastY, 2, dotPaint);

        // Min/max labels on right edge
        minMaxPaint.setTextAlign(Paint.Align.RIGHT);
        minMaxPaint.setTextSize(16f);
        String maxStr = maxVal == (int) maxVal ? String.valueOf((int) maxVal) : String.format("%.1f", maxVal);
        String minStr = minVal == (int) minVal ? String.valueOf((int) minVal) : String.format("%.1f", minVal);
        canvas.drawText(maxStr, right - 2, top + 14, minMaxPaint);
        canvas.drawText(minStr, right - 2, bottom - 4, minMaxPaint);
    }
}

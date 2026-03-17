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

import java.util.ArrayList;
import java.util.List;

public class LineChartView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        linePaint.setStrokeWidth(3f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        gridPaint.setColor(0xFF2C2C2E);
        gridPaint.setStrokeWidth(1f);
        textPaint.setColor(0xFF8E8E93);
        textPaint.setTextSize(28f);
    }

    public void setLineColor(int c) { lineColor = c; linePaint.setColor(c); invalidate(); }
    public void setLabel(String l) { this.label = l; invalidate(); }
    public void setUnit(String u) { this.unit = u; invalidate(); }
    public void setMaxPoints(int n) { this.maxPoints = n; }
    public void setGridColor(int c) { gridPaint.setColor(c); invalidate(); }
    public void setTextColor(int c) { textPaint.setColor(c); invalidate(); }

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
        float top = 36f, bottom = h - 8f, left = 8f, right = w - 8f;
        float chartH = bottom - top;
        float chartW = right - left;

        linePaint.setColor(lineColor);
        fillPaint.setColor((lineColor & 0x00FFFFFF) | 0x20000000);

        // Grid lines
        for (int i = 0; i <= 4; i++) {
            float y = top + chartH * i / 4f;
            canvas.drawLine(left, y, right, y, gridPaint);
        }

        // Label
        if (!label.isEmpty()) {
            textPaint.setTextSize(26f);
            canvas.drawText(label, left + 4, top - 10, textPaint);
        }

        // Current value
        if (!dataPoints.isEmpty()) {
            float last = dataPoints.get(dataPoints.size() - 1);
            String valTxt;
            if (last == (int) last) valTxt = (int) last + unit;
            else valTxt = String.format("%.1f", last) + unit;
            textPaint.setTextSize(26f);
            float tw = textPaint.measureText(valTxt);
            canvas.drawText(valTxt, right - tw, top - 10, textPaint);
        }

        if (dataPoints.size() < 2) return;

        int n = dataPoints.size();
        float step = chartW / (maxPoints - 1);
        float startX = right - (n - 1) * step;

        linePath.reset();
        fillPath.reset();

        for (int i = 0; i < n; i++) {
            float x = startX + i * step;
            float ratio = (dataPoints.get(i) - minVal) / (maxVal - minVal);
            float y = bottom - ratio * chartH;
            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, bottom);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }
        fillPath.lineTo(startX + (n - 1) * step, bottom);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);
    }
}

/*
 * Emegelauncher - Custom Launcher for MG Marvel R
 * Copyright (C) 2026 Emegelauncher Contributors
 *
 * Licensed under the Apache License, Version 2.0 with the
 * Commons Clause License Condition v1.0 (see LICENSE files).
 *
 * You may NOT sell this software. Donations are welcome.
 */

package com.emegelauncher.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import com.emegelauncher.vehicle.ChargingSessionManager.DataPoint;

import java.util.List;

/**
 * Multi-series charging chart: Power (kW), Voltage (V), SOC (%), Efficiency (%)
 * X-axis = time from start (minutes)
 * Left Y-axis = Power (kW) / Voltage (V) — dual scale
 * Right Y-axis = SOC (%) / Efficiency (%)
 */
public class ChargingChartView extends View {

    private static final int C_POWER = 0xFF30D158;     // green
    private static final int C_VOLTAGE = 0xFF26A69A;   // teal
    private static final int C_SOC = 0xFF2979FF;       // blue
    private static final int C_EFFICIENCY = 0xFFFF9500; // orange

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    private List<DataPoint> mPoints;
    private int textColor = 0xFF8E8E93;
    private int gridColor = 0xFF2C2C2E;
    private int bgColor = 0xFF1A1A1E;

    public ChargingChartView(Context context) { super(context); init(); }
    public ChargingChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        gridPaint.setColor(gridColor);
        gridPaint.setStrokeWidth(0.5f);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{4, 6}, 0));
        labelPaint.setColor(textColor);
        labelPaint.setTextSize(11f);
        titlePaint.setColor(textColor);
        titlePaint.setTextSize(14f);
        titlePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        legendPaint.setTextSize(11f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.5f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setTextColor(int c) { textColor = c; labelPaint.setColor(c); titlePaint.setColor(c); }
    public void setGridColor(int c) { gridColor = c; gridPaint.setColor(c); }
    public void setBgColor(int c) { bgColor = c; }

    /** Set data points to display (live or from stored session) */
    public void setData(List<DataPoint> points) {
        mPoints = points;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mPoints == null || mPoints.size() < 2) {
            titlePaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Waiting for charging data...", getWidth() / 2f, getHeight() / 2f, titlePaint);
            return;
        }

        float w = getWidth(), h = getHeight();
        float marginLeft = 50;    // left Y-axis labels
        float marginRight = 50;   // right Y-axis labels
        float marginTop = 30;     // title
        float marginBottom = 40;  // x-axis labels + legend
        float chartL = marginLeft;
        float chartR = w - marginRight;
        float chartT = marginTop;
        float chartB = h - marginBottom;
        float chartW = chartR - chartL;
        float chartH = chartB - chartT;

        // Background
        Paint bgPaint = new Paint();
        bgPaint.setColor(bgColor);
        canvas.drawRect(chartL, chartT, chartR, chartB, bgPaint);

        // Find ranges
        float maxPower = 0, maxVoltage = 0;
        long startTime = mPoints.get(0).timestamp;
        long endTime = mPoints.get(mPoints.size() - 1).timestamp;
        for (DataPoint dp : mPoints) {
            if (dp.powerKw > maxPower) maxPower = dp.powerKw;
            if (dp.voltage > maxVoltage) maxVoltage = dp.voltage;
        }
        // Round up to nice values
        maxPower = ceilNice(maxPower, 10);
        if (maxPower < 10) maxPower = 10;
        maxVoltage = ceilNice(maxVoltage, 50);
        if (maxVoltage < 400) maxVoltage = 400;
        float totalMinutes = (endTime - startTime) / 60000f;
        if (totalMinutes < 1) totalMinutes = 1;

        // Grid lines (5 horizontal)
        int gridLines = 5;
        for (int i = 0; i <= gridLines; i++) {
            float y = chartT + chartH * i / gridLines;
            canvas.drawLine(chartL, y, chartR, y, gridPaint);
        }

        // Left Y-axis labels: Power (kW)
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        labelPaint.setColor(C_POWER);
        labelPaint.setTextSize(10f);
        for (int i = 0; i <= gridLines; i++) {
            float y = chartT + chartH * i / gridLines;
            float val = maxPower * (gridLines - i) / gridLines;
            canvas.drawText(String.format("%.0f", val), chartL - 4, y + 4, labelPaint);
        }
        // Left axis title
        labelPaint.setTextSize(9f);
        canvas.drawText("kW", chartL - 4, chartT - 6, labelPaint);

        // Right Y-axis labels: SOC (%) — 0-100
        labelPaint.setTextAlign(Paint.Align.LEFT);
        labelPaint.setColor(C_SOC);
        labelPaint.setTextSize(10f);
        for (int i = 0; i <= gridLines; i++) {
            float y = chartT + chartH * i / gridLines;
            int val = 100 * (gridLines - i) / gridLines;
            canvas.drawText(val + "%", chartR + 4, y + 4, labelPaint);
        }

        // X-axis labels (time in minutes)
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(textColor);
        labelPaint.setTextSize(10f);
        int xTicks = Math.min(8, (int) totalMinutes);
        if (xTicks < 2) xTicks = 2;
        for (int i = 0; i <= xTicks; i++) {
            float x = chartL + chartW * i / xTicks;
            float mins = totalMinutes * i / xTicks;
            String label;
            if (totalMinutes > 120) label = String.format("%.0fh", mins / 60);
            else label = String.format("%.0f", mins);
            canvas.drawLine(x, chartB, x, chartB + 4, gridPaint);
            canvas.drawText(label, x, chartB + 16, labelPaint);
        }
        // X-axis title
        labelPaint.setTextSize(9f);
        canvas.drawText(totalMinutes > 120 ? "hours" : "min", chartR, chartB + 16, labelPaint);

        // Draw lines: Power, Voltage, SOC, Efficiency
        drawSeries(canvas, chartL, chartT, chartW, chartH, startTime, totalMinutes, maxPower, C_POWER, true,
            dp -> dp.powerKw);
        drawSeries(canvas, chartL, chartT, chartW, chartH, startTime, totalMinutes, maxVoltage, C_VOLTAGE, false,
            dp -> dp.voltage);
        drawSeries(canvas, chartL, chartT, chartW, chartH, startTime, totalMinutes, 100f, C_SOC, false,
            dp -> dp.socDisplay);
        drawSeries(canvas, chartL, chartT, chartW, chartH, startTime, totalMinutes, 100f, C_EFFICIENCY, false,
            dp -> dp.efficiency);

        // Legend
        float legendY = h - 4;
        float legendX = chartL;
        legendPaint.setTextSize(11f);
        String[][] legend = {
            {"Power (kW)", String.format("#%06X", C_POWER & 0xFFFFFF)},
            {"Voltage (V)", String.format("#%06X", C_VOLTAGE & 0xFFFFFF)},
            {"SOC (%)", String.format("#%06X", C_SOC & 0xFFFFFF)},
            {"Efficiency (%)", String.format("#%06X", C_EFFICIENCY & 0xFFFFFF)},
        };
        int[] legendColors = {C_POWER, C_VOLTAGE, C_SOC, C_EFFICIENCY};
        for (int i = 0; i < legend.length; i++) {
            legendPaint.setColor(legendColors[i]);
            canvas.drawRect(legendX, legendY - 8, legendX + 12, legendY, legendPaint);
            legendPaint.setColor(textColor);
            legendPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(legend[i][0], legendX + 16, legendY, legendPaint);
            legendX += legendPaint.measureText(legend[i][0]) + 28;
        }
    }

    private interface ValueExtractor {
        float get(DataPoint dp);
    }

    private void drawSeries(Canvas canvas, float chartL, float chartT, float chartW, float chartH,
                            long startTime, float totalMinutes, float maxVal, int color,
                            boolean drawFill, ValueExtractor extractor) {
        if (mPoints == null || mPoints.size() < 2 || maxVal == 0) return;

        linePath.reset();
        if (drawFill) fillPath.reset();
        boolean first = true;

        for (int i = 0; i < mPoints.size(); i++) {
            DataPoint dp = mPoints.get(i);
            float x = chartL + ((dp.timestamp - startTime) / 60000f / totalMinutes) * chartW;
            float val = extractor.get(dp);
            float y = chartT + chartH - (val / maxVal) * chartH;
            y = Math.max(chartT, Math.min(chartT + chartH, y));

            if (first) {
                linePath.moveTo(x, y);
                if (drawFill) { fillPath.moveTo(x, chartT + chartH); fillPath.lineTo(x, y); }
                first = false;
            } else {
                linePath.lineTo(x, y);
                if (drawFill) fillPath.lineTo(x, y);
            }
        }

        if (drawFill) {
            DataPoint last = mPoints.get(mPoints.size() - 1);
            float lastX = chartL + ((last.timestamp - startTime) / 60000f / totalMinutes) * chartW;
            fillPath.lineTo(lastX, chartT + chartH);
            fillPath.close();
            fillPaint.setShader(new LinearGradient(0, chartT, 0, chartT + chartH,
                color & 0x30FFFFFF, color & 0x05FFFFFF, Shader.TileMode.CLAMP));
            canvas.drawPath(fillPath, fillPaint);
            fillPaint.setShader(null);
        }

        linePaint.setColor(color);
        canvas.drawPath(linePath, linePaint);
    }

    private float ceilNice(float val, float step) {
        return (float) (Math.ceil(val / step) * step);
    }
}

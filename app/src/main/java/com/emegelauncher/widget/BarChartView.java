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
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Bar chart with x-axis date labels, y-axis values, and optional summary row.
 * Designed for cloud statistics display (daily/monthly/yearly data).
 */
public class BarChartView extends View {
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint summaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();

    private final List<Float> values = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private float maxVal = 0;
    private String title = "";
    private String unit = "";
    private String summary = "";
    private int barColor = 0xFF2979FF;
    private int textColor = 0xFF8E8E93;
    private int gridColor = 0xFF2C2C2E;
    private int bgColor = 0xFF1A1A1E;
    private int highlightIndex = -1; // index of highlighted bar (e.g., today)

    public BarChartView(Context context) { super(context); init(); }
    public BarChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        barPaint.setStyle(Paint.Style.FILL);
        gridPaint.setColor(gridColor);
        gridPaint.setStrokeWidth(0.5f);
        textPaint.setColor(textColor);
        textPaint.setTextSize(11f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(textColor);
        labelPaint.setTextSize(11f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setColor(textColor);
        valuePaint.setTextSize(10f);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setTextSize(15f);
        titlePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        summaryPaint.setTextSize(12f);
    }

    public void setTitle(String t) { this.title = t; }
    public void setUnit(String u) { this.unit = u; }
    public void setSummary(String s) { this.summary = s; }
    public void setBarColor(int c) { this.barColor = c; }
    public void setTextColor(int c) { this.textColor = c; textPaint.setColor(c); labelPaint.setColor(c); valuePaint.setColor(c); }
    public void setGridColor(int c) { this.gridColor = c; gridPaint.setColor(c); }
    public void setBgColor(int c) { this.bgColor = c; }
    public void setHighlightIndex(int i) { this.highlightIndex = i; }

    public void setData(List<Float> vals, List<String> lbls) {
        values.clear();
        labels.clear();
        values.addAll(vals);
        labels.addAll(lbls);
        maxVal = 0;
        for (float v : values) if (v > maxVal) maxVal = v;
        if (maxVal == 0) maxVal = 1;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        if (values.isEmpty()) return;

        float titleH = 28f;
        float summaryH = summary.isEmpty() ? 0 : 20f;
        float xLabelH = 22f; // space for x-axis labels
        float yLabelW = 36f; // space for y-axis values
        float top = titleH + 4;
        float bottom = h - xLabelH - summaryH;
        float left = yLabelW;
        float right = w - 8;
        float chartH = bottom - top;
        float chartW = right - left;

        // Title + unit
        titlePaint.setColor(textColor);
        canvas.drawText(title, left, titleH - 8, titlePaint);
        titlePaint.setColor(barColor);
        float titleW = titlePaint.measureText(title);
        titlePaint.setTextSize(12f);
        canvas.drawText(" " + unit, left + titleW + 4, titleH - 8, titlePaint);
        titlePaint.setTextSize(15f);

        // Grid lines + y-axis values
        gridPaint.setColor(gridColor);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        labelPaint.setTextSize(10f);
        labelPaint.setColor(textColor & 0x99FFFFFF);
        int gridLines = 4;
        for (int i = 0; i <= gridLines; i++) {
            float y = top + chartH * i / gridLines;
            canvas.drawLine(left, y, right, y, gridPaint);
            float val = maxVal * (gridLines - i) / gridLines;
            String yLabel = val >= 100 ? String.valueOf((int) val)
                : val >= 10 ? String.format("%.0f", val)
                : String.format("%.1f", val);
            canvas.drawText(yLabel, left - 4, y + 4, labelPaint);
        }

        // Bars
        int n = values.size();
        float barGap = Math.max(2, chartW * 0.08f / n);
        float barW = (chartW - barGap * (n + 1)) / n;
        barW = Math.max(4, Math.min(barW, 40)); // clamp width

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(10f);

        for (int i = 0; i < n; i++) {
            float x = left + barGap + i * (barW + barGap);
            float val = values.get(i);
            float barH = (val / maxVal) * chartH;
            if (barH < 2 && val > 0) barH = 2; // minimum visible bar

            barRect.set(x, bottom - barH, x + barW, bottom);

            // Bar fill with gradient
            int color = (i == highlightIndex) ? barColor : (barColor & 0xBBFFFFFF);
            barPaint.setShader(new LinearGradient(0, barRect.top, 0, barRect.bottom,
                color, color & 0x80FFFFFF, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(barRect, 3, 3, barPaint);
            barPaint.setShader(null);

            // Value above bar (only if enough space and value > 0)
            if (val > 0 && barW > 10) {
                valuePaint.setColor(barColor);
                valuePaint.setTextSize(Math.min(10f, barW * 0.4f));
                String vStr = val >= 100 ? String.valueOf((int) val)
                    : val >= 10 ? String.format("%.0f", val)
                    : String.format("%.1f", val);
                canvas.drawText(vStr, x + barW / 2, barRect.top - 3, valuePaint);
            }

            // X-axis label
            if (i < labels.size()) {
                labelPaint.setColor(i == highlightIndex ? barColor : (textColor & 0x99FFFFFF));
                // Show label if space allows (skip some if too many)
                int skip = n > 20 ? 4 : (n > 12 ? 2 : 1);
                if (i % skip == 0 || i == n - 1 || i == highlightIndex) {
                    canvas.drawText(labels.get(i), x + barW / 2, bottom + xLabelH - 6, labelPaint);
                }
            }
        }

        // Summary row at bottom
        if (!summary.isEmpty()) {
            summaryPaint.setColor(textColor & 0xBBFFFFFF);
            canvas.drawText(summary, left, h - 4, summaryPaint);
        }
    }
}

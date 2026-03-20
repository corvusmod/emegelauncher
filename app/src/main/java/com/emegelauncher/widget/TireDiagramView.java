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
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

public class TireDiagramView extends View {
    private final Paint carPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint carFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tirePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tireStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tireRect = new RectF();

    private float[] pressures = {0, 0, 0, 0};
    private float[] temps = {0, 0, 0, 0};

    public TireDiagramView(Context context) { super(context); init(); }
    public TireDiagramView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        carPaint.setColor(0xFF8E8E93);
        carPaint.setStyle(Paint.Style.STROKE);
        carPaint.setStrokeWidth(2.5f);
        carFillPaint.setStyle(Paint.Style.FILL);
        carFillPaint.setColor(0xFF1A1A1E);
        tirePaint.setStyle(Paint.Style.FILL);
        tireStrokePaint.setStyle(Paint.Style.STROKE);
        tireStrokePaint.setStrokeWidth(1.5f);
        textPaint.setColor(0xFFF5F5F7);
        textPaint.setTextSize(26f);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labelPaint.setColor(0xFF8E8E93);
        labelPaint.setTextSize(20f);
        labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        valuePaint.setTextSize(24f);
        valuePaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        legendPaint.setTextSize(17f);
        legendPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setPressures(float fl, float fr, float rl, float rr) {
        pressures = new float[]{fl, fr, rl, rr};
        invalidate();
    }

    public void setTemps(float fl, float fr, float rl, float rr) {
        temps = new float[]{fl, fr, rl, rr};
        invalidate();
    }

    public void setTextColor(int c) { textPaint.setColor(c); valuePaint.setColor(c); }
    public void setLabelColor(int c) { labelPaint.setColor(c); }
    public void setCarColor(int c) { carPaint.setColor(c); }

    private int pressureColor(float bar) {
        if (bar < 2.5f) return 0xFFFF453A;
        if (bar > 3.3f) return 0xFFFF9F0A;
        return 0xFF30D158;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2, cy = h / 2 - 10;
        float carW = w * 0.18f, carH = h * 0.55f;

        // Title
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(22f);
        canvas.drawText(getContext().getString(com.emegelauncher.R.string.graph_tire_title), cx, cy - carH / 2 - 30, textPaint);
        textPaint.setTextSize(26f);

        // Car body — top-down SUV/crossover silhouette (MG Marvel R style)
        carPaint.setStrokeWidth(2.5f);
        Path carPath = new Path();
        float l = cx - carW / 2, r = cx + carW / 2;
        float t = cy - carH / 2, b = cy + carH / 2;
        float rr = carW * 0.22f; // corner radius

        // Front (top) — narrower nose with curved hood
        carPath.moveTo(l + rr, t);
        carPath.quadTo(cx, t - carH * 0.04f, r - rr, t); // slight curve for hood
        carPath.quadTo(r, t, r, t + rr);

        // Right side — gentle bulge for fenders
        carPath.lineTo(r, cy - carH * 0.15f);
        carPath.quadTo(r + carW * 0.04f, cy, r, cy + carH * 0.15f); // side bulge
        carPath.lineTo(r, b - rr);
        carPath.quadTo(r, b, r - rr, b);

        // Rear (bottom) — wider, flat
        carPath.lineTo(l + rr, b);
        carPath.quadTo(l, b, l, b - rr);

        // Left side — mirror of right
        carPath.lineTo(l, cy + carH * 0.15f);
        carPath.quadTo(l - carW * 0.04f, cy, l, cy - carH * 0.15f);
        carPath.lineTo(l, t + rr);
        carPath.quadTo(l, t, l + rr, t);
        carPath.close();

        canvas.drawPath(carPath, carPaint);

        // Windshield (front window)
        Paint wsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wsPaint.setColor(carPaint.getColor());
        wsPaint.setStrokeWidth(1.5f);
        wsPaint.setStyle(Paint.Style.STROKE);
        Path wsPath = new Path();
        float wsT = cy - carH * 0.30f, wsB = cy - carH * 0.12f;
        wsPath.moveTo(l + carW * 0.18f, wsT);
        wsPath.quadTo(cx, wsT - 4, r - carW * 0.18f, wsT);
        wsPath.lineTo(r - carW * 0.12f, wsB);
        wsPath.lineTo(l + carW * 0.12f, wsB);
        wsPath.close();
        canvas.drawPath(wsPath, wsPaint);

        // Rear window
        Path rwPath = new Path();
        float rwT = cy + carH * 0.15f, rwB = cy + carH * 0.28f;
        rwPath.moveTo(l + carW * 0.12f, rwT);
        rwPath.lineTo(r - carW * 0.12f, rwT);
        rwPath.lineTo(r - carW * 0.18f, rwB);
        rwPath.quadTo(cx, rwB + 4, l + carW * 0.18f, rwB);
        rwPath.close();
        canvas.drawPath(rwPath, wsPaint);

        // Center line (roof ridge)
        canvas.drawLine(cx, cy - carH * 0.08f, cx, cy + carH * 0.12f, wsPaint);

        // Tire positions
        float[][] tirePos = {
            {cx - carW / 2 - 28, cy - carH * 0.28f},
            {cx + carW / 2 + 28, cy - carH * 0.28f},
            {cx - carW / 2 - 28, cy + carH * 0.28f},
            {cx + carW / 2 + 28, cy + carH * 0.28f},
        };
        String[] posLabels = {"FL", "FR", "RL", "RR"};

        for (int i = 0; i < 4; i++) {
            float tx = tirePos[i][0], ty = tirePos[i][1];
            int pColor = pressureColor(pressures[i]);

            // Tire body
            tireRect.set(tx - 16, ty - 32, tx + 16, ty + 32);
            tirePaint.setColor(pColor);
            tirePaint.setShader(null);
            canvas.drawRoundRect(tireRect, 6, 6, tirePaint);

            // Tire border
            tireStrokePaint.setColor(pColor);
            canvas.drawRoundRect(tireRect, 6, 6, tireStrokePaint);

            // Position label on tire
            Paint posLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            posLabelPaint.setColor(0xFFFFFFFF);
            posLabelPaint.setTextSize(14f);
            posLabelPaint.setTextAlign(Paint.Align.CENTER);
            posLabelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            canvas.drawText(posLabels[i], tx, ty + 5, posLabelPaint);

            // Pressure and temp text
            boolean isLeft = (i == 0 || i == 2);
            float textX = isLeft ? tx - 50 : tx + 50;

            valuePaint.setTextAlign(isLeft ? Paint.Align.RIGHT : Paint.Align.LEFT);
            valuePaint.setColor(pColor);
            valuePaint.setTextSize(24f);
            canvas.drawText(String.format("%.2f", pressures[i]), textX, ty - 6, valuePaint);

            labelPaint.setTextAlign(isLeft ? Paint.Align.RIGHT : Paint.Align.LEFT);
            labelPaint.setTextSize(16f);
            labelPaint.setColor(0xFF8E8E93);
            canvas.drawText("bar", textX, ty + 10, labelPaint);

            labelPaint.setColor(0xFF636366);
            canvas.drawText(String.format("%.0f°C", temps[i]), textX, ty + 28, labelPaint);
        }

        // Legend at bottom
        float legendY = cy + carH / 2 + 40;
        legendPaint.setTextAlign(Paint.Align.CENTER);

        legendPaint.setColor(0xFF30D158);
        canvas.drawCircle(cx - w * 0.28f, legendY - 5, 5, legendPaint);
        canvas.drawText("2.5-3.3", cx - w * 0.28f + 40, legendY, legendPaint);

        legendPaint.setColor(0xFFFF453A);
        canvas.drawCircle(cx - w * 0.02f, legendY - 5, 5, legendPaint);
        canvas.drawText("<2.5", cx - w * 0.02f + 30, legendY, legendPaint);

        legendPaint.setColor(0xFFFF9F0A);
        canvas.drawCircle(cx + w * 0.22f, legendY - 5, 5, legendPaint);
        canvas.drawText(">3.3", cx + w * 0.22f + 30, legendY, legendPaint);
    }

    private static int darkenColor(int color, float factor) {
        return Color.argb(Color.alpha(color),
            (int) (Color.red(color) * (1 - factor)),
            (int) (Color.green(color) * (1 - factor)),
            (int) (Color.blue(color) * (1 - factor)));
    }
}

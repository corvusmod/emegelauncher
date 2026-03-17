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
        carPaint.setColor(0xFF3A3A3C);
        carPaint.setStyle(Paint.Style.STROKE);
        carPaint.setStrokeWidth(2f);
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
        float carW = w * 0.26f, carH = h * 0.55f;

        // Title
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(22f);
        canvas.drawText("TIRE PRESSURE & TEMPERATURE", cx, cy - carH / 2 - 30, textPaint);
        textPaint.setTextSize(26f);

        // Car body with gradient fill
        RectF carRect = new RectF(cx - carW / 2, cy - carH / 2, cx + carW / 2, cy + carH / 2);
        carFillPaint.setShader(new LinearGradient(cx, cy - carH / 2, cx, cy + carH / 2,
            0xFF222226, 0xFF1A1A1E, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(carRect, carW * 0.35f, carW * 0.25f, carFillPaint);
        carFillPaint.setShader(null);
        canvas.drawRoundRect(carRect, carW * 0.35f, carW * 0.25f, carPaint);

        // Windshield line
        Paint windshieldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        windshieldPaint.setColor(0xFF3A3A3C);
        windshieldPaint.setStrokeWidth(1.5f);
        windshieldPaint.setStyle(Paint.Style.STROKE);
        RectF wsRect = new RectF(cx - carW * 0.32f, cy - carH * 0.28f, cx + carW * 0.32f, cy - carH * 0.08f);
        canvas.drawRoundRect(wsRect, 8, 8, windshieldPaint);

        // Center line
        canvas.drawLine(cx, cy - carH * 0.05f, cx, cy + carH * 0.35f, windshieldPaint);

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

            // Tire glow
            Paint glowP = new Paint(Paint.ANTI_ALIAS_FLAG);
            glowP.setColor(pColor & 0x20FFFFFF);
            glowP.setShadowLayer(10, 0, 0, pColor & 0x40FFFFFF);
            tireRect.set(tx - 16, ty - 32, tx + 16, ty + 32);
            canvas.drawRoundRect(tireRect, 6, 6, glowP);

            // Tire body with gradient
            tirePaint.setShader(new LinearGradient(tx - 16, ty, tx + 16, ty,
                pColor, darkenColor(pColor, 0.3f), Shader.TileMode.CLAMP));
            canvas.drawRoundRect(tireRect, 6, 6, tirePaint);
            tirePaint.setShader(null);

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

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
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Battery icon with dynamic fill level.
 * Draws a battery outline (body + terminal nub) with the interior
 * filled proportionally to the SOC percentage.
 * Fill color: green >50%, orange 20-50%, red <20%.
 */
public class BatteryView extends View {
    private float soc = 0f; // 0-100
    private int fillColor = 0xFF30D158; // green
    private int outlineColor = 0x88FFFFFF;

    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bodyRect = new RectF();
    private final RectF fillRect = new RectF();
    private final RectF terminalRect = new RectF();

    public BatteryView(Context context) {
        super(context);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(3);
        outlinePaint.setColor(outlineColor);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(fillColor);
    }

    public void setSoc(float soc) {
        this.soc = Math.max(0, Math.min(100, soc));
        // Color based on level
        if (this.soc > 50) fillColor = 0xFF30D158; // green
        else if (this.soc > 20) fillColor = 0xFFFF9F0A; // orange
        else fillColor = 0xFFFF453A; // red
        fillPaint.setColor(fillColor);
        invalidate();
    }

    public void setOutlineColor(int color) {
        outlineColor = color;
        outlinePaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();

        // Battery drawn vertically (tall, like a real battery icon)
        float pad = 6;
        float termH = h * 0.06f; // terminal nub height
        float termW = w * 0.35f; // terminal nub width
        float cornerR = 6;
        float strokeW = outlinePaint.getStrokeWidth();

        // Terminal nub at top center
        terminalRect.set(
            (w - termW) / 2, pad,
            (w + termW) / 2, pad + termH
        );
        canvas.drawRoundRect(terminalRect, 3, 3, outlinePaint);
        // Fill the terminal
        fillPaint.setColor(outlineColor);
        canvas.drawRoundRect(terminalRect, 3, 3, fillPaint);
        fillPaint.setColor(fillColor);

        // Battery body below terminal
        float bodyTop = pad + termH - 1;
        float bodyBottom = h - pad;
        bodyRect.set(pad, bodyTop, w - pad, bodyBottom);
        canvas.drawRoundRect(bodyRect, cornerR, cornerR, outlinePaint);

        // Fill inside body based on SOC (fills from bottom up)
        float innerPad = strokeW + 3;
        float innerTop = bodyTop + innerPad;
        float innerBottom = bodyBottom - innerPad;
        float innerLeft = pad + innerPad;
        float innerRight = w - pad - innerPad;
        float fillHeight = (innerBottom - innerTop) * (soc / 100f);

        if (fillHeight > 0) {
            fillRect.set(
                innerLeft,
                innerBottom - fillHeight,
                innerRight,
                innerBottom
            );
            canvas.drawRoundRect(fillRect, 3, 3, fillPaint);
        }
    }
}

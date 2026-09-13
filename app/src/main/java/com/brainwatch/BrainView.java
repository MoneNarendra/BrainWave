package com.brainwatch;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * A cartoon brain whose look reflects estimated dopamine fatigue (0..1).
 * Fresh = pink, upright, sharp and fast-pulsing. Exhausted = dark, drooping,
 * slow, half-closed eyes and a frown, with "Zzz" and a warning glow.
 */
public class BrainView extends View {

    private float fatigue = 0.2f;
    private float flash = 0f;
    private long lastFrame = 0;
    private final Random rnd = new Random();
    private ValueAnimator animator;

    private final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glossP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlineP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wrinkleP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeWhiteP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pupilP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lidP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blushP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mouthP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparkP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zzzP = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BrainView(Context context) {
        super(context);
        init();
    }

    public BrainView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        fillP.setStyle(Paint.Style.FILL);
        glossP.setStyle(Paint.Style.FILL);
        outlineP.setStyle(Paint.Style.STROKE);
        outlineP.setStrokeCap(Paint.Cap.ROUND);
        wrinkleP.setStyle(Paint.Style.STROKE);
        wrinkleP.setStrokeCap(Paint.Cap.ROUND);
        eyeWhiteP.setStyle(Paint.Style.FILL);
        pupilP.setStyle(Paint.Style.FILL);
        highlightP.setStyle(Paint.Style.FILL);
        lidP.setStyle(Paint.Style.FILL);
        lidP.setColor(0xFF14111F);
        blushP.setStyle(Paint.Style.FILL);
        mouthP.setStyle(Paint.Style.STROKE);
        mouthP.setStrokeCap(Paint.Cap.ROUND);
        glowP.setStyle(Paint.Style.FILL);
        sparkP.setStyle(Paint.Style.FILL);
        zzzP.setTextAlign(Paint.Align.CENTER);
        zzzP.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    }

    public void setFatigue(float f) {
        fatigue = Math.max(0f, Math.min(1f, f));
    }

    public void reelFlash() {
        flash = 1f;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator != null) {
            animator.cancel();
        }
        final long start = SystemClock.uptimeMillis();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(100000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(a -> {
            tick();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
        }
    }

    private void tick() {
        long now = SystemClock.uptimeMillis();
        if (lastFrame != 0) {
            long dt = now - lastFrame;
            flash = Math.max(0f, flash - dt / 700f);
        }
        lastFrame = now;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float t = SystemClock.uptimeMillis() / 1000f;
        float s = Math.min(w, h);

        float f = fatigue;
        float sag = s * 0.06f * f;
        float cx = w / 2f;
        float cy = h * 0.52f + sag;

        float periodMs = 1000f + f * 2600f;
        float pulse = 1f + 0.028f * (float) Math.sin(2 * Math.PI * t / (periodMs / 1000f));
        pulse += flash * 0.08f;
        float R = s * 0.30f * pulse;

        if (f > 0.45f) {
            int glowColor = Color.argb((int) (64 * f), 255, 123, 156);
            RadialGradient g = new RadialGradient(cx, cy, R * 1.9f, glowColor, Color.TRANSPARENT,
                    Shader.TileMode.CLAMP);
            glowP.setShader(g);
            canvas.drawCircle(cx, cy, R * 1.9f, glowP);
        }

        int fillColor = lerpColor(0xFFE88FA6, 0xFF6E2B3C, f);
        int outlineColor = lerpColor(0xFFB75E7C, 0xFF38111F, f);
        int wrinkleColor = blendToBlack(fillColor, 0.30f);

        fillP.setColor(fillColor);
        outlineP.setColor(outlineColor);
        outlineP.setStrokeWidth(Math.max(2.2f, s * 0.011f));
        wrinkleP.setColor(wrinkleColor);
        wrinkleP.setStrokeWidth(Math.max(1.5f, s * 0.0055f));
        eyeWhiteP.setColor(lerpColor(0xFFFFFFFF, 0xFFE8DDD6, f));
        pupilP.setColor(0xFF202238);

        Path brain = buildBrain(cx, cy, R);

        // stem behind
        canvas.save();
        canvas.translate(cx, cy + R * 1.55f);
        RectF stem = new RectF(-R * 0.13f, -R * 0.18f, R * 0.13f, R * 0.18f);
        canvas.drawOval(stem, fillP);
        canvas.drawOval(stem, outlineP);
        canvas.restore();

        // cerebellum
        drawBulge(canvas, cx, cy + R * 1.26f, R * 0.48f, R * 0.34f, 2);

        // main brain
        canvas.drawPath(brain, fillP);

        // inner gloss (soft top light) for volume
        RadialGradient g2 = new RadialGradient(cx - R * 0.45f, cy - R * 0.55f, R * 1.25f,
                0x59FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        glossP.setShader(g2);
        canvas.drawPath(brain, glossP);
        glossP.setShader(null);

        // gyri + groove, clipped to the silhouette
        canvas.save();
        canvas.clipPath(brain);
        drawGyri(canvas, cx, cy, R);
        outlineP.setStrokeWidth(Math.max(1.9f, s * 0.009f));
        Path groove = new Path();
        groove.moveTo(cx, cy - R * 1.30f);
        groove.cubicTo(cx + R * 0.08f, cy - R * 0.35f, cx - R * 0.08f, cy + R * 0.35f, cx, cy + R * 1.02f);
        canvas.drawPath(groove, outlineP);
        canvas.restore();

        canvas.drawPath(brain, outlineP);

        // face
        drawFace(canvas, cx, cy, R, f);

        if (flash > 0f) {
            drawSparks(canvas, cx, cy, R);
        }
        if (f > 0.55f) {
            drawZzz(canvas, cx, cy, R, t);
        }
    }

    private Path buildBrain(float cx, float cy, float R) {
        Path p = new Path();
        p.moveTo(cx, cy - R * 1.30f);
        p.cubicTo(cx - R * 0.60f, cy - R * 1.44f, cx - R * 1.12f, cy - R * 1.16f, cx - R * 1.24f, cy - R * 0.70f);
        p.cubicTo(cx - R * 1.38f, cy - R * 0.14f, cx - R * 1.02f, cy + R * 0.60f, cx - R * 0.60f, cy + R * 0.86f);
        p.cubicTo(cx - R * 0.38f, cy + R * 1.0f, cx - R * 0.08f, cy + R * 1.05f, cx, cy + R * 1.02f);
        p.cubicTo(cx + R * 0.08f, cy + R * 1.05f, cx + R * 0.38f, cy + R * 1.0f, cx + R * 0.60f, cy + R * 0.86f);
        p.cubicTo(cx + R * 1.02f, cy + R * 0.60f, cx + R * 1.38f, cy - R * 0.14f, cx + R * 1.24f, cy - R * 0.70f);
        p.cubicTo(cx + R * 1.12f, cy - R * 1.16f, cx + R * 0.60f, cy - R * 1.44f, cx, cy - R * 1.30f);
        p.close();
        return p;
    }

    private void drawGyri(Canvas canvas, float cx, float cy, float R) {
        int dirs = -1;
        for (int side = 0; side < 2; side++) {
            float mx = side == 0 ? -1f : 1f;
            float near = side == 0 ? -R * 1.08f : R * 0.10f;
            float far = side == 0 ? -R * 0.10f : R * 1.08f;
            for (int k = 0; k < 3; k++) {
                Path w = new Path();
                float baseY = cy - R * 0.52f + k * R * 0.42f;
                for (int i = 0; i <= 14; i++) {
                    float x = near + (far - near) * i / 14f;
                    float y = baseY + R * 0.05f * (float) Math.sin(x / R * 3.2f + k * 1.6f + dirs);
                    if (i == 0) {
                        w.moveTo(x, y);
                    } else {
                        w.lineTo(x, y);
                    }
                }
                canvas.drawPath(w, wrinkleP);
            }
        }
    }

    private void drawBulge(Canvas canvas, float bcx, float bcy, float rx, float ry, int hatches) {
        canvas.save();
        canvas.translate(bcx, bcy);
        Path bulge = new Path();
        bulge.addOval(new RectF(-rx, -ry, rx, ry), Path.Direction.CW);
        canvas.drawPath(bulge, fillP);
        canvas.save();
        canvas.clipPath(bulge);
        for (int k = 0; k < hatches; k++) {
            float y = -ry * 0.4f + k * ry * 0.8f;
            canvas.drawLine(-rx * 0.8f, y, rx * 0.8f, y, wrinkleP);
        }
        canvas.restore();
        canvas.drawPath(bulge, outlineP);
        canvas.restore();
    }

    private void drawFace(Canvas canvas, float cx, float cy, float R, float f) {
        float eyeRx = R * 0.16f;
        float eyeRy = R * 0.23f;
        float eyeY = cy - R * 0.10f;
        float[] xs = {cx - R * 0.52f, cx + R * 0.52f};

        for (float ex : xs) {
            RectF eye = new RectF(ex - eyeRx, eyeY - eyeRy, ex + eyeRx, eyeY + eyeRy);
            canvas.drawOval(eye, eyeWhiteP);

            float pupilRx = eyeRx * 0.44f;
            float pupilRy = eyeRy * 0.46f;
            float dropY = eyeY + eyeRy * 0.30f * f;
            canvas.drawOval(new RectF(ex - pupilRx, dropY - pupilRy, ex + pupilRx, dropY + pupilRy), pupilP);

            // glossy highlight
            highlightP.setColor(0xFFFFFFFF);
            highlightP.setAlpha(210);
            canvas.drawCircle(ex - pupilRx * 0.38f, dropY - pupilRy * 0.45f, pupilRx * 0.42f, highlightP);
            highlightP.setAlpha(255);

            // drooping lid
            float lidOpen = 1f - f * 0.82f;
            float lidBottom = eyeY - eyeRy + lidOpen * 2f * eyeRy;
            if (lidBottom > eyeY - eyeRy + 1f) {
                RectF lid = new RectF(ex - eyeRx, eyeY - eyeRy, ex + eyeRx,
                        Math.min(lidBottom, eyeY + eyeRy));
                canvas.drawRoundRect(lid, eyeRx, eyeRx, lidP);
            }
            outlineP.setStrokeWidth(Math.max(1.6f, eyeRx * 0.45f));
            canvas.drawLine(ex - eyeRx, Math.min(lidBottom, eyeY + eyeRy),
                    ex + eyeRx, Math.min(lidBottom, eyeY + eyeRy), outlineP);
        }

        if (f < 0.3f) {
            blushP.setColor(0xFFE88FA6);
            blushP.setAlpha(110);
            canvas.drawCircle(cx - R * 0.76f, eyeY + eyeRy + R * 0.04f, R * 0.12f, blushP);
            canvas.drawCircle(cx + R * 0.76f, eyeY + eyeRy + R * 0.04f, R * 0.12f, blushP);
            blushP.setAlpha(255);
        }

        float my = cy + R * 0.40f;
        float gap = R * 0.30f;
        float curve = (1f - f) * R * 0.30f - f * R * 0.18f;
        Path mouth = new Path();
        mouth.moveTo(cx - gap, my);
        mouth.quadTo(cx, my + curve, cx + gap, my);
        mouthP.setColor(lerpColor(0xFFF2F4FF, 0xFF38111F, f));
        mouthP.setStrokeWidth(Math.max(2.2f, R * 0.024f));
        canvas.drawPath(mouth, mouthP);
    }

    private void drawSparks(Canvas canvas, float cx, float cy, float R) {
        for (int i = 0; i < 7; i++) {
            float p = 1f - flash;
            float angle = (float) (Math.PI * (0.50 + rnd.nextDouble() * 0.5));
            float rad = R * (0.85f + p * 1.05f);
            float sx = cx + (float) Math.cos(angle) * rad;
            float sy = cy - (float) Math.sin(angle) * rad;
            sparkP.setColor((i % 2 == 0) ? 0xFFFFE29A : 0xFF6FE7C7);
            sparkP.setAlpha((int) (flash * 255f));
            canvas.drawCircle(sx, sy, Math.max(2f, R * 0.05f * (1.2f - p)), sparkP);
        }
    }

    private void drawZzz(Canvas canvas, float cx, float cy, float R, float t) {
        float cycle = t % 3.4f;
        if (cycle < 1.3f) {
            float p = cycle / 1.3f;
            zzzP.setColor(0xFFD5F8EF);
            zzzP.setAlpha((int) (30 + 150 * p));
            zzzP.setTextSize(R * (0.20f + 0.14f * p));
            canvas.drawText("Z", cx + R * 0.15f, cy - R * (1.05f + p * 0.7f), zzzP);
        }
        if (cycle > 1.6f && cycle < 2.9f) {
            float p = (cycle - 1.6f) / 1.3f;
            zzzP.setColor(0xFFD5F8EF);
            zzzP.setAlpha((int) (20 + 120 * p));
            zzzP.setTextSize(R * 0.14f);
            canvas.drawText("z", cx - R * 0.25f, cy - R * (1.15f + p * 0.5f), zzzP);
        }
    }

    private static int lerpColor(int a, int b, float t) {
        float tt = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * tt);
        int g = (int) (ag + (bg - ag) * tt);
        int bl = (int) (ab + (bb - ab) * tt);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int blendToBlack(int c, float t) {
        int r = (int) (((c >> 16) & 0xFF) * (1f - t));
        int g = (int) (((c >> 8) & 0xFF) * (1f - t));
        int b = (int) ((c & 0xFF) * (1f - t));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
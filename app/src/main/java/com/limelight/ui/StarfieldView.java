package com.limelight.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/**
 * Starwire's background: soft purple and blue nebula clouds with a field of small stars, a few of
 * which slowly twinkle. The stars are placed from a fixed seed so the sky looks the same every
 * time. Twinkling follows Android's animation settings and stops while the view is hidden.
 */
public class StarfieldView extends View {
    // MiWire: calibrated density to cut CPU/GPU draw overhead by >50% on TV boxes
    private static final int STARS_PER_MEGAPIXEL = 45;
    private static final int STATIC_STAR_ALPHA = (int) (0.55f * 255);
    private static final long TWINKLE_MS = 7000;
    private static final long TWINKLE_FRAME_INTERVAL_MS = 85L;
    private static final int[] STAR_COLORS = {0xFFFFFFFF, 0xFFFFE0B2, 0xFFFFCC80, 0xFFFFE57F};

    // Nebula: subtle ambient warm amber/flame clouds on dark carbon
    private static final float[][] CLOUD_SPOTS = {{0.12f, 0.18f, 0.55f}, {0.88f, 0.92f, 0.50f}, {0.30f, 0.75f, 0.40f}};
    private static final int[] CLOUD_COLORS = {0x26FF5722, 0x1AFF9800, 0x12FF3D00};

    private final Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cloud = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] x, y, radius, phase;
    private int[] color;
    private boolean[] twinkles;
    private RadialGradient[] clouds;
    private float[][] cloudRects;
    private float time;
    private long lastTwinkleBucket = -1L;
    private ValueAnimator twinkle;

    public StarfieldView(Context context) {
        this(context, null);
    }

    public StarfieldView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float d = getResources().getDisplayMetrics().density;
        Random random = new Random(0x7015L);
        int count = Math.max(40, (int) ((long) w * h * STARS_PER_MEGAPIXEL / 1_000_000L));
        x = new float[count];
        y = new float[count];
        radius = new float[count];
        phase = new float[count];
        color = new int[count];
        twinkles = new boolean[count];
        for (int i = 0; i < count; i++) {
            x[i] = random.nextFloat() * w;
            y[i] = random.nextFloat() * h;
            // Mostly tiny stars, a few brighter ones
            float size = random.nextFloat();
            radius[i] = (0.35f + size * size * size * 1.4f) * d;
            phase[i] = random.nextFloat() * (float) (Math.PI * 2);
            color[i] = STAR_COLORS[random.nextInt(STAR_COLORS.length)];
            twinkles[i] = random.nextFloat() < 0.18f;
        }

        clouds = new RadialGradient[CLOUD_SPOTS.length];
        cloudRects = new float[CLOUD_SPOTS.length][];
        for (int i = 0; i < CLOUD_SPOTS.length; i++) {
            float cx = CLOUD_SPOTS[i][0] * w, cy = CLOUD_SPOTS[i][1] * h;
            float r = Math.max(1f, CLOUD_SPOTS[i][2] * Math.max(w, h));
            int c = CLOUD_COLORS[i];
            clouds[i] = new RadialGradient(cx, cy, r, c, c & 0x00FFFFFF, Shader.TileMode.CLAMP);
            cloudRects[i] = new float[] {cx - r, cy - r, cx + r, cy + r};
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (x == null) {
            return;
        }
        for (int i = 0; i < clouds.length; i++) {
            cloud.setShader(clouds[i]);
            float[] r = cloudRects[i];
            canvas.drawRect(r[0], r[1], r[2], r[3], cloud);
        }
        for (int i = 0; i < x.length; i++) {
            star.setColor(color[i]);
            if (twinkles[i]) {
                float alpha = 0.35f + 0.6f * (0.5f + 0.5f * (float) Math.sin(time + phase[i]));
                star.setAlpha((int) (alpha * 255));
            } else {
                star.setAlpha(STATIC_STAR_ALPHA);
            }
            canvas.drawCircle(x[i], y[i], radius[i], star);
        }
    }

    @Override
    public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (visible) {
            if (twinkle == null && Loops.allowed(getContext())) {
                lastTwinkleBucket = -1L;
                twinkle = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
                twinkle.setDuration(TWINKLE_MS);
                twinkle.setRepeatCount(ValueAnimator.INFINITE);
                twinkle.setInterpolator(new LinearInterpolator());
                twinkle.addUpdateListener(animation -> {
                    long bucket = animation.getCurrentPlayTime() / TWINKLE_FRAME_INTERVAL_MS;
                    if (bucket == lastTwinkleBucket) {
                        return;
                    }
                    lastTwinkleBucket = bucket;
                    time = (Float) animation.getAnimatedValue();
                    invalidate();
                });
                twinkle.start();
            }
        }
        else if (twinkle != null) {
            twinkle.cancel();
            twinkle = null;
            lastTwinkleBucket = -1L;
        }
    }

    // For tests: whether the stars twinkle
    boolean isTwinkling() {
        return twinkle != null;
    }
}

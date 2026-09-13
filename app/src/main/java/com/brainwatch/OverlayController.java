package com.brainwatch;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import java.util.Locale;

/**
 * A tiny always-on chip that floats over Instagram: live session timer,
 * scroll count, reel count and a fatigue percentage. Slides in/out smoothly
 * and pops the counters every time they tick up. The chip background heats
 * up from green (fresh session) to red (heavy scrolling) as the user racks
 * up scrolls and reels - a live "dopamine demand" gauge.
 */
public class OverlayController {

    private final Context context;
    private final WindowManager wm;
    private View chip;
    private TextView tvScrolls, tvReels, tvClicks, tvTimer, tvFatigue;
    private View heatDot;
    private GradientDrawable dotDrawable;
    private boolean shown;
    private boolean touchable;
    private boolean draggable;
    private WindowManager.LayoutParams lp;

    private float heat = 0f;
    private ValueAnimator heatAnim;

    private float dragDx = 0f;
    private float dragDy = 0f;

    private long sessionStartMs = 0;
    private int lastScrolls = -1;
    private int lastReels = -1;
    private int lastClicks = -1;

    private static final int COLOR_GREEN = 0xFF6FE7C7;
    private static final int COLOR_RED = 0xFFFF7B9C;
    private static final float HEAT_FULL_AT = 260f; // scroll-equivalents for full red

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!shown) {
                return;
            }
            if (tvTimer != null) {
                tvTimer.setText(formatElapsed());
            }
            handler.postDelayed(this, 1000);
        }
    };

    public OverlayController(Context context) {
        this.context = context;
        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean canShow() {
        return Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(context);
    }

    public boolean isShown() {
        return shown;
    }

    public void shownSessionSince(long startMs, boolean draggableChip) {
        sessionStartMs = startMs > 0 ? startMs : System.currentTimeMillis();
        touchable = true;
        draggable = draggableChip;
    }

    public void show() {
        if (shown || chip != null || wm == null || !canShow()) {
            return;
        }
        try {
            chip = LayoutInflater.from(context).inflate(R.layout.overlay_chip, null);
            tvScrolls = chip.findViewById(R.id.ovScrolls);
            tvReels = chip.findViewById(R.id.ovReels);
            tvClicks = chip.findViewById(R.id.ovClicks);
            tvTimer = chip.findViewById(R.id.ovTimer);
            tvFatigue = chip.findViewById(R.id.ovFatigue);
            heatDot = chip.findViewById(R.id.ovDot);

            dotDrawable = new GradientDrawable();
            dotDrawable.setShape(GradientDrawable.OVAL);
            dotDrawable.setColor(heatColor(0f));
            dotDrawable.setStroke(dp(2), 0x66FFFFFF);
            if (heatDot != null) {
                heatDot.setBackground(dotDrawable);
            }

            int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            if (touchable && draggable) {
                chip.setOnTouchListener(this::onChipTouch);
            } else if (!touchable) {
                flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            }
            int type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    flags,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = dp(12);
            lp.y = dp(12);

            // center horizontally on first appearance
            chip.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int chipW = chip.getMeasuredWidth();
            int screenW = context.getResources().getDisplayMetrics().widthPixels;
            if (chipW > 0 && chipW < screenW) {
                lp.x = (screenW - chipW) / 2;
            }

            chip.setAlpha(0f);
            chip.setTranslationY(-dp(26));
            wm.addView(chip, lp);
            shown = true;

            ObjectAnimator in = ObjectAnimator.ofFloat(chip, "alpha", 0f, 1f);
            in.setDuration(280);
            ObjectAnimator slide = ObjectAnimator.ofFloat(chip, "translationY", -dp(26), 0f);
            slide.setDuration(320);
            slide.setInterpolator(new OvershootInterpolator(0.6f));
            in.start();
            slide.start();

            handler.removeCallbacks(ticker);
            handler.post(ticker);
        } catch (Exception ignored) {
            // overlay blocked (missing permission or OEM restrictions)
        }
    }

    private boolean onChipTouch(View v, android.view.MotionEvent e) {
        if (lp == null) {
            return false;
        }
        switch (e.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                dragDx = e.getRawX() - lp.x;
                dragDy = e.getRawY() - lp.y;
                return true;
            case android.view.MotionEvent.ACTION_MOVE:
                lp.x = (int) (e.getRawX() - dragDx);
                lp.y = (int) (e.getRawY() - dragDy);
                try {
                    wm.updateViewLayout(chip, lp);
                } catch (Exception ignored) {
                }
                return true;
            default:
                return false;
        }
    }

    public void hide() {
        if (!shown || chip == null) {
            return;
        }
        final View view = chip;
        final WindowManager wmRef = wm;
        shown = false;
        ObjectAnimator out = ObjectAnimator.ofPropertyValuesHolder(view,
                PropertyValuesHolder.ofFloat("alpha", 0f),
                PropertyValuesHolder.ofFloat("translationY", -dp(26)));
        out.setDuration(260);
        out.setInterpolator(new DecelerateInterpolator());
        out.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                try {
                    wmRef.removeView(view);
                } catch (Exception ignored) {
                }
            }
        });
        handler.removeCallbacks(ticker);
        out.start();
        chip = null;
        tvScrolls = null;
        tvReels = null;
        tvClicks = null;
        tvTimer = null;
        tvFatigue = null;
        heatDot = null;
        dotDrawable = null;
        lastScrolls = -1;
        lastReels = -1;
        lastClicks = -1;
        if (heatAnim != null) {
            heatAnim.cancel();
            heatAnim = null;
        }
    }

    public void setCounts(int scrolls, int reels, int clicks) {
        if (!shown || tvScrolls == null || tvReels == null) {
            return;
        }
        if (scrolls != lastScrolls) {
            tvScrolls.setText(String.format(Locale.US, "%d sc", scrolls));
            pop(tvScrolls);
            lastScrolls = scrolls;
        }
        if (reels != lastReels) {
            tvReels.setText(String.format(Locale.US, "%d rl", reels));
            pop(tvReels);
            lastReels = reels;
        }
        if (clicks != lastClicks && tvClicks != null) {
            tvClicks.setText(String.format(Locale.US, "👆 %d", clicks));
            pop(tvClicks);
            lastClicks = clicks;
        }
        animateHeat(heatTarget(scrolls, reels));
    }

    private float heatTarget(int scrolls, int reels) {
        float eq = scrolls + reels * 3f;
        return Math.min(1f, eq / HEAT_FULL_AT);
    }

    private void animateHeat(float target) {
        if (heatAnim != null) {
            heatAnim.cancel();
        }
        if (Math.abs(heat - target) < 0.004f) {
            return;
        }
        final float from = heat;
        heatAnim = ValueAnimator.ofFloat(from, target);
        heatAnim.setDuration(650);
        heatAnim.setInterpolator(new DecelerateInterpolator());
        heatAnim.addUpdateListener(anim -> {
            heat = (float) anim.getAnimatedValue();
            if (dotDrawable != null) {
                dotDrawable.setColor(heatColor(heat));
            }
        });
        heatAnim.start();
    }

    private int heatColor(float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = lerp((COLOR_GREEN >> 16) & 0xFF, (COLOR_RED >> 16) & 0xFF, t);
        int g = lerp((COLOR_GREEN >> 8) & 0xFF, (COLOR_RED >> 8) & 0xFF, t);
        int b = lerp(COLOR_GREEN & 0xFF, COLOR_RED & 0xFF, t);
        return Color.rgb(r, g, b);
    }

    private int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    public void setFatigue(int fatigue) {
        if (!shown || tvFatigue == null) {
            return;
        }
        tvFatigue.setText(String.format(Locale.US, "🧠 %d%%", fatigue));
    }

    private void pop(TextView tv) {
        ObjectAnimator pop = ObjectAnimator.ofPropertyValuesHolder(tv,
                PropertyValuesHolder.ofFloat("scaleX", 1.45f),
                PropertyValuesHolder.ofFloat("scaleY", 1.45f));
        pop.setDuration(90);
        pop.setStartDelay(0);
        pop.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                ObjectAnimator back = ObjectAnimator.ofPropertyValuesHolder(tv,
                        PropertyValuesHolder.ofFloat("scaleX", 1f),
                        PropertyValuesHolder.ofFloat("scaleY", 1f));
                back.setDuration(240);
                back.setInterpolator(new OvershootInterpolator(1.8f));
                back.start();
            }
        });
        pop.start();
    }

    private String formatElapsed() {
        long elapsed = Math.max(0, System.currentTimeMillis() - sessionStartMs) / 1000;
        long mm = elapsed / 60;
        long ss = elapsed % 60;
        long hh = mm / 60;
        mm = mm % 60;
        if (hh > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hh, mm, ss);
        }
        return String.format(Locale.US, "%02d:%02d", mm, ss);
    }

    private int dp(int v) {
        return Math.round(context.getResources().getDisplayMetrics().density * v);
    }
}
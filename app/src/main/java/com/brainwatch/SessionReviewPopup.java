package com.brainwatch;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.TextView;

import java.util.Locale;

/**
 * A centered mood-style popup that rises after a configurable session length
 * (5/10/15 min). Shows what the session "cost" the reward system and lets the
 * user either keep scrolling (reminds again at the next interval) or close the
 * session. Auto-dismisses after a while so it never blocks the screen forever.
 */
public class SessionReviewPopup {

    private final Context context;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View popup;
    private WindowManager.LayoutParams lp;
    private boolean showing;
    private Runnable autoDismiss;

    public SessionReviewPopup(Context context) {
        this.context = context;
        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean canShow() {
        return Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(context);
    }

    public boolean isShowing() {
        return showing;
    }

    @SuppressLint("ClickableViewAccessibility")
    public void show(long sessionStartMs, int scrolls, int reels, int clicks,
                     long elapsedMs, long intervalMs, int fatigue, Runnable onKeep,
                     Runnable onClose, Runnable onTimeout) {
        if (showing || popup != null || wm == null || !canShow()) {
            return;
        }
        try {
            popup = LayoutInflater.from(context).inflate(R.layout.review_popup, null);
            TextView tvTitle = popup.findViewById(R.id.rvTitle);
            TextView tvSub = popup.findViewById(R.id.rvSubtitle);
            TextView tvTime = popup.findViewById(R.id.rvTime);
            TextView tvScrolls = popup.findViewById(R.id.rvScrolls);
            TextView tvReels = popup.findViewById(R.id.rvReels);
            TextView tvClicks = popup.findViewById(R.id.rvClicks);
            TextView tvDopa = popup.findViewById(R.id.rvDopa);
            TextView tvFatigue = popup.findViewById(R.id.rvFatigue);
            TextView tvHint = popup.findViewById(R.id.rvHint);
            Button keep = popup.findViewById(R.id.rvKeep);
            Button close = popup.findViewById(R.id.rvClose);

            long minutes = elapsedMs / 60000;
            int dopa = Fatigue.dopamineUnits(scrolls, reels, minutes);
            int minutesInt = (int) minutes;

            tvTitle.setText("🧠 Break time — " + minutesInt + " min deep");
            tvSub.setText("You set a " + fmtInterval(intervalMs) + " reminder. Quick check-in:");
            tvTime.setText(fmtFull(elapsedMs));
            tvScrolls.setText(String.valueOf(scrolls));
            tvReels.setText(String.valueOf(reels));
            tvClicks.setText(String.valueOf(clicks));
            tvDopa.setText(Fatigue.dopamineText(dopa));
            tvFatigue.setText("Dopamine fatigue is now " + fatigue + "%"
                    + (fatigue > 60 ? " — your brain is getting fuzzy." : "."));
            tvHint.setText("Every reel you pass fires a dopamine spike. "
                    + "Closing now lets your reward system reset faster.");
            keep.setText("Keep scrolling");
            close.setText("Exit app");

            keep.setOnClickListener(v -> {
                dismiss();
                if (onKeep != null) {
                    onKeep.run();
                }
            });
            close.setOnClickListener(v -> {
                dismiss();
                if (onClose != null) {
                    onClose.run();
                }
            });

            int type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type, flags, PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.CENTER;

            popup.setAlpha(0f);
            popup.setScaleX(0.9f);
            popup.setScaleY(0.9f);
            wm.addView(popup, lp);
            showing = true;

            ObjectAnimator alpha = ObjectAnimator.ofFloat(popup, "alpha", 0f, 1f);
            alpha.setDuration(260);
            ObjectAnimator scale = ObjectAnimator.ofFloat(popup, "scaleX", 0.9f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(popup, "scaleY", 0.9f, 1f);
            scale.setDuration(320);
            scaleY.setDuration(320);
            scale.setInterpolator(new OvershootInterpolator(1.1f));
            scaleY.setInterpolator(new OvershootInterpolator(1.1f));
            alpha.start();
            scale.start();
            scaleY.start();

            autoDismiss = () -> {
                dismiss();
                if (onTimeout != null) {
                    onTimeout.run();
                }
            };
            handler.postDelayed(autoDismiss, 45000);
        } catch (Exception ignored) {
        }
    }

    public void dismiss() {
        handler.removeCallbacks(autoDismiss);
        if (!showing || popup == null) {
            return;
        }
        final View view = popup;
        final WindowManager wmRef = wm;
        showing = false;
        popup = null;
        ObjectAnimator out = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f);
        out.setDuration(200);
        out.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                try {
                    wmRef.removeView(view);
                } catch (Exception ignored) {
                }
            }
        });
        out.start();
    }

    private static String fmtFull(long ms) {
        long sec = ms / 1000;
        long mm = sec / 60;
        long ss = sec % 60;
        return String.format(Locale.US, "%02d:%02d", mm, ss);
    }

    private static String fmtInterval(long ms) {
        long totalSec = ms / 1000;
        if (totalSec % 60 == 0 && totalSec >= 60) {
            return (totalSec / 60) + "-min";
        }
        if (totalSec >= 3600) {
            return (totalSec / 3600) + " hr " + ((totalSec % 3600) / 60) + " min";
        }
        return totalSec + " sec";
    }
}
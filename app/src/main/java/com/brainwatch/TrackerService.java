package com.brainwatch;

import android.accessibilityservice.AccessibilityService;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class TrackerService extends AccessibilityService {

    public static final String IG = "com.instagram.android";
    public static final String ACTION_DATA_UPDATED = "com.brainwatch.DATA_UPDATED";

    private static TrackerService instance;

    private static final long HEARTBEAT_MS = 2000;
    private static final long IMPULSE_MS = 400;
    private static final long CLICK_DEBOUNCE_MS = 150;
    private static final long USAGE_CACHE_MS = 10000;
    private static final long FG_QUERY_WINDOW_MS = 60000;
    private static final long IDLE_GRACE_MS = 3000;
    private static final long AWAY_FINALIZE_MS = 8000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            beat();
            handler.postDelayed(this, HEARTBEAT_MS);
        }
    };

    private SessionStore store;
    private String today;
    private OverlayController overlay;
    private SessionReviewPopup reviewPopup;
    private boolean overlayVisible;

    private boolean inSession;
    private long sessionStart;
    private long lastEventTime;
    private int sessionScrolls;
    private int sessionReels;
    private int sessionClicks;
    private long lastClickTs = 0;

    // per-finger-gesture tracking (impulses separated by short idles):
    // a fresh impulse = one scroll; accumulated travel = reels
    private long lastScrollTs = 0;
    private int impulseDelta = 0;

    // foreground tracking (UsageStats)
    private long lastFgTs = 0;
    private boolean fgIsIg = false;
    private long awaySince = 0;

    // thresholds (screen-height based)
    private int minFlickPx = 1450;    // feed: a swipe covers ~75% of screen -> reel
    private int minReelPx = 760;      // reels pager: only ~40% needed (page-snap swipes)

    // break-reminder
    private long nextReviewAt = 0;
    private long appliedIntervalMs = 0;

    // fatigue cache
    private long lastUsageCache = 0;
    private long cachedUsageMs = -1;
    private int cachedFatigue = 0;

    // ---------------- public accessors ----------------
    public static TrackerService getInstance() {
        return instance;
    }

    public static boolean isInSession() {
        return instance != null && instance.inSession;
    }

    public static long sessionStartMs() {
        return instance == null ? 0 : instance.sessionStart;
    }

    public static long sessionScrollsNow() {
        return instance == null ? 0 : instance.sessionScrolls;
    }

    public static long sessionReelsNow() {
        return instance == null ? 0 : instance.sessionReels;
    }

    public static long sessionClicksNow() {
        return instance == null ? 0 : instance.sessionClicks;
    }

    @Override
    public void onServiceConnected() {
        instance = this;
        store = new SessionStore(this);
        today = SessionStore.todayKey();
        overlay = new OverlayController(this);
        reviewPopup = new SessionReviewPopup(this);

        int screenH = getResources().getDisplayMetrics().heightPixels;
        minFlickPx = Math.round(screenH * 0.75f);
        minReelPx = Math.round(screenH * 0.40f);

        handler.removeCallbacks(heartbeat);
        handler.post(heartbeat);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null
                || !IG.equalsIgnoreCase(event.getPackageName().toString())) {
            return;
        }
        long now = System.currentTimeMillis();
        ensureSessionOpen(now);
        lastEventTime = now;
        store.touchActive(today);

        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            trackScroll(now, event.getScrollDeltaY(), event.getClassName() == null ? null : event.getClassName().toString());
            if (overlayVisible) {
                overlay.setCounts(sessionScrolls, sessionReels, sessionClicks);
            }
        } else if (type == AccessibilityEvent.TYPE_VIEW_CLICKED
                || type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            trackClick(now);
            if (overlayVisible) {
                overlay.setCounts(sessionScrolls, sessionReels, sessionClicks);
            }
        }
    }

    /**
     * One physical finger gesture = one impulse (events whose scroll timestamps
     * are farther apart than 400ms belong to different gestures). Every new
     * impulse is one scroll, so each quick feed-flick counts as its own scroll
     * even when flicks arrive faster than 1.5s apart. A slow held-down drag
     * (continuous events < 400ms apart) still counts as exactly one scroll.
     *
     * Reels: accumulated finger travel becomes a reel once it crosses a
     * threshold - ~40% of screen on the Reels pager, ~75% on the Feed so big
     * fast feed-flings don't miscount. Each counted reel resets the travel so
     * back-to-back swipes each count exactly once.
     */
    private void trackScroll(long now, int dy, String cls) {
        if (dy == 0) {
            return;
        }
        if (now - lastScrollTs > IMPULSE_MS) {
            impulseDelta = 0;
            sessionScrolls++;
        }
        lastScrollTs = now;
        impulseDelta += dy;

        boolean pagerUi = cls != null && (cls.contains("Reel") || cls.contains("Pager"));
        int threshold = pagerUi ? minReelPx : minFlickPx;
        if (Math.abs(impulseDelta) >= threshold) {
            sessionReels++;
            impulseDelta = 0;
        }
    }

    /**
     * One on-screen tap. Instagram fires a CLICKED event per interactive view,
     * so a single finger tap can emit several events within milliseconds
     * (e.g. a row and its inner button). Events closer together than 150ms are
     * treated as one physical tap; genuine rapid double-taps (usually 200ms+)
     * each still count, so tap totals stay close to what the thumb actually did.
     */
    private void trackClick(long now) {
        if (now - lastClickTs < CLICK_DEBOUNCE_MS) {
            return;
        }
        lastClickTs = now;
        sessionClicks++;
    }

    private void beat() {
        today = SessionStore.todayKey();
        long now = System.currentTimeMillis();

        updateForeground(now);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean screenOn = pm == null || pm.isInteractive();

        boolean fgIsIgNow = fgIsIg;
        boolean recentActivity = now - lastEventTime < IDLE_GRACE_MS;

        if (fgIsIgNow) {
            ensureSessionOpen(Math.max(lastFgTs, now - 4000));
            store.touchActive(today);
        }

        if (screenOn) {
            if (fgIsIgNow || recentActivity) {
                if (!overlayVisible && overlay.canShow()) {
                    overlay.shownSessionSince(sessionStart, true);
                    overlay.show();
                    overlayVisible = true;
                }
            } else {
                if (overlayVisible) {
                    overlay.hide();
                    overlayVisible = false;
                }
            }
        } else {
            if (overlayVisible) {
                overlay.hide();
                overlayVisible = false;
            }
            if (inSession && now - sessionStart > 60000) {
                finalizeSession("screen-lock");
            }
        }

        if (inSession && !fgIsIgNow && !recentActivity) {
            if (awaySince == 0) {
                awaySince = now;
            } else if (now - awaySince >= AWAY_FINALIZE_MS) {
                if (now - sessionStart <= AWAY_FINALIZE_MS) {
                    resetSession();
                } else {
                    finalizeSession("away");
                }
            }
        } else {
            awaySince = 0;
        }

        // ---- break reminder ----
        if (inSession && screenOn) {
            long intervalMs = store.reminderEnabled() ? store.reviewIntervalMs() : 0L;
            if (intervalMs != appliedIntervalMs) {
                // timer changed mid-session -> re-anchor to this session
                appliedIntervalMs = intervalMs;
                if (intervalMs > 0) {
                    nextReviewAt = sessionStart + intervalMs;
                    while (nextReviewAt <= now) {
                        nextReviewAt += intervalMs;
                    }
                }
            }
            if (intervalMs > 0 && now >= nextReviewAt && overlay != null
                    && overlay.canShow() && !reviewPopup.isShowing()) {
                maybeRefreshFatigue(now);
                showReview(now, intervalMs);
            }
        }

        if (overlayVisible) {
            overlay.setCounts(sessionScrolls, sessionReels, sessionClicks);
            maybeRefreshFatigue(now);
            if (cachedFatigue >= 0) {
                overlay.setFatigue(cachedFatigue);
            }
            WidgetUpdater.update(this);
        } else {
            pushWidgetChanged();
        }
    }

    private void showReview(long now, long intervalMs) {
        long elapsed = now - sessionStart;
        maybeRefreshFatigue(now);
        final long interval = intervalMs;
        Runnable remindAgain = () -> {
            nextReviewAt = System.currentTimeMillis() + interval;
            appliedIntervalMs = store.reviewIntervalMs();
        };
        reviewPopup.show(sessionStart, sessionScrolls, sessionReels, sessionClicks,
                elapsed,
                intervalMs, cachedFatigue,
                remindAgain,   // onKeep -> reminds again at the next interval
                () -> { // onClose -> Exit app: bank the session and leave Instagram
                    int scrolls = sessionScrolls;
                    int reels = sessionReels;
                    long mins = Math.max(0, (System.currentTimeMillis() - sessionStart) / 60000);
                    int dopa = Fatigue.dopamineUnits(scrolls, reels, mins);
                    finalizeSession("review-exit");
                    Toast.makeText(TrackerService.this,
                            "Stepped away ✓  ~" + Fatigue.dopamineText(dopa)
                                    + " reward pulses banked. Your brain says thanks 🧠",
                            Toast.LENGTH_LONG).show();
                    try {
                        performGlobalAction(GLOBAL_ACTION_HOME);
                    } catch (Exception ignored) {
                    }
                    try {
                        Intent home = new Intent(Intent.ACTION_MAIN);
                        home.addCategory(Intent.CATEGORY_HOME);
                        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(home);
                    } catch (Exception ignored) {
                    }
                },
                remindAgain); // onTimeout -> auto-dismiss behaves like Keep scrolling
    }

    private void updateForeground(long now) {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            return;
        }
        try {
            UsageEvents events = usm.queryEvents(now - FG_QUERY_WINDOW_MS, now);
            if (events == null) {
                return;
            }
            UsageEvents.Event e = new UsageEvents.Event();
            while (events.getNextEvent(e)) {
                int t = e.getEventType();
                if ((t == UsageEvents.Event.MOVE_TO_FOREGROUND
                        || t == UsageEvents.Event.ACTIVITY_RESUMED)
                        && e.getPackageName() != null) {
                    fgIsIg = IG.equalsIgnoreCase(e.getPackageName());
                    lastFgTs = e.getTimeStamp();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void ensureSessionOpen(long start) {
        if (!inSession) {
            inSession = true;
            sessionStart = start;
            sessionScrolls = 0;
            sessionReels = 0;
            sessionClicks = 0;
            impulseDelta = 0;
            lastEventTime = start;
            appliedIntervalMs = store.reminderEnabled() ? store.reviewIntervalMs() : 0L;
            nextReviewAt = start + appliedIntervalMs;
        }
    }

    private void resetSession() {
        inSession = false;
        sessionStart = 0;
        sessionScrolls = 0;
        sessionReels = 0;
        sessionClicks = 0;
        impulseDelta = 0;
        if (reviewPopup != null && reviewPopup.isShowing()) {
            reviewPopup.dismiss();
        }
    }

    private void finalizeSession(String reason) {
        if (!inSession) {
            return;
        }
        long now = System.currentTimeMillis();
        SessionStore.Session s = new SessionStore.Session();
        s.start = sessionStart;
        s.end = Math.max(now, sessionStart + 1);
        s.scrolls = sessionScrolls;
        s.reels = sessionReels;
        s.clicks = sessionClicks;
        store.addSession(s, today);
        store.tally(today, sessionScrolls, sessionReels, sessionClicks);
        store.touchActive(today);
        resetSession();
        sendBroadcast(new Intent(ACTION_DATA_UPDATED));
    }

    private void maybeRefreshFatigue(long now) {
        if (now - lastUsageCache < USAGE_CACHE_MS) {
            return;
        }
        lastUsageCache = now;
        cachedUsageMs = UsageHelper.foregroundMsToday(this, IG);
        int totSc = store.totalScrolls(today) + sessionScrolls;
        int totRl = store.totalReels(today) + sessionReels;
        long lastActive = store.lastActive(today);
        if (inSession) {
            lastActive = Math.max(lastActive, sessionStart);
        }
        cachedFatigue = Fatigue.compute(now,
                cachedUsageMs > 0 ? cachedUsageMs / 60000.0 : 0.0, totSc, totRl, lastActive);
    }

    private void pushWidgetChanged() {
        String payload = (inSession ? "1" : "0") + "|"
                + sessionScrolls + "|" + sessionReels + "|" + sessionClicks + "|"
                + (inSession ? sessionStart : 0);
        if (!payload.equals(lastPayload)) {
            lastPayload = payload;
            WidgetUpdater.update(this);
        }
    }

    private String lastPayload = "";

    @Override
    public void onInterrupt() {
        // system interruption; heartbeat will handle state
    }

    @Override
    public void onDestroy() {
        finalizeSession("destroy");
        if (overlay != null && overlayVisible) {
            overlay.hide();
            overlayVisible = false;
        }
        if (reviewPopup != null && reviewPopup.isShowing()) {
            reviewPopup.dismiss();
        }
        handler.removeCallbacks(heartbeat);
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }
}
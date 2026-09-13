package com.brainwatch;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.Locale;

/**
 * Builds and pushes the home-screen widget. Day-level numbers are cached and
 * refreshed at most every 5s; the whole RemoteView is rebuilt at most every 3s
 * so the elapsed session timer stays fairly live without chewing battery.
 */
public final class WidgetUpdater {

    private static long lastDayRefresh = 0;
    private static long lastBuild = 0;
    private static long cachedUsageMs = -1;
    private static long cachedTotSc = 0;
    private static long cachedTotRl = 0;
    private static long cachedTotTaps = 0;
    private static long cachedSessions = 0;
    private static int cachedFatigue = 0;

    private WidgetUpdater() {
    }

    public static void update(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        ComponentName cn = new ComponentName(ctx, TrackerWidget.class);
        int[] ids = mgr.getAppWidgetIds(cn);
        if (ids.length == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        refreshDayIfStale(ctx, now);

        boolean inSession = TrackerService.isInSession();
        long sc = inSession ? TrackerService.sessionScrollsNow() : 0;
        long rl = inSession ? TrackerService.sessionReelsNow() : 0;
        long taps = inSession ? TrackerService.sessionClicksNow() : 0;

        long key = (inSession ? 1L : 0L) | (sc << 8) | (rl << 24) | (taps << 40) | (inSession
                ? TrackerService.sessionStartMs() / 1000 : 0);
        boolean stale = key != lastKey || now - lastBuild > 3000;
        if (!stale) {
            return;
        }
        lastKey = key;
        lastBuild = now;

        RemoteViews rv = build(ctx, now, inSession, sc, rl, taps);
        mgr.updateAppWidget(ids, rv);
    }

    private static long lastKey = Long.MIN_VALUE;

    private static void refreshDayIfStale(Context ctx, long now) {
        if (now - lastDayRefresh < 5000) {
            return;
        }
        lastDayRefresh = now;
        SessionStore store = new SessionStore(ctx);
        String day = SessionStore.todayKey();
        cachedUsageMs = UsageHelper.foregroundMsToday(ctx, TrackerService.IG);
        cachedTotSc = store.totalScrolls(day);
        cachedTotRl = store.totalReels(day);
        cachedTotTaps = store.totalClicks(day);
        cachedSessions = store.sessions(day).size();
        long la = store.lastActive(day);
        long st = TrackerService.sessionStartMs();
        if (st > 0) {
            la = Math.max(la, st);
        }
        long liveSc = TrackerService.sessionScrollsNow();
        long liveRl = TrackerService.sessionReelsNow();
        cachedFatigue = Fatigue.compute(now,
                cachedUsageMs > 0 ? cachedUsageMs / 60000.0 : 0.0,
                (int) (cachedTotSc + liveSc), (int) (cachedTotRl + liveRl), la);
    }

    private static RemoteViews build(Context ctx, long now, boolean inSession, long sc, long rl, long taps) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_tracker);
        rv.setTextViewText(R.id.wStatus, inSession ? "● LIVE" : "idle");

        String snap;
        if (inSession) {
            long el = now - TrackerService.sessionStartMs();
            snap = fmtDuration(el) + "  ·  " + sc + " scrolls  ·  " + rl + " reels  ·  " + taps + " taps";
        } else {
            snap = "No active session";
        }
        rv.setTextViewText(R.id.wSnapshot, snap);

        String todayLine = "Today: " + fmtDuration(cachedUsageMs > 0 ? cachedUsageMs : 0)
                + " on IG · " + cachedTotSc + " sc · " + cachedTotRl + " rl"
                + " · " + cachedTotTaps + " taps · " + cachedSessions + " sessions · 🧠 " + cachedFatigue + "%";
        rv.setTextViewText(R.id.wToday, todayLine);

        Intent open = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (open != null) {
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            rv.setOnClickPendingIntent(R.id.wRoot, PendingIntent.getActivity(ctx, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        return rv;
    }

    public static String fmtDuration(long ms) {
        long totalSec = ms / 1000;
        if (totalSec < 60) {
            return totalSec + "s";
        }
        long min = totalSec / 60;
        if (min < 60) {
            return min + "m";
        }
        return (min / 60) + "h " + (min % 60) + "m";
    }
}
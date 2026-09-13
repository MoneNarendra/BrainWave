package com.brainwatch;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;

import java.util.Calendar;

public final class UsageHelper {

    private UsageHelper() {
    }

    public static boolean hasUsageAccess(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            AppOpsManager appOps = context.getSystemService(AppOpsManager.class);
            int mode = appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        }
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static long startOfDayMs() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /**
     * Foreground time spent in {@code pkg} since the start of today, computed by merging
     * UsageEvents reachability segments. Returns -1 if Usage Access is not granted.
     */
    public static long foregroundMsToday(Context context, String pkg) {
        if (!hasUsageAccess(context)) {
            return -1;
        }
        UsageStatsManager usm =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        long end = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(startOfDayMs(), end);
        if (events == null) {
            return -1;
        }

        long acc = 0;
        long segStart = 0;
        boolean active = false;
        String target = pkg.toLowerCase().trim();
        UsageEvents.Event e = new UsageEvents.Event();

        while (events.getNextEvent(e)) {
            if (e.getPackageName() == null) {
                continue;
            }
            String p = e.getPackageName().toLowerCase().trim();
            boolean isTarget = p.equals(target);
            long ts = e.getTimeStamp();
            int type = e.getEventType();

            if (isTarget && (type == UsageEvents.Event.ACTIVITY_RESUMED || type == UsageEvents.Event.MOVE_TO_FOREGROUND)) {
                if (!active) {
                    active = true;
                    segStart = ts;
                }
            } else if (type == UsageEvents.Event.ACTIVITY_PAUSED || type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                if (active && isTarget) {
                    acc += ts - segStart;
                    active = false;
                }
            } else if (type == UsageEvents.Event.ACTIVITY_RESUMED || type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (active && !isTarget) {
                    acc += ts - segStart;
                    active = false;
                }
            }
        }
        if (active) {
            acc += end - segStart;
        }
        return Math.max(0, acc);
    }
}
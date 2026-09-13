package com.brainwatch;

import java.util.Locale;

/**
 * Heuristic "dopamine fatigue" model, 0..100.
 *
 * Each minute on Instagram, each scroll and especially each reel-fired approval hit
 * accumulates stress on the reward system. When you put the phone down, the brain
 * recovers over time (exponential decay).
 */
public final class Fatigue {

    private Fatigue() {
    }

    public static int compute(long nowMs, double timeMinutes, int scrolls, int reels, long lastActiveMs) {
        double timeF = Math.max(0.0, Math.min(240.0, timeMinutes)) / 240.0 * 70.0;
        double scrollF = Math.min(800, scrolls) / 800.0 * 10.0;
        double reelF = Math.min(120, reels) / 120.0 * 20.0;

        double raw = timeF + scrollF + reelF;

        double decay = 1.0;
        if (lastActiveMs > 0) {
            long idleMin = Math.max(0, (nowMs - lastActiveMs) / 60000L);
            decay = Math.pow(0.985, Math.min(idleMin, 600));
            if (decay < 0.12) {
                decay = 0.12;
            }
        }
        double val = raw * decay;
        val = Math.max(0.0, Math.min(100.0, val));
        return (int) Math.round(val);
    }

    public static String stateLabel(int fatigue) {
        if (fatigue < 26) {
            return "Fresh & Focused";
        } else if (fatigue < 51) {
            return "Buzzing";
        } else if (fatigue < 76) {
            return "Fuzzy";
        }
        return "Overloaded";
    }

    public static String stateDesc(int fatigue) {
        if (fatigue < 26) {
            return "Your reward system is recharged. Great time to do deep work.";
        } else if (fatigue < 51) {
            return "You are grinding through some dopamine. Watch the scrolls.";
        } else if (fatigue < 76) {
            return "Scrolling fatigue is creeping in. Short reels feel good but your brain is draining.";
        }
        return "Overloaded. Put the phone down, walk, and let your dopamine reset.";
    }

    /**
     * Rough "reward pulses" estimate for a single session: time on feed drips
     * dopamine, deliberate scrolls spike it a little, reels spike it a lot —
     * that's exactly why Reels hijack the reward system.
     */
    public static int dopamineUnits(int scrolls, int reels, long minutes) {
        return scrolls * 2 + reels * 35 + (int) Math.min(minutes, 600) * 12;
    }

    public static String dopamineText(int units) {
        if (units >= 1000) {
            double k = units / 1000.0;
            return String.format(Locale.US, "%.1fk", k);
        }
        return String.valueOf(units);
    }
}
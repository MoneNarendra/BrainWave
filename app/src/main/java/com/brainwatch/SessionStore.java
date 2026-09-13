package com.brainwatch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SessionStore {

    public static class Session {
        public long start;
        public long end;
        public int scrolls;
        public int reels;
        public int clicks;

        public long duration() {
            return Math.max(0, end - start);
        }
    }

    private static final String PREFS = "brainwatch";
    private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private final SharedPreferences sp;

    public SessionStore(Context context) {
        sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String todayKey() {
        return DAY.format(Calendar.getInstance().getTime());
    }

    public void addSession(Session s, String day) {
        synchronized (this) {
            JSONArray arr = readArray("sessions_" + day);
            try {
                JSONObject o = new JSONObject();
                o.put("s", s.start);
                o.put("e", s.end);
                o.put("sc", s.scrolls);
                o.put("r", s.reels);
                o.put("c", s.clicks);
                arr.put(o);
                sp.edit().putString("sessions_" + day, arr.toString()).apply();
            } catch (Exception ignored) {
            }
        }
    }

    public void tally(String day, int scrolls, int reels, int clicks) {
        synchronized (this) {
            sp.edit()
                    .putInt("scrolls_" + day, sp.getInt("scrolls_" + day, 0) + scrolls)
                    .putInt("reels_" + day, sp.getInt("reels_" + day, 0) + reels)
                    .putInt("clicks_" + day, sp.getInt("clicks_" + day, 0) + clicks)
                    .apply();
        }
    }

    public void touchActive(String day) {
        sp.edit().putLong("lastActive_" + day, System.currentTimeMillis()).apply();
    }

    public long reviewIntervalMs() {
        return sp.getLong("review_interval_ms", 10L * 60L * 1000L);
    }

    public void setReviewIntervalMs(long ms) {
        sp.edit().putLong("review_interval_ms", Math.max(5000L, ms)).apply();
    }

    public boolean reminderEnabled() {
        return sp.getBoolean("reminder_enabled", true);
    }

    public void setReminderEnabled(boolean on) {
        sp.edit().putBoolean("reminder_enabled", on).apply();
    }

    public long lastActive(String day) {
        return sp.getLong("lastActive_" + day, 0L);
    }

    public int totalScrolls(String day) {
        return sp.getInt("scrolls_" + day, 0);
    }

    public int totalReels(String day) {
        return sp.getInt("reels_" + day, 0);
    }

    public int totalClicks(String day) {
        return sp.getInt("clicks_" + day, 0);
    }

    public List<Session> sessions(String day) {
        List<Session> out = new ArrayList<>();
        try {
            JSONArray arr = readArray("sessions_" + day);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Session s = new Session();
                s.start = o.getLong("s");
                s.end = o.getLong("e");
                s.scrolls = o.getInt("sc");
                s.reels = o.getInt("r");
                s.clicks = o.optInt("c", 0);
                out.add(s);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private JSONArray readArray(String key) {
        try {
            return new JSONArray(sp.getString(key, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }
}
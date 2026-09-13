package com.brainwatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int TICK_MS = 1000;
    private static final int LIST_REFRESH_EVERY = 8;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private SessionStore store;
    private String today;
    private int lastShownScrolls = -1;
    private int lastShownReels = -1;
    private int lastShownClicks = -1;
    private int lastShownFatigue = -1;
    private int ticks = 0;

    private BrainView brain;
    private TextView txtFatiguePct, txtFatigueLabel, txtFatigueDesc;
    private TextView txtTime, txtScrolls, txtReels, txtClicks, txtSessionsTitle;
    private TextView txtCurStatus, txtCurTime, txtCurDetails;
    private TextView txtPermNote;
    private Button btnUsage, btnAccess, btnOverlay;
    private TextView tvReminder;
    private Button btnSetReminder;
    private Switch swReminder;
    private TextView tvReminderStatus;
    private LinearLayout listSessions;
    private boolean selfToggle = false;

    private final BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = new SessionStore(this);
        today = SessionStore.todayKey();

        bindViews();
        setupButtons();
        refresh();

        View root = findViewById(R.id.rootScroll);
        if (root != null) {
            root.setAlpha(0f);
            root.animate().alpha(1f).setDuration(500).start();
        }
    }

    private void bindViews() {
        brain = findViewById(R.id.brainView);
        txtFatiguePct = findViewById(R.id.txtFatiguePct);
        txtFatigueLabel = findViewById(R.id.txtFatigueLabel);
        txtFatigueDesc = findViewById(R.id.txtFatigueDesc);
        txtTime = findViewById(R.id.txtTime);
        txtScrolls = findViewById(R.id.txtScrolls);
        txtReels = findViewById(R.id.txtReels);
        txtClicks = findViewById(R.id.txtClicks);
        txtSessionsTitle = findViewById(R.id.txtSessionsTitle);
        txtCurStatus = findViewById(R.id.txtCurStatus);
        txtCurTime = findViewById(R.id.txtCurTime);
        txtCurDetails = findViewById(R.id.txtCurDetails);
        txtPermNote = findViewById(R.id.txtPermNote);
        btnUsage = findViewById(R.id.btnUsage);
        btnAccess = findViewById(R.id.btnAccess);
        txtPermNote = findViewById(R.id.txtPermNote);
        btnUsage = findViewById(R.id.btnUsage);
        btnAccess = findViewById(R.id.btnAccess);
        btnOverlay = findViewById(R.id.btnOverlay);
        tvReminder = findViewById(R.id.tvReminder);
        btnSetReminder = findViewById(R.id.btnSetReminder);
        swReminder = findViewById(R.id.swReminder);
        tvReminderStatus = findViewById(R.id.tvReminderStatus);
        listSessions = findViewById(R.id.listSessions);
    }

    private void setupButtons() {
        btnUsage.setOnClickListener(v -> {
            if (!UsageHelper.hasUsageAccess(this)) {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            }
        });
        btnAccess.setOnClickListener(v -> {
            if (!isAccessibilityEnabled()) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
        btnOverlay.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } else if (Build.VERSION.SDK_INT < 23) {
                btnOverlay.setText(R.string.btn_ok);
                btnOverlay.setEnabled(false);
            }
        });
        btnSetReminder.setOnClickListener(v -> showReminderPicker());
        swReminder.setOnCheckedChangeListener((btn, isChecked) -> {
            if (selfToggle) {
                return;
            }
            store.setReminderEnabled(isChecked);
            refresh();
        });
    }

    private String fmtReminder(long ms) {
        long totalSec = ms / 1000L;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) {
            return m > 0 ? h + " hr " + m + " min" : h + " hr";
        } else if (m > 0) {
            return s > 0 ? m + " min " + s + " sec" : m + " min";
        }
        return s + " sec";
    }

    private void showReminderPicker() {
        long cur = Math.max(5000L, store.reviewIntervalMs());
        NumberPicker hp = new NumberPicker(this);
        NumberPicker mp = new NumberPicker(this);
        NumberPicker sp = new NumberPicker(this);
        int h = (int) (cur / 3600000L);
        int m = (int) ((cur % 3600000L) / 60000L);
        int s = (int) ((cur % 60000L) / 1000L);
        hp.setMinValue(0);
        hp.setMaxValue(23);
        hp.setValue(Math.min(h, 23));
        mp.setMinValue(0);
        mp.setMaxValue(59);
        mp.setValue(m);
        sp.setMinValue(0);
        sp.setMaxValue(59);
        sp.setValue(s);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams col = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams col2 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams col3 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(hp, col);
        row.addView(mp, col2);
        row.addView(sp, col3);

        new AlertDialog.Builder(this)
                .setTitle("Remind me every")
                .setMessage("Hours / Minutes / Seconds")
                .setView(row)
                .setPositiveButton("Save", (dlg, which) -> {
                    long ms = (hp.getValue() * 3600L + mp.getValue() * 60L + sp.getValue()) * 1000L;
                    if (ms < 5000L) {
                        ms = 5000L;
                    }
                    store.setReviewIntervalMs(ms);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean canOverlay() {
        return Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(this);
    }

    private boolean isAccessibilityEnabled() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabled != null && enabled.contains("com.brainwatch.TrackerService");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 0);
        IntentFilter filter = new IntentFilter(TrackerService.ACTION_DATA_UPDATED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(dataReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(dataReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
        unregisterReceiver(dataReceiver);
    }

    private void refresh() {
        today = SessionStore.todayKey();

        boolean usageGranted = UsageHelper.hasUsageAccess(this);
        boolean accessEnabled = isAccessibilityEnabled();

        long usageMs = UsageHelper.foregroundMsToday(this, TrackerService.IG);
        long liveScrolls = TrackerService.sessionScrollsNow();
        long liveReels = TrackerService.sessionReelsNow();
        long liveClicks = TrackerService.sessionClicksNow();
        int totScrolls = store.totalScrolls(today) + (int) liveScrolls;
        int totReels = store.totalReels(today) + (int) liveReels;
        int totClicks = store.totalClicks(today) + (int) liveClicks;
        List<SessionStore.Session> sessions = store.sessions(today);

        long lastActive = store.lastActive(today);
        if (liveScrolls > 0 || liveReels > 0) {
            lastActive = Math.max(lastActive, TrackerService.sessionStartMs() > 0
                    ? TrackerService.sessionStartMs() : lastActive);
        }

        double timeMin = usageMs > 0 ? usageMs / 60000.0 : 0.0;
        int fatigue = Fatigue.compute(System.currentTimeMillis(), timeMin, totScrolls, totReels, lastActive);

        // ---- brain ----
        brain.setFatigue(fatigue / 100f);
        if (lastShownReels >= 0 && totReels > lastShownReels) {
            brain.reelFlash();
            pop(txtReels);
        }
        if (lastShownScrolls >= 0 && totScrolls > lastShownScrolls) {
            pop(txtScrolls);
        }
        if (lastShownClicks >= 0 && totClicks > lastShownClicks && txtClicks != null) {
            pop(txtClicks);
        }
        if (lastShownFatigue >= 0 && fatigue != lastShownFatigue) {
            pop(txtFatiguePct);
        }
        lastShownScrolls = totScrolls;
        lastShownReels = totReels;
        lastShownClicks = totClicks;
        lastShownFatigue = fatigue;
        WidgetUpdater.update(this);

        // ---- texts ----
        txtFatiguePct.setText(String.format(Locale.US, "%d%%", fatigue));
        txtFatigueLabel.setText(Fatigue.stateLabel(fatigue));
        txtFatigueDesc.setText(Fatigue.stateDesc(fatigue));

        txtTime.setText(formatDuration(usageMs < 0 ? 0 : usageMs));
        txtScrolls.setText(String.valueOf(totScrolls));
        txtReels.setText(String.valueOf(totReels));
        txtClicks.setText(String.valueOf(totClicks));
        txtSessionsTitle.setText("Recent sessions (" + sessions.size() + " today)");

        // ---- current session ----
        if (TrackerService.isInSession() && TrackerService.sessionStartMs() > 0) {
            long now = System.currentTimeMillis();
            txtCurStatus.setText("On Instagram now  •  " + (totScrolls > 0 || totReels > 0 ? "active" : "idle"));
            txtCurTime.setText("This session: " + formatDuration(now - TrackerService.sessionStartMs()));
            txtCurDetails.setText(liveScrolls + " scrolls  ·  " + liveReels + " reels  ·  "
                    + liveClicks + " taps");
        } else {
            txtCurStatus.setText(getString(R.string.cur_none));
            txtCurTime.setText("");
            txtCurDetails.setText("");
        }

        // ---- permission buttons ----
        boolean overlayGranted = canOverlay();
        btnUsage.setText(String.format(Locale.US, "%s  %s",
                getString(R.string.btn_usage), usageGranted ? "✓" : ""));
        btnAccess.setText(String.format(Locale.US, "%s  %s",
                getString(R.string.btn_access), accessEnabled ? "✓" : ""));
        btnOverlay.setText(String.format(Locale.US, "%s  %s",
                getString(R.string.btn_overlay), overlayGranted ? "✓" : ""));
        btnUsage.setEnabled(!usageGranted);
        btnAccess.setEnabled(!accessEnabled);
        btnOverlay.setEnabled(!overlayGranted);
        if (usageGranted && accessEnabled && overlayGranted) {
            txtPermNote.setText(getString(R.string.perm_note_all));
        } else {
            txtPermNote.setText(getString(R.string.perm_note));
        }

        // show reminder interval value + active/paused status
        boolean enabled = store.reminderEnabled();
        selfToggle = true;
        swReminder.setChecked(enabled);
        selfToggle = false;
        tvReminder.setText(fmtReminder(store.reviewIntervalMs()));
        tvReminderStatus.setTextColor(enabled ? Color.rgb(111, 231, 199) : Color.rgb(156, 147, 188));
        tvReminderStatus.setText(enabled ? "● ACTIVE — every " + fmtReminder(store.reviewIntervalMs())
                : "○ PAUSED");

        // ---- sessions list ----
        ticks++;
        if (ticks % LIST_REFRESH_EVERY == 1 || ticks == 1) {
            int count = sessions.size();
            int shownCount = listSessions.getChildCount();
            if (count == 0) {
                if (shownCount == 0) {
                    TextView h = emptySessionView();
                    listSessions.addView(h);
                }
            } else {
                if (shownCount == 1 && listSessions.getChildAt(0).getTag() == null) {
                    listSessions.removeAllViews();
                    shownCount = 0;
                }
                int n = Math.min(count, 12);
                for (int i = 0; i < n; i++) {
                    SessionStore.Session s = sessions.get(count - 1 - i);
                    if (i < shownCount) {
                        ((TextView) listSessions.getChildAt(i)).setText(sessionText(s));
                    } else {
                        TextView row = sessionRow();
                        row.setText(sessionText(s));
                        listSessions.addView(row);
                    }
                }
            }
        }
    }

    private String sessionText(SessionStore.Session s) {
        String start = new SimpleDateFormat("h:mm a", Locale.US).format(new Date(s.start));
        return start + "   ·   " + formatDuration(s.duration())
                + "   ·   " + s.scrolls + " scrolls"
                + "   ·   " + s.reels + " reels"
                + "   ·   " + s.clicks + " taps";
    }

    private TextView sessionRow() {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        tv.setLayoutParams(lp);
        tv.setPadding(dp(16), dp(14), dp(16), dp(14));
        tv.setTextSize(13);
        tv.setTextColor(Color.rgb(237, 234, 246));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(Color.rgb(34, 28, 51));
        bg.setStroke(dp(1), Color.rgb(59, 51, 87));
        tv.setBackground(bg);
        return tv;
    }

    private TextView emptySessionView() {
        TextView tv = new TextView(this);
        tv.setText(getString(R.string.sessions_empty));
        tv.setTextColor(Color.rgb(156, 147, 188));
        tv.setTextSize(13);
        tv.setPadding(dp(4), dp(8), dp(4), dp(8));
        return tv;
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private void pop(TextView tv) {
        tv.animate().cancel();
        tv.setScaleX(1.4f);
        tv.setScaleY(1.4f);
        tv.animate().scaleX(1f).scaleY(1f)
                .setDuration(320)
                .setInterpolator(new OvershootInterpolator(1.7f))
                .start();
    }

    private static String formatDuration(long ms) {
        long totalSec = ms / 1000;
        if (totalSec < 60) {
            return totalSec + "s";
        }
        long min = totalSec / 60;
        if (min < 60) {
            return min + "m";
        }
        long h = min / 60;
        long m = min % 60;
        return h + "h " + m + "m";
    }
}
# 🧠 BrainWatch

**Vibe-coded dopamine fatigue tracker for Android.**

BrainWatch sits quietly in the background, watches how you use **Instagram**, and turns it into a
living estimate of how drained your reward system is. Because your brain deserves a break.

- **100% on-device.** No accounts, no servers, no data leaves your phone.
- **Zero dependencies.** Pure Android framework SDK — no AndroidX, no Volley, no libraries.

---

## Features

- **Session tracking** — how long you've been on Instagram, split into sessions
- **Scroll / reel / tap counting** via an accessibility service (raw system events, no magic)
- **Animated brain** whose tiredness matches your scrolling — it sags, droops, slows down and
  eventually starts dozing off (`Zzz...`) as fatigue climbs
- **Floating live chip** over Instagram:
  - session timer
  - live scroll / reel / tap counters
  - fatigue %
  - draggable, with a **green → red "heat" dot** that reacts to every scroll
- **Break reminder** — choose *any* interval (hours : minutes : seconds, toggleable on/off) and
  BrainWatch pops a check-in card over the app with your live stats, a dopamine estimate and two
  choices: **Keep scrolling** or **Exit app** (sends you back to the home screen)
- **Home-screen widget** with your live session and today's totals
- **Session history** — every session logged with time, scrolls, reels and taps
- A rough **dopamine estimate** per session: `scrolls × 2 + reels × 35 + minutes × 12`

## The "heat" dot

Every scroll is a tiny dopamine hit. The floating chip's dot starts **green** (fresh brain) and
glides to **red** as the session racks up attention-grabbing input — reels count ×3 because
they're the biggest spikes. A live, honest mirror of what your thumb is doing to you.

## How the counting works (no AI, just Android)

| What | How |
|---|---|
| **Scrolls** | `TYPE_VIEW_SCROLLED` events grouped into finger-gesture "impulses" (400 ms idle = new gesture). One gesture = one scroll, so rapid feed-flicks don't collapse together. |
| **Reels** | The scrolled view's class is checked — Instagram's Reel grid is a pager (`RecyclerPager`), the Feed isn't. Crossing 40% of the screen on the pager = one reel; on the Feed a full 75% must be crossed so fast feed-flings don't miscount. |
| **Taps** | `TYPE_VIEW_CLICKED` + long-press events, debounced at 150 ms so the several click-events one physical tap produces count as **one** tap, while a real double-tap still counts as two. |
| **Time / sessions** | `UsageStatsManager` foreground tracking for `com.instagram.android`. The "on Instagram" state only changes when usage events actually show Instagram arriving/leaving the foreground, and a short away-grace keeps your counts if you duck out briefly. |

**Fatigue model** — time on feed, scrolls and reels push fatigue up (0–100); idle minutes let it
decay: `timeF + scrollF + reelF` scaled by `0.985^idleMinutes`.

## Permissions & privacy

- **Usage access** — measures how long you're actually on Instagram (aggregate package-level only)
- **Accessibility service** — receives scroll / click events from Instagram to count them, and
  reads the layout enough to tell Reels from the Feed. Used for nothing else
- **Display over other apps** — the floating chip and break-reminder popup

The app makes **no network calls**. Everything is stored in local `SharedPreferences` / JSON.

## Tech stack

- Pure Android framework SDK — **zero external dependencies**
- Java 17, custom `View` (`BrainView`) for the animated brain, `Canvas`-drawn
- `AccessibilityService`, `UsageStatsManager`, `WindowManager` overlays, `AppWidgetProvider`
- `SharedPreferences` + JSON for local session storage
- minSdk 24, target/compileSdk 34, AGP 8.5.2, Gradle 8.7

## Build it yourself

```bash
git clone <your-repo-url>
cd <project-dir>
gradle assembleRelease      # or use the Gradle wrapper: ./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

Set `sdk.dir` in `local.properties` to your Android SDK if it's not auto-detected.

## After installing

1. Open **BrainWatch** and grant the three permissions (Usage access, Accessibility, Display over other apps)
2. Open Instagram and scroll
3. Watch the brain get tired. Give it a break. 🧠

## Disclaimer

This is a vibe-coded experiment and an *estimate*, not a medical device. Brains are famously hard to measure — treat the numbers as a gentle nudge, not a diagnosis.

<img width="1080" height="2400" alt="Screenshot_20260914-040147_Instagram" src="https://github.com/user-attachments/assets/062ee0c7-a572-4c03-aa16-efc8be4af886" />
<img width="1080" height="2400" alt="Screenshot_20260914-035352_BrainWatch" src="https://github.com/user-attachments/assets/9ec5ae15-9ec2-4a6f-b206-c873b3d6f416" />
<img width="1080" height="2400" alt="Screenshot_20260914-035341_BrainWatch" src="https://github.com/user-attachments/assets/9f53b2c4-4512-4834-8110-f3f5b81e6944" />

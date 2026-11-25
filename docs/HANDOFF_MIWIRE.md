# MiWire — Complete Engineering Context & AI Handoff

> **Purpose:** This document captures the complete hardware target, user scenario, upgrade/signing scheme, chronological engineering history, subtle bug fixes, and repository guardrails for **MiWire**. Any future AI coding session can read this file first and immediately resume without re-asking the user for background.

---

## 1. Project Identity & Origin

- **Repository:** [`Gustavo14832/MiWire`](https://github.com/Gustavo14832/MiWire) (`main` branch)
- **Fork Lineage:** Fork of [Starwire](https://github.com/SicarioGaming/Starwire) (`0.3.0`), built on [Moonlight Android](https://github.com/moonlight-stream/moonlight-android) with upstream adaptations from Moonlight X, Artemis, Artemide, LavArtemis, and Moonlight V+.
- **Application IDs:**
  - Release / Universal upgrade line: **`com.miwire`** (`com.miwire.root` for root flavor)
  - Debug line: **`com.miwire.debug`**
  - Legacy stable backup *(kept side-by-side)*: **`com.miware`**
- **Versioning (`app/build.gradle`):**
  - `versionName`: `"0.3.0"`
  - `versionCode`: **`322`** (`320` = legacy `com.miware` backup, `321` = initial `com.miwire` builds, `322+` = current & future in-place upgrades)
- **Toolchains:** `compileSdk 37`, `targetSdk 36`, `minSdk 21`, NDK `29.0.14206865`, `-O3 -flto` + `-mtune=cortex-a55` (`arm64-v8a` release native flags).

---

## 2. User Hardware, Peripherals & Network Profile

Every tuning choice in this fork targets this exact real-world setup:

| Component | Exact Specification / Constraint | Engineering Impact |
|---|---|---|
| **TV Box** | **Xiaomi Mi Box S (3rd Gen)** — Amlogic S905X5M, Quad Cortex-A55 @ 2.5 GHz (`arm64-v8a`), Mali-G310 V2 GPU, **2 GB shared RAM**, Android 14 TV (API 34) | Tight CMA/DMA-BUF memory budget (4-image swapchain, LRU icon trim, low-RAM bitmap decode), Cortex-A55 frequency scaling sensitivity, Amlogic HWC compositor bypass quirk |
| **Monitor** | **1080p 75 Hz monitor**, forced by Mi Box OS to **1080p @ 60 Hz** | `MiBox60Hz` (`FRAME_PACING_MIBOX_60HZ`) phase-locked 16.667 ms VSync target, `PresentScheduler` locked to 1 vsync (`1.5 ms` margin guard) |
| **Color Pipeline** | Currently runs **YCbCr 4:2:2 12-bit SDR** (monitor lacks HDR). Future TV/monitor compatibility desired (`HDR10`, `HDR10+`, `HLG`, `Dolby Vision`) | HDR & 10-bit PQ-to-SDR compat default **off** (`checkbox_enable_hdr = false`, `checkbox_hdr_compat = false`), keeping SDR 12-bit 4:2:2 untouched until user opts in |
| **Audio Path** | Currently runs standard **2.0 Stereo 16-bit PCM**. Future AVR/HDMI eARC/TV surround compatibility desired | `list_audio_config` defaults to `"2"`, with opt-in `"auto"` (`Auto (HDMI / AVR max)`) + opt-in `checkbox_float_pcm_audio` (32-bit float Opus -> `ENCODING_PCM_FLOAT`) |
| **USB Hub Setup** | Single USB hub with **all ports populated**: USB **Ethernet** adapter + wired **keyboard** + wired **mouse** + wireless **controller USB dongle** | High USB OTG bus / HID IRQ contention: requires 1000 Hz mouse coalescing + immediate UDP flush, sub-pixel accumulator, controller packet deduplication, and `WIFI_MODE_FULL_LOW_LATENCY` skipped when on Ethernet |
| **Streaming Host & Network** | **Cloud PC (Azure Tesla T4 GPU)** streamed over **WAN** (as well as local network) | Frame pacer must NOT assume LAN zero-jitter; `MiBox60Hz` uses `decayDivisor_ = 32` and a `10 ms` jitter recovery cap to absorb WAN arrival variance without frame skips |

---

## 3. APK Signing, Backups & In-Place Upgrade Guarantees

1. **Can future APKs be installed directly over the current APK without uninstalling?**
   - **YES** for all **`com.miwire`** builds (`versionCode >= 321`):
     - CI signs release builds (`miwire-release` / `MiWire.apk`) using persistent repository secrets (`MIWIRE_KEYSTORE_BASE64`, alias `CN=MiWire Release`, cert SHA-256 `eaacc7635946a1824d69bc6d1ee743d78adadfe2d70734aee3194a75040a2f6d`).
     - CI signs debug builds (`com.miwire.debug`) using persistent secret `MIWIRE_DEBUG_KEYSTORE_BASE64`.
     - As long as `applicationId` remains `"com.miwire"` (never renamed) and `versionCode` monotonically increments (`321 -> 322 -> ...`), Android PackageInstaller and `adb install -r` upgrade directly over the installed APK while preserving PC pairings, certificates, and settings.
2. **Relationship with the Stable Backup (`com.miware`):**
   - Tag `backup-v0.3.0-estavel` (commit `494d377a`) and GitHub Release `v0.3.0-miware` preserve the pre-refactor **`com.miware`** (`versionCode = 320`, `14,622,630` bytes, SHA-256 `572CEBCEB5D47D410091CDBF2088762E2AE523C93A3D7DE0D5D42F7F1CF975FF`, signed with `CN=Starwire MiBox`).
   - Because `com.miware` and `com.miwire` have **different** package IDs and signing certificates, `com.miwire` installs **side-by-side** with `com.miware` and will never overwrite it.
3. **Android Keyboard Accessibility Service Constraint:**
   - Android forbids APKs from auto-enabling their own `AccessibilityService` (`KeyboardAccessibilityService`) at install time without ADB (`WRITE_SECURE_SETTINGS`), root, or Device Owner privileges (e.g., *Install With Options*). Settings displays live status and a one-click Accessibility shortcut.

---

## 4. Chronological Engineering Milestones on `main`

- **`a642d902` -> `ca7979e4` (Initial Mi Box 60 Hz & Rebrand Foundation):**
  - Introduced `MiBox60Hz` phase-locked pacer, 1000 Hz mouse coalescing + sub-pixel accumulator, `recvmmsg` UDP burst drain, FEC `-1` host default fix (`callbacks.c`), 1-ref HEVC opt-in, Xiaomi Gaming theme (`#FF6700` / `#0D0F12`), `pt-BR` localization, and Ghost Overlay / keep-alive modes.
- **`05378c3f` -> `494d377a` (Fork Presentation & `backup-v0.3.0-estavel`):**
  - Finalized `com.miware` v0.3.0 backup tag/release (`494d377a`) and MiWire GitHub repository presentation.
- **`c47abadf` / `16747d1f` (MiWire Migration & Persistent CI Signing):**
  - Migrated `applicationId` to `"com.miwire"`, configured `MIWIRE_KEYSTORE` CI signing, added Settings keyboard accessibility status shortcut, and aligned screenshot tests (`verifyRoborazziNonRootDebug`).
- **`ff986901` / `1d3a1712` / `f7b02f84` (Full Hardware Capability Pass):**
  - Unlocked FastMixer low-latency audio on TV (`checkbox_enable_audiofx = false` in `v8` migration + dynamic HAL burst buffer trim in `AndroidAudioRenderer`), native nice `-16` + `PR_SET_TIMERSLACK = 1` + 1 MB `DecodedFrameBuffer`, `-mtune=cortex-a55`, 4-image swapchain & `kMaxImports = 16` for `MiBox60Hz`, `c2.amlogic.*` AV1 HW decoder whitelist & Codec2 retries, HDMI 2.1 ALLM (`setPreferMinimalPostProcessing(true)`), `NetworkCallback` dynamic `WIFI_MODE_FULL_LOW_LATENCY`, `GameState(loading)` transitions, and RAM cache trimming (`AppIcons` / `MemoryAssetLoader`).
- **`14910667` / `9956ecf2` (Deep Flash-Era Audit & Ghost Overlay Refinement):**
  - Removed Flash-era `|| perfListener != null` bug that ran stats string formatting, mutex locks (`VulkanRenderer::mutex_`), and 512-delta sorts on the video thread even when telemetry was hidden.
  - Replaced with a pure zero-video-overhead **1 Hz Ghost Overlay** pulse on the UI thread (`0x01000000` alpha `"00\n00"` / `"00\n01"`) plus fixed `uiKeepAliveView` (`0x02000000` color + `0.6f <-> 1.0f` alpha) cleaned up on `onPause()`.
  - Added migration `v9` (`mibox_tv_gaming_defaults_v9`) stopping forced `setSustainedPerformanceMode(true)` on TV so ADPF boosts up to 2.5 GHz under load.
  - Fixed WAN `frame_pacer.cpp` collapse (removed `diff / 4` fast shrink; restored unified `decayDivisor_ = 32` + `10 ms` buffered jitter cap for Azure Tesla T4 WAN streams).
- **`85d2fdc0` / `ca52f510` / `407ef68e` (Opt-in Dynamic Range, Audio Probing & Pointer Recapture):**
  - Added extended HDR detection (`HDR10`, `HDR10+`, `HLG`, `Dolby Vision`), opt-in `checkbox_hdr_compat` (10-bit PQ-to-SDR Vulkan tone mapping + `HEVCProfileMain10HDR10Plus` / `AV1ProfileMain10HDR10Plus`), and gated `Window.setColorMode(COLOR_MODE_HDR)` (API 26+) strictly on physically HDR displays.
  - Added `list_audio_config` `"auto"` (`Auto (HDMI / AVR max)`), HDMI/AVR codec capability probing (`describeAudioCapabilities`), opt-in `checkbox_float_pcm_audio` (32-bit float Opus decode to `AudioTrack(ENCODING_PCM_FLOAT)`), and immediate API 30+ pointer recapture (limiting the 500 ms recapture delay to Android 10 only).
- **Menu / Shell Performance Pass + `versionCode = 322` (`HEAD`):**
  - Optimized all non-streaming menus, grid views, and asset loaders for 60 Hz fluidity and minimal background overhead on Cortex-A55 / Mali-G310 (see Section 5).

---

## 5. Critical Invariants & Engineering Decisions (Do Not Regress)

1. **Ghost Overlay (`Game.java` & `MediaCodecDecoderRenderer.java`):**
   - Amlogic Hardware Composer drops into overlay bypass and throttles CPU clocks if no View layer invalidates at least periodically, causing 8-10+ pacing skips when stats are off.
   - **Rule:** Keep the invisible `0x01000000` (alpha 1/255) `1 Hz` Ghost Overlay text swap on the UI thread when stats are disabled; **never** re-attach `perfListener` work (`VulkanRenderer::mutex_` lock + stats formatting) onto the video decode/render thread when telemetry is hidden.
2. **Sustained Performance (`PreferenceConfiguration.java` & `Game.java`):**
   - On Android TV / S905X5M, `setSustainedPerformanceMode(true)` caps CPU/GPU clocks low and harms 60 Hz pacing spikes. Default is `false` (`v9`), relying on ADPF (`AdpfHelper` + Vulkan `PerfHint`). Keep it opt-in.
3. **WAN `MiBox60Hz` Frame Pacer (`app/src/main/jni/vulkan/frame_pacer.cpp`):**
   - Never reintroduce `diff / 4` shrink; keep unified `decayDivisor_ = 32` and `10 ms` jitter cap (`kMiBox60HzMaxBufferedNs`) so Azure/WAN streams tolerate jitter without stutter.
4. **Menu / UI Shell Optimizations (Strictly Outside Streaming):**
   - `StarfieldView`: `85 ms` (`~12 FPS`) twinkle update gate + precomputed `STATIC_STAR_ALPHA` (pixel-identical frame 0 for Roborazzi).
   - `Rainbow`: `66 ms` (`~15 FPS`) animation update gate for wordmark shader shift and `Rainbow.Ring` sweep rotation.
   - `GenericGridAdapter`: static `ViewHolder` (`imgView`, `overlayView`, `txtView`, `prgView`, `settingsIndicator`, `boundTag`) avoids 5 `findViewById` lookups per tile bind.
   - `AppGridAdapter`: cached `density`, `SparseBooleanArray` custom-settings cache via `AppPreferences.hasCustomSettings`, single reused `Rainbow.Ring`, direct visible-tile D-pad selection mutation in `setSelectedAppId` (skipping full `notifyDataSetChanged()` grid rebinds), plus `shutdown()`.
   - `CachedAppAssetLoader`: `30 s` idle-timeout executors with `allowCoreThreadTimeOut(true)`, explicit `shutdown()` called from `AppView.onDestroy()` (eliminating thread leaks on activity exit), and a single GPU `ViewPropertyAnimator` `alpha(1f)` fade (`140 ms`).
   - `DiskAssetLoader`: API 28+ `ImageDecoder.setTargetSize` divided by `Math.max(1, sampleSize)`.
   - `PcView` / `AppView`: visible-field diff guard before `pcGridAdapter.notifyDataSetChanged()`, `onWindowFocusChanged` pausing `AppView` endless loops, and `CoverGlow.rememberedTintOrDefault` applying saved hero accent colors immediately before async box-art decode completes.

---

## 6. Known Hardware Limits Reached & Remaining Backlog

### Hard Technical Limits (Platform / Hardware Bound)
- **75 Hz Monitor on Mi Box S Gen 3:** Locked at **60 Hz** by Android TV / Amlogic HDMI output pipeline (`16.667 ms` physical floor).
- **USB 2.0 Single OTG Bus:** Ethernet + keyboard + mouse + gamepad dongle share one USB controller; driver-level polling jitter improves with `1000 Hz` coalescing + immediate UDP send, but physical USB IRQ contention remains hardware-bounded.
- **2 GB Shared RAM:** Caps simultaneous large decoded textures and 4K swapchain depth (`4` images / `kMaxImports = 16`).
- **Cloud WAN RTT (Azure Tesla T4):** Internet packet jitter sets the physical floor for buffer headroom (`<= 10 ms` buffered in `MiBox60Hz`).
- **Auto-Enabling Accessibility:** Blocked by Android security model without ADB/DeviceOwner/root.

### Non-Blocking Repo Cleanups (Optional Future Work)
- Legacy credit strings in `app/src/main/res/values/strings.xml` (`title_about_github`, `credit_*`) and URLs in `OpenSourceNotices.java` still reference Starwire / upstream links (note: `RepoConsistencyTest` asserts README Credits URLs match `AboutCredits.ALL` / `OpenSourceNotices.ALL`!).
- `github/workflows/gemini.yml` sits in `github/` (missing leading dot) rather than `.github/workflows/`.
- `.github/workflows/sync-release.yml` references `feature/back-as-meta-force-qwerty` and `build.yml` still names debug test artifacts `starwire-debug*`.
- Windows companion tools (`windows/StarwireMicReceiver.cs`, `windows/StarwireUsbHelper.cs`) preserve wire labels `"starwire-mic/1"` / `"starwire-usb/1"` — update both ends together if ever renamed.
- `release/MiWire.apk` tracked in Git increases repo clone size over time; releases can alternatively rely on GitHub Release assets only.

---

## 7. CI Verification & Guardrails (Critical Before Pushing)

No local JDK or Android SDK is installed on the Windows host; **GitHub Actions** (`Gustavo14832/MiWire`) runs the full suite on every push to `main`:
1. **`Commits` workflow (`.github/tools/check-commits.sh`):** Enforces Conventional Commits 1.0.0 (`type(scope): lowercase imperative`) on all commits after `05378c3f`.
2. **`Build and test` workflow:**
   - `./gradlew testNonRootDebugUnitTest`: Runs `MiBoxOptimizationTest`, `LoopsTest`, and repository integrity guards:
     - **`ClaimsTest`**: Rejects unbacked phrases (`faster video`, `fast video`, `every millisecond counts`, etc.) in `README.md`, `CHANGELOG.md`, `docs/ROADMAP.md`, `strings.xml`, `.github/release-notes/*.md`.
     - **`RepoConsistencyTest`**: Requires `README.md` Credits table URLs to match `AboutCredits.ALL` / `OpenSourceNotices.ALL` — do NOT alter existing credit rows casually.
     - **`VersionNumbersTest`**: Enforces SemVer + `[Unreleased]` <-> `versionName` sync (`versionCode` can increment independently while `versionName` stays `"0.3.0"`).
   - `./gradlew lintNonRootDebug`: Zero new lint issues beyond `app/lint-baseline.xml` (use inline `Build.VERSION.SDK_INT` checks instead of `@TargetApi`).
   - `./gradlew verifyRoborazziNonRootDebug`: Pixel-exact match against all 16 settings screenshots (only update baselines if preferences UI layout intentionally changes).
   - Native NDK `arm64-v8a` build + 4 host test suites (`partial_frame_test.sh`, `host_packets_test.sh`, `frame_pacer_test`, `present_scheduler_test`).
   - Signed APK artifact **`miwire-release`** (`MiWire.apk`).
3. **`AGENTS.md` constraint:** Must stay strictly **under 100 lines**.

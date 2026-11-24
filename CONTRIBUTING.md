# Contributing to MiWire

Thanks for taking a look at **MiWire** (a specialized fork of [Starwire](https://github.com/SicarioGaming/Starwire) by SicarioGaming, built on [Moonlight Android](https://github.com/moonlight-stream/moonlight-android) for Xiaomi Mi Box S Gen 3 at 1080p @ 60 Hz).

## Bugs and ideas

- **Bugs:** open an issue with the
  [Bug report form](https://github.com/Gustavo14832/MiWire/issues/new/choose). It asks for
  your MiWire version, your device and Android/Google TV version, and the host on your PC.
- **Ideas:** use the **Feature idea** form if you already know what you'd like, and note if
  Starwire or another Moonlight client already has it.
- **Security problems:** please follow [SECURITY.md](SECURITY.md) instead of opening a normal
  issue with the details.

## Building

With the NDK version from `app/build.gradle` installed:

```bash
git clone --recursive https://github.com/Gustavo14832/MiWire.git
cd MiWire
./gradlew assembleNonRootDebug
```

The debug APK lands in `app/build/outputs/apk/nonRoot/debug/` and installs as "MiWire (Debug)"
(`com.miwire.debug`), next to the release app (`com.miwire`) and any Starwire or Moonlight install.

## Tests

Most tests need no device:

- **JVM unit tests** in `app/src/test` run on the computer:

  ```bash
  ./gradlew testNonRootDebugUnitTest
  ```

  Lint runs with `./gradlew lintNonRootDebug` (existing baseline warnings are in `app/lint-baseline.xml`).
- **Screenshot tests** draw the settings, game, in-stream menu and PC list screens on the
  computer (Robolectric and Roborazzi) at 9 sizes and compare them against `app/src/test/screenshots`:

  ```bash
  ./gradlew verifyRoborazziNonRootDebug
  ./gradlew recordRoborazziNonRootDebug
  ```

- **Native tests:** C++ simulations of the frame pacer under `app/src/main/jni/vulkan/tests`,
  and native checks under `app/src/main/jni/moonlight-core/tests` (`partial_frame_test.sh`,
  `host_packets_test.sh`).
- **On-device tests** (JUnit 4, instrumented) in `app/src/androidTest` run via `.github/tools/device-test.sh -s <serial>`:

  ```bash
  ./gradlew assembleNonRootDebug assembleNonRootDebugAndroidTest
  .github/tools/device-test.sh -s <serial>
  ```

  Or directly with `am instrument` using `com.miwire.debug.test/androidx.test.runner.AndroidJUnitRunner`.
- **The Windows helpers' tests** in `windows/tests`: `run-tests.ps1`, `e2e-loopback.ps1` and
  `run-usb-helper-tests.ps1`.

## Commits, branches and the changelog

Commit messages follow [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/),
branch names follow [Conventional Branch](https://conventionalbranch.org), and
[CHANGELOG.md](CHANGELOG.md) follows [Keep a Changelog 1.0.0](https://keepachangelog.com/en/1.0.0/).

- **The check:** `.github/workflows/commits.yml` runs `.github/tools/check-commits.sh` on pull
  requests and pushes to `main`.

## Licence and credits

MiWire is GPL-3.0 ([LICENSE.txt](LICENSE.txt)), like [Starwire](https://github.com/SicarioGaming/Starwire),
[Moonlight Android](https://github.com/moonlight-stream/moonlight-android) and every fork it builds on,
and anything you contribute is under the same licence. Keep all upstream credits in [README.md](README.md)
and `AboutCredits.java` intact, and preserve existing copyright and licence notices.

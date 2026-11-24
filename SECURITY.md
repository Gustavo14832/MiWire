# Security

**Miware** is a fork of [Starwire](https://github.com/SicarioGaming/Starwire) (by SicarioGaming)
and [Moonlight Android](https://github.com/moonlight-stream/moonlight-android), specialized for the
Xiaomi Mi Box S Gen 3.

## Reporting a security problem

Please don't put the details in a public issue. Report it privately instead:

1. Go to the repo's [Security tab](https://github.com/Gustavo14832/Miware/security) and choose
   **Report a vulnerability** (or use [this link](https://github.com/Gustavo14832/Miware/security/advisories/new)).
2. Say which part of Miware it's in (the Android TV app, the Windows microphone receiver, the
   Windows USB helper, or their setup), what you found and how to reproduce it.

Please also check whether the issue affects the [latest release](https://github.com/Gustavo14832/Miware/releases/latest)
and whether it also exists upstream in [Starwire](https://github.com/SicarioGaming/Starwire/security).

## The microphone

This applies to **Settings → Sound & vibration → Send my microphone** and the receiver that
`setup-microphone.cmd` installs on the PC.

With **Send my microphone to** set to the encrypted PC receiver (default), audio is encrypted and
authenticated, and the receiver only plays it from devices paired with that PC. Each device pairs
once with a 16-character code, each stream derives its own AES-256-GCM key, and every packet
carries a monotonically increasing counter. The receiver refuses unpaired devices, replayed or forged
packets, and plain VBAN. Both sides restrict traffic to private addresses (local network or private VPN).
The protocol is documented in [docs/dev/mic-protocol.md](docs/dev/mic-protocol.md).

What it doesn't protect against:

- Someone who sees the pairing code before you type it could pair instead of you; do not share your
  screen while showing the code.
- A lost or stolen device that remains paired can send audio until unpaired from the receiver's tray menu.
- Network flooding/jamming or malware already running on the Android device or PC.
- Packet sizes and timing remain visible on the network.

**Not encrypted** (for Voicemeeter and other VBAN apps) sends plain VBAN, so anyone on the same
network can listen to the microphone.

The PC receiver only plays plain VBAN if set up with `-AllowUnencrypted`, in which case anyone on the
network can send audio into the PC's virtual mic whichever option your device uses. Its firewall rule
applies on every network profile Windows knows about (including public Wi-Fi). Use unencrypted VBAN
only on networks you trust.

## USB devices

This applies to **Settings → Controllers → USB devices (Experimental)** and the USB helper that
`setup-usb.cmd` installs on the PC. Both are off by default.

A device can only be shared with a PC whose microphone receiver the Android device is paired with,
and only after enabling USB sharing in the PC helper and ticking the device under **Allow USB for**.
Keys are derived from the microphone pairing, and transport uses TLS 1.2 or 1.3 over TCP port 6981
(firewall rule covers Private and Domain networks only). Device rules validate classes and descriptors
before Windows sees the device. See [docs/dev/usb-protocol.md](docs/dev/usb-protocol.md).

What it doesn't protect against:

- A malicious USB device that misreports its descriptors.
- A lost or stolen paired phone that is unlocked.
- Code inside the app process capable of reading the pairing key (such as root malware or an untrusted
  custom Vulkan driver).
- Local loopback connections (127.0.0.1) to the in-app exporter or malware on the PC.

## The custom Vulkan driver option

**Settings → Advanced → Custom Vulkan driver** is off by default and experimental.

When enabled, a picked `.so`, `.zip` or `.adpkg` driver file is copied into private app storage and
loaded only for PyroWave streams on 64-bit ARM devices (Android 10+) via `libadrenotools`. A loaded
driver runs native code inside Miware's process with Miware's permissions (network, microphone, Nearby
devices if granted, paired PC keys), so only use drivers from a trusted source. If a driver fails to
load or initialize PyroWave, Miware falls back to the system driver; if it crashes, Miware closes.

## Verifying a Miware download

- **Package ID:** `com.miware` (debug builds: `com.miware.debug`)
- **Latest APK (`release/Miware.apk`, `v0.3.0-miware`) SHA-256:**
  `572CEBCEB5D47D410091CDBF2088762E2AE523C93A3D7DE0D5D42F7F1CF975FF`
- You can inspect the signing certificate of any APK using the Android SDK's `apksigner`:
  `apksigner verify --print-certs Miware.apk`. Android will refuse to install an update signed with
  a different key over an existing install.

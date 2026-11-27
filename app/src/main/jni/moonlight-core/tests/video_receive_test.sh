#!/bin/sh
# Compile the receive loop from the pinned native submodule with deterministic socket and clock stubs.
set -eu
cd "$(dirname "$0")"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
awk '/^static void VideoReceiveThreadProc\(void\* context\) \{/ { inside=1 }
     inside {print}
     inside && /^}/ {found=1; exit}
     END {if (!found) exit 1}' ../moonlight-common-c/src/VideoStream.c > "$tmp/receive-loop.inc"
"${CC:-cc}" -std=c11 -O2 -Wall -Wextra -Wno-unused-parameter -I"$tmp" video_receive_test.c -o "$tmp/video_receive"
"$tmp/video_receive"

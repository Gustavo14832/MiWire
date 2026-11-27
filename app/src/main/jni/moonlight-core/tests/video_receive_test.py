"""Compile the production video receive loop against a deterministic socket and clock."""
import argparse
import pathlib
import re
import subprocess
import tempfile

tests = pathlib.Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument("--source", type=pathlib.Path, default=tests.parent / "moonlight-common-c/src/VideoStream.c")
parser.add_argument("--cc", default="cc")
parser.add_argument("--baseline", action="store_true")
args = parser.parse_args()
source = args.source.read_text(encoding="utf-8")
loop = re.search(r"^static void VideoReceiveThreadProc\(void\* context\) \{.*?^\}", source, re.M | re.S)
if loop is None:
    raise RuntimeError("Production receive loop missing")
with tempfile.TemporaryDirectory() as scratch:
    folder = pathlib.Path(scratch)
    (folder / "receive-loop.inc").write_text(loop.group(), encoding="utf-8")
    executable = folder / "receive-test.exe"
    subprocess.run([args.cc, "-std=c11", "-O1", "-I", scratch,
                    str(tests / "video_receive_test.c"), "-o", str(executable)], check=True)
    subprocess.run([str(executable), "baseline" if args.baseline else "integrated"], check=True)

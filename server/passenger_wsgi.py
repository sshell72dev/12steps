from __future__ import annotations

import os
import sys

ROOT = os.path.dirname(__file__)
VENDOR = os.path.join(ROOT, "vendor")

if ROOT not in sys.path:
    sys.path.insert(0, ROOT)
if os.path.isdir(VENDOR):
    sys.path.insert(0, VENDOR)

try:
    import flask  # noqa: F401
    import pymysql  # noqa: F401
except ImportError:
    import subprocess

    req = os.path.join(ROOT, "requirements.txt")
    wheels = os.path.join(ROOT, "wheels")
    os.makedirs(os.path.join(ROOT, "tmp"), exist_ok=True)
    cmd = [sys.executable, "-m", "pip", "install", "-r", req]
    if os.path.isdir(wheels) and os.listdir(wheels):
        cmd = [
            sys.executable,
            "-m",
            "pip",
            "install",
            "--no-index",
            "--find-links",
            wheels,
            "-r",
            req,
        ]
    subprocess.check_call(cmd, cwd=ROOT)

from app import app as application  # noqa: E402

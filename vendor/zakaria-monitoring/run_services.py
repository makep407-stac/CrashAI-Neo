"""Run the live collector and relay under one Render Background Worker."""
import os
import signal
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.abspath(__file__))
COMMANDS = [
    [sys.executable, os.path.join(ROOT, "run_scraper.py")],
    [sys.executable, os.path.join(ROOT, "relay_to_crashai.py")],
]
children = []
stopping = False

def stop(_signum, _frame):
    global stopping
    stopping = True
    for child in children:
        if child.poll() is None:
            child.terminate()

signal.signal(signal.SIGTERM, stop)
signal.signal(signal.SIGINT, stop)

try:
    for command in COMMANDS:
        children.append(subprocess.Popen(command, cwd=ROOT))
    while not stopping:
        for index, child in enumerate(children):
            if child.poll() is not None:
                print(f"Process {index} exited; restarting it.", flush=True)
                time.sleep(3)
                children[index] = subprocess.Popen(COMMANDS[index], cwd=ROOT)
        time.sleep(2)
finally:
    for child in children:
        if child.poll() is None:
            child.terminate()
    for child in children:
        try:
            child.wait(timeout=20)
        except subprocess.TimeoutExpired:
            child.kill()

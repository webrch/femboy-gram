import os
import shutil
import subprocess

REL = r"C:\Users\lain\Documents\FemboyGram\_release"
apks = []
for root, dirs, files in os.walk("tg-src/TMessagesProj_AppStandalone/build/outputs/apk"):
    for n in files:
        if n.endswith(".apk"):
            apks.append(os.path.join(root, n))
apk = max(apks, key=os.path.getmtime)
shutil.copy2(apk, "FemboyGram.apk")
shutil.copy2(apk, os.path.join(REL, "FemboyGram-1.1.apk"))
old = os.path.join(REL, "FemboyGram-1.0.apk")
if os.path.exists(old):
    os.remove(old)
print("apk bytes", os.path.getsize(apk))

skip = {"tg-src", ".git", "local.properties"}
for name in os.listdir("."):
    if name in skip:
        continue
    s = os.path.join(".", name)
    d = os.path.join(REL, name)
    if os.path.isdir(s):
        shutil.copytree(s, d, ignore=shutil.ignore_patterns("__pycache__", "*.pyc"), dirs_exist_ok=True)
    elif os.path.isfile(s):
        shutil.copy2(s, d)
print("synced")

subprocess.run(["git", "-C", REL, "add", "-A"], check=True)
subprocess.run(["git", "-C", REL, "commit", "-m", "FemboyGram 1.1: ghost mode release"], check=True)
print("committed")

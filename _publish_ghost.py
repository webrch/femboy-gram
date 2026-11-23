import os
import shutil
import subprocess

SRC = "."
REL = r"C:\Users\lain\Documents\FemboyGram\_release"

apks = []
for root, dirs, files in os.walk("tg-src/TMessagesProj_AppStandalone/build/outputs/apk"):
    for n in files:
        if n.endswith(".apk"):
            apks.append(os.path.join(root, n))
apk = max(apks, key=os.path.getmtime)
shutil.copy2(apk, "FemboyGram.apk")
shutil.copy2(apk, os.path.join(REL, "FemboyGram-1.0.apk"))
print("apk bytes", os.path.getsize(apk))

# attribution for the ported ghost-mode logic
p = "ATTRIBUTION.md"
t = open(p, encoding="utf-8").read()
line = ("\n## Режим призрака\n\nЛогика сокрытия прочитанного, онлайн-статуса и набора текста "
        "перенесена из AyuGram4A (https://github.com/AyuGram/AyuGram4A, GPL-2.0), "
        "Copyright @Radolyn, 2023. Код адаптирован под FemboyGram.\n")
if "AyuGram4A" not in t:
    open(p, "w", encoding="utf-8", newline="\n").write(t.rstrip() + "\n" + line)

# sync clean export (without tg-src and .git)
skip = {"tg-src", ".git", "local.properties"}
for name in os.listdir(SRC):
    if name in skip:
        continue
    s = os.path.join(SRC, name)
    d = os.path.join(REL, name)
    if os.path.isdir(s):
        shutil.copytree(s, d, ignore=shutil.ignore_patterns("__pycache__", "*.pyc"), dirs_exist_ok=True)
    elif os.path.isfile(s):
        shutil.copy2(s, d)
print("synced")

subprocess.run(["git", "-C", REL, "add", "-A"], check=True)
subprocess.run(["git", "-C", REL, "commit", "-m", "Add full ghost mode from AyuGram"], check=True)
print("committed")

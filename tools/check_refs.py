#!/usr/bin/env python3
"""Verify that every R.string.X / R.drawable.X / R.raw.X referenced from Java
actually exists in the main res tree. Also lists leftover branding words.
"""
import os, re, collections

ROOT = "tg-src/TMessagesProj/src/main"
JAVA = ROOT + "/java"
RES = ROOT + "/res"

# collect resource names by type
available = collections.defaultdict(set)
for dirpath, dirnames, filenames in os.walk(RES):
    for fn in filenames:
        base = os.path.splitext(fn)[0]
        folder = os.path.basename(dirpath)
        if folder.startswith("values"):
            if folder == "values" or folder.startswith("values-"):
                if fn == "strings.xml":
                    text = open(os.path.join(dirpath, fn), encoding="utf-8", errors="ignore").read()
                    available["string"].update(re.findall(r'<string\s+name="([^"]+)"', text))
        elif folder.startswith("drawable"):
            available["drawable"].add(base)
        elif folder.startswith("mipmap"):
            available["mipmap"].add(base)
        elif folder == "raw":
            available["raw"].add(base)

missing = collections.defaultdict(set)
for dirpath, dirnames, filenames in os.walk(JAVA):
    for fn in filenames:
        if not fn.endswith(".java"):
            continue
        text = open(os.path.join(dirpath, fn), encoding="utf-8", errors="ignore").read()
        for typ, name in re.findall(r'\bR\.(string|drawable|raw|mipmap)\.([A-Za-z0-9_]+)', text):
            if name not in available[typ]:
                missing[typ].add(name)

for typ in sorted(missing):
    print("MISSING R.%s (%d):" % (typ, len(missing[typ])))
    for n in sorted(missing[typ]):
        print("   ", n)
if not missing:
    print("all R.* references resolve")

# branding leftovers in Java and res text
pat = re.compile(r"(?i)Margy|Margelet|narezany|margygram|margy\.narez")
left = collections.Counter()
for dirpath, dirnames, filenames in os.walk(JAVA):
    for fn in filenames:
        if not fn.endswith(".java"):
            continue
        text = open(os.path.join(dirpath, fn), encoding="utf-8", errors="ignore").read()
        for m in pat.findall(text):
            left[m.lower()] += 1
print("\nbranding words in Java:", dict(left.most_common(20)))

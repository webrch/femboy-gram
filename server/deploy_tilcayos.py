"""Выкатка тилкайо на сервер: маршруты /gram/tilcayos и /gram/tilcayo/<id>.webp
в badges.py и новое описание бейджа кота в gram_badges.json. Запускать на сервере
в /opt/femboyt. Повторный запуск ничего не ломает."""
import json, os, shutil, time

base = "/opt/femboyt"
src = os.path.join(base, "badges.py")
code = open(src, encoding="utf-8").read()
anchor = '        if path == "/gram/badges":\n'
route = '''        # FemboyGram: тилкайо для «тиндера» по кнопке чатов
        if path == "/gram/tilcayos":
            file = os.path.join(os.path.dirname(os.path.abspath(__file__)), "gram_tilcayos.json")
            if not os.path.isfile(file):
                self.answer(404, {"error": "no tilcayos"})
                return
            with open(file, "rb") as handle:
                data = handle.read()
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "public, max-age=300")
            self.end_headers()
            self.push(data)
            return
        if path.startswith("/gram/tilcayo/") and path.endswith(".webp"):
            name = os.path.basename(path)
            file = os.path.join(os.path.dirname(os.path.abspath(__file__)), "tilcayos", name)
            if not os.path.isfile(file):
                self.answer(404, {"error": "no such tilcayo"})
                return
            with open(file, "rb") as handle:
                self.answer(200, None, "image/webp", handle.read())
            return
'''
if '"/gram/tilcayos"' not in code:
    assert code.count(anchor) == 1
    shutil.copy(src, src + ".bak-before-tilcayos-%d" % int(time.time()))
    code = code.replace(anchor, route + anchor)
    open(src, "w", encoding="utf-8").write(code)
    print("route added")
else:
    print("route already there")

badges = os.path.join(base, "gram_badges.json")
shutil.copy(badges, badges + ".bak-before-cat-about-%d" % int(time.time()))
d = json.load(open(badges, encoding="utf-8"))
n = 0
for b in d:
    if b.get("id") == "cat":
        b["about"] = "Their cat visited a FemboyGram easter egg before the easter egg was cut."
        b["about_ru"] = "Его кот побывал в пасхалке FemboyGram, пока пасхалку не вырезали."
        b["about_zh"] = "他的猫曾出现在 FemboyGram 的彩蛋里，后来彩蛋被删掉了。"
        n += 1
json.dump(d, open(badges, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("cat badges updated:", n)

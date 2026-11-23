"""Чтение японских строк латиницей (хэпбёрн) для текста песен в плеере FemboyGram.

Зовётся из FemboyRomajiPython пачкой: одна строка текста — одна строка ответа.
Пустая строка в ответе значит «тут читать нечего».
"""

_kakasi = None


def _data_dir():
    """Настоящая папка со словарями pykakasi.

    pykakasi склеивает путь через os.path.join, а Chaquopy отдаёт ему вместо
    строки свой AssetPath (пакет лежит внутри apk), и всё падает. Поэтому
    словари один раз копируются в файлы приложения, и путь даётся строкой.
    """
    import os
    import pykakasi
    here = os.path.join(os.path.dirname(str(pykakasi.__file__)), "data")
    if os.path.isfile(os.path.join(here, "kanwadict4.db")):
        return here
    out = os.path.join(os.environ.get("HOME") or "/data/local/tmp", "pykakasi_data")
    os.makedirs(out, exist_ok=True)
    import importlib.resources as resources
    for item in resources.files("pykakasi").joinpath("data").iterdir():
        target = os.path.join(out, item.name)
        if not os.path.exists(target):
            tmp = target + ".part"
            with open(tmp, "wb") as f:
                f.write(item.read_bytes())
            os.replace(tmp, target)
    return out


def _get():
    global _kakasi
    if _kakasi is None:
        import os
        import pykakasi
        import pykakasi.properties
        folder = _data_dir()
        pykakasi.properties.Configurations.dictpath = staticmethod(lambda dbfile: os.path.join(folder, dbfile))
        _kakasi = pykakasi.kakasi()
    return _kakasi


def _one(kakasi, line):
    parts = []
    for item in kakasi.convert(line):
        word = (item.get("hepburn") or "").strip()
        if word:
            parts.append(word)
    text = " ".join(parts)
    # пробел перед знаками препинания лишний
    for mark in (",", ".", "!", "?", ")", "」", "、", "。"):
        text = text.replace(" " + mark, mark)
    return text


def romanize(lines):
    kakasi = _get()
    out = []
    for line in lines:
        line = "" if line is None else str(line)
        try:
            out.append(_one(kakasi, line) if line.strip() else "")
        except Exception:
            # причина уходит в logcat (python.stderr), иначе её не найти
            import traceback
            traceback.print_exc()
            out.append("")
    return out

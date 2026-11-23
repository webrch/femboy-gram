# Музыка FemboyGram из сети: поиск, похожее и скачивание через yt-dlp.
#
# Зовётся из FemboyMusicPython.java. Каждая функция отвечает строкой JSON:
# {"ok": true, ...} или {"ok": false, "error": код, "message": текст}.
# ffmpeg в приложении нет, поэтому берём только звук одним файлом, без склейки.

import json
import os
import re
import urllib.parse

import yt_dlp

try:
    import urllib.request as _req
except Exception:
    _req = None

SC = "sc"
YT = "yt"

# папка кэша yt-dlp (client_id SoundCloud и т.п.), задаётся из Java
_cache_dir = False


def init(cache_dir):
    global _cache_dir
    try:
        os.makedirs(cache_dir, exist_ok=True)
        _cache_dir = cache_dir
    except Exception:
        _cache_dir = False
    return version()

# клиенты ютуба, которым не нужен JS для подписи ссылок
# (проверено 2026-11: без JS и PO-токена отдаёт ссылки только android, и то 18-й формат)
_YT_CLIENTS = [
    ["android"],
    ["android_vr"],
    ["ios"],
    ["tv"],
    ["android", "android_vr", "ios"],
]


class _Quiet:
    def debug(self, msg):
        pass

    def info(self, msg):
        pass

    def warning(self, msg):
        pass

    def error(self, msg):
        pass


def _opts(extra=None, clients=None):
    opts = {
        "quiet": True,
        "no_warnings": True,
        "noprogress": True,
        "logger": _Quiet(),
        "cachedir": _cache_dir,
        "noplaylist": True,
        "socket_timeout": 20,
        "retries": 3,
        "fragment_retries": 5,
        "geo_bypass": True,
        # на телефоне нет deno: не даём yt-dlp его искать
        "js_runtimes": {},
    }
    if clients:
        opts["extractor_args"] = {"youtube": {"player_client": clients}}
    if extra:
        opts.update(extra)
    return opts


def _ok(**kw):
    kw["ok"] = True
    return json.dumps(kw, ensure_ascii=False)


def _fail(error, message):
    return json.dumps({"ok": False, "error": error, "message": str(message)[:500]}, ensure_ascii=False)


def _classify(e):
    text = str(e)
    low = text.lower()
    if "javascript" in low or "js runtime" in low or "jsc" in low or "n challenge" in low or "signature" in low:
        return "js"
    if "sign in" in low or "confirm you" in low or " bot" in low or "403" in low or "forbidden" in low:
        return "blocked"
    if "drm" in low:
        return "unavailable"
    if "unavailable" in low or "private" in low or "not available" in low or "404" in low:
        return "unavailable"
    if ("urlopen" in low or "timed out" in low or "connection" in low or "resolve" in low
            or "network" in low or "ssl" in low or "errno" in low):
        return "network"
    if "requested format" in low or "no video formats" in low or "no formats" in low:
        return "format"
    return "other"


def _thumb(entry):
    thumbs = entry.get("thumbnails") or []
    best = None
    for t in thumbs:
        url = t.get("url")
        if not url:
            continue
        w = t.get("width") or 0
        # квадратная и средняя по размеру обложка лучше всего
        if best is None or (abs(w - 400) < abs((best.get("width") or 0) - 400)):
            best = t
    if best is not None:
        return best.get("url")
    return entry.get("thumbnail")


def _yt_cover(vid, entry):
    url = _thumb(entry)
    if url:
        return url
    if vid:
        return "https://i.ytimg.com/vi/%s/hqdefault.jpg" % vid
    return None


def _split_title(title, artist):
    # «Исполнитель - Название» в заголовке видео
    if title and not artist:
        for sep in (" - ", " – ", " — "):
            if sep in title:
                a, t = title.split(sep, 1)
                return t.strip(), a.strip()
    return title, artist


_JUNK = re.compile(r"\s*[\(\[](official|lyric|lyrics|audio|video|official video|official audio|music video|mv|hd|hq|visualizer)[^\)\]]*[\)\]]", re.I)


def _clean(title):
    if not title:
        return title
    return _JUNK.sub("", title).strip()


def _entry(source, e):
    if not e:
        return None
    if source == YT:
        vid = e.get("id")
        if not vid or len(vid) != 11:
            return None
        artist = e.get("artist") or e.get("creator") or e.get("uploader") or e.get("channel") or ""
        if isinstance(artist, list):
            artist = ", ".join(artist)
        artist = re.sub(r"\s*-\s*Topic$", "", artist or "")
        title = e.get("track") or e.get("title") or ""
        if not e.get("track"):
            t, a = _split_title(title, None)
            if a:
                title, artist = t, a
        title = _clean(title)
        return {
            "source": YT,
            "id": vid,
            "title": title,
            "artist": artist,
            "duration": int(e.get("duration") or 0),
            "cover": _yt_cover(vid, e),
            "url": "https://music.youtube.com/watch?v=" + vid,
        }
    url = e.get("webpage_url") or e.get("url") or ""
    sid = e.get("id")
    if not sid:
        return None
    title = e.get("title") or e.get("track") or ""
    artist = e.get("artist") or e.get("uploader") or ""
    t, a = _split_title(title, None)
    if a and (not artist or artist.lower() in a.lower()):
        title, artist = t, a
    if url.startswith("https://api") or "soundcloud.com" not in url:
        url = e.get("webpage_url") or url
    return {
        "source": SC,
        "id": str(sid),
        "title": title,
        "artist": artist,
        "duration": int(e.get("duration") or 0),
        "cover": _thumb(e),
        "url": url,
    }



# ---- YouTube Music напрямую через InnerTube: тут есть исполнитель, длина и квадратная обложка ----

_YTM_CLIENT = {"clientName": "WEB_REMIX", "clientVersion": "1.20251110.01.00", "hl": "en"}
_YTM_SONGS = "EgWKAQIIAWoQEAMQBBAJEAoQBRAREBAQFQ%3D%3D"


def _ytm(endpoint, body):
    body = dict(body)
    body["context"] = {"client": _YTM_CLIENT}
    request = _req.Request(
        "https://music.youtube.com/youtubei/v1/%s?prettyPrint=false" % endpoint,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json", "Origin": "https://music.youtube.com",
                 "Referer": "https://music.youtube.com/",
                 "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"})
    with _req.urlopen(request, timeout=20) as response:
        return json.loads(response.read().decode("utf-8"))


def _walk(node, key):
    if isinstance(node, dict):
        if key in node:
            yield node[key]
        for value in node.values():
            yield from _walk(value, key)
    elif isinstance(node, list):
        for value in node:
            yield from _walk(value, key)


def _runs(obj):
    return [r.get("text", "") for r in ((obj or {}).get("runs") or [])]


def _seconds(text):
    try:
        parts = [int(p) for p in (text or "").split(":")]
    except ValueError:
        return 0
    total = 0
    for p in parts:
        total = total * 60 + p
    return total


def _big(url):
    if not url:
        return url
    return re.sub(r"=w\d+-h\d+", "=w544-h544", url)


def _byline(runs):
    # «Исполнитель • Альбом • 3:45»: всё до первой точки и есть исполнители
    names = []
    for text in runs:
        if text.strip() == "•":
            break
        names.append(text)
    return "".join(names).strip()


def _ytm_item(vid, title, artist, duration, thumbs):
    if not vid:
        return None
    cover = _big(thumbs[-1]["url"]) if thumbs else "https://i.ytimg.com/vi/%s/hqdefault.jpg" % vid
    return {"source": YT, "id": vid, "title": title, "artist": artist, "duration": duration,
            "cover": cover, "url": "https://music.youtube.com/watch?v=" + vid}


def _ytm_search(query, limit):
    data = _ytm("search", {"query": query, "params": _YTM_SONGS})
    out = []
    for r in _walk(data, "musicResponsiveListItemRenderer"):
        vid = (r.get("playlistItemData") or {}).get("videoId")
        cols = [c.get("musicResponsiveListItemFlexColumnRenderer", {}).get("text") for c in (r.get("flexColumns") or [])]
        if not vid or not cols:
            continue
        title = "".join(_runs(cols[0]))
        info = _runs(cols[1]) if len(cols) > 1 else []
        duration = 0
        for text in reversed(info):
            if re.match(r"^\d+(:\d+)+$", text.strip()):
                duration = _seconds(text.strip())
                break
        thumbs = (((r.get("thumbnail") or {}).get("musicThumbnailRenderer") or {}).get("thumbnail") or {}).get("thumbnails") or []
        item = _ytm_item(vid, title, _byline(info), duration, thumbs)
        if item and all(o["id"] != vid for o in out):
            out.append(item)
        if len(out) >= limit:
            break
    return out


def _ytm_radio(vid, limit):
    data = _ytm("next", {"videoId": vid, "playlistId": "RDAMVM" + vid, "isAudioOnly": True})
    out = []
    for r in _walk(data, "playlistPanelVideoRenderer"):
        rid = r.get("videoId")
        title = "".join(_runs(r.get("title")))
        duration = _seconds("".join(_runs(r.get("lengthText"))))
        thumbs = (r.get("thumbnail") or {}).get("thumbnails") or []
        item = _ytm_item(rid, title, _byline(_runs(r.get("longBylineText"))), duration, thumbs)
        if item and all(o["id"] != rid for o in out):
            out.append(item)
        if len(out) >= limit:
            break
    return out


def _sc_cover(track):
    url = track.get("artwork_url") or ((track.get("user") or {}).get("avatar_url"))
    if url:
        url = re.sub(r"-(large|t\d+x\d+|crop|small|badge|tiny|mini)\.(jpg|png)", r"-t500x500.\2", url)
    return url


def _sc_api(path, query):
    # SoundCloud через его же API: yt-dlp сам достаёт и кэширует client_id
    with yt_dlp.YoutubeDL(_opts()) as ydl:
        ie = ydl.get_info_extractor("Soundcloud")
        ie.initialize()
        return ie._call_api(ie._API_V2_BASE + path, "femboy", query=query, headers=ie._HEADERS)


def _sc_tracks(data, limit):
    out = []
    for t in (data or {}).get("collection") or []:
        if not t.get("id") or t.get("kind", "track") != "track":
            continue
        # отрывки по 30 секунд (платные треки) и зашифрованные (DRM) пропускаем
        if t.get("policy") == "SNIP" or t.get("policy") == "BLOCK":
            continue
        codings = ((t.get("media") or {}).get("transcodings")) or []
        if codings and not any(((c.get("format") or {}).get("protocol") in ("hls", "progressive")) and not c.get("snipped")
                               for c in codings):
            continue
        title = t.get("title") or ""
        artist = (t.get("publisher_metadata") or {}).get("artist") or (t.get("user") or {}).get("username") or ""
        out.append({"source": SC, "id": str(t["id"]), "title": title, "artist": artist,
                    "duration": int((t.get("full_duration") or t.get("duration") or 0) / 1000),
                    "cover": _sc_cover(t), "url": t.get("permalink_url") or ""})
        if len(out) >= limit:
            break
    return out


def _sc_related(tid, limit):
    return _sc_tracks(_sc_api("tracks/%s/related" % tid, {"limit": limit + 5}), limit)


def _sc_search(query, limit):
    return _sc_tracks(_sc_api("search/tracks", {"q": query, "limit": limit + 10}), limit)


def _flat(url, source, limit, clients=None):
    opts = _opts({"extract_flat": "in_playlist", "playlistend": limit, "noplaylist": False}, clients)
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)
    entries = (info or {}).get("entries") or []
    out = []
    seen = set()
    for e in entries:
        item = _entry(source, e)
        if item is None or item["id"] in seen:
            continue
        seen.add(item["id"])
        out.append(item)
        if len(out) >= limit:
            break
    return out


def _search(source, query, limit):
    if source == SC:
        try:
            out = _sc_search(query, limit)
            if out:
                return out
        except Exception:
            pass
        # flat у SoundCloud отдаёт голые ссылки: берём полные данные, они лёгкие
        opts = _opts({"extract_flat": False, "playlistend": limit, "noplaylist": False,
                      "skip_download": True, "lazy_playlist": False})
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info("scsearch%d:%s" % (limit, query), download=False, process=False)
            entries = list((info or {}).get("entries") or [])
        out = []
        for e in entries[:limit]:
            item = _entry(SC, e)
            if item:
                out.append(item)
        return out
    # YouTube Music: раздел «Песни» напрямую, потом через yt-dlp, потом обычный поиск
    try:
        out = _ytm_search(query, limit)
        if out:
            return out
    except Exception:
        pass
    try:
        url = "https://music.youtube.com/search?q=" + urllib.parse.quote(query) + "#songs"
        out = _flat(url, YT, limit)
        if out:
            return out
    except Exception:
        pass
    return _flat("ytsearch%d:%s" % (limit, query + " audio"), YT, limit)


def search(source, query, limit=20):
    try:
        query = (query or "").strip()
        if not query:
            return _ok(items=[])
        return _ok(items=_search(source, query, int(limit)))
    except Exception as e:
        return _fail(_classify(e), e)


def _related(source, tid, url, limit):
    if source == YT:
        try:
            out = _ytm_radio(tid, limit + 1)
            if out:
                return out
        except Exception:
            pass
        return _flat("https://music.youtube.com/watch?v=%s&list=RDAMVM%s" % (tid, tid), YT, limit + 1)
    if source == SC and tid and str(tid).isdigit():
        try:
            out = _sc_related(tid, limit + 1)
            if out:
                return out
        except Exception:
            pass
    if url and "soundcloud.com" in url:
        base = url.split("?")[0].rstrip("/")
        return _flat(base + "/recommended", SC, limit + 1)
    return []


def related(source, tid, url, limit=20):
    try:
        items = [i for i in _related(source, tid, url, int(limit)) if i["id"] != tid]
        return _ok(items=items[:int(limit)])
    except Exception as e:
        return _fail(_classify(e), e)


def recommend(seeds_json, limit=30):
    """Похожее на то, что нравится. seeds: [{source, id, url, artist, title}]."""
    try:
        seeds = json.loads(seeds_json or "[]")
    except Exception:
        seeds = []
    limit = int(limit)
    groups = []
    errors = []
    skip = set()
    for s in seeds:
        if s.get("id"):
            skip.add(s.get("id"))
    for s in [s for s in seeds if s.get("source") != "skip"][:3]:
        source = s.get("source")
        try:
            if source in (YT, SC) and s.get("id"):
                items = _related(source, s.get("id"), s.get("url"), 15)
            else:
                # трек из чата: ищем его на YouTube Music и берём радио
                q = ((s.get("artist") or "") + " " + (s.get("title") or "")).strip()
                if not q:
                    continue
                items = []
                try:
                    found = _search(YT, q, 1)
                    if found:
                        skip.add(found[0]["id"])
                        items = _related(YT, found[0]["id"], None, 15)
                except Exception as e:
                    errors.append(e)
                if not items:
                    found = _search(SC, q, 1)
                    if found:
                        skip.add(found[0]["id"])
                        items = _related(SC, found[0]["id"], found[0]["url"], 15)
            groups.append(items)
        except Exception as e:
            errors.append(e)
    # перемешиваем по очереди из каждой группы, чтобы не шло подряд одно радио
    out = []
    seen = set(skip)
    i = 0
    while len(out) < limit and any(i < len(g) for g in groups):
        for g in groups:
            if i < len(g) and g[i]["id"] not in seen:
                seen.add(g[i]["id"])
                out.append(g[i])
        i += 1
    if not out and errors:
        return _fail(_classify(errors[0]), errors[0])
    return _ok(items=out[:limit])


def _formats(source):
    if source == YT:
        # одним файлом: m4a (AAC) или webm (opus), без видео
        # если отдают только ролик со звуком (18-й, mp4), берём его: плеер играет звук из mp4
        return ("bestaudio[ext=m4a][vcodec=none][protocol^=http]/bestaudio[acodec=opus][vcodec=none][protocol^=http]"
                "/bestaudio[vcodec=none][protocol^=http]/18/worst[acodec!=none][vcodec!=none][protocol^=http]")
    # SoundCloud: прямой mp3, потом HLS (собирается сам, без ffmpeg)
    return "http_mp3_1_0/http_mp3_0_1/bestaudio[protocol=http]/bestaudio[protocol=https]/hls_aac_160k/bestaudio[ext=m4a]/bestaudio[acodec=opus]/bestaudio"


def download(source, tid, url, out_path, progress=None):
    """Скачать звук в out_path (имя без расширения). progress: Java AtomicInteger, 0..1000."""
    if not url:
        url = ("https://music.youtube.com/watch?v=" + tid) if source == YT else None
    if not url:
        return _fail("unavailable", "no url")
    folder = os.path.dirname(out_path)
    os.makedirs(folder, exist_ok=True)

    def hook(d):
        if progress is None:
            return
        try:
            if d.get("status") == "downloading":
                total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
                done = d.get("downloaded_bytes") or 0
                if total:
                    progress.set(int(max(0, min(999, done * 1000 / total))))
                elif d.get("fragment_count"):
                    progress.set(int(max(0, min(999, (d.get("fragment_index") or 0) * 1000 / d["fragment_count"]))))
            elif d.get("status") == "finished":
                progress.set(1000)
        except Exception:
            pass

    attempts = _YT_CLIENTS if source == YT else [None]
    last = None
    for clients in attempts:
        try:
            opts = _opts({
                "format": _formats(source),
                "outtmpl": out_path + ".%(ext)s",
                "progress_hooks": [hook],
                "overwrites": True,
                "continuedl": False,
                "nopart": False,
                "postprocessors": [],
                "fixup": "never",
                "hls_prefer_native": True,
                "concurrent_fragment_downloads": 4,
            }, clients)
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(url, download=True)
                path = None
                req = (info or {}).get("requested_downloads") or []
                if req:
                    path = req[0].get("filepath") or req[0].get("_filename")
                if not path:
                    path = ydl.prepare_filename(info)
            if path and os.path.exists(path) and os.path.getsize(path) > 0:
                item = _entry(source, info) or {}
                return _ok(path=path, ext=(info or {}).get("ext"),
                           duration=int((info or {}).get("duration") or 0),
                           title=item.get("title"), artist=item.get("artist"), cover=item.get("cover"))
            last = Exception("file not written")
        except Exception as e:
            last = e
    return _fail(_classify(last), last)


def version():
    try:
        return yt_dlp.version.__version__
    except Exception:
        return "?"


def _selftest():
    # для проверки на компьютере: python3 femboy_music_online.py
    init("/tmp/claude-1000/-home-femboygram/35694441-9dfd-42cc-abbe-746c95f5e764/scratchpad/ytcache")
    print(search(SC, "daft punk", 3))
    print(related(SC, "254112175", "https://soundcloud.com/daftpunkofficialmusic/instant-crush", 3))


if __name__ == "__main__":
    _selftest()

package org.telegram.femboy;

import android.os.Build;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.TranslateAlert2;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Тексты песен. Сначала lrclib.net, и там всегда ищем синхронный текст:
 * точный запрос, потом поиск по нескольким вариантам названия. Если там
 * нет вообще ничего, берём обычный текст с Genius.
 *
 * Спрашиваем только название, исполнителя и длину трека: ни аккаунта, ни
 * номера, ничего о человеке туда не уходит.
 *
 * Ответ кладём в кэш файлом. «Не нашлось» помним пару дней, текст без
 * синхронизации неделю (его могут досинхронизировать), синхронный навсегда.
 * Туда же дописываем чтение латиницей, когда оно посчитано, и переводы
 * под ключами tr_<язык>.
 */
public final class FemboyLyrics {

    private FemboyLyrics() {}

    private static final String BASE = "https://lrclib.net/api/";
    private static final String GENIUS_SEARCH = "https://genius.com/api/search/multi?q=";
    // старый кэш помнил «не нашлось» и голый текст там, где синхронный есть: начинаем заново
    private static final String CACHE_DIR = "femboy_lyrics_v2";
    private static final String OLD_CACHE_DIR = "femboy_lyrics";
    private static final int TIMEOUT_MS = 10000;
    private static final long MISS_KEEP_MS = 2L * 24 * 60 * 60 * 1000;
    private static final long PLAIN_KEEP_MS = 7L * 24 * 60 * 60 * 1000;
    private static final String BROWSER_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";

    public static final String SOURCE_LRCLIB = "lrclib.net";
    public static final String SOURCE_GENIUS = "Genius";

    private static final DispatchQueue queue = new DispatchQueue("femboyLyrics");

    /** Строка синхронного текста: с какой миллисекунды и что поётся. */
    public static final class Line {
        public final long time;
        public final String text;

        Line(long time, String text) {
            this.time = time;
            this.text = text;
        }
    }

    /** Что нашлось. lines пустой, если синхронного текста нет, тогда смотреть plain. */
    public static final class Lyrics {
        public final ArrayList<Line> lines = new ArrayList<>();
        public String plain;
        public boolean instrumental;
        /** Откуда текст: lrclib.net или Genius. */
        public String source = SOURCE_LRCLIB;
        /** Чтение латиницей, по строке на каждую из textLines(); пустая строка, если читать нечего. */
        public ArrayList<String> romaji;
        /** Переводы по языку, тоже по строке на каждую из textLines(); пустая, где переводить нечего. */
        public final HashMap<String, ArrayList<String>> translations = new HashMap<>();
        /** Язык текста, когда уже определён; null, пока не знаем. */
        public String language;
        String cacheKey;

        public boolean synced() {
            return !lines.isEmpty();
        }

        public boolean empty() {
            return lines.isEmpty() && TextUtils.isEmpty(plain) && !instrumental;
        }

        /** Строки по порядку: у синхронного по меткам, у обычного по переносам (пустые остаются, это границы куплетов). */
        public List<String> textLines() {
            final ArrayList<String> out = new ArrayList<>();
            if (synced()) {
                for (Line line : lines) out.add(line.text == null ? "" : line.text);
            } else if (!TextUtils.isEmpty(plain)) {
                out.addAll(Arrays.asList(plain.split("\n", -1)));
            }
            return out;
        }
    }

    public interface Callback {
        /** На главном потоке; null, если текста нет или сеть не ответила. */
        void onLyrics(Lyrics lyrics);
    }

    private static final HashMap<String, Lyrics> memory = new HashMap<>();
    private static boolean oldCacheCleaned;

    public static void find(String title, String artist, int durationSec, Callback callback) {
        final String rawTitle = title == null ? "" : title.trim();
        final String rawArtist = artist == null ? "" : artist.trim();
        if (rawTitle.isEmpty()) {
            callback.onLyrics(null);
            return;
        }
        final String key = Utilities.MD5(rawArtist.toLowerCase(Locale.ROOT) + "\n" + rawTitle.toLowerCase(Locale.ROOT) + "\n" + durationSec);
        final Lyrics known = memory.get(key);
        if (known != null) {
            callback.onLyrics(known.empty() ? null : known);
            return;
        }
        queue.postRunnable(() -> {
            cleanOldCache();
            Lyrics found = null;
            final boolean[] answered = {false};
            final File cache = cacheFile(key);
            try {
                final JSONObject cached = readCache(cache);
                if (cached != null) {
                    answered[0] = true;
                    found = cached.optBoolean("none") ? new Lyrics() : parse(cached);
                } else {
                    JSONObject best = lookup(rawTitle, rawArtist, durationSec, answered);
                    if (best == null) best = genius(rawTitle, rawArtist, answered);
                    if (best != null) {
                        found = parse(best);
                        writeCache(cache, best.toString());
                    } else if (answered[0]) {
                        // сеть ответила, но текста нет нигде: запоминаем ненадолго
                        found = new Lyrics();
                        writeCache(cache, "{\"none\":true}");
                    }
                }
            } catch (Throwable error) {
                FileLog.e(error);
            }
            if (found != null) found.cacheKey = key;
            final Lyrics result = found;
            final boolean remember = answered[0];
            AndroidUtilities.runOnUIThread(() -> {
                if (result != null && remember) memory.put(key, result);
                callback.onLyrics(result == null || result.empty() ? null : result);
            });
        });
    }

    // ---- lrclib ----

    private static final class Candidate {
        JSONObject json;
        int score;
        boolean synced;
        boolean close;
    }

    /** Лучший ответ lrclib, синхронный по возможности; null, если текста там нет. */
    private static JSONObject lookup(String rawTitle, String rawArtist, int durationSec, boolean[] answered) throws Exception {
        String title = rawTitle, artist = cleanArtist(rawArtist);
        // «Исполнитель - Песня» прямо в названии, а исполнителя нет
        final int dash = title.indexOf(" - ");
        if (artist.isEmpty() && dash > 0) {
            artist = cleanArtist(title.substring(0, dash));
            title = title.substring(dash + 3);
        }
        final String clean = cleanTitle(title);
        final String primary = primaryArtist(artist);

        final ArrayList<Candidate> found = new ArrayList<>();
        final HashSet<Long> seen = new HashSet<>();

        // точный запрос: и с исходным названием, и с почищенным
        if (!artist.isEmpty()) {
            final LinkedHashSet<String> gets = new LinkedHashSet<>();
            gets.add(artist + "\n" + title.trim());
            gets.add(primary + "\n" + clean);
            for (String one : gets) {
                final String[] parts = one.split("\n", 2);
                final StringBuilder url = new StringBuilder(BASE).append("get?artist_name=").append(enc(parts[0]))
                        .append("&track_name=").append(enc(parts[1]));
                if (durationSec > 0) url.append("&duration=").append(durationSec);
                final String reply = request(url.toString(), false);
                if (reply == null) continue;
                answered[0] = true;
                if (reply.isEmpty()) continue;
                final Candidate c = consider(new JSONObject(reply), clean, primary, durationSec, seen);
                if (c != null) {
                    found.add(c);
                    if (c.synced && c.close) return c.json;
                }
            }
        }

        // поиск по нескольким вариантам, пока не найдётся синхронный той же длины
        final LinkedHashSet<String> searches = new LinkedHashSet<>();
        if (!primary.isEmpty()) searches.add("track_name=" + enc(clean) + "&artist_name=" + enc(primary));
        if (!primary.isEmpty()) searches.add("q=" + enc(primary + " " + clean));
        searches.add("q=" + enc(clean));
        if (clean.matches(".*['’`].*")) {
            // «Butcher's Vanity» lrclib не находит вовсе, а «Butcher Vanity» находит
            final String noPossessive = clean.replaceAll("['’`]s\\b", "").replaceAll("\\s+", " ").trim();
            final String noApostrophe = clean.replaceAll("['’`]", "");
            if (!primary.isEmpty()) searches.add("q=" + enc(primary + " " + noPossessive));
            searches.add("q=" + enc(noPossessive));
            searches.add("q=" + enc(noApostrophe));
        }
        for (String query : searches) {
            final String reply = request(BASE + "search?" + query, false);
            if (reply == null) continue;
            answered[0] = true;
            if (reply.isEmpty()) continue;
            final JSONArray array = new JSONArray(reply);
            boolean good = false;
            for (int i = 0; i < array.length(); i++) {
                final JSONObject one = array.optJSONObject(i);
                if (one == null) continue;
                final Candidate c = consider(one, clean, primary, durationSec, seen);
                if (c == null) continue;
                found.add(c);
                if (c.synced && c.close) good = true;
            }
            if (good) break;
        }

        Candidate best = null;
        for (Candidate c : found) {
            if (best == null || c.score > best.score) best = c;
        }
        return best == null ? null : best.json;
    }

    private static Candidate consider(JSONObject one, String wantTitle, String wantArtist, int durationSec, HashSet<Long> seen) {
        if (!hasText(one)) return null;
        final long id = one.optLong("id", 0);
        if (id != 0 && !seen.add(id)) return null;
        final int titleScore = titleScore(wantTitle, one.optString("trackName", ""));
        if (titleScore < 0) return null;
        int score = titleScore;
        if (!wantArtist.isEmpty() && artistMatches(wantArtist, one.optString("artistName", ""))) score += 25;
        boolean close = durationSec <= 0;
        if (durationSec > 0) {
            final double length = one.optDouble("duration", 0);
            if (length > 0) {
                final double diff = Math.abs(length - durationSec);
                if (diff > 20) return null;
                close = diff <= 5;
                score += close ? 40 - (int) (diff * 2) : 15 - (int) diff;
            }
        }
        // инструментальная версия, когда просили обычную: мимо
        final boolean wantInstrumental = INSTRUMENTAL.matcher(wantTitle).find();
        if (!wantInstrumental && INSTRUMENTAL.matcher(one.optString("trackName", "")).find()) score -= 80;
        final boolean synced = hasSynced(one);
        if (synced) score += 100;
        else if (TextUtils.isEmpty(textOf(one, "plainLyrics"))) score -= 40;
        final Candidate c = new Candidate();
        c.json = one;
        c.score = score;
        c.synced = synced;
        c.close = close && titleScore >= 35;
        return c;
    }

    private static final Pattern INSTRUMENTAL = Pattern.compile("(?i)instrumental|karaoke|off vocal|backing track");

    /** Насколько похоже название: 60 одинаковые, 35 одно внутри другого, меньше по общим словам, -1 мимо. */
    private static int titleScore(String want, String got) {
        final String a = norm(want), b = norm(cleanTitle(got));
        if (a.isEmpty() || b.isEmpty()) return -1;
        if (a.equals(b)) return 60;
        if (a.contains(b) || b.contains(a)) return 35;
        final String[] wanted = a.split(" ");
        final List<String> have = Arrays.asList(b.split(" "));
        int hits = 0;
        for (String word : wanted) {
            if (have.contains(word) || have.contains(word + "s") || word.endsWith("s") && have.contains(word.substring(0, word.length() - 1))) hits++;
        }
        final float share = hits / (float) wanted.length;
        return share < 0.5f ? -1 : (int) (share * 30);
    }

    private static boolean artistMatches(String want, String got) {
        final String a = norm(want), b = norm(got);
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.contains(b) || b.contains(a)) return true;
        for (String word : a.split(" ")) {
            if (word.length() >= 3 && b.contains(word)) return true;
        }
        return false;
    }

    /** Нижний регистр, без апострофов и знаков, пробелы схлопнуты. Иероглифы и кириллица остаются. */
    static String norm(String text) {
        if (text == null) return "";
        final String lower = text.toLowerCase(Locale.ROOT).replaceAll("['’`]", "");
        final StringBuilder out = new StringBuilder(lower.length());
        boolean space = false;
        for (int i = 0; i < lower.length(); i++) {
            final char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
                space = false;
            } else if (!space && out.length() > 0) {
                out.append(' ');
                space = true;
            }
        }
        return out.toString().trim();
    }

    private static final Pattern BRACKET_JUNK = Pattern.compile(
            "(?i)\\s*[(\\[【][^)\\]】]*(feat|ft\\.|featuring|prod\\.|official|lyric|audio|video|visuali[sz]er|\\bmv\\b|m/v|\\bhd\\b|\\bhq\\b|\\b4k\\b|remaster|slowed|sped up|speed up|nightcore|reverb|bass boost|explicit|\\bclean\\b|\\bfrom\\b|\\bost\\b|soundtrack|radio edit|version|ver\\.)[^)\\]】]*[)\\]】]");
    private static final Pattern ANY_SQUARE = Pattern.compile("\\s*[\\[【][^\\]】]*[\\]】]");
    private static final Pattern TAIL_FEAT = Pattern.compile("(?i)\\s+(feat\\.?|ft\\.?|featuring)\\s+.*$");
    private static final Pattern TAIL_JUNK = Pattern.compile("(?i)\\s*[-–—|]\\s*(official.*|lyrics?.*|audio|video|.*remaster.*|slowed.*|sped up.*|speed up.*|nightcore.*|from .*|topic)$");
    private static final Pattern TAIL_SPEED = Pattern.compile("(?i)\\s*\\(?\\s*(slowed( \\+| and| &)? reverb|slowed|sped up|speed up|nightcore)\\s*\\)?\\s*$");

    /** Название без «(feat…)», «[…]», «Official Video», «(Lyrics)», «slowed», «sped up» и прочего. */
    static String cleanTitle(String title) {
        if (title == null) return "";
        String s = title;
        s = BRACKET_JUNK.matcher(s).replaceAll("");
        s = ANY_SQUARE.matcher(s).replaceAll("");
        s = TAIL_FEAT.matcher(s).replaceAll("");
        s = TAIL_JUNK.matcher(s).replaceAll("");
        s = TAIL_SPEED.matcher(s).replaceAll("");
        s = s.replace('“', '"').replace('”', '"').replaceAll("^\"(.*)\"$", "$1");
        s = s.replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? title.trim() : s;
    }

    /** Исполнитель без « - Topic», «VEVO» и «Official» от ютуба. */
    static String cleanArtist(String artist) {
        if (artist == null) return "";
        String s = artist.replaceAll("(?i)\\s*-\\s*topic$", "")
                .replaceAll("(?i)vevo$", "")
                .replaceAll("(?i)\\s+official$", "")
                .replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? artist.trim() : s;
    }

    /** Первый из нескольких исполнителей: lrclib плохо ищет по «A, B & C». */
    static String primaryArtist(String artist) {
        if (TextUtils.isEmpty(artist)) return "";
        final String[] parts = artist.split("(?i)\\s*(,|&|;|/|\\s+x\\s+|\\s+feat\\.?\\s+|\\s+ft\\.?\\s+|\\s+and\\s+|\\s+with\\s+)\\s*");
        final String first = parts.length > 0 ? parts[0].trim() : "";
        return first.isEmpty() ? artist.trim() : first;
    }

    private static String enc(String text) throws Exception {
        return URLEncoder.encode(text, "UTF-8");
    }

    private static String textOf(JSONObject one, String field) {
        if (one.isNull(field)) return null;
        final String value = one.optString(field, null);
        return TextUtils.isEmpty(value) || "null".equals(value) ? null : value;
    }

    private static boolean hasSynced(JSONObject one) {
        return !TextUtils.isEmpty(textOf(one, "syncedLyrics"));
    }

    private static boolean hasText(JSONObject one) {
        return one.optBoolean("instrumental") || hasSynced(one) || !TextUtils.isEmpty(textOf(one, "plainLyrics"));
    }

    // ---- Genius ----

    /** Обычный текст с Genius: верхний трек из их поиска, текст со страницы. null, если не вышло. */
    private static JSONObject genius(String rawTitle, String rawArtist, boolean[] answered) throws Exception {
        String title = rawTitle, artist = cleanArtist(rawArtist);
        final int dash = title.indexOf(" - ");
        if (artist.isEmpty() && dash > 0) {
            artist = cleanArtist(title.substring(0, dash));
            title = title.substring(dash + 3);
        }
        final String clean = cleanTitle(title);
        final String primary = primaryArtist(artist);
        final LinkedHashSet<String> queries = new LinkedHashSet<>();
        if (!primary.isEmpty()) queries.add(primary + " " + clean);
        queries.add(clean);
        for (String q : queries) {
            final String reply = request(GENIUS_SEARCH + enc(q), true);
            if (reply == null) continue;
            answered[0] = true;
            if (reply.isEmpty()) continue;
            final String url = pickGenius(new JSONObject(reply), clean, primary);
            if (url == null) continue;
            final String page = request(url, true);
            if (TextUtils.isEmpty(page)) continue;
            final String text = geniusText(page);
            if (TextUtils.isEmpty(text)) continue;
            final JSONObject out = new JSONObject();
            out.put("plainLyrics", text);
            out.put("src", SOURCE_GENIUS);
            out.put("url", url);
            return out;
        }
        return null;
    }

    private static String pickGenius(JSONObject reply, String wantTitle, String wantArtist) {
        final JSONObject response = reply.optJSONObject("response");
        final JSONArray sections = response == null ? null : response.optJSONArray("sections");
        if (sections == null) return null;
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int s = 0; s < sections.length(); s++) {
            final JSONObject section = sections.optJSONObject(s);
            if (section == null) continue;
            final String type = section.optString("type");
            // «lyric» это совпадения внутри чужих текстов: туда не смотрим
            if (!"top_hit".equals(type) && !"song".equals(type)) continue;
            final JSONArray hits = section.optJSONArray("hits");
            if (hits == null) continue;
            for (int h = 0; h < hits.length(); h++) {
                final JSONObject hit = hits.optJSONObject(h);
                if (hit == null || !"song".equals(hit.optString("type"))) continue;
                final JSONObject result = hit.optJSONObject("result");
                if (result == null) continue;
                final String url = result.optString("url", null);
                if (url == null || !url.startsWith("https://genius.com/")) continue;
                final int titleScore = titleScore(wantTitle, result.optString("title", ""));
                if (titleScore < 0) continue;
                int score = titleScore;
                final JSONObject artist = result.optJSONObject("primary_artist");
                if (!wantArtist.isEmpty() && artist != null && artistMatches(wantArtist, artist.optString("name", ""))) score += 25;
                if ("top_hit".equals(type)) score += 5;
                // первые в выдаче чуть вероятнее
                score -= h;
                if (score > bestScore) {
                    bestScore = score;
                    best = url;
                }
            }
        }
        return best;
    }

    private static final Pattern DIV_TAG = Pattern.compile("<(/?)div\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern BR = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);");

    /** Конец div, который начинается с from (там стоит «<div»): считаем вложенные. */
    private static int divEnd(String html, int from) {
        final Matcher m = DIV_TAG.matcher(html);
        int depth = 0;
        int at = from;
        while (m.find(at)) {
            depth += m.group(1).isEmpty() ? 1 : -1;
            at = m.end();
            if (depth == 0) return m.end();
        }
        return html.length();
    }

    /** Текст из всех div[data-lyrics-container]: переносы из <br>, теги прочь, сущности раскрыты. */
    static String geniusText(String html) {
        final StringBuilder out = new StringBuilder();
        int pos = 0;
        while (true) {
            final int mark = html.indexOf("data-lyrics-container=\"true\"", pos);
            if (mark < 0) break;
            final int start = html.lastIndexOf("<div", mark);
            final int open = html.indexOf('>', mark);
            if (start < 0 || open < 0) break;
            final int end = divEnd(html, start);
            String inner = html.substring(open + 1, Math.max(open + 1, end - "</div>".length()));
            // шапка «N Contributors… Lyrics» и прочее, что Genius сам просит не выделять
            while (true) {
                final int skip = inner.indexOf("data-exclude-from-selection");
                if (skip < 0) break;
                final int skipStart = inner.lastIndexOf("<div", skip);
                if (skipStart < 0) break;
                inner = inner.substring(0, skipStart) + inner.substring(divEnd(inner, skipStart));
            }
            inner = BR.matcher(inner).replaceAll("\n");
            inner = TAG.matcher(inner).replaceAll("");
            if (out.length() > 0) out.append('\n');
            out.append(unescape(inner));
            pos = end;
        }
        return tidy(out.toString());
    }

    private static String unescape(String text) {
        final Matcher m = ENTITY.matcher(text);
        final StringBuffer out = new StringBuffer();
        while (m.find()) {
            final String e = m.group(1);
            String r;
            try {
                if (e.startsWith("#x")) r = new String(Character.toChars(Integer.parseInt(e.substring(2), 16)));
                else if (e.startsWith("#")) r = new String(Character.toChars(Integer.parseInt(e.substring(1))));
                else {
                    switch (e) {
                        case "amp": r = "&"; break;
                        case "lt": r = "<"; break;
                        case "gt": r = ">"; break;
                        case "quot": r = "\""; break;
                        case "apos": r = "'"; break;
                        case "nbsp": r = " "; break;
                        default: r = m.group(0);
                    }
                }
            } catch (Throwable ignored) {
                r = m.group(0);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(r));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static final String HOMOGLYPHS_CYR = "аеорсухіАЕОРСХ";
    private static final String HOMOGLYPHS_LAT = "aeopcyxiAEOPCX";

    /** Genius подмешивает кириллицу в английские слова («mysеlf»): возвращаем латиницу. */
    private static String fixHomoglyphs(String line) {
        final StringBuilder out = new StringBuilder(line);
        int i = 0;
        while (i < out.length()) {
            if (!Character.isLetter(out.charAt(i))) {
                i++;
                continue;
            }
            int j = i;
            boolean latin = false, cyr = false;
            while (j < out.length() && Character.isLetter(out.charAt(j))) {
                final char c = out.charAt(j);
                if (c < 128) latin = true;
                else if (HOMOGLYPHS_CYR.indexOf(c) >= 0) cyr = true;
                j++;
            }
            if (latin && cyr) {
                for (int k = i; k < j; k++) {
                    final int at = HOMOGLYPHS_CYR.indexOf(out.charAt(k));
                    if (at >= 0) out.setCharAt(k, HOMOGLYPHS_LAT.charAt(at));
                }
            }
            i = j;
        }
        return out.toString();
    }

    /** Строки без хвостов, куплеты через одну пустую строку, перед [Припевом] всегда отступ. */
    private static String tidy(String text) {
        final String[] raw = text.replace("\r", "").split("\n", -1);
        final StringBuilder out = new StringBuilder();
        boolean lastEmpty = true;
        for (String one : raw) {
            final String line = fixHomoglyphs(one.trim());
            if (line.isEmpty()) {
                if (!lastEmpty) out.append('\n');
                lastEmpty = true;
                continue;
            }
            if (line.startsWith("[") && !lastEmpty) out.append('\n');
            out.append(line).append('\n');
            lastEmpty = false;
        }
        return out.toString().trim();
    }

    // ---- разбор ----

    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");

    static Lyrics parse(JSONObject one) {
        final Lyrics lyrics = new Lyrics();
        lyrics.instrumental = one.optBoolean("instrumental");
        final String src = one.optString("src", null);
        if (!TextUtils.isEmpty(src)) lyrics.source = src;
        final String plain = textOf(one, "plainLyrics");
        lyrics.plain = TextUtils.isEmpty(plain) ? null : plain.replace("\r", "").trim();
        final String synced = textOf(one, "syncedLyrics");
        if (!TextUtils.isEmpty(synced)) {
            for (String raw : synced.split("\n")) {
                final Matcher m = STAMP.matcher(raw);
                final ArrayList<Long> times = new ArrayList<>();
                int end = 0;
                // у строки бывает несколько меток подряд: [00:12.00][01:40.00]припев
                while (m.find() && m.start() == end) {
                    long ms = Long.parseLong(m.group(1)) * 60000L + Long.parseLong(m.group(2)) * 1000L;
                    final String part = m.group(3);
                    if (part != null) {
                        if (part.length() == 1) ms += Long.parseLong(part) * 100;
                        else if (part.length() == 2) ms += Long.parseLong(part) * 10;
                        else ms += Long.parseLong(part);
                    }
                    times.add(ms);
                    end = m.end();
                }
                if (times.isEmpty()) continue;
                final String text = raw.substring(end).trim();
                for (long time : times) lyrics.lines.add(new Line(time, text));
            }
            java.util.Collections.sort(lyrics.lines, (a, b) -> Long.compare(a.time, b.time));
        }
        final JSONArray romaji = one.optJSONArray("romaji3");
        if (romaji != null && romaji.length() == lyrics.textLines().size()) {
            lyrics.romaji = new ArrayList<>();
            for (int i = 0; i < romaji.length(); i++) lyrics.romaji.add(romaji.optString(i, ""));
        }
        final int count = lyrics.textLines().size();
        final java.util.Iterator<String> keys = one.keys();
        while (keys.hasNext()) {
            final String name = keys.next();
            if (!name.startsWith(TRANSLATION_PREFIX)) continue;
            final JSONArray array = one.optJSONArray(name);
            if (array == null || array.length() != count) continue;
            final ArrayList<String> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) list.add(array.optString(i, ""));
            lyrics.translations.put(name.substring(TRANSLATION_PREFIX.length()), list);
        }
        return lyrics;
    }

    // ---- чтение латиницей ----

    private static boolean isKana(char c) {
        return c >= 0x3040 && c <= 0x30ff || c >= 0x31f0 && c <= 0x31ff || c >= 0xff66 && c <= 0xff9f;
    }

    private static boolean isHan(char c) {
        return c >= 0x4e00 && c <= 0x9fff || c >= 0x3400 && c <= 0x4dbf || c >= 0xf900 && c <= 0xfaff;
    }

    private static boolean isHangul(char c) {
        return c >= 0xac00 && c <= 0xd7af || c >= 0x1100 && c <= 0x11ff || c >= 0x3130 && c <= 0x318f;
    }

    private static boolean has(String line, int kind) {
        if (line == null) return false;
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (kind == 0 && isKana(c) || kind == 1 && isHan(c) || kind == 2 && isHangul(c)) return true;
        }
        return false;
    }

    /** Есть ли в тексте японское, китайское или корейское: тогда над строками нужно чтение. */
    public static boolean needsRomaji(Lyrics lyrics) {
        if (lyrics == null) return false;
        for (String line : lyrics.textLines()) {
            if (has(line, 0) || has(line, 1) || has(line, 2)) return true;
        }
        return false;
    }

    /** Посчитать чтение латиницей не на главном потоке; done на главном, когда lyrics.romaji готов. */
    public static void romanize(Lyrics lyrics, Runnable done) {
        if (lyrics == null) return;
        if (lyrics.romaji != null) {
            done.run();
            return;
        }
        queue.postRunnable(() -> {
            ArrayList<String> result;
            try {
                result = romanizeLines(lyrics.textLines());
            } catch (Throwable error) {
                FileLog.e(error);
                result = null;
            }
            final ArrayList<String> ready = result;
            // запасное чтение (питон не ответил) не кэшируем: иначе сбой застрянет навсегда
            if (ready != null && lyrics.cacheKey != null && pythonAnswered) saveRomaji(lyrics.cacheKey, ready);
            AndroidUtilities.runOnUIThread(() -> {
                if (ready != null) lyrics.romaji = ready;
                done.run();
            });
        });
    }

    /** Ответил ли питон в последнем romanizeLines (зовётся на одной очереди). */
    private static volatile boolean pythonAnswered;

    private static ArrayList<String> romanizeLines(List<String> lines) {
        pythonAnswered = true;
        boolean japanese = false;
        for (String line : lines) {
            if (has(line, 0)) {
                japanese = true;
                break;
            }
        }
        final String[] out = new String[lines.size()];
        Arrays.fill(out, "");
        final ArrayList<Integer> forPython = new ArrayList<>();
        for (int i = 0; i < out.length; i++) {
            final String line = lines.get(i);
            final boolean kana = has(line, 0), han = has(line, 1), hangul = has(line, 2);
            if (!kana && !han && !hangul) continue;
            if (hangul && !kana) {
                out[i] = icu("Hangul-Latin", line);
            } else if (japanese) {
                // в японской песне и строки из одних иероглифов читаем по-японски
                forPython.add(i);
            } else {
                out[i] = icu("Han-Latin", line);
            }
        }
        if (!forPython.isEmpty()) {
            pythonAnswered = false;
            final String[] batch = new String[forPython.size()];
            for (int i = 0; i < batch.length; i++) batch[i] = lines.get(forPython.get(i));
            String[] answer = null;
            try {
                answer = (String[]) Class.forName("org.telegram.femboy.FemboyRomajiPython")
                        .getMethod("romanize", String[].class)
                        .invoke(null, (Object) batch);
                pythonAnswered = true;
            } catch (Throwable error) {
                // FileLog в нашей сборке выключен: пишем в системный лог, иначе причину не найти
                android.util.Log.e("FemboyRomaji", "pykakasi не ответил", error instanceof java.lang.reflect.InvocationTargetException ? error.getCause() : error);
            }
            for (int i = 0; i < batch.length; i++) {
                String one = answer != null && i < answer.length ? answer[i] : null;
                if (one == null && !has(batch[i], 1)) {
                    // без питона хотя бы кану прочтём, а иероглифы китайским чтением не портим
                    one = icu("Hiragana-Latin; Katakana-Latin", batch[i]);
                }
                out[forPython.get(i)] = one == null ? "" : one;
            }
        }
        final ArrayList<String> result = new ArrayList<>(out.length);
        for (int i = 0; i < out.length; i++) {
            final String one = out[i] == null ? "" : out[i].trim();
            result.add(one.equals(lines.get(i).trim()) ? "" : one);
        }
        return result;
    }

    private static final HashMap<String, Object> transliterators = new HashMap<>();

    private static String icu(String id, String line) {
        if (Build.VERSION.SDK_INT < 29) return "";
        try {
            android.icu.text.Transliterator t = (android.icu.text.Transliterator) transliterators.get(id);
            if (t == null) {
                t = android.icu.text.Transliterator.getInstance(id);
                transliterators.put(id, t);
            }
            return t.transliterate(line).replaceAll("\\s+", " ").trim();
        } catch (Throwable error) {
            FileLog.e(error);
            return "";
        }
    }

    private static void saveRomaji(String key, ArrayList<String> romaji) {
        saveField(key, "romaji3", romaji);
    }

    /** Дописать в кэш песни массив строк под именем field; сам ответ поиска не трогаем. */
    private static synchronized void saveField(String key, String field, ArrayList<String> lines) {
        final File file = cacheFile(key);
        if (!file.exists()) return;
        try {
            final JSONObject one;
            try (FileInputStream in = new FileInputStream(file)) {
                one = new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
            }
            if (one.optBoolean("none")) return;
            one.put(field, new JSONArray(lines));
            final long modified = file.lastModified();
            writeCache(file, one.toString());
            // срок жизни считаем от поиска, а не от чтения латиницей
            file.setLastModified(modified);
        } catch (Throwable error) {
            FileLog.e(error);
        }
    }

    // ---- язык и перевод ----

    private static final String TRANSLATION_PREFIX = "tr_";
    /** Строк за один запрос: столько же берёт TranslateController (MAX_MESSAGES_PER_REQUEST). */
    private static final int TRANSLATE_BATCH_LINES = 20;
    /** Символов за запрос, с запасом до серверных 25000. */
    private static final int TRANSLATE_BATCH_CHARS = 5000;
    private static final long DETECT_TIMEOUT_MS = 2000;

    /** Свой поток для перевода: питон с чтением латиницей может долго держать queue. */
    private static final DispatchQueue translateQueue = new DispatchQueue("femboyLyricsTr");

    /** Стоит ли переводить строку: не пустая, не метка вроде [Chorus], есть хоть одна буква. */
    public static boolean translatable(String line) {
        if (line == null) return false;
        final String t = line.trim();
        if (t.isEmpty()) return false;
        if (t.length() > 2 && t.startsWith("[") && t.endsWith("]")) return false;
        for (int i = 0; i < t.length(); i++) {
            if (Character.isLetter(t.charAt(i))) return true;
        }
        return false;
    }

    /** Код языка без региона и письменности, в одном написании: iw и he одно и то же. */
    public static String baseLanguage(String lang) {
        if (TextUtils.isEmpty(lang)) return null;
        String base = lang.toLowerCase(Locale.ROOT);
        for (int i = 0; i < base.length(); i++) {
            final char c = base.charAt(i);
            if (c == '-' || c == '_') {
                base = base.substring(0, i);
                break;
            }
        }
        switch (base) {
            case "": case "und": return null;
            case "iw": return "he";
            case "in": return "id";
            case "ji": return "yi";
            case "nb": case "nn": return "no";
            default: return base;
        }
    }

    /**
     * Язык по письменности, дёшево и без сети. Иероглиф весит как слово, а буква
     * нет, поэтому японский с английскими вставками остаётся японским.
     * Латиницу считаем английским: точнее скажет ML Kit, если он есть.
     */
    public static String guessLanguage(Lyrics lyrics) {
        int kana = 0, han = 0, hangul = 0, cyrillic = 0, ukrainian = 0, latin = 0;
        for (String line : lyrics.textLines()) {
            if (!translatable(line)) continue;
            for (int i = 0; i < line.length(); i++) {
                final char c = line.charAt(i);
                if (isKana(c)) kana++;
                else if (isHan(c)) han++;
                else if (isHangul(c)) hangul++;
                else if (c >= 0x0400 && c <= 0x04ff) {
                    cyrillic++;
                    if ("іїєґІЇЄҐ".indexOf(c) >= 0) ukrainian++;
                } else if (c < 0x0250 && Character.isLetter(c)) latin++;
            }
        }
        final int cjk = kana + han + hangul;
        if (cjk > 0 && cjk * 3 >= latin + cyrillic) {
            if (hangul >= kana + han) return "ko";
            return kana > 0 ? "ja" : "zh";
        }
        if (cyrillic > 0 && cyrillic >= latin) {
            return ukrainian >= Math.max(2, cyrillic / 200) ? "uk" : "ru";
        }
        return latin > 0 ? "en" : null;
    }

    /** Кусок текста для ML Kit: разные строки, без меток и повторов припева. */
    private static String sample(Lyrics lyrics) {
        final StringBuilder out = new StringBuilder();
        final HashSet<String> seen = new HashSet<>();
        for (String line : lyrics.textLines()) {
            if (!translatable(line)) continue;
            final String t = line.trim();
            if (!seen.add(t)) continue;
            if (out.length() > 0) out.append('\n');
            out.append(t);
            if (out.length() > 1500) break;
        }
        return out.toString();
    }

    /**
     * Определить язык текста; done на главном потоке, null, если не понять.
     * Китайский, японский и корейский видно по письменности, остальное спрашиваем
     * у ML Kit (LanguageDetector телеграма), а если он молчит, берём догадку.
     */
    public static void detectLanguage(Lyrics lyrics, Utilities.Callback<String> done) {
        if (lyrics == null) return;
        if (lyrics.language != null) {
            done.run(lyrics.language);
            return;
        }
        final String guess = guessLanguage(lyrics);
        if (guess == null || "ja".equals(guess) || "zh".equals(guess) || "ko".equals(guess) || !LanguageDetector.hasSupport()) {
            lyrics.language = guess;
            done.run(guess);
            return;
        }
        final boolean[] answered = new boolean[1];
        final Utilities.Callback<String> finish = detected -> {
            if (answered[0]) return;
            answered[0] = true;
            String lang = baseLanguage(detected);
            if (lang == null) lang = guess;
            lyrics.language = lang;
            done.run(lang);
        };
        try {
            LanguageDetector.detectLanguage(sample(lyrics),
                    lang -> AndroidUtilities.runOnUIThread(() -> finish.run(lang)),
                    error -> AndroidUtilities.runOnUIThread(() -> finish.run(null)));
        } catch (Throwable error) {
            FileLog.e(error);
        }
        // ML Kit бывает не отвечает вовсе: тогда хватит догадки по письменности
        AndroidUtilities.runOnUIThread(() -> finish.run(null), DETECT_TIMEOUT_MS);
    }

    public interface TranslateCallback {
        /** На главном потоке: lines по строке на каждую из textLines(), или error, если не вышло. */
        void onTranslated(ArrayList<String> lines, String error);
    }

    /**
     * Перевести текст переводчиком телеграма (messages.translateText) на язык toLang.
     * Одинаковые строки уходят один раз, пачками по 20 строк, пачки по очереди.
     * Готовое кладём в кэш песни под "tr_<язык>", второй раз сеть не нужна.
     */
    public static void translate(Lyrics lyrics, String toLang, int account, TranslateCallback done) {
        final String lang = baseLanguage(toLang);
        if (lyrics == null || lang == null) {
            done.onTranslated(null, null);
            return;
        }
        final ArrayList<String> ready = lyrics.translations.get(lang);
        if (ready != null) {
            done.onTranslated(ready, null);
            return;
        }
        final List<String> lines = lyrics.textLines();
        final ArrayList<String> sources = new ArrayList<>();
        final HashMap<String, Integer> index = new HashMap<>();
        final int[] slot = new int[lines.size()];
        for (int i = 0; i < slot.length; i++) {
            slot[i] = -1;
            if (!translatable(lines.get(i))) continue;
            final String t = lines.get(i).trim();
            Integer at = index.get(t);
            if (at == null) {
                at = sources.size();
                index.put(t, at);
                sources.add(t);
            }
            slot[i] = at;
        }
        final String[] out = new String[sources.size()];
        final String cacheKey = lyrics.cacheKey;
        translateQueue.postRunnable(() -> translateBatch(sources, out, 0, lang, account, false, error -> {
            ArrayList<String> result = null;
            if (error == null) {
                result = new ArrayList<>(slot.length);
                for (int i = 0; i < slot.length; i++) {
                    String one = slot[i] < 0 || out[slot[i]] == null ? "" : out[slot[i]].trim();
                    // перевод слово в слово как оригинал («oh oh oh») не показываем
                    if (slot[i] >= 0 && one.equalsIgnoreCase(sources.get(slot[i]))) one = "";
                    result.add(one);
                }
                if (cacheKey != null) saveField(cacheKey, TRANSLATION_PREFIX + lang, result);
            }
            final ArrayList<String> finalResult = result;
            AndroidUtilities.runOnUIThread(() -> {
                if (finalResult != null) lyrics.translations.put(lang, finalResult);
                done.onTranslated(finalResult, error);
            });
        }));
    }

    /** Одна пачка с from; error в finish null, если всё переведено. alt: сервер просит переводить самим. */
    private static void translateBatch(ArrayList<String> sources, String[] out, int from, String lang, int account, boolean alt, Utilities.Callback<String> finish) {
        if (from >= sources.size()) {
            finish.run(null);
            return;
        }
        int end = from, chars = 0;
        while (end < sources.size() && end - from < TRANSLATE_BATCH_LINES
                && (end == from || chars + sources.get(end).length() <= TRANSLATE_BATCH_CHARS)) {
            chars += sources.get(end).length();
            end++;
        }
        final int to = end;
        if (alt) {
            translateAlt(sources, out, from, to, lang, account, finish);
            return;
        }
        final TLRPC.TL_messages_translateText req = new TLRPC.TL_messages_translateText();
        req.flags |= 2;
        for (int i = from; i < to; i++) {
            final TLRPC.TL_textWithEntities text = new TLRPC.TL_textWithEntities();
            text.text = sources.get(i);
            req.text.add(text);
        }
        req.to_lang = TranslateController.normalizeLanguage(lang);
        ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> translateQueue.postRunnable(() -> {
            if (res instanceof TLRPC.TL_messages_translateResult) {
                final ArrayList<TLRPC.TL_textWithEntities> result = ((TLRPC.TL_messages_translateResult) res).result;
                if (result == null || result.size() != to - from) {
                    finish.run("TRANSLATE_RESULT_MISMATCH");
                    return;
                }
                for (int i = 0; i < result.size(); i++) {
                    final TLRPC.TL_textWithEntities one = result.get(i);
                    out[from + i] = one == null || one.text == null ? "" : one.text;
                }
                translateBatch(sources, out, to, lang, account, false, finish);
            } else if (err != null && "TRANSLATIONS_DISABLED_ALT".equalsIgnoreCase(err.text)) {
                // как в TranslateAlert2: сервер сам отправляет к запасному переводчику
                translateAlt(sources, out, from, to, lang, account, finish);
            } else {
                finish.run(err != null && !TextUtils.isEmpty(err.text) ? err.text : "TRANSLATE_FAILED");
            }
        }));
    }

    /** Запасной переводчик телеграма: пачка одной строкой через перенос, обратно режем по нему же. */
    private static void translateAlt(ArrayList<String> sources, String[] out, int from, int to, String lang, int account, Utilities.Callback<String> finish) {
        final StringBuilder joined = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) joined.append('\n');
            joined.append(sources.get(i));
        }
        TranslateAlert2.alternativeTranslate(joined.toString(), "auto", lang, (result, rateLimit) -> translateQueue.postRunnable(() -> {
            if (result == null) {
                finish.run(rateLimit != null && rateLimit ? "FLOOD_WAIT" : "TRANSLATE_FAILED");
                return;
            }
            final String[] parts = result.trim().split("\n", -1);
            if (parts.length != to - from) {
                finish.run("TRANSLATE_RESULT_MISMATCH");
                return;
            }
            for (int i = 0; i < parts.length; i++) out[from + i] = parts[i];
            translateBatch(sources, out, to, lang, account, true, finish);
        }));
    }

    // ---- сеть и кэш ----

    /** null: сеть не ответила. Пустая строка: ответила, но ничего нет (404). */
    private static String request(String address, boolean browser) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            if (browser) {
                // Genius отдаёт страницы только тем, кто похож на браузер
                connection.setRequestProperty("User-Agent", BROWSER_UA);
                connection.setRequestProperty("Accept", "text/html,application/json;q=0.9,*/*;q=0.8");
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.8");
            } else {
                connection.setRequestProperty("User-Agent", "FemboyGram/" + FemboyConfig.APP_VERSION + " (https://github.com/webrch/femboy-gram)");
                connection.setRequestProperty("Accept", "application/json");
            }
            final int code = connection.getResponseCode();
            if (code == 404) return "";
            if (code != 200) return null;
            try (InputStream in = connection.getInputStream()) {
                return new String(readAll(in), StandardCharsets.UTF_8);
            }
        } catch (Throwable error) {
            FileLog.e(error);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private static File cacheFile(String key) {
        final File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), CACHE_DIR);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, key + ".json");
    }

    private static void cleanOldCache() {
        if (oldCacheCleaned) return;
        oldCacheCleaned = true;
        try {
            final File old = new File(ApplicationLoader.applicationContext.getCacheDir(), OLD_CACHE_DIR);
            final File[] files = old.listFiles();
            if (files != null) for (File f : files) f.delete();
            old.delete();
        } catch (Throwable ignored) {
        }
    }

    private static JSONObject readCache(File file) {
        if (!file.exists()) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            final JSONObject one = new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
            final long age = System.currentTimeMillis() - file.lastModified();
            if (one.optBoolean("none") && age > MISS_KEEP_MS) return null;
            // без синхронизации: через неделю спросим снова, вдруг появилась
            if (!one.optBoolean("none") && !hasSynced(one) && !one.optBoolean("instrumental") && age > PLAIN_KEEP_MS) return null;
            return one;
        } catch (Throwable error) {
            FileLog.e(error);
            return null;
        }
    }

    private static void writeCache(File file, String text) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable error) {
            FileLog.e(error);
        }
    }
}

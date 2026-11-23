package org.telegram.femboy;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.util.SparseIntArray;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.FemboyPetView;
import org.telegram.ui.FemboyStreakDrawable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серии в личке: день засчитан, когда за местные сутки написали оба. Серия это
 * непрерывный отрезок засчитанных дней, который кончается сегодня или вчера.
 *
 * Хранится отрезок (начало и конец) и флаги за последние три недели. Так серия
 * в год не требует хранить год дней. Всё меняется на своей очереди, имена в
 * списках только читают готовое.
 */
public final class FemboyStreaks implements NotificationCenter.NotificationCenterDelegate {

    /** С какой серии видно огонёк и яйцо. */
    public static final int MIN_SHOWN = 3;

    public static final int ME = 1;
    public static final int THEM = 2;
    private static final int BOTH = ME | THEM;

    /** Сколько последних дней держим с флагами. */
    private static final int KEEP_DAYS = 21;
    private static final int BACKFILL_DIALOGS = 60;
    private static final int BACKFILL_DAYS = 120;

    private static final String KEY_BACKFILL = "_bf";

    private FemboyStreaks() {}

    private static final FemboyStreaks INSTANCE = new FemboyStreaks();
    private static final DispatchQueue queue = new DispatchQueue("femboyStreaks");

    @SuppressWarnings("unchecked")
    private static final ConcurrentHashMap<Long, Entry>[] entries = new ConcurrentHashMap[UserConfig.MAX_ACCOUNT_COUNT];
    private static final boolean[] started = new boolean[UserConfig.MAX_ACCOUNT_COUNT];
    private static final int[] backfillChecked = new int[UserConfig.MAX_ACCOUNT_COUNT];
    private static final boolean[] notifyPending = new boolean[UserConfig.MAX_ACCOUNT_COUNT];

    static {
        for (int a = 0; a < entries.length; a++) entries[a] = new ConcurrentHashMap<>();
    }

    // ---------------------------------------------------------------- настройки

    public static boolean flamesEnabled() {
        return FemboyConfig.prefs().getBoolean("streak_flames", true);
    }

    public static void setFlamesEnabled(boolean on) {
        FemboyConfig.prefs().edit().putBoolean("streak_flames", on).apply();
        notifyAllAccounts();
    }

    public static boolean petEnabled() {
        return FemboyConfig.prefs().getBoolean("streak_pet", true);
    }

    public static void setPetEnabled(boolean on) {
        FemboyConfig.prefs().edit().putBoolean("streak_pet", on).apply();
    }

    // ---------------------------------------------------------------- дни

    private static volatile int cachedToday;
    private static volatile long todayEndsAt;

    /** Номер местного дня. */
    public static int today() {
        final long now = System.currentTimeMillis();
        if (now >= todayEndsAt) {
            cachedToday = dayOfMillis(now);
            final Calendar c = Calendar.getInstance();
            c.setTimeInMillis(now);
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            c.add(Calendar.DAY_OF_MONTH, 1);
            todayEndsAt = c.getTimeInMillis();
        }
        return cachedToday;
    }

    private static int dayOfMillis(long ms) {
        final long local = ms + TimeZone.getDefault().getOffset(ms);
        return (int) (local >= 0 ? local / 86400000L : (local - 86399999L) / 86400000L);
    }

    private static int dayOf(int unixSeconds) {
        return dayOfMillis(unixSeconds * 1000L);
    }

    // ---------------------------------------------------------------- запись одного диалога

    private static final class Entry {
        int runStart, runEnd;
        int broken;
        final SparseIntArray days = new SparseIntArray();
        String petName;
        int petDay;

        /** Отметить сторону за день. true, если что-то поменялось. */
        boolean mark(int day, int flag, int today) {
            if (day <= broken || day > today || day < today - KEEP_DAYS) return false;
            final int was = days.get(day);
            final int now = was | flag;
            if (now == was) return false;
            days.put(day, now);
            if (now == BOTH) counted(day);
            return true;
        }

        void counted(int day) {
            if (day <= broken) return;
            if (runEnd == 0 || day > runEnd + 1) {
                runStart = runEnd = day;
            } else if (day == runEnd + 1) {
                runEnd = day;
            } else if (day == runStart - 1) {
                runStart = day;
            }
            chain();
        }

        /** Дотянуть отрезок через уже засчитанные соседние дни. */
        void chain() {
            if (runEnd == 0) return;
            while (days.get(runStart - 1) == BOTH && runStart - 1 > broken) runStart--;
            while (days.get(runEnd + 1) == BOTH) runEnd++;
            if (runStart <= broken) runStart = broken + 1;
            if (runStart > runEnd) runStart = runEnd = 0;
        }

        void prune(int today) {
            for (int i = days.size() - 1; i >= 0; i--) {
                if (days.keyAt(i) < today - KEEP_DAYS) days.removeAt(i);
            }
        }

        int streak(int today) {
            if (runEnd == 0 || runEnd < today - 1 || runEnd > today || runStart <= broken) return 0;
            return runEnd - runStart + 1;
        }

        boolean petValid(int today) {
            return petName != null && streak(today) >= MIN_SHOWN && petDay >= runStart;
        }

        /** То, что видно у имени: смена этого числа значит перерисовать. */
        int shownCode(int today) {
            final int s = streak(today);
            if (s < MIN_SHOWN) return 0;
            return s * 2 + (runEnd < today ? 1 : 0);
        }

        JSONObject toJson() throws Exception {
            final JSONObject o = new JSONObject();
            o.put("s", runStart).put("e", runEnd);
            if (broken != 0) o.put("b", broken);
            final JSONArray d = new JSONArray();
            for (int i = 0; i < days.size(); i++) {
                d.put(days.keyAt(i)).put(days.valueAt(i));
            }
            o.put("d", d);
            if (petName != null) o.put("n", petName).put("nd", petDay);
            return o;
        }

        static Entry fromJson(JSONObject o) {
            final Entry e = new Entry();
            e.runStart = o.optInt("s");
            e.runEnd = o.optInt("e");
            e.broken = o.optInt("b");
            final JSONArray d = o.optJSONArray("d");
            if (d != null) {
                for (int i = 0; i + 1 < d.length(); i += 2) e.days.put(d.optInt(i), d.optInt(i + 1));
            }
            if (o.has("n")) {
                e.petName = o.optString("n", null);
                e.petDay = o.optInt("nd");
            }
            return e;
        }
    }

    private static SharedPreferences store(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences("femboy_streaks_" + account, Context.MODE_PRIVATE);
    }

    /** Только на своей очереди. */
    private static void save(int account, long did, Entry e) {
        try {
            final String json;
            synchronized (e) {
                json = e.toJson().toString();
            }
            store(account).edit().putString(String.valueOf(did), json).apply();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    // ---------------------------------------------------------------- запуск

    /** Поднять учёт для аккаунта. Дёшево, зовётся при каждом чтении. */
    private static void ensure(int account) {
        if (account < 0 || account >= started.length) return;
        if (!started[account]) {
            started[account] = true;
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(account)
                    .addObserver(INSTANCE, NotificationCenter.didReceiveNewMessages));
            queue.postRunnable(() -> load(account));
        }
        final int today = today();
        if (backfillChecked[account] != today) {
            backfillChecked[account] = today;
            queue.postRunnable(() -> maybeBackfill(account));
        }
    }

    private static void load(int account) {
        try {
            for (Map.Entry<String, ?> kv : store(account).getAll().entrySet()) {
                if (kv.getKey().startsWith("_") || !(kv.getValue() instanceof String)) continue;
                try {
                    final long did = Long.parseLong(kv.getKey());
                    final Entry e = Entry.fromJson(new JSONObject((String) kv.getValue()));
                    entries[account].putIfAbsent(did, e);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        notifyUi(account);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.didReceiveNewMessages || args.length < 2) return;
        if (args.length > 2 && args[2] instanceof Boolean && (Boolean) args[2]) return;
        if (args.length > 3 && args[3] instanceof Integer && (Integer) args[3] != 0) return;
        final long did = (Long) args[0];
        if (!candidate(account, did)) return;
        @SuppressWarnings("unchecked")
        final ArrayList<MessageObject> list = (ArrayList<MessageObject>) args[1];
        if (list == null || list.isEmpty()) return;
        // только дни и стороны, без самих сообщений
        final int[] days = new int[list.size()];
        final int[] flags = new int[list.size()];
        int n = 0;
        for (MessageObject m : list) {
            if (m == null || m.messageOwner == null || m.messageOwner.date == 0) continue;
            if (m.messageOwner instanceof TLRPC.TL_messageService) continue;
            days[n] = dayOf(m.messageOwner.date);
            flags[n] = m.isOut() ? ME : THEM;
            n++;
        }
        if (n == 0) return;
        final int count = n;
        queue.postRunnable(() -> {
            final int today = today();
            Entry e = entries[account].get(did);
            if (e == null) {
                e = new Entry();
                entries[account].put(did, e);
            }
            boolean changed = false;
            synchronized (e) {
                for (int i = 0; i < count; i++) changed |= e.mark(days[i], flags[i], today);
                if (changed) e.prune(today);
            }
            if (changed) {
                save(account, did, e);
                notifyUi(account);
            }
        });
    }

    // ---------------------------------------------------------------- догрузка из базы

    /** Раз в сутки: собрать дни из своей базы для свежих личных чатов. */
    private static void maybeBackfill(int account) {
        final int today = today();
        if (store(account).getInt(KEY_BACKFILL, 0) == today) return;
        final MessagesStorage storage = MessagesStorage.getInstance(account);
        final int offset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000;
        // живая серия бывает только у чата, где писали вчера или сегодня
        final long yesterdayStart = (long) (today - 1) * 86400L - offset;
        final long from = (long) (today - BACKFILL_DAYS) * 86400L - offset;
        storage.getStorageQueue().postRunnable(() -> {
            final ArrayList<Long> dids = new ArrayList<>();
            final ArrayList<SparseIntArray> found = new ArrayList<>();
            SQLiteCursor cursor = null;
            try {
                cursor = storage.getDatabase().queryFinalized("SELECT did FROM dialogs WHERE did > 0 AND date >= " + yesterdayStart + " ORDER BY date DESC LIMIT " + (BACKFILL_DIALOGS * 2));
                while (cursor.next() && dids.size() < BACKFILL_DIALOGS) {
                    final long did = cursor.longValue(0);
                    if (candidate(account, did)) dids.add(did);
                }
                cursor.dispose();
                cursor = null;
                for (long did : dids) {
                    final SparseIntArray days = new SparseIntArray();
                    // по суткам сразу в базе: строк не больше числа дней
                    cursor = storage.getDatabase().queryFinalized("SELECT (date + " + offset + ") / 86400 AS d, MAX(out), MIN(out) FROM messages_v2 WHERE uid = " + did + " AND date >= " + from + " GROUP BY d");
                    while (cursor.next()) {
                        int flag = 0;
                        if (cursor.intValue(1) != 0) flag |= ME;
                        if (cursor.intValue(2) == 0) flag |= THEM;
                        days.put(cursor.intValue(0), flag);
                    }
                    cursor.dispose();
                    cursor = null;
                    found.add(days);
                }
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                if (cursor != null) cursor.dispose();
            }
            queue.postRunnable(() -> {
                for (int i = 0; i < found.size(); i++) merge(account, dids.get(i), found.get(i), today);
                store(account).edit().putInt(KEY_BACKFILL, today).apply();
                notifyUi(account);
            });
        });
    }

    private static void merge(int account, long did, SparseIntArray days, int today) {
        Entry e = entries[account].get(did);
        if (e == null) {
            e = new Entry();
            entries[account].put(did, e);
        }
        synchronized (e) {
            // отрезок по базе: от последнего засчитанного дня назад
            int end = 0;
            for (int i = days.size() - 1; i >= 0; i--) {
                final int d = days.keyAt(i);
                if (d <= today && d > e.broken && days.valueAt(i) == BOTH) {
                    end = d;
                    break;
                }
            }
            if (end != 0) {
                int start = end;
                while (start - 1 > e.broken && days.get(start - 1) == BOTH) start--;
                if (e.runEnd == 0 || start > e.runEnd + 1) {
                    e.runStart = start;
                    e.runEnd = end;
                } else if (end >= e.runStart - 1) {
                    // пересекаются или стоят встык: склеить
                    e.runStart = Math.min(e.runStart, start);
                    e.runEnd = Math.max(e.runEnd, end);
                }
            }
            for (int i = 0; i < days.size(); i++) {
                final int d = days.keyAt(i);
                if (d > e.broken && d <= today && d >= today - KEEP_DAYS) {
                    e.days.put(d, e.days.get(d) | days.valueAt(i));
                }
            }
            e.chain();
            e.prune(today);
        }
        save(account, did, e);
    }

    // ---------------------------------------------------------------- кто считается

    /** Личка с живым человеком: не бот, не я, не служебный. */
    private static boolean candidate(int account, long did) {
        if (did <= 0 || !DialogObject.isUserDialog(did)) return false;
        if (did == UserConfig.getInstance(account).getClientUserId()) return false;
        if (UserObject.isService(did) || did == UserObject.VERIFY || did == UserObject.REPLY_BOT) return false;
        final TLRPC.User user = MessagesController.getInstance(account).getUser(did);
        return user == null || !(user.bot || user.self || MessagesController.isSupportUser(user) || UserObject.isDeleted(user));
    }

    public static boolean eligible(int account, long did) {
        return candidate(account, did) && MessagesController.getInstance(account).getUser(did) != null;
    }

    // ---------------------------------------------------------------- чтение

    /** Снимок для экрана питомца. */
    public static final class State {
        public int streak;
        public boolean waiting;
        public int todayFlags;
        public String petName;
    }

    public static State state(int account, long did) {
        final State s = new State();
        ensure(account);
        if (account < 0 || account >= entries.length) return s;
        final Entry e = entries[account].get(did);
        if (e == null) return s;
        final int today = today();
        synchronized (e) {
            s.streak = e.streak(today);
            s.waiting = s.streak > 0 && e.runEnd < today;
            s.todayFlags = today > e.broken ? e.days.get(today) : 0;
            s.petName = e.petValid(today) ? e.petName : null;
        }
        return s;
    }

    /**
     * Местная плашка в переписке: 2 = второй день подряд и сегодня уже
     * засчитан, 3 = в этот день появилось яйцо. 0 = ничего. Только из памяти.
     */
    public static int notice(int account, long did) {
        if (did <= 0 || account < 0 || account >= entries.length || !flamesEnabled()) return 0;
        ensure(account);
        final Entry e = entries[account].get(did);
        if (e == null) return 0;
        final int today = today();
        final int s;
        synchronized (e) {
            if (e.runEnd != today) return 0;
            s = e.streak(today);
        }
        return s == 2 || s == MIN_SHOWN ? s : 0;
    }

    /** Код огонька у имени: 0 — не рисовать, иначе серия * 2 + ждёт. Только из памяти. */
    private static int shownCode(int account, long did) {
        if (did <= 0 || account < 0 || account >= entries.length) return 0;
        ensure(account);
        if (!flamesEnabled()) return 0;
        final Entry e = entries[account].get(did);
        if (e == null) return 0;
        final int code;
        synchronized (e) {
            code = e.shownCode(today());
        }
        if (code == 0 || !eligible(account, did)) return 0;
        return code;
    }

    /** Ширина огонька с числом у имени, 0 — огонька нет. */
    public static float nameWidth(Paint paint, int account, long did) {
        final int code = shownCode(account, did);
        return code == 0 ? 0 : FemboyStreakDrawable.spanWidth(paint, code / 2);
    }

    /** Имя с огоньком и серией в конце. Без серии возвращает имя как есть. */
    public static CharSequence withStreak(View parent, CharSequence name, int account, long did) {
        if (name == null) return null;
        final int code = shownCode(account, did);
        if (code == 0) return name;
        final android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(name);
        final int at = sb.length();
        sb.append("⁠");
        sb.setSpan(new FemboyStreakDrawable.Span(parent, code / 2, (code & 1) != 0), at, at + 1,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    // ---------------------------------------------------------------- питомец

    /** Стадия по серии или -1, если яйца ещё нет. */
    public static int stage(int streak) {
        if (streak >= 30) return FemboyPetView.STAGE_ADULT;
        if (streak >= 10) return FemboyPetView.STAGE_BABY;
        if (streak >= 7) return FemboyPetView.STAGE_HATCHING;
        if (streak >= MIN_SHOWN) return FemboyPetView.STAGE_EGG;
        return -1;
    }

    /** С какого дня следующая стадия, 0 — дальше расти некуда. */
    public static int nextStageAt(int streak) {
        if (streak < MIN_SHOWN) return MIN_SHOWN;
        if (streak < 7) return 7;
        if (streak < 10) return 10;
        if (streak < 30) return 30;
        return 0;
    }

    /** С какого дня началась текущая стадия. */
    public static int stageFrom(int streak) {
        if (streak >= 30) return 30;
        if (streak >= 10) return 10;
        if (streak >= 7) return 7;
        if (streak >= MIN_SHOWN) return MIN_SHOWN;
        return 0;
    }

    public static void setPetName(int account, long did, String name, Runnable done) {
        queue.postRunnable(() -> {
            final Entry e = entries[account].get(did);
            if (e != null) {
                synchronized (e) {
                    e.petName = name;
                    e.petDay = today();
                }
                save(account, did, e);
            }
            if (done != null) AndroidUtilities.runOnUIThread(done);
        });
    }

    /** Оборвать серию руками: счёт с нуля, питомец уходит, новая начнётся завтра. */
    public static void breakStreak(int account, long did, Runnable done) {
        queue.postRunnable(() -> {
            Entry e = entries[account].get(did);
            if (e == null) {
                e = new Entry();
                entries[account].put(did, e);
            }
            synchronized (e) {
                e.broken = today();
                e.runStart = e.runEnd = 0;
                e.petName = null;
                e.petDay = 0;
                e.days.clear();
            }
            save(account, did, e);
            notifyUi(account);
            if (done != null) AndroidUtilities.runOnUIThread(done);
        });
    }

    // ---------------------------------------------------------------- перерисовка

    private static void notifyUi(int account) {
        AndroidUtilities.runOnUIThread(() -> {
            if (notifyPending[account]) return;
            notifyPending[account] = true;
            AndroidUtilities.runOnUIThread(() -> {
                notifyPending[account] = false;
                NotificationCenter.getInstance(account).postNotificationName(
                        NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
            }, 300);
        });
    }

    private static void notifyAllAccounts() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) notifyUi(a);
        }
    }
}

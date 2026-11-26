package com.oneuibook.checker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Subscriptions & fixed expenses: model, recurrence math, currency conversion and persistence. */
final class Store {
    private Store() {}

    static final int MONTH = 0, YEAR = 1, WEEK = 2, DAYS = 3;
    static final String[] CURRENCIES = {"KRW", "USD", "JPY", "EUR"};
    static final String[] CATEGORIES = {"엔터테인먼트", "음악", "AI·생산성", "클라우드", "쇼핑·멤버십", "고정지출", "기타"};
    static final int[] CATEGORY_COLORS = {0xFFE5484D, 0xFF1DB954, 0xFF7B61FF, 0xFF3B8BF5, 0xFFF59E0B, 0xFF64748B, 0xFF14B8A6};
    static final int[] SWATCHES = {0xFF19B36B, 0xFF1DB954, 0xFF3B8BF5, 0xFF7B61FF, 0xFFE5484D, 0xFFFF6B3D,
            0xFFF59E0B, 0xFFE84593, 0xFF14B8A6, 0xFF64748B, 0xFF111827, 0xFF0EA5E9};

    static final class Sub {
        String id = UUID.randomUUID().toString();
        String name = "", category = CATEGORIES[0], currency = "KRW", memo = "", payment = "";
        double amount;
        int cycle = MONTH, every = 1;
        long anchor = LocalDate.now().toEpochDay();
        long endDate = -1;
        int color = SWATCHES[0];
        int remind = -1;
        boolean paused, trial;

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("name", name); o.put("category", category); o.put("currency", currency);
            o.put("memo", memo); o.put("payment", payment); o.put("amount", amount); o.put("cycle", cycle);
            o.put("every", every); o.put("anchor", anchor); o.put("end", endDate); o.put("color", color);
            o.put("remind", remind); o.put("paused", paused); o.put("trial", trial);
            return o;
        }

        static Sub from(JSONObject o) {
            Sub s = new Sub();
            s.id = o.optString("id", s.id);
            s.name = o.optString("name", "");
            s.category = o.optString("category", CATEGORIES[0]);
            s.currency = o.optString("currency", "KRW");
            s.memo = o.optString("memo", "");
            s.payment = o.optString("payment", "");
            s.amount = o.optDouble("amount", 0);
            s.cycle = o.optInt("cycle", MONTH);
            s.every = Math.max(1, o.optInt("every", 1));
            s.anchor = o.optLong("anchor", s.anchor);
            s.endDate = o.optLong("end", -1);
            s.color = o.optInt("color", SWATCHES[0]);
            s.remind = o.optInt("remind", -1);
            s.paused = o.optBoolean("paused", false);
            s.trial = o.optBoolean("trial", false);
            return s;
        }

        String initial() {
            String n = name.trim();
            return n.isEmpty() ? "?" : n.substring(0, n.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
        }
    }

    /** One concrete billing event of a subscription. */
    static final class Due {
        final Sub sub;
        final LocalDate date;
        final long index;
        Due(Sub s, LocalDate d, long k) { sub = s; date = d; index = k; }
        String key() { return sub.id + "@" + date.toEpochDay(); }
        boolean paid() { return Store.paid.contains(key()); }
        boolean trialEnd() { return sub.trial && index == 0; }
    }

    static final List<Sub> subs = new ArrayList<>();
    static final Set<String> paid = new HashSet<>();
    static double usd = 1400, jpy = 9.4, eur = 1500;
    static long rateUpdatedAt = 0;
    static String rateSource = "기본값";
    static boolean autoRates = true;
    static long updatedAt = 0;
    static String syncUri = "";
    static long lastSyncAt = 0;
    static boolean autoSync = true;
    static int defaultRemind = 1, remindHour = 9, sortMode;
    static boolean notify = true, showPaused = true;
    static LocalDate today = null; // test override
    private static volatile boolean fetchingRates;

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("checker", Context.MODE_PRIVATE);
    }

    static LocalDate today() { return today != null ? today : LocalDate.now(); }

    static void load(Context c) {
        SharedPreferences p = prefs(c);
        subs.clear();
        paid.clear();
        try {
            JSONArray a = new JSONArray(p.getString("subs", "[]"));
            for (int i = 0; i < a.length(); i++) subs.add(Sub.from(a.getJSONObject(i)));
            JSONArray pd = new JSONArray(p.getString("paid", "[]"));
            for (int i = 0; i < pd.length(); i++) paid.add(pd.getString(i));
        } catch (Exception ignored) {}
        usd = p.getFloat("rate_usd", 1400f);
        jpy = p.getFloat("rate_jpy", 9.4f);
        eur = p.getFloat("rate_eur", 1500f);
        rateUpdatedAt = p.getLong("rate_updated_at", 0L);
        rateSource = p.getString("rate_source", "기본값");
        autoRates = p.getBoolean("auto_rates", true);
        updatedAt = p.getLong("updated_at", 0L);
        syncUri = p.getString("sync_uri", "");
        lastSyncAt = p.getLong("last_sync_at", 0L);
        autoSync = p.getBoolean("auto_sync", true);
        defaultRemind = p.getInt("remind_days", 1);
        remindHour = p.getInt("remind_hour", 9);
        notify = p.getBoolean("notify", true);
        showPaused = p.getBoolean("show_paused", true);
        sortMode = p.getInt("sort_mode", 0);
    }

    static void save(Context c) {
        updatedAt = System.currentTimeMillis();
        persist(c);
        Reminder.schedule(c);
        CheckerWidget.updateAll(c);
    }

    static void persist(Context c) {
        try {
            JSONArray a = new JSONArray();
            for (Sub s : subs) a.put(s.toJson());
            // keep only recent paid marks (one year) so the set never grows unbounded
            long cutoff = today().minusDays(400).toEpochDay();
            JSONArray pd = new JSONArray();
            for (String k : paid) {
                int at = k.lastIndexOf('@');
                long d = at > 0 ? Long.parseLong(k.substring(at + 1)) : 0;
                if (d >= cutoff) pd.put(k);
            }
            prefs(c).edit().putString("subs", a.toString()).putString("paid", pd.toString())
                    .putFloat("rate_usd", (float) usd).putFloat("rate_jpy", (float) jpy).putFloat("rate_eur", (float) eur)
                    .putLong("rate_updated_at", rateUpdatedAt).putString("rate_source", rateSource).putBoolean("auto_rates", autoRates)
                    .putLong("updated_at", updatedAt).putString("sync_uri", syncUri).putLong("last_sync_at", lastSyncAt).putBoolean("auto_sync", autoSync)
                    .putInt("remind_days", defaultRemind).putInt("remind_hour", remindHour)
                    .putBoolean("notify", notify).putBoolean("show_paused", showPaused).putInt("sort_mode", sortMode).apply();
        } catch (Exception ignored) {}
    }

    /** Parses KRW-base FX JSON from open.er-api.com or frankfurter.app into {usdToKrw, jpyToKrw, eurToKrw}. */
    static double[] parseRatesJson(String json) {
        try {
            JSONObject o = new JSONObject(json);
            JSONObject r = o.optJSONObject("rates");
            if (r == null) return null;
            double ru = r.optDouble("USD", 0), rj = r.optDouble("JPY", 0), re = r.optDouble("EUR", 0);
            if (ru <= 0 || rj <= 0 || re <= 0) return null;
            double u = Math.round((1.0 / ru) * 100.0) / 100.0;
            double j = Math.round((1.0 / rj) * 100.0) / 100.0;
            double e = Math.round((1.0 / re) * 100.0) / 100.0;
            if (u < 500 || u > 5000 || j < 2 || j > 50 || e < 500 || e > 5000) return null;
            return new double[]{u, j, e};
        } catch (Exception ignored) { return null; }
    }

    static String rateUpdatedLabel() {
        if (rateUpdatedAt <= 0) return rateSource;
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", Locale.KOREA);
        return rateSource + " · " + sdf.format(new java.util.Date(rateUpdatedAt));
    }

    static String syncStatusLabel() {
        if (syncUri == null || syncUri.isEmpty()) return "연결 안 됨";
        if (lastSyncAt <= 0) return "연결됨";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM/dd HH:mm", Locale.KOREA);
        return "동기화됨 · " + sdf.format(new java.util.Date(lastSyncAt));
    }

    /** Fetches real-time USD/JPY/EUR → KRW exchange rates asynchronously. */
    static void fetchLiveRates(Context c, boolean force, Runnable onUpdated, Runnable onFailed) {
        if (!force && (!autoRates || (System.currentTimeMillis() - rateUpdatedAt < 15 * 60 * 1000L && rateUpdatedAt > 0))) return;
        if (fetchingRates) return;
        fetchingRates = true;
        final Context app = c.getApplicationContext();
        final android.os.Handler mainH = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
            double[] parsed = null;
            String[] urls = {
                    "https://open.er-api.com/v6/latest/KRW",
                    "https://api.frankfurter.app/latest?from=KRW&to=USD,JPY,EUR"
            };
            for (String u : urls) {
                java.net.HttpURLConnection conn = null;
                try {
                    conn = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
                    conn.setConnectTimeout(4500);
                    conn.setReadTimeout(4500);
                    conn.setRequestMethod("GET");
                    if (conn.getResponseCode() == 200) {
                        java.io.BufferedReader br = new java.io.BufferedReader(
                                new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();
                        parsed = parseRatesJson(sb.toString());
                        if (parsed != null) break;
                    }
                } catch (Exception ignored) {
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
            fetchingRates = false;
            final double[] res = parsed;
            mainH.post(() -> {
                if (res != null) {
                    usd = res[0]; jpy = res[1]; eur = res[2];
                    rateUpdatedAt = System.currentTimeMillis();
                    rateSource = "실시간";
                    persist(app);
                    CheckerWidget.updateAll(app);
                    if (onUpdated != null) onUpdated.run();
                } else if (onFailed != null) {
                    onFailed.run();
                }
            });
        }, "checker-fx").start();
    }

    static String exportJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("app", "Checker");
        o.put("version", MainActivity.VERSION);
        o.put("updated_at", updatedAt > 0 ? updatedAt : System.currentTimeMillis());
        JSONArray a = new JSONArray();
        for (Sub s : subs) a.put(s.toJson());
        o.put("subs", a);
        JSONArray pd = new JSONArray();
        for (String k : paid) pd.put(k);
        o.put("paid", pd);
        o.put("rates", new JSONObject().put("USD", usd).put("JPY", jpy).put("EUR", eur));
        return o.toString(2);
    }

    /** Merges subscriptions & paid marks from an exported/synced file. Returns the number of imported entries. */
    static int importJson(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        JSONArray a = o.getJSONArray("subs");
        int n = 0;
        for (int i = 0; i < a.length(); i++) {
            Sub s = Sub.from(a.getJSONObject(i));
            Sub old = find(s.id);
            if (old != null) subs.set(subs.indexOf(old), s); else subs.add(s);
            n++;
        }
        JSONArray pd = o.optJSONArray("paid");
        if (pd != null) {
            for (int i = 0; i < pd.length(); i++) {
                String k = pd.optString(i, "");
                if (!k.isEmpty()) paid.add(k);
            }
        }
        JSONObject r = o.optJSONObject("rates");
        if (r != null && rateUpdatedAt == 0) {
            usd = r.optDouble("USD", usd); jpy = r.optDouble("JPY", jpy); eur = r.optDouble("EUR", eur);
        }
        return n;
    }

    /** Bidirectional timestamp-aware merge for real-time sync files. Returns true if local state changed. */
    static boolean mergeSyncJson(String remoteJson) throws Exception {
        if (remoteJson == null || remoteJson.trim().isEmpty()) return false;
        JSONObject o = new JSONObject(remoteJson);
        long remoteTs = o.optLong("updated_at", 0L);
        boolean changed = false;
        JSONArray a = o.optJSONArray("subs");
        if (a != null) {
            if (remoteTs > updatedAt) {
                subs.clear();
                for (int i = 0; i < a.length(); i++) subs.add(Sub.from(a.getJSONObject(i)));
                updatedAt = remoteTs;
                changed = true;
            } else {
                for (int i = 0; i < a.length(); i++) {
                    Sub s = Sub.from(a.getJSONObject(i));
                    if (find(s.id) == null) { subs.add(s); changed = true; }
                }
            }
        }
        JSONArray pd = o.optJSONArray("paid");
        if (pd != null) {
            for (int i = 0; i < pd.length(); i++) {
                String k = pd.optString(i, "");
                if (!k.isEmpty() && paid.add(k)) changed = true;
            }
        }
        return changed;
    }

    static Sub find(String id) {
        for (Sub s : subs) if (s.id.equals(id)) return s;
        return null;
    }

    // ---- Recurrence ----
    static LocalDate occurrence(Sub s, long k) {
        LocalDate a = LocalDate.ofEpochDay(s.anchor);
        long n = k * Math.max(1, s.every);
        switch (s.cycle) {
            case YEAR: return a.plusYears(n);
            case WEEK: return a.plusWeeks(n);
            case DAYS: return a.plusDays(n);
            default: return a.plusMonths(n);
        }
    }

    /** First occurrence index whose date is on or after {@code from}. */
    static long indexOnOrAfter(Sub s, LocalDate from) {
        LocalDate a = LocalDate.ofEpochDay(s.anchor);
        if (!from.isAfter(a)) return 0;
        long units;
        switch (s.cycle) {
            case YEAR: units = ChronoUnit.YEARS.between(a, from); break;
            case WEEK: units = ChronoUnit.WEEKS.between(a, from); break;
            case DAYS: units = ChronoUnit.DAYS.between(a, from); break;
            default: units = ChronoUnit.MONTHS.between(a, from);
        }
        long k = Math.max(0, units / Math.max(1, s.every) - 1);
        while (occurrence(s, k).isBefore(from)) k++;
        return k;
    }

    static boolean ended(Sub s, LocalDate d) { return s.endDate >= 0 && d.toEpochDay() > s.endDate; }

    static Due nextDue(Sub s, LocalDate from) {
        long k = indexOnOrAfter(s, from);
        LocalDate d = occurrence(s, k);
        return ended(s, d) ? null : new Due(s, d, k);
    }

    /** All billing events (excluding paused) in the closed range, sorted by date then amount. */
    static List<Due> dues(LocalDate from, LocalDate to) {
        List<Due> out = new ArrayList<>();
        for (Sub s : subs) {
            if (s.paused) continue;
            long k = indexOnOrAfter(s, from);
            for (int guard = 0; guard < 400; guard++, k++) {
                LocalDate d = occurrence(s, k);
                if (d.isAfter(to) || ended(s, d)) break;
                out.add(new Due(s, d, k));
            }
        }
        Collections.sort(out, (x, y) -> {
            int c = x.date.compareTo(y.date);
            return c != 0 ? c : Double.compare(krw(y.sub), krw(x.sub));
        });
        return out;
    }

    // ---- Money ----
    static double rate(String cur) {
        switch (cur) {
            case "USD": return usd;
            case "JPY": return jpy;
            case "EUR": return eur;
            default: return 1;
        }
    }

    static double krw(Sub s) { return s.amount * rate(s.currency); }

    /** Normalized monthly cost in KRW. */
    static double monthly(Sub s) {
        double v = krw(s) / Math.max(1, s.every);
        switch (s.cycle) {
            case YEAR: return v / 12.0;
            case WEEK: return v * 52.0 / 12.0;
            case DAYS: return v * 365.25 / 12.0;
            default: return v;
        }
    }

    static double monthlyTotal() {
        double t = 0;
        for (Sub s : subs) if (!s.paused) t += monthly(s);
        return t;
    }

    static int activeCount() {
        int n = 0;
        for (Sub s : subs) if (!s.paused) n++;
        return n;
    }

    /** Actual charges in a calendar month (KRW), optionally only the already-checked ones. */
    static double monthCharges(int year, int month, boolean onlyPaid) {
        LocalDate a = LocalDate.of(year, month, 1);
        double t = 0;
        for (Due d : dues(a, a.plusMonths(1).minusDays(1))) if (!onlyPaid || d.paid()) t += krw(d.sub);
        return t;
    }

    static Map<String, Double> byCategory() {
        Map<String, Double> m = new LinkedHashMap<>();
        for (String c : CATEGORIES) m.put(c, 0.0);
        for (Sub s : subs) {
            if (s.paused) continue;
            String c = m.containsKey(s.category) ? s.category : "기타";
            m.put(c, m.get(c) + monthly(s));
        }
        return m;
    }

    static int categoryColor(String c) {
        for (int i = 0; i < CATEGORIES.length; i++) if (CATEGORIES[i].equals(c)) return CATEGORY_COLORS[i];
        return CATEGORY_COLORS[CATEGORY_COLORS.length - 1];
    }

    static String won(double v) {
        return String.format(Locale.KOREA, "%,d원", Math.round(v));
    }

    static String amount(Sub s) {
        switch (s.currency) {
            case "USD": return String.format(Locale.US, "$%,.2f", s.amount);
            case "EUR": return String.format(Locale.GERMANY, "€%,.2f", s.amount);
            case "JPY": return String.format(Locale.JAPAN, "¥%,d", Math.round(s.amount));
            default: return won(s.amount);
        }
    }

    static String cycleLabel(Sub s) {
        String unit;
        switch (s.cycle) {
            case YEAR: unit = s.every == 1 ? "매년" : s.every + "년마다"; break;
            case WEEK: unit = s.every == 1 ? "매주" : s.every + "주마다"; break;
            case DAYS: unit = s.every + "일마다"; break;
            default: unit = s.every == 1 ? "매월" : s.every + "개월마다";
        }
        return unit;
    }

    static String relDay(LocalDate d) {
        long n = ChronoUnit.DAYS.between(today(), d);
        if (n == 0) return "오늘";
        if (n == 1) return "내일";
        if (n == 2) return "모레";
        if (n < 0) return (-n) + "일 지남";
        return n + "일 후";
    }

    static String dateLabel(LocalDate d) {
        String[] w = {"월", "화", "수", "목", "금", "토", "일"};
        return d.getMonthValue() + "월 " + d.getDayOfMonth() + "일 (" + w[d.getDayOfWeek().getValue() - 1] + ")";
    }

    static int remindDays(Sub s) { return s.remind < 0 ? defaultRemind : s.remind; }

    // ---- Presets with authentic regional pricing across KRW / USD / JPY / EUR ----
    static final class Preset {
        final String name, currency, category;
        final double amount, krw, usd, jpy, eur;
        final int cycle, color;
        Preset(String n, double krw, double usd, double jpy, double eur, String defCur, int cy, String cat, int col) {
            name = n; this.krw = krw; this.usd = usd; this.jpy = jpy; this.eur = eur;
            currency = defCur; amount = priceFor(defCur); cycle = cy; category = cat; color = col;
        }
        double priceFor(String cur) {
            switch (cur) {
                case "USD": return usd;
                case "JPY": return jpy;
                case "EUR": return eur;
                default: return krw;
            }
        }
        Sub make() { return make(currency); }
        Sub make(String cur) {
            Sub s = new Sub();
            s.name = name; s.currency = cur; s.amount = priceFor(cur); s.cycle = cycle; s.category = category; s.color = color;
            return s;
        }
    }

    static final Preset[] PRESETS = {
            new Preset("Netflix", 13500, 15.49, 1590, 13.99, "KRW", MONTH, "엔터테인먼트", 0xFFE50914),
            new Preset("YouTube Premium", 14900, 13.99, 1280, 12.99, "KRW", MONTH, "엔터테인먼트", 0xFFFF2D2D),
            new Preset("Spotify", 11990, 11.99, 980, 10.99, "KRW", MONTH, "음악", 0xFF1DB954),
            new Preset("ChatGPT Plus", 29000, 20.0, 3000, 20.0, "USD", MONTH, "AI·생산성", 0xFF10A37F),
            new Preset("Claude Pro", 29000, 20.0, 3000, 18.0, "USD", MONTH, "AI·생산성", 0xFFD97757),
            new Preset("NovelAI", 35000, 25.0, 3800, 23.0, "USD", MONTH, "AI·생산성", 0xFF3D4A9E),
            new Preset("Disney+", 9900, 13.99, 1140, 11.99, "KRW", MONTH, "엔터테인먼트", 0xFF113CCF),
            new Preset("쿠팡 와우", 7890, 5.99, 890, 5.49, "KRW", MONTH, "쇼핑·멤버십", 0xFFE52528),
            new Preset("네이버플러스", 4900, 3.99, 550, 3.49, "KRW", MONTH, "쇼핑·멤버십", 0xFF03C75A),
            new Preset("iCloud+", 1100, 0.99, 130, 0.99, "KRW", MONTH, "클라우드", 0xFF3693F3),
            new Preset("Google One", 2400, 1.99, 250, 1.99, "KRW", MONTH, "클라우드", 0xFF4285F4),
            new Preset("Microsoft 365", 119000, 69.99, 14900, 69.0, "KRW", YEAR, "AI·생산성", 0xFFD83B01),
            new Preset("휴대폰 요금", 55000, 40.0, 5500, 35.0, "KRW", MONTH, "고정지출", 0xFF64748B),
            new Preset("월세", 500000, 1200.0, 80000, 900.0, "KRW", MONTH, "고정지출", 0xFF7B61FF),
            new Preset("보험료", 80000, 60.0, 8500, 55.0, "KRW", MONTH, "고정지출", 0xFF0EA5E9),
            new Preset("헬스장", 60000, 45.0, 7000, 40.0, "KRW", MONTH, "기타", 0xFFFF6B3D),
            new Preset("Apple One", 14900, 19.95, 1200, 19.95, "KRW", MONTH, "엔터테인먼트", 0xFF1C1C1E),
            new Preset("Nintendo Online", 4900, 3.99, 306, 3.99, "KRW", MONTH, "엔터테인먼트", 0xFFE60012),
            new Preset("Adobe CC", 66000, 59.99, 7780, 67.49, "USD", MONTH, "AI·생산성", 0xFFFF0000),
    };
}

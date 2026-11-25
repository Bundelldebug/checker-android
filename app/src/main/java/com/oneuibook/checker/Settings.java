package com.oneuibook.checker;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Checker settings: notifications, exchange rates, appearance (A/G glass), backup and info. */
final class Settings {
    private Settings() {}

    static final class SettingsPage extends Page {
        SettingsPage(MainActivity m) {
            super(m, m.getString(R.string.tab_settings), true);
            setGrouped(true);
            headerIcons = new int[0];
        }

        @Override void build() {
            Context c = getContext();
            SharedPreferences p = MainActivity.prefs(c);

            // 1. Notifications
            Widgets.Group gNotif = new Widgets.Group(c, "결제 알림");
            LinearLayout toggleRow = new LinearLayout(c);
            toggleRow.setGravity(Gravity.CENTER_VERTICAL);
            toggleRow.setPadding(Ui.dp(18), Ui.dp(14), Ui.dp(18), Ui.dp(14));
            toggleRow.addView(Ui.text(c, "결제일 알림 사용", 16, Ui.medium, Ui.text()),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Widgets.Toggle nt = new Widgets.Toggle(c, Store.notify);
            nt.change = on -> {
                Store.notify = on;
                if (on && !main.notificationsAllowed()) main.requestNotificationPermission();
                main.dataChanged();
            };
            toggleRow.addView(nt);
            gNotif.addCustom(toggleRow);

            String[] dayLabels = {"결제 당일", "1일 전", "2일 전", "3일 전", "7일 전"};
            int[] dayVals = {0, 1, 2, 3, 7};
            int curDayIdx = 1;
            for (int i = 0; i < dayVals.length; i++) if (dayVals[i] == Store.defaultRemind) curDayIdx = i;
            final int fDayIdx = curDayIdx;
            gNotif.row(Icons.BELL, Ui.ACCENT, "기본 알림 시점", dayLabels[fDayIdx], true, v ->
                    Widgets.pickSheet(main, v, "기본 알림 시점", dayLabels, fDayIdx, idx -> {
                        Store.defaultRemind = dayVals[idx];
                        main.dataChanged();
                    }));

            String[] hourLabels = {"오전 8시", "오전 9시", "오전 10시", "오후 12시", "오후 6시", "오후 9시"};
            int[] hourVals = {8, 9, 10, 12, 18, 21};
            int curHIdx = 1;
            for (int i = 0; i < hourVals.length; i++) if (hourVals[i] == Store.remindHour) curHIdx = i;
            final int fHIdx = curHIdx;
            gNotif.row(Icons.CLOCK, 0xFF3B8BF5, "알림 시간", hourLabels[fHIdx], true, v ->
                    Widgets.pickSheet(main, v, "알림 시간", hourLabels, fHIdx, idx -> {
                        Store.remindHour = hourVals[idx];
                        main.dataChanged();
                    }));

            gNotif.row(Icons.CHECK, 0xFF19B36B, "지금 알림 점검 실행", main.notificationsAllowed() ? "허용됨" : "권한 필요", false, v -> {
                if (!main.notificationsAllowed()) { main.requestNotificationPermission(); return; }
                int posted = Reminder.notifyDue(c);
                Toast.makeText(c, posted > 0 ? posted + "건의 알림을 보냈어요" : "오늘 보낼 결제 알림이 없어요", Toast.LENGTH_SHORT).show();
            });
            content.addView(gNotif, Widgets.MW);

            // 2. Exchange rates
            Widgets.Group gRate = new Widgets.Group(c, "외화 환산 기준 (원화)");
            gRate.row(Icons.GLOBE, 0xFF7B61FF, "환율 설정",
                    String.format("USD %.0f · JPY %.1f · EUR %.0f", Store.usd, Store.jpy, Store.eur), true, v -> showRatesSheet());
            content.addView(gRate, Widgets.MW);

            // 3. Appearance (matching Play / Prism)
            Widgets.Group gApp = new Widgets.Group(c, "화면 및 글래스");
            String[] themes = {"시스템 설정", "라이트", "다크"};
            String[] themeKeys = {"system", "light", "dark"};
            String curTheme = p.getString("theme", "system");
            int tIdx = curTheme.equals("light") ? 1 : (curTheme.equals("dark") ? 2 : 0);
            gApp.row(Icons.APPEARANCE, 0xFF3B8BF5, "테마", themes[tIdx], true, v ->
                    Widgets.pickSheet(main, v, "테마", themes, tIdx, idx -> {
                        p.edit().putString("theme", themeKeys[idx]).apply();
                        Ui.init(main, p);
                        main.refreshAppearance();
                    }));

            String[] styles = {"글래스 (A)", "블러 (G)"};
            int stIdx = Ui.gStyle ? 1 : 0;
            gApp.row(Icons.INFO, 0xFF14B8A6, "글래스 스타일", styles[stIdx], true, v ->
                    Widgets.pickSheet(main, v, "글래스 스타일", styles, stIdx, idx -> {
                        p.edit().putString("ui_style", idx == 1 ? "oneui" : "prism").apply();
                        Ui.init(main, p);
                        main.refreshAppearance();
                    }));

            String[] accLabels = new String[Ui.ACCENT_LABELS.length];
            int aIdx = 0;
            String ak = Ui.accentKey(p);
            for (int i = 0; i < Ui.ACCENT_LABELS.length; i++) {
                accLabels[i] = c.getString(Ui.ACCENT_LABELS[i]);
                if (Ui.ACCENT_KEYS[i].equals(ak)) aIdx = i;
            }
            final int faIdx = aIdx;
            gApp.row(Icons.TAG, Ui.ACCENT, "강조 색상", accLabels[faIdx], true, v ->
                    Widgets.pickSheet(main, v, "강조 색상", accLabels, faIdx, idx -> {
                        p.edit().putString("accent_color", Ui.ACCENT_KEYS[idx]).apply();
                        Ui.init(main, p);
                        main.refreshAppearance();
                    }));

            LinearLayout slBox = new LinearLayout(c);
            slBox.setOrientation(LinearLayout.VERTICAL);
            slBox.setPadding(Ui.dp(18), Ui.dp(12), Ui.dp(18), Ui.dp(12));
            slBox.addView(Ui.text(c, "글래스 투명도", 14.5f, Ui.medium, Ui.text()));
            slBox.addView(new Widgets.GlassSlider(c, Ui.glassT, val -> {
                Ui.glassT = val;
                p.edit().putFloat("glass_t", val).apply();
                main.header.invalidate();
                main.dock.invalidate();
            }));
            gApp.addCustom(slBox);
            content.addView(gApp, Widgets.MW);

            // 4. Data & Backup
            Widgets.Group gData = new Widgets.Group(c, "데이터");
            gData.row(Icons.SHARE, 0xFF19B36B, "JSON 백업 내보내기", Store.subs.size() + "개", true, v -> main.exportData());
            gData.row(Icons.DOWNLOAD, 0xFF3B8BF5, "JSON 백업 가져오기", null, true, v -> main.importData());
            gData.row(Icons.PLUS, 0xFFF59E0B, "샘플 구독 5개 추가", "체험용", false, v -> {
                seedSamples();
                main.dataChanged();
                Toast.makeText(c, "샘플 구독을 추가했어요", Toast.LENGTH_SHORT).show();
            });
            content.addView(gData, Widgets.MW);

            // 5. About
            Widgets.Group gAbout = new Widgets.Group(c, "앱 정보");
            gAbout.row(Icons.INFO, Ui.text2(), c.getString(R.string.app_name), MainActivity.VERSION, false, null);
            content.addView(gAbout, Widgets.MW);
        }

        private void showRatesSheet() {
            LinearLayout form = new LinearLayout(main);
            form.setOrientation(LinearLayout.VERTICAL);
            EditText uEt = Widgets.formField(main, "USD (1달러 → 원)", "1400", String.valueOf(Store.usd), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, form);
            EditText jEt = Widgets.formField(main, "JPY (1엔 → 원)", "9.4", String.valueOf(Store.jpy), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, form);
            EditText eEt = Widgets.formField(main, "EUR (1유로 → 원)", "1500", String.valueOf(Store.eur), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, form);
            Widgets.showFormSheet(main, "외화 환산 기준", form, main.getString(R.string.save), () -> {
                try { Store.usd = Math.max(1, Double.parseDouble(uEt.getText().toString().trim())); } catch (Exception ignored) {}
                try { Store.jpy = Math.max(0.1, Double.parseDouble(jEt.getText().toString().trim())); } catch (Exception ignored) {}
                try { Store.eur = Math.max(1, Double.parseDouble(eEt.getText().toString().trim())); } catch (Exception ignored) {}
                main.dataChanged();
            });
        }

        private void seedSamples() {
            int[] idxs = {0, 2, 3, 5, 7};
            java.time.LocalDate t = Store.today();
            for (int k = 0; k < idxs.length; k++) {
                Store.Sub s = Store.PRESETS[idxs[k]].make();
                s.anchor = t.plusDays(k * 3L).toEpochDay();
                if (k == 3) s.trial = true;
                Store.subs.add(s);
            }
        }
    }
}

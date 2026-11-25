package com.oneuibook.checker;

import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** All main screens of Checker: Home, Calendar, Stats, Search, plus editor & detail sheets. */
final class Pages {
    private Pages() {}

    static View avatar(Context c, Store.Sub s, int sizeDp) {
        final String ch = s.initial();
        final int bg = s.color;
        final float rad = sizeDp * 0.34f;
        final float sp = sizeDp * 0.44f;
        return new View(c) {
            final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
            final RectF rf = new RectF();
            {
                bp.setColor(bg);
                tp.setColor(0xFFFFFFFF);
                tp.setTypeface(Ui.bold);
                tp.setTextAlign(Paint.Align.CENTER);
                tp.setTextSize(Ui.sp(sp));
            }
            @Override protected void onDraw(Canvas cv) {
                rf.set(0, 0, getWidth(), getHeight());
                cv.drawRoundRect(rf, Ui.dpf(rad), Ui.dpf(rad), bp);
                Paint.FontMetrics fm = tp.getFontMetrics();
                cv.drawText(ch, getWidth() / 2f, getHeight() / 2f - (fm.ascent + fm.descent) / 2f, tp);
            }
        };
    }

    static LinearLayout cardBox(Context c) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = Ui.round(Ui.card(), Ui.cardRadius());
        if (Ui.gStyle) bg.setStroke(Ui.dp(0.8f), Ui.gStroke());
        card.setBackground(bg);
        card.setClipToOutline(true);
        return card;
    }

    static LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8));
        return lp;
    }

    // ---- 1. Home Page ----
    static final class HomePage extends Page {
        private int catFilter = 0; // 0 = 전체

        HomePage(MainActivity m) {
            super(m, m.getString(R.string.app_name), true);
            setGrouped(true);
            headerIcons = new int[]{Icons.PLUS, Icons.SORT};
        }

        @Override void build() {
            Context c = getContext();
            LocalDate today = Store.today();
            double monthTotal = Store.monthCharges(today.getYear(), today.getMonthValue(), false);
            double monthPaid = Store.monthCharges(today.getYear(), today.getMonthValue(), true);
            double normMonthly = Store.monthlyTotal();
            double normYearly = normMonthly * 12.0;

            // Hero summary card
            LinearLayout hero = cardBox(c);
            hero.setPadding(Ui.dp(20), Ui.dp(18), Ui.dp(20), Ui.dp(18));

            LinearLayout topRow = new LinearLayout(c);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView monthLbl = Ui.text(c, today.getMonthValue() + "월 예정 지출", 13.5f, Ui.semibold, Ui.text2());
            topRow.addView(monthLbl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView cntBadge = Widgets.pillBadge(c, "활성 " + Store.activeCount() + "개", Ui.alpha(Ui.ACCENT, Ui.dark ? 0.24f : 0.14f), Ui.ACCENT);
            topRow.addView(cntBadge);
            hero.addView(topRow, Widgets.MW);

            TextView bigAmt = Ui.text(c, Store.won(monthTotal), 32, Ui.heavy, Ui.text());
            LinearLayout.LayoutParams balp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            balp.topMargin = Ui.dp(6);
            hero.addView(bigAmt, balp);

            // Progress bar (paid / total)
            float ratio = monthTotal <= 0 ? 0f : Ui.clamp((float) (monthPaid / monthTotal), 0f, 1f);
            View prog = new View(c) {
                final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas cv) {
                    float rh = getHeight() / 2f;
                    p.setColor(Ui.separator());
                    cv.drawRoundRect(0, 0, getWidth(), getHeight(), rh, rh, p);
                    if (ratio > 0f) {
                        p.setColor(Ui.ACCENT);
                        cv.drawRoundRect(0, 0, Math.max(getHeight(), getWidth() * ratio), getHeight(), rh, rh, p);
                    }
                }
            };
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(7));
            plp.topMargin = Ui.dp(12);
            hero.addView(prog, plp);

            LinearLayout subRow = new LinearLayout(c);
            subRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            srlp.topMargin = Ui.dp(10);
            TextView leftSub = Ui.text(c, "확인 완료 " + Store.won(monthPaid), 12.5f, Ui.medium, Ui.ACCENT);
            TextView rightSub = Ui.text(c, "남은 결제 " + Store.won(Math.max(0, monthTotal - monthPaid)), 12.5f, Ui.regular, Ui.text2());
            subRow.addView(leftSub, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            subRow.addView(rightSub);
            hero.addView(subRow, srlp);

            View sep = new View(c);
            sep.setBackgroundColor(Ui.separator());
            LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(0.6f)));
            sepLp.setMargins(0, Ui.dp(14), 0, Ui.dp(12));
            hero.addView(sep, sepLp);

            LinearLayout normRow = new LinearLayout(c);
            normRow.setOrientation(LinearLayout.HORIZONTAL);
            normRow.addView(metricCol(c, "월 평균 환산", Store.won(normMonthly)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            normRow.addView(metricCol(c, "연간 예상", Store.won(normYearly)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            hero.addView(normRow, Widgets.MW);

            content.addView(hero, cardLp());

            // Upcoming payments within 14 days
            List<Store.Due> upcoming = new ArrayList<>();
            for (Store.Due d : Store.dues(today, today.plusDays(14))) {
                if (!d.paid()) upcoming.add(d);
            }
            if (!upcoming.isEmpty()) {
                content.addView(Widgets.section(c, "다가오는 결제"));
                LinearLayout rail = Widgets.rail(content, 10);
                int max = Math.min(upcoming.size(), 8);
                for (int i = 0; i < max; i++) {
                    rail.addView(dueCard(c, upcoming.get(i)));
                }
            }

            // Category filter chips
            String[] chipLabels = new String[Store.CATEGORIES.length + 1];
            chipLabels[0] = "전체";
            System.arraycopy(Store.CATEGORIES, 0, chipLabels, 1, Store.CATEGORIES.length);
            content.addView(Widgets.section(c, "내 구독·고정지출"));
            content.addView(new Widgets.Chips(c, chipLabels, catFilter, idx -> {
                catFilter = idx;
                rebuild();
            }), Widgets.MW);

            // Filtered & sorted list
            List<Store.Sub> filtered = new ArrayList<>();
            for (Store.Sub s : Store.subs) {
                if (s.paused && !Store.showPaused) continue;
                if (catFilter > 0 && !Store.CATEGORIES[catFilter - 1].equals(s.category)) continue;
                filtered.add(s);
            }
            sortSubs(filtered, today);

            if (filtered.isEmpty()) {
                LinearLayout empty = cardBox(c);
                empty.setPadding(Ui.dp(24), Ui.dp(28), Ui.dp(24), Ui.dp(28));
                empty.setGravity(Gravity.CENTER_HORIZONTAL);
                TextView et = Ui.text(c, Store.subs.isEmpty() ? "등록된 항목 없음" : "항목 없음", 16, Ui.semibold, Ui.text());
                et.setGravity(Gravity.CENTER);
                empty.addView(et);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                blp.topMargin = Ui.dp(14);
                empty.addView(Widgets.button(c, "항목 추가", true, v -> openEditor(main, null, null)), blp);
                content.addView(empty, cardLp());
                return;
            }

            if (main.wide()) {
                // 2-column grid on foldable unfolded / tablet
                LinearLayout grid = new LinearLayout(c);
                grid.setOrientation(LinearLayout.VERTICAL);
                grid.setPadding(Ui.dp(16), Ui.dp(4), Ui.dp(16), Ui.dp(8));
                for (int i = 0; i < filtered.size(); i += 2) {
                    LinearLayout row = new LinearLayout(c);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rlp.bottomMargin = Ui.dp(10);
                    LinearLayout leftCard = cardBox(c);
                    leftCard.addView(subRow(c, filtered.get(i), today));
                    LinearLayout.LayoutParams clp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    clp1.rightMargin = Ui.dp(5);
                    row.addView(leftCard, clp1);
                    if (i + 1 < filtered.size()) {
                        LinearLayout rightCard = cardBox(c);
                        rightCard.addView(subRow(c, filtered.get(i + 1), today));
                        LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                        clp2.leftMargin = Ui.dp(5);
                        row.addView(rightCard, clp2);
                    } else {
                        View spacer = new View(c);
                        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, 1, 1f);
                        slp.leftMargin = Ui.dp(5);
                        row.addView(spacer, slp);
                    }
                    grid.addView(row, rlp);
                }
                content.addView(grid, Widgets.MW);
            } else {
                Widgets.Group g = new Widgets.Group(c, null);
                for (Store.Sub s : filtered) g.addCustom(subRow(c, s, today));
                content.addView(g, Widgets.MW);
            }
        }

        private LinearLayout metricCol(Context c, String label, String val) {
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.addView(Ui.text(c, label, 12, Ui.regular, Ui.text2()));
            TextView tv = Ui.text(c, val, 16, Ui.bold, Ui.text());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(2);
            box.addView(tv, lp);
            return box;
        }

        private View dueCard(Context c, Store.Due d) {
            LinearLayout card = cardBox(c);
            card.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(14));
            LinearLayout top = new LinearLayout(c);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(avatar(c, d.sub, 34), new LinearLayout.LayoutParams(Ui.dp(34), Ui.dp(34)));
            View sp = new View(c);
            top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
            long days = ChronoUnit.DAYS.between(Store.today(), d.date);
            int badgeCol = days == 0 ? 0xFFE5484D : (days <= 2 ? 0xFFF59E0B : Ui.ACCENT);
            top.addView(Widgets.pillBadge(c, d.trialEnd() ? "체험 종료" : Store.relDay(d.date),
                    Ui.alpha(badgeCol, Ui.dark ? 0.28f : 0.14f), badgeCol));
            card.addView(top, Widgets.MW);

            TextView name = Ui.text(c, d.sub.name, 14.5f, Ui.semibold, Ui.text());
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = Ui.dp(10);
            card.addView(name, nlp);

            TextView amt = Ui.text(c, Store.amount(d.sub), 16.5f, Ui.bold, Ui.text());
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.topMargin = Ui.dp(3);
            card.addView(amt, alp);

            TextView dt = Ui.text(c, Store.dateLabel(d.date), 11.5f, Ui.regular, Ui.text2());
            LinearLayout.LayoutParams dtlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dtlp.topMargin = Ui.dp(2);
            card.addView(dt, dtlp);

            TextView checkBtn = Ui.text(c, "결제 확인", 12.5f, Ui.semibold, Ui.ACCENT);
            checkBtn.setGravity(Gravity.CENTER);
            checkBtn.setPadding(0, Ui.dp(7), 0, Ui.dp(7));
            checkBtn.setBackground(Ui.round(Ui.alpha(Ui.ACCENT, Ui.dark ? 0.2f : 0.12f), 100));
            checkBtn.setOnClickListener(v -> {
                Store.paid.add(d.key());
                main.dataChanged();
                Toast.makeText(c, d.sub.name + " 결제를 확인했어요", Toast.LENGTH_SHORT).show();
            });
            Ui.press(checkBtn, 0.95f);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.topMargin = Ui.dp(10);
            card.addView(checkBtn, blp);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(168), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(10);
            card.setLayoutParams(lp);
            card.setOnClickListener(v -> openDetail(main, d.sub, d));
            Ui.press(card, 0.97f);
            return card;
        }

        private View subRow(Context c, Store.Sub s, LocalDate today) {
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(Ui.dp(16), Ui.dp(13), Ui.dp(16), Ui.dp(13));
            r.setBackground(Widgets.ripple());
            if (s.paused) r.setAlpha(0.55f);

            View av = avatar(c, s, 40);
            LinearLayout.LayoutParams avlp = new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40));
            avlp.rightMargin = Ui.dp(14);
            r.addView(av, avlp);

            LinearLayout mid = new LinearLayout(c);
            mid.setOrientation(LinearLayout.VERTICAL);
            LinearLayout titleLine = new LinearLayout(c);
            titleLine.setGravity(Gravity.CENTER_VERTICAL);
            titleLine.addView(Ui.text(c, s.name, 15.5f, Ui.semibold, Ui.text()));
            if (s.paused) titleLine.addView(Widgets.pillBadge(c, "일시정지", Ui.chip(), Ui.text2()));
            else if (s.trial) titleLine.addView(Widgets.pillBadge(c, "체험", Ui.alpha(0xFFF59E0B, 0.2f), 0xFFF59E0B));
            mid.addView(titleLine);

            Store.Due next = s.paused ? null : Store.nextDue(s, today);
            String subTxt = s.category + " · " + Store.cycleLabel(s)
                    + (next != null ? " · " + Store.relDay(next.date) : "");
            TextView st = Ui.text(c, subTxt, 12.5f, Ui.regular, Ui.text2());
            LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stlp.topMargin = Ui.dp(2);
            mid.addView(st, stlp);
            r.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout right = new LinearLayout(c);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            right.addView(Ui.text(c, Store.amount(s), 15.5f, Ui.bold, Ui.text()));
            if (!"KRW".equals(s.currency)) {
                TextView approx = Ui.text(c, "≈ " + Store.won(Store.krw(s)), 11.5f, Ui.regular, Ui.text2());
                right.addView(approx);
            } else if (s.cycle == Store.YEAR) {
                TextView approx = Ui.text(c, "월 " + Store.won(Store.monthly(s)), 11.5f, Ui.regular, Ui.text2());
                right.addView(approx);
            }
            r.addView(right);

            r.setOnClickListener(v -> openDetail(main, s, next));
            return r;
        }
    }

    static void sortSubs(List<Store.Sub> list, LocalDate today) {
        Collections.sort(list, (a, b) -> {
            if (a.paused != b.paused) return a.paused ? 1 : -1;
            if (Store.sortMode == 1) return Double.compare(Store.monthly(b), Store.monthly(a));
            if (Store.sortMode == 2) return a.name.compareToIgnoreCase(b.name);
            Store.Due da = Store.nextDue(a, today), db = Store.nextDue(b, today);
            if (da == null && db == null) return a.name.compareToIgnoreCase(b.name);
            if (da == null) return 1;
            if (db == null) return -1;
            int cmp = da.date.compareTo(db.date);
            return cmp != 0 ? cmp : Double.compare(Store.krw(b), Store.krw(a));
        });
    }

    // ---- 2. Calendar Page ----
    static final class CalendarPage extends Page {
        private YearMonth ym = YearMonth.from(Store.today());
        private LocalDate selected = Store.today();

        CalendarPage(MainActivity m) {
            super(m, m.getString(R.string.tab_calendar), true);
            setGrouped(true);
            headerIcons = new int[]{Icons.CALENDAR, Icons.PLUS};
        }

        void goToday() {
            ym = YearMonth.from(Store.today());
            selected = Store.today();
            rebuild();
        }

        @Override void build() {
            Context c = getContext();
            LocalDate first = ym.atDay(1);
            LocalDate last = ym.atEndOfMonth();
            List<Store.Due> monthDues = Store.dues(first, last);
            Map<Integer, List<Store.Due>> byDay = new HashMap<>();
            double total = 0;
            for (Store.Due d : monthDues) {
                total += Store.krw(d.sub);
                byDay.computeIfAbsent(d.date.getDayOfMonth(), k -> new ArrayList<>()).add(d);
            }

            // Month navigation + calendar card
            LinearLayout calCard = cardBox(c);
            calCard.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(16));

            LinearLayout nav = new LinearLayout(c);
            nav.setGravity(Gravity.CENTER_VERTICAL);
            Icons.IconView prev = new Icons.IconView(c, Icons.CHEVRON_L, 20, Ui.text());
            prev.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
            prev.setBackground(Widgets.ripple());
            prev.setOnClickListener(v -> { ym = ym.minusMonths(1); selected = ym.atDay(1); rebuild(); });
            nav.addView(prev, new LinearLayout.LayoutParams(Ui.dp(42), Ui.dp(42)));

            LinearLayout mid = new LinearLayout(c);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setGravity(Gravity.CENTER_HORIZONTAL);
            mid.addView(Ui.text(c, ym.getYear() + "년 " + ym.getMonthValue() + "월", 18, Ui.bold, Ui.text()));
            mid.addView(Ui.text(c, "예정 " + Store.won(total) + " · " + monthDues.size() + "건", 12.5f, Ui.medium, Ui.ACCENT));
            nav.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Icons.IconView next = new Icons.IconView(c, Icons.CHEVRON, 20, Ui.text());
            next.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
            next.setBackground(Widgets.ripple());
            next.setOnClickListener(v -> { ym = ym.plusMonths(1); selected = ym.atDay(1); rebuild(); });
            nav.addView(next, new LinearLayout.LayoutParams(Ui.dp(42), Ui.dp(42)));
            calCard.addView(nav, Widgets.MW);

            // Day of week header (Sun..Sat)
            String[] dows = {"일", "월", "화", "수", "목", "금", "토"};
            LinearLayout dowRow = new LinearLayout(c);
            dowRow.setPadding(0, Ui.dp(10), 0, Ui.dp(6));
            for (int i = 0; i < 7; i++) {
                int col = i == 0 ? 0xFFE5484D : (i == 6 ? 0xFF3B8BF5 : Ui.text2());
                TextView t = new TextView(c);
                t.setText(dows[i]);
                t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
                t.setTypeface(Ui.semibold);
                t.setTextColor(col);
                t.setIncludeFontPadding(false);
                t.setGravity(Gravity.CENTER);
                dowRow.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            calCard.addView(dowRow, Widgets.MW);

            // Weeks grid
            int leadBlanks = first.getDayOfWeek().getValue() % 7; // Sunday=0
            int daysInMonth = ym.lengthOfMonth();
            int totalCells = leadBlanks + daysInMonth;
            int rows = (totalCells + 6) / 7;
            LocalDate today = Store.today();

            for (int r = 0; r < rows; r++) {
                LinearLayout week = new LinearLayout(c);
                for (int col = 0; col < 7; col++) {
                    int day = r * 7 + col - leadBlanks + 1;
                    if (day < 1 || day > daysInMonth) {
                        week.addView(new View(c), new LinearLayout.LayoutParams(0, Ui.dp(50), 1f));
                        continue;
                    }
                    LocalDate cellDate = ym.atDay(day);
                    boolean isToday = cellDate.equals(today);
                    boolean isSel = cellDate.equals(selected);
                    List<Store.Due> list = byDay.get(day);

                    LinearLayout cell = new LinearLayout(c);
                    cell.setOrientation(LinearLayout.VERTICAL);
                    cell.setGravity(Gravity.CENTER_HORIZONTAL);
                    cell.setPadding(0, Ui.dp(6), 0, Ui.dp(5));
                    if (isSel) {
                        cell.setBackground(Ui.round(Ui.alpha(Ui.ACCENT, Ui.dark ? 0.28f : 0.16f), 14));
                    } else if (isToday) {
                        GradientDrawable gd = Ui.round(0x00000000, 14);
                        gd.setStroke(Ui.dp(1.2f), Ui.ACCENT);
                        cell.setBackground(gd);
                    }

                    int numCol = isSel ? Ui.ACCENT : (col == 0 ? 0xFFE5484D : (col == 6 ? 0xFF3B8BF5 : Ui.text()));
                    TextView num = new TextView(c);
                    num.setText(String.valueOf(day));
                    num.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
                    num.setTypeface((isSel || isToday) ? Ui.bold : Ui.medium);
                    num.setTextColor(numCol);
                    num.setIncludeFontPadding(false);
                    num.setGravity(Gravity.CENTER);
                    cell.addView(num, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    if (list != null && !list.isEmpty()) {
                        LinearLayout dots = new LinearLayout(c);
                        dots.setGravity(Gravity.CENTER);
                        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        dlp.topMargin = Ui.dp(4);
                        int shown = Math.min(list.size(), 3);
                        for (int k = 0; k < shown; k++) {
                            View dot = new View(c);
                            dot.setBackground(Ui.round(list.get(k).sub.color, 4));
                            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(Ui.dp(5.5f), Ui.dp(5.5f));
                            if (k > 0) dotLp.leftMargin = Ui.dp(2.5f);
                            dots.addView(dot, dotLp);
                        }
                        cell.addView(dots, dlp);
                    }
                    cell.setOnClickListener(v -> { selected = cellDate; rebuild(); });
                    week.addView(cell, new LinearLayout.LayoutParams(0, Ui.dp(50), 1f));
                }
                calCard.addView(week, Widgets.MW);
            }
            content.addView(calCard, cardLp());

            // Selected day payments
            List<Store.Due> selDues = selected.getYear() == ym.getYear() && selected.getMonthValue() == ym.getMonthValue()
                    ? byDay.get(selected.getDayOfMonth()) : null;
            content.addView(Widgets.section(c, Store.dateLabel(selected) + " 결제"));
            if (selDues == null || selDues.isEmpty()) {
                content.addView(Widgets.caption(c, "이 날 예정된 결제가 없어요."));
            } else {
                Widgets.Group g = new Widgets.Group(c, null);
                for (Store.Due d : selDues) g.addCustom(dueRow(c, d));
                content.addView(g, Widgets.MW);
            }

            // Full month schedule
            if (!monthDues.isEmpty()) {
                content.addView(Widgets.section(c, ym.getMonthValue() + "월 전체 일정"));
                Widgets.Group g = new Widgets.Group(c, null);
                for (Store.Due d : monthDues) g.addCustom(dueRow(c, d));
                content.addView(g, Widgets.MW);
            }
        }

        private View dueRow(Context c, Store.Due d) {
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12));
            r.setBackground(Widgets.ripple());

            View av = avatar(c, d.sub, 36);
            LinearLayout.LayoutParams avlp = new LinearLayout.LayoutParams(Ui.dp(36), Ui.dp(36));
            avlp.rightMargin = Ui.dp(12);
            r.addView(av, avlp);

            LinearLayout mid = new LinearLayout(c);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.addView(Ui.text(c, d.sub.name, 15, Ui.semibold, Ui.text()));
            String meta = d.date.getMonthValue() + "/" + d.date.getDayOfMonth() + " · " + Store.amount(d.sub)
                    + (d.paid() ? " · 확인 완료" : "");
            mid.addView(Ui.text(c, meta, 12.5f, Ui.regular, d.paid() ? Ui.ACCENT : Ui.text2()));
            r.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView chk = Ui.text(c, d.paid() ? "완료" : "확인", 12.5f, Ui.semibold, d.paid() ? 0xFFFFFFFF : Ui.ACCENT);
            chk.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
            chk.setBackground(Ui.round(d.paid() ? Ui.ACCENT : Ui.alpha(Ui.ACCENT, Ui.dark ? 0.2f : 0.12f), 100));
            chk.setOnClickListener(v -> {
                if (d.paid()) Store.paid.remove(d.key()); else Store.paid.add(d.key());
                main.dataChanged();
            });
            Ui.press(chk, 0.94f);
            r.addView(chk);

            r.setOnClickListener(v -> openDetail(main, d.sub, d));
            return r;
        }
    }

    // ---- 3. Stats Page ----
    static final class StatsPage extends Page {
        StatsPage(MainActivity m) {
            super(m, m.getString(R.string.tab_stats), true);
            setGrouped(true);
            headerIcons = new int[]{Icons.SHARE};
        }

        @Override void build() {
            Context c = getContext();
            double monthly = Store.monthlyTotal();
            double yearly = monthly * 12.0;

            // Summary card
            LinearLayout sum = cardBox(c);
            sum.setPadding(Ui.dp(20), Ui.dp(18), Ui.dp(20), Ui.dp(18));
            sum.addView(Ui.text(c, "연간 예상 고정지출", 13.5f, Ui.semibold, Ui.text2()));
            TextView yv = Ui.text(c, Store.won(yearly), 30, Ui.heavy, Ui.text());
            LinearLayout.LayoutParams ylp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ylp.topMargin = Ui.dp(4);
            sum.addView(yv, ylp);
            sum.addView(Ui.text(c, "월 평균 " + Store.won(monthly) + " · 일 평균 " + Store.won(yearly / 365.0), 13, Ui.regular, Ui.text2()));
            content.addView(sum, cardLp());

            // 6-month bar chart
            content.addView(Widgets.section(c, "6개월 예정 지출 추이"));
            LinearLayout barCard = cardBox(c);
            barCard.setPadding(Ui.dp(16), Ui.dp(18), Ui.dp(16), Ui.dp(16));
            YearMonth curYm = YearMonth.from(Store.today());
            double[] vals = new double[6];
            String[] labels = new String[6];
            double maxV = 1;
            for (int i = 0; i < 6; i++) {
                YearMonth m = curYm.plusMonths(i);
                vals[i] = Store.monthCharges(m.getYear(), m.getMonthValue(), false);
                labels[i] = m.getMonthValue() + "월";
                if (vals[i] > maxV) maxV = vals[i];
            }
            barCard.addView(new BarChartView(c, vals, labels, maxV),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(170)));
            content.addView(barCard, cardLp());

            // Category breakdown
            content.addView(Widgets.section(c, "카테고리별 비중"));
            Map<String, Double> byCat = Store.byCategory();
            List<Map.Entry<String, Double>> entries = new ArrayList<>();
            for (Map.Entry<String, Double> e : byCat.entrySet()) if (e.getValue() > 0) entries.add(e);
            entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            if (entries.isEmpty()) {
                content.addView(Widgets.caption(c, "활성 구독이 생기면 카테고리별 비중이 표시됩니다."));
            } else {
                LinearLayout catCard = cardBox(c);
                catCard.setPadding(Ui.dp(18), Ui.dp(16), Ui.dp(18), Ui.dp(16));
                // Stacked horizontal bar
                catCard.addView(new StackedBarView(c, entries, Math.max(1, monthly)),
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(14)));
                for (Map.Entry<String, Double> e : entries) {
                    LinearLayout row = new LinearLayout(c);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, Ui.dp(10), 0, Ui.dp(2));
                    View dot = new View(c);
                    dot.setBackground(Ui.round(Store.categoryColor(e.getKey()), 5));
                    LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(10), Ui.dp(10));
                    dlp.rightMargin = Ui.dp(10);
                    row.addView(dot, dlp);
                    row.addView(Ui.text(c, e.getKey(), 14.5f, Ui.medium, Ui.text()),
                            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    int pct = (int) Math.round(e.getValue() / Math.max(1, monthly) * 100);
                    row.addView(Ui.text(c, Store.won(e.getValue()) + " (" + pct + "%)", 14, Ui.semibold, Ui.text2()));
                    catCard.addView(row, Widgets.MW);
                }
                content.addView(catCard, cardLp());
            }

            // Top spending subscriptions
            List<Store.Sub> active = new ArrayList<>();
            for (Store.Sub s : Store.subs) if (!s.paused) active.add(s);
            active.sort((a, b) -> Double.compare(Store.monthly(b), Store.monthly(a)));
            if (!active.isEmpty()) {
                content.addView(Widgets.section(c, "지출 상위 항목"));
                Widgets.Group g = new Widgets.Group(c, null);
                int lim = Math.min(active.size(), 5);
                for (int i = 0; i < lim; i++) {
                    Store.Sub s = active.get(i);
                    g.row(0, s.color, (i + 1) + ". " + s.name, "월 " + Store.won(Store.monthly(s)), true,
                            v -> openDetail(main, s, Store.nextDue(s, Store.today())));
                }
                content.addView(g, Widgets.MW);
            }
        }
    }

    static final class BarChartView extends View {
        private final double[] vals;
        private final String[] labels;
        private final double max;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);

        BarChartView(Context c, double[] vals, String[] labels, double max) {
            super(c);
            this.vals = vals;
            this.labels = labels;
            this.max = max;
            tp.setTypeface(Ui.medium);
            tp.setTextAlign(Paint.Align.CENTER);
        }

        @Override protected void onDraw(Canvas cv) {
            int n = vals.length;
            if (n == 0) return;
            float w = getWidth(), h = getHeight();
            float topPad = Ui.dpf(22), botPad = Ui.dpf(24);
            float chartH = Math.max(1f, h - topPad - botPad);
            float slot = w / n;
            float barW = Math.min(Ui.dpf(28), slot * 0.48f);

            for (int i = 0; i < n; i++) {
                float cx = slot * (i + 0.5f);
                float ratio = (float) (vals[i] / max);
                float bh = Math.max(Ui.dpf(6), chartH * ratio);
                float left = cx - barW / 2f, top = topPad + (chartH - bh);
                p.setColor(i == 0 ? Ui.ACCENT : Ui.alpha(Ui.ACCENT, Ui.dark ? 0.45f : 0.32f));
                cv.drawRoundRect(left, top, left + barW, topPad + chartH, Ui.dpf(8), Ui.dpf(8), p);

                tp.setTextSize(Ui.sp(10.5f));
                tp.setColor(Ui.text2());
                String shortVal = vals[i] >= 10000 ? Math.round(vals[i] / 10000.0) + "만" : String.valueOf(Math.round(vals[i] / 1000.0)) + "천";
                if (vals[i] > 0) cv.drawText(shortVal, cx, top - Ui.dpf(6), tp);

                tp.setTextSize(Ui.sp(12f));
                tp.setColor(i == 0 ? Ui.ACCENT : Ui.text2());
                cv.drawText(labels[i], cx, h - Ui.dpf(4), tp);
            }
        }
    }

    static final class StackedBarView extends View {
        private final List<Map.Entry<String, Double>> items;
        private final double total;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        StackedBarView(Context c, List<Map.Entry<String, Double>> items, double total) {
            super(c);
            this.items = items;
            this.total = total;
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), x = 0f, gap = Ui.dpf(3);
            for (int i = 0; i < items.size(); i++) {
                float seg = (float) (items.get(i).getValue() / total) * w;
                float drawW = Math.max(Ui.dpf(6), seg - (i < items.size() - 1 ? gap : 0));
                p.setColor(Store.categoryColor(items.get(i).getKey()));
                cv.drawRoundRect(x, 0, Math.min(w, x + drawW), h, h / 2f, h / 2f, p);
                x += seg;
            }
        }
    }

    // ---- 4. Search Page ----
    static final class SearchPage extends Page {
        private String query = "";

        SearchPage(MainActivity m) {
            super(m, m.getString(R.string.search), true);
            setGrouped(true);
            headerIcons = new int[0];
        }

        void setQuery(String q) {
            query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
            rebuild();
        }

        @Override void build() {
            Context c = getContext();
            List<Store.Sub> hits = new ArrayList<>();
            for (Store.Sub s : Store.subs) {
                if (query.isEmpty()
                        || s.name.toLowerCase(Locale.ROOT).contains(query)
                        || s.category.toLowerCase(Locale.ROOT).contains(query)
                        || s.memo.toLowerCase(Locale.ROOT).contains(query)
                        || s.payment.toLowerCase(Locale.ROOT).contains(query)) {
                    hits.add(s);
                }
            }
            if (hits.isEmpty()) {
                content.addView(Widgets.caption(c, query.isEmpty() ? "등록된 구독이 없어요." : "검색 결과가 없어요."));
                return;
            }
            Widgets.Group g = new Widgets.Group(c, "결과 " + hits.size() + "건");
            for (Store.Sub s : hits) {
                g.row(0, s.color, s.name, Store.amount(s) + " · " + Store.cycleLabel(s), true,
                        v -> openDetail(main, s, Store.nextDue(s, Store.today())));
            }
            content.addView(g, Widgets.MW);
        }
    }

    // ---- 5. Detail Sheet ----
    static void openDetail(MainActivity m, Store.Sub s, Store.Due due) {
        LinearLayout body = new LinearLayout(m);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(20), Ui.dp(4), Ui.dp(20), Ui.dp(12));

        LinearLayout head = new LinearLayout(m);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams avlp = new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48));
        avlp.rightMargin = Ui.dp(14);
        head.addView(avatar(m, s, 48), avlp);
        LinearLayout info = new LinearLayout(m);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(Ui.text(m, s.name, 19, Ui.bold, Ui.text()));
        info.addView(Ui.text(m, s.category + " · " + Store.cycleLabel(s), 13.5f, Ui.regular, Ui.text2()));
        head.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.text(m, Store.amount(s), 18, Ui.bold, Ui.ACCENT));
        body.addView(head, Widgets.MW);

        Store.Due next = due != null ? due : Store.nextDue(s, Store.today());
        LinearLayout meta = new LinearLayout(m);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
        meta.setBackground(Ui.round(Ui.chip(), 16));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = Ui.dp(14);
        if (next != null) {
            meta.addView(Ui.text(m, "다음 결제일: " + Store.dateLabel(next.date) + " (" + Store.relDay(next.date) + ")", 14, Ui.medium, Ui.text()));
        }
        meta.addView(Ui.text(m, "월 환산 금액: " + Store.won(Store.monthly(s)), 13.5f, Ui.regular, Ui.text2()));
        meta.addView(Ui.text(m, "알림: 결제 " + Store.remindDays(s) + "일 전 · " + Store.remindHour + "시", 13.5f, Ui.regular, Ui.text2()));
        if (!s.payment.isEmpty()) meta.addView(Ui.text(m, "결제 수단: " + s.payment, 13.5f, Ui.regular, Ui.text2()));
        if (!s.memo.isEmpty()) meta.addView(Ui.text(m, "메모: " + s.memo, 13.5f, Ui.regular, Ui.text2()));
        body.addView(meta, mlp);

        // Action buttons
        LinearLayout row1 = new LinearLayout(m);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r1lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r1lp.topMargin = Ui.dp(14);

        if (next != null && !s.paused) {
            boolean paid = next.paid();
            TextView payBtn = Widgets.button(m, paid ? "확인 취소" : "이번 결제 확인", !paid, v -> {
                if (paid) Store.paid.remove(next.key()); else Store.paid.add(next.key());
                GlassMenu.dismissAny(m.root);
                m.dataChanged();
            });
            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p1.rightMargin = Ui.dp(8);
            row1.addView(payBtn, p1);
        }

        TextView editBtn = Widgets.button(m, "편집", false, v -> {
            GlassMenu.dismissAny(m.root);
            m.root.postDelayed(() -> openEditor(m, s, null), 160);
        });
        row1.addView(editBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(row1, r1lp);

        LinearLayout row2 = new LinearLayout(m);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2lp.topMargin = Ui.dp(8);

        TextView pauseBtn = Widgets.button(m, s.paused ? "다시 시작" : "일시정지", false, v -> {
            s.paused = !s.paused;
            GlassMenu.dismissAny(m.root);
            m.dataChanged();
        });
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p2.rightMargin = Ui.dp(8);
        row2.addView(pauseBtn, p2);

        TextView delBtn = Ui.text(m, "삭제", 15, Ui.semibold, 0xFFE5484D);
        delBtn.setGravity(Gravity.CENTER);
        delBtn.setPadding(Ui.dp(18), Ui.dp(12), Ui.dp(18), Ui.dp(12));
        delBtn.setBackground(Ui.round(Ui.alpha(0xFFE5484D, Ui.dark ? 0.22f : 0.12f), 100));
        delBtn.setOnClickListener(v -> {
            Store.subs.remove(s);
            GlassMenu.dismissAny(m.root);
            m.dataChanged();
            Toast.makeText(m, s.name + " 항목을 삭제했어요", Toast.LENGTH_SHORT).show();
        });
        Ui.press(delBtn, 0.95f);
        row2.addView(delBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(row2, r2lp);

        RectF anchor = new RectF(Ui.screenW / 2f - Ui.dpf(40), Ui.screenH - Ui.dpf(80), Ui.screenW / 2f + Ui.dpf(40), Ui.screenH - Ui.dpf(40));
        GlassMenu.customSheet(m, anchor, "구독 상세", body, null);
    }

    // ---- 6. Add / Edit Sheet ----
    static void openEditor(MainActivity m, Store.Sub existing, RectF anchor) {
        final boolean isNew = existing == null;
        final Store.Sub work = isNew ? new Store.Sub() : existing;
        final int[] selCycle = {work.cycle};
        final int[] selCur = {indexOf(Store.CURRENCIES, work.currency)};
        final int[] selCat = {indexOf(Store.CATEGORIES, work.category)};
        final int[] selColor = {work.color};
        final long[] selAnchor = {work.anchor};
        final int[] selRemind = {work.remind};
        final boolean[] isTrial = {work.trial};

        LinearLayout form = new LinearLayout(m);
        form.setOrientation(LinearLayout.VERTICAL);

        final EditText nameEt;
        final EditText amtEt;

        // Presets strip for new subscriptions
        if (isNew) {
            TextView pl = Ui.text(m, "프리셋", 12.5f, Ui.semibold, Ui.text2());
            LinearLayout.LayoutParams pllp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pllp.bottomMargin = Ui.dp(6);
            form.addView(pl, pllp);
        }

        HorizontalScrollView presetScroll = new HorizontalScrollView(m);
        presetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presetRow = new LinearLayout(m);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetScroll.addView(presetRow);
        if (isNew) form.addView(presetScroll, Widgets.MW);

        nameEt = Widgets.formField(m, "이름", "예: Netflix, 월세, Spotify", work.name, InputType.TYPE_CLASS_TEXT, form);
        amtEt = Widgets.formField(m, "금액", "예: 13500", work.amount > 0 ? trimNum(work.amount) : "", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, form);

        // Currency chips
        TextView curLbl = Ui.text(m, "통화", 13, Ui.semibold, Ui.text2());
        curLbl.setPadding(Ui.dp(4), Ui.dp(6), 0, Ui.dp(4));
        form.addView(curLbl);
        Widgets.Chips curChips = new Widgets.Chips(m, Store.CURRENCIES, selCur[0], idx -> selCur[0] = idx);
        form.addView(curChips, Widgets.MW);

        // Cycle chips
        String[] cycleLabels = {"매월", "매년", "매주", "일 단위"};
        TextView cycLbl = Ui.text(m, "결제 주기", 13, Ui.semibold, Ui.text2());
        cycLbl.setPadding(Ui.dp(4), Ui.dp(6), 0, Ui.dp(4));
        form.addView(cycLbl);
        Widgets.Chips cycChips = new Widgets.Chips(m, cycleLabels, selCycle[0], idx -> selCycle[0] = idx);
        form.addView(cycChips, Widgets.MW);

        // First / next billing date picker button
        TextView dateBtn = Widgets.button(m, "기준 결제일: " + Store.dateLabel(LocalDate.ofEpochDay(selAnchor[0])), false, null);
        dateBtn.setOnClickListener(v -> {
            LocalDate d = LocalDate.ofEpochDay(selAnchor[0]);
            new DatePickerDialog(m, (dp, y, mo, day) -> {
                LocalDate nd = LocalDate.of(y, mo + 1, day);
                selAnchor[0] = nd.toEpochDay();
                dateBtn.setText("기준 결제일: " + Store.dateLabel(nd));
            }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
        });
        LinearLayout.LayoutParams dblp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dblp.setMargins(0, Ui.dp(8), 0, Ui.dp(8));
        form.addView(dateBtn, dblp);

        // Category chips
        TextView catLbl = Ui.text(m, "카테고리", 13, Ui.semibold, Ui.text2());
        catLbl.setPadding(Ui.dp(4), Ui.dp(4), 0, Ui.dp(4));
        form.addView(catLbl);
        Widgets.Chips catChips = new Widgets.Chips(m, Store.CATEGORIES, selCat[0], idx -> selCat[0] = idx);
        form.addView(catChips, Widgets.MW);

        // Reminder days selector
        String[] remLabels = {"기본값", "당일", "1일 전", "3일 전", "7일 전"};
        int[] remVals = {-1, 0, 1, 3, 7};
        int remIdx = 0;
        for (int i = 0; i < remVals.length; i++) if (remVals[i] == selRemind[0]) remIdx = i;
        TextView remLbl = Ui.text(m, "사전 알림", 13, Ui.semibold, Ui.text2());
        remLbl.setPadding(Ui.dp(4), Ui.dp(6), 0, Ui.dp(4));
        form.addView(remLbl);
        form.addView(new Widgets.Chips(m, remLabels, remIdx, idx -> selRemind[0] = remVals[idx]), Widgets.MW);

        // Free trial toggle
        LinearLayout trialRow = new LinearLayout(m);
        trialRow.setGravity(Gravity.CENTER_VERTICAL);
        trialRow.setPadding(Ui.dp(4), Ui.dp(8), Ui.dp(4), Ui.dp(6));
        trialRow.addView(Ui.text(m, "무료 체험 중 (첫 결제일 알림 강조)", 14.5f, Ui.medium, Ui.text()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Widgets.Toggle trialToggle = new Widgets.Toggle(m, isTrial[0]);
        trialToggle.change = on -> isTrial[0] = on;
        trialRow.addView(trialToggle);
        form.addView(trialRow, Widgets.MW);

        // Color swatches
        TextView colLbl = Ui.text(m, "아이콘 색상", 13, Ui.semibold, Ui.text2());
        colLbl.setPadding(Ui.dp(4), Ui.dp(6), 0, Ui.dp(6));
        form.addView(colLbl);
        HorizontalScrollView swScroll = new HorizontalScrollView(m);
        swScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout swRow = new LinearLayout(m);
        swRow.setOrientation(LinearLayout.HORIZONTAL);
        List<View> swViews = new ArrayList<>();
        for (int col : Store.SWATCHES) {
            View sw = new View(m);
            Runnable styleSw = () -> {
                GradientDrawable gd = Ui.round(col, 16);
                if (selColor[0] == col) gd.setStroke(Ui.dp(3), Ui.text());
                sw.setBackground(gd);
            };
            styleSw.run();
            sw.setTag(styleSw);
            sw.setOnClickListener(v -> {
                selColor[0] = col;
                for (View x : swViews) ((Runnable) x.getTag()).run();
            });
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Ui.dp(30), Ui.dp(30));
            slp.rightMargin = Ui.dp(8);
            swRow.addView(sw, slp);
            swViews.add(sw);
        }
        swScroll.addView(swRow);
        form.addView(swScroll, Widgets.MW);

        EditText payEt = Widgets.formField(m, "결제 수단 (선택)", "예: 신용카드, 카카오페이", work.payment, InputType.TYPE_CLASS_TEXT, form);
        EditText memoEt = Widgets.formField(m, "메모 (선택)", "예: 가족 요금제 4인", work.memo, InputType.TYPE_CLASS_TEXT, form);

        // Fill preset chips
        if (isNew) {
            for (Store.Preset pr : Store.PRESETS) {
                TextView chip = Ui.text(m, pr.name, 13, Ui.semibold, 0xFFFFFFFF);
                chip.setPadding(Ui.dp(12), Ui.dp(7), Ui.dp(12), Ui.dp(7));
                chip.setBackground(Ui.round(pr.color, 100));
                chip.setOnClickListener(v -> {
                    nameEt.setText(pr.name);
                    amtEt.setText(trimNum(pr.amount));
                    selCur[0] = indexOf(Store.CURRENCIES, pr.currency);
                    selCycle[0] = pr.cycle;
                    selCat[0] = indexOf(Store.CATEGORIES, pr.category);
                    selColor[0] = pr.color;
                    for (View x : swViews) ((Runnable) x.getTag()).run();
                    Toast.makeText(m, pr.name + " 기본값을 채웠어요. 실제 금액을 확인하세요.", Toast.LENGTH_SHORT).show();
                });
                Ui.press(chip, 0.94f);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.rightMargin = Ui.dp(6);
                presetRow.addView(chip, clp);
            }
        }

        Widgets.showFormSheet(m, isNew ? "구독·고정지출 추가" : "구독 편집", form, m.getString(R.string.save), () -> {
            String nm = nameEt.getText().toString().trim();
            if (nm.isEmpty()) { Toast.makeText(m, "이름을 입력해 주세요", Toast.LENGTH_SHORT).show(); return; }
            double a = 0;
            try { a = Double.parseDouble(amtEt.getText().toString().trim().replace(",", "")); } catch (Exception ignored) {}
            if (a <= 0) { Toast.makeText(m, "금액을 올바르게 입력해 주세요", Toast.LENGTH_SHORT).show(); return; }
            work.name = nm;
            work.amount = a;
            work.currency = Store.CURRENCIES[selCur[0]];
            work.cycle = selCycle[0];
            work.every = 1;
            work.anchor = selAnchor[0];
            work.category = Store.CATEGORIES[selCat[0]];
            work.remind = selRemind[0];
            work.trial = isTrial[0];
            work.color = selColor[0];
            work.payment = payEt.getText().toString().trim();
            work.memo = memoEt.getText().toString().trim();
            if (isNew) Store.subs.add(work);
            m.dataChanged();
        });
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }

    private static String trimNum(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}

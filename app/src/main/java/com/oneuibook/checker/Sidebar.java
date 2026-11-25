package com.oneuibook.checker;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Tablet / unfolded sidebar: app name, search pill, 4 tabs, live month summary and upcoming payments. */
final class Sidebar extends FrameLayout {
    private final MainActivity m;
    private final List<LinearLayout> tabRows = new ArrayList<>();
    private final LinearLayout extra;

    static float widthFor(int W) { return Ui.clamp(W * 0.27f, Ui.dpf(240), Ui.dpf(320)); }

    Sidebar(MainActivity m) {
        super(m);
        this.m = m;
        setBackgroundColor(Ui.gStyle ? Ui.groupedBg() : (Ui.dark ? 0xFF161618 : 0xFFE9E9EE));
        setClickable(true);
        ScrollView sv = new ScrollView(m);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(OVER_SCROLL_NEVER);
        addView(sv, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout col = new LinearLayout(m);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(12), Ui.statusInset + Ui.dp(12), Ui.dp(12), m.bottomPadding());
        sv.addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = new LinearLayout(m);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(8), 0, 0, Ui.dp(14));
        top.addView(Ui.text(m, m.getString(R.string.app_name), 21, Ui.bold, Ui.text()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Icons.IconView close = new Icons.IconView(m, Icons.SIDEBAR, 22, Ui.text());
        GradientDrawable cb = Ui.round(Ui.card(), 100);
        cb.setStroke(Ui.dp(1), Ui.dark ? 0x33FFFFFF : 0x1F000000);
        close.setBackground(cb);
        close.setOnClickListener(v -> m.closeSidebar());
        Ui.press(close, 0.9f);
        top.addView(close, new LinearLayout.LayoutParams(Ui.dp(46), Ui.dp(46)));
        col.addView(top, Widgets.MW);

        LinearLayout search = new LinearLayout(m);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(Ui.dp(14), 0, Ui.dp(14), 0);
        search.setBackground(Ui.round(Ui.dark ? 0xFF2C2C2E : 0xFFDCDCE2, 100));
        search.addView(new Icons.IconView(m, Icons.SEARCH, 18, Ui.text2()), new LinearLayout.LayoutParams(Ui.dp(22), Ui.dp(22)));
        TextView hint = Ui.text(m, m.getString(R.string.search), 16, Ui.regular, Ui.text2());
        hint.setPadding(Ui.dp(8), 0, 0, 0);
        search.addView(hint);
        search.setOnClickListener(v -> m.onSearch());
        Ui.press(search, 0.97f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(44));
        slp.bottomMargin = Ui.dp(16);
        col.addView(search, slp);

        int[] titles = {R.string.tab_home, R.string.tab_calendar, R.string.tab_stats, R.string.tab_settings};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout r = row(Dock.TAB_ICONS[i], m.getString(titles[i]), null, v -> m.sidebarTab(idx));
            tabRows.add(r);
            col.addView(r, rowLp());
        }
        LinearLayout add = row(Icons.PLUS, "구독 추가", null, v -> Pages.openEditor(m, null, null));
        col.addView(add, rowLp());

        extra = new LinearLayout(m);
        extra.setOrientation(LinearLayout.VERTICAL);
        col.addView(extra, Widgets.MW);
        refresh();
    }

    void refresh() {
        extra.removeAllViews();
        LocalDate t = Store.today();
        TextView h = Ui.text(getContext(), "이번 달", 13, Ui.medium, Ui.text2());
        h.setPadding(Ui.dp(10), Ui.dp(22), Ui.dp(10), Ui.dp(6));
        extra.addView(h, Widgets.MW);
        extra.addView(row(Icons.WALLET, Store.won(Store.monthCharges(t.getYear(), t.getMonthValue(), false)),
                "확인 " + Store.won(Store.monthCharges(t.getYear(), t.getMonthValue(), true)), v -> m.sidebarTab(0)), rowLp());
        TextView h2 = Ui.text(getContext(), "다가오는 결제", 13, Ui.medium, Ui.text2());
        h2.setPadding(Ui.dp(10), Ui.dp(16), Ui.dp(10), Ui.dp(6));
        extra.addView(h2, Widgets.MW);
        int n = 0;
        for (Store.Due d : Store.dues(t, t.plusDays(30))) {
            if (d.paid()) continue;
            final Store.Sub s = d.sub;
            LinearLayout r = row(0, s.name, Store.relDay(d.date) + " · " + Store.amount(s), v -> Pages.openDetail(m, s, null));
            r.addView(Pages.avatar(getContext(), s, 26), 0, new LinearLayout.LayoutParams(Ui.dp(26), Ui.dp(26)));
            extra.addView(r, rowLp());
            if (++n >= 5) break;
        }
        if (n == 0) {
            TextView e = Ui.text(getContext(), "30일 안에 예정된 결제가 없어요", 14, Ui.regular, Ui.text3());
            e.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
            extra.addView(e, Widgets.MW);
        }
    }

    private static LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(48));
        lp.bottomMargin = Ui.dp(2);
        return lp;
    }

    private LinearLayout row(int icon, String title, String sub, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(getContext());
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(12), 0, Ui.dp(12), 0);
        if (icon != 0) r.addView(new Icons.IconView(getContext(), icon, 21, Ui.ACCENT), new LinearLayout.LayoutParams(Ui.dp(26), Ui.dp(26)));
        LinearLayout tx = new LinearLayout(getContext());
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(Ui.dp(14), 0, 0, 0);
        TextView t = Ui.text(getContext(), title, sub == null ? 17 : 15, Ui.regular, Ui.text());
        tx.addView(t);
        if (sub != null) tx.addView(Ui.text(getContext(), sub, 12.5f, Ui.regular, Ui.text2()));
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.setOnClickListener(click);
        r.setBackground(new RippleDrawable(ColorStateList.valueOf(Ui.pressed()), null, Ui.round(0xFFFFFFFF, 100)));
        return r;
    }

    void setSelected(int tab) {
        for (int i = 0; i < tabRows.size(); i++) {
            LinearLayout r = tabRows.get(i);
            boolean on = i == tab;
            int selCol = Ui.gStyle ? Ui.alpha(Ui.ACCENT, Ui.dark ? 0.22f : 0.13f) : (Ui.dark ? 0x33FFFFFF : 0x17000000);
            Drawable base = on ? Ui.round(selCol, 100) : null;
            r.setBackground(new RippleDrawable(ColorStateList.valueOf(Ui.pressed()), base, Ui.round(0xFFFFFFFF, 100)));
            TextView t = (TextView) ((LinearLayout) r.getChildAt(1)).getChildAt(0);
            t.setTypeface(on ? Ui.semibold : Ui.regular);
            t.setTextColor(on && Ui.gStyle ? Ui.ACCENT : Ui.text());
        }
    }
}

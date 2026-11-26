package com.oneuibook.checker;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 체커 — subscription & fixed-expense reminder.
 * Glass chrome (header pill, dock / rail / tablet tab pill, sidebar) shared with Play/Prism,
 * phone · unfolded foldable · tablet responsive layouts.
 */
public final class MainActivity extends Activity implements Dock.Listener, Header.Listener {
    static final String VERSION = "9.5.2";

    private static final int REQ_NOTIF = 3001, REQ_EXPORT = 3002, REQ_IMPORT = 3003;

    FrameLayout root;
    Glass.Backdrop backdrop;
    private FrameLayout pageHost;
    Header header;
    Dock dock;

    private final NavStack[] tabs = new NavStack[4];
    int tab;
    private Pages.SearchPage searchPage;
    private Sidebar sidebar;
    private float sidebarP;
    private static boolean sidebarWanted;

    static SharedPreferences prefs(Context c) { return Store.prefs(c); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences p = prefs(this);
        Ui.init(this, p);
        Store.load(this);

        Window w = getWindow();
        w.setDecorFitsSystemWindows(false);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.setNavigationBarContrastEnforced(false);
        w.getDecorView().setBackgroundColor(Ui.groupedBg());

        root = new FrameLayout(this);
        backdrop = new Glass.Backdrop(this);
        backdrop.setBackgroundColor(Ui.groupedBg());
        root.addView(backdrop, match());
        pageHost = new FrameLayout(this);
        backdrop.addView(pageHost, match());

        header = new Header(this, backdrop);
        header.setListener(this);
        root.addView(header, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, headerHeight()));
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitHeader());

        dock = new Dock(this, backdrop, new String[]{
                getString(R.string.tab_home), getString(R.string.tab_calendar),
                getString(R.string.tab_stats), getString(R.string.tab_settings)});
        dock.setListener(this);
        root.addView(dock, match());
        setContentView(root);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets sys = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            int ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
            boolean changed = sys.top != Ui.statusInset || sys.bottom != Ui.navInset;
            Ui.statusInset = sys.top;
            Ui.navInset = sys.bottom;
            Ui.cutoutLeft = sys.left;
            Ui.cutoutRight = sys.right;
            Ui.imeInset = ime;
            dock.setImeOffset(ime);
            fitHeader();
            if (changed) {
                for (NavStack s : tabs) if (s != null) s.applyInsets();
                if (searchPage != null) searchPage.applyInsets();
            }
            return WindowInsets.CONSUMED;
        });

        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                updateScrollChrome(currentPage());
                boolean dirty = backdrop.isDirty();
                if (dirty || header.animating()) header.invalidate();
                if (dirty || dock.animating()) dock.invalidate();
                for (int i = 0; i < root.getChildCount(); i++) {
                    View c = root.getChildAt(i);
                    if (c instanceof GlassMenu) c.invalidate();
                }
                return true;
            }
        });

        tabs[0] = new NavStack(new Pages.HomePage(this));
        tabs[1] = new NavStack(new Pages.CalendarPage(this));
        tabs[2] = new NavStack(new Pages.StatsPage(this));
        tabs[3] = new NavStack(new Settings.SettingsPage(this));
        for (NavStack s : tabs) { s.setVisibility(View.GONE); pageHost.addView(s, match()); }
        tab = state == null ? 0 : state.getInt("tab", 0);
        tabs[tab].setVisibility(View.VISIBLE);
        tabs[tab].top().show();
        dock.select(tab, false);
        syncHeader();
        setLightStatus(!Ui.dark);

        Reminder.ensureChannel(this);
        Reminder.schedule(this);
        Reminder.catchUp(this);
        CheckerWidget.updateAll(this);
        if (state == null && Build.VERSION.SDK_INT >= 33 && Store.notify
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !p.getBoolean("asked_notif", false)) {
            p.edit().putBoolean("asked_notif", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        if (sidebarWanted) root.post(() -> { if (tabletUi()) openSidebar(false); });
        handleIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent i) {
        if (i == null) return;
        String id = i.getStringExtra("sub_id");
        if (id == null) return;
        Store.Sub s = Store.find(id);
        if (s != null) root.post(() -> Pages.openDetail(this, s, null));
    }

    @Override protected void onResume() {
        super.onResume();
        // the notification action may have checked a payment while we were away
        Store.load(this);
        reloadAll();
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        Ui.init(this, prefs(this));
        fitHeader();
        dock.requestLayout();
        for (NavStack s : tabs) if (s != null) { s.applyInsets(); s.rebuildAll(); }
        if (tabletUi() && sidebarWanted && sidebar == null) openSidebar(false);
        else if (!tabletUi() && sidebar != null) closeSidebar();
    }

    void setLightStatus(boolean light) {
        WindowInsetsController ctrl = getWindow().getInsetsController();
        if (ctrl == null) return;
        int flag = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        ctrl.setSystemBarsAppearance(light ? flag : 0, flag);
    }

    // ---- Responsive helpers ----
    int rootW() { return root != null && root.getWidth() > 0 ? root.getWidth() : Ui.screenW; }
    int rootH() { return root != null && root.getHeight() > 0 ? root.getHeight() : Ui.screenH; }
    boolean tabletUi() { return Dock.tabletFor(rootW(), rootH()); }
    boolean railUi() { return Dock.railFor(rootW(), rootH()); }
    boolean wide() { return tabletUi() || railUi(); }
    int sideInset() { return railUi() ? Ui.dp(92) : 0; }
    int contentInsetPx() { return sidebar != null ? Math.round(Sidebar.widthFor(rootW()) * sidebarP) : 0; }
    int bottomPadding() { return Ui.navInset + Ui.dp(tabletUi() ? 92 : 148); }
    int contentWidth() { return Math.max(Ui.dp(280), rootW() - sideInset() - contentInsetPx()); }

    private int headerHeight() { return Ui.statusInset + Ui.dp(railUi() ? 172 : 88); }

    private void fitHeader() {
        if (header == null) return;
        int h = headerHeight();
        if (header.getLayoutParams().height == h) return;
        header.getLayoutParams().height = h;
        header.post(header::requestLayout);
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    // ---- Sidebar ----
    void openSidebar(boolean animate) {
        if (!tabletUi()) return;
        sidebarWanted = true;
        if (sidebar == null) {
            sidebar = new Sidebar(this);
            sidebar.setSelected(tab);
            root.addView(sidebar, root.indexOfChild(dock), new FrameLayout.LayoutParams(Math.round(Sidebar.widthFor(rootW())), ViewGroup.LayoutParams.MATCH_PARENT));
        }
        float w = Sidebar.widthFor(rootW());
        if (animate) Ui.animate(sidebarP, 1f, 320, Ui.EMPHASIZED, a -> applySidebar((float) a.getAnimatedValue(), w));
        else applySidebar(1f, w);
    }

    void closeSidebar() {
        sidebarWanted = false;
        if (sidebar == null) return;
        float w = Sidebar.widthFor(rootW());
        Ui.animate(sidebarP, 0f, 260, Ui.EMPHASIZED, a -> {
            float v = (float) a.getAnimatedValue();
            applySidebar(v, w);
            if (v <= 0.01f && sidebar != null) { root.removeView(sidebar); sidebar = null; }
        });
    }

    private void applySidebar(float p, float w) {
        sidebarP = p;
        if (sidebar != null) sidebar.setTranslationX(-w * (1f - p));
        pageHost.setPadding(Math.round(w * p), 0, 0, 0);
        header.setPadding(Math.round(w * p), 0, 0, 0);
        dock.setSidebar(w, p);
    }

    void sidebarTab(int idx) { dock.select(idx, true); onTab(idx, idx == tab); }

    // ---- Data ----
    /** Persist, reschedule, refresh widgets and rebuild every page. */
    void dataChanged() {
        Store.save(this);
        reloadAll();
        if (sidebar != null) sidebar.refresh();
    }

    void reloadAll() {
        for (NavStack s : tabs) if (s != null) s.rebuildAll();
        if (searchPage != null && searchPage.getVisibility() == View.VISIBLE) searchPage.rebuild();
    }

    void refreshAppearance() {
        getWindow().getDecorView().setBackgroundColor(Ui.groupedBg());
        backdrop.setBackgroundColor(Ui.groupedBg());
        setLightStatus(!Ui.dark);
        if (sidebar != null) { root.removeView(sidebar); sidebar = null; if (sidebarWanted) openSidebar(false); }
        reloadAll();
        header.invalidate();
        dock.requestLayout();
        dock.invalidate();
    }

    void push(Page page) { tabs[tab].push(page); syncHeader(); }

    Page currentPage() {
        return searchPage != null && searchPage.getVisibility() == View.VISIBLE ? searchPage : tabs[tab].top();
    }

    void syncHeader() {
        Page p = currentPage();
        header.configure(p.title, p.headerIcons, p.back);
        updateScrollChrome(p);
    }

    void onPageScroll(Page p, int y, int dy) {
        if (p != currentPage()) return;
        updateScrollChrome(p);
        if (y <= Ui.dp(8)) dock.setCollapsed(false);
        else if (dy > Ui.dp(10) && y > Ui.dp(36)) dock.setCollapsed(true);
        else if (dy < -Ui.dp(10)) dock.setCollapsed(false);
    }

    private void updateScrollChrome(Page p) {
        if (p != null) header.setScroll(p.titleProgress(), p.blurProgress());
    }

    // ---- Dock.Listener ----
    @Override public void onTab(int index, boolean reselect) {
        if (searchPage != null && searchPage.getVisibility() == View.VISIBLE) onExitSearch();
        if (reselect) {
            if (tabs[tab].canPop()) tabs[tab].popToRoot(); else tabs[tab].top().scrollToTop();
            syncHeader();
            return;
        }
        tabs[tab].setVisibility(View.GONE);
        tab = index;
        tabs[tab].setVisibility(View.VISIBLE);
        tabs[tab].top().show();
        if (sidebar != null) sidebar.setSelected(tab);
        syncHeader();
    }

    @Override public void onMini() {}

    @Override public void onSearch() {
        if (searchPage == null) {
            searchPage = new Pages.SearchPage(this);
            pageHost.addView(searchPage, match());
        }
        searchPage.setVisibility(View.VISIBLE);
        searchPage.show();
        dock.setSearchMode(true);
        syncHeader();
    }

    @Override public void onExitSearch() {
        if (searchPage != null) searchPage.setVisibility(View.GONE);
        dock.setSearchMode(false);
        syncHeader();
    }

    @Override public void onQuery(String q, boolean submit) { if (searchPage != null) searchPage.setQuery(q); }

    @Override public void onSidebar() { if (sidebar == null) openSidebar(true); else closeSidebar(); }

    // ---- Header.Listener ----
    @Override public void onHeaderButton(int icon, RectF anchor) {
        if (icon == Icons.PLUS) {
            Pages.openEditor(this, null, anchor);
        } else if (icon == Icons.SORT || icon == Icons.MORE) {
            List<GlassMenu.Entry> es = new ArrayList<>();
            String[] labels = {"다음 결제순", "금액 높은 순", "이름순"};
            for (int i = 0; i < labels.length; i++) {
                final int mode = i;
                GlassMenu.Entry e = new GlassMenu.Entry(labels[i], 0, Store.sortMode == mode, () -> { Store.sortMode = mode; dataChanged(); });
                e.checkable = true;
                es.add(e);
            }
            GlassMenu.Entry sp = new GlassMenu.Entry("일시정지 항목 표시", 0, Store.showPaused, () -> { Store.showPaused = !Store.showPaused; dataChanged(); });
            sp.checkable = true;
            es.add(sp);
            GlassMenu.show(this, anchor, es);
        } else if (icon == Icons.SHARE) {
            exportData();
        } else if (icon == Icons.CALENDAR) {
            Page cur = currentPage();
            if (cur instanceof Pages.CalendarPage) ((Pages.CalendarPage) cur).goToday();
        }
    }

    @Override public void onBack() { onBackPressed(); }

    @Override public void onBackPressed() {
        if (GlassMenu.dismissAny(root)) return;
        if (searchPage != null && searchPage.getVisibility() == View.VISIBLE) { onExitSearch(); return; }
        if (tabs[tab].canPop()) { tabs[tab].pop(); syncHeader(); return; }
        super.onBackPressed();
    }

    // ---- Import / export (SAF) ----
    void exportData() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "checker-backup-" + Store.today() + ".json");
        startActivityForResult(i, REQ_EXPORT);
    }

    void importData() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_IMPORT);
    }

    void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
    }

    boolean notificationsAllowed() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (req == REQ_EXPORT) {
                try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
                    os.write(Store.exportJson().getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "백업 파일을 저장했어요", Toast.LENGTH_SHORT).show();
            } else if (req == REQ_IMPORT) {
                StringBuilder sb = new StringBuilder();
                try (InputStream is = getContentResolver().openInputStream(uri);
                     BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line).append('\n');
                }
                int n = Store.importJson(sb.toString());
                dataChanged();
                Toast.makeText(this, n + "개 항목을 가져왔어요", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "파일을 처리하지 못했어요: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_NOTIF) reloadAll();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    // ---- Navigation stack per tab ----
    final class NavStack extends FrameLayout {
        private final List<Page> stack = new ArrayList<>();

        NavStack(Page rootPage) {
            super(MainActivity.this);
            stack.add(rootPage);
            addView(rootPage, match());
        }

        Page top() { return stack.get(stack.size() - 1); }
        boolean canPop() { return stack.size() > 1; }

        void push(Page p) {
            Page prev = top();
            stack.add(p);
            addView(p, match());
            p.show();
            p.setAlpha(0f);
            p.setTranslationX(Ui.dpf(42));
            p.animate().alpha(1f).translationX(0f).setDuration(300).setInterpolator(Ui.EMPHASIZED).start();
            prev.animate().alpha(0.3f).translationX(-Ui.dpf(24)).setDuration(300).setInterpolator(Ui.EMPHASIZED)
                    .withEndAction(() -> prev.setVisibility(View.GONE)).start();
        }

        void pop() {
            if (stack.size() <= 1) return;
            Page cur = stack.remove(stack.size() - 1);
            Page prev = top();
            prev.setVisibility(View.VISIBLE);
            prev.animate().alpha(1f).translationX(0f).setDuration(260).setInterpolator(Ui.EMPHASIZED).start();
            cur.animate().alpha(0f).translationX(Ui.dpf(42)).setDuration(240).setInterpolator(Ui.EMPHASIZED)
                    .withEndAction(() -> removeView(cur)).start();
        }

        void popToRoot() {
            while (stack.size() > 1) removeView(stack.remove(stack.size() - 1));
            Page r = top();
            r.setVisibility(View.VISIBLE);
            r.setAlpha(1f);
            r.setTranslationX(0f);
        }

        void applyInsets() { for (Page p : stack) p.applyInsets(); }
        void rebuildAll() { for (Page p : stack) p.rebuild(); }
    }
}

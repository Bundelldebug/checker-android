package com.oneuibook.checker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.RemoteViews;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class CheckerUiTest {
    private File outDir;

    @Before public void setup() {
        outDir = new File(System.getProperty("checker.output", "."));
        outDir.mkdirs();
        Store.today = LocalDate.of(2026, 11, 25);
        Store.subs.clear();
        Store.paid.clear();
        Store.usd = 1400; Store.jpy = 9.4; Store.eur = 1500;
        Store.defaultRemind = 1; Store.remindHour = 9; Store.notify = true;

        int[] idxs = {0, 2, 3, 5, 7, 11};
        int[] offsets = {0, 1, 3, 5, 10, 18};
        for (int i = 0; i < idxs.length; i++) {
            Store.Sub s = Store.PRESETS[idxs[i]].make();
            s.anchor = Store.today.plusDays(offsets[i]).toEpochDay();
            if (i == 3) s.trial = true;
            Store.subs.add(s);
        }
        // Mark one payment already checked this month
        Store.Sub past = Store.PRESETS[8].make();
        past.anchor = LocalDate.of(2026, 11, 5).toEpochDay();
        Store.subs.add(past);
        Store.paid.add(past.id + "@" + LocalDate.of(2026, 11, 5).toEpochDay());
        Store.persist(RuntimeEnvironment.getApplication());
    }

    private void saveShot(View v, int w, int h, String name) throws Exception {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        v.draw(c);
        try (FileOutputStream fos = new FileOutputStream(new File(outDir, name))) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        bmp.recycle();
    }

    @Test public void storeMathAndBackup() throws Exception {
        Store.Sub u = Store.subs.get(2); // ChatGPT Plus $20
        assertEquals(28000.0, Store.krw(u), 0.1);
        assertEquals(28000.0, Store.monthly(u), 0.1);

        Store.Sub yr = Store.subs.get(5); // Microsoft 365 119000/yr
        assertEquals(119000.0 / 12.0, Store.monthly(yr), 0.1);

        List<Store.Due> dues = Store.dues(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 12, 10));
        assertTrue(dues.size() >= 5);
        assertEquals("오늘", Store.relDay(Store.today));
        assertEquals("내일", Store.relDay(Store.today.plusDays(1)));

        String json = Store.exportJson();
        int orig = Store.subs.size();
        Store.subs.clear();
        assertEquals(orig, Store.importJson(json));
        assertEquals(orig, Store.subs.size());

        // Real-time FX JSON parsing (open.er-api.com / frankfurter.app format)
        double[] fx = Store.parseRatesJson("{\"rates\":{\"USD\":0.0007142857,\"JPY\":0.105263,\"EUR\":0.0006666667}}");
        assertNotNull(fx);
        assertEquals(1400.0, fx[0], 1.0);
        assertEquals(9.5, fx[1], 0.2);
        assertEquals(1500.0, fx[2], 1.0);

        // Multi-currency preset prices (USD / JPY / EUR)
        Store.Preset nf = Store.PRESETS[0]; // Netflix
        assertEquals(13500.0, nf.priceFor("KRW"), 0.01);
        assertEquals(15.49, nf.priceFor("USD"), 0.01);
        assertEquals(1590.0, nf.priceFor("JPY"), 0.01);
        assertEquals(13.99, nf.priceFor("EUR"), 0.01);

        // Bidirectional sync merge
        long prevTs = Store.updatedAt;
        Store.updatedAt = 1000L;
        String newerJson = json.replace("\"updated_at\":", "\"updated_at\": 9999999999999, \"_old\":");
        assertTrue(Store.mergeSyncJson(newerJson));
        Store.updatedAt = prevTs;

        long next = Reminder.nextTrigger(LocalDateTime.of(2026, 11, 25, 10, 0), 9);
        assertTrue(next > 0);
        assertTrue(Reminder.title(dues.get(0), 0).contains("오늘"));

        Context ctx = RuntimeEnvironment.getApplication();
        RemoteViews rv = CheckerWidget.build(ctx);
        assertNotNull(rv);
    }

    @Test public void launcherIconRenders() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        Drawable icon = ctx.getDrawable(R.mipmap.ic_launcher);
        assertNotNull(icon);
        Bitmap bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        android.graphics.Path sq = new android.graphics.Path();
        float r = 122f, cx = 128f, cy = 128f, n = 2.76f;
        for (int i = 0; i <= 120; i++) {
            double a = i * Math.PI * 2.0 / 120.0;
            double ca = Math.cos(a), sa = Math.sin(a);
            float x = cx + (float) (Math.signum(ca) * Math.pow(Math.abs(ca), 2.0 / n) * r);
            float y = cy + (float) (Math.signum(sa) * Math.pow(Math.abs(sa), 2.0 / n) * r);
            if (i == 0) sq.moveTo(x, y); else sq.lineTo(x, y);
        }
        sq.close();
        c.clipPath(sq);
        icon.setBounds(0, 0, 256, 256);
        icon.draw(c);
        try (FileOutputStream fos = new FileOutputStream(new File(outDir, "icon.png"))) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        bmp.recycle();
    }

    @Test @Config(qualifiers = "ko-rKR-w412dp-h915dp-xxhdpi")
    public void phoneScreens() throws Exception {
        ActivityController<MainActivity> ctrl = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity act = ctrl.get();
        assertEquals("체커", act.getString(R.string.app_name));
        assertEquals("9.5.5", MainActivity.VERSION);
        saveShot(act.root, 1236, 2745, "phone-home.png");

        act.dock.select(1, false);
        act.onTab(1, false);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(350, java.util.concurrent.TimeUnit.MILLISECONDS);
        saveShot(act.root, 1236, 2745, "phone-calendar.png");

        // Back button on non-home tab returns to Tab 0 instead of exiting
        act.onBackPressed();
        assertEquals(0, act.tab);
        assertFalse(act.isFinishing());

        act.dock.select(2, false);
        act.onTab(2, false);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(350, java.util.concurrent.TimeUnit.MILLISECONDS);
        saveShot(act.root, 1236, 2745, "phone-stats.png");

        act.dock.select(3, false);
        act.onTab(3, false);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(350, java.util.concurrent.TimeUnit.MILLISECONDS);
        saveShot(act.root, 1236, 2745, "phone-settings.png");
    }

    @Test @Config(qualifiers = "ko-rKR-w720dp-h760dp-xxhdpi")
    public void foldableRail() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        MainActivity.prefs(ctx).edit().putString("theme", "dark").putString("ui_style", "oneui").apply();
        ActivityController<MainActivity> ctrl = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity act = ctrl.get();
        assertTrue(act.railUi());
        saveShot(act.root, 2160, 2280, "foldable-dark.png");
    }

    @Test @Config(qualifiers = "ko-rKR-w1100dp-h720dp-xhdpi")
    public void tabletSidebar() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        MainActivity.prefs(ctx).edit().putString("theme", "light").putString("ui_style", "prism").apply();
        ActivityController<MainActivity> ctrl = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity act = ctrl.get();
        assertTrue(act.tabletUi());
        act.openSidebar(false);
        saveShot(act.root, 2200, 1440, "tablet-sidebar.png");
    }
}

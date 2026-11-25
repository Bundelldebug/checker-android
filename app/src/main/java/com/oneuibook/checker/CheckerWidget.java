package com.oneuibook.checker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.util.List;

/** Home-screen widget: this month's fixed spend, checked progress and the next two payments. */
public final class CheckerWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        Store.load(c);
        for (int id : ids) m.updateAppWidget(id, build(c));
    }

    static void updateAll(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            if (m == null) return;
            int[] ids = m.getAppWidgetIds(new ComponentName(c, CheckerWidget.class));
            if (ids == null || ids.length == 0) return;
            RemoteViews v = build(c);
            for (int id : ids) m.updateAppWidget(id, v);
        } catch (Throwable ignored) {}
    }

    static String line(Store.Due d) {
        return d.sub.name + " · " + Store.relDay(d.date) + " · " + Store.amount(d.sub);
    }

    static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_checker);
        LocalDate t = Store.today();
        double month = Store.monthCharges(t.getYear(), t.getMonthValue(), false);
        double paid = Store.monthCharges(t.getYear(), t.getMonthValue(), true);
        v.setTextViewText(R.id.w_total, Store.won(month));
        v.setTextViewText(R.id.w_sub, c.getString(R.string.w_checked, Store.won(paid)));
        v.setProgressBar(R.id.w_progress, 1000, month <= 0 ? 0 : (int) Math.round(paid / month * 1000), false);
        List<Store.Due> next = Store.dues(t, t.plusDays(60));
        int shown = 0;
        String a = c.getString(R.string.w_empty), b = "";
        for (Store.Due d : next) {
            if (d.paid()) continue;
            if (shown == 0) a = line(d); else if (shown == 1) b = line(d);
            if (++shown >= 2) break;
        }
        v.setTextViewText(R.id.w_next1, a);
        v.setTextViewText(R.id.w_next2, b);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 7, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        return v;
    }
}

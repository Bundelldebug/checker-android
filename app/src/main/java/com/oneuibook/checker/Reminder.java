package com.oneuibook.checker;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Daily billing check. An inexact while-idle alarm wakes the app once a day at the chosen hour,
 * posts "N일 전" / "오늘" payment notifications with a "결제 확인" action, and reschedules itself.
 * No exact-alarm permission is needed.
 */
public final class Reminder extends BroadcastReceiver {
    static final String CHANNEL = "billing";
    static final String ACT_CHECK = "com.oneuibook.checker.CHECK";
    static final String ACT_PAID = "com.oneuibook.checker.PAID";

    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction() == null ? "" : i.getAction();
        Store.load(c);
        if (ACT_PAID.equals(a)) {
            String key = i.getStringExtra("key");
            if (key != null) {
                Store.paid.add(key);
                Store.persist(c);
                CheckerWidget.updateAll(c);
            }
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(i.getIntExtra("nid", 0));
            return;
        }
        if (ACT_CHECK.equals(a)) {
            notifyDue(c);
        } else {
            // boot / update / time change: catch up if today's check was missed
            catchUp(c);
        }
        schedule(c);
        CheckerWidget.updateAll(c);
    }

    static long nextTrigger(LocalDateTime now, int hour) {
        LocalDateTime t = now.toLocalDate().atTime(hour, 0);
        if (!t.isAfter(now)) t = t.plusDays(1);
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        Intent i = new Intent(c, Reminder.class).setAction(ACT_CHECK);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        if (!Store.notify) { am.cancel(pi); return; }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger(LocalDateTime.now(), Store.remindHour), pi);
    }

    static void catchUp(Context c) {
        if (!Store.notify) return;
        if (android.os.Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        long last = Store.prefs(c).getLong("last_check", -1);
        if (last < Store.today().toEpochDay() && LocalDateTime.now().getHour() >= Store.remindHour) notifyDue(c);
    }

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, c.getString(R.string.channel_billing), NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(c.getString(R.string.channel_billing_desc));
        nm.createNotificationChannel(ch);
    }

    /** Returns how many notifications were posted. */
    static int notifyDue(Context c) {
        Store.prefs(c).edit().putLong("last_check", Store.today().toEpochDay()).apply();
        if (!Store.notify) return 0;
        LocalDate today = Store.today();
        List<Store.Due> list = Store.dues(today, today.plusDays(31));
        int n = 0;
        for (Store.Due d : list) {
            if (d.paid()) continue;
            long days = ChronoUnit.DAYS.between(today, d.date);
            int before = Store.remindDays(d.sub);
            if (days == 0 || days == before) {
                post(c, d, days);
                n++;
            }
        }
        return n;
    }

    static String title(Store.Due d, long days) {
        String when = days == 0 ? "오늘" : days == 1 ? "내일" : days + "일 후";
        return when + " " + d.sub.name + (d.trialEnd() ? " 무료 체험 종료" : " 결제");
    }

    static void post(Context c, Store.Due d, long days) {
        ensureChannel(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        int nid = d.key().hashCode();
        Intent open = new Intent(c, MainActivity.class).putExtra("sub_id", d.sub.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent po = PendingIntent.getActivity(c, nid, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent paid = new Intent(c, Reminder.class).setAction(ACT_PAID).putExtra("key", d.key()).putExtra("nid", nid);
        PendingIntent pp = PendingIntent.getBroadcast(c, nid, paid, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = Store.amount(d.sub) + (d.sub.currency.equals("KRW") ? "" : " (약 " + Store.won(Store.krw(d.sub)) + ")")
                + " · " + Store.dateLabel(d.date) + (d.sub.payment.isEmpty() ? "" : " · " + d.sub.payment);
        Notification nb = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_checker)
                .setContentTitle(title(d, days))
                .setContentText(text)
                .setColor(d.sub.color)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(po)
                .addAction(new Notification.Action.Builder(null, c.getString(R.string.mark_paid), pp).build())
                .build();
        try { nm.notify(nid, nb); } catch (SecurityException ignored) {}
    }
}

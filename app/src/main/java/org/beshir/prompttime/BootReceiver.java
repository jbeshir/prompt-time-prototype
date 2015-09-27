package org.beshir.prompttime;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.content.WakefulBroadcastReceiver;

public class BootReceiver extends WakefulBroadcastReceiver {
    public BootReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        // Load our alarm active times.
        ActiveTimesStorage activeTimesStorage = new ActiveTimesStorage(
                context.getFilesDir().getPath() + "active_times",
                context.getFilesDir().getPath() + "next_prompt_time");
        activeTimesStorage.load();
        CountDownState countDownState = new CountDownState();
        countDownState.setStorage(activeTimesStorage);

        // Schedule a prompt alarm appropriately.
        long currentTime = System.currentTimeMillis() / 1000;
        countDownState.updateActiveTimeState(currentTime);
        long nextEventTime = countDownState.getNextEvent(currentTime);
        boolean nextEventPrompt = countDownState.isNextEventPrompt();

        // Play our alarm sound, if we are showing a prompt,
        // and halt it otherwise.
        Intent updateServiceIntent = new Intent(context, AlarmService.class);
        if (nextEventTime == currentTime) {
            updateServiceIntent.putExtra("alarm_state", true);
        } else {
            updateServiceIntent.putExtra("alarm_state", false);
            if (nextEventPrompt) {
                updateServiceIntent.putExtra("next_alarm_time", nextEventTime);
            }
        }
        updateServiceIntent.putExtra("wakeful_boot_broadcast", true);
        startWakefulService(context, updateServiceIntent);

        // If we're waiting for a prompt event, schedule an alarm when it arrives.
        // We cancel any existing alarm either way.
        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, AlarmWakefulReceiver.class);
        PendingIntent alarmPendingIntent = PendingIntent.getBroadcast(context, 0, i, 0);
        alarmManager.cancel(alarmPendingIntent);
        if (nextEventTime != currentTime && nextEventTime != Long.MAX_VALUE && nextEventPrompt) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextEventTime * 1000, alarmPendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, nextEventTime * 1000, alarmPendingIntent);
            }
        }
    }
}

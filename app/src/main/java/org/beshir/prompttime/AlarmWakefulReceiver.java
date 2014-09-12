package org.beshir.prompttime;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

public class AlarmWakefulReceiver extends WakefulBroadcastReceiver {

    public void onReceive(Context context, Intent intent) {

        // Signal our service to start making an alarm.
        Intent service = new Intent(context, AlarmService.class);
        service.putExtra("wakeful_broadcast", true);
        startWakefulService(context, service);
    }
}
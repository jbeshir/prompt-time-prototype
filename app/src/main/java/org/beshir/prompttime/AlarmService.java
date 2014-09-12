package org.beshir.prompttime;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;

public class AlarmService extends Service {

    private Ringtone currentlyPlayingRingtone = null;
    private PowerManager.WakeLock alarmWakeLock;

    public AlarmService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // If we explicitly receive a stop alarm intent, stop the alarm.
        // Otherwise, start it if it isn't playing.
        if (intent != null && !intent.getBooleanExtra("alarm_state", true)) {

            // Stop the alarm.
            if (currentlyPlayingRingtone != null) {
                currentlyPlayingRingtone.stop();
                currentlyPlayingRingtone = null;

                // Unmute music now our notification sound is over.
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                audioManager.setStreamSolo(AudioManager.STREAM_NOTIFICATION, false);

                // Release our wake lock, if we have one.
                if (alarmWakeLock != null) {
                    alarmWakeLock.release();
                }
            }

            // If the intent was from our wakeful broadcast receiver,
            // complete the action.
            if (intent != null && intent.getBooleanExtra("wakeful_broadcast", false)) {
                AlarmWakefulReceiver.completeWakefulIntent(intent);
            }

            // Stop the service.
            stopSelf(startId);

            return START_NOT_STICKY;
        } else {

            // Start the alarm.
            if (currentlyPlayingRingtone == null) {

                // Force the service to stay awake until the alarm stops.
                if (alarmWakeLock == null) {
                    alarmWakeLock = ((PowerManager)getSystemService(Context.POWER_SERVICE)).newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "prompt_time"
                    );
                }
                alarmWakeLock.acquire();

                Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);

                if (alert == null) {
                    // alert is null, using backup
                    alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

                    // I can't see this ever being null (as always have a default notification)
                    // but just in case
                    if (alert == null) {
                        // alert backup is null, using 2nd backup
                        alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                    }
                }

                currentlyPlayingRingtone = RingtoneManager.getRingtone(getApplicationContext(), alert);
                currentlyPlayingRingtone.setStreamType(AudioManager.STREAM_NOTIFICATION);
                currentlyPlayingRingtone.play();

                // Mute music temporarily while our notification sound plays.
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                audioManager.setStreamSolo(AudioManager.STREAM_NOTIFICATION, true);

                // Make ourselves a foreground service, suppressing automatic termination,
                // and providing a message that the user can tap to switch to our prompt.
                Intent i = new Intent(this, PromptTime.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                PendingIntent startAppIntent = PendingIntent.getActivity(this, 0, i, 0);
                NotificationCompat.Builder mBuilder =
                        new NotificationCompat.Builder(this)
                                .setSmallIcon(R.drawable.ic_launcher)
                                .setContentTitle("Prompt Time")
                                .setContentText("Your next prompt has arrived.")
                                .setContentIntent(startAppIntent);
                this.startForeground(1, mBuilder.build());

                // Start up the app so the user has the prompt presented to them.
                Intent appIntent = new Intent();
                appIntent.setClassName("org.beshir.prompttime", "org.beshir.prompttime.PromptTime");
                appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                getApplicationContext().startActivity(appIntent);
            }

            // If the intent was from our wakeful broadcast receiver,
            // complete the action.
            if (intent != null && intent.getBooleanExtra("wakeful_broadcast", false)) {
                AlarmWakefulReceiver.completeWakefulIntent(intent);
            }

            return START_STICKY;
        }
    }
}

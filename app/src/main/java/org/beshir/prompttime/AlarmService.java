package org.beshir.prompttime;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AlarmService extends Service {

    private Ringtone currentlyPlayingRingtone = null;
    private PowerManager.WakeLock alarmWakeLock;
    private AudioFocusChangeListener audioFocusChangeListener = new AudioFocusChangeListener();
    private boolean wantToPlay = false;
    private boolean haveFocus = false;


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

            // Stop requesting audio focus now we no longer want to play an alarm.
            if (wantToPlay) {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                audioManager.abandonAudioFocus(audioFocusChangeListener);
                haveFocus = false;
            }

            // Set that we no longer want to play an alarm, and stop any which is currently playing.
            wantToPlay = false;
            stopAlarm();

            // Release our wake lock, if we have one.
            if (alarmWakeLock != null) {
                alarmWakeLock.release();
            }

            // Show a notification telling the user how long it is until the next prompt,
            // if there is one.
            boolean showingNotification = false;
            long nextAlarmTime = intent.getLongExtra("next_alarm_time", Long.MAX_VALUE);
            if (nextAlarmTime != Long.MAX_VALUE) {
                Intent i = new Intent(this, PromptTime.class);
                PendingIntent startAppIntent = PendingIntent.getActivity(this, 0, i, 0);

                DateFormat sdf = SimpleDateFormat.getDateTimeInstance();
                NotificationCompat.Builder mBuilder =
                        new NotificationCompat.Builder(this)
                                .setSmallIcon(R.drawable.ic_stat_notify)
                                .setContentTitle("Prompt Time")
                                .setContentText("Next prompt is at " + sdf.format(new Date(nextAlarmTime * 1000)))
                                .setOngoing(true)
                                .setContentIntent(startAppIntent);

                this.startForeground(1, mBuilder.build());
                showingNotification = true;
            }

            // If the intent was from our wakeful broadcast receiver,
            // complete the action.
            if (intent.getBooleanExtra("wakeful_broadcast", false)) {
                AlarmWakefulReceiver.completeWakefulIntent(intent);
            }

            // If the intent was from our wakeful boot broadcast receiver,
            // complete the action.
            if (intent.getBooleanExtra("wakeful_boot_broadcast", false)) {
                BootReceiver.completeWakefulIntent(intent);
            }

            if (showingNotification) {
                return START_STICKY;
            } else {
                stopSelf();
                return START_NOT_STICKY;
            }
        } else {

            if (!wantToPlay) {

                // Force the service to stay awake until we no longer want to play the alarm.
                if (alarmWakeLock == null) {
                    alarmWakeLock = ((PowerManager)getSystemService(Context.POWER_SERVICE)).newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "prompt_time"
                    );
                }
                alarmWakeLock.acquire();

                wantToPlay = true;
                startAlarm();

                // Make ourselves a foreground service, suppressing automatic termination,
                // and providing a message that the user can tap to switch to our prompt.
                Intent i = new Intent(this, PromptTime.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                PendingIntent startAppIntent = PendingIntent.getActivity(this, 0, i, 0);
                NotificationCompat.Builder mBuilder =
                        new NotificationCompat.Builder(this)
                                .setSmallIcon(R.drawable.ic_stat_notify)
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

            // If the intent was from our wakeful boot broadcast receiver,
            // complete the action.
            if (intent != null && intent.getBooleanExtra("wakeful_boot_broadcast", false)) {
                BootReceiver.completeWakefulIntent(intent);
            }

            return START_STICKY;
        }
    }

    private void startAlarm() {

        // If we don't currently want to play, don't try to.
        if (!wantToPlay) {
            return;
        }

        // Mute music temporarily while our notification sound plays.
        // Documentation says we should do this before we play.
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (!haveFocus) {
            int focusResult = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                haveFocus = true;
            }
        }

        if (haveFocus) {
            if (currentlyPlayingRingtone == null) {

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
                currentlyPlayingRingtone.setStreamType(AudioManager.STREAM_ALARM);
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioAttributes.Builder builder = new AudioAttributes.Builder();
                    builder.setLegacyStreamType(AudioManager.STREAM_ALARM);
                    builder.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION);
                    builder.setUsage(AudioAttributes.USAGE_ALARM);
                    builder.setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED);
                    currentlyPlayingRingtone.setAudioAttributes(builder.build());
                }

                // Ensure volume is set to maximum.
                // Incorrectly set volume may lead to missed alarms.
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);

                currentlyPlayingRingtone.play();
            }
        } else {

            // Retry after a delay.
            startAlarmAfterDelay();
        }
    }

    private void startAlarmAfterDelay() {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                startAlarm();
            }
        }, 5000);
    }

    private void stopAlarm() {
        // Stop the alarm.
        if (currentlyPlayingRingtone != null) {
            currentlyPlayingRingtone.stop();
            currentlyPlayingRingtone = null;
        }
    }

    private class AudioFocusChangeListener implements AudioManager.OnAudioFocusChangeListener {

        @Override
        public void onAudioFocusChange(int focusChange) {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                    || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                haveFocus = false;
                stopAlarm();

                // Try to get our focus back after a delay.
                // Sometimes while we're alarming, some other app steals focus.
                // This can result in missed alarms if not handled.
                // We delay just so that if some other alarm starts fighting,
                // it doesn't eat too many resources.
                startAlarmAfterDelay();
            }
            if (focusChange == AudioManager.AUDIOFOCUS_GAIN && wantToPlay) {
                startAlarm();
            }
        }
    }
}

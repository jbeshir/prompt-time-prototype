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
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AlarmService extends Service {

    private Ringtone currentlyPlayingRingtone = null;
    private PowerManager.WakeLock alarmWakeLock;
    private AudioFocusChangeListener audioFocusChangeListener = new AudioFocusChangeListener();
    private VolumeTicker volumeTicker;
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

            // Restore alarm volume to what it was before we started wanting to notify the user.
            if (volumeTicker != null) {
                volumeTicker.stop();
                volumeTicker = null;
            }

            // Stop any current vibration pattern for the alarm.
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.cancel();

            // Show a notification telling the user how long it is until the next prompt,
            // if there is one.
            boolean showingNotification = false;
            long nextAlarmTime = intent.getLongExtra("next_alarm_time", Long.MAX_VALUE);
            if (nextAlarmTime != Long.MAX_VALUE) {
                Intent i = new Intent(this, PromptTime.class);
                PendingIntent startAppIntent = PendingIntent.getActivity(this, 0, i, 0);

                NotificationCompat.Builder mBuilder =
                        new NotificationCompat.Builder(this)
                                .setSmallIcon(R.drawable.ic_stat_notify)
                                .setContentTitle("Prompt Time")
                                .setContentText("Next prompt is at " + formatNextPrompt(nextAlarmTime))
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

            // Release our wake lock, if we have one.
            if (alarmWakeLock != null && alarmWakeLock.isHeld()) {
                alarmWakeLock.release();
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
                volumeTicker = new VolumeTicker();
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

    private String formatNextPrompt(long promptTimeSeconds) {
        DateFormat sdf;

        long currentTime = System.currentTimeMillis() / 1000;
        if (promptTimeSeconds - currentTime < 23*60*60) {
            sdf = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT);
        } else {
            sdf = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.SHORT);
        }

        return sdf.format(new Date(promptTimeSeconds * 1000));
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
                    currentlyPlayingRingtone.setAudioAttributes(builder.build());
                }

                // Ensure volume is set to maximum.
                // Incorrectly set volume may lead to missed alarms.
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, volumeTicker.getCurrentVolume(), 0);

                // Make the phone vibrate in brief pulses while the alarm is playing.
                // This draws attention if audio is directed to headphones or similar.
                Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                long[] vibratePattern = {0, 200, 1000};
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioAttributes.Builder builder = new AudioAttributes.Builder();
                    builder.setUsage(AudioAttributes.USAGE_ALARM);
                    vibrator.vibrate(vibratePattern, 0, builder.build());
                } else {
                    vibrator.vibrate(vibratePattern, 0);
                }

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

    private class VolumeTicker {

        private final int MILLISECONDS_TO_MAXIMUM = 60*1000;

        private boolean running = true;
        private int volumeToRestore;
        private float currentVolume;
        private int maximumVolume;

        public VolumeTicker() {
            currentVolume = 1;

            final AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            maximumVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            volumeToRestore = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);

            final int tickSpacingEst = MILLISECONDS_TO_MAXIMUM / maximumVolume;
            final float tickAmount =  tickSpacingEst < 100 ? 100.0f/tickSpacingEst : 1;
            final int tickSpacing =  tickSpacingEst < 100 ? 100 : tickSpacingEst;

            Runnable tick = new Runnable() {
                @Override
                public void run() {

                    if (!running) {
                        return;
                    }

                    if (maximumVolume > currentVolume) {
                        currentVolume += tickAmount;
                    } else {
                        currentVolume = maximumVolume;
                    }
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, getCurrentVolume(), 0);

                    if ((int) (currentVolume + 0.5f) != maximumVolume) {
                        new Handler(Looper.getMainLooper()).postDelayed(this, tickSpacing);
                    }
                }
            };

            currentVolume = tickAmount;
            new Handler(Looper.getMainLooper()).postDelayed(tick, tickSpacing);
        }

        public void stop() {
            running = false;

            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, volumeToRestore, 0);
        }

        public int getCurrentVolume() {
            return (int)(currentVolume + 0.5f);
        }
    }
}

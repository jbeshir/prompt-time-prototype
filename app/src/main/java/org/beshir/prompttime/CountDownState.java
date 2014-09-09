package org.beshir.prompttime;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class CountDownState {

    private ActiveTimesStorage storage;

    private long nextStartTimeSeconds;
    private long nextEndTimeSeconds;
    private boolean inActiveTime;

    private ArrayList<ICountDownChangeListener> listeners =
            new ArrayList<ICountDownChangeListener>();

    public void setStorage(ActiveTimesStorage storage) {
        this.storage = storage;

        updateActiveTimeState(System.currentTimeMillis() / 1000);
    }

    public void updateActiveTimeState(long currentTime) {

        int activeTimeBlockCount = storage.getCount();

        Date now = new Date();

        nextStartTimeSeconds = Long.MAX_VALUE;
        nextEndTimeSeconds = Long.MAX_VALUE;
        long prevStartTimeSeconds = 0L;
        long prevEndTimeSeconds = 0L;

        // Check times yesterday, today, and tomorrow.
        Calendar c = Calendar.getInstance();
        c.setTime(now);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        ArrayList<Long> startTimes = new ArrayList<Long>();
        ArrayList<Long> endTimes = new ArrayList<Long>();

        c.add(Calendar.DAY_OF_MONTH, -1);
        for (int day = -1; day < 2; day++) {

            int dayOfWeek = convertDayOfWeek(c.get(Calendar.DAY_OF_WEEK));
            long dayStart = c.getTimeInMillis() / 1000;

            c.add(Calendar.DAY_OF_MONTH, 1);
            long nextDayStart = c.getTimeInMillis() / 1000;
            c.add(Calendar.DAY_OF_MONTH, -1);

            for (int i = 0; i < activeTimeBlockCount; i++) {

                if (!storage.getDayOfWeek(i, dayOfWeek)) {
                    continue;
                }

                long ourStartTime = 60 * storage.getStartTime(i);
                long ourEndTime = 60 * storage.getEndTime(i);

                startTimes.add(dayStart + ourStartTime);
                if (ourEndTime >= ourStartTime) {
                    endTimes.add(dayStart + ourEndTime);
                } else {
                    endTimes.add(nextDayStart + ourEndTime);
                }
            }

            c.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Figure out the next start time that doesn't coincide with an end time.
        for (long time : startTimes) {

            if (endTimes.contains(time)) {
                continue;
            }

            if (time > currentTime && time < nextStartTimeSeconds) {
                nextStartTimeSeconds = time;
            }
        }

        // Figure out the next end time that doesn't coincide with a start time.
        for (long time : endTimes) {

            if (startTimes.contains(time)) {
                continue;
            }

            if (time > currentTime && time < nextEndTimeSeconds) {
                nextEndTimeSeconds = time;
            }
        }

        // Look for the previous start time, exclusive of this second.
        // It must be inclusive, so if we're exactly on a start time,
        // we don't think that we're not in active time because we
        // don't see an start time after our last end time.
        for (long time : startTimes) {
            if (time <= currentTime && time > prevStartTimeSeconds) {
                prevStartTimeSeconds = time;
            }
        }

        // Look for the previous end time, inclusive of this second.
        // It must be inclusive, so if we're exactly on an end time,
        // we don't think that we're still in active time because we
        // don't see an end time after our last start time.
        for (long time : endTimes) {
            if (time <= currentTime && time > prevEndTimeSeconds) {
                prevEndTimeSeconds = time;
            }
        }

        // We're currently active if the prev start time is closer than the prev end time.
        inActiveTime = prevStartTimeSeconds > prevEndTimeSeconds;

        callListeners(currentTime);
    }

    public void setPromptTimeDelay(long seconds) {
        long currentTime = System.currentTimeMillis() /  1000;
        storage.setNextPromptTime(currentTime + seconds);
        callListeners(currentTime);
    }

    public long getNextEvent(long currentTime) {

        if (nextEndTimeSeconds <= currentTime || nextStartTimeSeconds <= currentTime) {
            updateActiveTimeState(currentTime);
        }

        long nextPromptTimeSeconds = storage.getNextPromptTime();
        if (inActiveTime) {
            if (nextPromptTimeSeconds > nextEndTimeSeconds) {
                return nextEndTimeSeconds;
            } else {
                if (nextPromptTimeSeconds <= currentTime) {
                    return currentTime;
                } else {
                    return nextPromptTimeSeconds;
                }
            }
        } else {
            return nextStartTimeSeconds;
        }
    }

    public boolean isInActiveTime() {
        return inActiveTime;
    }

    public boolean isNextEventPrompt() {
        long nextPromptTimeSeconds = storage.getNextPromptTime();
        return !inActiveTime || nextPromptTimeSeconds < nextEndTimeSeconds;
    }

    public void addListener(ICountDownChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ICountDownChangeListener listener) {
        listeners.remove(listener);
    }

    private void callListeners(long currentTime) {
        for (ICountDownChangeListener listener : listeners) {
            listener.onCountDownChanged(currentTime);
        }
    }

    private int convertDayOfWeek(int calendarDayOfWeek) {
        switch (calendarDayOfWeek) {
            case Calendar.MONDAY:
                return 0;
            case Calendar.TUESDAY:
                return 1;
            case Calendar.WEDNESDAY:
                return 2;
            case Calendar.THURSDAY:
                return 3;
            case Calendar.FRIDAY:
                return 4;
            case Calendar.SATURDAY:
                return 5;
            case Calendar.SUNDAY:
                return 6;
            default:
                throw new RuntimeException("Someone added an extra day to the week.");
        }
    }
}

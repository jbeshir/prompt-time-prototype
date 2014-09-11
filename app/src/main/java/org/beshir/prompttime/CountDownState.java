package org.beshir.prompttime;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

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

        // First, build a set of non-overlapping absolute time blocks,
        // throughout the previous week and next week,
        // by retrieving time blocks in that time period, then merging overlapping blocks.
        Date now = new Date();

        nextStartTimeSeconds = Long.MAX_VALUE;
        nextEndTimeSeconds = Long.MAX_VALUE;
        long prevStartTimeSeconds = 0L;
        long prevEndTimeSeconds = 0L;

        Calendar c = Calendar.getInstance();
        c.setTime(now);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        ArrayList<TimeBlock> timeBlockList = new ArrayList<TimeBlock>();
        c.add(Calendar.DAY_OF_MONTH, -8);
        for (int day = -8; day < 9; day++) {

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

                TimeBlock timeBlock = new TimeBlock();

                timeBlock.startTime = dayStart + ourStartTime;
                if (ourEndTime >= ourStartTime) {
                    timeBlock.endTime = dayStart + ourEndTime;
                } else {
                    timeBlock.endTime = nextDayStart + ourEndTime;
                }

                timeBlockList.add(timeBlock);
            }

            c.add(Calendar.DAY_OF_MONTH, 1);
        }

        // For every time block, look for a time block whose start time is between its
        // start time and end time, inclusive, and merge the two, then repeat.
        // This gets us a list of strictly non-repeating blocks.
        for (int i = 0; i < timeBlockList.size(); i++) {
            TimeBlock currentTimeBlock = timeBlockList.get(i);
            for (int j = i + 1; j < timeBlockList.size(); j++) {
                TimeBlock otherTimeBlock = timeBlockList.get(j);

                // Two time blocks overlap if the start of either is >= the other's start,
                // and <= the other's end.
                boolean overlap = false;
                if (otherTimeBlock.startTime >= currentTimeBlock.startTime
                    && otherTimeBlock.startTime <= currentTimeBlock.endTime) {
                    overlap = true;
                }

                if (currentTimeBlock.startTime >= otherTimeBlock.startTime
                        && currentTimeBlock.startTime <= otherTimeBlock.endTime) {
                    overlap = true;
                }

                // If they overlap, create a single time block with the earlier start time
                // and later end time, remove the other time block, and restart the checks.
                if (overlap) {

                    if (otherTimeBlock.startTime < currentTimeBlock.startTime) {
                        currentTimeBlock.startTime = otherTimeBlock.startTime;
                    }
                    if (otherTimeBlock.endTime > currentTimeBlock.endTime) {
                        currentTimeBlock.endTime = otherTimeBlock.endTime;
                    }
                    timeBlockList.remove(j);

                    // Set to -1 so, after the loop's increment, we start over at 0.
                    i = -1;
                    break;
                }
            }
        }

        // Figure out the next start time that doesn't coincide with an end time.
        for (TimeBlock block : timeBlockList) {

            if (block.startTime > currentTime && block.startTime < nextStartTimeSeconds) {
                nextStartTimeSeconds = block.startTime;
            }
        }

        // Figure out the next end time that doesn't coincide with a start time.
        // If it appears to be over a week away, that means there is no end,
        // because our time blocks repeat weekly,
        // but we will never have a prompt that far out anyway.
        for (TimeBlock block : timeBlockList) {

            if (block.endTime > currentTime && block.endTime < nextEndTimeSeconds) {
                nextEndTimeSeconds = block.endTime;
            }
        }

        // Look for the previous start time, exclusive of this second.
        // It must be inclusive, so if we're exactly on a start time,
        // we don't think that we're not in active time because we
        // don't see an start time after our last end time.
        for (TimeBlock block : timeBlockList) {
            if (block.startTime <= currentTime && block.startTime > prevStartTimeSeconds) {
                prevStartTimeSeconds = block.startTime;
            }
        }

        // Look for the previous end time, inclusive of this second.
        // It must be inclusive, so if we're exactly on an end time,
        // we don't think that we're still in active time because we
        // don't see an end time after our last start time.
        for (TimeBlock block : timeBlockList) {
            if (block.endTime <= currentTime && block.endTime > prevEndTimeSeconds) {
                prevEndTimeSeconds = block.endTime;
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

    private static class TimeBlock
    {
        public long startTime;
        public long endTime;
    }
}

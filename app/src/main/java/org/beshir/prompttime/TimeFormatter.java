package org.beshir.prompttime;

public class TimeFormatter {

    public static String formatDuration(long seconds) {
        if (seconds > 60*60*24*2) {
            return (seconds / (60*60*24)) + " days";
        }
        if (seconds > 60*60*24) {
            return "1 day";
        }
        if (seconds > 60*60) {
            return (seconds / (60*60)) + ":"
                    + String.format("%02d", (seconds % (60*60)) / 60) + ":"
                    + String.format("%02d", seconds % 60);
        }
        if (seconds > 60) {
            return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
        }

        return Long.toString(seconds);
    }
}

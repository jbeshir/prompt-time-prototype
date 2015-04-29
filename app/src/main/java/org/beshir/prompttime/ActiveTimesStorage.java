package org.beshir.prompttime;

import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;

public class ActiveTimesStorage {

    private final String activeTimesPath;
    private final String nextPromptTimePath;

    private JSONArray activeTimeBlocks = new JSONArray();
    private long nextPromptTimeSeconds = 0L;

    private static HashMap<String, Boolean> ongoingSaves = new HashMap<String, Boolean>();
    private static HashMap<String, String> waitingSaves = new HashMap<String, String>();

    public ActiveTimesStorage(String activeTimesPath, String nextPromptTimePath) {
        this.activeTimesPath = activeTimesPath;
        this.nextPromptTimePath = nextPromptTimePath;
    }

    public boolean load() {

        // Try to load from file. Return success,
        // which is defined as successfully reading at least one block.
        try {
            activeTimeBlocks = loadActiveTimes();
            nextPromptTimeSeconds = loadNextPromptTime();
        } catch (Exception e) {

            // Treat it as an empty read.
            activeTimeBlocks = new JSONArray();
            nextPromptTimeSeconds = 0L;
        }

        if (activeTimeBlocks.length() == 0) {
            return false;
        } else {
            return true;
        }
    }

    public int getCount() {
        return activeTimeBlocks.length();
    }

    public boolean getDayOfWeek(int blockIndex, int dayOfWeek) {

        String name = getDayOfWeekSettingName(dayOfWeek);

        try {
            // Return day of week status as we last loaded it.
            JSONObject activeTimesBlock = activeTimeBlocks.getJSONObject(blockIndex);
            return (Boolean)activeTimesBlock.get(name);

        } catch (JSONException e) {

            // Not loaded, or some other error.
            // Try returning from default object.
            JSONObject defaultTimeBlock = generateDefaultTimeBlock();
            try {
                return (Boolean)defaultTimeBlock.get(name);
            } catch (JSONException e2) {
                throw new RuntimeException(e2);
            }
        } catch (ClassCastException e) {

            // Somehow the JSON object didn't contain what we expected.
            // Try returning from default object.
            JSONObject defaultTimeBlock = generateDefaultTimeBlock();
            try {
                return (Boolean)defaultTimeBlock.get(name);
            } catch (JSONException e2) {
                throw new RuntimeException(e2);
            }
        }
    }

    // Returns start time of day as a count of minutes from the start of the day.
    public int getStartTime(int blockIndex) {

        return getInteger(blockIndex, "start_time");
    }

    // Returns end time of day as a count of minutes from the end of the day.
    public int getEndTime(int blockIndex) {

        return getInteger(blockIndex, "end_time");
    }

    public long getNextPromptTime() {
        return nextPromptTimeSeconds;
    }

    // Validates and saves a changed active time.
    // If validation fails, returns false.
    public boolean set(int blockIndex, boolean mon, boolean tue, boolean wed, boolean thur, boolean fri,
                       boolean sat, boolean sun, int startTime, int endTime) {

        // An end time which is before a start time is legal,
        // and means we run past midnight, through to that time next day.

        // Save changes.
        JSONObject newTimeBlock = new JSONObject();
        try {
            newTimeBlock.put(getDayOfWeekSettingName(0), mon);
            newTimeBlock.put(getDayOfWeekSettingName(1), tue);
            newTimeBlock.put(getDayOfWeekSettingName(2), wed);
            newTimeBlock.put(getDayOfWeekSettingName(3), thur);
            newTimeBlock.put(getDayOfWeekSettingName(4), fri);
            newTimeBlock.put(getDayOfWeekSettingName(5), sat);
            newTimeBlock.put(getDayOfWeekSettingName(6), sun);
            newTimeBlock.put("start_time", startTime);
            newTimeBlock.put("end_time", endTime);

            activeTimeBlocks.put(blockIndex, newTimeBlock);
            saveActiveTimes();
            return true;
        } catch (JSONException e) {

            // No idea what happened. Fail the save.
            return false;
        }
    }

    public void delete(int blockIndex) {
        JSONArray newActiveTimeBlocks = new JSONArray();
        for (int i = 0; i < activeTimeBlocks.length(); i++) {
            if (i == blockIndex) {
                continue;
            }

            try {
                newActiveTimeBlocks.put(activeTimeBlocks.get(i));
            }
            catch (JSONException e) {
                // This should never happen.
                return;
            }
        }

        activeTimeBlocks = newActiveTimeBlocks;
        saveActiveTimes();
    }

    public void setNextPromptTime(long seconds) {
        this.nextPromptTimeSeconds = seconds;
        saveNextPromptTime();
    }

    private JSONArray loadActiveTimes() throws JSONException {
        return new JSONArray(readFileAsString(activeTimesPath));
    }

    private void saveActiveTimes() {
        writeStringToFile(activeTimesPath, activeTimeBlocks.toString());
    }

    private long loadNextPromptTime() {
        return Long.parseLong(readFileAsString(nextPromptTimePath));
    }

    private void saveNextPromptTime() {
        writeStringToFile(nextPromptTimePath, Long.toString(nextPromptTimeSeconds));
    }

    private Integer getInteger(int blockIndex, String name) {
        try {
            // Return value as we last loaded it.
            JSONObject activeTimesBlock = activeTimeBlocks.getJSONObject(blockIndex);
            return (Integer)activeTimesBlock.get(name);

        } catch (JSONException e) {

            // Not loaded, or some other error.
            // Try returning from default object.
            JSONObject defaultTimeBlock = generateDefaultTimeBlock();
            try {
                return (Integer)defaultTimeBlock.get(name);
            } catch (JSONException e2) {
                throw new RuntimeException(e2);
            }
        } catch (ClassCastException e) {

            // Somehow the JSON object didn't contain what we expected.
            // Try returning from default object.
            JSONObject defaultTimeBlock = generateDefaultTimeBlock();
            try {
                return (Integer)defaultTimeBlock.get(name);
            } catch (JSONException e2) {
                throw new RuntimeException(e2);
            }
        }
    }

    private static JSONObject generateDefaultTimeBlock() {
        try {
            JSONObject defaultTimeBlock = new JSONObject();
            for (int i = 0; i < 6; i++) {
                defaultTimeBlock.put(getDayOfWeekSettingName(i), true);
            }
            defaultTimeBlock.put(getDayOfWeekSettingName(6), false);
            defaultTimeBlock.put("start_time", 1140);
            defaultTimeBlock.put("end_time", 1320);
            return defaultTimeBlock;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getDayOfWeekSettingName(int dayOfWeek) {
        switch (dayOfWeek) {
            case 0:
                return "monday_enabled";
            case 1:
                return "tuesday_enabled";
            case 2:
                return "wednesday_enabled";
            case 3:
                return "thursday_enabled";
            case 4:
                return "friday_enabled";
            case 5:
                return "saturday_enabled";
            case 6:
                return "sunday_enabled";
            default:
                throw new IllegalArgumentException("Invalid day of week.");
        }
    }

    private static String readFileAsString(String path) {
        try {
            FileInputStream is = new FileInputStream(path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            StringBuilder sb = new StringBuilder();
            String line = reader.readLine();
            sb.append(line);

            reader.close();

            return sb.toString();
        } catch (FileNotFoundException e) {

            // If there's no options file present, treat as empty.
            return "";
        } catch (IOException e) {
            return "";
        }
    }

    private static void writeStringToFile(String path, String content) {
        if (ongoingSaves.containsKey(path)) {
            waitingSaves.put(path, content);
        } else {
            ongoingSaves.put(path, true);
            new SaveTask().execute(path, content);
        }
    }

    private static class SaveTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... strings) {
            String path = strings[0];
            String content = strings[1];

            try {
                PrintWriter out = new PrintWriter(path);
                out.print(content);
                out.close();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

            return path;
        }

        @Override
        protected void onPostExecute(String path) {
            if (waitingSaves.containsKey(path)) {
                String content = waitingSaves.remove(path);
                new SaveTask().execute(path, content);
            } else {
                ongoingSaves.remove(path);
            }

            super.onPostExecute(path);
        }
    }
}

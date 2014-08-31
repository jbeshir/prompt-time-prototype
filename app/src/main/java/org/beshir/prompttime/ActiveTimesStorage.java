package org.beshir.prompttime;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

public class ActiveTimesStorage {

    private final String path;

    private JSONArray activeTimeBlocks = new JSONArray();

    public ActiveTimesStorage(String path) {
        this.path = path;
    }

    public boolean load() {

        // Try to load from file. Return success,
        // which is defined as successfully reading at least one block.
        try {
            activeTimeBlocks = loadActiveTimes();
        } catch (JSONException e) {

            // Treat it as an empty read.
            activeTimeBlocks = new JSONArray();
        }

        if (activeTimeBlocks.length() == 0) {
            activeTimeBlocks = new JSONArray();
            JSONObject defaultTimeBlock = generateDefaultTimeBlock();
            activeTimeBlocks.put(defaultTimeBlock);
            return false;
        } else {
            return true;
        }
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

    // Validates and saves a changed active time.
    // If validation fails, returns false.
    public boolean set(int blockIndex, boolean mon, boolean tue, boolean wed, boolean thur, boolean fri,
                       boolean sat, boolean sun, int startTime, int endTime) {

        // Validate the given settings.
        if (startTime > endTime) {
            return false;
        }

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

    private JSONArray loadActiveTimes() throws JSONException {
        return new JSONArray(readFileAsString(path));
    }

    private void saveActiveTimes() {
        writeStringToFile(path, activeTimeBlocks.toString());
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
            defaultTimeBlock.put("startTime", 1140);
            defaultTimeBlock.put("endTime", 1320);
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

            // If there's no options file present,
            return "";
        } catch (IOException e) {
            return "";
        }
    }

    private static void writeStringToFile(String path, String content) {
        try {
            PrintWriter out = new PrintWriter(path);
            out.print(content);
            out.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}

package org.beshir.prompttime;

import java.util.Locale;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.CountDownTimer;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.FragmentPagerAdapter;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.ToggleButton;


public class PromptTime extends ActionBarActivity implements ActionBar.TabListener, ICountDownChangeListener, TimePicker.OnTimeChangedListener {

    private static ActiveTimesStorage activeTimesStorage;
    private static CountDownState countDownState = new CountDownState();
    private static boolean showNewTimeBlock = false;

    // Whether we are currently updating our own active time block state.
    // Causes us to ignore changes, and not automatically save them.
    private static boolean updatingTimeBlockState = false;

    private static BaseAdapter activeTimesListAdapter = null;

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_prompt_time);

        // Set up the action bar.
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        // When swiping between different sections, select the corresponding
        // tab. We can also use ActionBar.Tab#select() to do this if we have
        // a reference to the Tab.
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }
        });

        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
            // Create a tab with text corresponding to the page title defined by
            // the adapter. Also specify this Activity object, which implements
            // the TabListener interface, as the callback (listener) for when
            // this tab is selected.
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(mSectionsPagerAdapter.getPageTitle(i))
                            .setTabListener(this));
        }

        // Setup active times storage handler.
        activeTimesStorage = new ActiveTimesStorage(getFilesDir().getPath() + "active_times",
                getFilesDir().getPath() + "next_prompt_time");

        // Try to load saved active times.
        // If we fail, hide the tabs; we need to have the user pick times first.
        if (!activeTimesStorage.load()) {
            actionBar.hide();
        } else {
            mViewPager.setCurrentItem(1);
        }

        // Register our alarm handler.
        // We have to do this before setting up the countdown state,
        // because we could try start the alarm there.

        // Setup our countdown state.
        countDownState.addListener(this);
        countDownState.setStorage(activeTimesStorage);
    }

    @Override
    protected void onNewIntent(Intent i) {

        // On receiving a new intent, reset to the prompt if it is enabled.
        if (getSupportActionBar().isShowing()) {
            mViewPager.setCurrentItem(1);
        }

        // On receiving a new intent, always check whether we need to update our alarm state.
        // This is important, because it means if due to a race between the alarm and
        // the app being opened manually, the alarm
        onCountDownChanged(System.currentTimeMillis() / 1000);
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        // When the given tab is selected, switch to the corresponding page in
        // the ViewPager.
        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @TargetApi(21)
    public void onCountDownChanged(long currentTime) {

        long nextEventTime = countDownState.getNextEvent(currentTime);
        boolean nextEventPrompt = countDownState.isNextEventPrompt();

        // Play our alarm sound, if we are showing a prompt,
        // and halt it otherwise.
        if (nextEventTime == currentTime) {
            startService(new Intent(this, AlarmService.class).putExtra("alarm_state", true));
        } else {
            startService(new Intent(this, AlarmService.class).putExtra("alarm_state", false));
        }

        // If we're waiting for a prompt event, schedule an alarm when it arrives.
        // We cancel any existing alarm either way.
        AlarmManager alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(this, AlarmWakefulReceiver.class);
        PendingIntent alarmPendingIntent = PendingIntent.getBroadcast(this, 0, i, 0);
        alarmManager.cancel(alarmPendingIntent);
        if (nextEventTime != currentTime && nextEventTime != Long.MAX_VALUE && nextEventPrompt) {
            // If we target an SDK above 19, on newer devices, set behaves inexactly.
            // As a result we need to use setExact on newer devices,
            // and set on older ones where setExact doesn't exist.
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextEventTime * 1000, alarmPendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, nextEventTime * 1000, alarmPendingIntent);
            }
        }
    }

    public void onSetActiveTimes(View view) {

        // Ignore programmatic changes.
        if (updatingTimeBlockState) {
            return;
        }

        // The grandparent of the view has our view information in a holder as a tag.
        ActiveTimesBlockViewHolder activeTimesBlock;
        activeTimesBlock = (ActiveTimesBlockViewHolder)((View) view.getParent().getParent()).getTag();

        // If this is a new block, don't do anything unless this was the save button being pressed.
        // We wait until they save the block before acting.
        if (view.getId() != R.id.save_button
                && activeTimesBlock.blockIndex == activeTimesStorage.getCount()) {

            return;
        }

        // Don't show a new time block any more, if this was one.
        // We won't need to.
        if (activeTimesBlock.blockIndex == activeTimesStorage.getCount()) {
            showNewTimeBlock = false;
        }

        // Save the current state of the list item to the file.
        int startTime = activeTimesBlock.startTimePicker.getCurrentHour() * 60 +
                activeTimesBlock.startTimePicker.getCurrentMinute();
        int endTime = activeTimesBlock.endTimePicker.getCurrentHour() * 60 +
                activeTimesBlock.endTimePicker.getCurrentMinute();
        activeTimesStorage.set(activeTimesBlock.blockIndex,
                activeTimesBlock.mondayToggle.isChecked(),
                activeTimesBlock.tuesdayToggle.isChecked(),
                activeTimesBlock.wednesdayToggle.isChecked(),
                activeTimesBlock.thursdayToggle.isChecked(),
                activeTimesBlock.fridayToggle.isChecked(),
                activeTimesBlock.saturdayToggle.isChecked(),
                activeTimesBlock.sundayToggle.isChecked(),
                startTime,
                endTime);

        countDownState.updateActiveTimeState(System.currentTimeMillis() / 1000);

        // If we were previously hiding the tab bar, enable it.
        getSupportActionBar().show();

        // Trigger updates to the active times list, if this was from the save button.
        // Changes of other things always leave the UI already up to date,
        // and calling this can be disruptive.
        if (view.getId() == R.id.save_button) {
            activeTimesListAdapter.notifyDataSetChanged();
        }
    }

    public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
        onSetActiveTimes(view);
    }

    public void onPromptNow(View button) {
        countDownState.setPromptTimeDelay(0);
    }

    public void onPromptFive(View button) {
        countDownState.setPromptTimeDelay(5 * 60);
    }

    public void onPromptFifteen(View button) {
        countDownState.setPromptTimeDelay(15 * 60);
    }

    public void onPromptTwentyFive(View button) {
        countDownState.setPromptTimeDelay(25 * 60);
    }

    public void onPromptHour(View button) {
        countDownState.setPromptTimeDelay(60 * 60);
    }

    public void onAddActiveTimesBlock(View button) {
        showNewTimeBlock = true;
        activeTimesListAdapter.notifyDataSetChanged();
    }

    public void onRemoveTimeBlock(View button) {
        ActiveTimesBlockViewHolder viewHolder;
        viewHolder = (ActiveTimesBlockViewHolder)((View) button.getParent().getParent()).getTag();

        if (viewHolder.blockIndex == activeTimesStorage.getCount()) {
            showNewTimeBlock = false;
            activeTimesListAdapter.notifyDataSetChanged();
        } else {
            activeTimesStorage.delete(viewHolder.blockIndex);
            activeTimesListAdapter.notifyDataSetChanged();
            countDownState.updateActiveTimeState(System.currentTimeMillis() / 1000);
        }
    }

    @Override
    protected void onDestroy() {
        countDownState.removeListener(this);
        super.onDestroy();
    }

    static class ActiveTimesBlockViewHolder {
        int blockIndex;

        ToggleButton mondayToggle;
        ToggleButton tuesdayToggle;
        ToggleButton wednesdayToggle;
        ToggleButton thursdayToggle;
        ToggleButton fridayToggle;
        ToggleButton saturdayToggle;
        ToggleButton sundayToggle;

        TimePicker startTimePicker;
        TimePicker endTimePicker;

        Button saveTimesButton;
    }

    public static class ActiveTimesListAdapter extends BaseAdapter {

        LayoutInflater layoutInflater;
        Context context;

        private ActiveTimesBlockViewHolder viewHolder;

        public ActiveTimesListAdapter(Context context) {
            this.context = context;
            layoutInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return activeTimesStorage.getCount() + (showNewTimeBlock ? 1 : 0);
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = layoutInflater.inflate(R.layout.active_time_block, null);

                viewHolder = new ActiveTimesBlockViewHolder();
                viewHolder.blockIndex = i;

                viewHolder.mondayToggle = (ToggleButton)convertView.findViewById(R.id.monday_toggle);
                viewHolder.tuesdayToggle = (ToggleButton)convertView.findViewById(R.id.tuesday_toggle);
                viewHolder.wednesdayToggle = (ToggleButton)convertView.findViewById(R.id.wednesday_toggle);
                viewHolder.thursdayToggle = (ToggleButton)convertView.findViewById(R.id.thursday_toggle);
                viewHolder.fridayToggle = (ToggleButton)convertView.findViewById(R.id.friday_toggle);
                viewHolder.saturdayToggle = (ToggleButton)convertView.findViewById(R.id.saturday_toggle);
                viewHolder.sundayToggle = (ToggleButton)convertView.findViewById(R.id.sunday_toggle);

                viewHolder.startTimePicker = (TimePicker)convertView.findViewById(R.id.start_time_picker);
                viewHolder.startTimePicker.setOnTimeChangedListener((TimePicker.OnTimeChangedListener)context);

                viewHolder.endTimePicker = (TimePicker)convertView.findViewById(R.id.end_time_picker);
                viewHolder.endTimePicker.setOnTimeChangedListener((TimePicker.OnTimeChangedListener)context);

                viewHolder.saveTimesButton = (Button)convertView.findViewById(R.id.save_button);

                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ActiveTimesBlockViewHolder)convertView.getTag();
                viewHolder.blockIndex = i;
            }

            updatingTimeBlockState = true;

            viewHolder.mondayToggle.setChecked(activeTimesStorage.getDayOfWeek(i, 0));
            viewHolder.tuesdayToggle.setChecked(activeTimesStorage.getDayOfWeek(i, 1));
            viewHolder.wednesdayToggle.setChecked(activeTimesStorage.getDayOfWeek(i, 2));
            viewHolder.thursdayToggle.setChecked(activeTimesStorage.getDayOfWeek(i, 3));
            viewHolder.fridayToggle.setChecked(activeTimesStorage.getDayOfWeek(i, 4));
            viewHolder.saturdayToggle.setChecked(activeTimesStorage.getDayOfWeek(i, 5));
            viewHolder.sundayToggle.setChecked(activeTimesStorage.getDayOfWeek(i, 6));

            int startTime = activeTimesStorage.getStartTime(i);
            viewHolder.startTimePicker.setCurrentHour(startTime / 60);
            viewHolder.startTimePicker.setCurrentMinute(startTime % 60);

            int endTime = activeTimesStorage.getEndTime(i);
            viewHolder.endTimePicker.setCurrentHour(endTime / 60);
            viewHolder.endTimePicker.setCurrentMinute(endTime % 60);

            int showSave = i == activeTimesStorage.getCount() ? View.VISIBLE : View.GONE;
            viewHolder.saveTimesButton.setVisibility(showSave);

            updatingTimeBlockState = false;

            return convertView;
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a SectionActiveTimesFragment (defined as a static inner class below).
            switch (position) {
                case 0:
                    return SectionActiveTimesFragment.newInstance(position + 1);
                case 1:
                    return SectionPromptFragment.newInstance(position + 1);
            }
            return null;
        }

        @Override
        public int getCount() {
            // Show 2 total pages.
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();
            switch (position) {
                case 0:
                    return getString(R.string.title_section_active_times).toUpperCase(l);
                case 1:
                    return getString(R.string.title_section_prompt).toUpperCase(l);
            }
            return null;
        }
    }

    /**
     * A fragment describing the active times section.
     */
    public static class SectionActiveTimesFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static SectionActiveTimesFragment newInstance(int sectionNumber) {
            SectionActiveTimesFragment fragment = new SectionActiveTimesFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_section_active_times, container, false);

            // Setup the list view on our configuration panel to contain our active times.
            ListView activeTimesListView = (ListView)rootView.findViewById(R.id.active_times_block_list);

            if (activeTimesListAdapter == null) {
                activeTimesListAdapter = new ActiveTimesListAdapter(getActivity());
            }
            activeTimesListView.setAdapter(activeTimesListAdapter);

            View footerView = inflater.inflate(R.layout.active_time_footer, null, false);
            activeTimesListView.addFooterView(footerView);

            return rootView;
        }
    }

    /**
     * A fragment describing the active times section.
     */
    public static class SectionPromptFragment extends Fragment implements ICountDownChangeListener {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static SectionPromptFragment newInstance(int sectionNumber) {
            SectionPromptFragment fragment = new SectionPromptFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        private CountDownTimer countDownTimer;
        private View rootView;
        private boolean screenForcedOn = false;

        public SectionPromptFragment() {
        }

        public void onCountDownChanged(long currentTime) {
            if (countDownTimer != null) {
                countDownTimer.cancel();
                countDownTimer = null;
            }

            long nextEvent = countDownState.getNextEvent(currentTime);
            boolean inActiveTime = countDownState.isInActiveTime();
            long countDownTime = nextEvent - currentTime;
            int visibilityNowButton = View.GONE;
            int visibilityNextPromptButtons = View.GONE;
            if (nextEvent == currentTime) {
                ((TextView) rootView.findViewById(R.id.countdown)).setText("Prompt!");
                visibilityNextPromptButtons = View.VISIBLE;

                // Allow us to show over the user's lock screen, with the screen forced on.
                if (!screenForcedOn) {
                    getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
                    screenForcedOn = true;
                }

            } else if (nextEvent == Long.MAX_VALUE) {
                ((TextView) rootView.findViewById(R.id.countdown)).setText("None");

                // Stop showing us over the lock screen, with the screen forced on.
                if (screenForcedOn) {
                    getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
                    screenForcedOn = false;
                }
            } else {
                countDownTimer = new CountDownTimer(countDownTime * 1000, 1000) {

                    public void onTick(long millisUntilFinished) {
                        if (countDownTimer == null) {
                            return;
                        }

                        long currentTime = System.currentTimeMillis() / 1000;
                        long secondsToGo = countDownState.getNextEvent(currentTime) - currentTime;
                        String formatted = TimeFormatter.formatDuration(secondsToGo);
                        ((TextView) rootView.findViewById(R.id.countdown)).setText(formatted);
                    }

                    public void onFinish() {
                        if (countDownTimer == null) {
                            return;
                        }

                        onCountDownChanged(System.currentTimeMillis() / 1000);
                    }
                }.start();
                if (inActiveTime) {
                    visibilityNowButton = View.VISIBLE;
                }

                // Stop showing us over the lock screen, with the screen forced on.
                if (screenForcedOn) {
                    getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
                    screenForcedOn = false;
                }
            }

			// Adjust the proportion of the UI given to the countdown display,
			// so it fills whatever space is remaining after the buttons.
            View countdownView = rootView.findViewById(R.id.countdown);
            LinearLayout.LayoutParams countdownLayoutParams = (LinearLayout.LayoutParams)countdownView.getLayoutParams();
            countdownLayoutParams.weight = 5f
                    - (visibilityNextPromptButtons == View.VISIBLE ? 4f : 0f)
                    - (visibilityNowButton == View.VISIBLE ? 1f : 0f);
            countdownView.setLayoutParams(countdownLayoutParams);

            rootView.findViewById(R.id.prompt_now_button).setVisibility(visibilityNowButton);
            rootView.findViewById(R.id.prompt_five_button).setVisibility(visibilityNextPromptButtons);
            rootView.findViewById(R.id.prompt_fifteen_button).setVisibility(visibilityNextPromptButtons);
            rootView.findViewById(R.id.prompt_twenty_five_button).setVisibility(visibilityNextPromptButtons);
            rootView.findViewById(R.id.prompt_hour_button).setVisibility(visibilityNextPromptButtons);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            rootView = inflater.inflate(R.layout.fragment_section_prompt, container, false);

            ((AutoResizeTextView)rootView.findViewById(R.id.countdown)).enableSizeCache(false);
            ((TextView)rootView.findViewById(R.id.countdown)).setText("");

            // Register with our countdown state to receive notifications of changes.
            countDownState.addListener(this);

            // Update our view to correspond to the current countdown state.
            onCountDownChanged(System.currentTimeMillis() / 1000);

            return rootView;
        }

        @Override
        public void onDestroyView() {
            if (countDownTimer != null) {
                countDownTimer.cancel();
                countDownTimer = null;
            }

            rootView = null;

            // Stop showing us over the lock screen, with the screen forced on.
            if (screenForcedOn) {
                getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
                screenForcedOn = false;
            }

            // Stop receiving notifications of changes to our countdown state.
            countDownState.removeListener(this);

            super.onDestroyView();
        }
    }
}
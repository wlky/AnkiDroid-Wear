package com.yannik.ankidroid_wear;

import android.content.Intent;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;


public class SettingsActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        // Display the fragment as the main content
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragment {


        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.activity_settings);

            NumberPickerPreference fontSizeNumberPicker = (NumberPickerPreference) this.findPreference(getResources().getString(R.string.font_size_key));

            SendToWatchWhenPreferencesChangeListener listener = new SendToWatchWhenPreferencesChangeListener();
            fontSizeNumberPicker.setOnPreferenceChangeListener(listener);
            this.findPreference(getResources().getString(R.string.card_flip_animation_key)).setOnPreferenceChangeListener(listener);
            this.findPreference(getResources().getString(R.string.double_tap_key)).setOnPreferenceChangeListener(listener);
            this.findPreference(getResources().getString(R.string.sound_preference_key)).setOnPreferenceChangeListener(listener);


        }


        class SendToWatchWhenPreferencesChangeListener implements Preference.OnPreferenceChangeListener{
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                ((SettingsActivity)getActivity()).sendPreferencesToWatch();
                return true;
            }
        }
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_settings, menu);
        return true;
    }

    public static final String TASK_ACTION = "com.yannik.ankidroid_wear.fromsettings";


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        sendPreferencesToWatch();
        if (id == R.id.action_settings) {

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void sendPreferencesToWatch() {
        Intent intent = new Intent(this, WearMessageListenerService.class);
        intent.putExtra("MyService.data", "myValue");
        startService(intent);
    }
}
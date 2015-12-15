package com.yannik.anki;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;


public class SettingsActivity extends ActionBarActivity {
    static Animation rotation;
    static ImageView sendingIndicator;
    MessageReceiver messageReceiver;
    private boolean isRefreshing = false;

    private static void startRotation() {
        rotation.setRepeatCount(Animation.INFINITE);
        sendingIndicator.startAnimation(rotation);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        // Display the fragment as the main content
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

        IntentFilter messageFilter = new IntentFilter(Intent.ACTION_SEND);
        messageReceiver = new MessageReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver,messageFilter);


    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        super.onCreateOptionsMenu(menu);
        //inflate a menu which shows the non-animated refresh icon
        getMenuInflater().inflate(R.menu.menu_settings, menu);

        if(rotation == null){
            rotation = AnimationUtils.loadAnimation(this, R.anim.refresh_animation);
            rotation.setRepeatCount(Animation.INFINITE);
        }

        MenuItem item = menu.findItem(R.id.sendIcon);
        item.setActionView(R.layout.action_bar_indeterminate_progress);
        sendingIndicator = (ImageView) item.getActionView().findViewById(R.id.loadingImageView);
        if (isRefreshing) {
            rotation.setRepeatCount(Animation.INFINITE);
            sendingIndicator.startAnimation(rotation);
        }
            if(rotation != null)rotation.setRepeatCount(0);
            item.getActionView().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(!isRefreshing) {
                        sendPreferencesToWatch();
                        isRefreshing = true;
                        rotation.setRepeatCount(Animation.INFINITE);
                        sendingIndicator.startAnimation(rotation);
                    }
                }
            });


        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.sendIcon) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void sendPreferencesToWatch() {
        Intent intent = new Intent(this, WearMessageListenerService.class);
        startService(intent);
    }

    public static class SettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.activity_settings);

            NumberPickerPreference fontSizeNumberPicker = (NumberPickerPreference) this.findPreference(getResources().getString(R.string.font_size_key));
            NumberPickerPreference screenTimeoutNumberPicker = (NumberPickerPreference) this.findPreference(getResources().getString(R.string.screen_timeout));
            EditTextPreference mediaLocationDir = (EditTextPreference) this.findPreference(getResources().getString(R.string.media_folder_location));
            if (mediaLocationDir.getText() == null || mediaLocationDir.getText().isEmpty()) {
                mediaLocationDir.setText(Environment.getExternalStorageDirectory() + "/AnkiDroid/collection.media");
            }
            CardMedia.mediaFolder = mediaLocationDir.getText();

            SendToWatchWhenPreferencesChangeListener listener = new SendToWatchWhenPreferencesChangeListener();
            fontSizeNumberPicker.setOnPreferenceChangeListener(listener);
            screenTimeoutNumberPicker.setOnPreferenceChangeListener(listener);
            this.findPreference(getResources().getString(R.string.card_flip_animation_key)).setOnPreferenceChangeListener(listener);
            this.findPreference(getResources().getString(R.string.double_tap_key)).setOnPreferenceChangeListener(listener);
            this.findPreference(getResources().getString(R.string.play_sounds)).setOnPreferenceChangeListener(listener);
            this.findPreference(getResources().getString(R.string.ask_before_first_sound)).setOnPreferenceChangeListener(listener);
            this.findPreference(getResources().getString(R.string.day_mode)).setOnPreferenceChangeListener(listener);
            mediaLocationDir.setOnPreferenceChangeListener(listener);

        }

        class SendToWatchWhenPreferencesChangeListener implements Preference.OnPreferenceChangeListener {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {


                if (preference.getKey().equals(getResources().getString(R.string.media_folder_location))) {
                    CardMedia.mediaFolder = (String) newValue;
                    if (!new File((String) newValue).exists()) {
                        Toast.makeText(getActivity(), "Folder does not exist", Toast.LENGTH_LONG).show();
                    }
                    return true;
                }

                ((SettingsActivity) getActivity()).sendPreferencesToWatch();
                SettingsActivity.startRotation();

                return true;
            }
        }
    }

    class MessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getIntExtra("status",-1337) != -1){
                isRefreshing = false;
                rotation.setRepeatCount(0);
            }
        }
    }
}
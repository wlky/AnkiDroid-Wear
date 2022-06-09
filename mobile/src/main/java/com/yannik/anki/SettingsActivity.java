package com.yannik.anki;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;


public class SettingsActivity extends AppCompatActivity {
    /**
     * Permission to read/write ankidroid cards, this is a permission with a permissionLevel= dangerous.
     *
     * @link https://github.com/ankidroid/Anki-Android/blob/master/AnkiDroid/src/main/AndroidManifest.xml
     */
    public static final String COM_ICHI2_ANKI_PERMISSION_READ_WRITE_DATABASE =
            "com.ichi2.anki.permission.READ_WRITE_DATABASE";
    private static final String TAG = "WearMessageListener";
    /**
     * Request code returned in param to callback when user has granted or refused read anki cards perm.
     */
    private static final int MY_PERMISSIONS_REQUEST = 24;
    ;
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

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        // Display the fragment as the main content
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

        IntentFilter messageFilter = new IntentFilter(Intent.ACTION_SEND);
        messageReceiver = new MessageReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter);

        // check for permissions to read Ankidroid database
        if (ContextCompat.checkSelfPermission(this, COM_ICHI2_ANKI_PERMISSION_READ_WRITE_DATABASE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            Log.v(TAG, "Asking user for dangerous permission : read anki database");

            // We don't wonder whether or not we should ask user as application CAN NOT work without
            // this permission and would consequently be completely useless.
            // So we ask all the time till user finally agrees.

            ActivityCompat.requestPermissions(this,
                    new String[]{COM_ICHI2_ANKI_PERMISSION_READ_WRITE_DATABASE, Manifest.permission.READ_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST);

            // See callback in #onRequestPermissionsResult()
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        super.onCreateOptionsMenu(menu);
        //inflate a menu which shows the non-animated refresh icon
        getMenuInflater().inflate(R.menu.menu_settings, menu);

        if (rotation == null) {
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
        if (rotation != null) rotation.setRepeatCount(0);
        item.getActionView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRefreshing) {
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

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        if (requestCode != MY_PERMISSIONS_REQUEST) {
            return;
        }
        for (int i = 0; i < permissions.length; i++) {
            String permission = permissions[i];
            int result = grantResults[i];
            switch (permission) {
                case COM_ICHI2_ANKI_PERMISSION_READ_WRITE_DATABASE: {
                    // If request is cancelled, the result arrays are empty.
                    if (grantResults.length > 0
                            && result == PackageManager.PERMISSION_GRANTED) {

                        // permission was granted, yay!
                        Log.v(TAG, "User granted dangerous permission : read AnkiDroid database");
                    } else {
                        // permission was NOT granted, explain user this is required!
                        Log.w(TAG, "User DID NOT grant dangerous permission : read anki database");
                        Toast.makeText(this,
                                getString(R.string.settings_activity__permission_necessary),
                                Toast.LENGTH_LONG).show();
                    }
                }
                case Manifest.permission.READ_EXTERNAL_STORAGE: {
                    // If request is cancelled, the result arrays are empty.
                    if (grantResults.length > 0
                            && result == PackageManager.PERMISSION_GRANTED) {

                        // permission was granted
                        Log.v(TAG, "User granted permission : read external storage");
                    } else {
                        // permission was NOT granted, explain user this is required!
                        Log.w(TAG, "User DID NOT grant permission : read external storage");
                    }
                }
                default:
                    Log.w(TAG, "UN-MANAGED request permission result code = " + requestCode);

            }
        }
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
            NumberPickerPreference maxFontSizeNumberPicker = (NumberPickerPreference) this.findPreference(getResources().getString(R.string.max_font_size_key));
            NumberPickerPreference extraPaddingNumberPicker = (NumberPickerPreference) this.findPreference(getResources().getString(R.string.extra_padding_key));
            NumberPickerPreference screenTimeoutNumberPicker = (NumberPickerPreference) this.findPreference(getResources().getString(R.string.screen_timeout));
            EditTextPreference mediaLocationDir = (EditTextPreference) this.findPreference(getResources().getString(R.string.media_folder_location));
            if (mediaLocationDir.getText() == null || mediaLocationDir.getText().isEmpty()) {
                mediaLocationDir.setText(Environment.getExternalStorageDirectory() + "/AnkiDroid/collection.media");
            }
            CardMedia.mediaFolder = mediaLocationDir.getText();

            SendToWatchWhenPreferencesChangeListener listener = new SendToWatchWhenPreferencesChangeListener();
            fontSizeNumberPicker.setOnPreferenceChangeListener(listener);
            maxFontSizeNumberPicker.setOnPreferenceChangeListener(listener);
            extraPaddingNumberPicker.setOnPreferenceChangeListener(listener);
            screenTimeoutNumberPicker.setOnPreferenceChangeListener(listener);
            this.findPreference(getResources().getString(R.string.card_flip_animation_key)).setOnPreferenceChangeListener(listener);
            this.findPreference(getResources().getString(R.string.double_tap_key)).setOnPreferenceChangeListener(listener);
            this.findPreference(getResources().getString(R.string.play_sounds)).setOnPreferenceChangeListener(listener);
            this.findPreference(getResources().getString(R.string.ask_before_first_sound)).setOnPreferenceChangeListener(listener);
            this.findPreference(getResources().getString(R.string.day_mode)).setOnPreferenceChangeListener(listener);
            this.findPreference(getResources().getString(R.string.ambient_mode_key))
                    .setOnPreferenceChangeListener(listener);
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
            if (intent.getIntExtra("status", -1337) != -1) {
                isRefreshing = false;
                rotation.setRepeatCount(0);
            }
        }
    }
}
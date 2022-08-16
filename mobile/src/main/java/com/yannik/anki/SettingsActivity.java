package com.yannik.anki;

import static android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;

import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;


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
    private static final int REQUEST_EXTERNAL_STORAGE = 11;
    static Animation rotation;
    static ImageView sendingIndicator;
    MessageReceiver messageReceiver;
    private boolean isRefreshing = false;
    public static Context Instance;

    private static void startRotation() {
        rotation.setRepeatCount(Animation.INFINITE);
        sendingIndicator.startAnimation(rotation);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setDisplayHomeAsUpEnabled(true);

        // Display the fragment as the main content
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

        IntentFilter messageFilter = new IntentFilter(Intent.ACTION_SEND);
        messageReceiver = new MessageReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter);
        Instance = getApplicationContext();
        // check for permissions to read AnkiDroid database
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
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            checkStorageManagerPermission();
        }




    }
    final static int APP_STORAGE_ACCESS_REQUEST_CODE = 501; // Any value
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
        sendingIndicator = item.getActionView().findViewById(R.id.loadingImageView);
        if (isRefreshing) {
            rotation.setRepeatCount(Animation.INFINITE);
            sendingIndicator.startAnimation(rotation);
        }
        if (rotation != null) rotation.setRepeatCount(0);
        item.getActionView().setOnClickListener(v -> {
            if (!isRefreshing) {
                sendPreferencesToWatch();
                isRefreshing = true;
                rotation.setRepeatCount(Animation.INFINITE);
                sendingIndicator.startAnimation(rotation);
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
                                           @NonNull String[] permissions,
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
                    if (result == PackageManager.PERMISSION_GRANTED) {

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
                    if (result == PackageManager.PERMISSION_GRANTED) {

                        // permission was granted
                        Log.v(TAG, "User granted permission : read external storage");
                        checkStorageManagerPermission();
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

    private void checkStorageManagerPermission() {
//        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
//            if(!Environment.isExternalStorageManager()){
//                try {
//                    Uri uri = Uri.parse("package:" + BuildConfig.APPLICATION_ID);
//                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
//                    intent.addCategory("android.intent.category.DEFAULT");
//                    intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
//                    startActivity(intent);
//                } catch (Exception ex){
//                    Intent intent = new Intent();
//                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
//                    startActivity(intent);
//                }
//            }
//        }
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        String accessUri = sharedPref.getString(getString(R.string.saved_folder_route_key), null);
        boolean accessGranted = false;
        if(accessUri != null){
            DocumentFile documentsTree = DocumentFile.fromTreeUri(getApplication(), Uri.parse(accessUri));
            if(documentsTree != null && documentsTree.canRead()){
                accessGranted = true;
                CardMedia.baseUri = Uri.parse(accessUri);
//                Log.e("Anki", "documentsTree read " + documentsTree.canRead());
            }

        }

        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        // Choose a directory using the system's file picker.
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

                        // Optionally, specify a URI for the directory that should be opened in
                        // the system file picker when it loads.
                        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.fromFile(new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/AnkiDroid/collection.media/")));

                        startActivityForResult(intent, 123);
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        //No button clicked
                        break;
                }
            }
        };

        if(!accessGranted) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Please select the collection.media folder inside the AnkiDroid folder to allow access to images and audio.").setPositiveButton("Ok", dialogClickListener)
                    .setNegativeButton("Don't need it", dialogClickListener).show();

        }
    }
  private MediaPlayer mediaPlayer;
    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {
        if (requestCode == 123
                && resultCode == Activity.RESULT_OK) {
            // The result data contains a URI for the document or directory that
            // the user selected.

            Uri uri, mediaUri = null;
            if (resultData != null) {
                uri = resultData.getData();
                Log.e("anki", "onActivityResult " + uri + " last: " + uri.getLastPathSegment());
                if(uri.getLastPathSegment().endsWith("AnkiDroid")){

                    mediaUri = Uri.parse(uri + "%2Fcollection.media");
                }else if(uri.getLastPathSegment().endsWith("collection.media")){
                    mediaUri = uri;
                }
                Log.d("anki", "mediaURI " + mediaUri);
                if(mediaUri == null){
                    Toast.makeText(getBaseContext(), "Wrong folder selected", Toast.LENGTH_LONG).show();
                    return;
                }
                final int takeFlags = (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
// Check for the freshest data.
                getContentResolver().takePersistableUriPermission(uri, takeFlags);

                SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString(getString(R.string.saved_folder_route_key), mediaUri.toString());
                editor.apply();
                CardMedia.baseUri =mediaUri;
//                DocumentFile documentsTree = DocumentFile.fromTreeUri(getApplication(), uri);
//                Log.e("anki", "Found file is: " + documentsTree.findFile("1_a.mp3").getUri());
//                DocumentFile[] childDocuments = documentsTree.listFiles();
//                int i = 0;
//                for (DocumentFile df: childDocuments
//                     ) {
//                    i++;
//                    if(i>100)break;
//                    Log.e("anki", "uri " + df.getUri() + " path " + getPath(getApplicationContext(), df.getUri()));
//                    if(df.getName().endsWith("mp3")){
//
//                        mediaPlayer = new MediaPlayer();
//                        try {
//                            // mediaPlayer.setDataSource(String.valueOf(myUri));
//                            mediaPlayer.setDataSource(this,df.getUri());
//
//                            mediaPlayer.prepare();
//                            mediaPlayer.start();
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                        break;
//                    }
//
//                }

//                // Perform operations on the document using its URI.
//                File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/AnkiDroid/collection.media/");
//                Log.e("anki", f.getPath() + " Read: " + f.canRead() + " " + Arrays.toString(f.list()));
//                Log.e("anki", f.getParentFile().getPath() + " Read: " + f.canRead() +  " " + Arrays.toString(f.list()));
            }
        }
    }

    private void sendPreferencesToWatch() {
        Intent intent = new Intent(this, WearMessageListenerService.class);
        startService(intent);
    }

    public static class SettingsFragment extends PreferenceFragment {

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            ((SettingsActivity)getActivity()).onActivityResult(requestCode, resultCode, data);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.activity_settings);

            NumberPickerPreference fontSizeNumberPicker = (NumberPickerPreference) this.findPreference(getResources().getString(R.string.font_size_key));
            NumberPickerPreference maxFontSizeNumberPicker = (NumberPickerPreference) this.findPreference(getResources().getString(R.string.max_font_size_key));
            NumberPickerPreference extraPaddingNumberPicker = (NumberPickerPreference) this.findPreference(getResources().getString(R.string.extra_padding_key));
            NumberPickerPreference screenTimeoutNumberPicker = (NumberPickerPreference) this.findPreference(getResources().getString(R.string.screen_timeout));
            SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);

            Preference mediaLocationDir = this.findPreference(getResources().getString(R.string.media_folder_location));
            mediaLocationDir.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

                    // Optionally, specify a URI for the directory that should be opened in
                    // the system file picker when it loads.
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.fromFile(new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/AnkiDroid/collection.media/")));

                    startActivityForResult(intent, 123);
                    return true;
                }
            });
            String mediaFolder = sharedPref.getString(getString(R.string.saved_folder_route_key),null );
            Log.d("Tag", "Media Folder is: " + mediaFolder);
//            if( mediaFolder != null){
//                mediaLocationDir.setText(sharedPref.getString(getString(R.string.saved_folder_route_key), null));
//            }else{
//                if (mediaLocationDir.getText() == null || mediaLocationDir.getText().isEmpty()) {
//                    mediaLocationDir.setText(Environment.getExternalStorageDirectory() + "/AnkiDroid/collection.media");
//                }
//            }
            CardMedia.mediaFolder = sharedPref.getString(getString(R.string.saved_folder_route_key), null);

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
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {

            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();

            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }
}
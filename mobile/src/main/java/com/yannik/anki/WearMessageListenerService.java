package com.yannik.anki;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
//import android.support.v4.content.ContextCompat;
import androidx.core.content.ContextCompat;
//import android.support.v4.content.LocalBroadcastManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.google.android.gms.wearable.WearableStatusCodes;
import com.ichi2.anki.FlashCardsContract;
import com.yannik.sharedvalues.CommonIdentifiers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

import static com.yannik.anki.SettingsActivity.COM_ICHI2_ANKI_PERMISSION_READ_WRITE_DATABASE;
import static com.yannik.sharedvalues.CommonIdentifiers.P2W_COLLECTION_LIST_DECK_COUNT;
import static com.yannik.sharedvalues.CommonIdentifiers.P2W_COLLECTION_LIST_DECK_ID;


/**
 * @author Created by Yannik on 12.03.2015.
 */
public class WearMessageListenerService extends WearableListenerService {

    private static final String TAG = "WearMessageListener";

    public static final int TASK_SEND_SETTINGS = 12;
    public static final String[] SIMPLE_CARD_PROJECTION = {
            FlashCardsContract.Card.ANSWER_PURE,
            FlashCardsContract.Card.QUESTION_SIMPLE};
    private static final Uri DUE_CARD_REVIEW_INFO_URI = FlashCardsContract.ReviewInfo.CONTENT_URI;
    private static final Uri REQUEST_DECKS_URI = Uri.withAppendedPath(FlashCardsContract.AUTHORITY_URI, "decks");
    static Handler soundThreadHandler = new Handler();
    static MediaPlayer soundMediaPlayer;
    private static ArrayList<String> deckNames = new ArrayList<String>();
    private static long cardStartTime;
    private final String[] okImageExtensions = new String[]{"jpg", "png", "gif", "jpeg"};
    private final String[] okSoundExtensions = new String[]{"3gp", "mp3", "wma"};
    ArrayList<Uri> soundsToPlay;
    SoundPlayerOnCompletionListener soundEndedListener = new SoundPlayerOnCompletionListener();
    ArrayList<CardInfo> cardQueue = new ArrayList<CardInfo>();
    private GoogleApiClient googleApiClient;
    private long lastToastTime;
    private Handler uiHandler = new Handler();

    @Override
    public void onCreate() {
        super.onCreate();

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        googleApiClient.connect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        //LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
    }

    private void sendNoMoreCardsToWear() {
        fireMessage(null, CommonIdentifiers.P2W_NO_MORE_CARDS);
    }

    private void sendCardToWear(String q, String a, JSONArray nextReviewTimes, long noteID, int cardOrd) {
        Log.v(TAG, "Sending Card to Wear: Q: " + q + "\n A: " + a);
        HashMap<String, Object> message = new HashMap<String, Object>();
        message.put("q", q);
        message.put("a", a);
        message.put("b", nextReviewTimes);
        message.put("note_id", noteID);
        message.put("card_ord", cardOrd);
        JSONObject js = new JSONObject(message);

        fireMessage(js.toString().getBytes(), CommonIdentifiers.P2W_RESPOND_CARD);


        //fireMessage((Html.fromHtml(mCurrentCard._getQA().get("q")).toString() + "<-!SEP!->" + Html.fromHtml(mCurrentCard.getPureAnswerForReading())).getBytes());

    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.v(TAG, "Message path received on phone is: " + messageEvent.getPath());
        if (messageEvent.getPath().equals(CommonIdentifiers.W2P_REQUEST_CARD)) {
            final String message = new String(messageEvent.getData());

            Log.v(TAG, "Message received on phone is: " + message);
            long deckID;
            try {
                deckID = Long.valueOf(message);
            } catch (NumberFormatException nfe) {
                deckID = -1;
            }
            queryForCurrentCard(deckID);
            setSoundQueue(null);
        } else if (messageEvent.getPath().equals(CommonIdentifiers.W2P_CHOOSE_COLLECTION)) {
            // region CHOOSE COLLECTION
            long deckId = Long.valueOf(new String(messageEvent.getData()));
            Log.v(TAG, "Message received on phone is: " + deckId);

            ContentResolver cr = getContentResolver();

            Uri selectDeckUri = FlashCardsContract.Deck.CONTENT_SELECTED_URI;
            ContentValues values = new ContentValues();
            values.put(FlashCardsContract.Deck.DECK_ID, deckId);
            cr.update(selectDeckUri, values, null, null);


            //----------------------------------------------------------------------------

            Uri deckUri = Uri.withAppendedPath(FlashCardsContract.Deck.CONTENT_ALL_URI, Long.toString(deckId));
            Cursor decksCursor = getContentResolver().query(deckUri, null, null, null, null);

            if (decksCursor == null || !decksCursor.moveToFirst()) {
                Log.d(TAG, "query for deck returned no result");
                if (decksCursor != null) {
                    decksCursor.close();
                }
            } else {
                JSONObject decks = new JSONObject();
                long deckID = decksCursor.getLong(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_ID));
                String deckName = decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_NAME));

                try {
                    JSONObject deckOptions = new JSONObject(decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.OPTIONS)));
                    // These are the deck counts of the Deck. [learn, review, new]
                    JSONArray deckCounts = new JSONArray(decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_COUNTS)));
                    Log.d(TAG, "deckCounts " + deckCounts);
                    Log.d(TAG, "deck Options " + deckOptions);
                    decks.put(deckName, deckID);
                } catch (JSONException e) {
                    Log.e(TAG, "JSONException " + e);
                    e.printStackTrace();
                }

                decksCursor.close();
            }
            // endregion CHOOSE COLLECTION

        } else if (messageEvent.getPath().equals(CommonIdentifiers.W2P_REQUEST_DECKS)) {
            queryForDeckNames();

        } else if (messageEvent.getPath().equals(CommonIdentifiers.W2P_RESPOND_CARD_EASE)) {

            int ease = 0;
            JSONObject json;
            String easeString = null;
            try {
                json = new JSONObject(new String(messageEvent.getData()));
                ease = json.getInt("ease");
                long timeTaken = System.currentTimeMillis() - cardStartTime;
                ContentResolver cr = getContentResolver();
                Uri reviewInfoUri = FlashCardsContract.ReviewInfo.CONTENT_URI;
                ContentValues values = new ContentValues();
                values.put(FlashCardsContract.ReviewInfo.NOTE_ID, json.getLong("note_id"));
                values.put(FlashCardsContract.ReviewInfo.CARD_ORD, json.getInt("card_ord"));
                values.put(FlashCardsContract.ReviewInfo.EASE, ease);
                values.put(FlashCardsContract.ReviewInfo.TIME_TAKEN, timeTaken);
                Log.d(TAG, timeTaken + " time taken " + values.getAsLong(FlashCardsContract.ReviewInfo.TIME_TAKEN));
                cr.update(reviewInfoUri, values, null, null);

                queryForCurrentCard(json.getLong("deck_id"));


            } catch (JSONException e) {
                Log.e(TAG, "JSONException " + e);
                e.printStackTrace();
            }
        } else if (messageEvent.getPath().equals(CommonIdentifiers.W2P_REQUEST_SETTINGS)) {
            sendSettings();
        } else if (messageEvent.getPath().equals(CommonIdentifiers.W2P_PLAY_SOUNDS)) {
            JSONArray soundNames;
            try {
                soundNames = new JSONArray(new String(messageEvent.getData()));
                setSoundQueue(soundNames);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (messageEvent.getPath().equals(CommonIdentifiers.W2P_EXITING)) {
            setSoundQueue(null);
        } else {
            super.onMessageReceived(messageEvent);
        }
    }

    private synchronized void setSoundQueue(JSONArray soundFileNames) {
        Log.d("TAG", "Setting sound to queue: " + (soundsToPlay == null ? "null" : soundsToPlay.size()));
        if (soundsToPlay == null) {
            soundsToPlay = new ArrayList<Uri>();
        } else {
            soundsToPlay.clear();
        }

        if (soundMediaPlayer != null && soundMediaPlayer.isPlaying()) {
            soundMediaPlayer.stop();
            soundMediaPlayer.reset();
        }

        if (soundFileNames != null) {
            for (int i = 0; i < soundFileNames.length(); i++) {
                try {
                    File soundFile = new File(CardMedia.getMediaPath(soundFileNames.getString(i)));
                    if (soundFile.exists()) {
                        Uri soundUri = Uri.fromFile(soundFile);
                        soundsToPlay.add(soundUri);
                        Log.d("TAG", "adding sound to queue: " + soundUri + " ns: " + soundsToPlay.size());
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        soundThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                startPlayingSounds();
            }
        });
    }

    private synchronized void startPlayingSounds() {
        if (soundsToPlay == null) return;
        if (soundsToPlay.size() >= 1) {
            Log.d("TAG", "starting to play sound");
            if (soundMediaPlayer == null) {
                soundMediaPlayer = new MediaPlayer();
                soundMediaPlayer.setOnCompletionListener(soundEndedListener);
            }
            soundMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            try {
                soundMediaPlayer.setDataSource(getApplicationContext(), soundsToPlay.remove(0));

                soundMediaPlayer.setOnCompletionListener(soundEndedListener);
                soundMediaPlayer.prepare();
                soundMediaPlayer.start();
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            if (soundMediaPlayer != null) {
                if (soundMediaPlayer.isPlaying()) {
                    soundMediaPlayer.stop();
                }
                soundMediaPlayer.release();
                soundMediaPlayer = null;
            }
        }
    }

    private void fireMessage(final byte[] data, final String path) {
        fireMessage(data, path, 0);
    }

    private void fireMessage(final byte[] data, final String path, final int retryCount) {

        PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient);
        nodes.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult result) {
                for (int i = 0; i < result.getNodes().size(); i++) {
                    Node node = result.getNodes().get(i);
                    Log.v(TAG, "Phone firing message with path : " + path);

                    PendingResult<MessageApi.SendMessageResult> messageResult = Wearable.MessageApi.sendMessage(googleApiClient, node.getId(),
                            path, data);
                    messageResult.setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                            Status status = sendMessageResult.getStatus();
                            Timber.d("Status: " + status.toString());
                            if (status.getStatusCode() != WearableStatusCodes.SUCCESS) {
                                if (!status.isSuccess()) {
                                    if (retryCount > 5) return;
                                    soundThreadHandler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            fireMessage(data, path, retryCount + 1);
                                        }
                                    }, 1000 * retryCount);
                                }
                            }
                            if (path.equals(CommonIdentifiers.P2W_CHANGE_SETTINGS)) {
                                Intent messageIntent = new Intent();
                                messageIntent.setAction(Intent.ACTION_SEND);
                                messageIntent.putExtra("status", status.getStatusCode());
                                LocalBroadcastManager.getInstance(WearMessageListenerService.this).sendBroadcast(messageIntent);
                            }
                        }
                    });
                }
            }
        });
    }

    private void queryForCurrentCard(long deckID) {
        Log.d(TAG, "QueryForCurrentCard");

        String deckArguments[] = new String[deckID == -1 ? 1 : 2];
        String deckSelector = "limit=?";
        deckArguments[0] = "" + 1;
        if (deckID != -1) {
            deckSelector += ",deckID=?";
            deckArguments[1] = "" + deckID;
        }

        // This call requires com.ichi2.anki.permission.READ_WRITE_DATABASE to be granted by user as it is
        // marked as "dangerous" by ankidroid app. This permission has been asked before. Would crash if
        // not granted, so checking
        if (ContextCompat.checkSelfPermission(this,
                COM_ICHI2_ANKI_PERMISSION_READ_WRITE_DATABASE)
                != PackageManager.PERMISSION_GRANTED) {
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getBaseContext(),
                            R.string.wearservice_permissionnotgranted_toast,
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // permission has been granted, normal case

            Cursor reviewInfoCursor =
                    getContentResolver().query(DUE_CARD_REVIEW_INFO_URI, null, deckSelector, deckArguments, null);

            if (reviewInfoCursor == null || !reviewInfoCursor.moveToFirst()) {
                Log.d(TAG, "query for due card info returned no result");
                sendNoMoreCardsToWear();
                if (reviewInfoCursor != null) {
                    reviewInfoCursor.close();
                }
            } else {
                cardQueue.clear();
                do {
                    CardInfo card = new CardInfo();

                    card.cardOrd = reviewInfoCursor.getInt(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.CARD_ORD));
                    card.noteID = reviewInfoCursor.getLong(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.NOTE_ID));
                    card.buttonCount = reviewInfoCursor.getInt(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.BUTTON_COUNT));

                    try {
                        card.fileNames = new JSONArray(reviewInfoCursor.getString(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.MEDIA_FILES)));
                        card.nextReviewTexts = new JSONArray(reviewInfoCursor.getString(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.NEXT_REVIEW_TIMES)));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    Log.v(TAG, "card added to queue: " + card.fileNames);

                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.READ_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_GRANTED) {
                        new GrabAndProcessFilesTask().execute(card);
                    }


                    cardQueue.add(card);
                } while (reviewInfoCursor.moveToNext());

                reviewInfoCursor.close();

                if (cardQueue.size() >= 1) {

                    for (CardInfo card : cardQueue) {
                        Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(card.noteID));
                        Uri cardsUri = Uri.withAppendedPath(noteUri, "cards");
                        Uri specificCardUri = Uri.withAppendedPath(cardsUri, Integer.toString(card.cardOrd));
                        final Cursor specificCardCursor = getContentResolver().query(specificCardUri,
                                SIMPLE_CARD_PROJECTION,  // projection
                                null,  // selection is ignored for this URI
                                null,  // selectionArgs is ignored for this URI
                                null   // sortOrder is ignored for this URI
                        );

                        if (specificCardCursor == null || !specificCardCursor.moveToFirst()) {
                            Log.d(TAG, "query for due card info returned no result");
                            sendNoMoreCardsToWear();
                            if (specificCardCursor != null) {
                                specificCardCursor.close();
                            }
                            return;
                        } else {
                            card.a = specificCardCursor.getString(specificCardCursor.getColumnIndex(FlashCardsContract.Card.ANSWER_PURE));
                            card.q = specificCardCursor.getString(specificCardCursor.getColumnIndex(FlashCardsContract.Card.QUESTION_SIMPLE));
                            specificCardCursor.close();
                        }
                    }

                    CardInfo nextCard = cardQueue.get(0);
                    cardStartTime = System.currentTimeMillis();
                    sendCardToWear(nextCard.q, nextCard.a, nextCard.nextReviewTexts, nextCard.noteID, nextCard.cardOrd);
                }
            }
        }
    }

    private boolean isImage(String name) {
        return hasCertainExtension(name, okImageExtensions);
    }

    private boolean isSound(String name) {
        return hasCertainExtension(name, okSoundExtensions);
    }

    private boolean hasCertainExtension(String name, String[] extensions) {
        for (String extension : extensions) {
            if (name.trim().toLowerCase().endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private void queryForDeckNames() {

        // This call requires com.ichi2.anki.permission.READ_WRITE_DATABASE to be granted by user as it is
        // marked as "dangerous" by ankidroid app. This permission has been asked before. Would crash if
        // not granted, so checking
        if (ContextCompat.checkSelfPermission(this,
                COM_ICHI2_ANKI_PERMISSION_READ_WRITE_DATABASE)
                != PackageManager.PERMISSION_GRANTED) {
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getBaseContext(),
                            R.string.wearservice_permissionnotgranted_toast,
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // permission has been granted, normal case

            Cursor decksCursor =
                    getContentResolver().query(FlashCardsContract.Deck.CONTENT_ALL_URI,
                            FlashCardsContract.Deck.DEFAULT_PROJECTION, null, null, null);

            if (decksCursor == null) {
                if (System.currentTimeMillis() - lastToastTime > 10000) {
                    lastToastTime = System.currentTimeMillis();
                    //have to run the Toast with a Handler since otherwise, since we're starting it
                    // from a Service the Service stops before disappear is called on the Toast
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getBaseContext(),
                                    "Couldn't query for decks. Do you have AnkiDroid installed?",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

                }
                return;
            }
            if (!decksCursor.moveToFirst()) {
                Log.d(TAG, "query for decks returned no result");
                decksCursor.close();
            } else {
                JSONObject decksJSONObj = new JSONObject();
                do {
                    long deckID = decksCursor.getLong(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_ID));
                    String deckName = decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_NAME));

                    try {
                        JSONObject deckOptions = new JSONObject(decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.OPTIONS)));
                        JSONArray deckCounts = new JSONArray(decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_COUNTS)));

                        Log.d(TAG, "deckName = " + deckName);
                        Log.d(TAG, "deckCounts = " + deckCounts);
                        Log.v(TAG, "deck Options = " + deckOptions);

                        // Creating json object as we have numerous deck related information
                        // Re-using flashcardscontract identifiers.
                        JSONObject deckOJSONObj = new JSONObject();
                        deckOJSONObj.put(P2W_COLLECTION_LIST_DECK_ID, deckID);
                        deckOJSONObj.put(P2W_COLLECTION_LIST_DECK_COUNT, deckCounts);

                        decksJSONObj.put(deckName, deckOJSONObj);

                    } catch (JSONException e) {
                        Log.e(TAG, "Exception when generating JSON");
                        e.printStackTrace();
                    }
                } while (decksCursor.moveToNext());

                sendDecksToWear(decksJSONObj);
            }
        }
        ;
    }

    private void sendDecksToWear(JSONObject decks) {
        fireMessage(decks.toString().getBytes(), CommonIdentifiers.P2W_COLLECTION_LIST);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        sendSettings();
        return START_NOT_STICKY;
    }

    public void sendSettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        JSONObject js = new JSONObject();
        Map<String, ?> preferences = prefs.getAll();
        for (String key : preferences.keySet()) {
            try {
                js.put(key, preferences.get(key));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        fireMessage(js.toString().getBytes(), CommonIdentifiers.P2W_CHANGE_SETTINGS);
    }

    class SoundPlayerOnCompletionListener implements MediaPlayer.OnCompletionListener {

        @Override
        public void onCompletion(MediaPlayer mp) {
            mp.stop();
            mp.reset();
            startPlayingSounds();
        }
    }

    private class GrabAndProcessFilesTask extends AsyncTask<CardInfo, Void, Void> {
        @Override
        protected Void doInBackground(CardInfo... cards) {
            boolean nothingChanged = true;
            PutDataMapRequest dataMap = PutDataMapRequest.create("/image/card");
            for (CardInfo card : cards) {
                for (int i = 0; i < card.fileNames.length(); i++) {
                    try {
                        String name = card.fileNames.getString(i);
                        String mediaPath = CardMedia.getMediaPath(name);
                        if (isImage(name)) {
                            Bitmap bitmap = CardMedia.pullScaledBitmap(mediaPath, 200, 200, true);
                            Asset asset = CardMedia.createAssetFromBitmap(bitmap);

                            dataMap.getDataMap().putAsset(name, asset);
                            nothingChanged = false;
                        } else if (isSound(name)) {
                            card.addSoundUri(mediaPath);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        return null;
                    }

                    // Escape early if cancel() is called
                    if (isCancelled()) break;
                }
            }
            if (!nothingChanged) {
                PutDataRequest request = dataMap.asPutDataRequest();
                Wearable.DataApi.putDataItem(googleApiClient, request);
            }
            return null;
        }
    }

    class CardInfo {
        String q = "", a = "";
        int cardOrd;
        long noteID;
        int buttonCount;
        JSONArray nextReviewTexts = null;
        JSONArray fileNames;
        ArrayList<Uri> soundUris = null;

        public synchronized void addSoundUri(String path) {
            Uri uri = Uri.fromFile(new File(path));

            if (soundUris == null) {
                soundUris = new ArrayList<Uri>();
            }

            soundUris.add(uri);
        }
    }
}

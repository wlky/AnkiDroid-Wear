package com.yannik.ankidroid_wear;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

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
import com.ichi2.anki.provider.FlashCardsContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.yannik.sharedvalues.CommonIdentifiers;

import timber.log.Timber;


/**
 * Created by Yannik on 12.03.2015.
 */
public class WearMessageListenerService extends WearableListenerService {

    private static final String TAG = "WearMessageListener";
    private GoogleApiClient googleApiClient;
    private static ArrayList<String> deckNames = new ArrayList<String>();




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

    public static final int TASK_SEND_SETTINGS = 12;

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

//        } else if (messageEvent.getPath().equals(CommonIdentifiers.W2P_CHOOSE_COLLECTION)) {
//            long deckId = Long.valueOf(new String(messageEvent.getData()));
//            Log.v(TAG, "Message received on phone is: " + deckId);
//
//            ContentResolver cr = getContentResolver();
//
//            Uri selectDeckUri = FlashCardsContract.Deck.CONTENT_SELECTED_URI;
//            ContentValues values = new ContentValues();
//            values.put(FlashCardsContract.Deck.DECK_ID, deckId);
//            cr.update(selectDeckUri, values, null, null);
//
//
//            queryForCurrentCard();

        } else if (messageEvent.getPath().equals(CommonIdentifiers.W2P_REQUEST_DECKS)) {
            queryForDeckNames();

        } else if (messageEvent.getPath().equals(CommonIdentifiers.W2P_RESPOND_CARD_EASE)) {
            int ease = 0;
            JSONObject json;
            String easeString = null;
            try {
                json = new JSONObject(new String(messageEvent.getData()));
                ease = json.getInt("ease");

                ContentResolver cr = getContentResolver();
                Uri reviewInfoUri = FlashCardsContract.ReviewInfo.CONTENT_URI;
                ContentValues values = new ContentValues();
                values.put(FlashCardsContract.ReviewInfo.NOTE_ID, json.getLong("note_id"));
                values.put(FlashCardsContract.ReviewInfo.CARD_ORD, json.getInt("card_ord"));
                values.put(FlashCardsContract.ReviewInfo.EASE, ease);
                cr.update(reviewInfoUri, values, null, null);

                queryForCurrentCard(json.getLong("deck_id"));


            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (messageEvent.getPath().equals(CommonIdentifiers.W2P_REQUEST_SETTINGS)) {
            sendSettings();
        }else if (messageEvent.getPath().equals(CommonIdentifiers.W2P_PLAY_SOUNDS)) {
            JSONArray soundNames;
            try {
                soundNames = new JSONArray(new String(messageEvent.getData()));
                addToSoundQueue(soundNames);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            super.onMessageReceived(messageEvent);
        }
    }

    ArrayList<Uri> soundsToPlay;

    private void addToSoundQueue(JSONArray soundFileNames){
        if(soundsToPlay == null) soundsToPlay = new ArrayList<Uri>();

        for(int i = 0; i < soundFileNames.length(); i++){
            try {
                File soundFile = new File(CardMedia.getMediaPath(soundFileNames.getString(i)));
                if(soundFile.exists()){
                    Uri soundUri = Uri.fromFile(soundFile);
                    soundsToPlay.add(soundUri);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if(soundMediaPlayer == null || !soundMediaPlayer.isPlaying()) {

            soundThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    startPlayingSounds();
                }
            });

        }
    }
    Handler soundThreadHandler = new Handler();
    MediaPlayer soundMediaPlayer;

    private void startPlayingSounds(){
        if(soundsToPlay.size() >= 1) {
            if(soundMediaPlayer == null){
                soundMediaPlayer = new MediaPlayer();
                soundMediaPlayer.setOnCompletionListener(soundEndedListener);
            }
            soundMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            try {
                soundMediaPlayer.setDataSource(getApplicationContext(), soundsToPlay.remove(0));

                soundMediaPlayer.setOnCompletionListener(soundEndedListener);
                soundMediaPlayer.prepare();
                soundMediaPlayer.start();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }else{
            if (soundMediaPlayer != null){
                soundMediaPlayer.release();
                soundMediaPlayer = null;
            }
        }
    }

    SoundPlayerOnCompletionListener soundEndedListener = new SoundPlayerOnCompletionListener();

    class SoundPlayerOnCompletionListener implements MediaPlayer.OnCompletionListener{

        @Override
        public void onCompletion(MediaPlayer mp) {
            mp.stop();
            mp.reset();
            startPlayingSounds();
        }
    }


    private void fireMessage(final byte[] data, final String path) {

        PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient);
        nodes.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult result) {
                for (int i = 0; i < result.getNodes().size(); i++) {
                    Node node = result.getNodes().get(i);
                    String nName = node.getDisplayName();
                    String nId = node.getId();
                    Log.v(TAG, "Phone firing message with path : " + path);

                    PendingResult<MessageApi.SendMessageResult> messageResult = Wearable.MessageApi.sendMessage(googleApiClient, node.getId(),
                            path, data);
                    messageResult.setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                            Status status = sendMessageResult.getStatus();
                            Timber.d("Status: " + status.toString());
                            if (status.getStatusCode() != WearableStatusCodes.SUCCESS) {
//                                alertButton.setProgress(-1);
//                                label.setText("Tap to retry. Alert not sent :(");
                            }
                            if(path.equals(CommonIdentifiers.P2W_CHANGE_SETTINGS)) {
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


    private static final Uri DUE_CARD_REVIEW_INFO_URI = FlashCardsContract.ReviewInfo.CONTENT_URI;
    private static final Uri REQUEST_DECKS_URI = Uri.withAppendedPath(FlashCardsContract.AUTHORITY_URI, "decks");

    ArrayList<CardInfo> cardQueue = new ArrayList<CardInfo>();

    private void queryForCurrentCard(long deckID) {
        Log.d(TAG, "WearMessageListenerService: queryForCurrentCard");
        String deckArguments[] = new String[deckID == -1 ? 1 : 2];
        String deckSelector = "limit=?";
        deckArguments[0] = "" + 1;
        if (deckID != -1) {
            deckSelector += ",deckID=?";
            deckArguments[1] = "" + deckID;
        }

        Cursor reviewInfoCursor = getContentResolver().query(DUE_CARD_REVIEW_INFO_URI, null, deckSelector, deckArguments, null);

        if (reviewInfoCursor == null || !reviewInfoCursor.moveToFirst()) {
            Log.d(TAG, "query for due card info returned no result");
            sendNoMoreCardsToWear();
        } else {
            cardQueue.clear();
            do {
                CardInfo card = new CardInfo();

                card.cardOrd = reviewInfoCursor.getInt(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.CARD_ORD));
                card.noteID = reviewInfoCursor.getLong(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.NOTE_ID));
                card.buttonCount = reviewInfoCursor.getInt(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.BUTTON_COUNT));
                card.q = reviewInfoCursor.getString(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.QUESTION_SIMPLE));
                card.a = reviewInfoCursor.getString(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.ANSWER_SIMPLE));

                try {
                    card.fileNames = new JSONArray(reviewInfoCursor.getString(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.MEDIA_FILES)));
                    card.nextReviewTexts = new JSONArray(reviewInfoCursor.getString(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.NEXT_REVIEW_TIMES)));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Log.v(TAG, "card added to queue: " + card.fileNames);

                new GrabAndProcessImagesTask().execute(card);

                cardQueue.add(card);
            } while (reviewInfoCursor.moveToNext());
            reviewInfoCursor.close();
            if (cardQueue.size() >= 1) {
                CardInfo nextCard = cardQueue.get(0);
                sendCardToWear(nextCard.q, nextCard.a, nextCard.nextReviewTexts, nextCard.noteID, nextCard.cardOrd);
            }
        }

    }

    private class GrabAndProcessImagesTask extends AsyncTask<CardInfo, Void, Void> {
        @Override
        protected Void doInBackground(CardInfo... cards) {
            boolean nothingChanged = true;
            PutDataMapRequest dataMap = PutDataMapRequest.create("/image/card");
            for (CardInfo card : cards) {
                for (int i = 0; i < card.fileNames.length(); i++) {
                    try {
                        String name = card.fileNames.getString(i);
                        String mediaPath = CardMedia.getMediaPath(name);
                        if(isImage(name)) {
                            Bitmap bitmap = CardMedia.pullScaledBitmap(mediaPath, 200,200, true);
                            Asset asset = CardMedia.createAssetFromBitmap(bitmap);

                            dataMap.getDataMap().putAsset(name, asset);
                            nothingChanged = false;
                        }else if(isSound(name)){
                            card.addSoundUri(mediaPath);
                        }
                    } catch (JSONException e) {e.printStackTrace();return null;}


                    // Escape early if cancel() is called
                    if (isCancelled()) break;
                }
            }
            if(!nothingChanged) {
                PutDataRequest request = dataMap.asPutDataRequest();
                Wearable.DataApi.putDataItem(googleApiClient, request);
            }
            return null;
        }
    }



    private final String[] okImageExtensions =  new String[] {"jpg", "png", "gif","jpeg"};
    private boolean isImage(String name){
        return hasCertainExtension(name, okImageExtensions);
    }

    private final String[] okSoundExtensions =  new String[] {"3gp", "mp3", "wma"};
    private boolean isSound(String name){
        return hasCertainExtension(name, okSoundExtensions);
    }


    private boolean hasCertainExtension(String name, String[] extensions){
        for(String extension : extensions){
            if (name.trim().toLowerCase().endsWith(extension))
            {
                return true;
            }
        }
        return false;
    }


    private void queryForDeckNames() {
        Cursor decksCursor = getContentResolver().query(FlashCardsContract.Deck.CONTENT_ALL_URI, null, null, null, null);

        if (!decksCursor.moveToFirst()) {
            Log.d(TAG, "query for decks returned no result");
        } else {
            JSONObject decks = new JSONObject();
            do {
                long deckID = decksCursor.getLong(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_ID));
                String deckName = decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_NAME));

                try {
                    decks.put(deckName, deckID);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } while (decksCursor.moveToNext());

            sendDecksToWear(decks);
        }
    }

    private void sendDecksToWear(JSONObject decks) {
        fireMessage(decks.toString().getBytes(), CommonIdentifiers.P2W_COLLECTION_LIST);
    }

    class CardInfo {
        String q = "", a = "";
        int cardOrd;
        long noteID;
        int buttonCount;
        JSONArray nextReviewTexts = null;
        JSONArray fileNames;
        ArrayList<Uri> soundUris = null;

        public void addSoundUri(String path){
            Uri uri = Uri.fromFile(new File(path));

            if(soundUris == null){
                soundUris = new ArrayList<Uri>();
            }

            soundUris.add(uri);
        }
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
}

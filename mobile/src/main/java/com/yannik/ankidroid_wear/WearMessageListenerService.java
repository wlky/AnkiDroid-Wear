package com.yannik.ankidroid_wear;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Html;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.google.android.gms.wearable.WearableStatusCodes;
import com.ichi2.anki.provider.FlashCardsContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Comment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import com.yannik.sharedvalues.CommonIdentifiers;
import timber.log.Timber;


/**
 * Created by Yannik on 12.03.2015.
 */
public class WearMessageListenerService extends WearableListenerService {

    private static final String TAG = "WearMessageListener";
    private GoogleApiClient googleApiClient;
    private static ArrayList<String> deckNames = new ArrayList<String>();

 //   MessageReceiver messageReceiver = new MessageReceiver();

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
            queryForCurrentCard();

        } else if (messageEvent.getPath().equals(CommonIdentifiers.W2P_CHOOSE_COLLECTION)) {
            long deckId = Long.valueOf(new String(messageEvent.getData()));
            Log.v(TAG, "Message received on phone is: " + deckId);

            ContentResolver cr = getContentResolver();

            Uri dataUri = Uri.withAppendedPath(FlashCardsContract.AUTHORITY_URI, "select_deck");
            ContentValues values = new ContentValues();
            values.put(FlashCardsContract.Deck.DECK_ID, deckId);
            cr.update(dataUri, values, null, null);


            queryForCurrentCard();

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

                Uri dataUri = Uri.withAppendedPath(FlashCardsContract.AUTHORITY_URI, "due_cards");
                ContentValues values = new ContentValues();
                values.put(FlashCardsContract.ReviewInfo.NOTE_ID, json.getLong("note_id"));
                values.put(FlashCardsContract.ReviewInfo.CARD_ORD, json.getInt("card_ord"));
                values.put(FlashCardsContract.ReviewInfo.EASE, ease);
                cr.update(dataUri, values, null, null);

                queryForCurrentCard();



            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if(messageEvent.getPath().equals(CommonIdentifiers.W2P_REQUEST_SETTINGS)) {
            sendSettings();
        }else{
            super.onMessageReceived(messageEvent);
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
                        }
                    });
                }
            }
        });
    }


    private void updateDeckNames(TreeSet<Object[]> decks, int eta, int count) {
        if (decks == null) {
            return;
        }
        deckNames.clear();
        ArrayList<Long> deckIDs = new ArrayList<Long>();
        JSONObject json = new JSONObject();
        for (Object[] d : decks) {
            String[] name = ((String[]) d[0]);
            long did = (Long) d[1];
//            String readableName = DeckPicker.readableDeckName(name);
            deckIDs.add(did);

//            try {
//                json.put(readableName, did);
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }

//            deckNames.add(readableName);
//            Log.v("test", readableName);
        }

        fireMessage(json.toString().getBytes(), CommonIdentifiers.P2W_COLLECTION_LIST);


    }

    private void chooseSelection(long did) {

    }

    private static final Uri DUE_CARD_REVIEW_INFO_URI = Uri.withAppendedPath(FlashCardsContract.AUTHORITY_URI, "due_cards");
    private static final Uri REQUEST_DECKS_URI = Uri.withAppendedPath(FlashCardsContract.AUTHORITY_URI, "decks");
    private int buttonCount;
    private JSONArray nextReviewTexts = null;

    private void queryForCurrentCard() {
        Log.d(TAG, "WearMessageListenerService: queryForCurrentCard");
        Cursor reviewInfoCursor = getContentResolver().query(DUE_CARD_REVIEW_INFO_URI, null, null, null, "name");

        if (reviewInfoCursor == null || !reviewInfoCursor.moveToFirst()) {
            Log.d(TAG, "query for due card info returned no result");
            sendNoMoreCardsToWear();
        } else {
            String q = "", a = "";


//            do {
            int cardOrd = reviewInfoCursor.getInt(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.CARD_ORD));
            long noteID = reviewInfoCursor.getLong(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.NOTE_ID));
            buttonCount = reviewInfoCursor.getInt(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.BUTTON_COUNT));
            q = reviewInfoCursor.getString(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.QUESTION_SIMPLE));
            a = reviewInfoCursor.getString(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.ANSWER_SIMPLE));


            try {
                nextReviewTexts = new JSONArray(reviewInfoCursor.getString(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.NEXT_REVIEW_TIMES)));
            } catch (JSONException e) {
                e.printStackTrace();
            }finally {
                reviewInfoCursor.close();
            }

            Log.v(TAG, "nextReviewTexts: " + nextReviewTexts);
            /*Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteID));
            Uri cardsUri = Uri.withAppendedPath(noteUri, "cards");
            Uri specificCardUri = Uri.withAppendedPath(cardsUri, Integer.toString(cardOrd));
            Cursor cardCursor = getContentResolver().query(specificCardUri, null, null, null, "name");

            if (cardCursor.moveToFirst()) {
//                    do {

                a = cardCursor.getString(cardCursor.getColumnIndex(FlashCardsContract.Card.ANSWER));
                q = cardCursor.getString(cardCursor.getColumnIndex(FlashCardsContract.Card.QUESTION));
//                    } while (cardCursor.moveToNext());
            } else {
                Timber.d("query for card returned no result");
            }
            cardCursor.close();*/

//            } while (reviewInfoCursor.moveToNext());

//            q = Html.fromHtml(q).toString();
//            a = Html.fromHtml(a).toString();

            sendCardToWear(q, a, nextReviewTexts, noteID, cardOrd);
        }

    }

    private void queryForDeckNames() {
        Cursor decksCursor = getContentResolver().query(REQUEST_DECKS_URI, null, null, null, "name");

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


    public class MessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int task = intent.getIntExtra("task", 1337);
            switch (task) {
                case TASK_SEND_SETTINGS:
                    sendSettings();
                    break;
            }
        }
    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {
        sendSettings();
        return START_NOT_STICKY;
    }

    public void sendSettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        JSONObject js = new JSONObject();
        Map<String, ?> preferences =  prefs.getAll();
        for(String key : preferences.keySet()){
            try {
                js.put(key, preferences.get(key));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        fireMessage(js.toString().getBytes(), CommonIdentifiers.P2W_CHANGE_SETTINGS);
    }
}

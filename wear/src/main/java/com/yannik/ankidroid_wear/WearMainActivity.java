package com.yannik.ankidroid_wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.WindowManager;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.yannik.sharedvalues.CommonIdentifiers;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class WearMainActivity extends FragmentActivity {
    private static final String TAG = "WearMain";
    public static final String PREFS_NAME = "ANKIDROID_WEAR_PREFERENCES";

    private static GoogleApiClient googleApiClient;
    private MessageReceiver messageReceiver;
    private IntentFilter messageFilter;


    public static HashMap<String, Asset> availableAssets = new HashMap<String, Asset>();

    Preferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wear_main);

        preferences = new Preferences(this);
        preferences.load();
        final ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
        final PagerAdapter adapter = new PagerAdapter(getSupportFragmentManager());

        final ReviewFragment reviewFragment = ReviewFragment.newInstance(preferences);
        CollectionFragment decksFragment = CollectionFragment.newInstance(null);

        decksFragment.setChooseDeckListener(new CollectionFragment.OnFragmentInteractionListener() {
            @Override
            public void onFragmentInteraction(long id) {
                fireMessage(CommonIdentifiers.W2P_CHOOSE_COLLECTION, "" + id);
                fireMessage(CommonIdentifiers.W2P_REQUEST_CARD, "" + id);
                viewPager.setCurrentItem(0);
                reviewFragment.indicateLoading();
                preferences.setSelectedDeck(id);
            }
        });

        jsonReceivers.add(new JsonReceiver() {
            @Override
            public void onJsonReceive(String path, JSONObject json) {
                if (path.equals(CommonIdentifiers.P2W_CHANGE_SETTINGS)) {
                    for (Iterator<String> it = json.keys(); it.hasNext(); ) {
                        try {
                            String name = it.next();
                            if (name.equals(Preferences.CARD_FONT_SIZE)) {
                                preferences.setCardFontSize((float) json.getInt(name));
                            } else if (name.equals(Preferences.DOUBLE_TAP)) {
                                preferences.setDoubleTapReview(json.getBoolean(name));
                            } else if (name.equals(Preferences.FLIP_ANIMATION)) {
                                preferences.setFlipCards(json.getBoolean(name));
                            } else if (name.equals(Preferences.SCREEN_TIMEOUT)) {
                                preferences.setScreenTimeout(json.getInt(name));
                            } else if (name.equals(Preferences.PLAY_SOUNDS)) {
                                preferences.setPlaySound(json.getInt(name));
                            } else if (name.equals(Preferences.ASK_BEFORE_FIRST_SOUND)) {
                                preferences.setAskBeforeFirstSound(json.getBoolean(name));
                            }

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    reviewFragment.applySettings();
                }
            }
        });

        jsonReceivers.add(reviewFragment);
        jsonReceivers.add(decksFragment);
        adapter.addFragment(reviewFragment);
        adapter.addFragment(decksFragment);
        viewPager.setAdapter(adapter);

        messageFilter = new IntentFilter(Intent.ACTION_SEND);
        messageReceiver = new MessageReceiver();



        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        googleApiClient.registerConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(Bundle bundle) {
                Log.d(TAG, "Wear connected to Google Api");

                PendingResult<DataItemBuffer> results = Wearable.DataApi.getDataItems(googleApiClient);
                results.setResultCallback(new ResultCallback<DataItemBuffer>() {
                    @Override
                    public void onResult(DataItemBuffer dataItems) {
                        if (dataItems.getCount() != 0) {
                            for(int i = 0; i< dataItems.getCount(); i++) {
                                DataItem dataItem = dataItems.get(i);
                                try{
                                    DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                                    for (String name : dataMapItem.getDataMap().keySet()) {
                                        WearMainActivity.availableAssets.put(name, dataMapItem.getDataMap().getAsset(name));
                                        Log.v("myTag", "Immage received on watch is: " + name);
                                    }
                                }catch (IllegalStateException ise){}
                            }
                        }

                        dataItems.release();
                    }
                });

                if (firstStart) {
                    firstStart = false;
                    fireMessage(CommonIdentifiers.W2P_REQUEST_SETTINGS, null);
                    fireMessage(CommonIdentifiers.W2P_REQUEST_CARD, "" + preferences.getSelectedDeck());
                    fireMessage(CommonIdentifiers.W2P_REQUEST_DECKS, null);
                }
            }

            @Override
            public void onConnectionSuspended(int i) {
                Log.d(TAG, "Wear connection to Google Api suspended");
            }
        });
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    boolean firstStart = true;

    @Override
    protected void onStart() {
        Log.d(getClass().getName(), "MainActivity.onStart");
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter);
        googleApiClient.connect();

    }

    @Override
    public void onStop() {
        Log.d(getClass().getName(), "MainActivity.onStop");
        if (null != googleApiClient && googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
        super.onStop();
    }

    @Override
    public void onPause() {
        Log.d(getClass().getName(), "MainActivity.onPause");
        super.onPause();

        fireMessage(CommonIdentifiers.W2P_EXITING, "");


        //finish(); //TODO Real onPause - onResume implementation
    }

    @Override
    public void onResume() {
        Log.d(getClass().getName(), "MainActivity.onResume");
        super.onResume();
    }

    private void fireMessage(final String path, final String data ) {
        fireMessage(data, path, 0);
    }

    private Handler mHandler = new Handler();

    private void fireMessage(final String data, final String path,final int retryCount) {
        Log.d(TAG, "Firing Request " + path);
        // Send the RPC
        PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient);
        nodes.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult result) {
                for (int i = 0; i < result.getNodes().size(); i++) {
                    Node node = result.getNodes().get(i);
                    String nName = node.getDisplayName();
                    String nId = node.getId();
                    Log.d(TAG, "firing Message with path: " + path);

                    PendingResult<MessageApi.SendMessageResult> messageResult = Wearable.MessageApi.sendMessage(googleApiClient, node.getId(),
                            path, (data == null ? "" : data).getBytes());
                    messageResult.setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                            Status status = sendMessageResult.getStatus();
                            Log.d(TAG, "Status: " + status.toString());
                            if(retryCount > 5)return;
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    fireMessage(data, path, retryCount + 1);
                                }
                            }, 1000 * retryCount);

                        }
                    });
                }
            }
        });
    }



    public static Bitmap  loadBitmapFromAsset(Asset asset) {
        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                googleApiClient, asset).await().getInputStream();

        if (assetInputStream == null) {
            Log.w(TAG, "Requested an unknown Asset.");
            return null;
        }
        // decode the stream into a bitmap
        return BitmapFactory.decodeStream(assetInputStream);
    }



    ArrayList<JsonReceiver> jsonReceivers = new ArrayList<JsonReceiver>();

    public class MessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            JSONObject js = null;
            String message = intent.getStringExtra("message");
            String path = intent.getStringExtra("path");
            if (message != null) {
                try {
                    js = new JSONObject(message);
                } catch (JSONException e) {
                }
            }
            for (JsonReceiver jsr : jsonReceivers) {
                jsr.onJsonReceive(path, js);
            }

        }
    }

    interface JsonReceiver {
        public void onJsonReceive(String path, JSONObject json);
    }

    private class PagerAdapter extends FragmentPagerAdapter {
        List<Fragment> fragmentList = null;

        public PagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
            fragmentList = new ArrayList<Fragment>();
        }

        @Override
        public Fragment getItem(int position) {
            return fragmentList.get(position);
        }

        @Override
        public int getCount() {
            return fragmentList.size();
        }

        public void addFragment(Fragment fragment) {
            fragmentList.add(fragment);
            notifyDataSetChanged();
        }
    }
}

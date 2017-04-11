package com.yannik.anki;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.FragmentGridPagerAdapter;
import android.support.wearable.view.GridViewPager;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

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
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class WearMainActivity extends WearableActivity {
    public static final String PREFS_NAME = "ANKIDROID_WEAR_PREFERENCES";
    private static final String TAG = "WearMain";
    public static HashMap<String, Asset> availableAssets = new HashMap<String, Asset>();
    private static GoogleApiClient googleApiClient;
    private static Handler mHandler = new Handler();
    Preferences preferences;
    boolean firstStart = true;
    ArrayList<JsonReceiver> jsonReceivers = new ArrayList<JsonReceiver>();
    private MessageReceiver messageReceiver;
    private IntentFilter messageFilter;
    private ReviewFragment reviewFragment;
    private CollectionFragment decksFragment;
    private TextView timeOverlay;
    private Handler timeHandler = new Handler();
    private Runnable timeRunnable = new Runnable() {
        @Override
        public void run() {
            Calendar c = Calendar.getInstance();
            //            int hours = c.get(Calendar.HOUR);
            //            int minutes = c.get(Calendar.MINUTE);
            int seconds = c.get(Calendar.SECOND);
            CharSequence timeText;
            if (!DateFormat.is24HourFormat(WearMainActivity.this)) {
                timeText = android.text.format.DateFormat.format("hh:mm", new java.util.Date());
            } else {
                timeText = android.text.format.DateFormat.format("kk:mm", new java.util.Date());
            }
            timeOverlay.setText(timeText);
            timeHandler.postDelayed(this, 1000 * (60 - seconds));
        }
    };
    private MyGridViewPager viewPager;

    public static void fireMessage(final String path, final String data) {
        fireMessage(data, path, 0);
    }

    private static void fireMessage(final String data, final String path, final int retryCount) {
        Log.d(TAG, "Firing Request " + path + " retryCount = " + retryCount);
        // Send the RPC
        PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(
                googleApiClient);
        nodes.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult result) {
                for (int i = 0; i < result.getNodes().size(); i++) {
                    Node node = result.getNodes().get(i);
                    String nName = node.getDisplayName();
                    String nId = node.getId();
                    Log.d(TAG, "Firing Message with path: " + path);

                    PendingResult<MessageApi.SendMessageResult> messageResult = Wearable
                            .MessageApi.sendMessage(
                                    googleApiClient,
                                    node.getId(),
                                    path,
                                    (data == null ? "" : data).getBytes());
                    messageResult.setResultCallback(new ResultCallback<MessageApi
                            .SendMessageResult>() {
                        @Override
                        public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                            Status status = sendMessageResult.getStatus();

                            Log.d(TAG, "Status: " + status.toString());
                            if (!status.isSuccess()) {
                                if (retryCount > 5) {
                                    Log.w(TAG, "Too many retries, giving up.");
                                    return;
                                }
                                mHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        fireMessage(data, path, retryCount + 1);
                                    }
                                }, 1000 * retryCount);
                            }
                        }
                    });
                }
            }
        });
    }

    public static Bitmap loadBitmapFromAsset(Asset asset) {
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

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        if (preferences.isAmbientMode()) {
            reviewFragment.onEnterAmbient();
            decksFragment.onEnterAmbient();
            timeOverlay.setVisibility(View.VISIBLE);

            timeHandler = new Handler();
            timeHandler.post(timeRunnable);

            Log.d(TAG, "Entered ambient mode");
        } else {
            finish();
        }
    }

    @Override
    public void onExitAmbient() {
        super.onExitAmbient();
        reviewFragment.onExitAmbient();
        decksFragment.onExitAmbient();
        timeOverlay.setVisibility(View.GONE);
        timeHandler.removeCallbacks(timeRunnable);
        Log.d(TAG, "Exited ambient mode");

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wear_main);


        preferences = new Preferences(this);
        preferences.load();
        viewPager = (MyGridViewPager) findViewById(R.id.pager);
        final MyGridViewPager viewPager = (MyGridViewPager) findViewById(R.id.pager);

        final PagerAdapter adapter = new PagerAdapter(getFragmentManager());


        viewPager.setOnPageChangeListener(new GridViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, int i1, float v, float v1, int i2, int i3) {
            }

            @Override
            public void onPageSelected(int x, int y) {
                Log.d(getClass().getName(), "Page selected x: " + x + " y: " + y);
                if (x == 0 && y == 1) {
                    fireMessage(CommonIdentifiers.W2P_REQUEST_DECKS, null);
                }
            }

            @Override
            public void onPageScrollStateChanged(int i) {
            }
        });

        reviewFragment = ReviewFragment.newInstance(preferences, viewPager);
        decksFragment = CollectionFragment.newInstance(null);

        if (preferences.isAmbientMode()) {
            setAmbientEnabled();
        }

        decksFragment.setChooseDeckListener(new CollectionFragment.OnFragmentInteractionListener() {
            @Override
            public void onFragmentInteraction(long id) {
                fireMessage(CommonIdentifiers.W2P_CHOOSE_COLLECTION, "" + id);
                fireMessage(CommonIdentifiers.W2P_REQUEST_CARD, "" + id);
                viewPager.setCurrentItem(0, 0);
                reviewFragment.indicateLoading();
                preferences.setSelectedDeck(id);
            }
        });

        timeOverlay = (TextView) findViewById(R.id.time_overlay);

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
                            } else if (name.equals(Preferences.DAY_MODE)) {
                                preferences.setDayMode(json.getBoolean(name));
                            } else if (name.equals(Preferences.AMBIENT_MODE)) {
                                preferences.setAmbientMode(json.getBoolean(name));
                                if (preferences.isAmbientMode()) {
                                    setAmbientEnabled();
                                }
                            }

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    reviewFragment.applySettings();
                    decksFragment.setSettings(preferences);
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

                PendingResult<DataItemBuffer> results = Wearable.DataApi.getDataItems(
                        googleApiClient);
                results.setResultCallback(new ResultCallback<DataItemBuffer>() {
                    @Override
                    public void onResult(DataItemBuffer dataItems) {
                        if (dataItems.getCount() != 0) {
                            for (int i = 0; i < dataItems.getCount(); i++) {
                                DataItem dataItem = dataItems.get(i);
                                try {
                                    DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                                    for (String name : dataMapItem.getDataMap().keySet()) {
                                        WearMainActivity.availableAssets.put(name,
                                                dataMapItem.getDataMap().getAsset(name));
                                        Log.v("myTag", "Immage received on watch is: " + name);
                                    }
                                } catch (IllegalStateException ise) {
                                }
                            }
                        }

                        dataItems.release();
                    }
                });

                if (firstStart) {
                    firstStart = false;
                    fireMessage(CommonIdentifiers.W2P_REQUEST_SETTINGS, null);
                    fireMessage(CommonIdentifiers.W2P_REQUEST_CARD,
                            "" + preferences.getSelectedDeck());
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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (viewPager.getCurrentItem().x == 0) {
            return reviewFragment.onKeyDown(keyCode, event);
        }
        return false;
    }

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
    }

    @Override
    public void onResume() {
        Log.d(getClass().getName(), "MainActivity.onResume");
        super.onResume();
        if (preferences != null) {
            preferences.load();
        }
        fireMessage(CommonIdentifiers.W2P_REQUEST_DECKS, null);
    }

    interface JsonReceiver {
        void onJsonReceive(String path, JSONObject json);
    }

    interface AmbientStatusReceiver {
        void onExitAmbient();

        void onEnterAmbient();
    }

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
                    Log.e(TAG, "JSONException " + e);
                }
            }
            for (JsonReceiver jsr : jsonReceivers) {
                jsr.onJsonReceive(path, js);
            }

        }
    }

    private class PagerAdapter extends FragmentGridPagerAdapter {
        List<Fragment> fragmentList = null;

        public PagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
            fragmentList = new ArrayList<>();
        }

        @Override
        public Fragment getFragment(int x, int y) {
            return fragmentList.get(y);
        }

        //        @Override
        //        public Fragment getItem(int position) {
        //            return fragmentList.get(position);
        //        }
        //
        //        @Override
        //        public int getCount() {
        //            return fragmentList.size();
        //        }

        public void addFragment(Fragment fragment) {
            fragmentList.add(fragment);
            notifyDataSetChanged();
        }

        @Override
        public int getRowCount() {
            return 1;
        }

        @Override
        public int getColumnCount(int i) {
            return 2;
        }
    }
}

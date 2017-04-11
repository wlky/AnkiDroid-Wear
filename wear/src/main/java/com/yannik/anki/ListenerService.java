package com.yannik.anki;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * @author Created by Yannik on 12.03.2015.
 */
public class ListenerService extends WearableListenerService {
    private static final String TAG = "ListenerService";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.v(TAG, "Message received ");

        final String message = new String(messageEvent.getData());
        Log.v(TAG, "Message received on watch path is: " + messageEvent.getPath());
        Log.v(TAG, "Message received on watch data is: " + message);

        Intent messageIntent = new Intent();
        messageIntent.setAction(Intent.ACTION_SEND);
        messageIntent.putExtra("path", messageEvent.getPath());
        messageIntent.putExtra("message", new String(messageEvent.getData()));
        LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);

        super.onMessageReceived(messageEvent);
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        boolean newData = false;
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED && event.getDataItem().getUri().getPath().equals("/image/card")) {
                DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                for(String name : dataMapItem.getDataMap().keySet()) {
                    WearMainActivity.availableAssets.put(name, dataMapItem.getDataMap().getAsset(name));
                    Log.v(TAG, "Image received on watch is: " + name);

                    newData = true;


                }
                // Do something with the bitmap


            }else  if (event.getType() == DataEvent.TYPE_DELETED && event.getDataItem().getUri().getPath().equals("/image/card")){
                DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                for(String name : dataMapItem.getDataMap().keySet()) {
                    WearMainActivity.availableAssets.remove(name);
                    Log.v(TAG, "Image deleted on watch is: " + name);
                }
            }
        }
        if(newData) {
            Intent messageIntent = new Intent();
            messageIntent.setAction(Intent.ACTION_SEND);
            messageIntent.putExtra("path", ReviewFragment.W2W_RELOAD_HTML_FOR_MEDIA);
            messageIntent.putExtra("message", "");
            LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
        }
    }
}
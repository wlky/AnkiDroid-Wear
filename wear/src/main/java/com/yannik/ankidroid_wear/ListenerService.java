package com.yannik.ankidroid_wear;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by Yannik on 12.03.2015.
 */
public class ListenerService extends WearableListenerService {
    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
            final String message = new String(messageEvent.getData());
            Log.v("myTag", "Message path received on watch is: " + messageEvent.getPath());
            Log.v("myTag", "Message received on watch is: " + message);

            Intent messageIntent = new Intent();
            messageIntent.setAction(Intent.ACTION_SEND);
            messageIntent.putExtra("path", messageEvent.getPath());
            messageIntent.putExtra("message", new String(messageEvent.getData()));
            LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);


            super.onMessageReceived(messageEvent);

    }
}
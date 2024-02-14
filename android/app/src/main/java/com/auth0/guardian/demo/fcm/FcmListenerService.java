/*
 * Copyright (c) 2020 Auth0 (http://auth0.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.auth0.guardian.demo.fcm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.auth0.android.authentication.storage.SharedPreferencesStorage;
import com.auth0.android.guardian.sdk.Guardian;
import com.auth0.android.guardian.sdk.ParcelableNotification;
import com.auth0.guardian.demo.BuildConfig;
import com.auth0.guardian.demo.Constants;
import com.auth0.guardian.demo.NotificationActivity;
import com.auth0.guardian.demo.R;
import com.auth0.guardian.demo.events.GuardianNotificationReceivedEvent;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.greenrobot.eventbus.EventBus;

import java.util.Random;

public class FcmListenerService extends FirebaseMessagingService {

    private static final String TAG = FcmListenerService.class.getName();
    public static final String NOTIFICATION_CHANNEL_ID = "guardian-demo-channel-id";
    public static final String NOTIFICATION_CHANNEL_NAME = "MFA Notification Demo Channel";
    public static final String NOTIFICATION_CHANNEL_DESCRIPTION = "You have received a Push Notification";

    public static final int NOTIFICATION_ID = 1;
    private NotificationManager mNotificationManager;
    static Context ctx;

    private static final Gson JSON = new GsonBuilder().create();

    /**
     * Called when message is received.
     *
     * @param message The message instance
     */
    @Override
    public void onMessageReceived(RemoteMessage message) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, String.format("Received FCM message from: %s with data: %s", message.getFrom(), message.getData()));
        }

        try {
            ParcelableNotification notification = Guardian.parseNotification(message.getData());
            EventBus eventBus = EventBus.getDefault();
            if (eventBus.hasSubscriberForEvent(GuardianNotificationReceivedEvent.class)) {
                eventBus.post(new GuardianNotificationReceivedEvent(notification));
            } else {
                Log.d(TAG, "Dropping notification. App not is not handling notifications ATM.");
            }
            // Save notification for transaction to Shared Preferences
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(Constants.NOTIFICATION, JSON.toJson(message.getData()));
            editor.apply();
            sendNotification(NOTIFICATION_CHANNEL_DESCRIPTION);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Received a notification that is not a Guardian Notification", e);
        }
    }


    @Override
    public void onNewToken(String token) {
        // Use updated token and notify our app's server of any changes (if applicable).
        Log.w(TAG, "Should refresh token!");
    }
    private void sendNotification(String msg) {

        Intent intent = new Intent(this, NotificationActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                intent, PendingIntent.FLAG_IMMUTABLE);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        String channelId  = getString(R.string.push_notification_channel_id);
        String channelName = getString(R.string.push_notification_channel_name);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
                this,
                channelId)
                .setContentText(msg)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(android.R.drawable.ic_secure)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(contentIntent);;

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }
        Random notification_id = new Random();
        //notificationManager.notify(notification_id.nextInt(100), notificationBuilder.build());
        notificationManager.notify(Constants.NOTIFICATION_ID, notificationBuilder.build());
    }
}

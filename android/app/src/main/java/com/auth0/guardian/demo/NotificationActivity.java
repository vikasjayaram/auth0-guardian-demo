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

package com.auth0.guardian.demo;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.auth0.android.guardian.sdk.Guardian;
import com.auth0.android.guardian.sdk.ParcelableNotification;
import com.auth0.android.guardian.sdk.networking.Callback;
import com.auth0.guardian.demo.events.GuardianNotificationReceivedEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Map;

public class NotificationActivity extends AppCompatActivity {

    private TextView userText;
    private TextView browserText;
    private TextView osText;
    private TextView locationText;
    private TextView dateText;
    private Guardian guardian;
    private ParcelableEnrollment enrollment;
    private ParcelableNotification notification;
    private static final Gson JSON = new GsonBuilder().create();

    private SharedPreferences sharedPreferences;
    static Intent getStartIntent(@NonNull Context context,
                                 @NonNull ParcelableNotification notification,
                                 @NonNull ParcelableEnrollment enrollment) {
        if (!enrollment.getId().equals(notification.getEnrollmentId())) {
            final String message = String.format("Notification doesn't match enrollment (%s != %s)",
                    notification.getEnrollmentId(), enrollment.getId());
            throw new IllegalArgumentException(message);
        }

        Intent intent = new Intent(context, NotificationActivity.class);
        intent.putExtra(Constants.ENROLLMENT, enrollment);
        intent.putExtra(Constants.NOTIFICATION, notification);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);
        // Clear the notification
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.cancel(Constants.NOTIFICATION_ID);

        guardian = new Guardian.Builder()
                .url(Uri.parse(getString(R.string.guardian_url)))
                .enableLogging()
                .build();

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String enrollmentJSON = sharedPreferences.getString(Constants.ENROLLMENT, null);
        String notificationJSON = sharedPreferences.getString(Constants.NOTIFICATION, null);
        Intent intent = getIntent();
        if (enrollmentJSON != null) {
            enrollment = ParcelableEnrollment.fromJSON(enrollmentJSON);
        } else {
            enrollment = intent.getParcelableExtra(Constants.ENROLLMENT);
        }
        if (notificationJSON != null) {
            Log.d("notification", notificationJSON);
            java.lang.reflect.Type type = new TypeToken<HashMap<String, String>>(){}.getType();
            HashMap<String, String> newMap = JSON.fromJson(notificationJSON, type);


            notification = ParcelableNotification.parse(newMap);
            Log.d("new map", newMap.toString());
            Log.d("new notification", notification.getBrowserName());
        } else {
            notification = intent.getParcelableExtra(Constants.NOTIFICATION);
        }
        setupUI();
        updateUI();
    }

    private void setupUI() {
        userText = findViewById(R.id.userText);
        browserText = findViewById(R.id.browserText);
        osText = findViewById(R.id.osText);
        locationText = findViewById(R.id.locationText);
        dateText = findViewById(R.id.dateText);

        Button rejectButton = findViewById(R.id.rejectButton);
        rejectButton.setOnClickListener(v -> rejectRequested());

        Button allowButton = findViewById(R.id.allowButton);
        allowButton.setOnClickListener(v -> allowRequested());
    }

    private void updateUI() {
//        userText.setText(enrollment.getUserId());
        browserText.setText(
                String.format("%s, %s",
                        notification.getBrowserName(),
                        notification.getBrowserVersion()));
        osText.setText(
                String.format("%s, %s",
                        notification.getOsName(),
                        notification.getOsVersion()));
        locationText.setText(notification.getLocation());
        dateText.setText(notification.getDate().toString());
    }

    private void rejectRequested() {
        guardian
                .reject(notification, enrollment)
                .start(new DialogCallback<>(this,
                        R.string.progress_title_please_wait,
                        R.string.progress_message_reject,
                        new Callback<Void>() {
                            @Override
                            public void onSuccess(Void response) {
                                Log.d("reject", "push rejected");
                                finish();
                            }

                            @Override
                            public void onFailure(Throwable exception) {
                                exception.printStackTrace();

                            }
                        }));
    }

    private void allowRequested() {
        guardian
                .allow(notification, enrollment)
                .start(new DialogCallback<>(this,
                        R.string.progress_title_please_wait,
                        R.string.progress_message_allow,
                        new Callback<Void>() {
                            @Override
                            public void onSuccess(Void response) {
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putString(Constants.NOTIFICATION,  null);
                                editor.apply();
                                finish();
                            }

                            @Override
                            public void onFailure(Throwable exception) {
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putString(Constants.NOTIFICATION,  null);
                                editor.apply();
                            }
                        }));
    }
}

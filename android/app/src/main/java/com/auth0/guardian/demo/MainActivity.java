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

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
//import androidx.browser.customtabs.CustomTabsIntent;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.auth0.android.Auth0;
import com.auth0.android.authentication.AuthenticationAPIClient;
import com.auth0.android.authentication.AuthenticationException;
import com.auth0.android.authentication.ParameterBuilder;
import com.auth0.android.authentication.storage.SecureCredentialsManager;
import com.auth0.android.authentication.storage.SharedPreferencesStorage;
import com.auth0.android.guardian.sdk.Guardian;
import com.auth0.android.guardian.sdk.GuardianException;
import com.auth0.android.guardian.sdk.ParcelableNotification;
import com.auth0.android.guardian.sdk.networking.Callback;
import com.auth0.android.provider.TokenValidationException;
import com.auth0.android.provider.WebAuthProvider;
import com.auth0.android.request.DefaultClient;
import com.auth0.android.result.Credentials;
import com.auth0.guardian.demo.events.GuardianNotificationReceivedEvent;
import com.auth0.guardian.demo.fcm.FcmListenerService;
import com.auth0.guardian.demo.fcm.FcmUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Collections;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements FcmUtils.FcmTokenListener {

    private static final String TAG = MainActivity.class.getName();

    private static final int ENROLL_REQUEST = 123;
    private static final int ENROLL_WITH_RT_REQUEST = 124;

    private View loadingView;
    private View enrollView;
    private View accountView;
    private TextView deviceNameText;
    private TextView fcmTokenText;
    private TextView userText, totp;
    private EditText username, password;
    private EventBus eventBus;
    private Guardian guardian;
    private ParcelableEnrollment enrollment;
    private String fcmToken;
    private String refreshToken;
    private boolean silentAccept = false;
    private Auth0 auth0;
    private SecureCredentialsManager credentialsManager;
    private AuthenticationAPIClient apiClient;
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "Notifications permission granted",Toast.LENGTH_SHORT)
                            .show();
                } else {
                    Toast.makeText(this, "FCM can't post notifications without POST_NOTIFICATIONS permission",
                            Toast.LENGTH_LONG).show();
                }
            });
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Auth0
        auth0 = new Auth0(this);
        DefaultClient netClient = new DefaultClient(10, 15, Collections.emptyMap(), true);

        auth0.setNetworkingClient(netClient);
        apiClient = new AuthenticationAPIClient(auth0);


        SharedPreferencesStorage storage = new SharedPreferencesStorage(this);
        credentialsManager = new SecureCredentialsManager(this,apiClient, storage);
        /*
         * Create Notification Channel
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create channel to show notifications.
            String channelId  = getString(R.string.push_notification_channel_id);
            String channelName = getString(R.string.push_notification_channel_name);
            NotificationManager notificationManager =
                    getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(new NotificationChannel(channelId,
                    channelName, NotificationManager.IMPORTANCE_HIGH));
        }
        setupUI();
        eventBus = EventBus.getDefault();
        eventBus.register(this);
        guardian = new Guardian.Builder()
                .url(Uri.parse(getString(R.string.guardian_url)))
                .enableLogging()
                .build();

        /*
         * The following fetch token call is NOT required in a production app
         * as the registration token is generated automatically by the Firebase SDK.
         * This is just here for display purposes on this Activity's layout.
         *
         * See: https://developers.google.com/cloud-messaging/android/android-migrate-iid-service
         */
        FcmUtils fcmUtils = new FcmUtils();
        fcmUtils.fetchFcmToken(this);
        //FcmListenerService.createChannelAndHandleNotifications(getApplicationContext());

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String enrollmentJSON = sharedPreferences.getString(Constants.ENROLLMENT, null);
        if (enrollmentJSON != null) {
            enrollment = ParcelableEnrollment.fromJSON(enrollmentJSON);
            updateUI();

            ParcelableNotification notification = getIntent().getParcelableExtra(Constants.NOTIFICATION);
            if (notification != null) {
                onPushNotificationReceived(notification);
            }
        }
        askNotificationPermission();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        eventBus.unregister(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ENROLL_REQUEST) {
            if (resultCode == RESULT_OK) {
                ParcelableEnrollment enrollment = data.getParcelableExtra(Constants.ENROLLMENT);
                updateEnrollment(enrollment);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void setupUI() {
        loadingView = findViewById(R.id.loadingLayout);
        enrollView = findViewById(R.id.enrollLayout);
        accountView = findViewById(R.id.accountLayout);
        deviceNameText = findViewById(R.id.deviceNameText);
        fcmTokenText = findViewById(R.id.fcmTokenText);
        userText = findViewById(R.id.userText);
        totp = findViewById(R.id.totp);

        deviceNameText.setText(Build.ID);

        Button loginButton = findViewById(R.id.loginButton);
        loginButton.setOnClickListener(v -> login());

        Button enrollWithATButton = findViewById(R.id.enrollWithAT);
        enrollWithATButton.setOnClickListener(v -> onEnrollRequested("login"));

        Button enrollWithRT = findViewById(R.id.enrollWithRT);
        enrollWithRT.setOnClickListener(v -> onEnrollRequested("withRT"));

        Button forceloginButton = findViewById(R.id.forceLogin);
        forceloginButton.setOnClickListener(v -> login());

        Button unenrollButton = findViewById(R.id.unenrollButton);
        unenrollButton.setOnClickListener(v -> onUnEnrollRequested());

        Button loginROPGButton = findViewById(R.id.login);
        loginROPGButton.setOnClickListener(v -> loginWithROPG());
        Thread t = new Thread() {

            @Override
            public void run() {
                try {
                    while (!isInterrupted()) {
                        Thread.sleep(1000);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateTOTPView();
                            }
                        });
                    }
                } catch (InterruptedException e) {
                }
            }
        };

        t.start();
    }

    public void onSilentCheckboxClicked(View view) {
        this.silentAccept = ((CheckBox) view).isChecked();
    }

    public void onNotificationListenerClicked(View view) {
        boolean enabled = ((CheckBox) view).isChecked();

        if (enabled) {
            eventBus.register(this);
        } else {
            eventBus.unregister(this);
        }
    }


    private void updateUI() {
        runOnUiThread(() -> {
            loadingView.setVisibility(fcmToken != null ? View.GONE : View.VISIBLE);
            if (enrollment == null) {
                //fcmTokenText.setText(fcmToken);
                accountView.setVisibility(View.GONE);
                enrollView.setVisibility(fcmToken != null ? View.VISIBLE : View.GONE);
            } else {
                userText.setText(enrollment.getUserId());
                accountView.setVisibility(fcmToken != null ? View.VISIBLE : View.GONE);
                enrollView.setVisibility(View.GONE);
            }
        });
    }

    private void updateTOTPView() {
        if (enrollment != null) {
            totp.setText(Guardian.getOTPCode(enrollment));
        } else {
            totp.setText("No TOTP to Display");
        }
    }

    private void onEnrollRequested(String action) {
        Intent enrollIntent = EnrollActivity
                .getStartIntent(this, deviceNameText.getText().toString(), fcmToken, action);
        startActivityForResult(enrollIntent, ENROLL_REQUEST);
    }

    private void onEnrollWithRTRequested() {
        Intent enrollIntent = EnrollActivity
                .getStartIntent(this, deviceNameText.getText().toString(), fcmToken, "withRT");
        startActivityForResult(enrollIntent, ENROLL_WITH_RT_REQUEST);
    }

    private void onConfirmEnrollmentRequested() {
        Intent enrollIntent = EnrollActivity
                .getStartIntent(this, deviceNameText.getText().toString(), fcmToken, "challenge");
        startActivityForResult(enrollIntent, ENROLL_WITH_RT_REQUEST);
    }

    private void onUnEnrollRequested() {
        guardian.delete(enrollment)
                .start(new DialogCallback<>(this,
                        R.string.progress_title_please_wait,
                        R.string.progress_message_unenroll,
                        new Callback<Void>() {
                            @Override
                            public void onSuccess(Void response) {
                                updateEnrollment(null);
                            }

                            @Override
                            public void onFailure(Throwable exception) {
                                if (exception instanceof GuardianException) {
                                    GuardianException guardianException = (GuardianException) exception;
                                    if (guardianException.isEnrollmentNotFound()) {
                                        // the enrollment doesn't exist on the server
                                        updateEnrollment(null);
                                    }
                                }
                            }
                        }));
    }

    private void updateEnrollment(ParcelableEnrollment enrollment) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Constants.ENROLLMENT, enrollment != null ? enrollment.toJSON() : null);
        editor.apply();

        this.enrollment = enrollment;

        updateUI();
    }

    private void updateRefreshToken(String rt) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Constants.REFRESH_TOKEN, rt != null ? rt : null);
        editor.apply();

        this.refreshToken = rt;
    }

    private void updateMFAAccessToken(String at) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Constants.MFA_ACCESS_TOKEN, at != null ? at : null);
        editor.apply();
    }
    private void onPushNotificationReceived(ParcelableNotification notification) {
        if (this.silentAccept) {
             guardian.allow(notification,enrollment).start(new SilentCallback<>());
        }
        else {
            Log.d("onPush", "onPushNotificationReceived block");
            Intent intent = NotificationActivity
                    .getStartIntent(this, notification, enrollment);

            startActivity(intent);
        }
    }


    @Override
    public void onFcmTokenObtained(String fcmToken) {
        this.fcmToken = fcmToken;

        updateUI();
    }

    @Override
    public void onFcmFailure(Throwable exception) {
        Log.e(TAG, "Error obtaining FCM token", exception);
        new AlertDialog.Builder(this)
                .setTitle(R.string.alert_title_error)
                .setMessage(getString(R.string.alert_message_fcm_error))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        finish();
                    }
                })
                .create()
                .show();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onGuardianNotificationReceived(GuardianNotificationReceivedEvent event) {
        onPushNotificationReceived(event.getData());

        //TODO: Stop listening for push notification ...
    }

    private void login() {
        ParameterBuilder builder = ParameterBuilder.newBuilder();
        Map<String, String> authenticationParameters = builder.set("prompt", "login").asDictionary();
        WebAuthProvider.login(auth0)
                .withScheme("demo")
                .withTrustedWebActivity()
                .withParameters(authenticationParameters)
                .withAudience(getString(R.string.mfa_audience))
                .withScope("openid profile enroll offline_access")
                .start(MainActivity.this, new com.auth0.android.callback.Callback<Credentials, AuthenticationException>() {

                    @Override
                    public void onFailure(@NonNull final AuthenticationException exception) {
                        Toast.makeText(MainActivity.this, "Error: " + exception.getCode(), Toast.LENGTH_SHORT).show();
                        if (exception.isIdTokenValidationError()) {
                            if (exception.getCause() instanceof TokenValidationException) {
                                exception.printStackTrace();
                            }
                        }
                    }

                    @Override
                    public void onSuccess(@Nullable final Credentials credentials) {
                        //credentialsManager.saveCredentials(credentials);
                        updateRefreshToken(credentials.getRefreshToken());
                        Log.d("AT", credentials.getAccessToken());
                        Log.d("ID", credentials.getIdToken());
                        Log.d("RT", credentials.getRefreshToken());
                        Log.d("Scope", credentials.getScope());
                        Log.d("Expires At", credentials.getExpiresAt().toString());
                        userText.setText(credentials.getUser().getId());
                        accountView.setVisibility(View.VISIBLE);
                        enrollView.setVisibility(View.GONE);
                    }
                });
    }

    public void loginWithROPG() {
        username = (EditText)findViewById(R.id.username);
        password = (EditText)findViewById(R.id.password);
        apiClient.login(username.getText().toString(), password.getText().toString(), "Username-Password-Authentication")
                .validateClaims()
                .setAudience(getString(R.string.mfa_audience).toString())
                .setScope("openid profile email enroll")
                .start(new com.auth0.android.callback.Callback<Credentials, AuthenticationException>() {
                    @Override
                    public void onSuccess(Credentials credentials) {
                        updateMFAAccessToken(credentials.getAccessToken());
                        onEnrollRequested("withROPG");
                    }

                    @Override
                    public void onFailure(@NonNull AuthenticationException e) {
                        Toast.makeText(MainActivity.this, "Error: " + e.getCode(), Toast.LENGTH_LONG).show();
                    }
                });

    }
    private void askNotificationPermission() {
        // This is only necessary for API Level > 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                // FCM SDK (and your app) can post notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

}

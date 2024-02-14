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

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.auth0.android.Auth0;
import com.auth0.android.authentication.AuthenticationAPIClient;
import com.auth0.android.authentication.AuthenticationException;
import com.auth0.android.authentication.ParameterBuilder;
import com.auth0.android.authentication.storage.CredentialsManagerException;
import com.auth0.android.authentication.storage.SecureCredentialsManager;
import com.auth0.android.authentication.storage.SharedPreferencesStorage;
import com.auth0.android.guardian.sdk.CurrentDevice;
import com.auth0.android.guardian.sdk.DeviceAPIClient;
import com.auth0.android.guardian.sdk.Enrollment;
import com.auth0.android.guardian.sdk.Guardian;
import com.auth0.android.guardian.sdk.GuardianAPIClient;
import com.auth0.android.guardian.sdk.networking.Callback;
import com.auth0.android.provider.AuthCallback;
import com.auth0.android.provider.TokenValidationException;
import com.auth0.android.provider.WebAuthProvider;
import com.auth0.android.result.Credentials;
import com.google.gson.Gson;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;

public class EnrollActivity extends AppCompatActivity {

    private static final String TAG = EnrollActivity.class.getName();

    private static final String DEVICE_NAME = "com.auth0.guardian.demo.EnrollActivity.DEVICE_NAME";
    private static final String FCM_TOKEN = "com.auth0.guardian.demo.EnrollActivity.FCM_TOKEN";
    private static final String ACTION = "com.auth0.guardian.demo.EnrollActivity.ACTION";

    private Auth0 auth0;
    private SecureCredentialsManager credentialsManager;
    private AuthenticationAPIClient apiClient;
    private EnrollmentResponseModel enrollmentResponseModel;
    private Guardian guardian;
    private String deviceName;
    private String fcmToken;
    private String action;
    private String mfaIronToken;

    static Intent getStartIntent(@NonNull Context context,
                                 @NonNull String deviceName,
                                 @NonNull String fcmToken,
                                 @NonNull String action) {
        Intent intent = new Intent(context, EnrollActivity.class);
        intent.putExtra(DEVICE_NAME, deviceName);
        intent.putExtra(FCM_TOKEN, fcmToken);
        intent.putExtra(ACTION, action);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Auth0
        auth0 = new Auth0(this);
        apiClient = new AuthenticationAPIClient(auth0);
        SharedPreferencesStorage storage = new SharedPreferencesStorage(this);
        credentialsManager = new SecureCredentialsManager(this,apiClient, storage);

        setContentView(R.layout.inprogress);

        setupGuardian();
        /*
        Intent intent = getIntent();

        action = intent.getStringExtra(ACTION);

        */
        //login();
    }

    public void enrollWithUri(String enrollmentData) {
        try {
            KeyPair keyPair = generateKeyPair();
            CurrentDevice device = new CurrentDevice(this, fcmToken, deviceName);
            guardian.enroll(enrollmentData, device, keyPair)
                    .start(new DialogCallback<>(this,
                            R.string.progress_title_please_wait,
                            R.string.progress_message_enroll,
                            new Callback<Enrollment>() {
                                @Override
                                public void onSuccess(Enrollment enrollment) {
                                    Log.d(TAG, "enroll success");
                                    onEnrollSuccess(enrollment);
                                }

                                @Override
                                public void onFailure(Throwable exception) {
                                    Log.d(TAG, "enroll Failed");
                                }
                            }));
        } catch (IllegalArgumentException exception) {
            Log.e(TAG, "enroll throw an exception", exception);
            onEnrollFailure(exception);
        }
    }

    private void login() {
        WebAuthProvider.login(auth0)
                .withScheme("demo")
                .withAudience(getString(R.string.mfa_audience))
                .withScope("openid profile enroll remove:authenticators offline_access")
                .start(EnrollActivity.this, new com.auth0.android.callback.Callback<Credentials, AuthenticationException>() {

                    @Override
                    public void onFailure(@NonNull final AuthenticationException exception) {
                        Toast.makeText(EnrollActivity.this, "Error: " + exception.getCode(), Toast.LENGTH_SHORT).show();
                        if (exception.isIdTokenValidationError()) {
                            if (exception.getCause() instanceof TokenValidationException) {
                                exception.printStackTrace();
                            }
                        }
                    }

                    @Override
                    public void onSuccess(@Nullable final Credentials credentials) {
                        //credentialsManager.saveCredentials(credentials);
                        Log.d("AT", credentials.getAccessToken());
                        Log.d("ID", credentials.getIdToken());
                        Log.d("RT", credentials.getRefreshToken());
                        Log.d("Scope", credentials.getScope());
                        Log.d("Expires At", credentials.getExpiresAt().toString());
                        associate(credentials.getAccessToken());
                    }
                });
    }

    class EnrollmentResponseModel {
        protected String barcode_uri;
        protected String oob_code;
        public EnrollmentResponseModel(String barcode_uri, String oob_code){
            this.barcode_uri = barcode_uri;
            this.oob_code = oob_code;
        }
    }

    private void associate(String mfaToken) {
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");

        RequestBody payload = RequestBody.create(JSON,getString(R.string.Associate_Push_Payload));

        final Request.Builder reqBuilder = new Request.Builder()
                .post(payload)
                .url(getString(R.string.Auth0_Associate));
        if (mfaToken!=null) {
            reqBuilder.addHeader("Authorization", "Bearer " + mfaToken);
        }

        OkHttpClient client = new OkHttpClient();
        Request request = reqBuilder.build();

        client.newCall(request).enqueue(new com.squareup.okhttp.Callback() {
            @Override
            public void onFailure(Request request, final IOException e) {
                runOnUiThread(() -> Toast.makeText(EnrollActivity.this, "An error occurred" + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(final Response response) throws IOException {

                String body = response.body().string();
                Gson gson = new Gson();
                enrollmentResponseModel = gson.fromJson(body, EnrollmentResponseModel.class);
                Log.d("barcode", body);
                runOnUiThread(() -> enrollWithUri(enrollmentResponseModel.barcode_uri));
            }
        });

    }

    private void confirmEnrollment(String mfaToken, String authenticator_id) {
        Log.d(TAG, mfaToken);
        Log.d(TAG, authenticator_id);
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        String challenge = "{\"challenge_type\":\"oob\", \"client_id\":\"" + getString(R.string.com_auth0_client_id) + "\", \"authenticator_id\":\"push|" + authenticator_id + "\", \"mfa_token\":\"" + mfaToken + "\"}";
        Log.d(TAG, challenge);
        RequestBody payload = RequestBody.create(JSON,challenge);
        final Request.Builder reqBuilder = new Request.Builder()
                .post(payload)
                .url(getString(R.string.Auth0_Challenge));
        if (mfaToken!=null) {
            reqBuilder.addHeader("Authorization", "Bearer " + mfaToken);
        }

        OkHttpClient client = new OkHttpClient();
        Request request = reqBuilder.build();

        client.newCall(request).enqueue(new com.squareup.okhttp.Callback() {
            @Override
            public void onFailure(Request request, final IOException e) {
                runOnUiThread(() -> Toast.makeText(EnrollActivity.this, "An error occurred" + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(final Response response) throws IOException {

                String body = response.body().string();
                Gson gson = new Gson();
                Log.d("tokens", body);
//                Credentials credentials = gson.fromJson(body, Credentials.class);
//                credentialsManager.saveCredentials(credentials);
//                updateRefreshToken(credentials.getRefreshToken());
//                runOnUiThread(() -> onChallengeSuccess());
            }
        });

    }


    private void setupGuardian() {
        Intent intent = getIntent();
        deviceName = intent.getStringExtra(DEVICE_NAME);
        fcmToken = intent.getStringExtra(FCM_TOKEN);
        action = intent.getStringExtra(ACTION);
        Log.d("fcmToken", fcmToken);
        if (deviceName == null || fcmToken == null) {
            throw new IllegalStateException("Missing deviceName or fcmToken");
        }

        guardian = new Guardian.Builder()
                .url(Uri.parse(getString(R.string.guardian_url)))
                .enableLogging()
                .build();
        if (action != null) {
            switch (action) {
                case "login":
                    login();
                    break;
                case "withRT":
                    enrollMFAWithRefreshToken();
                    break;
                case "withROPG":
                    enrollMFAWithROPG();
                    break;
                case "challenge":
                    //confirmEnrollment(intent.getStringExtra("mfaIronToken"), intent.getStringExtra("authenticator_id"));
                    break;
                default:
                    break;
            }
        }
    }


    private void onEnrollSuccess(Enrollment enrollment) {
        Intent data = new Intent();
        ParcelableEnrollment parcelableEnrollment = new ParcelableEnrollment(enrollment);
        data.putExtra(Constants.ENROLLMENT, parcelableEnrollment);
        data.putExtra("mfaIronToken", mfaIronToken);
        setResult(RESULT_OK, data);
//        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
//        SharedPreferences.Editor editor = sharedPreferences.edit();
//        editor.putString(Constants.ENROLLMENT, parcelableEnrollment != null ? parcelableEnrollment.toJSON() : null);
//        editor.apply();
//        Log.d(TAG, action);
//
//        Handler handler = new Handler();
//
//        // Create a Runnable that calls the doSomething() method
//        Runnable runnable = new Runnable() {
//            @Override
//            public void run() {
//                if (action != null && action.equals("withRT")) {
//                    Log.d(TAG, "action challenge");
//                    confirmEnrollment(mfaIronToken, enrollment.getId());
//                }            }
//        };

        // Post the Runnable with a delay
        //handler.postDelayed(runnable, 3000);
        finish();
    }

    private void onChallengeSuccess() {
        finish();
    }
    private void onEnrollFailure(final Throwable exception) {
        runOnUiThread(() -> new AlertDialog.Builder(EnrollActivity.this)
                .setTitle(R.string.alert_title_error)
                .setMessage(exception.getMessage())
                .setPositiveButton(
                        android.R.string.ok,
                        (dialog, which) -> dialog.dismiss())
                .create()
                .show());
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048); // at least 2048 bits!
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error generating keys", e);
        }

        return null;
    }
    private void updateRefreshToken(String rt) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Constants.REFRESH_TOKEN, rt != null ? rt : null);
        editor.apply();

    }
    private void enrollMFAWithRefreshToken() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String rt = sharedPreferences.getString(Constants.REFRESH_TOKEN, null);
        Log.d("withRT", rt);
        apiClient.renewAuth(rt)
                .addParameter("scope", "openid profile email offline_access enroll device:enroll")
                .addParameter("deviceEnroll", "yes")
                .start(new com.auth0.android.callback.Callback<Credentials, AuthenticationException>() {
                    @Override
                    public void onSuccess(Credentials credentials) {
                        Log.d("AT", credentials.getAccessToken());
                        Log.d("ID", credentials.getIdToken());
                        Log.d("RT", credentials.getRefreshToken());
                        Log.d("Scope", credentials.getScope());
                        Log.d("Expires At", credentials.getExpiresAt().toString());
                    }

                    @Override
                    public void onFailure(@NonNull AuthenticationException e) {
                        e.printStackTrace();
                        if(e.isMultifactorRequired()) {
                            Log.d(e.getCode(), e.getValue("mfa_token").toString());
                            mfaIronToken = e.getValue("mfa_token").toString();
                            associate(mfaIronToken);
                        }
                    }
                });
    }

    private void enrollMFAWithMFAAudience () {

    }

    private void enrollMFAWithROPG() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String at = sharedPreferences.getString(Constants.MFA_ACCESS_TOKEN, null);
        Log.d("withROPG", at);
        associate(at);
    }
}

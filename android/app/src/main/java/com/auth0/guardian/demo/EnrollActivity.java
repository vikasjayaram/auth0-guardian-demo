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
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.auth0.android.Auth0;
import com.auth0.android.authentication.AuthenticationException;
import com.auth0.android.guardian.sdk.CurrentDevice;
import com.auth0.android.guardian.sdk.Enrollment;
import com.auth0.android.guardian.sdk.Guardian;
import com.auth0.android.guardian.sdk.networking.Callback;
import com.auth0.android.provider.AuthCallback;
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

public class EnrollActivity extends AppCompatActivity {

    private static final String TAG = EnrollActivity.class.getName();

    private static final String DEVICE_NAME = "com.auth0.guardian.demo.EnrollActivity.DEVICE_NAME";
    private static final String FCM_TOKEN = "com.auth0.guardian.demo.EnrollActivity.FCM_TOKEN";

    private Auth0 auth0;

    private Guardian guardian;
    private String deviceName;
    private String fcmToken;
    private String mfaToken;

    static Intent getStartIntent(@NonNull Context context,
                                 @NonNull String deviceName,
                                 @NonNull String fcmToken) {
        Intent intent = new Intent(context, EnrollActivity.class);
        intent.putExtra(DEVICE_NAME, deviceName);
        intent.putExtra(FCM_TOKEN, fcmToken);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth0 = new Auth0(this);
        auth0.setOIDCConformant(true);
        auth0.setLoggingEnabled(true);

        setContentView(R.layout.inprogress);

        setupGuardian();
        login();
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
                .withScope("openid profile enroll")
                .start(EnrollActivity.this, new AuthCallback() {
                    @Override
                    public void onFailure(@NonNull Dialog dialog) {
                        Log.e(TAG, "login throw an exception");
                    }

                    @Override
                    public void onFailure(AuthenticationException exception) {
                        Log.e(TAG, "login throw an exception", exception);
                        finish();
                    }

                    @Override
                    public void onSuccess(@NonNull Credentials credentials) {
                        mfaToken = credentials.getAccessToken();

                       associate();

                    }
                });
    }

    class EnrollmentResponseModel {
        protected String barcode_uri;
        public EnrollmentResponseModel(String barcode_uri){
            this.barcode_uri = barcode_uri;
        }
    }

    private void associate() {
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
                EnrollmentResponseModel enrollmentResponseModel = gson.fromJson(body, EnrollmentResponseModel.class);
                Log.d("barcode", body);
                runOnUiThread(() -> enrollWithUri(enrollmentResponseModel.barcode_uri));
            }
        });

    }


    private void setupGuardian() {
        Intent intent = getIntent();
        deviceName = intent.getStringExtra(DEVICE_NAME);
        fcmToken = intent.getStringExtra(FCM_TOKEN);

        if (deviceName == null || fcmToken == null) {
            throw new IllegalStateException("Missing deviceName or fcmToken");
        }

        guardian = new Guardian.Builder()
                .url(Uri.parse(getString(R.string.guardian_url)))
                .enableLogging()
                .build();
    }


    private void onEnrollSuccess(Enrollment enrollment) {
        Intent data = new Intent();
        ParcelableEnrollment parcelableEnrollment = new ParcelableEnrollment(enrollment);
        data.putExtra(Constants.ENROLLMENT, parcelableEnrollment);
        setResult(RESULT_OK, data);
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
}

package com.auth0.guardian.demo;

import com.auth0.android.guardian.sdk.networking.Callback;

public class SilentCallback<T> implements Callback<T> {

    @Override
    public void onSuccess(T response) {

    }

    @Override
    public void onFailure(Throwable exception) {

    }
}

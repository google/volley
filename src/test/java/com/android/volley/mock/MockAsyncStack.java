/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.volley.mock;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.AsyncHttpStack;
import com.android.volley.toolbox.HttpResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class MockAsyncStack extends AsyncHttpStack {

    private HttpResponse mResponseToReturn;

    private IOException mExceptionToThrow;

    private String mLastUrl;

    private Map<String, String> mLastHeaders;

    private byte[] mLastPostBody;

    public String getLastUrl() {
        return mLastUrl;
    }

    public Map<String, String> getLastHeaders() {
        return mLastHeaders;
    }

    public byte[] getLastPostBody() {
        return mLastPostBody;
    }

    public void setResponseToReturn(HttpResponse response) {
        mResponseToReturn = response;
    }

    public void setExceptionToThrow(IOException exception) {
        mExceptionToThrow = exception;
    }

    @Override
    public void executeRequest(
            Request<?> request, Map<String, String> additionalHeaders, OnRequestComplete callback) {
        if (mExceptionToThrow != null) {
            callback.onError(mExceptionToThrow);
            return;
        }
        mLastUrl = request.getUrl();
        mLastHeaders = new HashMap<>();
        try {
            if (request.getHeaders() != null) {
                mLastHeaders.putAll(request.getHeaders());
            }
        } catch (AuthFailureError authFailureError) {
            callback.onAuthError(authFailureError);
            return;
        }
        if (additionalHeaders != null) {
            mLastHeaders.putAll(additionalHeaders);
        }
        try {
            mLastPostBody = request.getBody();
        } catch (AuthFailureError e) {
            mLastPostBody = null;
        }
        callback.onSuccess(mResponseToReturn);
    }

    @Override
    public void setNonBlockingExecutor(ExecutorService executor) {
        // do nothing
    }

    @Override
    public void setBlockingExecutor(ExecutorService executor) {
        // do nothing
    }
}

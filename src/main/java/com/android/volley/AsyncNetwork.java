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

package com.android.volley;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AsyncNetwork implements Network {

    public interface OnRequestComplete {
        void onSuccess(NetworkResponse networkResponse);

        void onError(VolleyError volleyError);
    }

    public abstract void performRequest(Request<?> request, OnRequestComplete callback);

    @Override
    public NetworkResponse performRequest(Request<?> request) throws VolleyError {
        // Is there a better way to go about this?
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Response> entry = new AtomicReference<>();
        performRequest(
                request,
                new OnRequestComplete() {
                    @Override
                    public void onSuccess(NetworkResponse networkResponse) {
                        entry.set(new Response(networkResponse, /* error= */ null));
                        latch.countDown();
                    }

                    @Override
                    public void onError(VolleyError volleyError) {
                        entry.set(new Response(/* networkResponse= */ null, volleyError));
                        latch.countDown();
                    }
                });
        try {
            latch.await();
        } catch (InterruptedException e) {
            VolleyLog.e(e, "while waiting for CountDownLatch");
            Thread.currentThread().interrupt();
            throw new VolleyError(e);
        }

        Response response = entry.get();
        if (response.networkResponse != null) {
            return response.networkResponse;
        } else {
            throw response.volleyError;
        }
    }

    /**
     * This method sets the non blocking executor to be used by the stack for non-blocking tasks. If
     * you are not using an {@link com.android.volley.toolbox.AsyncHttpStack}, this should do
     * nothing. This method must be called before performing any requests if you are using an
     * AsyncHttpStack.
     */
    @RestrictTo({RestrictTo.Scope.SUBCLASSES, RestrictTo.Scope.LIBRARY_GROUP})
    public abstract void setNonBlockingExecutorForStack(ExecutorService executor);

    /**
     * This method sets the blocking executor to be used by the network and stack for potentially
     * blocking tasks. This method must be called before performing any requests. Only set the
     * executor for the stack if it is an instance of {@link
     * com.android.volley.toolbox.AsyncHttpStack}
     */
    @RestrictTo({RestrictTo.Scope.SUBCLASSES, RestrictTo.Scope.LIBRARY_GROUP})
    public abstract void setBlockingExecutor(ExecutorService executor);

    static class Response {
        NetworkResponse networkResponse;
        VolleyError volleyError;

        private Response(@Nullable NetworkResponse networkResponse, @Nullable VolleyError error) {
            this.networkResponse = networkResponse;
            volleyError = error;
        }
    }
}

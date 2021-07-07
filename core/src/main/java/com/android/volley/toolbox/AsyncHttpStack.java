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

package com.android.volley.toolbox;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.VolleyLog;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Asynchronous extension of the {@link BaseHttpStack} class.
 *
 * <p><b>WARNING</b>: This API is experimental and subject to breaking changes. Please see
 * https://github.com/google/volley/wiki/Asynchronous-Volley for more details.
 */
public abstract class AsyncHttpStack extends BaseHttpStack {
    private ExecutorService mBlockingExecutor;
    private ExecutorService mNonBlockingExecutor;

    public interface OnRequestComplete {
        /** Invoked when the stack successfully completes a request. */
        void onSuccess(HttpResponse httpResponse);

        /** Invoked when the stack throws an {@link AuthFailureError} during a request. */
        void onAuthError(AuthFailureError authFailureError);

        /** Invoked when the stack throws an {@link IOException} during a request. */
        void onError(IOException ioException);
    }

    /**
     * Makes an HTTP request with the given parameters, and calls the {@link OnRequestComplete}
     * callback, with either the {@link HttpResponse} or error that was thrown.
     *
     * @param request to perform
     * @param additionalHeaders to be sent together with {@link Request#getHeaders()}
     * @param callback to be called after retrieving the {@link HttpResponse} or throwing an error.
     */
    public abstract void executeRequest(
            Request<?> request, Map<String, String> additionalHeaders, OnRequestComplete callback);

    /**
     * This method sets the non blocking executor to be used by the stack for non-blocking tasks.
     * This method must be called before executing any requests.
     */
    @RestrictTo({RestrictTo.Scope.LIBRARY_GROUP})
    public void setNonBlockingExecutor(ExecutorService executor) {
        mNonBlockingExecutor = executor;
    }

    /**
     * This method sets the blocking executor to be used by the stack for potentially blocking
     * tasks. This method must be called before executing any requests.
     */
    @RestrictTo({RestrictTo.Scope.LIBRARY_GROUP})
    public void setBlockingExecutor(ExecutorService executor) {
        mBlockingExecutor = executor;
    }

    /** Gets blocking executor to perform any potentially blocking tasks. */
    protected ExecutorService getBlockingExecutor() {
        return mBlockingExecutor;
    }

    /** Gets non-blocking executor to perform any non-blocking tasks. */
    protected ExecutorService getNonBlockingExecutor() {
        return mNonBlockingExecutor;
    }

    /**
     * Performs an HTTP request with the given parameters.
     *
     * @param request the request to perform
     * @param additionalHeaders additional headers to be sent together with {@link
     *     Request#getHeaders()}
     * @return the {@link HttpResponse}
     * @throws IOException if an I/O error occurs during the request
     * @throws AuthFailureError if an authentication failure occurs during the request
     */
    @Override
    public final HttpResponse executeRequest(
            Request<?> request, Map<String, String> additionalHeaders)
            throws IOException, AuthFailureError {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Response> entry = new AtomicReference<>();
        executeRequest(
                request,
                additionalHeaders,
                new OnRequestComplete() {
                    @Override
                    public void onSuccess(HttpResponse httpResponse) {
                        Response response =
                                new Response(
                                        httpResponse,
                                        /* ioException= */ null,
                                        /* authFailureError= */ null);
                        entry.set(response);
                        latch.countDown();
                    }

                    @Override
                    public void onAuthError(AuthFailureError authFailureError) {
                        Response response =
                                new Response(
                                        /* httpResponse= */ null,
                                        /* ioException= */ null,
                                        authFailureError);
                        entry.set(response);
                        latch.countDown();
                    }

                    @Override
                    public void onError(IOException ioException) {
                        Response response =
                                new Response(
                                        /* httpResponse= */ null,
                                        ioException,
                                        /* authFailureError= */ null);
                        entry.set(response);
                        latch.countDown();
                    }
                });
        try {
            latch.await();
        } catch (InterruptedException e) {
            VolleyLog.e(e, "while waiting for CountDownLatch");
            Thread.currentThread().interrupt();
            throw new InterruptedIOException(e.toString());
        }
        Response response = entry.get();
        if (response.httpResponse != null) {
            return response.httpResponse;
        } else if (response.ioException != null) {
            throw response.ioException;
        } else {
            throw response.authFailureError;
        }
    }

    private static class Response {
        HttpResponse httpResponse;
        IOException ioException;
        AuthFailureError authFailureError;

        private Response(
                @Nullable HttpResponse httpResponse,
                @Nullable IOException ioException,
                @Nullable AuthFailureError authFailureError) {
            this.httpResponse = httpResponse;
            this.ioException = ioException;
            this.authFailureError = authFailureError;
        }
    }
}

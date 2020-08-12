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

import static com.android.volley.toolbox.NetworkUtility.logSlowRequests;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.android.volley.AsyncNetwork;
import com.android.volley.AuthFailureError;
import com.android.volley.Cache.Entry;
import com.android.volley.ClientError;
import com.android.volley.Header;
import com.android.volley.NetworkError;
import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.RetryPolicy;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/** A network performing Volley requests over an {@link HttpStack}. */
public class BasicAsyncNetwork extends AsyncNetwork {

    private static final int DEFAULT_POOL_SIZE = 4096;

    /**
     * @deprecated Should never have been exposed in the API. This field may be removed in a future
     *     release of Volley.
     */
    @Deprecated protected final HttpStack mHttpStack;

    private final BaseHttpStack mBaseHttpStack;

    protected final ByteArrayPool mPool;

    protected ExecutorService mBlockingExecutor;

    protected final Handler mHandler;

    /**
     * @param httpStack HTTP stack to be used
     * @deprecated use {@link #BasicAsyncNetwork(BaseHttpStack)} instead to avoid depending on
     *     Apache HTTP. This method may be removed in a future release of Volley.
     */
    @Deprecated
    public BasicAsyncNetwork(HttpStack httpStack) {
        // If a pool isn't passed in, then build a small default pool that will give us a lot of
        // benefit and not use too much memory.
        this(httpStack, new ByteArrayPool(DEFAULT_POOL_SIZE));
    }

    /**
     * @param httpStack HTTP stack to be used
     * @param pool a buffer pool that improves GC performance in copy operations
     * @deprecated use {@link #BasicAsyncNetwork(BaseHttpStack, ByteArrayPool)} instead to avoid
     *     depending on Apache HTTP. This method may be removed in a future release of Volley.
     */
    @Deprecated
    public BasicAsyncNetwork(HttpStack httpStack, ByteArrayPool pool) {
        mHttpStack = httpStack;
        mBaseHttpStack = new AdaptedHttpStack(httpStack);
        mPool = pool;
        mHandler = null;
    }

    /** @param httpStack HTTP stack to be used */
    public BasicAsyncNetwork(BaseHttpStack httpStack) {
        // If a pool isn't passed in, then build a small default pool that will give us a lot of
        // benefit and not use too much memory.
        this(httpStack, new ByteArrayPool(DEFAULT_POOL_SIZE));
    }

    /**
     * @param httpStack HTTP stack to be used
     * @param pool a buffer pool that improves GC performance in copy operations
     */
    public BasicAsyncNetwork(BaseHttpStack httpStack, ByteArrayPool pool) {
        mBaseHttpStack = httpStack;
        // Populate mHttpStack for backwards compatibility, since it is a protected field. However,
        // we won't use it directly here, so clients which don't access it directly won't need to
        // depend on Apache HTTP.
        mHttpStack = httpStack;
        mPool = pool;
        mHandler = new Handler(Looper.myLooper());
    }

    /* Method to be called after a successful network request */
    private void onRequestSucceeded(
            final Request<?> request,
            final long requestStartMs,
            final HttpResponse httpResponse,
            final OnRequestComplete callback) {
        final int statusCode = httpResponse.getStatusCode();
        final List<Header> responseHeaders = httpResponse.getHeaders();
        // Handle cache validation.
        if (statusCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            Entry entry = request.getCacheEntry();
            if (entry == null) {
                callback.onSuccess(
                        new NetworkResponse(
                                HttpURLConnection.HTTP_NOT_MODIFIED,
                                /* data= */ null,
                                /* notModified= */ true,
                                SystemClock.elapsedRealtime() - requestStartMs,
                                responseHeaders));
                return;
            }
            // Combine cached and response headers so the response will be complete.
            List<Header> combinedHeaders = NetworkUtility.combineHeaders(responseHeaders, entry);
            callback.onSuccess(
                    new NetworkResponse(
                            HttpURLConnection.HTTP_NOT_MODIFIED,
                            entry.data,
                            /* notModified= */ true,
                            SystemClock.elapsedRealtime() - requestStartMs,
                            combinedHeaders));
            return;
        }

        byte[] responseContents = httpResponse.getContentBytes();

        if (responseContents == null) {
            final InputStream inputStream = httpResponse.getContent();
            if (inputStream == null) {
                // Add 0 byte response as a way of honestly representing a
                // no-content request.
                responseContents = new byte[0];
            } else {
                Runnable run =
                        new Runnable() {
                            @Override
                            public void run() {
                                PoolingByteArrayOutputStream bytes =
                                        new PoolingByteArrayOutputStream(
                                                mPool, httpResponse.getContentLength());
                                byte[] buffer = new byte[1024];
                                int count = 0;
                                while (true) {
                                    try {
                                        if ((count = inputStream.read(buffer)) == -1) break;
                                    } catch (IOException e) {
                                        onRequestFailed(
                                                request,
                                                callback,
                                                e,
                                                requestStartMs,
                                                httpResponse,
                                                bytes.toByteArray());
                                    }
                                    bytes.write(buffer, 0, count);
                                }
                                byte[] finalResponseContents = bytes.toByteArray();
                                runAfterBytesReceived(
                                        requestStartMs,
                                        statusCode,
                                        httpResponse,
                                        request,
                                        callback,
                                        responseHeaders,
                                        finalResponseContents);
                            }
                        };
                mBlockingExecutor.execute(run);
                return;
            }
        }
        runAfterBytesReceived(
                requestStartMs,
                statusCode,
                httpResponse,
                request,
                callback,
                responseHeaders,
                responseContents);
    }

    /* Method to be called after a failed network request */
    private void onRequestFailed(
            Request<?> request,
            OnRequestComplete callback,
            IOException exception,
            long requestStartMs,
            @Nullable HttpResponse httpResponse,
            @Nullable byte[] responseContents) {
        if (exception instanceof SocketTimeoutException) {
            attemptRetryOnException("socket", request, callback, new TimeoutError());
        } else if (exception instanceof MalformedURLException) {
            throw new RuntimeException("Bad URL " + request.getUrl(), exception);
        } else {
            int statusCode;
            if (httpResponse != null) {
                statusCode = httpResponse.getStatusCode();
            } else {
                if (request.shouldRetryConnectionErrors()) {
                    attemptRetryOnException(
                            "connection", request, callback, new NoConnectionError());
                } else {
                    callback.onError(new NoConnectionError(exception));
                }
                return;
            }
            VolleyLog.e("Unexpected response code %d for %s", statusCode, request.getUrl());
            NetworkResponse networkResponse;
            if (responseContents != null) {
                List<Header> responseHeaders;
                responseHeaders = httpResponse.getHeaders();
                networkResponse =
                        new NetworkResponse(
                                statusCode,
                                responseContents,
                                /* notModified= */ false,
                                SystemClock.elapsedRealtime() - requestStartMs,
                                responseHeaders);
                if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED
                        || statusCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    attemptRetryOnException(
                            "auth", request, callback, new AuthFailureError(networkResponse));
                } else if (statusCode >= 400 && statusCode <= 499) {
                    // Don't retry other client errors.
                    callback.onError(new ClientError(networkResponse));
                } else if (statusCode >= 500 && statusCode <= 599) {
                    if (request.shouldRetryServerErrors()) {
                        attemptRetryOnException(
                                "server", request, callback, new ServerError(networkResponse));
                    } else {
                        callback.onError(new ServerError(networkResponse));
                    }
                } else {
                    // 3xx? No reason to retry.
                    callback.onError(new ServerError(networkResponse));
                }
            } else {
                attemptRetryOnException("network", request, callback, new NetworkError());
            }
        }
    }

    @Override
    public void performRequest(final Request<?> request, final OnRequestComplete callback) {
        if (mBlockingExecutor == null) {
            throw new IllegalStateException(
                    "mBlockingExecuter should be set before making a request");
        }
        final long requestStartMs = SystemClock.elapsedRealtime();
        // Gather headers.
        final Map<String, String> additionalRequestHeaders =
                NetworkUtility.getCacheHeaders(request.getCacheEntry());
        if (mBaseHttpStack instanceof AsyncHttpStack) {
            AsyncHttpStack asyncStack = (AsyncHttpStack) mBaseHttpStack;
            asyncStack.executeRequest(
                    request,
                    additionalRequestHeaders,
                    new AsyncHttpStack.OnRequestComplete() {
                        @Override
                        public void onSuccess(HttpResponse httpResponse) {
                            onRequestSucceeded(request, requestStartMs, httpResponse, callback);
                        }

                        @Override
                        public void onAuthError(AuthFailureError authFailureError) {
                            callback.onError(authFailureError);
                        }

                        @Override
                        public void onError(IOException ioException) {
                            onRequestFailed(
                                    request,
                                    callback,
                                    ioException,
                                    requestStartMs,
                                    /* httpResponse= */ null,
                                    /* responseContents= */ null);
                        }
                    });
        } else {
            mBlockingExecutor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            try {
                                onRequestSucceeded(
                                        request,
                                        requestStartMs,
                                        mBaseHttpStack.executeRequest(
                                                request, additionalRequestHeaders),
                                        callback);
                            } catch (AuthFailureError e) {
                                callback.onError(e);
                            } catch (IOException e) {
                                onRequestFailed(
                                        request,
                                        callback,
                                        e,
                                        requestStartMs,
                                        /* httpResponse= */ null,
                                        /* responseContents= */ null);
                            }
                        }
                    });
        }
    }

    /**
     * This method sets the non blocking executor to be used by the stack for non-blocking tasks. If
     * you are not using an {@link com.android.volley.toolbox.AsyncHttpStack}, this should do
     * nothing. This method must be called before performing any requests if you are using an
     * AsyncHttpStack.
     */
    @Override
    public void setNonBlockingExecutorForStack(ExecutorService executor) {
        if (mBaseHttpStack instanceof AsyncHttpStack) {
            AsyncHttpStack stack = (AsyncHttpStack) mBaseHttpStack;
            stack.setNonBlockingExecutor(executor);
        } else {
            VolleyLog.d("Cannot set non-blocking executor for non-async stack");
        }
    }

    /**
     * This method sets the blocking executor to be used by the network and stack for potentially
     * blocking tasks. This method must be called before performing any requests. Only set the
     * executor for the stack if it is an instance of {@link
     * com.android.volley.toolbox.AsyncHttpStack}
     */
    @Override
    public void setBlockingExecutor(ExecutorService executor) {
        mBlockingExecutor = executor;
        if (mBaseHttpStack instanceof AsyncHttpStack) {
            AsyncHttpStack stack = (AsyncHttpStack) mBaseHttpStack;
            stack.setBlockingExecutor(executor);
        } else {
            VolleyLog.d("Cannot set blocking executor for non-async stack");
        }
    }

    /**
     * Attempts to prepare the request for a retry. If there are no more attempts remaining in the
     * request's retry policy, a timeout exception is thrown.
     *
     * @param request The request to use.
     */
    private void attemptRetryOnException(
            final String logPrefix,
            final Request<?> request,
            final OnRequestComplete callback,
            final VolleyError exception) {
        final RetryPolicy retryPolicy = request.getRetryPolicy();
        final int oldTimeout = request.getTimeoutMs();

        try {
            retryPolicy.retry(exception);
        } catch (VolleyError e) {
            request.addMarker(
                    String.format("%s-timeout-giveup [timeout=%s]", logPrefix, oldTimeout));
            callback.onError(e);
            return;
        }
        request.addMarker(String.format("%s-retry [timeout=%s]", logPrefix, oldTimeout));
        // If we are using an async stack, then perform retry after a timeout.
        if (mBaseHttpStack instanceof AsyncHttpStack) {
            mHandler.postDelayed(
                    new Runnable() {
                        @Override
                        public void run() {
                            performRequest(request, callback);
                        }
                    },
                    100);
        } else {
            performRequest(request, callback);
        }
    }

    /* Helper method that determines what to do after byte[] is received */
    private void runAfterBytesReceived(
            long requestStartMs,
            int statusCode,
            HttpResponse httpResponse,
            Request<?> request,
            OnRequestComplete callback,
            List<Header> responseHeaders,
            byte[] responseContents) {
        // if the request is slow, log it.
        long requestLifetime = SystemClock.elapsedRealtime() - requestStartMs;
        logSlowRequests(requestLifetime, request, responseContents, statusCode);

        if (statusCode < 200 || statusCode > 299) {
            onRequestFailed(
                    request,
                    callback,
                    new IOException(),
                    requestStartMs,
                    httpResponse,
                    responseContents);
        }

        callback.onSuccess(
                new NetworkResponse(
                        statusCode,
                        responseContents,
                        /* notModified= */ false,
                        SystemClock.elapsedRealtime() - requestStartMs,
                        responseHeaders));
    }
}

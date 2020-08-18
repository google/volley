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

import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.android.volley.AuthFailureError;
import com.android.volley.Cache;
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

/**
 * Utility class for methods that are shared between {@link BasicNetwork} and {@link
 * BasicAsyncNetwork}
 */
public final class NetworkUtility {
    private static final int SLOW_REQUEST_THRESHOLD_MS = 3000;

    private NetworkUtility() {}

    /** Logs requests that took over SLOW_REQUEST_THRESHOLD_MS to complete. */
    static void logSlowRequests(
            long requestLifetime, Request<?> request, byte[] responseContents, int statusCode) {
        if (VolleyLog.DEBUG || requestLifetime > SLOW_REQUEST_THRESHOLD_MS) {
            VolleyLog.d(
                    "HTTP response for request=<%s> [lifetime=%d], [size=%s], "
                            + "[rc=%d], [retryCount=%s]",
                    request,
                    requestLifetime,
                    responseContents != null ? responseContents.length : "null",
                    statusCode,
                    request.getRetryPolicy().getCurrentRetryCount());
        }
    }

    static NetworkResponse getNotModifiedNetworkResponse(
            Request<?> request, long requestDuration, List<Header> responseHeaders) {
        Cache.Entry entry = request.getCacheEntry();
        if (entry == null) {
            return new NetworkResponse(
                    HttpURLConnection.HTTP_NOT_MODIFIED,
                    /* data= */ null,
                    /* notModified= */ true,
                    requestDuration,
                    responseHeaders);
        }
        // Combine cached and response headers so the response will be complete.
        List<Header> combinedHeaders = HttpHeaderParser.combineHeaders(responseHeaders, entry);
        return new NetworkResponse(
                HttpURLConnection.HTTP_NOT_MODIFIED,
                entry.data,
                /* notModified= */ true,
                requestDuration,
                combinedHeaders);
    }

    /** Reads the contents of an InputStream into a byte[]. */
    static byte[] inputStreamToBytes(InputStream in, int contentLength, ByteArrayPool pool)
            throws IOException {
        PoolingByteArrayOutputStream bytes = new PoolingByteArrayOutputStream(pool, contentLength);
        byte[] buffer = null;
        try {
            buffer = pool.getBuf(1024);
            int count;
            while ((count = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            return bytes.toByteArray();
        } finally {
            try {
                // Close the InputStream and release the resources by "consuming the content".
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                // This can happen if there was an exception above that left the stream in
                // an invalid state.
                VolleyLog.v("Error occurred when closing InputStream");
            }
            pool.returnBuf(buffer);
            bytes.close();
        }
    }

    /**
     * Attempts to prepare the request for a retry. If there are no more attempts remaining in the
     * request's retry policy, a timeout exception is thrown.
     *
     * @param request The request to use.
     */
    private static void attemptRetryOnException(
            final String logPrefix, final Request<?> request, final VolleyError exception)
            throws VolleyError {
        final RetryPolicy retryPolicy = request.getRetryPolicy();
        final int oldTimeout = request.getTimeoutMs();
        try {
            retryPolicy.retry(exception);
        } catch (VolleyError e) {
            request.addMarker(
                    String.format("%s-timeout-giveup [timeout=%s]", logPrefix, oldTimeout));
            throw e;
        }
        request.addMarker(String.format("%s-retry [timeout=%s]", logPrefix, oldTimeout));
    }

    /**
     * Based on the exception thrown, decides whether to attempt to retry, or to throw the error.
     * Also handles logging.
     */
    static void handleException(
            Request<?> request,
            IOException exception,
            long requestStartMs,
            @Nullable HttpResponse httpResponse,
            @Nullable byte[] responseContents)
            throws VolleyError {
        if (exception instanceof SocketTimeoutException) {
            attemptRetryOnException("socket", request, new TimeoutError());
        } else if (exception instanceof MalformedURLException) {
            throw new RuntimeException("Bad URL " + request.getUrl(), exception);
        } else {
            int statusCode;
            if (httpResponse != null) {
                statusCode = httpResponse.getStatusCode();
            } else {
                if (request.shouldRetryConnectionErrors()) {
                    attemptRetryOnException("connection", request, new NoConnectionError());
                    return;
                } else {
                    throw new NoConnectionError(exception);
                }
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
                    attemptRetryOnException("auth", request, new AuthFailureError(networkResponse));
                } else if (statusCode >= 400 && statusCode <= 499) {
                    // Don't retry other client errors.
                    throw new ClientError(networkResponse);
                } else if (statusCode >= 500 && statusCode <= 599) {
                    if (request.shouldRetryServerErrors()) {
                        attemptRetryOnException(
                                "server", request, new ServerError(networkResponse));
                    } else {
                        throw new ServerError(networkResponse);
                    }
                } else {
                    // 3xx? No reason to retry.
                    throw new ServerError(networkResponse);
                }
            } else {
                attemptRetryOnException("network", request, new NetworkError());
            }
        }
    }
}

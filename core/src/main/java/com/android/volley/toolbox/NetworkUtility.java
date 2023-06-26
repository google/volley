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
final class NetworkUtility {
    private NetworkUtility() {}

    /** Logs a summary about the request when debug logging is enabled. */
    static void logRequestSummary(
            long requestLifetime, Request<?> request, byte[] responseContents, int statusCode) {
        if (VolleyLog.DEBUG) {
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
     * request's retry policy, the provided exception is thrown.
     *
     * <p>Must be invoked from a background thread, as client implementations of RetryPolicy#retry
     * may make blocking calls.
     *
     * @param request The request to use.
     */
    static void attemptRetryOnException(final Request<?> request, final RetryInfo retryInfo)
            throws VolleyError {
        final RetryPolicy retryPolicy = request.getRetryPolicy();
        final int oldTimeout = request.getTimeoutMs();
        try {
            retryPolicy.retry(retryInfo.errorToRetry);
        } catch (VolleyError e) {
            request.addMarker(
                    String.format(
                            "%s-timeout-giveup [timeout=%s]", retryInfo.logPrefix, oldTimeout));
            throw e;
        }
        request.addMarker(String.format("%s-retry [timeout=%s]", retryInfo.logPrefix, oldTimeout));
    }

    static class RetryInfo {
        private final String logPrefix;
        private final VolleyError errorToRetry;

        private RetryInfo(String logPrefix, VolleyError errorToRetry) {
            this.logPrefix = logPrefix;
            this.errorToRetry = errorToRetry;
        }
    }

    /**
     * Based on the exception thrown, decides whether to attempt to retry, or to throw the error.
     *
     * <p>If this method returns without throwing, {@link #attemptRetryOnException} should be called
     * with the provided {@link RetryInfo} to consult the client's retry policy.
     */
    static RetryInfo shouldRetryException(
            Request<?> request,
            IOException exception,
            long requestStartMs,
            @Nullable HttpResponse httpResponse,
            @Nullable byte[] responseContents)
            throws VolleyError {
        if (exception instanceof SocketTimeoutException) {
            return new RetryInfo("socket", new TimeoutError());
        } else if (exception instanceof MalformedURLException) {
            throw new RuntimeException("Bad URL " + request.getUrl(), exception);
        } else {
            int statusCode;
            if (httpResponse != null) {
                statusCode = httpResponse.getStatusCode();
            } else {
                if (request.shouldRetryConnectionErrors()) {
                    return new RetryInfo("connection", new NoConnectionError());
                }
                throw new NoConnectionError(exception);
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
                    return new RetryInfo("auth", new AuthFailureError(networkResponse));
                }
                if (statusCode >= 400 && statusCode <= 499) {
                    // Don't retry other client errors.
                    throw new ClientError(networkResponse);
                }
                if (statusCode >= 500 && statusCode <= 599) {
                    if (request.shouldRetryServerErrors()) {
                        return new RetryInfo("server", new ServerError(networkResponse));
                    }
                }
                // Server error and client has opted out of retries, or 3xx. No reason to retry.
                throw new ServerError(networkResponse);
            }
            return new RetryInfo("network", new NetworkError());
        }
    }
}

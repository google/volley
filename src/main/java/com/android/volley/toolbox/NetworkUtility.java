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
import com.android.volley.AsyncNetwork;
import com.android.volley.AuthFailureError;
import com.android.volley.Cache;
import com.android.volley.ClientError;
import com.android.volley.Header;
import com.android.volley.Network;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Utility class for methods that are shared between {@link BasicNetwork} and {@link
 * BasicAsyncNetwork}
 */
public class NetworkUtility {
    private static final int SLOW_REQUEST_THRESHOLD_MS = 3000;

    /**
     * Combine cache headers with network response headers for an HTTP 304 response.
     *
     * <p>An HTTP 304 response does not have all header fields. We have to use the header fields
     * from the cache entry plus the new ones from the response. See also:
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.5
     *
     * @param responseHeaders Headers from the network response.
     * @param entry The cached response.
     * @return The combined list of headers.
     */
    static List<Header> combineHeaders(List<Header> responseHeaders, Cache.Entry entry) {
        // First, create a case-insensitive set of header names from the network
        // response.
        Set<String> headerNamesFromNetworkResponse = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (!responseHeaders.isEmpty()) {
            for (Header header : responseHeaders) {
                headerNamesFromNetworkResponse.add(header.getName());
            }
        }

        // Second, add headers from the cache entry to the network response as long as
        // they didn't appear in the network response, which should take precedence.
        List<Header> combinedHeaders = new ArrayList<>(responseHeaders);
        if (entry.allResponseHeaders != null) {
            if (!entry.allResponseHeaders.isEmpty()) {
                for (Header header : entry.allResponseHeaders) {
                    if (!headerNamesFromNetworkResponse.contains(header.getName())) {
                        combinedHeaders.add(header);
                    }
                }
            }
        } else {
            // Legacy caches only have entry.responseHeaders.
            if (!entry.responseHeaders.isEmpty()) {
                for (Map.Entry<String, String> header : entry.responseHeaders.entrySet()) {
                    if (!headerNamesFromNetworkResponse.contains(header.getKey())) {
                        combinedHeaders.add(new Header(header.getKey(), header.getValue()));
                    }
                }
            }
        }
        return combinedHeaders;
    }

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

    static Map<String, String> getCacheHeaders(Cache.Entry entry) {
        // If there's no cache entry, we're done.
        if (entry == null) {
            return Collections.emptyMap();
        }

        Map<String, String> headers = new HashMap<>();

        if (entry.etag != null) {
            headers.put("If-None-Match", entry.etag);
        }

        if (entry.lastModified > 0) {
            headers.put(
                    "If-Modified-Since", HttpHeaderParser.formatEpochAsRfc1123(entry.lastModified));
        }

        return headers;
    }

    static NetworkResponse getNetworkResponse(
            int statusCode, Request<?> request, long requestStart, List<Header> responseHeaders) {
        Cache.Entry entry = request.getCacheEntry();
        if (entry == null) {
            return new NetworkResponse(
                    HttpURLConnection.HTTP_NOT_MODIFIED,
                    /* data= */ null,
                    /* notModified= */ true,
                    SystemClock.elapsedRealtime() - requestStart,
                    responseHeaders);
        }
        // Combine cached and response headers so the response will be complete.
        List<Header> combinedHeaders = NetworkUtility.combineHeaders(responseHeaders, entry);
        return new NetworkResponse(
                HttpURLConnection.HTTP_NOT_MODIFIED,
                entry.data,
                /* notModified= */ true,
                SystemClock.elapsedRealtime() - requestStart,
                combinedHeaders);
    }

    /** Reads the contents of an InputStream into a byte[]. */
    static byte[] inputStreamToBytes(
            @Nullable InputStream in, int contentLength, ByteArrayPool pool)
            throws IOException, ServerError {
        PoolingByteArrayOutputStream bytes = new PoolingByteArrayOutputStream(pool, contentLength);
        byte[] buffer = null;
        try {
            if (in == null) {
                throw new ServerError();
            }
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
            final String logPrefix,
            final Request<?> request,
            @Nullable final AsyncNetwork.OnRequestComplete callback,
            final VolleyError exception,
            final Network network)
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
        if (network instanceof AsyncNetwork) {
            if (callback == null) {
                throw new VolleyError("No callback provided");
            }
            ((AsyncNetwork) network).performRequest(request, callback);
        } else {
            network.performRequest(request);
        }
    }

    /*
     * Based on the exception thrown, decides whether to attempt to retry, or to throw the error.
     * Also handles logging.
     */
    static void handleException(
            Request<?> request,
            @Nullable AsyncNetwork.OnRequestComplete callback,
            IOException exception,
            long requestStartMs,
            @Nullable HttpResponse httpResponse,
            @Nullable byte[] responseContents,
            Network network)
            throws VolleyError {
        if (exception instanceof SocketTimeoutException) {
            attemptRetryOnException("socket", request, callback, new TimeoutError(), network);
        } else if (exception instanceof MalformedURLException) {
            throw new RuntimeException("Bad URL " + request.getUrl(), exception);
        } else {
            int statusCode;
            if (httpResponse != null) {
                statusCode = httpResponse.getStatusCode();
            } else {
                if (request.shouldRetryConnectionErrors()) {
                    attemptRetryOnException(
                            "connection", request, callback, new NoConnectionError(), network);
                } else {
                    throw new NoConnectionError(exception);
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
                            "auth",
                            request,
                            callback,
                            new AuthFailureError(networkResponse),
                            network);
                } else if (statusCode >= 400 && statusCode <= 499) {
                    // Don't retry other client errors.
                    throw new ClientError(networkResponse);
                } else if (statusCode >= 500 && statusCode <= 599) {
                    if (request.shouldRetryServerErrors()) {
                        attemptRetryOnException(
                                "server",
                                request,
                                callback,
                                new ServerError(networkResponse),
                                network);
                    } else {
                        throw new ServerError(networkResponse);
                    }
                } else {
                    // 3xx? No reason to retry.
                    throw new ServerError(networkResponse);
                }
            } else {
                attemptRetryOnException("network", request, callback, new NetworkError(), network);
            }
        }
    }
}

/*
 * Copyright (C) 2011 The Android Open Source Project
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
import com.android.volley.Header;
import com.android.volley.Network;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.NetworkUtility.RetryInfo;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** A network performing Volley requests over an {@link HttpStack}. */
public class BasicNetwork implements Network {
    private static final int DEFAULT_POOL_SIZE = 4096;

    /**
     * @deprecated Should never have been exposed in the API. This field may be removed in a future
     *     release of Volley.
     */
    @Deprecated protected final HttpStack mHttpStack;

    private final BaseHttpStack mBaseHttpStack;

    protected final ByteArrayPool mPool;

    /**
     * @param httpStack HTTP stack to be used
     * @deprecated use {@link #BasicNetwork(BaseHttpStack)} instead to avoid depending on Apache
     *     HTTP. This method may be removed in a future release of Volley.
     */
    @Deprecated
    public BasicNetwork(HttpStack httpStack) {
        // If a pool isn't passed in, then build a small default pool that will give us a lot of
        // benefit and not use too much memory.
        this(httpStack, new ByteArrayPool(DEFAULT_POOL_SIZE));
    }

    /**
     * @param httpStack HTTP stack to be used
     * @param pool a buffer pool that improves GC performance in copy operations
     * @deprecated use {@link #BasicNetwork(BaseHttpStack, ByteArrayPool)} instead to avoid
     *     depending on Apache HTTP. This method may be removed in a future release of Volley.
     */
    @Deprecated
    public BasicNetwork(HttpStack httpStack, ByteArrayPool pool) {
        mHttpStack = httpStack;
        mBaseHttpStack = new AdaptedHttpStack(httpStack);
        mPool = pool;
    }

    /** @param httpStack HTTP stack to be used */
    public BasicNetwork(BaseHttpStack httpStack) {
        // If a pool isn't passed in, then build a small default pool that will give us a lot of
        // benefit and not use too much memory.
        this(httpStack, new ByteArrayPool(DEFAULT_POOL_SIZE));
    }

    /**
     * @param httpStack HTTP stack to be used
     * @param pool a buffer pool that improves GC performance in copy operations
     */
    public BasicNetwork(BaseHttpStack httpStack, ByteArrayPool pool) {
        mBaseHttpStack = httpStack;
        // Populate mHttpStack for backwards compatibility, since it is a protected field. However,
        // we won't use it directly here, so clients which don't access it directly won't need to
        // depend on Apache HTTP.
        mHttpStack = httpStack;
        mPool = pool;
    }

    @Override
    public NetworkResponse performRequest(Request<?> request) throws VolleyError {
        long requestStart = SystemClock.elapsedRealtime();
        while (true) {
            HttpResponse httpResponse = null;
            byte[] responseContents = null;
            List<Header> responseHeaders = Collections.emptyList();
            try {
                // Gather headers.
                Map<String, String> additionalRequestHeaders =
                        HttpHeaderParser.getCacheHeaders(request.getCacheEntry());
                httpResponse = mBaseHttpStack.executeRequest(request, additionalRequestHeaders);
                int statusCode = httpResponse.getStatusCode();

                responseHeaders = httpResponse.getHeaders();
                // Handle cache validation.
                if (statusCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    long requestDuration = SystemClock.elapsedRealtime() - requestStart;
                    return NetworkUtility.getNotModifiedNetworkResponse(
                            request, requestDuration, responseHeaders);
                }

                // Some responses such as 204s do not have content.  We must check.
                InputStream inputStream = httpResponse.getContent();
                if (inputStream != null) {
                    responseContents =
                            NetworkUtility.inputStreamToBytes(
                                    inputStream, httpResponse.getContentLength(), mPool);
                } else {
                    // Add 0 byte response as a way of honestly representing a
                    // no-content request.
                    responseContents = new byte[0];
                }

                // if the request is slow, log it.
                long requestLifetime = SystemClock.elapsedRealtime() - requestStart;
                NetworkUtility.logSlowRequests(
                        requestLifetime, request, responseContents, statusCode);

                if (statusCode < 200 || statusCode > 299) {
                    throw new IOException();
                }
                return new NetworkResponse(
                        statusCode,
                        responseContents,
                        /* notModified= */ false,
                        SystemClock.elapsedRealtime() - requestStart,
                        responseHeaders);
            } catch (IOException e) {
                // This will either throw an exception, breaking us from the loop, or will loop
                // again and retry the request.
                RetryInfo retryInfo =
                        NetworkUtility.shouldRetryException(
                                request, e, requestStart, httpResponse, responseContents);
                // We should already be on a background thread, so we can invoke the retry inline.
                NetworkUtility.attemptRetryOnException(request, retryInfo);
            }
        }
    }

    /**
     * Converts Headers[] to Map&lt;String, String&gt;.
     *
     * @deprecated Should never have been exposed in the API. This method may be removed in a future
     *     release of Volley.
     */
    @Deprecated
    protected static Map<String, String> convertHeaders(Header[] headers) {
        Map<String, String> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < headers.length; i++) {
            result.put(headers[i].getName(), headers[i].getValue());
        }
        return result;
    }
}

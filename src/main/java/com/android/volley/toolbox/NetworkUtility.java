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

import com.android.volley.Cache;
import com.android.volley.Header;
import com.android.volley.Request;
import com.android.volley.VolleyLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Utility class for methods that are shared between {@link BasicNetwork} and {@link
 * BasicAsyncNetwork}
 */
public class NetworkUtility {
    protected static final boolean DEBUG = VolleyLog.DEBUG;

    private static final int SLOW_REQUEST_THRESHOLD_MS = 3000;
    /**
     * Converts Headers[] to Map&lt;String, String&gt;.
     *
     * @deprecated Should never have been exposed in the API. This method may be removed in a future
     *     release of Volley.
     */
    @Deprecated
    static Map<String, String> convertHeaders(Header[] headers) {
        Map<String, String> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < headers.length; i++) {
            result.put(headers[i].getName(), headers[i].getValue());
        }
        return result;
    }

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
        if (DEBUG || requestLifetime > SLOW_REQUEST_THRESHOLD_MS) {
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
}

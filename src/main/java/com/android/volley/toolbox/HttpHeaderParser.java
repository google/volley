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

import com.android.volley.Cache;
import com.android.volley.Header;
import com.android.volley.NetworkResponse;
import com.android.volley.VolleyLog;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

/** Utility methods for parsing HTTP headers. */
public class HttpHeaderParser {

    static final String HEADER_CONTENT_TYPE = "Content-Type";

    private static final String DEFAULT_CONTENT_CHARSET = "ISO-8859-1";

    private static final String RFC1123_PARSE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

    // Hardcode 'GMT' rather than using 'zzz' since some platforms append an extraneous +00:00.
    // See #287.
    private static final String RFC1123_OUTPUT_FORMAT = "EEE, dd MMM yyyy HH:mm:ss 'GMT'";

    /**
     * Extracts a {@link com.android.volley.Cache.Entry} from a {@link NetworkResponse}.
     *
     * @param response The network response to parse headers from
     * @return a cache entry for the given response, or null if the response is not cacheable.
     */
    public static Cache.Entry parseCacheHeaders(NetworkResponse response) {
        long now = System.currentTimeMillis();

        Map<String, String> headers = response.headers;

        long serverDate = 0;
        long lastModified = 0;
        long serverExpires = 0;
        long softExpire = 0;
        long finalExpire = 0;
        long maxAge = 0;
        long staleWhileRevalidate = 0;
        boolean hasCacheControl = false;
        boolean mustRevalidate = false;

        String serverEtag = null;
        String headerValue;

        headerValue = headers.get("Date");
        if (headerValue != null) {
            serverDate = parseDateAsEpoch(headerValue);
        }

        headerValue = headers.get("Cache-Control");
        if (headerValue != null) {
            hasCacheControl = true;
            String[] tokens = headerValue.split(",", 0);
            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i].trim();
                if (token.equals("no-cache") || token.equals("no-store")) {
                    return null;
                } else if (token.startsWith("max-age=")) {
                    try {
                        maxAge = Long.parseLong(token.substring(8));
                    } catch (Exception e) {
                    }
                } else if (token.startsWith("stale-while-revalidate=")) {
                    try {
                        staleWhileRevalidate = Long.parseLong(token.substring(23));
                    } catch (Exception e) {
                    }
                } else if (token.equals("must-revalidate") || token.equals("proxy-revalidate")) {
                    mustRevalidate = true;
                }
            }
        }

        headerValue = headers.get("Expires");
        if (headerValue != null) {
            serverExpires = parseDateAsEpoch(headerValue);
        }

        headerValue = headers.get("Last-Modified");
        if (headerValue != null) {
            lastModified = parseDateAsEpoch(headerValue);
        }

        serverEtag = headers.get("ETag");

        // Cache-Control takes precedence over an Expires header, even if both exist and Expires
        // is more restrictive.
        if (hasCacheControl) {
            softExpire = now + maxAge * 1000;
            finalExpire = mustRevalidate ? softExpire : softExpire + staleWhileRevalidate * 1000;
        } else if (serverDate > 0 && serverExpires >= serverDate) {
            // Default semantic for Expire header in HTTP specification is softExpire.
            softExpire = now + (serverExpires - serverDate);
            finalExpire = softExpire;
        }

        Cache.Entry entry = new Cache.Entry();
        entry.data = response.data;
        entry.etag = serverEtag;
        entry.softTtl = softExpire;
        entry.ttl = finalExpire;
        entry.serverDate = serverDate;
        entry.lastModified = lastModified;
        entry.responseHeaders = headers;
        entry.allResponseHeaders = response.allHeaders;

        return entry;
    }

    /** Parse date in RFC1123 format, and return its value as epoch */
    public static long parseDateAsEpoch(String dateStr) {
        try {
            // Parse date in RFC1123 format if this header contains one
            return newUsGmtFormatter(RFC1123_PARSE_FORMAT).parse(dateStr).getTime();
        } catch (ParseException e) {
            // Date in invalid format, fallback to 0
            // If the value is either "0" or "-1" we only log to verbose,
            // these values are pretty common and cause log spam.
            String message = "Unable to parse dateStr: %s, falling back to 0";
            if ("0".equals(dateStr) || "-1".equals(dateStr)) {
                VolleyLog.v(message, dateStr);
            } else {
                VolleyLog.e(e, message, dateStr);
            }

            return 0;
        }
    }

    /** Format an epoch date in RFC1123 format. */
    static String formatEpochAsRfc1123(long epoch) {
        return newUsGmtFormatter(RFC1123_OUTPUT_FORMAT).format(new Date(epoch));
    }

    private static SimpleDateFormat newUsGmtFormatter(String format) {
        SimpleDateFormat formatter = new SimpleDateFormat(format, Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        return formatter;
    }

    /**
     * Retrieve a charset from headers
     *
     * @param headers An {@link java.util.Map} of headers
     * @param defaultCharset Charset to return if none can be found
     * @return Returns the charset specified in the Content-Type of this header, or the
     *     defaultCharset if none can be found.
     */
    public static String parseCharset(Map<String, String> headers, String defaultCharset) {
        String contentType = headers.get(HEADER_CONTENT_TYPE);
        if (contentType != null) {
            String[] params = contentType.split(";", 0);
            for (int i = 1; i < params.length; i++) {
                String[] pair = params[i].trim().split("=", 0);
                if (pair.length == 2) {
                    if (pair[0].equals("charset")) {
                        return pair[1];
                    }
                }
            }
        }

        return defaultCharset;
    }

    /**
     * Returns the charset specified in the Content-Type of this header, or the HTTP default
     * (ISO-8859-1) if none can be found.
     */
    public static String parseCharset(Map<String, String> headers) {
        return parseCharset(headers, DEFAULT_CONTENT_CHARSET);
    }

    // Note - these are copied from NetworkResponse to avoid making them public (as needed to access
    // them from the .toolbox package), which would mean they'd become part of the Volley API.
    // TODO: Consider obfuscating official releases so we can share utility methods between Volley
    // and Toolbox without making them public APIs.

    static Map<String, String> toHeaderMap(List<Header> allHeaders) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        // Later elements in the list take precedence.
        for (Header header : allHeaders) {
            headers.put(header.getName(), header.getValue());
        }
        return headers;
    }

    static List<Header> toAllHeaderList(Map<String, String> headers) {
        List<Header> allHeaders = new ArrayList<>(headers.size());
        for (Map.Entry<String, String> header : headers.entrySet()) {
            allHeaders.add(new Header(header.getKey(), header.getValue()));
        }
        return allHeaders;
    }
}

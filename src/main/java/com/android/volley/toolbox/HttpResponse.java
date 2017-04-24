/*
 * Copyright (C) 2017 The Android Open Source Project
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

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/** A response from an HTTP server. */
public class HttpResponse {
    private final int mStatusCode;
    private final Map<String, List<String>> mHeaders;
    private final int mContentLength;
    private final InputStream mContent;

    /**
     * Construct a new HttpResponse for an empty response body.
     *
     * @param statusCode the HTTP status code of the response
     * @param headers the response headers
     */
    public HttpResponse(int statusCode, Map<String, List<String>> headers) {
        this(statusCode, headers, -1 /* contentLength */, null /* content */);
    }

    /**
     * Construct a new HttpResponse.
     *
     * @param statusCode the HTTP status code of the response
     * @param headers the response headers
     * @param contentLength the length of the response content. Ignored if there is no content.
     * @param content an {@link InputStream} of the response content. May be null to indicate that
     *     the response has no content.
     */
    public HttpResponse(int statusCode, Map<String, List<String>> headers,
            int contentLength, InputStream content) {
        mStatusCode = statusCode;
        mHeaders = headers;
        mContentLength = contentLength;
        mContent = content;
    }

    /** Returns the HTTP status code of the response. */
    public final int getStatusCode() {
        return mStatusCode;
    }

    /** Returns the response headers. */
    public final Map<String, List<String>> getHeaders() {
        return mHeaders;
    }

    /** Returns the length of the content. Only valid if {@link #getContent} is non-null. */
    public final int getContentLength() {
        return mContentLength;
    }

    /**
     * Returns an {@link InputStream} of the response content. May be null to indicate that the
     * response has no content.
     */
    public final InputStream getContent() {
        return mContent;
    }
}

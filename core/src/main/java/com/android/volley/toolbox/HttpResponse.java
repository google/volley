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

import androidx.annotation.Nullable;
import com.android.volley.Header;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/** A response from an HTTP server. */
public final class HttpResponse {

    private final int mStatusCode;
    private final List<Header> mHeaders;
    private final int mContentLength;
    @Nullable private final InputStream mContent;
    @Nullable private final byte[] mContentBytes;

    /**
     * Construct a new HttpResponse for an empty response body.
     *
     * @param statusCode the HTTP status code of the response
     * @param headers the response headers
     */
    public HttpResponse(int statusCode, List<Header> headers) {
        this(statusCode, headers, /* contentLength= */ -1, /* content= */ null);
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
    public HttpResponse(
            int statusCode, List<Header> headers, int contentLength, InputStream content) {
        mStatusCode = statusCode;
        mHeaders = headers;
        mContentLength = contentLength;
        mContent = content;
        mContentBytes = null;
    }

    /**
     * Construct a new HttpResponse.
     *
     * @param statusCode the HTTP status code of the response
     * @param headers the response headers
     * @param contentBytes a byte[] of the response content. This is an optimization for HTTP stacks
     *     that natively support returning a byte[].
     */
    public HttpResponse(int statusCode, List<Header> headers, byte[] contentBytes) {
        mStatusCode = statusCode;
        mHeaders = headers;
        mContentLength = contentBytes.length;
        mContentBytes = contentBytes;
        mContent = null;
    }

    /** Returns the HTTP status code of the response. */
    public final int getStatusCode() {
        return mStatusCode;
    }

    /** Returns the response headers. Must not be mutated directly. */
    public final List<Header> getHeaders() {
        return Collections.unmodifiableList(mHeaders);
    }

    /** Returns the length of the content. Only valid if {@link #getContent} is non-null. */
    public final int getContentLength() {
        return mContentLength;
    }

    /**
     * If a byte[] was already provided by an HTTP stack that natively supports returning one, this
     * method will return that byte[] as an optimization over copying the bytes from an input
     * stream. It may return null, even if the response has content, as long as mContent is
     * provided.
     */
    @Nullable
    public final byte[] getContentBytes() {
        return mContentBytes;
    }

    /**
     * Returns an {@link InputStream} of the response content. May be null to indicate that the
     * response has no content.
     */
    @Nullable
    public final InputStream getContent() {
        if (mContent != null) {
            return mContent;
        } else if (mContentBytes != null) {
            return new ByteArrayInputStream(mContentBytes);
        } else {
            return null;
        }
    }
}

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

package com.android.volley;

import androidx.annotation.Nullable;

/**
 * Encapsulates a parsed response for delivery.
 *
 * @param <T> Parsed type of this response
 */
public class Response<T> {

    /** Callback interface for delivering parsed responses. */
    public interface Listener<T> {
        /** Called when a response is received. */
        void onResponse(T response);
    }

    /** Callback interface for delivering error responses. */
    public interface ErrorListener {
        /**
         * Callback method that an error has been occurred with the provided error code and optional
         * user-readable message.
         */
        void onErrorResponse(VolleyError error);
    }

    /** Returns a successful response containing the parsed result. */
    public static <T> Response<T> success(@Nullable T result, @Nullable Cache.Entry cacheEntry) {
        return new Response<>(result, cacheEntry);
    }

    /**
     * Returns a failed response containing the given error code and an optional localized message
     * displayed to the user.
     */
    public static <T> Response<T> error(VolleyError error) {
        return new Response<>(error);
    }

    /** Parsed response, can be null; always null in the case of error. */
    @Nullable public final T result;

    /** Cache metadata for this response; null if not cached or in the case of error. */
    @Nullable public final Cache.Entry cacheEntry;

    /** Detailed error information if <code>errorCode != OK</code>. */
    @Nullable public final VolleyError error;

    /** True if this response was a soft-expired one and a second one MAY be coming. */
    public boolean intermediate = false;

    /** Returns whether this response is considered successful. */
    public boolean isSuccess() {
        return error == null;
    }

    private Response(@Nullable T result, @Nullable Cache.Entry cacheEntry) {
        this.result = result;
        this.cacheEntry = cacheEntry;
        this.error = null;
    }

    private Response(VolleyError error) {
        this.result = null;
        this.cacheEntry = null;
        this.error = error;
    }
}

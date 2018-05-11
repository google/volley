package com.android.volley.toolbox;

import com.android.volley.NetworkResponse;
import com.android.volley.Response;

/**
 * Converts a {@link NetworkResponse} data to a {@link Response} for a {@link
 * com.android.volley.Request}.
 */
public interface ResponseParser<T> {

    /** See class documentation. */
    Response<T> parseNetworkResponse(NetworkResponse response);

    void configureDefaults(RequestBuilder<T, ?> requestBuilder);
}

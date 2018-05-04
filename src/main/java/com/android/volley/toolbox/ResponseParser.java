package com.android.volley.toolbox;

import com.android.volley.NetworkResponse;
import com.android.volley.Response;

/**
 * TODO
 */
public interface ResponseParser<T> {
    /**
     * TODO
     */
    Response<T> parseNetworkResponse(NetworkResponse response);
}

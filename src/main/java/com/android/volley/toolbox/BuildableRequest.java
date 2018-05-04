package com.android.volley.toolbox;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;

/**
 * TODO
 */
public class BuildableRequest<T> extends Request<T> {

    private final Object mLock = new Object();
    private final ResponseParser<T> parser;

    private Response.Listener<T> mListener;

    public BuildableRequest(
            int method,
            String url,
            Response.Listener<T> listener,
            Response.ErrorListener errorListener,
            ResponseParser<T> parser
    ) {
        super(method, url, errorListener);
        // TODO Null checks for listeners
        this.mListener = listener;
        this.parser = parser;
    }

    @Override
    public void cancel() {
        super.cancel();
        synchronized (mLock) {
            mListener = null;
        }
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        return parser.parseNetworkResponse(response);
    }

    @Override
    protected void deliverResponse(T response) {
        Response.Listener<T> listener;
        synchronized (mLock) {
            listener = mListener;
        }
        if (listener != null) {
            listener.onResponse(response);
        }
    }
}

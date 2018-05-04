package com.android.volley.toolbox;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

/**
 * TODO
 */
public class BuildableRequest<T> extends Request<T> {

    private final Object mLock = new Object();
    private final ResponseParser<T> parser;
    private final String bodyContentType;
    private final byte[] body;

    private Response.Listener<T> mListener; // Mutable to be consistent with other Requests

    public BuildableRequest(
            int method,
            String url,
            Response.Listener<T> listener,
            Response.ErrorListener errorListener,
            ResponseParser<T> parser,
            String bodyContentType,
            byte[] body
    ) {
        super(method, url, errorListener);
        // TODO Null checks for listeners
        this.mListener = listener;
        this.parser = parser;
        this.bodyContentType = bodyContentType;
        this.body = body;
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

    @Override
    public String getBodyContentType() {
        return bodyContentType;
    }

    @Override
    public byte[] getBody() throws AuthFailureError {
        return body;
    }
}

/**
 * TODO make methods for all of the other kinds of stuff
 * TODO desc
 * TODO move
 */
class Body {
    public static byte[] forJSONObject(JSONObject jsonObject) {
        try {
            return jsonObject.toString().getBytes(JsonRequest.PROTOCOL_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new Error();
            // TODO
        }
    }
}

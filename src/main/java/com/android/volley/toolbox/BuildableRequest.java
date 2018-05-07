package com.android.volley.toolbox;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;

import static java.util.Objects.requireNonNull;

/**
 * TODO documentation for this class, and for the constructor and methods
 */
public class BuildableRequest<T> extends Request<T> {

    private final ResponseParser<T> parser;
    private final String bodyContentType;
    private final byte[] body;
    private Response.Listener<T> listener;

    public BuildableRequest(
            int method,
            String url,
            Response.Listener<T> listener,
            Response.ErrorListener errorListener,
            ResponseParser<T> parser,
            String bodyContentType,
            byte[] body
    ) {
        super(
                method,
                requireNonNull(url, "Missing url"),
                requireNonNull(errorListener, "Missing error listener")
        );
        this.listener = requireNonNull(listener, "Missing listener");
        this.parser = parser;
        this.bodyContentType = bodyContentType;
        this.body = body;
    }

    @Override
    public void cancel() {
        super.cancel();
        listener = null;
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        return parser.parseNetworkResponse(response);
    }

    @Override
    protected void deliverResponse(T response) {
        if (listener == null) {
            return;
        }
        listener.onResponse(response);
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

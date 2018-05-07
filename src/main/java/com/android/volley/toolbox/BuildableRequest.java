package com.android.volley.toolbox;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;

import java.util.Collection;

import static java.util.Objects.requireNonNull;

/**
 * TODO documentation for this class, and for the constructor and methods
 */
public class BuildableRequest<T> extends Request<T> {

    private final ResponseParser<T> parser;
    private final String bodyContentType;
    private final byte[] body;
    private final Collection<Response.Listener<T>> mListeners;

    public BuildableRequest(
            int method,
            String url,
            Collection<Response.Listener<T>> listeners,
            final Collection<Response.ErrorListener> errorListeners,
            ResponseParser<T> parser,
            String bodyContentType,
            byte[] body
    ) {
        super(method, requireNonNull(url, "Missing url"), new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                for (Response.ErrorListener errorListener : errorListeners) {
                    errorListener.onErrorResponse(error);
                }
            }
        });
        // TODO Null checks for listeners
        this.mListeners = null; // new CopyOnWriteArrayList<>(listeners);
        this.parser = parser;
        this.bodyContentType = bodyContentType;
        this.body = body;
    }

    @Override
    public void cancel() {
        super.cancel();
        mListeners.clear();
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        return parser.parseNetworkResponse(response);
    }

    @Override
    protected void deliverResponse(T response) {
        for (Response.Listener<T> listener : mListeners) {
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

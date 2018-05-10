package com.android.volley.toolbox;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Objects.requireNonNull;

/**
 * TODO documentation for this class, and for the constructor and methods
 */
public class BuildableRequest<T> extends Request<T> {

    private final ResponseParser<T> parser;
    private final String bodyContentType;
    private final byte[] bodyBytes;
    private final Priority priority;
    private final Map<String, String> headers;
    private final Map<String, String> params;
    private final String paramsEncoding;
    private final Collection<Response.Listener<T>> listeners;

    /**
     * TODO docs
     * NOTE: Prefer using the {@link RequestBuilder} over this constructor.
     * @param method
     * @param url
     * @param listeners
     * @param errorListeners
     * @param parser
     * @param body
     * @param priority
     * @param headers
     * @param params
     * @param paramsEncoding
     */
    public BuildableRequest(
            int method,
            String url,
            Collection<Response.Listener<T>> listeners,
            Collection<Response.ErrorListener> errorListeners,
            ResponseParser<T> parser,
            Body body,
            Priority priority,
            Map<String, String> headers,
            Map<String, String> params,
            String paramsEncoding
    ) {
        super(
                method,
                requireNonNull(url, "Missing url"),
                new ErrorListenersWrapper(errorListeners)
        );
        this.listeners = new CopyOnWriteArrayList<>(listeners);
        this.parser = parser;
        this.bodyContentType = body.contentType();
        // Eager load bodyBytes to prevent calling user code on network dispatcher thread
        // (avoids potential nasty concurrency bugs)
        this.bodyBytes = body.bytes();
        this.priority = priority;
        this.headers = Collections.unmodifiableMap(
                requireNonNull(headers, "Pass empty map instead of null for headers")
        );
        this.params = Collections.unmodifiableMap(
                requireNonNull(params, "Pass empty map instead of null for params")
        );
        this.paramsEncoding = paramsEncoding;
    }

    @Override
    public void cancel() {
        super.cancel();
        listeners.clear();
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        return parser.parseNetworkResponse(response);
    }

    @Override
    protected void deliverResponse(T response) {
        for (Response.Listener<T> listener : listeners) {
            listener.onResponse(response);
        }
    }

    @Override
    public String getBodyContentType() {
        return bodyContentType;
    }

    @Override
    public byte[] getBody() throws AuthFailureError {
        return bodyBytes;
    }

    @Override
    public Priority getPriority() {
        return priority;
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return headers;
    }

    @Override
    public Map<String, String> getParams() {
        return params;
    }

    @Override
    protected String getParamsEncoding() {
        return paramsEncoding;
    }

    private static class ErrorListenersWrapper implements Response.ErrorListener {
        private final Collection<Response.ErrorListener> errorListeners;

        public ErrorListenersWrapper(Collection<Response.ErrorListener> errorListeners) {
            if (errorListeners.isEmpty()) {
                throw new NoSuchElementException("You must register at least one error listener");
            }
            this.errorListeners = new ArrayList<>(errorListeners);
        }

        @Override
        public void onErrorResponse(VolleyError error) {
            for (Response.ErrorListener errorListener : errorListeners) {
                errorListener.onErrorResponse(error);
            }
        }
    }
}

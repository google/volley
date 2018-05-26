package com.android.volley.toolbox.requestbuilder;

import static com.android.volley.Utils.requireNonNull;

import android.net.Uri;
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

/**
 * Class created by {@link RequestBuilder}. Prefer using the {@link RequestBuilder} to create this
 * class over the constructor in this class. This class is designed to be more parameterized, and
 * therefore easier to configure than the other {@link Request} subclasses.
 */
class BuildableRequest<T> extends Request<T> {

    private static String buildUrl(String baseUrl, Map<String, String> params) {
        if (params == null) {
            params = Collections.emptyMap();
        }

        Uri.Builder uriBuilder = Uri.parse(baseUrl).buildUpon();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            uriBuilder.appendQueryParameter(entry.getKey(), entry.getValue());
        }

        return uriBuilder.build().toString();
    }

    private final ResponseParser<T> parser;
    private final String bodyContentType;
    private final byte[] bodyBytes;
    private final Priority priority;
    private final Map<String, String> headers;
    private final Map<String, String> params;
    private final String paramsEncoding;

    private final Collection<Response.Listener<T>> listeners;

    /**
     * NOTE: Prefer using the {@link RequestBuilder} over this constructor.
     *
     * <p>See the {@link RequestBuilder} for documentation about these parameters.
     */
    public BuildableRequest(
            int method,
            String url,
            Collection<Response.Listener<T>> listeners,
            Collection<Response.ErrorListener> errorListeners,
            ResponseParser<T> parser,
            Body body,
            String bodyContentType,
            Priority priority,
            Map<String, String> headers,
            Map<String, String> params,
            String paramsEncoding) {
        super(
                method,
                buildUrl(requireNonNull(url, "Missing url"), params),
                new ErrorListenersWrapper(errorListeners));
        this.listeners = new CopyOnWriteArrayList<>(listeners);
        this.parser = parser;
        this.bodyContentType = bodyContentType;
        // Eager load bodyBytes to prevent calling user code on network dispatcher thread
        // (avoids potential nasty concurrency bugs). Assumes this is called on main thread.
        this.bodyBytes = body.bytes();
        this.priority = priority;
        this.headers =
                Collections.unmodifiableMap(
                        requireNonNull(headers, "Pass empty map instead of null for headers"));
        this.params =
                Collections.unmodifiableMap(
                        requireNonNull(params, "Pass empty map instead of null for params"));
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

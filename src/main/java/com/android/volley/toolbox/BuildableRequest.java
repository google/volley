package com.android.volley.toolbox;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;

import java.util.Collections;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * TODO documentation for this class, and for the constructor and methods
 */
public class BuildableRequest<T> extends Request<T> {

    private final ResponseParser<T> parser;
    private final String bodyContentType;
    private final byte[] body;
    private final Priority priority;
    private final Map<String, String> headers;
    private final Map<String, String> params;
    private final String paramsEncoding;
    private volatile Response.Listener<T> listener;

    /**
     * TODO docs
     * NOTE: Prefer using the {@link RequestBuilder} over this constructor.
     * @param method
     * @param url
     * @param listener
     * @param errorListener
     * @param parser
     * @param bodyContentType
     * @param body
     * @param priority
     * @param headers
     * @param params
     * @param paramsEncoding
     */
    public BuildableRequest(
            int method,
            String url,
            Response.Listener<T> listener,
            Response.ErrorListener errorListener,
            ResponseParser<T> parser,
            String bodyContentType,
            byte[] body,
            Priority priority,
            Map<String, String> headers,
            Map<String, String> params,
            String paramsEncoding
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
        listener = null;
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        return parser.parseNetworkResponse(response);
    }

    @Override
    protected void deliverResponse(T response) {
        Response.Listener<T> listener = this.listener;
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
}

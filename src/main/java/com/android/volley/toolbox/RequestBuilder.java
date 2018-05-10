package com.android.volley.toolbox;

import com.android.volley.Request;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.RetryPolicy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * TODO Documentation for this classed
 * TODO Documentation for this the methods
 *
 * @param <ResponseT> The type of the response
 * @param <ThisT> The type of this {@link RequestBuilder}. This type parameter allows creating
 *            subclasses, where each method on the builder is able to itself.
 */
public class RequestBuilder<ResponseT, ThisT extends RequestBuilder<ResponseT, ThisT>> {

    // TODO rename to startNew or start?
    // TODO doc with override note
    public static <T> RequestBuilder<T, ? extends RequestBuilder> create() {
        return new RequestBuilder<>();
    }

    protected int requestMethod = Request.DEFAULT_METHOD;
    protected String url = null;
    protected List<Listener<ResponseT>> listeners = new ArrayList<>();
    protected List<ErrorListener> errorListeners = new ArrayList<>();
    protected ResponseParser<ResponseT> parser = ResponseParsers.stub();
    protected Object tag;
    protected RetryPolicy retryPolicy;
    protected Boolean retryOnServerErrors;
    protected Boolean shouldCache;
    protected Request.Priority priority = Request.DEFAULT_PRIORITY;
    protected Map<String, String> headers = new HashMap<>();
    protected Map<String, String> params = new HashMap<>();
    protected String paramsEncoding = Request.DEFAULT_PARAMS_ENCODING;
    protected Body body = Bodies.STUB;

    private boolean hasBuilt;

    protected RequestBuilder() {
    }

    public ThisT method(int requestMethod) {
        this.requestMethod = requestMethod;
        return getThis();
    }

    public ThisT url(String url) {
        this.url = requireNonNull(url);
        return getThis();
    }

    public ThisT appendUrl(String append) {
        requireNonNull(url, "You must set a `url` before calling `appendUrl`");
        url += append;
        return getThis();
    }

    public ThisT onSuccess(Listener<ResponseT> listener) {
        this.listeners.add(requireNonNull(listener));
        return getThis();
    }

    public ThisT onError(ErrorListener errorListener) {
        this.errorListeners.add(requireNonNull(errorListener));
        return getThis();
    }

    public ThisT parseResponse(ResponseParser<ResponseT> parser) {
        this.parser = requireNonNull(parser);
        return getThis();
    }

    public ThisT tag(Object tag) {
        this.tag = requireNonNull(tag);
        return getThis();
    }

    public ThisT retryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = requireNonNull(retryPolicy);
        return getThis();
    }

    public ThisT retryOnServerErrors(boolean retryOnServerErrors) {
        this.retryOnServerErrors = retryOnServerErrors;
        return getThis();
    }

    public ThisT shouldCache(boolean shouldCache) {
        this.shouldCache = shouldCache;
        return getThis();
    }

    public ThisT priority(Request.Priority priority) {
        this.priority = requireNonNull(priority);
        return getThis();
    }

    public ThisT header(String key, String value) {
        headers.put(requireNonNull(key), requireNonNull(value));
        return getThis();
    }

    public ThisT headers(Map<String, String> map) {
        headers.putAll(requireNonNull(map));
        return getThis();
    }

    public ThisT range(String rangeName, int start, int end) {
        headers.put("Range", String.format(Locale.US, "%s=%d-%d", rangeName, start, end));
        return getThis();
    }

    // TODO refactor this with RangeBuilder in the future
    public ThisT rangeForPage(String rangeName, int pageNumber, int pageSize) {
        range(rangeName, pageNumber * pageSize, (pageNumber + 1 ) * pageSize - 1);
        return getThis();
    }

    public ThisT param(String key, String value) {
        params.put(requireNonNull(key), requireNonNull(value));
        return getThis();
    }

    public ThisT params(Map<String, String> map) {
        params.putAll(requireNonNull(map));
        return getThis();
    }

    public ThisT paramsEncoding(String encoding) {
        this.paramsEncoding = requireNonNull(encoding);
        return getThis();
    }

    public ThisT body(Body body) {
        this.body = requireNonNull(body);
        return getThis();
    }

    public Request<ResponseT> build() {
        if (hasBuilt) {
            throw new IllegalStateException(
                    "Already built using this builder. " +
                            "Use a new builder instead of reusing this one"
            );
        }
        hasBuilt = true;

        BuildableRequest<ResponseT> request = buildRequest();
        if (tag != null) {
            request.setTag(tag);
        }
        if (retryPolicy != null) {
            request.setRetryPolicy(retryPolicy);
        }
        if (retryOnServerErrors != null) {
            request.setShouldRetryServerErrors(retryOnServerErrors);
        }
        if (shouldCache != null) {
            request.setShouldCache(shouldCache);
        }
        return request;
    }

    protected BuildableRequest<ResponseT> buildRequest() {
        return new BuildableRequest<>(
                requestMethod,
                url,
                listeners,
                errorListeners,
                parser,
                body,
                priority,
                headers,
                params,
                paramsEncoding
        );
    }

    /**
     * Hack for java generics lacking the ability to refer to self.
     *
     * @return Casted this.
     */
    @SuppressWarnings("unchecked")
    protected ThisT getThis() {
        return (ThisT) this;
    }
}


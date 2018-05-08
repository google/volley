package com.android.volley.toolbox;

import com.android.volley.Request;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.RetryPolicy;

import static com.android.volley.Request.Method;
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

    public static <T> RequestBuilder<T, ? extends RequestBuilder> create() {
        return new RequestBuilder<>();
    }

    protected int requestMethod = Method.GET;
    protected String url = null;
    protected Listener<ResponseT> listener;
    protected ErrorListener errorListener;
    protected ResponseParser<ResponseT> parser;
    protected Object tag;
    protected RetryPolicy retryPolicy;
    protected Boolean retryOnServerErrors;
    protected Boolean shouldCache;

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
        this.listener = requireNonNull(listener);
        return getThis();
    }

    public ThisT onError(ErrorListener errorListener) {
        this.errorListener = requireNonNull(errorListener);
        return getThis();
    }

    public ThisT parseResponse(ResponseParser<ResponseT> parser) {
        this.parser = requireNonNull(parser);
        return getThis();
    }

    public ThisT tag(Object tag) {
        this.tag = tag;
        return getThis();
    }

    public ThisT retryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
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

    public Request<ResponseT> build() {
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
                listener == null ? new StubListener<ResponseT>() : listener,
                errorListener,
                parser == null ? ResponseParsers.<ResponseT>stub() : parser,
                null,
                null
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

    private static class StubListener<ResponseT> implements Listener<ResponseT> {
        @Override
        public void onResponse(ResponseT response) {
            // Stub
        }
    }
}


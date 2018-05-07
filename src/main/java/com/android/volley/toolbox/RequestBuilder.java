package com.android.volley.toolbox;

import com.android.volley.Request;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;

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

    protected String url = null;
    protected Listener<ResponseT> listener;
    protected ErrorListener errorListener;
    protected ResponseParser<ResponseT> parser;

    public ThisT url(String url) {
        this.url = requireNonNull(url);
        return getThis();
    }

    public ThisT appendUrl(String append) {
        this.url += append;
        return getThis();
    }

    public ThisT onSuccess(Listener<ResponseT> listener) {
        if (this.errorListener != null) {
            throw new IllegalStateException("Already set listener");
        }
        this.listener = requireNonNull(listener);
        return getThis();
    }

    public ThisT onError(ErrorListener errorListener) {
        if (this.errorListener != null) {
            throw new IllegalStateException("Already set error listener");
        }
        this.errorListener = requireNonNull(errorListener);
        return getThis();
    }

    public ThisT parseResponse(ResponseParser<ResponseT> parser) {
        if (this.parser != null) {
            throw new IllegalStateException("Already set parser");
        }
        this.parser = requireNonNull(parser);
        return getThis();
    }

    public Request<ResponseT> build() {
        return new BuildableRequest<>(
                Method.GET,
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


package com.android.volley.toolbox;

/**
 * TODO Documentation for this classed
 * TODO Documentation for this the methods
 *
 * @param <ResponseT> The type of the response
 * @param <ThisT> The type of this {@link RequestBuilder}. This type parameter allows creating
 *            subclasses, where each method on the builder is able to itself.
 */
public class RequestBuilder<ResponseT, ThisT extends RequestBuilder<ResponseT, ThisT>> {

    public static RequestBuilder<Void, ? extends RequestBuilder> create() {
        return new RequestBuilder<>();
    }

    protected String url = null;

    public ThisT url(String url) {
        this.url = url;
        return getThis();
    }

    public ThisT appendUrl(String append) {
        this.url += append;
        return getThis();
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


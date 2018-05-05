package com.android.volley.toolbox;

/**
 * TODO
 *
 * @param <ResponseT> The type of the response
 * @param <ThisT> The type of this {@link RequestBuilder}. This type parameter allows creating
 *            subclasses, where each method on the builder is able to itself.
 */
public class RequestBuilder<ResponseT, ThisT extends RequestBuilder<ResponseT, ThisT>> {

    public static RequestBuilder<Void, ? extends RequestBuilder> start() {
        return new RequestBuilder<>();
    }

    protected String url = "";

    public ThisT url(String url) {
        this.url = url;
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

    // TODO append url
}

/**
 * TODO
 *
 * @param <ResponseT>
 * @param <ThisT>
 */
class ExampleCustomRequestBuilder
        <ResponseT, ThisT extends ExampleCustomRequestBuilder<ResponseT, ThisT>>
        extends RequestBuilder<ResponseT, ThisT> {

    public static ExampleCustomRequestBuilder<Void, ? extends ExampleCustomRequestBuilder> start() {
        return new ExampleCustomRequestBuilder<>();
    }

    public static ExampleCustomRequestBuilder<Void, ? extends ExampleCustomRequestBuilder>
    startWithMyBusinessHeaders() {
        return start()
                .customSetSomething("")
                .url("a")
                .customSetSomething("")
                // TODO Make this a test example
                ;
    }

    public ThisT customSetSomething(String something) {
        // this.something = something
        return getThis();
    }
}

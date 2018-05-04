package com.android.volley.toolbox;

/**
 * TODO
 *
 * @param <T> The type of the response
 * @param <B> The type of this {@link RequestBuilder}. This type parameter allows creating
 *            subclasses, where each method on the builder is able to itself.
 */
public class RequestBuilder<T, B extends RequestBuilder<T, B>> {

    public static RequestBuilder<Void, ? extends RequestBuilder> start() {
        return new RequestBuilder<>();
    }

    protected String url = "";

    public B url(String url) {
        this.url = url;
        return getThis();
    }

    /**
     * Hack for java generics lacking the ability to refer to self.
     *
     * @return Casted this.
     */
    @SuppressWarnings("unchecked")
    protected B getThis() {
        return (B) this;
    }

    // TODO set url
}

/**
 * TODO
 *
 * @param <T>
 * @param <B>
 */
class ExampleCustomRequestBuilder<T, B extends ExampleCustomRequestBuilder<T, B>> extends RequestBuilder<T, B> {
    public static ExampleCustomRequestBuilder<Void, ? extends ExampleCustomRequestBuilder> start() {
        return new ExampleCustomRequestBuilder<>();
    }

    public static ExampleCustomRequestBuilder<Void, ? extends ExampleCustomRequestBuilder> startWithMyBusinessHeaders() {
        return start()
                .customSetSomething("")
                .url("a")
                .customSetSomething("")
                ;
    }

    public B customSetSomething(String something) {
        // this.something = something
        return getThis();
    }
}

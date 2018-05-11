package com.android.volley.toolbox;

/**
 * A {@link com.android.volley.Request} body, to be passed into {@link RequestBuilder#body(Body)}.
 * Usually, don't create an implementation of this directly. Use the implementations in {@link
 * Bodies} for convenience, unless you need to make your own.
 */
public interface Body {

    /** Data to send in a {@link com.android.volley.Request} as a {@link Body}. */
    byte[] bytes();

    /** Returns the content type of the POST or PUT body. */
    String contentType();
}

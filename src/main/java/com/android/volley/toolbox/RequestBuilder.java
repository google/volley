package com.android.volley.toolbox;

import static java.util.Objects.requireNonNull;

import com.android.volley.Request;
import com.android.volley.Request.Method;
import com.android.volley.RequestQueue;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.RetryPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Has all the convenient configuration methods for a {@link Request}, and is able to create a
 * single {@link Request}. The default method is {@link Method#GET}.
 *
 * <p>Steps for usage:
 *
 * <p>
 *
 * <ol>
 *   <li>Call the {@link #startNew()} method.
 *   <li>Call methods for configuration, such as {@link #url(String)}. Each of them return this
 *       {@link RequestBuilder} for chaining.
 *   <li>Call {@link #build()} to create the {@link Request}.
 *   <li>Call {@link Request#addTo(RequestQueue)}.
 *       <p>Example usage:
 * </ol>
 *
 * <p>
 *
 * <pre><code>
 * RequestBuilder.&lt;JSONObject&gt;startNew()
 *         .method(Request.Method.POST)
 *         .url("http://example.com")
 *         .param("key", "value")
 *         .header("key", "value")
 *         .onSuccess(new Response.Listener&lt;JSONObject&gt;() {
 *            {@literal @}Override
 *             public void onResponse(JSONObject response) {
 *                 // Do some stuff
 *             }
 *         })
 *         .onError(new Response.ErrorListener() {
 *            {@literal @}Override
 *             public void onErrorResponse(VolleyError error) {
 *                 // Do some stuff
 *             }
 *         })
 *         .build()
 *         .addTo(myRequestQueue);
 * </code></pre>
 *
 * <p>Note that you must set the generic type, when calling <code>
 * RequestBuilder.&lt;JSONObject&gt;startNew()</code> to the type of the response.
 *
 * <p>There are also various getter methods that are intended to be used for code that sets default
 * configurations. For example, see {@link ImageResponseParser#configureDefaults(RequestBuilder)}.
 *
 * <p>You can also extend this class, for example, to add logging and default headers to {@link
 * Request}s. See below for an example. (Note: If you are viewing the source code for Volley, then
 * you can copy paste the code without the escaped characters from {@code
 * com.android.volley.toolbox.RequestBuilderExtensibilityTest}.
 *
 * <p>
 *
 * <pre><code>
 * private static class ABCDRequestBuilder
 *         &lt;ResponseT, ThisT extends ABCDRequestBuilder&lt;ResponseT, ThisT&gt;&gt;
 *         extends RequestBuilder&lt;ResponseT, ThisT&gt; {
 *
 *     protected ABCDRequestBuilder() {
 *     }
 *
 *     // Creates builder with headers required to send to the ABCD API server.
 *     public static &lt;T&gt; ABCDRequestBuilder&lt;T, ? extends ABCDRequestBuilder&gt; startNew() {
 *         return ABCDRequestBuilder.&lt;T&gt;baseStartNew()
 *                 .addABCDAuthHeaders()
 *                 .url("http://my.base.url.for.requests.to.abcd.server/"); // we can then call {@link #appendUrl(String)}
 *     }
 *
 *     // Creates a normal builder, with extra loggers.
 *     public static &lt;T&gt; ABCDRequestBuilder&lt;T, ? extends ABCDRequestBuilder&gt; baseStartNew() {
 *         return ABCDRequestBuilder.&lt;T&gt;baseStartNewNoLogging().addABCDLoggers();
 *     }
 *
 *     // Creates a builder without any configuration
 *     public static &lt;T&gt; ABCDRequestBuilder&lt;T, ? extends ABCDRequestBuilder&gt; baseStartNewNoLogging() {
 *         return new ABCDRequestBuilder&lt;&gt;();
 *     }
 *
 *     public ThisT addABCDAuthHeaders() {
 *         header("Authentication", "key");
 *         return endSetter();
 *     }
 *
 *     public ThisT addABCDLoggers() {
 *         onSuccess(new Response.Listener&lt;ResponseT&gt;() {
 *            {@literal @}Override
 *             public void onResponse(ResponseT response) {
 *                 // Some logging here
 *             }
 *         });
 *         onError(new Response.ErrorListener() {
 *            {@literal @}Override
 *             public void onErrorResponse(VolleyError error) {
 *                 // Some logging here
 *             }
 *         });
 *         return endSetter();
 *     }
 * }
 * </code></pre>
 *
 * @param <ResponseT> The type of the response
 * @param <ThisT> The type of this {@link RequestBuilder}. This type parameter allows creating
 *     subclasses, where each method on the builder is able to itself.
 */
public class RequestBuilder<ResponseT, ThisT extends RequestBuilder<ResponseT, ThisT>> {

    /**
     * Creates a new {@link RequestBuilder}.
     *
     * @param <T> The type of the network response. As a convention, set it to {@link Void} if there
     *     is to be no response parsed.
     */
    public static <T> RequestBuilder<T, ? extends RequestBuilder> startNew() {
        return new RequestBuilder<>();
    }

    // Fields can be modified by custom subclasses
    // should set to null if they haven't been set yet (except for listeners, headers)

    protected Integer requestMethod;
    protected String url = null;
    protected List<Listener<ResponseT>> listeners = new ArrayList<>();
    protected List<ErrorListener> errorListeners = new ArrayList<>();
    protected ResponseParser<ResponseT> parser;
    protected Object tag;
    protected RetryPolicy retryPolicy;
    protected Boolean retryOnServerErrors;
    protected Boolean shouldCache;
    protected Request.Priority priority;
    protected Map<String, String> headers = new HashMap<>();
    protected Map<String, String> params = new HashMap<>();
    protected String paramsEncoding;
    protected Body body;
    protected String bodyContentType;

    private boolean hasBuilt;

    /**
     * {@link RequestBuilder}s should be created by the factory method {@link #startNew()}. This
     * constructor is protected only to allow extension of this class.
     */
    protected RequestBuilder() {}

    /** Takes a {@link Method} */
    public ThisT method(int requestMethod) {
        this.requestMethod = requestMethod;
        return endSetter();
    }

    /** Url for the {@link Request} */
    public ThisT url(String url) {
        this.url = requireNonNull(url);
        return endSetter();
    }

    /**
     * Appends a string to the current {@link #url}. A url must be initially set before calling
     * this.
     *
     * <p>This method is useful when setting a default base url in a factory method for your own
     * {@link RequestBuilder}s. Use {@link #appendUrl(String)} to add the name of the API endpoint
     * to the url.
     */
    public ThisT appendUrl(String append) {
        requireNonNull(url, "You must set a `url` before calling `appendUrl`");
        url += append;
        return endSetter();
    }

    /**
     * Adds a listener to an internal list. Multiple listeners can be added. (This is useful for
     * logging). The listeners are called after the {@link Request} has failed.
     *
     * @param listener An object that receives a response of the {@link Request} built by this
     *     {@link RequestBuilder}.
     */
    public ThisT onSuccess(Listener<ResponseT> listener) {
        this.listeners.add(requireNonNull(listener));
        return endSetter();
    }

    /**
     * Adds an error listener to an internal list. Multiple error listeners can be added. (This is
     * useful for logging). The error listeners are called after the {@link Request} has failed.
     */
    public ThisT onError(ErrorListener errorListener) {
        this.errorListeners.add(requireNonNull(errorListener));
        return endSetter();
    }

    /**
     * Sets the response parser. The given parser must match the type of this {@link
     * RequestBuilder}.
     */
    public ThisT parseResponse(ResponseParser<ResponseT> parser) {
        this.parser = requireNonNull(parser);
        parser.configureDefaults(this);
        return endSetter();
    }

    /** @see Request#setTag(Object) */
    public ThisT tag(Object tag) {
        this.tag = requireNonNull(tag);
        return endSetter();
    }

    /** @see Request#setRetryPolicy(RetryPolicy) */
    public ThisT retryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = requireNonNull(retryPolicy);
        return endSetter();
    }

    /** @see Request#setShouldRetryServerErrors(boolean) */
    public ThisT retryOnServerErrors(boolean retryOnServerErrors) {
        this.retryOnServerErrors = retryOnServerErrors;
        return endSetter();
    }

    /** @see Request#setShouldCache(boolean) */
    public ThisT shouldCache(boolean shouldCache) {
        this.shouldCache = shouldCache;
        return endSetter();
    }

    /**
     * Sets the priority of the {@link Request}.
     *
     * @see Request#getPriority()
     */
    public ThisT priority(Request.Priority priority) {
        this.priority = requireNonNull(priority);
        return endSetter();
    }

    /** Adds a HTTP header key-value pair to a map. */
    public ThisT header(String key, String value) {
        headers.put(requireNonNull(key), requireNonNull(value));
        return endSetter();
    }

    /**
     * Adds multiple HTTP headers from the given map.
     *
     * @see #header(String, String)
     */
    public ThisT headers(Map<String, String> map) {
        headers.putAll(requireNonNull(map));
        return endSetter();
    }

    /** Convenience method for adding a Range HTTP header. */
    public ThisT range(String rangeName, int start, int end) {
        headers.put("Range", String.format(Locale.US, "%s=%d-%d", rangeName, start, end));
        return endSetter();
    }

    // TODO refactor this with RangeBuilder in the future
    /** Convenience method for adding a Range HTTP header for paginated requests. */
    public ThisT rangeForPage(String rangeName, int pageNumber, int pageSize) {
        range(rangeName, pageNumber * pageSize, (pageNumber + 1) * pageSize - 1);
        return endSetter();
    }

    /**
     * Adds a query parameter key-value pair to a map.
     *
     * <p>Prefer using this method over {@link Bodies#forParams(Map)}.
     */
    public ThisT param(String key, String value) {
        params.put(requireNonNull(key), requireNonNull(value));
        return endSetter();
    }

    /**
     * Adds multiple query parameters key-value pair from the given map.
     *
     * @see #param(String, String)
     */
    public ThisT params(Map<String, String> map) {
        params.putAll(requireNonNull(map));
        return endSetter();
    }

    /**
     * @see Request#getParamsEncoding()
     * @see #param(String, String)
     */
    public ThisT paramsEncoding(String encoding) {
        this.paramsEncoding = requireNonNull(encoding);
        return endSetter();
    }

    /**
     * Sets the body of the {@link Request}, e.g. for a {@link Method#POST} request. Remember to set
     * the {@link #method(int)} on this builder.
     *
     * @see Request#getBody()
     */
    public ThisT body(Body body) {
        this.body = requireNonNull(body);
        body.configureDefaults(this);
        return endSetter();
    }

    /** See {@link Request#getBodyContentType()} */
    public ThisT bodyContentType(String type) {
        this.bodyContentType = requireNonNull(type);
        return endSetter();
    }

    /**
     * Getter to be used in a sort of templated configuration, e.g. {@link
     * Body#configureDefaults(RequestBuilder)}. Not for normal use. Returns null if not set yet.
     * This {@link RequestBuilder} will fallback to default values for most fields in this class.
     */
    public Integer getRequestMethod() {
        return requestMethod;
    }

    /**
     * Getter to be used in a sort of templated configuration, e.g. {@link
     * Body#configureDefaults(RequestBuilder)}. Returns null if not set yet.
     */
    public String getUrl() {
        return url;
    }

    // TODO
    public List<Listener<ResponseT>> getListeners() {
        return Collections.unmodifiableList(listeners);
    }

    public List<ErrorListener> getErrorListeners() {
        return Collections.unmodifiableList(errorListeners);
    }

    /** See description for {@link #getUrl()} */
    public ResponseParser<ResponseT> getParser() {
        return parser;
    }

    /** See description for {@link #getUrl()} */
    public Object getTag() {
        return tag;
    }

    /** See description for {@link #getUrl()} */
    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    /** See description for {@link #getUrl()} */
    public Boolean getRetryOnServerErrors() {
        return retryOnServerErrors;
    }

    /** See description for {@link #getUrl()} */
    public Boolean getShouldCache() {
        return shouldCache;
    }

    /** See description for {@link #getUrl()} */
    public Request.Priority getPriority() {
        return priority;
    }

    // TODO
    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public Map<String, String> getParams() {
        return Collections.unmodifiableMap(params);
    }

    /** See description for {@link #getUrl()} */
    public String getParamsEncoding() {
        return paramsEncoding;
    }

    /** See description for {@link #getUrl()} */
    public Body getBody() {
        return body;
    }

    /** See description for {@link #getUrl()} */
    public String getBodyContentType() {
        return bodyContentType;
    }

    /**
     * Creates the {@link Request}. Can only be called once per builder. If you want to create your
     * own {@link Request} subclass, then extend this class (see class documentation) and add your
     * own build method with a different name.
     */
    public final Request<ResponseT> build() {
        checkNotBuilt(true);

        Request<ResponseT> request = buildRequest();
        configureAfterBuilt(request);
        return request;
    }

    protected void checkNotBuilt(boolean aboutToBuild) {
        if (hasBuilt) {
            throw new IllegalStateException(
                    "Already built using this builder. "
                            + "Use a new builder instead of reusing this one");
        }

        if (aboutToBuild) {
            hasBuilt = true;
        }
    }

    /**
     * Hack for java generics lacking the ability to refer to self. This casts this to its own type
     * for chaining methods, which work even after extending this class. This is safe because the
     * factory methods will not allow the user to specify an incorrect type parameter for ThisT,
     * unless the user creates their own factory methods and uses the wrong generic type (which
     * would be hard to do).
     *
     * @return Safely casted this.
     */
    @SuppressWarnings("unchecked")
    protected ThisT getThis() {
        return (ThisT) this;
    }

    /**
     * To be called at the end of every setter method on this {@link RequestBuilder} (see example
     * below). Uses the method {@link #getThis()} to do casting.
     *
     * <p>Example usage:
     *
     * <p>
     *
     * <pre><code>
     *     public ThisT url(String url) {
     *         this.url = requireNonNull(url);
     *         return endSetter();
     *     }
     * </code></pre>
     *
     * @return Safely casted this, to be returned in setter methods on this builder, for chaining.
     * @see #getThis()
     */
    protected ThisT endSetter() {
        checkNotBuilt(false);
        return getThis();
    }

    private Request<ResponseT> buildRequest() {
        return new BuildableRequest<>(
                or(requestMethod, Request.DEFAULT_METHOD),
                url,
                listeners,
                errorListeners,
                or(parser, ResponseParsers.<ResponseT>stub()),
                or(body, Bodies.STUB),
                or(bodyContentType, Bodies.DEFAULT_CONTENT_TYPE),
                or(priority, Request.DEFAULT_PRIORITY),
                headers,
                params,
                or(paramsEncoding, Request.DEFAULT_PARAMS_ENCODING));
    }

    private void configureAfterBuilt(Request<ResponseT> request) {
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
    }

    private <T> T or(T valueOrNull, T defaultValue) {
        if (valueOrNull == null) {
            return requireNonNull(defaultValue);
        }

        return valueOrNull;
    }
}

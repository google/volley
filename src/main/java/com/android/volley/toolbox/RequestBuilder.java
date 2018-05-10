package com.android.volley.toolbox;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
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
 * Has all of the configuration possible for a {@link Request}, and is able to create a single
 * {@link Request}.
 * <p>
 * Steps for usage:
 * <p><ol>
 * <li>Call the {@link #startNew()} method.
 * <li>Call methods for configuration, such as {@link #url(String)}. Each of them return this
 *     {@link RequestBuilder} for chaining.
 * <li>Call {@link #build()} to create the {@link Request}.
 * <li>Call {@link Request#addTo(RequestQueue)}.
 * <p>
 * Example usage:
 * </ol><p>
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
 * <p>
 * Note that you must set the generic type, when calling
 * <code>RequestBuilder.&lt;JSONObject&gt;startNew()</code> to the type of the response.
 * TODO there may be a better way to do this
 * <p>
 * You can also extend this class, for example, to add logging and default headers to
 * {@link Request}s. See below for an example:
 * <p>
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
 *         return ABCDRequestBuilder.&lt;T&gt;baseStartNew().addABCDAuthHeaders();
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
 *         return getThis();
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
 *         return getThis();
 *     }
 * }
 * </code></pre>
 *
 * @param <ResponseT> The type of the response
 * @param <ThisT>     The type of this {@link RequestBuilder}. This type parameter allows creating
 *                    subclasses, where each method on the builder is able to itself.
 */
public class RequestBuilder<ResponseT, ThisT extends RequestBuilder<ResponseT, ThisT>> {

    // TODO rename to startNew or start?
    // TODO doc with override note
    public static <T> RequestBuilder<T, ? extends RequestBuilder> startNew() {
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


/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.volley;

import android.net.TrafficStats;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.CallSuper;
import android.support.annotation.GuardedBy;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.android.volley.VolleyLog.MarkerLog;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Map;

/**
 * Base class for all network requests.
 *
 * @param <T> The type of parsed response this request expects.
 */
public abstract class Request<T> implements Comparable<Request<T>> {

    /** Default encoding for POST or PUT parameters. See {@link #getParamsEncoding()}. */
    private static final String DEFAULT_PARAMS_ENCODING = "UTF-8";

    /** Supported request methods. */
    public interface Method {
        int DEPRECATED_GET_OR_POST = -1;
        int GET = 0;
        int POST = 1;
        int PUT = 2;
        int DELETE = 3;
        int HEAD = 4;
        int OPTIONS = 5;
        int TRACE = 6;
        int PATCH = 7;
    }

    /** Callback to notify when the network request returns. */
    /* package */ interface NetworkRequestCompleteListener {

        /** Callback when a network response has been received. */
        void onResponseReceived(Request<?> request, Response<?> response);

        /** Callback when request returns from network without valid response. */
        void onNoUsableResponseReceived(Request<?> request);
    }

    /** An event log tracing the lifetime of this request; for debugging. */
    private final MarkerLog mEventLog = MarkerLog.ENABLED ? new MarkerLog() : null;

    /**
     * Request method of this request. Currently supports GET, POST, PUT, DELETE, HEAD, OPTIONS,
     * TRACE, and PATCH.
     */
    private final int mMethod;

    /** URL of this request. */
    private final String mUrl;

    /** Default tag for {@link TrafficStats}. */
    private final int mDefaultTrafficStatsTag;

    /** Lock to guard state which can be mutated after a request is added to the queue. */
    private final Object mLock = new Object();

    /** Listener interface for errors. */
    @Nullable
    @GuardedBy("mLock")
    private Response.ErrorListener mErrorListener;

    /** Sequence number of this request, used to enforce FIFO ordering. */
    private Integer mSequence;

    /** The request queue this request is associated with. */
    private RequestQueue mRequestQueue;

    /** Whether or not responses to this request should be cached. */
    // TODO(#190): Turn this off by default for anything other than GET requests.
    private boolean mShouldCache = true;

    /** Whether or not this request has been canceled. */
    @GuardedBy("mLock")
    private boolean mCanceled = false;

    /** Whether or not a response has been delivered for this request yet. */
    @GuardedBy("mLock")
    private boolean mResponseDelivered = false;

    /** Whether the request should be retried in the event of an HTTP 5xx (server) error. */
    private boolean mShouldRetryServerErrors = false;

    /** The retry policy for this request. */
    private RetryPolicy mRetryPolicy;

    /**
     * When a request can be retrieved from cache but must be refreshed from the network, the cache
     * entry will be stored here so that in the event of a "Not Modified" response, we can be sure
     * it hasn't been evicted from cache.
     */
    private Cache.Entry mCacheEntry = null;

    /** An opaque token tagging this request; used for bulk cancellation. */
    private Object mTag;

    /** Listener that will be notified when a response has been delivered. */
    @GuardedBy("mLock")
    private NetworkRequestCompleteListener mRequestCompleteListener;

    /**
     * Creates a new request with the given URL and error listener. Note that the normal response
     * listener is not provided here as delivery of responses is provided by subclasses, who have a
     * better idea of how to deliver an already-parsed response.
     *
     * @deprecated Use {@link #Request(int, String, com.android.volley.Response.ErrorListener)}.
     */
    @Deprecated
    public Request(String url, Response.ErrorListener listener) {
        this(Method.DEPRECATED_GET_OR_POST, url, listener);
    }

    /**
     * Creates a new request with the given method (one of the values from {@link Method}), URL, and
     * error listener. Note that the normal response listener is not provided here as delivery of
     * responses is provided by subclasses, who have a better idea of how to deliver an
     * already-parsed response.
     */
    public Request(int method, String url, @Nullable Response.ErrorListener listener) {
        mMethod = method;
        mUrl = url;
        mErrorListener = listener;
        setRetryPolicy(new DefaultRetryPolicy());

        mDefaultTrafficStatsTag = findDefaultTrafficStatsTag(url);
    }

    /** Return the method for this request. Can be one of the values in {@link Method}. */
    public int getMethod() {
        return mMethod;
    }

    /**
     * Set a tag on this request. Can be used to cancel all requests with this tag by {@link
     * RequestQueue#cancelAll(Object)}.
     *
     * @return This Request object to allow for chaining.
     */
    public Request<?> setTag(Object tag) {
        mTag = tag;
        return this;
    }

    /**
     * Returns this request's tag.
     *
     * @see Request#setTag(Object)
     */
    public Object getTag() {
        return mTag;
    }

    /** @return this request's {@link com.android.volley.Response.ErrorListener}. */
    @Nullable
    public Response.ErrorListener getErrorListener() {
        synchronized (mLock) {
            return mErrorListener;
        }
    }

    /** @return A tag for use with {@link TrafficStats#setThreadStatsTag(int)} */
    public int getTrafficStatsTag() {
        return mDefaultTrafficStatsTag;
    }

    /** @return The hashcode of the URL's host component, or 0 if there is none. */
    private static int findDefaultTrafficStatsTag(String url) {
        if (!TextUtils.isEmpty(url)) {
            Uri uri = Uri.parse(url);
            if (uri != null) {
                String host = uri.getHost();
                if (host != null) {
                    return host.hashCode();
                }
            }
        }
        return 0;
    }

    /**
     * Sets the retry policy for this request.
     *
     * @return This Request object to allow for chaining.
     */
    public Request<?> setRetryPolicy(RetryPolicy retryPolicy) {
        mRetryPolicy = retryPolicy;
        return this;
    }

    /** Adds an event to this request's event log; for debugging. */
    public void addMarker(String tag) {
        if (MarkerLog.ENABLED) {
            mEventLog.add(tag, Thread.currentThread().getId());
        }
    }

    /**
     * Notifies the request queue that this request has finished (successfully or with error).
     *
     * <p>Also dumps all events from this request's event log; for debugging.
     */
    void finish(final String tag) {
        if (mRequestQueue != null) {
            mRequestQueue.finish(this);
        }
        if (MarkerLog.ENABLED) {
            final long threadId = Thread.currentThread().getId();
            if (Looper.myLooper() != Looper.getMainLooper()) {
                // If we finish marking off of the main thread, we need to
                // actually do it on the main thread to ensure correct ordering.
                Handler mainThread = new Handler(Looper.getMainLooper());
                mainThread.post(
                        new Runnable() {
                            @Override
                            public void run() {
                                mEventLog.add(tag, threadId);
                                mEventLog.finish(Request.this.toString());
                            }
                        });
                return;
            }

            mEventLog.add(tag, threadId);
            mEventLog.finish(this.toString());
        }
    }

    /**
     * Associates this request with the given queue. The request queue will be notified when this
     * request has finished.
     *
     * @return This Request object to allow for chaining.
     */
    public Request<?> setRequestQueue(RequestQueue requestQueue) {
        mRequestQueue = requestQueue;
        return this;
    }

    /**
     * Sets the sequence number of this request. Used by {@link RequestQueue}.
     *
     * @return This Request object to allow for chaining.
     */
    public final Request<?> setSequence(int sequence) {
        mSequence = sequence;
        return this;
    }

    /** Returns the sequence number of this request. */
    public final int getSequence() {
        if (mSequence == null) {
            throw new IllegalStateException("getSequence called before setSequence");
        }
        return mSequence;
    }

    /** Returns the URL of this request. */
    public String getUrl() {
        return mUrl;
    }

    /** Returns the cache key for this request. By default, this is the URL. */
    public String getCacheKey() {
        String url = getUrl();
        // If this is a GET request, just use the URL as the key.
        // For callers using DEPRECATED_GET_OR_POST, we assume the method is GET, which matches
        // legacy behavior where all methods had the same cache key. We can't determine which method
        // will be used because doing so requires calling getPostBody() which is expensive and may
        // throw AuthFailureError.
        // TODO(#190): Remove support for non-GET methods.
        int method = getMethod();
        if (method == Method.GET || method == Method.DEPRECATED_GET_OR_POST) {
            return url;
        }
        return Integer.toString(method) + '-' + url;
    }

    /**
     * Annotates this request with an entry retrieved for it from cache. Used for cache coherency
     * support.
     *
     * @return This Request object to allow for chaining.
     */
    public Request<?> setCacheEntry(Cache.Entry entry) {
        mCacheEntry = entry;
        return this;
    }

    /** Returns the annotated cache entry, or null if there isn't one. */
    public Cache.Entry getCacheEntry() {
        return mCacheEntry;
    }

    /**
     * Mark this request as canceled.
     *
     * <p>No callback will be delivered as long as either:
     *
     * <ul>
     *   <li>This method is called on the same thread as the {@link ResponseDelivery} is running on.
     *       By default, this is the main thread.
     *   <li>The request subclass being used overrides cancel() and ensures that it does not invoke
     *       the listener in {@link #deliverResponse} after cancel() has been called in a
     *       thread-safe manner.
     * </ul>
     *
     * <p>There are no guarantees if both of these conditions aren't met.
     */
    @CallSuper
    public void cancel() {
        synchronized (mLock) {
            mCanceled = true;
            mErrorListener = null;
        }
    }

    /** Returns true if this request has been canceled. */
    public boolean isCanceled() {
        synchronized (mLock) {
            return mCanceled;
        }
    }

    /**
     * Returns a list of extra HTTP headers to go along with this request. Can throw {@link
     * AuthFailureError} as authentication may be required to provide these values.
     *
     * @throws AuthFailureError In the event of auth failure
     */
    public Map<String, String> getHeaders() throws AuthFailureError {
        return Collections.emptyMap();
    }

    /**
     * Returns a Map of POST parameters to be used for this request, or null if a simple GET should
     * be used. Can throw {@link AuthFailureError} as authentication may be required to provide
     * these values.
     *
     * <p>Note that only one of getPostParams() and getPostBody() can return a non-null value.
     *
     * @throws AuthFailureError In the event of auth failure
     * @deprecated Use {@link #getParams()} instead.
     */
    @Deprecated
    protected Map<String, String> getPostParams() throws AuthFailureError {
        return getParams();
    }

    /**
     * Returns which encoding should be used when converting POST parameters returned by {@link
     * #getPostParams()} into a raw POST body.
     *
     * <p>This controls both encodings:
     *
     * <ol>
     *   <li>The string encoding used when converting parameter names and values into bytes prior to
     *       URL encoding them.
     *   <li>The string encoding used when converting the URL encoded parameters into a raw byte
     *       array.
     * </ol>
     *
     * @deprecated Use {@link #getParamsEncoding()} instead.
     */
    @Deprecated
    protected String getPostParamsEncoding() {
        return getParamsEncoding();
    }

    /** @deprecated Use {@link #getBodyContentType()} instead. */
    @Deprecated
    public String getPostBodyContentType() {
        return getBodyContentType();
    }

    /**
     * Returns the raw POST body to be sent.
     *
     * @throws AuthFailureError In the event of auth failure
     * @deprecated Use {@link #getBody()} instead.
     */
    @Deprecated
    public byte[] getPostBody() throws AuthFailureError {
        // Note: For compatibility with legacy clients of volley, this implementation must remain
        // here instead of simply calling the getBody() function because this function must
        // call getPostParams() and getPostParamsEncoding() since legacy clients would have
        // overridden these two member functions for POST requests.
        Map<String, String> postParams = getPostParams();
        if (postParams != null && postParams.size() > 0) {
            return encodeParameters(postParams, getPostParamsEncoding());
        }
        return null;
    }

    /**
     * Returns a Map of parameters to be used for a POST or PUT request. Can throw {@link
     * AuthFailureError} as authentication may be required to provide these values.
     *
     * <p>Note that you can directly override {@link #getBody()} for custom data.
     *
     * @throws AuthFailureError in the event of auth failure
     */
    protected Map<String, String> getParams() throws AuthFailureError {
        return null;
    }

    /**
     * Returns which encoding should be used when converting POST or PUT parameters returned by
     * {@link #getParams()} into a raw POST or PUT body.
     *
     * <p>This controls both encodings:
     *
     * <ol>
     *   <li>The string encoding used when converting parameter names and values into bytes prior to
     *       URL encoding them.
     *   <li>The string encoding used when converting the URL encoded parameters into a raw byte
     *       array.
     * </ol>
     */
    protected String getParamsEncoding() {
        return DEFAULT_PARAMS_ENCODING;
    }

    /** Returns the content type of the POST or PUT body. */
    public String getBodyContentType() {
        return "application/x-www-form-urlencoded; charset=" + getParamsEncoding();
    }

    /**
     * Returns the raw POST or PUT body to be sent.
     *
     * <p>By default, the body consists of the request parameters in
     * application/x-www-form-urlencoded format. When overriding this method, consider overriding
     * {@link #getBodyContentType()} as well to match the new body format.
     *
     * @throws AuthFailureError in the event of auth failure
     */
    public byte[] getBody() throws AuthFailureError {
        Map<String, String> params = getParams();
        if (params != null && params.size() > 0) {
            return encodeParameters(params, getParamsEncoding());
        }
        return null;
    }

    /** Converts <code>params</code> into an application/x-www-form-urlencoded encoded string. */
    private byte[] encodeParameters(Map<String, String> params, String paramsEncoding) {
        StringBuilder encodedParams = new StringBuilder();
        try {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Request#getParams() or Request#getPostParams() returned a map "
                                            + "containing a null key or value: (%s, %s). All keys "
                                            + "and values must be non-null.",
                                    entry.getKey(), entry.getValue()));
                }
                encodedParams.append(URLEncoder.encode(entry.getKey(), paramsEncoding));
                encodedParams.append('=');
                encodedParams.append(URLEncoder.encode(entry.getValue(), paramsEncoding));
                encodedParams.append('&');
            }
            return encodedParams.toString().getBytes(paramsEncoding);
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException("Encoding not supported: " + paramsEncoding, uee);
        }
    }

    /**
     * Set whether or not responses to this request should be cached.
     *
     * @return This Request object to allow for chaining.
     */
    public final Request<?> setShouldCache(boolean shouldCache) {
        mShouldCache = shouldCache;
        return this;
    }

    /** Returns true if responses to this request should be cached. */
    public final boolean shouldCache() {
        return mShouldCache;
    }

    /**
     * Sets whether or not the request should be retried in the event of an HTTP 5xx (server) error.
     *
     * @return This Request object to allow for chaining.
     */
    public final Request<?> setShouldRetryServerErrors(boolean shouldRetryServerErrors) {
        mShouldRetryServerErrors = shouldRetryServerErrors;
        return this;
    }

    /**
     * Returns true if this request should be retried in the event of an HTTP 5xx (server) error.
     */
    public final boolean shouldRetryServerErrors() {
        return mShouldRetryServerErrors;
    }

    /**
     * Priority values. Requests will be processed from higher priorities to lower priorities, in
     * FIFO order.
     */
    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        IMMEDIATE
    }

    /** Returns the {@link Priority} of this request; {@link Priority#NORMAL} by default. */
    public Priority getPriority() {
        return Priority.NORMAL;
    }

    /**
     * Returns the socket timeout in milliseconds per retry attempt. (This value can be changed per
     * retry attempt if a backoff is specified via backoffTimeout()). If there are no retry attempts
     * remaining, this will cause delivery of a {@link TimeoutError} error.
     */
    public final int getTimeoutMs() {
        return getRetryPolicy().getCurrentTimeout();
    }

    /** Returns the retry policy that should be used for this request. */
    public RetryPolicy getRetryPolicy() {
        return mRetryPolicy;
    }

    /**
     * Mark this request as having a response delivered on it. This can be used later in the
     * request's lifetime for suppressing identical responses.
     */
    public void markDelivered() {
        synchronized (mLock) {
            mResponseDelivered = true;
        }
    }

    /** Returns true if this request has had a response delivered for it. */
    public boolean hasHadResponseDelivered() {
        synchronized (mLock) {
            return mResponseDelivered;
        }
    }

    /**
     * Subclasses must implement this to parse the raw network response and return an appropriate
     * response type. This method will be called from a worker thread. The response will not be
     * delivered if you return null.
     *
     * @param response Response from the network
     * @return The parsed response, or null in the case of an error
     */
    protected abstract Response<T> parseNetworkResponse(NetworkResponse response);

    /**
     * Subclasses can override this method to parse 'networkError' and return a more specific error.
     *
     * <p>The default implementation just returns the passed 'networkError'.
     *
     * @param volleyError the error retrieved from the network
     * @return an NetworkError augmented with additional information
     */
    protected VolleyError parseNetworkError(VolleyError volleyError) {
        return volleyError;
    }

    /**
     * Subclasses must implement this to perform delivery of the parsed response to their listeners.
     * The given response is guaranteed to be non-null; responses that fail to parse are not
     * delivered.
     *
     * @param response The parsed response returned by {@link
     *     #parseNetworkResponse(NetworkResponse)}
     */
    protected abstract void deliverResponse(T response);

    /**
     * Delivers error message to the ErrorListener that the Request was initialized with.
     *
     * @param error Error details
     */
    public void deliverError(VolleyError error) {
        Response.ErrorListener listener;
        synchronized (mLock) {
            listener = mErrorListener;
        }
        if (listener != null) {
            listener.onErrorResponse(error);
        }
    }

    /**
     * {@link NetworkRequestCompleteListener} that will receive callbacks when the request returns
     * from the network.
     */
    /* package */ void setNetworkRequestCompleteListener(
            NetworkRequestCompleteListener requestCompleteListener) {
        synchronized (mLock) {
            mRequestCompleteListener = requestCompleteListener;
        }
    }

    /**
     * Notify NetworkRequestCompleteListener that a valid response has been received which can be
     * used for other, waiting requests.
     *
     * @param response received from the network
     */
    /* package */ void notifyListenerResponseReceived(Response<?> response) {
        NetworkRequestCompleteListener listener;
        synchronized (mLock) {
            listener = mRequestCompleteListener;
        }
        if (listener != null) {
            listener.onResponseReceived(this, response);
        }
    }

    /**
     * Notify NetworkRequestCompleteListener that the network request did not result in a response
     * which can be used for other, waiting requests.
     */
    /* package */ void notifyListenerResponseNotUsable() {
        NetworkRequestCompleteListener listener;
        synchronized (mLock) {
            listener = mRequestCompleteListener;
        }
        if (listener != null) {
            listener.onNoUsableResponseReceived(this);
        }
    }

    /**
     * Our comparator sorts from high to low priority, and secondarily by sequence number to provide
     * FIFO ordering.
     */
    @Override
    public int compareTo(Request<T> other) {
        Priority left = this.getPriority();
        Priority right = other.getPriority();

        // High-priority requests are "lesser" so they are sorted to the front.
        // Equal priorities are sorted by sequence number to provide FIFO ordering.
        return left == right ? this.mSequence - other.mSequence : right.ordinal() - left.ordinal();
    }

    @Override
    public String toString() {
        String trafficStatsTag = "0x" + Integer.toHexString(getTrafficStatsTag());
        return (isCanceled() ? "[X] " : "[ ] ")
                + getUrl()
                + " "
                + trafficStatsTag
                + " "
                + getPriority()
                + " "
                + mSequence;
    }
}

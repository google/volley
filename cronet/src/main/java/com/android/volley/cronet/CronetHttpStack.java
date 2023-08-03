/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.volley.cronet;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.android.volley.AuthFailureError;
import com.android.volley.Header;
import com.android.volley.Request;
import com.android.volley.RequestTask;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.AsyncHttpStack;
import com.android.volley.toolbox.ByteArrayPool;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.HttpResponse;
import com.android.volley.toolbox.PoolingByteArrayOutputStream;
import com.android.volley.toolbox.UrlRewriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.UploadDataProvider;
import org.chromium.net.UploadDataProviders;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlRequest.Callback;
import org.chromium.net.UrlResponseInfo;

/**
 * A {@link AsyncHttpStack} that's based on Cronet's fully asynchronous API for network requests.
 *
 * <p><b>WARNING</b>: This API is experimental and subject to breaking changes. Please see
 * https://github.com/google/volley/wiki/Asynchronous-Volley for more details.
 */
public class CronetHttpStack extends AsyncHttpStack {

    private final CronetEngine mCronetEngine;
    private final ByteArrayPool mPool;
    private final UrlRewriter mUrlRewriter;
    private final RequestListener mRequestListener;

    // cURL logging support
    private final boolean mCurlLoggingEnabled;
    private final CurlCommandLogger mCurlCommandLogger;
    private final boolean mLogAuthTokensInCurlCommands;

    private CronetHttpStack(
            CronetEngine cronetEngine,
            ByteArrayPool pool,
            UrlRewriter urlRewriter,
            RequestListener requestListener,
            boolean curlLoggingEnabled,
            CurlCommandLogger curlCommandLogger,
            boolean logAuthTokensInCurlCommands) {
        mCronetEngine = cronetEngine;
        mPool = pool;
        mUrlRewriter = urlRewriter;
        mRequestListener = requestListener;
        mCurlLoggingEnabled = curlLoggingEnabled;
        mCurlCommandLogger = curlCommandLogger;
        mLogAuthTokensInCurlCommands = logAuthTokensInCurlCommands;

        mRequestListener.initialize(this);
    }

    @Override
    public void executeRequest(
            final Request<?> request,
            final Map<String, String> additionalHeaders,
            final OnRequestComplete callback) {
        if (getBlockingExecutor() == null || getNonBlockingExecutor() == null) {
            throw new IllegalStateException("Must set blocking and non-blocking executors");
        }
        final Callback urlCallback =
                new Callback() {
                    PoolingByteArrayOutputStream bytesReceived = null;
                    WritableByteChannel receiveChannel = null;

                    @Override
                    public void onRedirectReceived(
                            UrlRequest urlRequest,
                            UrlResponseInfo urlResponseInfo,
                            String newLocationUrl) {
                        urlRequest.followRedirect();
                    }

                    @Override
                    public void onResponseStarted(
                            UrlRequest urlRequest, UrlResponseInfo urlResponseInfo) {
                        bytesReceived =
                                new PoolingByteArrayOutputStream(
                                        mPool, getContentLength(urlResponseInfo));
                        receiveChannel = Channels.newChannel(bytesReceived);
                        urlRequest.read(ByteBuffer.allocateDirect(1024));
                    }

                    @Override
                    public void onReadCompleted(
                            UrlRequest urlRequest,
                            UrlResponseInfo urlResponseInfo,
                            ByteBuffer byteBuffer) {
                        byteBuffer.flip();
                        try {
                            receiveChannel.write(byteBuffer);
                            byteBuffer.clear();
                            urlRequest.read(byteBuffer);
                        } catch (IOException e) {
                            urlRequest.cancel();
                            callback.onError(e);
                        }
                    }

                    @Override
                    public void onSucceeded(
                            UrlRequest urlRequest, UrlResponseInfo urlResponseInfo) {
                        List<Header> headers = getHeaders(urlResponseInfo.getAllHeadersAsList());
                        HttpResponse response =
                                new HttpResponse(
                                        urlResponseInfo.getHttpStatusCode(),
                                        headers,
                                        bytesReceived.toByteArray());
                        callback.onSuccess(response);
                    }

                    @Override
                    public void onFailed(
                            UrlRequest urlRequest,
                            UrlResponseInfo urlResponseInfo,
                            CronetException e) {
                        callback.onError(e);
                    }
                };

        String url = request.getUrl();
        String rewritten = mUrlRewriter.rewriteUrl(url);
        if (rewritten == null) {
            callback.onError(new IOException("URL blocked by rewriter: " + url));
            return;
        }
        url = rewritten;

        // We can call allowDirectExecutor here and run directly on the network thread, since all
        // the callbacks are non-blocking.
        final UrlRequest.Builder builder =
                mCronetEngine
                        .newUrlRequestBuilder(url, urlCallback, getNonBlockingExecutor())
                        .allowDirectExecutor()
                        .disableCache()
                        .setPriority(getPriority(request))
                        .setTrafficStatsTag(request.getTrafficStatsTag());
        // request.getHeaders() may be blocking, so submit it to the blocking executor.
        getBlockingExecutor()
                .execute(
                        new SetUpRequestTask<>(request, url, builder, additionalHeaders, callback));
    }

    private class SetUpRequestTask<T> extends RequestTask<T> {
        UrlRequest.Builder builder;
        String url;
        Map<String, String> additionalHeaders;
        OnRequestComplete callback;
        Request<T> request;

        SetUpRequestTask(
                Request<T> request,
                String url,
                UrlRequest.Builder builder,
                Map<String, String> additionalHeaders,
                OnRequestComplete callback) {
            super(request);
            // Note that this URL may be different from Request#getUrl() due to the UrlRewriter.
            this.url = url;
            this.builder = builder;
            this.additionalHeaders = additionalHeaders;
            this.callback = callback;
            this.request = request;
        }

        @Override
        public void run() {
            try {
                mRequestListener.onRequestPrepared(request, builder);
                CurlLoggedRequestParameters requestParameters = new CurlLoggedRequestParameters();
                setHttpMethod(requestParameters, request);
                setRequestHeaders(requestParameters, request, additionalHeaders);
                requestParameters.applyToRequest(builder, getNonBlockingExecutor());
                UrlRequest urlRequest = builder.build();
                if (mCurlLoggingEnabled) {
                    mCurlCommandLogger.logCurlCommand(generateCurlCommand(url, requestParameters));
                }
                urlRequest.start();
            } catch (AuthFailureError authFailureError) {
                callback.onAuthError(authFailureError);
            }
        }
    }

    @VisibleForTesting
    public static List<Header> getHeaders(List<Map.Entry<String, String>> headersList) {
        List<Header> headers = new ArrayList<>();
        for (Map.Entry<String, String> header : headersList) {
            headers.add(new Header(header.getKey(), header.getValue()));
        }
        return headers;
    }

    /** Sets the connection parameters for the UrlRequest */
    private void setHttpMethod(CurlLoggedRequestParameters requestParameters, Request<?> request)
            throws AuthFailureError {
        switch (request.getMethod()) {
            case Request.Method.DEPRECATED_GET_OR_POST:
                // This is the deprecated way that needs to be handled for backwards compatibility.
                // If the request's post body is null, then the assumption is that the request is
                // GET.  Otherwise, it is assumed that the request is a POST.
                byte[] postBody = request.getPostBody();
                if (postBody != null) {
                    requestParameters.setHttpMethod("POST");
                    addBodyIfExists(requestParameters, request.getPostBodyContentType(), postBody);
                } else {
                    requestParameters.setHttpMethod("GET");
                }
                break;
            case Request.Method.GET:
                // Not necessary to set the request method because connection defaults to GET but
                // being explicit here.
                requestParameters.setHttpMethod("GET");
                break;
            case Request.Method.DELETE:
                requestParameters.setHttpMethod("DELETE");
                break;
            case Request.Method.POST:
                requestParameters.setHttpMethod("POST");
                addBodyIfExists(requestParameters, request.getBodyContentType(), request.getBody());
                break;
            case Request.Method.PUT:
                requestParameters.setHttpMethod("PUT");
                addBodyIfExists(requestParameters, request.getBodyContentType(), request.getBody());
                break;
            case Request.Method.HEAD:
                requestParameters.setHttpMethod("HEAD");
                break;
            case Request.Method.OPTIONS:
                requestParameters.setHttpMethod("OPTIONS");
                break;
            case Request.Method.TRACE:
                requestParameters.setHttpMethod("TRACE");
                break;
            case Request.Method.PATCH:
                requestParameters.setHttpMethod("PATCH");
                addBodyIfExists(requestParameters, request.getBodyContentType(), request.getBody());
                break;
            default:
                throw new IllegalStateException("Unknown method type.");
        }
    }

    /**
     * Sets the request headers for the UrlRequest.
     *
     * @param requestParameters parameters that we are adding the request headers to
     * @param request to get the headers from
     * @param additionalHeaders for the UrlRequest
     * @throws AuthFailureError is thrown if Request#getHeaders throws ones
     */
    private void setRequestHeaders(
            CurlLoggedRequestParameters requestParameters,
            Request<?> request,
            Map<String, String> additionalHeaders)
            throws AuthFailureError {
        requestParameters.putAllHeaders(additionalHeaders);
        // Request.getHeaders() takes precedence over the given additional (cache) headers).
        requestParameters.putAllHeaders(request.getHeaders());
    }

    /** Sets the UploadDataProvider of the UrlRequest.Builder */
    private void addBodyIfExists(
            CurlLoggedRequestParameters requestParameters,
            String contentType,
            @Nullable byte[] body) {
        requestParameters.setBody(contentType, body);
    }

    /** Helper method that maps Volley's request priority to Cronet's */
    private int getPriority(Request<?> request) {
        switch (request.getPriority()) {
            case LOW:
                return UrlRequest.Builder.REQUEST_PRIORITY_LOW;
            case HIGH:
            case IMMEDIATE:
                return UrlRequest.Builder.REQUEST_PRIORITY_HIGHEST;
            case NORMAL:
            default:
                return UrlRequest.Builder.REQUEST_PRIORITY_MEDIUM;
        }
    }

    private int getContentLength(UrlResponseInfo urlResponseInfo) {
        List<String> content = urlResponseInfo.getAllHeaders().get("Content-Length");
        if (content == null) {
            return 1024;
        } else {
            return Integer.parseInt(content.get(0));
        }
    }

    private String generateCurlCommand(String url, CurlLoggedRequestParameters requestParameters) {
        StringBuilder builder = new StringBuilder("curl ");

        // HTTP method
        builder.append("-X ").append(requestParameters.getHttpMethod()).append(" ");

        // Request headers
        for (Map.Entry<String, String> header : requestParameters.getHeaders().entrySet()) {
            builder.append("--header \"").append(header.getKey()).append(": ");
            if (!mLogAuthTokensInCurlCommands
                    && ("Authorization".equals(header.getKey())
                            || "Cookie".equals(header.getKey()))) {
                builder.append("[REDACTED]");
            } else {
                builder.append(header.getValue());
            }
            builder.append("\" ");
        }

        // URL
        builder.append("\"").append(url).append("\"");

        // Request body (if any)
        if (requestParameters.getBody() != null) {
            if (requestParameters.getBody().length >= 1024) {
                builder.append(" [REQUEST BODY TOO LARGE TO INCLUDE]");
            } else if (isBinaryContentForLogging(requestParameters)) {
                String base64 = Base64.encodeToString(requestParameters.getBody(), Base64.NO_WRAP);
                builder.insert(0, "echo '" + base64 + "' | base64 -d > /tmp/$$.bin; ")
                        .append(" --data-binary @/tmp/$$.bin");
            } else {
                // Just assume the request body is UTF-8 since this is for debugging.
                try {
                    builder.append(" --data-ascii \"")
                            .append(new String(requestParameters.getBody(), "UTF-8"))
                            .append("\"");
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException("Could not encode to UTF-8", e);
                }
            }
        }

        return builder.toString();
    }

    /** Rough heuristic to determine whether the request body is binary, for logging purposes. */
    private boolean isBinaryContentForLogging(CurlLoggedRequestParameters requestParameters) {
        // Check to see if the content is gzip compressed - this means it should be treated as
        // binary content regardless of the content type.
        String contentEncoding = requestParameters.getHeaders().get("Content-Encoding");
        if (contentEncoding != null) {
            String[] encodings = TextUtils.split(contentEncoding, ",");
            for (String encoding : encodings) {
                if ("gzip".equals(encoding.trim())) {
                    return true;
                }
            }
        }

        // If the content type is a known text type, treat it as text content.
        String contentType = requestParameters.getHeaders().get("Content-Type");
        if (contentType != null) {
            return !contentType.startsWith("text/")
                    && !contentType.startsWith("application/xml")
                    && !contentType.startsWith("application/json");
        }

        // Otherwise, assume it is binary content.
        return true;
    }

    /**
     * Builder is used to build an instance of {@link CronetHttpStack} from values configured by the
     * setters.
     */
    public static class Builder {
        private static final int DEFAULT_POOL_SIZE = 4096;
        private CronetEngine mCronetEngine;
        private final Context context;
        private ByteArrayPool mPool;
        private UrlRewriter mUrlRewriter;
        private RequestListener mRequestListener;
        private boolean mCurlLoggingEnabled;
        private CurlCommandLogger mCurlCommandLogger;
        private boolean mLogAuthTokensInCurlCommands;

        public Builder(Context context) {
            this.context = context;
        }

        /** Sets the CronetEngine to be used. Defaults to a vanialla CronetEngine. */
        public Builder setCronetEngine(CronetEngine engine) {
            mCronetEngine = engine;
            return this;
        }

        /** Sets the ByteArrayPool to be used. Defaults to a new pool with 4096 bytes. */
        public Builder setPool(ByteArrayPool pool) {
            mPool = pool;
            return this;
        }

        /** Sets the UrlRewriter to be used. Default is to return the original string. */
        public Builder setUrlRewriter(UrlRewriter urlRewriter) {
            mUrlRewriter = urlRewriter;
            return this;
        }

        /** Set the optional RequestListener to be used. */
        public Builder setRequestListener(RequestListener requestListener) {
            mRequestListener = requestListener;
            return this;
        }

        /**
         * Sets whether cURL logging should be enabled for debugging purposes.
         *
         * <p>When enabled, for each request dispatched to the network, a roughly-equivalent cURL
         * command will be logged to logcat.
         *
         * <p>The command may be missing some headers that are added by Cronet automatically, and
         * the full request body may not be included if it is too large. To inspect the full
         * requests and responses, see {@code CronetEngine#startNetLogToFile}.
         *
         * <p>WARNING: This is only intended for debugging purposes and should never be enabled on
         * production devices.
         *
         * @see #setCurlCommandLogger(CurlCommandLogger)
         * @see #setLogAuthTokensInCurlCommands(boolean)
         */
        public Builder setCurlLoggingEnabled(boolean curlLoggingEnabled) {
            mCurlLoggingEnabled = curlLoggingEnabled;
            return this;
        }

        /**
         * Sets the function used to log cURL commands.
         *
         * <p>Allows customization of the logging performed when cURL logging is enabled.
         *
         * <p>By default, when cURL logging is enabled, cURL commands are logged using {@link
         * VolleyLog#v}, e.g. at the verbose log level with the same log tag used by the rest of
         * Volley. This function may optionally be invoked to provide a custom logger.
         *
         * @see #setCurlLoggingEnabled(boolean)
         */
        public Builder setCurlCommandLogger(CurlCommandLogger curlCommandLogger) {
            mCurlCommandLogger = curlCommandLogger;
            return this;
        }

        /**
         * Sets whether to log known auth tokens in cURL commands, or redact them.
         *
         * <p>By default, headers which may contain auth tokens (e.g. Authorization or Cookie) will
         * have their values redacted. Passing true to this method will disable this redaction and
         * log the values of these headers.
         *
         * <p>This heuristic is not perfect; tokens that are logged in unknown headers, or in the
         * request body itself, will not be redacted as they cannot be detected generically.
         *
         * @see #setCurlLoggingEnabled(boolean)
         */
        public Builder setLogAuthTokensInCurlCommands(boolean logAuthTokensInCurlCommands) {
            mLogAuthTokensInCurlCommands = logAuthTokensInCurlCommands;
            return this;
        }

        public CronetHttpStack build() {
            if (mCronetEngine == null) {
                mCronetEngine = new CronetEngine.Builder(context).build();
            }
            if (mUrlRewriter == null) {
                mUrlRewriter =
                        new UrlRewriter() {
                            @Override
                            public String rewriteUrl(String originalUrl) {
                                return originalUrl;
                            }
                        };
            }
            if (mRequestListener == null) {
                mRequestListener = new RequestListener() {};
            }
            if (mPool == null) {
                mPool = new ByteArrayPool(DEFAULT_POOL_SIZE);
            }
            if (mCurlCommandLogger == null) {
                mCurlCommandLogger =
                        new CurlCommandLogger() {
                            @Override
                            public void logCurlCommand(String curlCommand) {
                                VolleyLog.v(curlCommand);
                            }
                        };
            }
            return new CronetHttpStack(
                    mCronetEngine,
                    mPool,
                    mUrlRewriter,
                    mRequestListener,
                    mCurlLoggingEnabled,
                    mCurlCommandLogger,
                    mLogAuthTokensInCurlCommands);
        }
    }

    /** Callback interface allowing clients to intercept different parts of the request flow. */
    public abstract static class RequestListener {
        private CronetHttpStack mStack;

        void initialize(CronetHttpStack stack) {
            mStack = stack;
        }

        /**
         * Called when a request is prepared and about to be sent over the network.
         *
         * <p>Clients may use this callback to customize UrlRequests before they are dispatched,
         * e.g. to enable socket tagging or request finished listeners.
         */
        public void onRequestPrepared(Request<?> request, UrlRequest.Builder requestBuilder) {}

        /** @see AsyncHttpStack#getNonBlockingExecutor() */
        protected Executor getNonBlockingExecutor() {
            return mStack.getNonBlockingExecutor();
        }

        /** @see AsyncHttpStack#getBlockingExecutor() */
        protected Executor getBlockingExecutor() {
            return mStack.getBlockingExecutor();
        }
    }

    /**
     * Interface for logging cURL commands for requests.
     *
     * @see Builder#setCurlCommandLogger(CurlCommandLogger)
     */
    public interface CurlCommandLogger {
        /** Log the given cURL command. */
        void logCurlCommand(String curlCommand);
    }

    /**
     * Internal container class for request parameters that impact logged cURL commands.
     *
     * <p>When cURL logging is enabled, an equivalent cURL command to a given request must be
     * generated and logged. However, the Cronet UrlRequest object is write-only. So, we write any
     * relevant parameters into this read-write container so they can be referenced when generating
     * the cURL command (if needed) and then merged into the UrlRequest.
     */
    private static class CurlLoggedRequestParameters {
        private final TreeMap<String, String> mHeaders =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        private String mHttpMethod;
        @Nullable private byte[] mBody;

        /**
         * Return the headers to be used for the request.
         *
         * <p>The returned map is case-insensitive.
         */
        TreeMap<String, String> getHeaders() {
            return mHeaders;
        }

        /** Apply all the headers in the given map to the request. */
        void putAllHeaders(Map<String, String> headers) {
            mHeaders.putAll(headers);
        }

        String getHttpMethod() {
            return mHttpMethod;
        }

        void setHttpMethod(String httpMethod) {
            mHttpMethod = httpMethod;
        }

        @Nullable
        byte[] getBody() {
            return mBody;
        }

        void setBody(String contentType, @Nullable byte[] body) {
            mBody = body;
            if (body != null && !mHeaders.containsKey(HttpHeaderParser.HEADER_CONTENT_TYPE)) {
                // Set the content-type unless it was already set (by Request#getHeaders).
                mHeaders.put(HttpHeaderParser.HEADER_CONTENT_TYPE, contentType);
            }
        }

        void applyToRequest(UrlRequest.Builder builder, ExecutorService nonBlockingExecutor) {
            for (Map.Entry<String, String> header : mHeaders.entrySet()) {
                builder.addHeader(header.getKey(), header.getValue());
            }
            builder.setHttpMethod(mHttpMethod);
            if (mBody != null) {
                UploadDataProvider dataProvider = UploadDataProviders.create(mBody);
                builder.setUploadDataProvider(dataProvider, nonBlockingExecutor);
            }
        }
    }
}

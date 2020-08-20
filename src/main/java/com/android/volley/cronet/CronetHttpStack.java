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
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.android.volley.AuthFailureError;
import com.android.volley.Header;
import com.android.volley.Request;
import com.android.volley.RequestTask;
import com.android.volley.toolbox.AsyncHttpStack;
import com.android.volley.toolbox.ByteArrayPool;
import com.android.volley.toolbox.HttpResponse;
import com.android.volley.toolbox.PoolingByteArrayOutputStream;
import com.android.volley.toolbox.UrlRewriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.UploadDataProvider;
import org.chromium.net.UploadDataProviders;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlRequest.Callback;
import org.chromium.net.UrlResponseInfo;

/**
 * A {@link AsyncHttpStack} that's based on Cronet's fully asynchronous API for network requests.
 */
public class CronetHttpStack extends AsyncHttpStack {

    private final CronetEngine mCronetEngine;
    private final ByteArrayPool mPool;
    private final UrlRewriter mUrlRewriter;

    private CronetHttpStack(
            CronetEngine cronetEngine, ByteArrayPool pool, UrlRewriter urlRewriter) {
        mCronetEngine = cronetEngine;
        mPool = pool;
        mUrlRewriter = urlRewriter;
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
                        .setPriority(getPriority(request));
        // request.getHeaders() may be blocking, so submit it to the blocking executor.
        getBlockingExecutor()
                .execute(new SetUpRequestTask<>(request, builder, additionalHeaders, callback));
    }

    private class SetUpRequestTask<T> extends RequestTask<T> {
        UrlRequest.Builder builder;
        Map<String, String> additionalHeaders;
        OnRequestComplete callback;
        Request<T> request;

        SetUpRequestTask(
                Request<T> request,
                UrlRequest.Builder builder,
                Map<String, String> additionalHeaders,
                OnRequestComplete callback) {
            super(request);
            this.builder = builder;
            this.additionalHeaders = additionalHeaders;
            this.callback = callback;
            this.request = request;
        }

        @Override
        public void run() {
            try {
                setHttpMethod(request, builder);
                setRequestHeaders(request, additionalHeaders, builder);
                UrlRequest urlRequest = builder.build();
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
    private void setHttpMethod(Request<?> request, UrlRequest.Builder builder)
            throws AuthFailureError {
        switch (request.getMethod()) {
            case Request.Method.DEPRECATED_GET_OR_POST:
                // This is the deprecated way that needs to be handled for backwards compatibility.
                // If the request's post body is null, then the assumption is that the request is
                // GET.  Otherwise, it is assumed that the request is a POST.
                byte[] postBody = request.getPostBody();
                if (postBody != null) {
                    builder.setHttpMethod("POST");
                    addBodyIfExists(postBody, builder);
                } else {
                    builder.setHttpMethod("GET");
                }
                break;
            case Request.Method.GET:
                // Not necessary to set the request method because connection defaults to GET but
                // being explicit here.
                builder.setHttpMethod("GET");
                break;
            case Request.Method.DELETE:
                builder.setHttpMethod("DELETE");
                break;
            case Request.Method.POST:
                builder.setHttpMethod("POST");
                addBodyIfExists(request.getBody(), builder);
                break;
            case Request.Method.PUT:
                builder.setHttpMethod("PUT");
                addBodyIfExists(request.getBody(), builder);
                break;
            case Request.Method.HEAD:
                builder.setHttpMethod("HEAD");
                break;
            case Request.Method.OPTIONS:
                builder.setHttpMethod("OPTIONS");
                break;
            case Request.Method.TRACE:
                builder.setHttpMethod("TRACE");
                break;
            case Request.Method.PATCH:
                builder.setHttpMethod("PATCH");
                addBodyIfExists(request.getBody(), builder);
                break;
            default:
                throw new IllegalStateException("Unknown method type.");
        }
    }

    /**
     * Sets the request headers for the UrlRequest.
     *
     * @param request to get the headers from
     * @param additionalHeaders for the UrlRequest
     * @param builder that we are adding the request headers to
     * @throws AuthFailureError is thrown if Request#getHeaders throws ones
     */
    private void setRequestHeaders(
            Request<?> request, Map<String, String> additionalHeaders, UrlRequest.Builder builder)
            throws AuthFailureError {
        HashMap<String, String> map = new HashMap<>();
        map.putAll(additionalHeaders);
        // Request.getHeaders() takes precedence over the given additional (cache) headers).
        map.putAll(request.getHeaders());
        for (Map.Entry<String, String> header : map.entrySet()) {
            builder.addHeader(header.getKey(), header.getValue());
        }
    }

    /** Sets the UploadDataProvider of the UrlRequest.Builder */
    private void addBodyIfExists(@Nullable byte[] body, UrlRequest.Builder builder) {
        if (body != null) {
            UploadDataProvider dataProvider = UploadDataProviders.create(body);
            builder.setUploadDataProvider(dataProvider, getNonBlockingExecutor());
        }
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

        public Builder(Context context) {
            this.context = context;
            mCronetEngine = null;
            mPool = null;
            mUrlRewriter = null;
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
            if (mPool == null) {
                mPool = new ByteArrayPool(DEFAULT_POOL_SIZE);
            }
            return new CronetHttpStack(mCronetEngine, mPool, mUrlRewriter);
        }
    }
}

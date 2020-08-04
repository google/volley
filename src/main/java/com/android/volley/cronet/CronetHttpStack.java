package com.android.volley.cronet;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.android.volley.AuthFailureError;
import com.android.volley.Header;
import com.android.volley.Request;
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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.UploadDataProvider;
import org.chromium.net.UploadDataProviders;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlRequest.Callback;
import org.chromium.net.UrlResponseInfo;

public class CronetHttpStack extends AsyncHttpStack {

    private static final int DEFAULT_POOL_SIZE = 4096;
    private final CronetEngine mCronetEngine;
    private final Executor mCallbackExecutor;
    private final ExecutorService mBlockingExecutor;
    private final ByteArrayPool mPool;
    private final UrlRewriter mUrlRewriter;

    public CronetHttpStack(
            Context context,
            Executor callbackExecutor,
            ExecutorService blockingExecutor,
            ByteArrayPool pool,
            UrlRewriter rewriter) {
        mCronetEngine = new CronetEngine.Builder(context).build();
        mCallbackExecutor = callbackExecutor;
        mBlockingExecutor = blockingExecutor;
        mPool = pool;
        mUrlRewriter = rewriter;
    }

    public CronetHttpStack(
            Context context,
            Executor callbackExecutor,
            ExecutorService blockingExecutor,
            ByteArrayPool pool) {
        this(
                context,
                callbackExecutor,
                blockingExecutor,
                pool,
                new UrlRewriter() {
                    @Override
                    public String rewriteUrl(String originalUrl) {
                        return originalUrl;
                    }
                });
    }

    public CronetHttpStack(
            Context context, ExecutorService blockingExecutor, Executor callbackExecutor) {
        this(context, callbackExecutor, blockingExecutor, new ByteArrayPool(DEFAULT_POOL_SIZE));
    }

    public CronetHttpStack(
            Context context,
            Executor callbackExecutor,
            ExecutorService blockingExecutor,
            UrlRewriter rewriter) {
        this(
                context,
                callbackExecutor,
                blockingExecutor,
                new ByteArrayPool(DEFAULT_POOL_SIZE),
                rewriter);
    }

    @Override
    public void executeRequest(
            final Request<?> request,
            final Map<String, String> additionalHeaders,
            final OnRequestComplete callback) {
        final PoolingByteArrayOutputStream bytesReceived = new PoolingByteArrayOutputStream(mPool);
        final WritableByteChannel receiveChannel = Channels.newChannel(bytesReceived);
        final Callback urlCallback =
                new Callback() {
                    @Override
                    public void onRedirectReceived(
                            UrlRequest urlRequest, UrlResponseInfo urlResponseInfo, String s) {
                        urlRequest.followRedirect();
                    }

                    @Override
                    public void onResponseStarted(
                            UrlRequest urlRequest, UrlResponseInfo urlResponseInfo) {
                        int size = getContentLength(urlResponseInfo);
                        urlRequest.read(ByteBuffer.allocateDirect(size));
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
        }
        url = rewritten;

        // We can call allowDirectExecutor here and run directly on the network thread, since all
        // the callbacks are non-blocking.
        final UrlRequest.Builder builder =
                mCronetEngine
                        .newUrlRequestBuilder(url, urlCallback, mCallbackExecutor)
                        .allowDirectExecutor()
                        .disableCache();
        setPriority(request, builder);
        // This code may be blocking, so submit it to the blocking executor.
        Future<?> future =
                mBlockingExecutor.submit(
                        new Runnable() {
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
                        });
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
            builder.setUploadDataProvider(dataProvider, mCallbackExecutor);
        }
    }

    /** Sets the priority of this request. */
    private void setPriority(Request<?> request, UrlRequest.Builder builder) {
        builder.setPriority(request.getPriority().ordinal());
    }

    private int getContentLength(UrlResponseInfo urlResponseInfo) {
        List<String> content = urlResponseInfo.getAllHeaders().get("content-length");
        if (content == null) {
            return 1024;
        } else {
            return Integer.parseInt(content.get(0));
        }
    }
}

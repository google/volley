package com.android.volley.toolbox;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.VolleyLog;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Asynchronous extension of the {@link BaseHttpStack} class. */
public abstract class AsyncHttpStack extends BaseHttpStack {

    public interface OnRequestComplete {
        /** Invoked when the stack successfully completes a request. */
        void onSuccess(HttpResponse httpResponse);

        /** Invoked when the stack throws an {@link AuthFailureError} during a request. */
        void onAuthError(AuthFailureError authFailureError);

        /** Invoked when the stack throws an {@link IOException} during a request. */
        void onError(IOException ioException);
    }

    /**
     * Makes an HTTP request with the given parameters, and calls the {@link OnRequestComplete}
     * callback, with either the {@link HttpResponse} or error that was thrown.
     *
     * @param request to perform
     * @param additionalHeaders to be sent together with {@link Request#getHeaders()}
     * @param callback to be called after retrieving the {@link HttpResponse} or throwing an error.
     */
    public abstract void executeRequest(
            Request<?> request, Map<String, String> additionalHeaders, OnRequestComplete callback);

    /**
     * Performs an HTTP request with the given parameters.
     *
     * @param request the request to perform
     * @param additionalHeaders additional headers to be sent together with {@link
     *     Request#getHeaders()}
     * @return the {@link HttpResponse}
     * @throws IOException if an I/O error occurs during the request
     * @throws AuthFailureError if an authentication failure occurs during the request
     */
    @Override
    public final HttpResponse executeRequest(
            Request<?> request, Map<String, String> additionalHeaders)
            throws IOException, AuthFailureError {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Object> entry = new AtomicReference<>();
        executeRequest(
                request,
                additionalHeaders,
                new OnRequestComplete() {
                    @Override
                    public void onSuccess(HttpResponse httpResponse) {
                        entry.set(httpResponse);
                        latch.countDown();
                    }

                    @Override
                    public void onAuthError(AuthFailureError authFailureError) {
                        entry.set(authFailureError);
                        latch.countDown();
                    }

                    @Override
                    public void onError(IOException ioException) {
                        entry.set(ioException);
                        latch.countDown();
                    }
                });
        try {
            latch.await();
        } catch (InterruptedException e) {
            VolleyLog.e(e, "while waiting for CountDownLatch");
            Thread.currentThread().interrupt();
            return null;
        }
        Object response = entry.get();
        if (response instanceof HttpResponse) {
            return (HttpResponse) response;
        } else if (response instanceof IOException) {
            throw (IOException) response;
        } else if (response instanceof AuthFailureError) {
            throw (AuthFailureError) response;
        } else {
            VolleyLog.d("Response was in an unexpected form");
            return null;
        }
    }
}

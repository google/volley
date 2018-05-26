package com.android.volley.mock;

import android.annotation.SuppressLint;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.NoCache;
import com.android.volley.toolbox.RequestBuilder;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.utils.ImmediateResponseDelivery;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

public class StubbedRequestQueue extends RequestQueue {

    public static <T> T getResultWithMockedQueue(RequestBuilder<T> builder, String mockResponse)
            throws Exception {
        RequestFuture<T> future = RequestFuture.newFuture();
        Request<T> request = builder.onSuccess(future).onError(future).build();

        return getResultWithMockedQueue(request, future, mockResponse);
    }

    public static <T> T getResultWithMockedQueue(
            Request<T> request, RequestFuture<T> future, String mockResponse) throws Exception {
        StubbedRequestQueue queue = new StubbedRequestQueue(mockResponse);
        queue.start();
        queue.add(request);
        return future.get(3, TimeUnit.SECONDS);
    }

    @SuppressLint("NewApi")
    public static byte[] stringBytes(String s) {
        return s.getBytes(Charset.forName("UTF-8"));
    }

    public StubbedRequestQueue(byte[] response) {
        super(new NoCache(), new MockNetwork(response), 1, new ImmediateResponseDelivery());
    }

    public StubbedRequestQueue(String response) {
        this(stringBytes(response));
    }
}

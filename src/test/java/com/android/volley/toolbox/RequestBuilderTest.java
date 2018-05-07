package com.android.volley.toolbox;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.android.volley.utils.ImmediateResponseDelivery;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class RequestBuilderTest {

    public static final String URL = "http://www.example.com/";

    @Test
    public void setsAndAppendsUrl() {
        String append = "subdomain/";

        Request<Void> request = RequestBuilder.<Void>create()
                .url(URL)
                .appendUrl(append)
                .onError(new StubErrorListener())
                .build();

        assertEquals(URL + append, request.getUrl());
    }

    @Test(expected = NullPointerException.class)
    public void nullUrlIsThrownOnBuild() {
        RequestBuilder.<Void>create()
                .onSuccess(new StubListener<Void>())
                .onError(new StubErrorListener())
                .build();
    }

    @Test(expected = NullPointerException.class)
    public void nullUrlIsThrownWhenPassedIn() {
        RequestBuilder.create().url(null);
    }

    @Test(expected = NullPointerException.class)
    public void nullListenerIsThrownWhenPassedIn() {
        RequestBuilder.create().onSuccess(null);
    }

    @Test(expected = NullPointerException.class)
    public void nullErrorListenerIsThrownWhenPassedIn() {
        RequestBuilder.create().onError(null);
    }

    @Test
    public void listenerIsCalledOnSuccess() throws ExecutionException, InterruptedException {
        final AtomicBoolean hasFinished = new AtomicBoolean();
        Listener<String> listener = spy(new Listener<String>() {
            @Override
            public void onResponse(String response) {
                hasFinished.set(true);
            }
        });

        Request<String> request = RequestBuilder.<String>create()
                .url(URL)
                .onSuccess(listener)
                .onError(new StubErrorListener())
                .build();

        MockedRequestQueue queue = new MockedRequestQueue();
        queue.start();
        queue.add(request);

        long start = System.currentTimeMillis();
        while (!hasFinished.get()) {
            long end = System.currentTimeMillis();
            if (end - start > 3000) {
                throw new IllegalStateException("Timeout");
            }
            Thread.sleep(10);
        }

        verify(listener, times(1)).onResponse(anyString());
    }

    private static class MockedRequestQueue extends RequestQueue {
        public MockedRequestQueue() {
            super(mock(Cache.class), mock(Network.class), 1, new ImmediateResponseDelivery());
        }
    }

    private static class StubListener<T> implements Listener<T> {
        @Override
        public void onResponse(T response) {
            // Stub
        }
    }

    private static class StubErrorListener implements Response.ErrorListener {
        @Override
        public void onErrorResponse(VolleyError error) {
            // Stub
        }
    }
}

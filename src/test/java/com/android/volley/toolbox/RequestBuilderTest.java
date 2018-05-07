package com.android.volley.toolbox;

import android.annotation.SuppressLint;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.android.volley.mock.MockNetwork;
import com.android.volley.utils.ImmediateResponseDelivery;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
    public void listenerAndParserIsCalledOnSuccess() throws Exception {
        RequestFuture<String> future = RequestFuture.newFuture();
        ResponseParser<String> parser = spy(ResponseParsers.forString());

        Request<String> request = RequestBuilder.<String>create()
                .url(URL)
                .onSuccess(future)
                .onError(future)
                .parseResponse(parser)
                .build();

        String expectedResponse = "Response";
        MockedRequestQueue queue = new MockedRequestQueue(expectedResponse);
        queue.start();
        queue.add(request);

        // Times out if listener (future) does not get called
        String response = future.get(3, TimeUnit.SECONDS);
        assertEquals(expectedResponse, response);

        verify(parser, times(1)).parseNetworkResponse((NetworkResponse) any());
    }

    private static class MockedRequestQueue extends RequestQueue {

        @SuppressLint("NewApi")
        public MockedRequestQueue(byte[] response) {
            super(
                    new NoCache(),
                    new MockNetwork(response),
                    1,
                    new ImmediateResponseDelivery()
            );
        }

        @SuppressLint("NewApi")
        public MockedRequestQueue(String response) {
            this(response.getBytes(Charset.forName("UTF-8")));
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

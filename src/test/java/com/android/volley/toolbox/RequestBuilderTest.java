package com.android.volley.toolbox;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.Response.Listener;
import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;
import com.android.volley.mock.MockNetwork;
import com.android.volley.utils.ImmediateResponseDelivery;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.android.volley.utils.Utils.stringBytes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class RequestBuilderTest {

    public static final String URL = "http://www.example.com/";

    @Test
    public void baseBuilderIsValid() {
        baseValidBuilder().build(); // test fails by exception if invalid
    }

    @Test(expected = IllegalStateException.class)
    public void cannotBuildTwice() {
        RequestBuilder<?, ?> builder = baseValidBuilder();
        builder.build();
        builder.build();
    }

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

    @Test
    public void methodDefaultsToGet() {
        int actual = baseValidBuilder()
                .build()
                .getMethod();
        assertEquals(Request.Method.GET, actual);
    }

    @Test
    public void methodIsSet() {
        int expected = Request.Method.DELETE;
        int actual = baseValidBuilder()
                .method(expected)
                .build()
                .getMethod();
        assertEquals(expected, actual);
    }

    /**
     * Uses == for equality (see {@link RequestQueue#cancelAll(Object)}
     */
    @Test
    public void tagIsSet() {
        Object expected = new Object();
        Object actual = baseValidBuilder()
                .tag(expected)
                .build()
                .getTag();
        assertTrue(expected == actual);
    }

    @Test
    public void retryPolicyIsSet() {
        RetryPolicy expected = new DefaultRetryPolicy();
        RetryPolicy actual = baseValidBuilder()
                .retryPolicy(expected)
                .build()
                .getRetryPolicy();
        assertEquals(expected, actual);
    }

    @Test
    public void retryOnServerErrorsIsSet() {
        boolean expected = true;
        boolean actual = baseValidBuilder()
                .retryOnServerErrors(expected)
                .build()
                .shouldRetryServerErrors();
        assertEquals(expected, actual);
    }

    @Test
    public void shouldCacheIsSet() {
        boolean expected = false;
        boolean actual = baseValidBuilder()
                .shouldCache(expected)
                .build()
                .shouldCache();
        assertEquals(expected, actual);
    }

    @Test
    public void priorityIsSet() {
        Request.Priority expected = Request.Priority.HIGH;
        Request.Priority actual = baseValidBuilder()
                .priority(expected)
                .build()
                .getPriority();
        assertEquals(expected, actual);
    }

    @Test
    public void headersAreSetAndOverridable() throws AuthFailureError {
        String key1 = "Key1";
        String key2 = "Key2";
        String val1 = "Val1";
        Map<String, String> subMap = new HashMap<>();
        subMap.put(key2, "Val2-new");
        subMap.put("Key-3", "Val-3");
        Map<String, String> expected = new HashMap<>(subMap);
        expected.put(key1, val1);

        Map<String, String> actual = baseValidBuilder()
                .header(key1, val1)
                .header(key2, "Val-2-replaced_later")
                .headers(subMap)
                .build()
                .getHeaders();

        assertEquals(expected, actual);
    }

    @Test(expected = NullPointerException.class)
    public void nullHeaderKeyIsThrown() {
        baseValidBuilder().header(null, "val");
    }

    @Test(expected = NullPointerException.class)
    public void nullHeaderValueIsThrown() {
        baseValidBuilder().header("key", null);
    }

    private <T> RequestBuilder<T, ? extends RequestBuilder> baseValidBuilder() {
        return RequestBuilder.<T>create()
                .url(URL)
                .onError(new StubErrorListener());
    }

    private static class MockedRequestQueue extends RequestQueue {

        public MockedRequestQueue(byte[] response) {
            super(
                    new NoCache(),
                    new MockNetwork(response),
                    1,
                    new ImmediateResponseDelivery()
            );
        }

        public MockedRequestQueue(String response) {
            this(stringBytes(response));
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

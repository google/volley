package com.android.volley.toolbox;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.widget.ImageView.ScaleType;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Request.Priority;
import com.android.volley.RequestQueue;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;
import com.android.volley.mock.MockNetwork;
import com.android.volley.utils.ImmediateResponseDelivery;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import static com.android.volley.utils.TestUtils.stringBytes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class RequestBuilderTest {

    public static final String URL = "http://www.example.com/";
    public static final int FUTURE_WAIT_TIME = 3;

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

    @Test(expected = IllegalStateException.class)
    public void cannotConfigureAfterBuilt() {
        RequestBuilder<?, ?> builder = baseValidBuilder();
        builder.build();
        builder.url(URL);
    }

    @Test
    public void setsAndAppendsUrl() {
        String append = "subdomain/";

        Request<Void> request =
                RequestBuilder.<Void>startNew()
                        .url(URL)
                        .appendUrl(append)
                        .onError(new StubErrorListener())
                        .build();

        assertEquals(URL + append, request.getUrl());
    }

    @Test(expected = NullPointerException.class)
    public void nullUrlIsThrownOnBuild() {
        RequestBuilder.<Void>startNew()
                .onSuccess(new StubListener<Void>())
                .onError(new StubErrorListener())
                .build();
    }

    @Test(expected = NullPointerException.class)
    public void nullUrlIsThrownWhenPassedIn() {
        RequestBuilder.startNew().url(null);
    }

    @Test(expected = NullPointerException.class)
    public void nullListenerIsThrownWhenPassedIn() {
        RequestBuilder.startNew().onSuccess(null);
    }

    @Test(expected = NullPointerException.class)
    public void nullErrorListenerIsThrownWhenPassedIn() {
        RequestBuilder.startNew().onError(null);
    }

    @Test(expected = NoSuchElementException.class)
    public void buildFailsWithoutErrorListener() {
        RequestBuilder.startNew().url(URL).build();
    }

    @Test
    public void listenersAndParsersAreCalledOnSuccess() throws Exception {
        @SuppressWarnings("unchecked") Listener<String> extraListener = mock(Listener.class);
        ErrorListener extraErrorListener = mock(ErrorListener.class);
        ResponseParser<String> parser = spy(ResponseParsers.forString());

        String expectedResponse = "Response";

        String response = getResultWithMockedQueue(
                RequestBuilder.<String>startNew()
                        .url(URL)
                        .onSuccess(extraListener)
                        .onError(extraErrorListener)
                        .parseResponse(parser),
                expectedResponse
        );

        assertEquals(expectedResponse, response);

        verify(parser).parseNetworkResponse((NetworkResponse) any());
        verify(extraListener).onResponse(response);
        verify(extraErrorListener, never()).onErrorResponse((VolleyError) any());
    }

    @Test
    public void nullIsGivenToTheListenerIfNoResponse() throws Exception {
        String valueTheListenerReceived = getResultWithMockedQueue(
                RequestBuilder.<String>startNew().url(URL),
                "Response"
        );
        assertEquals(null, valueTheListenerReceived);
    }

    @Test
    public void jsonObjectIsGivenToTheListener() throws Exception {
        JSONObject expected =
                new JSONObject().put("first-key", "first-value").put("second key", 3);

        JSONObject valueTheListenerReceived = getResultWithMockedQueue(
                RequestBuilder.<JSONObject>startNew()
                        .url(URL)
                        .parseResponse(ResponseParsers.forJSONObject()),
                expected.toString()
        );
        assertEquals(expected.toString(), valueTheListenerReceived.toString());
    }

    @Test
    public void methodDefaultsToGet() {
        int actual = baseValidBuilder().build().getMethod();
        assertEquals(Request.Method.GET, actual);
    }

    @Test
    public void methodIsSet() {
        int expected = Request.Method.DELETE;
        int actual = baseValidBuilder().method(expected).build().getMethod();
        assertEquals(expected, actual);
    }

    /** Uses == for equality (see {@link RequestQueue#cancelAll(Object)} */
    @Test
    public void tagIsSet() {
        Object expected = new Object();
        Object actual = baseValidBuilder().tag(expected).build().getTag();
        assertTrue(expected == actual);
    }

    @Test
    public void retryPolicyIsSet() {
        RetryPolicy expected = new DefaultRetryPolicy();
        RetryPolicy actual = baseValidBuilder().retryPolicy(expected).build().getRetryPolicy();
        assertEquals(expected, actual);
    }

    @Test
    public void retryOnServerErrorsIsSet() {
        boolean expected = true;
        boolean actual =
                baseValidBuilder().retryOnServerErrors(expected).build().shouldRetryServerErrors();
        assertEquals(expected, actual);
    }

    @Test
    public void shouldCacheIsSet() {
        boolean expected = false;
        boolean actual = baseValidBuilder().shouldCache(expected).build().shouldCache();
        assertEquals(expected, actual);
    }

    @Test
    public void priorityIsSet() {
        Priority expected = Priority.HIGH;
        Priority actual = baseValidBuilder().priority(expected).build().getPriority();
        assertEquals(expected, actual);
    }

    @Test
    public void requestForImageHasLowPriority() {
        Request<Bitmap> request =
                this.<Bitmap>baseValidBuilder()
                        .parseResponse(
                                ResponseParsers.forImage(Config.ALPHA_8, 1, 1, ScaleType.CENTER))
                        .build();
        assertEquals(ImageRequest.DEFAULT_IMAGE_PRIORITY, request.getPriority());
    }

    @Test
    public void requestForImageWithDoesNotOverridePriority() {
        Priority expected = Priority.HIGH;

        assertNotEquals(ImageRequest.DEFAULT_IMAGE_PRIORITY, expected); // sanity check for test

        Request<Bitmap> request =
                this.<Bitmap>baseValidBuilder()
                        .priority(expected)
                        .parseResponse(
                                ResponseParsers.forImage(Config.ALPHA_8, 1, 1, ScaleType.CENTER))
                        .build();

        assertEquals(expected, request.getPriority());
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

        Map<String, String> actual =
                baseValidBuilder()
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

    @Test
    public void rangeAddsToHeaders() throws AuthFailureError {
        Map<String, String> expected = new HashMap<>();
        expected.put("Range", "type=0-100");

        Map<String, String> actual = baseValidBuilder().range("type", 0, 100).build().getHeaders();

        assertEquals(expected, actual);
    }

    @Test
    public void rangeForPageAddsToHeaders() throws AuthFailureError {
        Map<String, String> expected = new HashMap<>();
        expected.put("Range", "type=0-99");

        Map<String, String> actual =
                baseValidBuilder().rangeForPage("type", 0, 100).build().getHeaders();

        assertEquals(expected, actual);
    }

    @Test(expected = NullPointerException.class)
    public void nullParamKeyIsThrown() {
        baseValidBuilder().param(null, "val");
    }

    @Test(expected = NullPointerException.class)
    public void nullParamValueIsThrown() {
        baseValidBuilder().param("key", null);
    }

    @Test
    public void paramsAreSetAndOverridable() throws AuthFailureError {
        String key1 = "Key1";
        String key2 = "Key2";
        String val1 = "Val1";
        Map<String, String> subMap = new HashMap<>();
        subMap.put(key2, "Val2-new");
        subMap.put("Key-3", "Val-3");
        Map<String, String> expected = new HashMap<>(subMap);
        expected.put(key1, val1);

        Map<String, String> actual =
                ((BuildableRequest<Object>)
                                baseValidBuilder()
                                        .param(key1, val1)
                                        .param(key2, "Val-2-replaced_later")
                                        .params(subMap)
                                        .build())
                        .getParams();

        assertEquals(expected, actual);
    }

    @Test
    public void paramsAreSetInUrl() {
        String expected = URL + "?key1=value1&key2=value2";

        String actual =
                baseValidBuilder().param("key1", "value1").param("key2", "value2").build().getUrl();

        assertEquals(expected, actual);
    }

    @Test
    public void bodyDefaultsAreCorrect() throws AuthFailureError {
        Request<Object> request = baseValidBuilder().build();

        assertEquals(null, request.getBody());
        assertEquals(Bodies.DEFAULT_CONTENT_TYPE, request.getBodyContentType());
    }

    @Test
    public void bodyIsSet() throws AuthFailureError, JSONException {
        JSONObject jsonObject =
                new JSONObject().put("first-key", "first-value").put("second key", 3);

        Request<Object> request = baseValidBuilder().body(Bodies.forJSONObject(jsonObject)).build();

        assertTrue(request.getBody().length > 0);
        assertEquals(JsonRequest.PROTOCOL_CONTENT_TYPE, request.getBodyContentType());
    }

    private <T> RequestBuilder<T, ? extends RequestBuilder> baseValidBuilder() {
        return RequestBuilder.<T>startNew().url(URL).onError(new StubErrorListener());
    }

    private <T> T getResultWithMockedQueue(
            RequestBuilder<T, ?> builder, String mockResponse) throws Exception {
        RequestFuture<T> future = RequestFuture.newFuture();

        Request<T> request = builder.onSuccess(future).onError(future).build();

        MockedRequestQueue queue = new MockedRequestQueue(mockResponse);
        queue.start();
        queue.add(request);
        return future.get(3, TimeUnit.SECONDS);
    }

    private static class MockedRequestQueue extends RequestQueue {

        public MockedRequestQueue(byte[] response) {
            super(new NoCache(), new MockNetwork(response), 1, new ImmediateResponseDelivery());
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

    private static class StubErrorListener implements ErrorListener {
        @Override
        public void onErrorResponse(VolleyError error) {
            // Stub
        }
    }
}

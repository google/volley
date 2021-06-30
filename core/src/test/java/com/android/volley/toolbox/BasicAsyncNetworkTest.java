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

package com.android.volley.toolbox;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.volley.AsyncNetwork;
import com.android.volley.AuthFailureError;
import com.android.volley.Cache.Entry;
import com.android.volley.Header;
import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.RetryPolicy;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.mock.MockAsyncStack;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BasicAsyncNetworkTest {

    @Mock private RetryPolicy mMockRetryPolicy;
    @Mock private AsyncNetwork.OnRequestComplete mockCallback;
    private ExecutorService executor = MoreExecutors.newDirectExecutorService();

    @Before
    public void setUp() throws Exception {
        initMocks(this);
    }

    @Test
    public void headersAndPostParams() throws Exception {
        MockAsyncStack mockAsyncStack = new MockAsyncStack();
        HttpResponse fakeResponse =
                new HttpResponse(
                        200,
                        Collections.<Header>emptyList(),
                        "foobar".getBytes(StandardCharsets.UTF_8));
        mockAsyncStack.setResponseToReturn(fakeResponse);
        BasicAsyncNetwork httpNetwork = new BasicAsyncNetwork.Builder(mockAsyncStack).build();
        httpNetwork.setBlockingExecutor(executor);
        Request<String> request = buildRequest();
        Entry entry = new Entry();
        entry.etag = "foobar";
        entry.lastModified = 1503102002000L;
        request.setCacheEntry(entry);
        perform(request, httpNetwork).get();
        assertEquals("foo", mockAsyncStack.getLastHeaders().get("requestheader"));
        assertEquals("foobar", mockAsyncStack.getLastHeaders().get("If-None-Match"));
        assertEquals(
                "Sat, 19 Aug 2017 00:20:02 GMT",
                mockAsyncStack.getLastHeaders().get("If-Modified-Since"));
        assertEquals(
                "requestpost=foo&",
                new String(mockAsyncStack.getLastPostBody(), StandardCharsets.UTF_8));
    }

    @Test
    public void headersAndPostParamsStream() throws Exception {
        MockAsyncStack mockAsyncStack = new MockAsyncStack();
        ByteArrayInputStream stream = new ByteArrayInputStream("foobar".getBytes("UTF-8"));
        HttpResponse fakeResponse =
                new HttpResponse(200, Collections.<Header>emptyList(), 6, stream);
        mockAsyncStack.setResponseToReturn(fakeResponse);
        BasicAsyncNetwork httpNetwork = new BasicAsyncNetwork.Builder(mockAsyncStack).build();
        httpNetwork.setBlockingExecutor(executor);
        Request<String> request = buildRequest();
        Entry entry = new Entry();
        entry.etag = "foobar";
        entry.lastModified = 1503102002000L;
        request.setCacheEntry(entry);
        perform(request, httpNetwork).get();
        assertEquals("foo", mockAsyncStack.getLastHeaders().get("requestheader"));
        assertEquals("foobar", mockAsyncStack.getLastHeaders().get("If-None-Match"));
        assertEquals(
                "Sat, 19 Aug 2017 00:20:02 GMT",
                mockAsyncStack.getLastHeaders().get("If-Modified-Since"));
        assertEquals(
                "requestpost=foo&",
                new String(mockAsyncStack.getLastPostBody(), StandardCharsets.UTF_8));
    }

    @Test
    public void notModified() throws Exception {
        MockAsyncStack mockAsyncStack = new MockAsyncStack();
        List<Header> headers = new ArrayList<>();
        headers.add(new Header("ServerKeyA", "ServerValueA"));
        headers.add(new Header("ServerKeyB", "ServerValueB"));
        headers.add(new Header("SharedKey", "ServerValueShared"));
        headers.add(new Header("sharedcaseinsensitivekey", "ServerValueShared1"));
        headers.add(new Header("SharedCaseInsensitiveKey", "ServerValueShared2"));
        HttpResponse fakeResponse = new HttpResponse(HttpURLConnection.HTTP_NOT_MODIFIED, headers);
        mockAsyncStack.setResponseToReturn(fakeResponse);
        BasicAsyncNetwork httpNetwork = new BasicAsyncNetwork.Builder(mockAsyncStack).build();
        httpNetwork.setBlockingExecutor(executor);
        Request<String> request = buildRequest();
        Entry entry = new Entry();
        entry.allResponseHeaders = new ArrayList<>();
        entry.allResponseHeaders.add(new Header("CachedKeyA", "CachedValueA"));
        entry.allResponseHeaders.add(new Header("CachedKeyB", "CachedValueB"));
        entry.allResponseHeaders.add(new Header("SharedKey", "CachedValueShared"));
        entry.allResponseHeaders.add(new Header("SHAREDCASEINSENSITIVEKEY", "CachedValueShared1"));
        entry.allResponseHeaders.add(new Header("shAREDcaSEinSENSITIVEkeY", "CachedValueShared2"));
        request.setCacheEntry(entry);
        httpNetwork.performRequest(request, mockCallback);
        NetworkResponse response = perform(request, httpNetwork).get();
        List<Header> expectedHeaders = new ArrayList<>();
        // Should have all server headers + cache headers that didn't show up in server response.
        expectedHeaders.add(new Header("ServerKeyA", "ServerValueA"));
        expectedHeaders.add(new Header("ServerKeyB", "ServerValueB"));
        expectedHeaders.add(new Header("SharedKey", "ServerValueShared"));
        expectedHeaders.add(new Header("sharedcaseinsensitivekey", "ServerValueShared1"));
        expectedHeaders.add(new Header("SharedCaseInsensitiveKey", "ServerValueShared2"));
        expectedHeaders.add(new Header("CachedKeyA", "CachedValueA"));
        expectedHeaders.add(new Header("CachedKeyB", "CachedValueB"));
        assertThat(expectedHeaders, containsInAnyOrder(response.allHeaders.toArray(new Header[0])));
    }

    @Test
    public void notModified_legacyCache() throws Exception {
        MockAsyncStack mockAsyncStack = new MockAsyncStack();
        List<Header> headers = new ArrayList<>();
        headers.add(new Header("ServerKeyA", "ServerValueA"));
        headers.add(new Header("ServerKeyB", "ServerValueB"));
        headers.add(new Header("SharedKey", "ServerValueShared"));
        headers.add(new Header("sharedcaseinsensitivekey", "ServerValueShared1"));
        headers.add(new Header("SharedCaseInsensitiveKey", "ServerValueShared2"));
        HttpResponse fakeResponse = new HttpResponse(HttpURLConnection.HTTP_NOT_MODIFIED, headers);
        mockAsyncStack.setResponseToReturn(fakeResponse);
        BasicAsyncNetwork httpNetwork = new BasicAsyncNetwork.Builder(mockAsyncStack).build();
        httpNetwork.setBlockingExecutor(executor);
        Request<String> request = buildRequest();
        Entry entry = new Entry();
        entry.responseHeaders = new HashMap<>();
        entry.responseHeaders.put("CachedKeyA", "CachedValueA");
        entry.responseHeaders.put("CachedKeyB", "CachedValueB");
        entry.responseHeaders.put("SharedKey", "CachedValueShared");
        entry.responseHeaders.put("SHAREDCASEINSENSITIVEKEY", "CachedValueShared1");
        entry.responseHeaders.put("shAREDcaSEinSENSITIVEkeY", "CachedValueShared2");
        request.setCacheEntry(entry);
        NetworkResponse response = perform(request, httpNetwork).get();
        List<Header> expectedHeaders = new ArrayList<>();
        // Should have all server headers + cache headers that didn't show up in server response.
        expectedHeaders.add(new Header("ServerKeyA", "ServerValueA"));
        expectedHeaders.add(new Header("ServerKeyB", "ServerValueB"));
        expectedHeaders.add(new Header("SharedKey", "ServerValueShared"));
        expectedHeaders.add(new Header("sharedcaseinsensitivekey", "ServerValueShared1"));
        expectedHeaders.add(new Header("SharedCaseInsensitiveKey", "ServerValueShared2"));
        expectedHeaders.add(new Header("CachedKeyA", "CachedValueA"));
        expectedHeaders.add(new Header("CachedKeyB", "CachedValueB"));
        assertThat(expectedHeaders, containsInAnyOrder(response.allHeaders.toArray(new Header[0])));
    }

    @Test
    public void socketTimeout() throws Exception {
        MockAsyncStack mockAsyncStack = new MockAsyncStack();
        mockAsyncStack.setExceptionToThrow(new SocketTimeoutException());
        BasicAsyncNetwork httpNetwork = new BasicAsyncNetwork.Builder(mockAsyncStack).build();
        httpNetwork.setBlockingExecutor(executor);
        Request<String> request = buildRequest();
        request.setRetryPolicy(mMockRetryPolicy);
        doThrow(new VolleyError()).when(mMockRetryPolicy).retry(any(VolleyError.class));
        httpNetwork.performRequest(request, mockCallback);
        verify(mockCallback, times(1)).onError(any(VolleyError.class));
        verify(mockCallback, never()).onSuccess(any(NetworkResponse.class));
        // should retry socket timeouts
        verify(mMockRetryPolicy).retry(any(TimeoutError.class));
        reset(mMockRetryPolicy, mockCallback);
    }

    @Test
    public void noConnectionDefault() throws Exception {
        MockAsyncStack mockAsyncStack = new MockAsyncStack();
        mockAsyncStack.setExceptionToThrow(new IOException());
        BasicAsyncNetwork httpNetwork = new BasicAsyncNetwork.Builder(mockAsyncStack).build();
        httpNetwork.setBlockingExecutor(executor);
        Request<String> request = buildRequest();
        request.setRetryPolicy(mMockRetryPolicy);
        doThrow(new VolleyError()).when(mMockRetryPolicy).retry(any(VolleyError.class));
        httpNetwork.performRequest(request, mockCallback);
        verify(mockCallback, times(1)).onError(any(VolleyError.class));
        verify(mockCallback, never()).onSuccess(any(NetworkResponse.class));
        // should not retry when there is no connection
        verify(mMockRetryPolicy, never()).retry(any(VolleyError.class));
        reset(mMockRetryPolicy, mockCallback);
    }

    @Test
    public void noConnectionRetry() throws Exception {
        MockAsyncStack mockAsyncStack = new MockAsyncStack();
        mockAsyncStack.setExceptionToThrow(new IOException());
        BasicAsyncNetwork httpNetwork = new BasicAsyncNetwork.Builder(mockAsyncStack).build();
        httpNetwork.setBlockingExecutor(executor);
        Request<String> request = buildRequest();
        request.setRetryPolicy(mMockRetryPolicy);
        request.setShouldRetryConnectionErrors(true);
        doThrow(new VolleyError()).when(mMockRetryPolicy).retry(any(VolleyError.class));
        httpNetwork.performRequest(request, mockCallback);
        verify(mockCallback, times(1)).onError(any(VolleyError.class));
        verify(mockCallback, never()).onSuccess(any(NetworkResponse.class));
        // should retry when there is no connection
        verify(mMockRetryPolicy).retry(any(NoConnectionError.class));
        reset(mMockRetryPolicy, mockCallback);
    }

    @Test
    public void noConnectionNoRetry() throws Exception {
        MockAsyncStack mockAsyncStack = new MockAsyncStack();
        mockAsyncStack.setExceptionToThrow(new IOException());
        BasicAsyncNetwork httpNetwork = new BasicAsyncNetwork.Builder(mockAsyncStack).build();
        httpNetwork.setBlockingExecutor(executor);
        Request<String> request = buildRequest();
        request.setRetryPolicy(mMockRetryPolicy);
        request.setShouldRetryConnectionErrors(false);
        doThrow(new VolleyError()).when(mMockRetryPolicy).retry(any(VolleyError.class));
        httpNetwork.performRequest(request, mockCallback);
        verify(mockCallback, times(1)).onError(any(VolleyError.class));
        verify(mockCallback, never()).onSuccess(any(NetworkResponse.class));
        // should not retry when there is no connection
        verify(mMockRetryPolicy, never()).retry(any(VolleyError.class));
        reset(mMockRetryPolicy, mockCallback);
    }

    @Test
    public void unauthorized() throws Exception {
        MockAsyncStack mockAsyncStack = new MockAsyncStack();
        HttpResponse fakeResponse = new HttpResponse(401, Collections.<Header>emptyList());
        mockAsyncStack.setResponseToReturn(fakeResponse);
        BasicAsyncNetwork httpNetwork = new BasicAsyncNetwork.Builder(mockAsyncStack).build();
        httpNetwork.setBlockingExecutor(executor);
        Request<String> request = buildRequest();
        request.setRetryPolicy(mMockRetryPolicy);
        doThrow(new VolleyError()).when(mMockRetryPolicy).retry(any(VolleyError.class));
        httpNetwork.performRequest(request, mockCallback);
        verify(mockCallback, times(1)).onError(any(VolleyError.class));
        verify(mockCallback, never()).onSuccess(any(NetworkResponse.class));
        // should retry in case it's an auth failure.
        verify(mMockRetryPolicy).retry(any(AuthFailureError.class));
        reset(mMockRetryPolicy, mockCallback);
    }

    @Test(expected = RuntimeException.class)
    public void malformedUrlRequest() throws VolleyError, ExecutionException, InterruptedException {
        MockAsyncStack mockAsyncStack = new MockAsyncStack();
        mockAsyncStack.setExceptionToThrow(new MalformedURLException());
        BasicAsyncNetwork httpNetwork = new BasicAsyncNetwork.Builder(mockAsyncStack).build();
        httpNetwork.setBlockingExecutor(executor);
        Request<String> request = buildRequest();
        request.setRetryPolicy(mMockRetryPolicy);
        doThrow(new VolleyError()).when(mMockRetryPolicy).retry(any(VolleyError.class));
        perform(request, httpNetwork).get();
    }

    @Test
    public void forbidden() throws Exception {
        MockAsyncStack mockAsyncStack = new MockAsyncStack();
        HttpResponse fakeResponse = new HttpResponse(403, Collections.<Header>emptyList());
        mockAsyncStack.setResponseToReturn(fakeResponse);
        BasicAsyncNetwork httpNetwork = new BasicAsyncNetwork.Builder(mockAsyncStack).build();
        httpNetwork.setBlockingExecutor(executor);
        Request<String> request = buildRequest();
        request.setRetryPolicy(mMockRetryPolicy);
        doThrow(new VolleyError()).when(mMockRetryPolicy).retry(any(VolleyError.class));
        httpNetwork.performRequest(request, mockCallback);
        verify(mockCallback, times(1)).onError(any(VolleyError.class));
        verify(mockCallback, never()).onSuccess(any(NetworkResponse.class));
        // should retry in case it's an auth failure.
        verify(mMockRetryPolicy).retry(any(AuthFailureError.class));
        reset(mMockRetryPolicy, mockCallback);
    }

    @Test
    public void redirect() throws Exception {
        for (int i = 300; i <= 399; i++) {
            MockAsyncStack mockAsyncStack = new MockAsyncStack();
            HttpResponse fakeResponse = new HttpResponse(i, Collections.<Header>emptyList());
            mockAsyncStack.setResponseToReturn(fakeResponse);
            BasicAsyncNetwork httpNetwork = new BasicAsyncNetwork.Builder(mockAsyncStack).build();
            httpNetwork.setBlockingExecutor(executor);
            Request<String> request = buildRequest();
            request.setRetryPolicy(mMockRetryPolicy);
            doThrow(new VolleyError()).when(mMockRetryPolicy).retry(any(VolleyError.class));
            httpNetwork.performRequest(request, mockCallback);
            if (i != 304) {
                verify(mockCallback, times(1)).onError(any(VolleyError.class));
                verify(mockCallback, never()).onSuccess(any(NetworkResponse.class));
            } else {
                verify(mockCallback, never()).onError(any(VolleyError.class));
                verify(mockCallback, times(1)).onSuccess(any(NetworkResponse.class));
            }
            // should not retry 300 responses.
            verify(mMockRetryPolicy, never()).retry(any(VolleyError.class));
            reset(mMockRetryPolicy, mockCallback);
        }
    }

    @Test
    public void otherClientError() throws Exception {
        for (int i = 400; i <= 499; i++) {
            if (i == 401 || i == 403) {
                // covered above.
                continue;
            }
            MockAsyncStack mockAsyncStack = new MockAsyncStack();
            HttpResponse fakeResponse = new HttpResponse(i, Collections.<Header>emptyList());
            mockAsyncStack.setResponseToReturn(fakeResponse);
            BasicAsyncNetwork httpNetwork = new BasicAsyncNetwork.Builder(mockAsyncStack).build();
            httpNetwork.setBlockingExecutor(executor);
            Request<String> request = buildRequest();
            request.setRetryPolicy(mMockRetryPolicy);
            doThrow(new VolleyError()).when(mMockRetryPolicy).retry(any(VolleyError.class));
            httpNetwork.performRequest(request, mockCallback);
            verify(mockCallback, times(1)).onError(any(VolleyError.class));
            verify(mockCallback, never()).onSuccess(any(NetworkResponse.class));
            // should not retry other 400 errors.
            verify(mMockRetryPolicy, never()).retry(any(VolleyError.class));
            reset(mMockRetryPolicy, mockCallback);
        }
    }

    @Test
    public void serverError_enableRetries() throws Exception {
        for (int i = 500; i <= 599; i++) {
            MockAsyncStack mockAsyncStack = new MockAsyncStack();
            HttpResponse fakeResponse = new HttpResponse(i, Collections.<Header>emptyList());
            mockAsyncStack.setResponseToReturn(fakeResponse);
            BasicAsyncNetwork httpNetwork =
                    new BasicAsyncNetwork.Builder(mockAsyncStack)
                            .setPool(new ByteArrayPool(4096))
                            .build();
            httpNetwork.setBlockingExecutor(executor);
            Request<String> request = buildRequest();
            request.setRetryPolicy(mMockRetryPolicy);
            request.setShouldRetryServerErrors(true);
            doThrow(new VolleyError()).when(mMockRetryPolicy).retry(any(VolleyError.class));
            httpNetwork.performRequest(request, mockCallback);
            verify(mockCallback, times(1)).onError(any(VolleyError.class));
            verify(mockCallback, never()).onSuccess(any(NetworkResponse.class));
            // should retry all 500 errors
            verify(mMockRetryPolicy).retry(any(ServerError.class));
            reset(mMockRetryPolicy, mockCallback);
        }
    }

    @Test
    public void serverError_disableRetries() throws Exception {
        for (int i = 500; i <= 599; i++) {
            MockAsyncStack mockAsyncStack = new MockAsyncStack();
            HttpResponse fakeResponse = new HttpResponse(i, Collections.<Header>emptyList());
            mockAsyncStack.setResponseToReturn(fakeResponse);
            BasicAsyncNetwork httpNetwork = new BasicAsyncNetwork.Builder(mockAsyncStack).build();
            httpNetwork.setBlockingExecutor(executor);
            Request<String> request = buildRequest();
            request.setRetryPolicy(mMockRetryPolicy);
            doThrow(new VolleyError()).when(mMockRetryPolicy).retry(any(VolleyError.class));
            httpNetwork.performRequest(request, mockCallback);
            verify(mockCallback, times(1)).onError(any(VolleyError.class));
            verify(mockCallback, never()).onSuccess(any(NetworkResponse.class));
            // should not retry any 500 error w/ HTTP 500 retries turned off (the default).
            verify(mMockRetryPolicy, never()).retry(any(VolleyError.class));
            reset(mMockRetryPolicy, mockCallback);
        }
    }

    @Test
    public void notModifiedShortCircuit() throws Exception {
        MockAsyncStack mockAsyncStack = new MockAsyncStack();
        List<Header> headers = new ArrayList<>();
        headers.add(new Header("ServerKeyA", "ServerValueA"));
        headers.add(new Header("ServerKeyB", "ServerValueB"));
        headers.add(new Header("SharedKey", "ServerValueShared"));
        headers.add(new Header("sharedcaseinsensitivekey", "ServerValueShared1"));
        headers.add(new Header("SharedCaseInsensitiveKey", "ServerValueShared2"));
        HttpResponse fakeResponse = new HttpResponse(HttpURLConnection.HTTP_NOT_MODIFIED, headers);
        mockAsyncStack.setResponseToReturn(fakeResponse);
        BasicAsyncNetwork httpNetwork = new BasicAsyncNetwork.Builder(mockAsyncStack).build();
        httpNetwork.setBlockingExecutor(executor);
        Request<String> request = buildRequest();
        httpNetwork.performRequest(request, mockCallback);
        verify(mockCallback, times(1)).onSuccess(any(NetworkResponse.class));
        verify(mockCallback, never()).onError(any(VolleyError.class));
        reset(mMockRetryPolicy, mockCallback);
    }

    @Test
    public void performRequestSuccess() throws Exception {
        MockAsyncStack mockAsyncStack = new MockAsyncStack();
        HttpResponse fakeResponse =
                new HttpResponse(
                        200,
                        Collections.<Header>emptyList(),
                        "foobar".getBytes(StandardCharsets.UTF_8));
        mockAsyncStack.setResponseToReturn(fakeResponse);
        BasicAsyncNetwork httpNetwork = new BasicAsyncNetwork.Builder(mockAsyncStack).build();
        httpNetwork.setBlockingExecutor(executor);
        Request<String> request = buildRequest();
        Entry entry = new Entry();
        entry.etag = "foobar";
        entry.lastModified = 1503102002000L;
        request.setCacheEntry(entry);
        httpNetwork.performRequest(request, mockCallback);
        verify(mockCallback, times(1)).onSuccess(any(NetworkResponse.class));
        verify(mockCallback, never()).onError(any(VolleyError.class));
        reset(mMockRetryPolicy, mockCallback);
    }

    @Test(expected = IllegalStateException.class)
    public void performRequestNeverSetExecutorTest() throws Exception {
        MockAsyncStack mockAsyncStack = new MockAsyncStack();
        HttpResponse fakeResponse = new HttpResponse(200, Collections.<Header>emptyList());
        mockAsyncStack.setResponseToReturn(fakeResponse);
        BasicAsyncNetwork httpNetwork = new BasicAsyncNetwork.Builder(mockAsyncStack).build();
        Request<String> request = buildRequest();
        perform(request, httpNetwork).get();
    }

    /** Helper functions */
    private CompletableFuture<NetworkResponse> perform(Request<?> request, AsyncNetwork network)
            throws VolleyError {
        final CompletableFuture<NetworkResponse> future = new CompletableFuture<>();
        network.performRequest(
                request,
                new AsyncNetwork.OnRequestComplete() {
                    @Override
                    public void onSuccess(NetworkResponse networkResponse) {
                        future.complete(networkResponse);
                    }

                    @Override
                    public void onError(VolleyError volleyError) {
                        future.complete(null);
                    }
                });
        return future;
    }

    private static Request<String> buildRequest() {
        return new Request<String>(Request.Method.GET, "http://foo", null) {

            @Override
            protected Response<String> parseNetworkResponse(NetworkResponse response) {
                return null;
            }

            @Override
            protected void deliverResponse(String response) {}

            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> result = new HashMap<String, String>();
                result.put("requestheader", "foo");
                return result;
            }

            @Override
            public Map<String, String> getParams() {
                Map<String, String> result = new HashMap<String, String>();
                result.put("requestpost", "foo");
                return result;
            }
        };
    }
}

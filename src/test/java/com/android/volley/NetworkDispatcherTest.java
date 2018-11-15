/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.volley;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.volley.toolbox.NoCache;
import com.android.volley.toolbox.StringRequest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class NetworkDispatcherTest {
    private NetworkDispatcher mDispatcher;
    private @Mock ResponseDelivery mDelivery;
    private @Mock BlockingQueue<Request<?>> mNetworkQueue;
    private @Mock Network mNetwork;
    private @Mock Cache mCache;
    private StringRequest mRequest;

    private static final byte[] CANNED_DATA =
            "Ceci n'est pas une vraie reponse".getBytes(StandardCharsets.UTF_8);

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        mRequest = new StringRequest(Request.Method.GET, "http://foo", null, null);
        mDispatcher = new NetworkDispatcher(mNetworkQueue, mNetwork, mCache, mDelivery);
    }

    @Test
    public void successPostsResponse() throws Exception {
        when(mNetwork.performRequest(any(Request.class)))
                .thenReturn(new NetworkResponse(CANNED_DATA));
        mDispatcher.processRequest(mRequest);

        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(mDelivery).postResponse(any(Request.class), response.capture());
        assertTrue(response.getValue().isSuccess());
        assertEquals(response.getValue().result, new String(CANNED_DATA, StandardCharsets.UTF_8));

        verify(mDelivery, never()).postError(any(Request.class), any(VolleyError.class));
    }

    @Test
    public void successNotifiesListener() throws Exception {
        RequestQueue.RequestEventListener listener = mock(RequestQueue.RequestEventListener.class);
        RequestQueue queue = new RequestQueue(new NoCache(), mNetwork, 0, mDelivery);
        queue.addRequestEventListener(listener);
        mRequest.setRequestQueue(queue);

        when(mNetwork.performRequest(any(Request.class)))
                .thenReturn(new NetworkResponse(CANNED_DATA));

        mDispatcher.processRequest(mRequest);

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener)
                .onRequestEvent(
                        mRequest, RequestQueue.RequestEvent.REQUEST_NETWORK_DISPATCH_STARTED);
        inOrder.verify(listener)
                .onRequestEvent(
                        mRequest, RequestQueue.RequestEvent.REQUEST_NETWORK_DISPATCH_FINISHED);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void exceptionPostsError() throws Exception {
        when(mNetwork.performRequest(any(Request.class))).thenThrow(new ServerError());
        mDispatcher.processRequest(mRequest);

        verify(mDelivery).postError(any(Request.class), any(VolleyError.class));
        verify(mDelivery, never()).postResponse(any(Request.class), any(Response.class));
    }

    @Test
    public void exceptionNotifiesListener() throws Exception {
        RequestQueue.RequestEventListener listener = mock(RequestQueue.RequestEventListener.class);
        RequestQueue queue = new RequestQueue(new NoCache(), mNetwork, 0, mDelivery);
        queue.addRequestEventListener(listener);
        mRequest.setRequestQueue(queue);

        when(mNetwork.performRequest(any(Request.class))).thenThrow(new ServerError());

        mDispatcher.processRequest(mRequest);

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener)
                .onRequestEvent(
                        mRequest, RequestQueue.RequestEvent.REQUEST_NETWORK_DISPATCH_STARTED);
        inOrder.verify(listener)
                .onRequestEvent(
                        mRequest, RequestQueue.RequestEvent.REQUEST_NETWORK_DISPATCH_FINISHED);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void shouldCacheFalse() throws Exception {
        mRequest.setShouldCache(false);
        mDispatcher.processRequest(mRequest);
        verify(mCache, never()).put(anyString(), any(Cache.Entry.class));
    }

    @Test
    public void shouldCacheTrue() throws Exception {
        when(mNetwork.performRequest(any(Request.class)))
                .thenReturn(new NetworkResponse(CANNED_DATA));
        mRequest.setShouldCache(true);
        mDispatcher.processRequest(mRequest);
        ArgumentCaptor<Cache.Entry> entry = ArgumentCaptor.forClass(Cache.Entry.class);
        verify(mCache).put(eq(mRequest.getCacheKey()), entry.capture());
        assertTrue(Arrays.equals(entry.getValue().data, CANNED_DATA));
    }
}

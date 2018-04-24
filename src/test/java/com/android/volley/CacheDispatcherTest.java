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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.volley.toolbox.StringRequest;
import com.android.volley.utils.CacheTestUtils;
import java.util.concurrent.BlockingQueue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
@SuppressWarnings("rawtypes")
public class CacheDispatcherTest {
    private CacheDispatcher mDispatcher;
    private @Mock BlockingQueue<Request<?>> mCacheQueue;
    private @Mock BlockingQueue<Request<?>> mNetworkQueue;
    private @Mock Cache mCache;
    private @Mock ResponseDelivery mDelivery;
    private StringRequest mRequest;

    @Before
    public void setUp() throws Exception {
        initMocks(this);

        mRequest = new StringRequest(Request.Method.GET, "http://foo", null, null);
        mDispatcher = new CacheDispatcher(mCacheQueue, mNetworkQueue, mCache, mDelivery);
    }

    private static class WaitForever implements Answer {
        @Override
        public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
            Thread.sleep(Long.MAX_VALUE);
            return null;
        }
    }

    @Test
    public void runStopsOnQuit() throws Exception {
        when(mCacheQueue.take()).then(new WaitForever());
        mDispatcher.start();
        mDispatcher.quit();
        mDispatcher.join(1000);
    }

    private static void verifyNoResponse(ResponseDelivery delivery) {
        verify(delivery, never()).postResponse(any(Request.class), any(Response.class));
        verify(delivery, never())
                .postResponse(any(Request.class), any(Response.class), any(Runnable.class));
        verify(delivery, never()).postError(any(Request.class), any(VolleyError.class));
    }

    // A cancelled request should not be processed at all.
    @Test
    public void cancelledRequest() throws Exception {
        mRequest.cancel();
        mDispatcher.processRequest(mRequest);
        verify(mCache, never()).get(anyString());
        verifyNoResponse(mDelivery);
    }

    // A cache miss does not post a response and puts the request on the network queue.
    @Test
    public void cacheMiss() throws Exception {
        mDispatcher.processRequest(mRequest);
        verifyNoResponse(mDelivery);
        verify(mNetworkQueue).put(mRequest);
        assertNull(mRequest.getCacheEntry());
    }

    // A non-expired cache hit posts a response and does not queue to the network.
    @Test
    public void nonExpiredCacheHit() throws Exception {
        Cache.Entry entry = CacheTestUtils.makeRandomCacheEntry(null, false, false);
        when(mCache.get(anyString())).thenReturn(entry);
        mDispatcher.processRequest(mRequest);
        verify(mDelivery).postResponse(any(Request.class), any(Response.class));
        verify(mDelivery, never()).postError(any(Request.class), any(VolleyError.class));
    }

    // A soft-expired cache hit posts a response and queues to the network.
    @Test
    public void softExpiredCacheHit() throws Exception {
        Cache.Entry entry = CacheTestUtils.makeRandomCacheEntry(null, false, true);
        when(mCache.get(anyString())).thenReturn(entry);
        mDispatcher.processRequest(mRequest);

        // Soft expiration needs to use the deferred Runnable variant of postResponse,
        // so make sure it gets to run.
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        verify(mDelivery).postResponse(any(Request.class), any(Response.class), runnable.capture());
        runnable.getValue().run();
        // This way we can verify the behavior of the Runnable as well.
        verify(mNetworkQueue).put(mRequest);
        assertSame(entry, mRequest.getCacheEntry());

        verify(mDelivery, never()).postError(any(Request.class), any(VolleyError.class));
    }

    // An expired cache hit does not post a response and queues to the network.
    @Test
    public void expiredCacheHit() throws Exception {
        Cache.Entry entry = CacheTestUtils.makeRandomCacheEntry(null, true, true);
        when(mCache.get(anyString())).thenReturn(entry);
        mDispatcher.processRequest(mRequest);
        verifyNoResponse(mDelivery);
        verify(mNetworkQueue).put(mRequest);
        assertSame(entry, mRequest.getCacheEntry());
    }

    @Test
    public void duplicateCacheMiss() throws Exception {
        StringRequest secondRequest =
                new StringRequest(Request.Method.GET, "http://foo", null, null);
        mRequest.setSequence(1);
        secondRequest.setSequence(2);
        mDispatcher.processRequest(mRequest);
        mDispatcher.processRequest(secondRequest);
        verify(mNetworkQueue).put(mRequest);
        verifyNoResponse(mDelivery);
    }

    @Test
    public void tripleCacheMiss_networkErrorOnFirst() throws Exception {
        StringRequest secondRequest =
                new StringRequest(Request.Method.GET, "http://foo", null, null);
        StringRequest thirdRequest =
                new StringRequest(Request.Method.GET, "http://foo", null, null);
        mRequest.setSequence(1);
        secondRequest.setSequence(2);
        thirdRequest.setSequence(3);
        mDispatcher.processRequest(mRequest);
        mDispatcher.processRequest(secondRequest);
        mDispatcher.processRequest(thirdRequest);

        verify(mNetworkQueue).put(mRequest);
        verifyNoResponse(mDelivery);

        ((Request<?>) mRequest).notifyListenerResponseNotUsable();
        // Second request should now be in network queue.
        verify(mNetworkQueue).put(secondRequest);
        // Another unusable response, third request should now be added.
        ((Request<?>) secondRequest).notifyListenerResponseNotUsable();
        verify(mNetworkQueue).put(thirdRequest);
    }

    @Test
    public void duplicateSoftExpiredCacheHit_failedRequest() throws Exception {
        Cache.Entry entry = CacheTestUtils.makeRandomCacheEntry(null, false, true);
        when(mCache.get(anyString())).thenReturn(entry);

        StringRequest secondRequest =
                new StringRequest(Request.Method.GET, "http://foo", null, null);
        mRequest.setSequence(1);
        secondRequest.setSequence(2);

        mDispatcher.processRequest(mRequest);
        mDispatcher.processRequest(secondRequest);

        // Soft expiration needs to use the deferred Runnable variant of postResponse,
        // so make sure it gets to run.
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        verify(mDelivery).postResponse(any(Request.class), any(Response.class), runnable.capture());
        runnable.getValue().run();
        // This way we can verify the behavior of the Runnable as well.

        verify(mNetworkQueue).put(mRequest);
        verify(mDelivery)
                .postResponse(any(Request.class), any(Response.class), any(Runnable.class));

        ((Request<?>) mRequest).notifyListenerResponseNotUsable();
        // Second request should now be in network queue.
        verify(mNetworkQueue).put(secondRequest);
    }

    @Test
    public void duplicateSoftExpiredCacheHit_successfulRequest() throws Exception {
        Cache.Entry entry = CacheTestUtils.makeRandomCacheEntry(null, false, true);
        when(mCache.get(anyString())).thenReturn(entry);

        StringRequest secondRequest =
                new StringRequest(Request.Method.GET, "http://foo", null, null);
        mRequest.setSequence(1);
        secondRequest.setSequence(2);

        mDispatcher.processRequest(mRequest);
        mDispatcher.processRequest(secondRequest);

        // Soft expiration needs to use the deferred Runnable variant of postResponse,
        // so make sure it gets to run.
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        verify(mDelivery).postResponse(any(Request.class), any(Response.class), runnable.capture());
        runnable.getValue().run();
        // This way we can verify the behavior of the Runnable as well.

        verify(mNetworkQueue).put(mRequest);
        verify(mDelivery)
                .postResponse(any(Request.class), any(Response.class), any(Runnable.class));

        ((Request<?>) mRequest).notifyListenerResponseReceived(Response.success(null, entry));
        // Second request should have delivered response.
        verify(mNetworkQueue, never()).put(secondRequest);
        verify(mDelivery)
                .postResponse(any(Request.class), any(Response.class), any(Runnable.class));
    }
}

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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.volley.mock.ShadowSystemClock;
import com.android.volley.toolbox.NoAsyncCache;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.utils.ImmediateResponseDelivery;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Unit tests for AsyncRequestQueue, with all dependencies mocked out */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowSystemClock.class})
public class AsyncRequestQueueTest {

    @Mock private AsyncNetwork mMockNetwork;
    @Mock private ScheduledExecutorService mMockScheduledExecutor;
    private AsyncRequestQueue queue;

    @Before
    public void setUp() throws Exception {
        ResponseDelivery mDelivery = new ImmediateResponseDelivery();
        initMocks(this);
        queue =
                new AsyncRequestQueue.Builder(mMockNetwork)
                        .setAsyncCache(new NoAsyncCache())
                        .setResponseDelivery(mDelivery)
                        .setExecutorFactory(
                                new AsyncRequestQueue.ExecutorFactory() {
                                    @Override
                                    public ExecutorService createNonBlockingExecutor(
                                            BlockingQueue<Runnable> taskQueue) {
                                        return MoreExecutors.newDirectExecutorService();
                                    }

                                    @Override
                                    public ExecutorService createBlockingExecutor(
                                            BlockingQueue<Runnable> taskQueue) {
                                        return MoreExecutors.newDirectExecutorService();
                                    }

                                    @Override
                                    public ScheduledExecutorService
                                            createNonBlockingScheduledExecutor() {
                                        return mMockScheduledExecutor;
                                    }
                                })
                        .build();
    }

    @Test
    public void cancelAll_onlyCorrectTag() throws Exception {
        queue.start();
        Object tagA = new Object();
        Object tagB = new Object();
        StringRequest req1 = mock(StringRequest.class);
        when(req1.getTag()).thenReturn(tagA);
        StringRequest req2 = mock(StringRequest.class);
        when(req2.getTag()).thenReturn(tagB);
        StringRequest req3 = mock(StringRequest.class);
        when(req3.getTag()).thenReturn(tagA);
        StringRequest req4 = mock(StringRequest.class);
        when(req4.getTag()).thenReturn(tagA);

        queue.add(req1); // A
        queue.add(req2); // B
        queue.add(req3); // A
        queue.cancelAll(tagA);
        queue.add(req4); // A

        verify(req1).cancel(); // A cancelled
        verify(req3).cancel(); // A cancelled
        verify(req2, never()).cancel(); // B not cancelled
        verify(req4, never()).cancel(); // A added after cancel not cancelled
        queue.stop();
    }

    @Test
    public void add_notifiesListener() throws Exception {
        RequestQueue.RequestEventListener listener = mock(RequestQueue.RequestEventListener.class);
        queue.start();
        queue.addRequestEventListener(listener);
        StringRequest req = mock(StringRequest.class);

        queue.add(req);

        verify(listener).onRequestEvent(req, RequestQueue.RequestEvent.REQUEST_QUEUED);
        verifyNoMoreInteractions(listener);
        queue.stop();
    }

    @Test
    public void finish_notifiesListener() throws Exception {
        RequestQueue.RequestEventListener listener = mock(RequestQueue.RequestEventListener.class);
        queue.start();
        queue.addRequestEventListener(listener);
        StringRequest req = mock(StringRequest.class);

        queue.finish(req);

        verify(listener).onRequestEvent(req, RequestQueue.RequestEvent.REQUEST_FINISHED);
        verifyNoMoreInteractions(listener);
        queue.stop();
    }

    @Test
    public void sendRequestEvent_notifiesListener() throws Exception {
        StringRequest req = mock(StringRequest.class);
        RequestQueue.RequestEventListener listener = mock(RequestQueue.RequestEventListener.class);
        queue.start();
        queue.addRequestEventListener(listener);

        queue.sendRequestEvent(req, RequestQueue.RequestEvent.REQUEST_NETWORK_DISPATCH_STARTED);

        verify(listener)
                .onRequestEvent(req, RequestQueue.RequestEvent.REQUEST_NETWORK_DISPATCH_STARTED);
        verifyNoMoreInteractions(listener);
        queue.stop();
    }

    @Test
    public void removeRequestEventListener_removesListener() throws Exception {
        StringRequest req = mock(StringRequest.class);
        RequestQueue.RequestEventListener listener = mock(RequestQueue.RequestEventListener.class);
        queue.start();
        queue.addRequestEventListener(listener);
        queue.removeRequestEventListener(listener);

        queue.sendRequestEvent(req, RequestQueue.RequestEvent.REQUEST_NETWORK_DISPATCH_STARTED);

        verifyNoMoreInteractions(listener);
        queue.stop();
    }
}

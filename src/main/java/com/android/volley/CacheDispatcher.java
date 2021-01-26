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

import android.os.Process;
import androidx.annotation.VisibleForTesting;
import java.util.concurrent.BlockingQueue;

/**
 * Provides a thread for performing cache triage on a queue of requests.
 *
 * <p>Requests added to the specified cache queue are resolved from cache. Any deliverable response
 * is posted back to the caller via a {@link ResponseDelivery}. Cache misses and responses that
 * require refresh are enqueued on the specified network queue for processing by a {@link
 * NetworkDispatcher}.
 */
public class CacheDispatcher extends Thread {

    private static final boolean DEBUG = VolleyLog.DEBUG;

    /** The queue of requests coming in for triage. */
    private final BlockingQueue<Request<?>> mCacheQueue;

    /** The queue of requests going out to the network. */
    private final BlockingQueue<Request<?>> mNetworkQueue;

    /** The cache to read from. */
    private final Cache mCache;

    /** For posting responses. */
    private final ResponseDelivery mDelivery;

    /** Used for telling us to die. */
    private volatile boolean mQuit = false;

    /** Manage list of waiting requests and de-duplicate requests with same cache key. */
    private final WaitingRequestManager mWaitingRequestManager;

    /**
     * Creates a new cache triage dispatcher thread. You must call {@link #start()} in order to
     * begin processing.
     *
     * @param cacheQueue Queue of incoming requests for triage
     * @param networkQueue Queue to post requests that require network to
     * @param cache Cache interface to use for resolution
     * @param delivery Delivery interface to use for posting responses
     */
    public CacheDispatcher(
            BlockingQueue<Request<?>> cacheQueue,
            BlockingQueue<Request<?>> networkQueue,
            Cache cache,
            ResponseDelivery delivery) {
        mCacheQueue = cacheQueue;
        mNetworkQueue = networkQueue;
        mCache = cache;
        mDelivery = delivery;
        mWaitingRequestManager = new WaitingRequestManager(this, networkQueue, delivery);
    }

    /**
     * Forces this dispatcher to quit immediately. If any requests are still in the queue, they are
     * not guaranteed to be processed.
     */
    public void quit() {
        mQuit = true;
        interrupt();
    }

    @Override
    public void run() {
        if (DEBUG) VolleyLog.v("start new dispatcher");
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        // Make a blocking call to initialize the cache.
        mCache.initialize();

        while (true) {
            try {
                processRequest();
            } catch (InterruptedException e) {
                // We may have been interrupted because it was time to quit.
                if (mQuit) {
                    Thread.currentThread().interrupt();
                    return;
                }
                VolleyLog.e(
                        "Ignoring spurious interrupt of CacheDispatcher thread; "
                                + "use quit() to terminate it");
            }
        }
    }

    // Extracted to its own method to ensure locals have a constrained liveness scope by the GC.
    // This is needed to avoid keeping previous request references alive for an indeterminate amount
    // of time. Update consumer-proguard-rules.pro when modifying this. See also
    // https://github.com/google/volley/issues/114
    private void processRequest() throws InterruptedException {
        // Get a request from the cache triage queue, blocking until
        // at least one is available.
        final Request<?> request = mCacheQueue.take();
        processRequest(request);
    }

    @VisibleForTesting
    void processRequest(final Request<?> request) throws InterruptedException {
        request.addMarker("cache-queue-take");
        request.sendEvent(RequestQueue.RequestEvent.REQUEST_CACHE_LOOKUP_STARTED);

        try {
            // If the request has been canceled, don't bother dispatching it.
            if (request.isCanceled()) {
                request.finish("cache-discard-canceled");
                return;
            }

            // Attempt to retrieve this item from cache.
            Cache.Entry entry = mCache.get(request.getCacheKey());
            if (entry == null) {
                request.addMarker("cache-miss");
                // Cache miss; send off to the network dispatcher.
                if (!mWaitingRequestManager.maybeAddToWaitingRequests(request)) {
                    mNetworkQueue.put(request);
                }
                return;
            }

            // Use a single instant to evaluate cache expiration. Otherwise, a cache entry with
            // identical soft and hard TTL times may appear to be valid when checking isExpired but
            // invalid upon checking refreshNeeded(), triggering a soft TTL refresh which should be
            // impossible.
            long currentTimeMillis = System.currentTimeMillis();

            // If it is completely expired, just send it to the network.
            if (entry.isExpired(currentTimeMillis)) {
                request.addMarker("cache-hit-expired");
                request.setCacheEntry(entry);
                if (!mWaitingRequestManager.maybeAddToWaitingRequests(request)) {
                    mNetworkQueue.put(request);
                }
                return;
            }

            // We have a cache hit; parse its data for delivery back to the request.
            request.addMarker("cache-hit");
            Response<?> response =
                    request.parseNetworkResponse(
                            new NetworkResponse(entry.data, entry.responseHeaders));
            request.addMarker("cache-hit-parsed");

            if (!response.isSuccess()) {
                request.addMarker("cache-parsing-failed");
                mCache.invalidate(request.getCacheKey(), true);
                request.setCacheEntry(null);
                if (!mWaitingRequestManager.maybeAddToWaitingRequests(request)) {
                    mNetworkQueue.put(request);
                }
                return;
            }
            if (!entry.refreshNeeded(currentTimeMillis)) {
                // Completely unexpired cache hit. Just deliver the response.
                mDelivery.postResponse(request, response);
            } else {
                // Soft-expired cache hit. We can deliver the cached response,
                // but we need to also send the request to the network for
                // refreshing.
                request.addMarker("cache-hit-refresh-needed");
                request.setCacheEntry(entry);
                // Mark the response as intermediate.
                response.intermediate = true;

                if (!mWaitingRequestManager.maybeAddToWaitingRequests(request)) {
                    // Post the intermediate response back to the user and have
                    // the delivery then forward the request along to the network.
                    mDelivery.postResponse(
                            request,
                            response,
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        mNetworkQueue.put(request);
                                    } catch (InterruptedException e) {
                                        // Restore the interrupted status
                                        Thread.currentThread().interrupt();
                                    }
                                }
                            });
                } else {
                    // request has been added to list of waiting requests
                    // to receive the network response from the first request once it returns.
                    mDelivery.postResponse(request, response);
                }
            }
        } finally {
            request.sendEvent(RequestQueue.RequestEvent.REQUEST_CACHE_LOOKUP_FINISHED);
        }
    }
}

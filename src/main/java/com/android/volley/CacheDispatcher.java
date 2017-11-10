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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * Provides a thread for performing cache triage on a queue of requests.
 *
 * Requests added to the specified cache queue are resolved from cache.
 * Any deliverable response is posted back to the caller via a
 * {@link ResponseDelivery}.  Cache misses and responses that require
 * refresh are enqueued on the specified network queue for processing
 * by a {@link NetworkDispatcher}.
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
     * Creates a new cache triage dispatcher thread.  You must call {@link #start()}
     * in order to begin processing.
     *
     * @param cacheQueue Queue of incoming requests for triage
     * @param networkQueue Queue to post requests that require network to
     * @param cache Cache interface to use for resolution
     * @param delivery Delivery interface to use for posting responses
     */
    public CacheDispatcher(
            BlockingQueue<Request<?>> cacheQueue, BlockingQueue<Request<?>> networkQueue,
            Cache cache, ResponseDelivery delivery) {
        mCacheQueue = cacheQueue;
        mNetworkQueue = networkQueue;
        mCache = cache;
        mDelivery = delivery;
        mWaitingRequestManager = new WaitingRequestManager(this);
    }

    /**
     * Forces this dispatcher to quit immediately.  If any requests are still in
     * the queue, they are not guaranteed to be processed.
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
                    return;
                }
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
        request.addMarker("cache-queue-take");

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

        // If it is completely expired, just send it to the network.
        if (entry.isExpired()) {
            request.addMarker("cache-hit-expired");
            request.setCacheEntry(entry);
            if (!mWaitingRequestManager.maybeAddToWaitingRequests(request)) {
                mNetworkQueue.put(request);
            }
            return;
        }

        // We have a cache hit; parse its data for delivery back to the request.
        request.addMarker("cache-hit");
        Response<?> response = request.parseNetworkResponse(
                new NetworkResponse(entry.data, entry.responseHeaders));
        request.addMarker("cache-hit-parsed");

        if (!entry.refreshNeeded()) {
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
                mDelivery.postResponse(request, response, new Runnable() {
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
    }

    private static class WaitingRequestManager implements Request.NetworkRequestCompleteListener {

        /**
         * Staging area for requests that already have a duplicate request in flight.
         *
         * <ul>
         *     <li>containsKey(cacheKey) indicates that there is a request in flight for the given cache
         *          key.</li>
         *     <li>get(cacheKey) returns waiting requests for the given cache key. The in flight request
         *          is <em>not</em> contained in that list. Is null if no requests are staged.</li>
         * </ul>
         */
        private final Map<String, List<Request<?>>> mWaitingRequests = new HashMap<>();

        private final CacheDispatcher mCacheDispatcher;

        WaitingRequestManager(CacheDispatcher cacheDispatcher) {
            mCacheDispatcher = cacheDispatcher;
        }

        /** Request received a valid response that can be used by other waiting requests. */
        @Override
        public void onResponseReceived(Request<?> request, Response<?> response) {
            if (response.cacheEntry == null || response.cacheEntry.isExpired()) {
                onNoUsableResponseReceived(request);
                return;
            }
            String cacheKey = request.getCacheKey();
            List<Request<?>> waitingRequests;
            synchronized (this) {
                waitingRequests = mWaitingRequests.remove(cacheKey);
            }
            if (waitingRequests != null) {
                if (VolleyLog.DEBUG) {
                    VolleyLog.v("Releasing %d waiting requests for cacheKey=%s.",
                            waitingRequests.size(), cacheKey);
                }
                // Process all queued up requests.
                for (Request<?> waiting : waitingRequests) {
                    mCacheDispatcher.mDelivery.postResponse(waiting, response);
                }
            }
        }

        /** No valid response received from network, release waiting requests. */
        @Override
        public synchronized void onNoUsableResponseReceived(Request<?> request) {
            String cacheKey = request.getCacheKey();
            List<Request<?>> waitingRequests = mWaitingRequests.remove(cacheKey);
            if (waitingRequests != null && !waitingRequests.isEmpty()) {
                if (VolleyLog.DEBUG) {
                    VolleyLog.v("%d waiting requests for cacheKey=%s; resend to network",
                            waitingRequests.size(), cacheKey);
                }
                Request<?> nextInLine = waitingRequests.remove(0);
                mWaitingRequests.put(cacheKey, waitingRequests);
                nextInLine.setNetworkRequestCompleteListener(this);
                try {
                    mCacheDispatcher.mNetworkQueue.put(nextInLine);
                } catch (InterruptedException iex) {
                    VolleyLog.e("Couldn't add request to queue. %s", iex.toString());
                    // Restore the interrupted status of the calling thread (i.e. NetworkDispatcher)
                    Thread.currentThread().interrupt();
                    // Quit the current CacheDispatcher thread.
                    mCacheDispatcher.quit();
                }
            }
        }

        /**
         * For cacheable requests, if a request for the same cache key is already in flight,
         * add it to a queue to wait for that in-flight request to finish.
         * @return whether the request was queued. If false, we should continue issuing the request
         * over the network. If true, we should put the request on hold to be processed when
         * the in-flight request finishes.
         */
        private synchronized boolean maybeAddToWaitingRequests(Request<?> request) {
            String cacheKey = request.getCacheKey();
            // Insert request into stage if there's already a request with the same cache key
            // in flight.
            if (mWaitingRequests.containsKey(cacheKey)) {
                // There is already a request in flight. Queue up.
                List<Request<?>> stagedRequests = mWaitingRequests.get(cacheKey);
                if (stagedRequests == null) {
                    stagedRequests = new ArrayList<Request<?>>();
                }
                request.addMarker("waiting-for-response");
                stagedRequests.add(request);
                mWaitingRequests.put(cacheKey, stagedRequests);
                if (VolleyLog.DEBUG) {
                    VolleyLog.d("Request for cacheKey=%s is in flight, putting on hold.", cacheKey);
                }
                return true;
            } else {
                // Insert 'null' queue for this cacheKey, indicating there is now a request in
                // flight.
                mWaitingRequests.put(cacheKey, null);
                request.setNetworkRequestCompleteListener(this);
                if (VolleyLog.DEBUG) {
                    VolleyLog.d("new request, sending to network %s", cacheKey);
                }
                return false;
            }
        }
    }
}

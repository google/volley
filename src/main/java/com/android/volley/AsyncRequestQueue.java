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

package com.android.volley;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.volley.AsyncCache.OnGetCompleteCallback;
import com.android.volley.AsyncNetwork.OnRequestComplete;
import com.android.volley.Cache.Entry;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * An asynchronous request dispatch queue.
 *
 * <p>Add requests to the queue with {@link #add(Request)}. Once completed, responses will be
 * delivered on the main thread (unless a custom {@link ResponseDelivery} has been provided).
 *
 * <p><b>WARNING</b>: This API is experimental and subject to breaking changes. Please see
 * https://github.com/google/volley/wiki/Asynchronous-Volley for more details.
 */
public class AsyncRequestQueue extends RequestQueue {
    /** Default number of blocking threads to start. */
    private static final int DEFAULT_BLOCKING_THREAD_POOL_SIZE = 4;

    /**
     * AsyncCache used to retrieve and store responses.
     *
     * <p>{@code null} indicates use of blocking Cache.
     */
    @Nullable private final AsyncCache mAsyncCache;

    /** AsyncNetwork used to perform nework requests. */
    private final AsyncNetwork mNetwork;

    /** Executor for non-blocking tasks. */
    private ExecutorService mNonBlockingExecutor;

    /** Executor to be used for non-blocking tasks that need to be scheduled. */
    private ScheduledExecutorService mNonBlockingScheduledExecutor;

    /**
     * Executor for blocking tasks.
     *
     * <p>Some tasks in handling requests may not be easy to implement in a non-blocking way, such
     * as reading or parsing the response data. This executor is used to run these tasks.
     */
    private ExecutorService mBlockingExecutor;

    /**
     * This interface may be used by advanced applications to provide custom executors according to
     * their needs. Apps must create ExecutorServices dynamically given a blocking queue rather than
     * providing them directly so that Volley can provide a PriorityQueue which will prioritize
     * requests according to Request#getPriority.
     */
    private ExecutorFactory mExecutorFactory;

    /** Manage list of waiting requests and de-duplicate requests with same cache key. */
    private final WaitingRequestManager mWaitingRequestManager = new WaitingRequestManager(this);

    /**
     * Requests which have been queued before cache initialization has completed.
     *
     * <p>These requests are kicked off once cache initialization finishes. We avoid enqueuing them
     * sooner as the cache may not yet be ready.
     */
    private final List<Request<?>> mRequestsAwaitingCacheInitialization = new ArrayList<>();

    private volatile boolean mIsCacheInitialized = false;
    private final Object mCacheInitializationLock = new Object[0];

    /**
     * Sets all the variables, but processing does not begin until {@link #start()} is called.
     *
     * @param cache to use for persisting responses to disk. If an AsyncCache was provided, then
     *     this will be a {@link ThrowingCache}
     * @param network to perform HTTP requests
     * @param asyncCache to use for persisting responses to disk. May be null to indicate use of
     *     blocking cache
     * @param responseDelivery interface for posting responses and errors
     * @param executorFactory Interface to be used to provide custom executors according to the
     *     users needs.
     */
    private AsyncRequestQueue(
            Cache cache,
            AsyncNetwork network,
            @Nullable AsyncCache asyncCache,
            ResponseDelivery responseDelivery,
            ExecutorFactory executorFactory) {
        super(cache, network, /* threadPoolSize= */ 0, responseDelivery);
        mAsyncCache = asyncCache;
        mNetwork = network;
        mExecutorFactory = executorFactory;
    }

    /** Sets the executors and initializes the cache. */
    @Override
    public void start() {
        stop(); // Make sure any currently running threads are stopped

        // Create blocking / non-blocking executors and set them in the network and stack.
        mNonBlockingExecutor = mExecutorFactory.createNonBlockingExecutor(getBlockingQueue());
        mBlockingExecutor = mExecutorFactory.createBlockingExecutor(getBlockingQueue());
        mNonBlockingScheduledExecutor = mExecutorFactory.createNonBlockingScheduledExecutor();
        mNetwork.setBlockingExecutor(mBlockingExecutor);
        mNetwork.setNonBlockingExecutor(mNonBlockingExecutor);
        mNetwork.setNonBlockingScheduledExecutor(mNonBlockingScheduledExecutor);

        // Kick off cache initialization, which must complete before any requests can be processed.
        if (mAsyncCache != null) {
            mNonBlockingExecutor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            mAsyncCache.initialize(
                                    new AsyncCache.OnWriteCompleteCallback() {
                                        @Override
                                        public void onWriteComplete() {
                                            onCacheInitializationComplete();
                                        }
                                    });
                        }
                    });
        } else {
            mBlockingExecutor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            getCache().initialize();
                            mNonBlockingExecutor.execute(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            onCacheInitializationComplete();
                                        }
                                    });
                        }
                    });
        }
    }

    /** Shuts down and nullifies both executors */
    @Override
    public void stop() {
        if (mNonBlockingExecutor != null) {
            mNonBlockingExecutor.shutdownNow();
            mNonBlockingExecutor = null;
        }
        if (mBlockingExecutor != null) {
            mBlockingExecutor.shutdownNow();
            mBlockingExecutor = null;
        }
        if (mNonBlockingScheduledExecutor != null) {
            mNonBlockingScheduledExecutor.shutdownNow();
            mNonBlockingScheduledExecutor = null;
        }
    }

    /** Begins the request by sending it to the Cache or Network. */
    @Override
    <T> void beginRequest(Request<T> request) {
        // If the cache hasn't been initialized yet, add the request to a temporary queue to be
        // flushed once initialization completes.
        if (!mIsCacheInitialized) {
            synchronized (mCacheInitializationLock) {
                if (!mIsCacheInitialized) {
                    mRequestsAwaitingCacheInitialization.add(request);
                    return;
                }
            }
        }

        // If the request is uncacheable, send it over the network.
        if (request.shouldCache()) {
            if (mAsyncCache != null) {
                mNonBlockingExecutor.execute(new CacheTask<>(request));
            } else {
                mBlockingExecutor.execute(new CacheTask<>(request));
            }
        } else {
            sendRequestOverNetwork(request);
        }
    }

    private void onCacheInitializationComplete() {
        List<Request<?>> requestsToDispatch;
        synchronized (mCacheInitializationLock) {
            requestsToDispatch = new ArrayList<>(mRequestsAwaitingCacheInitialization);
            mRequestsAwaitingCacheInitialization.clear();
            mIsCacheInitialized = true;
        }

        // Kick off any requests that were queued while waiting for cache initialization.
        for (Request<?> request : requestsToDispatch) {
            beginRequest(request);
        }
    }

    @Override
    <T> void sendRequestOverNetwork(Request<T> request) {
        mNonBlockingExecutor.execute(new NetworkTask<>(request));
    }

    /** Runnable that gets an entry from the cache. */
    private class CacheTask<T> extends RequestTask<T> {
        CacheTask(Request<T> request) {
            super(request);
        }

        @Override
        public void run() {
            // If the request has been canceled, don't bother dispatching it.
            if (mRequest.isCanceled()) {
                mRequest.finish("cache-discard-canceled");
                return;
            }

            mRequest.addMarker("cache-queue-take");

            // Attempt to retrieve this item from cache.
            if (mAsyncCache != null) {
                mAsyncCache.get(
                        mRequest.getCacheKey(),
                        new OnGetCompleteCallback() {
                            @Override
                            public void onGetComplete(Entry entry) {
                                handleEntry(entry, mRequest);
                            }
                        });
            } else {
                Entry entry = getCache().get(mRequest.getCacheKey());
                handleEntry(entry, mRequest);
            }
        }
    }

    /** Helper method that handles the cache entry after getting it from the Cache. */
    private void handleEntry(final Entry entry, final Request<?> mRequest) {
        if (entry == null) {
            mRequest.addMarker("cache-miss");
            // Cache miss; send off to the network dispatcher.
            if (!mWaitingRequestManager.maybeAddToWaitingRequests(mRequest)) {
                sendRequestOverNetwork(mRequest);
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
            mRequest.addMarker("cache-hit-expired");
            mRequest.setCacheEntry(entry);
            if (!mWaitingRequestManager.maybeAddToWaitingRequests(mRequest)) {
                sendRequestOverNetwork(mRequest);
            }
            return;
        }

        // We have a cache hit; parse its data for delivery back to the request.
        mBlockingExecutor.execute(new CacheParseTask<>(mRequest, entry, currentTimeMillis));
    }

    private class CacheParseTask<T> extends RequestTask<T> {
        Cache.Entry entry;
        long startTimeMillis;

        CacheParseTask(Request<T> request, Cache.Entry entry, long startTimeMillis) {
            super(request);
            this.entry = entry;
            this.startTimeMillis = startTimeMillis;
        }

        @Override
        public void run() {
            mRequest.addMarker("cache-hit");
            Response<?> response =
                    mRequest.parseNetworkResponse(
                            new NetworkResponse(
                                    HttpURLConnection.HTTP_OK,
                                    entry.data,
                                    /* notModified= */ false,
                                    /* networkTimeMs= */ 0,
                                    entry.allResponseHeaders));
            mRequest.addMarker("cache-hit-parsed");

            if (!entry.refreshNeeded(startTimeMillis)) {
                // Completely unexpired cache hit. Just deliver the response.
                getResponseDelivery().postResponse(mRequest, response);
            } else {
                // Soft-expired cache hit. We can deliver the cached response,
                // but we need to also send the request to the network for
                // refreshing.
                mRequest.addMarker("cache-hit-refresh-needed");
                mRequest.setCacheEntry(entry);
                // Mark the response as intermediate.
                response.intermediate = true;

                if (!mWaitingRequestManager.maybeAddToWaitingRequests(mRequest)) {
                    // Post the intermediate response back to the user and have
                    // the delivery then forward the request along to the network.
                    getResponseDelivery()
                            .postResponse(
                                    mRequest,
                                    response,
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            sendRequestOverNetwork(mRequest);
                                        }
                                    });
                } else {
                    // request has been added to list of waiting requests
                    // to receive the network response from the first request once it
                    // returns.
                    getResponseDelivery().postResponse(mRequest, response);
                }
            }
        }
    }

    private class ParseErrorTask<T> extends RequestTask<T> {
        VolleyError volleyError;

        ParseErrorTask(Request<T> request, VolleyError volleyError) {
            super(request);
            this.volleyError = volleyError;
        }

        @Override
        public void run() {
            VolleyError parsedError = mRequest.parseNetworkError(volleyError);
            getResponseDelivery().postError(mRequest, parsedError);
            mRequest.notifyListenerResponseNotUsable();
        }
    }

    /** Runnable that performs the network request */
    private class NetworkTask<T> extends RequestTask<T> {
        NetworkTask(Request<T> request) {
            super(request);
        }

        @Override
        public void run() {
            // If the request was cancelled already, do not perform the network request.
            if (mRequest.isCanceled()) {
                mRequest.finish("network-discard-cancelled");
                mRequest.notifyListenerResponseNotUsable();
                return;
            }

            final long startTimeMs = SystemClock.elapsedRealtime();
            mRequest.addMarker("network-queue-take");

            // TODO: Figure out what to do with traffic stats tags. Can this be pushed to the
            // HTTP stack, or is it no longer feasible to support?

            // Perform the network request.
            mNetwork.performRequest(
                    mRequest,
                    new OnRequestComplete() {
                        @Override
                        public void onSuccess(final NetworkResponse networkResponse) {
                            mRequest.addMarker("network-http-complete");

                            // If the server returned 304 AND we delivered a response already,
                            // we're done -- don't deliver a second identical response.
                            if (networkResponse.notModified && mRequest.hasHadResponseDelivered()) {
                                mRequest.finish("not-modified");
                                mRequest.notifyListenerResponseNotUsable();
                                return;
                            }

                            // Parse the response here on the worker thread.
                            mBlockingExecutor.execute(
                                    new NetworkParseTask<>(mRequest, networkResponse));
                        }

                        @Override
                        public void onError(final VolleyError volleyError) {
                            volleyError.setNetworkTimeMs(
                                    SystemClock.elapsedRealtime() - startTimeMs);
                            mBlockingExecutor.execute(new ParseErrorTask<>(mRequest, volleyError));
                        }
                    });
        }
    }

    /** Runnable that parses a network response. */
    private class NetworkParseTask<T> extends RequestTask<T> {
        NetworkResponse networkResponse;

        NetworkParseTask(Request<T> request, NetworkResponse networkResponse) {
            super(request);
            this.networkResponse = networkResponse;
        }

        @Override
        public void run() {
            final Response<?> response = mRequest.parseNetworkResponse(networkResponse);
            mRequest.addMarker("network-parse-complete");

            // Write to cache if applicable.
            // TODO: Only update cache metadata instead of entire
            // record for 304s.
            if (mRequest.shouldCache() && response.cacheEntry != null) {
                if (mAsyncCache != null) {
                    mNonBlockingExecutor.execute(new CachePutTask<>(mRequest, response));
                } else {
                    mBlockingExecutor.execute(new CachePutTask<>(mRequest, response));
                }
            } else {
                finishRequest(mRequest, response, /* cached= */ false);
            }
        }
    }

    private class CachePutTask<T> extends RequestTask<T> {
        Response<?> response;

        CachePutTask(Request<T> request, Response<?> response) {
            super(request);
            this.response = response;
        }

        @Override
        public void run() {
            if (mAsyncCache != null) {
                mAsyncCache.put(
                        mRequest.getCacheKey(),
                        response.cacheEntry,
                        new AsyncCache.OnWriteCompleteCallback() {
                            @Override
                            public void onWriteComplete() {
                                finishRequest(mRequest, response, /* cached= */ true);
                            }
                        });
            } else {
                getCache().put(mRequest.getCacheKey(), response.cacheEntry);
                finishRequest(mRequest, response, /* cached= */ true);
            }
        }
    }

    /** Posts response and notifies listener */
    private void finishRequest(Request<?> mRequest, Response<?> response, boolean cached) {
        if (cached) {
            mRequest.addMarker("network-cache-written");
        }
        // Post the response back.
        mRequest.markDelivered();
        getResponseDelivery().postResponse(mRequest, response);
        mRequest.notifyListenerResponseReceived(response);
    }

    /**
     * Factory to create/provide the executors which Volley will use.
     *
     * <p>This class may be used by advanced applications to provide custom executors according to
     * their needs.
     *
     * <p>For applications which rely on setting request priority via {@link Request#getPriority}, a
     * task queue is provided which will prioritize requests of higher priority should the thread
     * pool itself be exhausted. If a shared pool is provided which does not make use of the given
     * queue, then lower-priority requests may have tasks executed before higher-priority requests
     * when enough tasks are in flight to fully saturate the shared pool.
     */
    public abstract static class ExecutorFactory {
        public abstract ExecutorService createNonBlockingExecutor(
                BlockingQueue<Runnable> taskQueue);

        public abstract ExecutorService createBlockingExecutor(BlockingQueue<Runnable> taskQueue);

        public abstract ScheduledExecutorService createNonBlockingScheduledExecutor();
    }

    /** Provides a BlockingQueue to be used to create executors. */
    private static PriorityBlockingQueue<Runnable> getBlockingQueue() {
        return new PriorityBlockingQueue<>(
                /* initialCapacity= */ 11,
                new Comparator<Runnable>() {
                    @Override
                    public int compare(Runnable r1, Runnable r2) {
                        // Vanilla runnables are prioritized first, then RequestTasks are ordered
                        // by the underlying Request.
                        if (r1 instanceof RequestTask) {
                            if (r2 instanceof RequestTask) {
                                return ((RequestTask<?>) r1).compareTo(((RequestTask<?>) r2));
                            }
                            return 1;
                        }
                        return r2 instanceof RequestTask ? -1 : 0;
                    }
                });
    }

    /**
     * Builder is used to build an instance of {@link AsyncRequestQueue} from values configured by
     * the setters.
     */
    public static class Builder {
        @Nullable private AsyncCache mAsyncCache = null;
        private final AsyncNetwork mNetwork;
        @Nullable private Cache mCache = null;
        @Nullable private ExecutorFactory mExecutorFactory = null;
        @Nullable private ResponseDelivery mResponseDelivery = null;

        public Builder(AsyncNetwork asyncNetwork) {
            if (asyncNetwork == null) {
                throw new IllegalArgumentException("Network cannot be null");
            }
            mNetwork = asyncNetwork;
        }

        /**
         * Sets the executor factory to be used by the AsyncRequestQueue. If this is not called,
         * Volley will create suitable private thread pools.
         */
        public Builder setExecutorFactory(ExecutorFactory executorFactory) {
            mExecutorFactory = executorFactory;
            return this;
        }

        /**
         * Sets the response deliver to be used by the AsyncRequestQueue. If this is not called, we
         * will default to creating a new {@link ExecutorDelivery} with the application's main
         * thread.
         */
        public Builder setResponseDelivery(ResponseDelivery responseDelivery) {
            mResponseDelivery = responseDelivery;
            return this;
        }

        /** Sets the AsyncCache to be used by the AsyncRequestQueue. */
        public Builder setAsyncCache(AsyncCache asyncCache) {
            mAsyncCache = asyncCache;
            return this;
        }

        /** Sets the Cache to be used by the AsyncRequestQueue. */
        public Builder setCache(Cache cache) {
            mCache = cache;
            return this;
        }

        /** Provides a default ExecutorFactory to use, if one is never set. */
        private ExecutorFactory getDefaultExecutorFactory() {
            return new ExecutorFactory() {
                @Override
                public ExecutorService createNonBlockingExecutor(
                        BlockingQueue<Runnable> taskQueue) {
                    return getNewThreadPoolExecutor(
                            /* maximumPoolSize= */ 1,
                            /* threadNameSuffix= */ "Non-BlockingExecutor",
                            taskQueue);
                }

                @Override
                public ExecutorService createBlockingExecutor(BlockingQueue<Runnable> taskQueue) {
                    return getNewThreadPoolExecutor(
                            /* maximumPoolSize= */ DEFAULT_BLOCKING_THREAD_POOL_SIZE,
                            /* threadNameSuffix= */ "BlockingExecutor",
                            taskQueue);
                }

                @Override
                public ScheduledExecutorService createNonBlockingScheduledExecutor() {
                    return new ScheduledThreadPoolExecutor(
                            /* corePoolSize= */ 0, getThreadFactory("ScheduledExecutor"));
                }

                private ThreadPoolExecutor getNewThreadPoolExecutor(
                        int maximumPoolSize,
                        final String threadNameSuffix,
                        BlockingQueue<Runnable> taskQueue) {
                    return new ThreadPoolExecutor(
                            /* corePoolSize= */ 0,
                            /* maximumPoolSize= */ maximumPoolSize,
                            /* keepAliveTime= */ 60,
                            /* unit= */ TimeUnit.SECONDS,
                            taskQueue,
                            getThreadFactory(threadNameSuffix));
                }

                private ThreadFactory getThreadFactory(final String threadNameSuffix) {
                    return new ThreadFactory() {
                        @Override
                        public Thread newThread(@NonNull Runnable runnable) {
                            Thread t = Executors.defaultThreadFactory().newThread(runnable);
                            t.setName("Volley-" + threadNameSuffix);
                            return t;
                        }
                    };
                }
            };
        }

        public AsyncRequestQueue build() {
            // If neither cache is set by the caller, throw an illegal argument exception.
            if (mCache == null && mAsyncCache == null) {
                throw new IllegalArgumentException("You must set one of the cache objects");
            }
            if (mCache == null) {
                // if no cache is provided, we will provide one that throws
                // UnsupportedOperationExceptions to pass into the parent class.
                mCache = new ThrowingCache();
            }
            if (mResponseDelivery == null) {
                mResponseDelivery = new ExecutorDelivery(new Handler(Looper.getMainLooper()));
            }
            if (mExecutorFactory == null) {
                mExecutorFactory = getDefaultExecutorFactory();
            }
            return new AsyncRequestQueue(
                    mCache, mNetwork, mAsyncCache, mResponseDelivery, mExecutorFactory);
        }
    }

    /** A cache that throws an error if a method is called. */
    private static class ThrowingCache implements Cache {
        @Override
        public Entry get(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void put(String key, Entry entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void initialize() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void invalidate(String key, boolean fullExpire) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }
    }
}

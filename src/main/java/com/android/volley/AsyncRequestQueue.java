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
import com.android.volley.toolbox.ThrowingCache;
import java.net.HttpURLConnection;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncRequestQueue extends RequestQueue {
    /** Default number of blocking threads to start. */
    private static final int DEFAULT_BLOCKING_THREAD_POOL_SIZE = 4;

    /** AsyncCache used to retrieve and store responses. Null indicates use of blocking Cache. */
    @Nullable private final AsyncCache mAsyncCache;

    /** AsyncNetwork used to perform nework requests. */
    private final AsyncNetwork mNetwork;

    /**
     * Cache used to retrieve and store responses. Only use if an AsyncCache cannot be provided due
     * to lower API.
     */
    private final Cache mCache;

    /** Executor for non-blocking tasks. */
    private ExecutorService mNonBlockingExecutor;

    /** Blocking queue to be used to created Executors */
    private static final PriorityBlockingQueue<Runnable> mBlockingQueue =
            new PriorityBlockingQueue<>(
                    11,
                    new Comparator<Runnable>() {
                        @Override
                        public int compare(Runnable r1, Runnable r2) {
                            // Vanilla runnables are prioritized first, then RequestTasks are
                            // ordered
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
    private final WaitingRequestManager mWaitingRequestManager;

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
            @Nullable ExecutorFactory executorFactory) {
        super(cache, network, 0, responseDelivery);
        mAsyncCache = asyncCache;
        mCache = cache;
        mNetwork = network;
        mExecutorFactory = executorFactory;
        mWaitingRequestManager = new WaitingRequestManager(this);
    }

    /** Sets the executors and initializes the cache. */
    @Override
    public void start() {
        stop(); // Make sure any currently running threads are stopped

        // Create blocking / non-blocking executors and set them in the network and stack.
        mNonBlockingExecutor = mExecutorFactory.createNonBlockingExecutor(mBlockingQueue);
        mBlockingExecutor = mExecutorFactory.createBlockingExecutor(mBlockingQueue);
        mNetwork.setBlockingExecutor(mBlockingExecutor);
        mNetwork.setNonBlockingExecutor(mNonBlockingExecutor);

        mNonBlockingExecutor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        // This is intentionally blocking, because we don't want to process any
                        // requests until the cache is initialized.
                        if (mAsyncCache != null) {
                            final CountDownLatch latch = new CountDownLatch(1);
                            mAsyncCache.initialize(
                                    new AsyncCache.OnWriteCompleteCallback() {
                                        @Override
                                        public void onWriteComplete() {
                                            latch.countDown();
                                        }
                                    });
                            try {
                                latch.await();
                            } catch (InterruptedException e) {
                                VolleyLog.e(
                                        e, "Thread was interrupted while initializing the cache.");
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                        } else {
                            mCache.initialize();
                        }
                    }
                });
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
    }

    /** Begins the request by sending it to the Cache or Network. */
    @Override
    <T> void beginRequest(Request<T> request) {
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

    @Override
    <T> void sendRequestOverNetwork(Request<T> request) {
        mNonBlockingExecutor.execute(new NetworkTask<>(request));
    }

    /** Abstract runnable that's a task to be completed by the RequestQueue. */
    private abstract static class RequestTask<T> implements Runnable {
        final Request<T> mRequest;

        RequestTask(Request<T> request) {
            mRequest = request;
        }

        @SuppressWarnings("unchecked")
        public int compareTo(RequestTask<?> other) {
            return mRequest.compareTo((Request<T>) other.mRequest);
        }
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
                Entry entry = mCache.get(mRequest.getCacheKey());
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

        // If it is completely expired, just send it to the network.
        if (entry.isExpired()) {
            mRequest.addMarker("cache-hit-expired");
            mRequest.setCacheEntry(entry);
            if (!mWaitingRequestManager.maybeAddToWaitingRequests(mRequest)) {
                sendRequestOverNetwork(mRequest);
            }
            return;
        }

        // We have a cache hit; parse its data for delivery back to the request.
        mBlockingExecutor.execute(
                new Runnable() {
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

                        if (!entry.refreshNeeded()) {
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
                });
    }

    /** Runnable that performs the network request */
    private class NetworkTask<T> extends RequestTask<T> {
        NetworkTask(Request<T> request) {
            super(request);
        }

        @Override
        public void run() {
            // If the request was cancelled already, do not perform the
            // network request.
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
                            mBlockingExecutor.execute(new ParseTask<>(mRequest, networkResponse));
                        }

                        @Override
                        public void onError(final VolleyError volleyError) {
                            volleyError.setNetworkTimeMs(
                                    SystemClock.elapsedRealtime() - startTimeMs);
                            mBlockingExecutor.execute(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            VolleyError parsedError =
                                                    mRequest.parseNetworkError(volleyError);
                                            getResponseDelivery().postError(mRequest, parsedError);
                                            mRequest.notifyListenerResponseNotUsable();
                                        }
                                    });
                        }
                    });
        }
    }

    /** Runnable that parses a network response. */
    private class ParseTask<T> extends RequestTask<T> {
        NetworkResponse networkResponse;

        ParseTask(Request<T> request, NetworkResponse networkResponse) {
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
                    mNonBlockingExecutor.execute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    mAsyncCache.put(
                                            mRequest.getCacheKey(),
                                            response.cacheEntry,
                                            new AsyncCache.OnWriteCompleteCallback() {
                                                @Override
                                                public void onWriteComplete() {
                                                    finishRequest(response, true);
                                                }
                                            });
                                }
                            });
                } else {
                    mCache.put(mRequest.getCacheKey(), response.cacheEntry);
                    finishRequest(response, true);
                }
            } else {
                finishRequest(response, false);
            }
        }

        private void finishRequest(Response<?> response, boolean cached) {
            if (cached) {
                mRequest.addMarker("network-cache-written");
            }
            // Post the response back.
            mRequest.markDelivered();
            getResponseDelivery().postResponse(mRequest, response);
            mRequest.notifyListenerResponseReceived(response);
        }
    }

    /**
     * This interface may be used by advanced applications to provide custom executors according to
     * their needs. Apps must create ExecutorServices dynamically given a blocking queue rather than
     * providing them directly so that Volley can provide a PriorityQueue which will prioritize
     * requests according to Request#getPriority.
     */
    public interface ExecutorFactory {
        ExecutorService createNonBlockingExecutor(BlockingQueue<?> taskQueue);

        ExecutorService createBlockingExecutor(BlockingQueue<?> taskQueue);
    }

    /**
     * Builder is used to build an instance of {@link AsyncRequestQueue} from values configured by
     * the setters.
     */
    public static class Builder {
        private AsyncCache mAsyncCache;
        private final AsyncNetwork mNetwork;
        private Cache mCache;
        private ExecutorFactory mExecutorFactory;
        private ResponseDelivery mResponseDelivery;

        public Builder(AsyncNetwork asyncNetwork) {
            if (asyncNetwork == null) {
                throw new IllegalArgumentException("Network cannot be null");
            }
            mNetwork = asyncNetwork;
            mExecutorFactory = null;
            mResponseDelivery = new ExecutorDelivery(new Handler(Looper.getMainLooper()));
            mAsyncCache = null;
            mCache = null;
        }

        /**
         * Sets the executor factory to be used by the AsyncRequestQueue. If this is not called, we
         * will default to calling getDefaultExecutorFactory.
         */
        public Builder setExecutorFactory(ExecutorFactory executorFactory) {
            mExecutorFactory = executorFactory;
            return this;
        }

        /**
         * Sets the response deliver to be used by the AsyncRequestQueue. If this is not called, we
         * will default to creating a new {@link ExecutorDelivery} with the applications main
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
                public ExecutorService createNonBlockingExecutor(BlockingQueue<?> taskQueue) {
                    return new ThreadPoolExecutor(
                            /* corePoolSize= */ 0,
                            /* maximumPoolSize= */ 1,
                            /* keepAliveTime= */ 60,
                            /* unit= */ TimeUnit.SECONDS,
                            mBlockingQueue,
                            new ThreadFactory() {
                                @Override
                                public Thread newThread(@NonNull Runnable runnable) {
                                    Thread t = Executors.defaultThreadFactory().newThread(runnable);
                                    t.setName("Volley-NonBlockingExecutor");
                                    return t;
                                }
                            });
                }

                @Override
                public ExecutorService createBlockingExecutor(BlockingQueue<?> taskQueue) {
                    return new ThreadPoolExecutor(
                            /* corePoolSize= */ 0,
                            /* maximumPoolSize= */ DEFAULT_BLOCKING_THREAD_POOL_SIZE,
                            /* keepAliveTime= */ 60,
                            /* unit= */ TimeUnit.SECONDS,
                            mBlockingQueue,
                            new ThreadFactory() {
                                @Override
                                public Thread newThread(@NonNull Runnable runnable) {
                                    Thread t = Executors.defaultThreadFactory().newThread(runnable);
                                    t.setName("Volley-BlockingExecutor");
                                    return t;
                                }
                            });
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
}

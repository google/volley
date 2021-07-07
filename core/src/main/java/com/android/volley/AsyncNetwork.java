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

import androidx.annotation.RestrictTo;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An asynchronous implementation of {@link Network} to perform requests.
 *
 * <p><b>WARNING</b>: This API is experimental and subject to breaking changes. Please see
 * https://github.com/google/volley/wiki/Asynchronous-Volley for more details.
 */
public abstract class AsyncNetwork implements Network {
    private ExecutorService mBlockingExecutor;
    private ExecutorService mNonBlockingExecutor;
    private ScheduledExecutorService mNonBlockingScheduledExecutor;

    protected AsyncNetwork() {}

    /** Interface for callback to be called after request is processed. */
    public interface OnRequestComplete {
        /** Method to be called after successful network request. */
        void onSuccess(NetworkResponse networkResponse);

        /** Method to be called after unsuccessful network request. */
        void onError(VolleyError volleyError);
    }

    /**
     * Non-blocking method to perform the specified request.
     *
     * @param request Request to process
     * @param callback to be called once NetworkResponse is received
     */
    public abstract void performRequest(Request<?> request, OnRequestComplete callback);

    /**
     * Blocking method to perform network request.
     *
     * @param request Request to process
     * @return response retrieved from the network
     * @throws VolleyError in the event of an error
     */
    @Override
    public NetworkResponse performRequest(Request<?> request) throws VolleyError {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<NetworkResponse> response = new AtomicReference<>();
        final AtomicReference<VolleyError> error = new AtomicReference<>();
        performRequest(
                request,
                new OnRequestComplete() {
                    @Override
                    public void onSuccess(NetworkResponse networkResponse) {
                        response.set(networkResponse);
                        latch.countDown();
                    }

                    @Override
                    public void onError(VolleyError volleyError) {
                        error.set(volleyError);
                        latch.countDown();
                    }
                });
        try {
            latch.await();
        } catch (InterruptedException e) {
            VolleyLog.e(e, "while waiting for CountDownLatch");
            Thread.currentThread().interrupt();
            throw new VolleyError(e);
        }

        if (response.get() != null) {
            return response.get();
        } else if (error.get() != null) {
            throw error.get();
        } else {
            throw new VolleyError("Neither response entry was set");
        }
    }

    /**
     * This method sets the non blocking executor to be used by the network for non-blocking tasks.
     *
     * <p>This method must be called before performing any requests.
     */
    @RestrictTo({RestrictTo.Scope.LIBRARY_GROUP})
    public void setNonBlockingExecutor(ExecutorService executor) {
        mNonBlockingExecutor = executor;
    }

    /**
     * This method sets the blocking executor to be used by the network for potentially blocking
     * tasks.
     *
     * <p>This method must be called before performing any requests.
     */
    @RestrictTo({RestrictTo.Scope.LIBRARY_GROUP})
    public void setBlockingExecutor(ExecutorService executor) {
        mBlockingExecutor = executor;
    }

    /**
     * This method sets the scheduled executor to be used by the network for non-blocking tasks to
     * be scheduled.
     *
     * <p>This method must be called before performing any requests.
     */
    @RestrictTo({RestrictTo.Scope.LIBRARY_GROUP})
    public void setNonBlockingScheduledExecutor(ScheduledExecutorService executor) {
        mNonBlockingScheduledExecutor = executor;
    }

    /** Gets blocking executor to perform any potentially blocking tasks. */
    protected ExecutorService getBlockingExecutor() {
        return mBlockingExecutor;
    }

    /** Gets non-blocking executor to perform any non-blocking tasks. */
    protected ExecutorService getNonBlockingExecutor() {
        return mNonBlockingExecutor;
    }

    /** Gets scheduled executor to perform any non-blocking tasks that need to be scheduled. */
    protected ScheduledExecutorService getNonBlockingScheduledExecutor() {
        return mNonBlockingScheduledExecutor;
    }
}

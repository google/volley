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
import com.android.volley.toolbox.AsyncHttpStack;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/** An asynchronous implementation of {@link Network} to perform requests. */
public abstract class AsyncNetwork implements Network {
    private final AsyncHttpStack mAsyncStack;
    private ExecutorService mBlockingExecutor;
    private ExecutorService mNonBlockingExecutor;

    protected AsyncNetwork(AsyncHttpStack stack) {
        mAsyncStack = stack;
    }

    public interface OnRequestComplete {
        void onSuccess(NetworkResponse networkResponse);

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
     * This method sets the non blocking executor to be used by the stack for non-blocking tasks.
     * This method must be called before performing any requests.
     */
    @RestrictTo({RestrictTo.Scope.LIBRARY_GROUP})
    public void setNonBlockingExecutorForStack(ExecutorService executor) {
        mNonBlockingExecutor = executor;
        mAsyncStack.setNonBlockingExecutor(executor);
    }

    /**
     * This method sets the blocking executor to be used by the network and stack for potentially
     * blocking tasks. This method must be called before performing any requests.
     */
    @RestrictTo({RestrictTo.Scope.LIBRARY_GROUP})
    public void setBlockingExecutor(ExecutorService executor) {
        mBlockingExecutor = executor;
        mAsyncStack.setBlockingExecutor(executor);
    }

    protected ExecutorService getBlockingExecutor() {
        return mBlockingExecutor;
    }

    protected ExecutorService getNonBlockingExecutor() {
        return mNonBlockingExecutor;
    }

    protected AsyncHttpStack getHttpStack() {
        return mAsyncStack;
    }
}

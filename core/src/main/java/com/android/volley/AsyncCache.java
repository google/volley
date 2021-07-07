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

import androidx.annotation.Nullable;

/**
 * Asynchronous equivalent to the {@link Cache} interface.
 *
 * <p><b>WARNING</b>: This API is experimental and subject to breaking changes. Please see
 * https://github.com/google/volley/wiki/Asynchronous-Volley for more details.
 */
public abstract class AsyncCache {

    public interface OnGetCompleteCallback {
        /**
         * Invoked when the read from the cache is complete.
         *
         * @param entry The entry read from the cache, or null if the read failed or the key did not
         *     exist in the cache.
         */
        void onGetComplete(@Nullable Cache.Entry entry);
    }

    /**
     * Retrieves an entry from the cache and sends it back through the {@link
     * OnGetCompleteCallback#onGetComplete} function
     *
     * @param key Cache key
     * @param callback Callback that will be notified when the information has been retrieved
     */
    public abstract void get(String key, OnGetCompleteCallback callback);

    public interface OnWriteCompleteCallback {
        /** Invoked when the cache operation is complete */
        void onWriteComplete();
    }

    /**
     * Writes a {@link Cache.Entry} to the cache, and calls {@link
     * OnWriteCompleteCallback#onWriteComplete} after the operation is finished.
     *
     * @param key Cache key
     * @param entry The entry to be written to the cache
     * @param callback Callback that will be notified when the information has been written
     */
    public abstract void put(String key, Cache.Entry entry, OnWriteCompleteCallback callback);

    /**
     * Clears the cache. Deletes all cached files from disk. Calls {@link
     * OnWriteCompleteCallback#onWriteComplete} after the operation is finished.
     */
    public abstract void clear(OnWriteCompleteCallback callback);

    /**
     * Initializes the cache and calls {@link OnWriteCompleteCallback#onWriteComplete} after the
     * operation is finished.
     */
    public abstract void initialize(OnWriteCompleteCallback callback);

    /**
     * Invalidates an entry in the cache and calls {@link OnWriteCompleteCallback#onWriteComplete}
     * after the operation is finished.
     *
     * @param key Cache key
     * @param fullExpire True to fully expire the entry, false to soft expire
     * @param callback Callback that's invoked once the entry has been invalidated
     */
    public abstract void invalidate(
            String key, boolean fullExpire, OnWriteCompleteCallback callback);

    /**
     * Removes a {@link Cache.Entry} from the cache, and calls {@link
     * OnWriteCompleteCallback#onWriteComplete} after the operation is finished.
     *
     * @param key Cache key
     * @param callback Callback that's invoked once the entry has been removed
     */
    public abstract void remove(String key, OnWriteCompleteCallback callback);
}

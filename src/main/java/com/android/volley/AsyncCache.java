package com.android.volley;

import androidx.annotation.Nullable;

/** Asynchronous equivalent to the {@link Cache} interface. */
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

    public interface OnCompleteCallback {
        /** Invoked when the cache operation is complete */
        void onComplete();
    }

    /**
     * Writes a {@link Cache.Entry} to the cache, and calls {@link OnCompleteCallback#onComplete}
     * after the operation is finished.
     *
     * @param key Cache key
     * @param entry The entry to be written to the cache
     * @param callback Callback that will be notified when the information has been written
     */
    public abstract void put(String key, Cache.Entry entry, OnCompleteCallback callback);

    /** Clears the cache. Deletes all cached files from disk. */
    public abstract void clear(OnCompleteCallback callback);

    /** Initializes the cache. */
    public abstract void initialize(OnCompleteCallback callback);

    /**
     * Invalidates an entry in the cache.
     *
     * @param key Cache key
     * @param fullExpire True to fully expire the entry, false to soft expire
     * @param callback Callback that's invoked once the entry has been invalidated
     */
    public abstract void invalidate(String key, boolean fullExpire, OnCompleteCallback callback);

    /**
     * Removes a {@link Cache.Entry} from the cache, and calls {@link OnCompleteCallback#onComplete}
     * after the operation is finished.
     *
     * @param key Cache key
     * @param callback Callback that's invoked once the entry has been removed
     */
    public abstract void remove(String key, OnCompleteCallback callback);
}

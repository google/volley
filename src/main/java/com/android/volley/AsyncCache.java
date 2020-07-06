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
        /** Invoked when the operation on the cache is complete */
        void onComplete();
    }

    /**
     * Puts an entry into the cache and then calls {@link OnCompleteCallback#onComplete} when it is
     * finished.
     *
     * @param key Cache key
     * @param callback Callback that will be notified when the information has been retrieved
     */
    public abstract void put(String key, Cache.Entry entry, OnCompleteCallback callback);

    /**
     * Initializes the asynchronous cache and then calls {@link OnCompleteCallback#onComplete} when
     * it is finished.
     *
     * @param callback Callback that will be notified when the cache has been initialized
     */
    public abstract void initialize(OnCompleteCallback callback);

    /**
     * Either fully or soft expires an entry in the cache, then calls {@link
     * OnCompleteCallback#onComplete} function.
     *
     * @param key Cache key,
     * @param fullExpire True to fully expire the entry, false to soft expire
     * @param callback Callback that will be notified when the entry has been invalidated
     */
    public abstract void invalidate(String key, boolean fullExpire, OnCompleteCallback callback);

    /**
     * Removes an entry from the cache and calls {@link OnCompleteCallback#onComplete} function when
     * it's finished
     *
     * @param key Cache key
     * @param callback Callback that will be notified when the information has been retrieved
     */
    public abstract void remove(String key, OnCompleteCallback callback);
}

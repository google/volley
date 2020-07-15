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

    public interface OnPutCompleteCallback {
        /** Invoked when the put to the cache is complete. */
        void onPutComplete();
    }

    /**
     * Writes a {@link Cache.Entry} to the cache, and calls {@link
     * OnPutCompleteCallback#onPutComplete} after the operation is finished.
     *
     * @param key Cache key
     * @param entry The entry to be written to the cache
     * @param callback Callback that will be notified when the information has been written
     */
    public abstract void put(String key, Cache.Entry entry, OnPutCompleteCallback callback);

    // TODO(#181): Implement the rest.
}

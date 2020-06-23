package com.android.volley;

import androidx.annotation.Nullable;

/** Asynchronous equivalent to the {@link Cache} interface. */
public abstract class AsyncCache {

    public interface OnGetCompleteCallback {
        /**
         * Callback that's invoked when the read from the cache is complete.
         *
         * @param entry The entry read from the cache, or null if the read failed or the key did not
         *     exist in the cache.
         */
        void onGetComplete(@Nullable Cache.Entry entry);
    }

    /**
     * Retrieves an entry from the cache and send it back through the callback function
     *
     * @param key Cache key
     * @param callback Callback function that sets the retrieved entry and lets the program know it
     *     is finished retrieving information.
     */
    public abstract void get(String key, OnGetCompleteCallback callback);

    // TODO(#181): Implement the rest.
}

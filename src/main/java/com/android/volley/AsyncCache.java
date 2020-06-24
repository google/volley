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

    // TODO(#181): Implement the rest.
}

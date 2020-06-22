package com.android.volley;

import androidx.annotation.Nullable;

/**
 * AsyncCache is an abstract class which implements the Cache interface for backwards compatibility,
 * although it is not meant to be used as a Cache directly. For each method in the Cache interface
 * (get, put, initialize, invalidate, remove, clear), it defines a corresponding abstract version
 * which takes a Callback to be invoked upon completion instead of returning the result directly.
 * The methods in the cache interface are implemented by making calls to the asynchronous method and
 * blocking until they complete.
 */
public abstract class AsyncCache {

    public interface OnGetCompleteCallback {
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

    public interface OnPutCompleteCallback {
        void onPutComplete(boolean success);
    }

    /**
     * Puts an entry into the cache
     *
     * @param key Cache key
     * @param entry Entry to put in the Cache.
     * @param callback Callback function that says whether it was a success or not
     */
    public abstract void put(String key, Cache.Entry entry, OnPutCompleteCallback callback);

    // TODO(#181): Implement the rest.
}

package com.android.volley.toolbox;

import com.android.volley.AsyncCache;
import com.android.volley.Cache;

/**
 * An AsyncCache that doesn't cache anything.
 *
 * <p><b>WARNING</b>: This API is experimental and subject to breaking changes. Please see
 * https://github.com/google/volley/wiki/Asynchronous-Volley for more details.
 */
public class NoAsyncCache extends AsyncCache {
    @Override
    public void get(String key, OnGetCompleteCallback callback) {
        callback.onGetComplete(null);
    }

    @Override
    public void put(String key, Cache.Entry entry, OnWriteCompleteCallback callback) {
        callback.onWriteComplete();
    }

    @Override
    public void clear(OnWriteCompleteCallback callback) {
        callback.onWriteComplete();
    }

    @Override
    public void initialize(OnWriteCompleteCallback callback) {
        callback.onWriteComplete();
    }

    @Override
    public void invalidate(String key, boolean fullExpire, OnWriteCompleteCallback callback) {
        callback.onWriteComplete();
    }

    @Override
    public void remove(String key, OnWriteCompleteCallback callback) {
        callback.onWriteComplete();
    }
}

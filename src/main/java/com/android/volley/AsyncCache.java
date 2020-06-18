package com.android.volley;

import androidx.annotation.Nullable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AsyncCache is an abstract class which implements the Cache interface for backwards compatibility,
 * although it is not meant to be used as a Cache directly. For each method in the Cache interface
 * (get, put, initialize, invalidate, remove, clear), it defines a corresponding abstract version
 * which takes a Callback to be invoked upon completion instead of returning the result directly.
 * The methods in the cache interface are implemented by making calls to the asynchronous method and
 * blocking until they complete.
 */
public abstract class AsyncCache implements Cache {

    public interface OnGetCompleteCallback {
        void onGetComplete(@Nullable Entry entry);
    }

    /**
     * Retrieves an entry from the cache and send it back through the callback function
     *
     * @param key Cache key
     * @param callback Callback function that sets the retrieved entry and lets the program know it
     *     is finished retrieving information.
     */
    public abstract void get(String key, OnGetCompleteCallback callback);

    @Nullable
    @Override
    public final Entry get(String key) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Entry> entryRef = new AtomicReference<>();
        get(
                key,
                new OnGetCompleteCallback() {
                    @Override
                    public void onGetComplete(Entry entry) {
                        entryRef.set(entry);
                        latch.countDown();
                    }
                });
        try {
            latch.await();
            return entryRef.get();
        } catch (InterruptedException e) {
            VolleyLog.d("%s: %s", key, e.toString());
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // TODO(#181): Implement the rest.

    public interface OnPutCompleteCallback {
        void onPutComplete(boolean success);
    }

    public abstract void put(String key, Entry entry, OnPutCompleteCallback callback);

    @Override
    public final void put(String key, Entry entry) {
        final CountDownLatch latch = new CountDownLatch(1);
        put(
                key,
                entry,
                new OnPutCompleteCallback() {
                    @Override
                    public void onPutComplete(boolean success) {
                        if (success) {
                            latch.countDown();
                        } else {
                            return;
                        }
                    }
                });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
    }

    @Override
    public void initialize() {}

    @Override
    public void invalidate(String key, boolean fullExpire) {}

    @Override
    public void remove(String key) {}

    @Override
    public void clear() {}
}

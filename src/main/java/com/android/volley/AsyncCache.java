package com.android.volley;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AsyncCache implements Cache {

    public interface OnGetCompleteCallback {
        void onGetComplete(Entry entry);
    }

    public abstract void get(String key, OnGetCompleteCallback callback);

    // TODO: Handle callback never being invoked? Match behavior when sync cache throws
    // RuntimeException.

    @Override
    public Entry get(String key) {
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
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // TODO: Implement the rest.

    public interface OnPutCompleteCallback {
        void onPutComplete(boolean success);
    }

    public abstract void put(String key, Entry entry, OnPutCompleteCallback callback);

    @Override
    public void put(String key, Entry entry) {
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

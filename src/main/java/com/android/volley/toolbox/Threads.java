package com.android.volley.toolbox;

import android.os.Looper;

final class Threads {
    private Threads() {}

    static void throwIfNotOnMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Must be invoked from the main thread.");
        }
    }
}

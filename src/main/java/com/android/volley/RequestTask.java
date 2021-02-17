package com.android.volley;

/**
 * Abstract runnable that's a task to be completed by the RequestQueue.
 *
 * <p><b>WARNING</b>: This API is experimental and subject to breaking changes. Please see
 * https://github.com/google/volley/wiki/Asynchronous-Volley for more details.
 */
public abstract class RequestTask<T> implements Runnable {
    final Request<T> mRequest;

    public RequestTask(Request<T> request) {
        mRequest = request;
    }

    @SuppressWarnings("unchecked")
    public int compareTo(RequestTask<?> other) {
        return mRequest.compareTo((Request<T>) other.mRequest);
    }
}

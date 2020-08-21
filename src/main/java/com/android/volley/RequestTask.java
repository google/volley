package com.android.volley;

/** Abstract runnable that's a task to be completed by the RequestQueue. */
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

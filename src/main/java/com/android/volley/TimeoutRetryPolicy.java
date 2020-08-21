package com.android.volley;

/**
 * Retry policy for a request.
 *
 * <p>Same as {@link RetryPolicy}, except instead of triggering the retry attempt instantly, the
 * user can set a timeout in milliseconds to wait before retrying the request.
 */
public interface TimeoutRetryPolicy extends RetryPolicy {
    /** Returns the timeout before attempting to retry the request in ms. */
    int getTimeoutBeforeRetryMs();
}

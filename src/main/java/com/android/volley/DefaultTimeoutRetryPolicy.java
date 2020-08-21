package com.android.volley;

/** Default retry policy for requests that want a timeout before retrying an error. */
public class DefaultTimeoutRetryPolicy extends DefaultRetryPolicy implements TimeoutRetryPolicy {
    /** The timeout before retrying a failed request in milliseconds. */
    int mTimeoutBeforeRetryMs;

    /**
     * Constructs a new retry policy
     *
     * @param timeoutBeforeRetryMs is the timeout before retrying a failed request in milliseconds.
     */
    public DefaultTimeoutRetryPolicy(int timeoutBeforeRetryMs) {
        super();
        if (timeoutBeforeRetryMs < 0) {
            throw new IllegalArgumentException("timeout cannot be negative");
        }
        mTimeoutBeforeRetryMs = timeoutBeforeRetryMs;
    }

    /**
     * Constructs a new retry policy.
     *
     * @param initialTimeoutMs The initial timeout for the policy.
     * @param maxNumRetries The maximum number of retries.
     * @param backoffMultiplier Backoff multiplier for the policy.
     * @param timeoutBeforeRetryMs Timeout before retrying a failed request in milliseconds.
     */
    public DefaultTimeoutRetryPolicy(
            int initialTimeoutMs,
            int maxNumRetries,
            float backoffMultiplier,
            int timeoutBeforeRetryMs) {
        super(initialTimeoutMs, maxNumRetries, backoffMultiplier);
        mTimeoutBeforeRetryMs = timeoutBeforeRetryMs;
    }

    /** Returns the timeout before attempting to retry the request in ms. */
    @Override
    public int getTimeoutBeforeRetryMs() {
        return mTimeoutBeforeRetryMs;
    }
}

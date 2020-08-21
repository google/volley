/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

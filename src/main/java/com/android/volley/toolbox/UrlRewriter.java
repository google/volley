package com.android.volley.toolbox;

import androidx.annotation.Nullable;

/** An interface for transforming URLs before use. */
public interface UrlRewriter {
    /**
     * Returns a URL to use instead of the provided one, or null to indicate this URL should not be
     * used at all.
     */
    @Nullable
    String rewriteUrl(String originalUrl);
}

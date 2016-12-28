/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.volley.utils;

import com.android.volley.Cache;

import java.util.Random;

public class CacheTestUtils {

    /**
     * Makes a random cache entry.
     * @param data Data to use, or null to use random data
     * @param isExpired Whether the TTLs should be set such that this entry is expired
     * @param needsRefresh Whether the TTLs should be set such that this entry needs refresh
     */
    public static Cache.Entry makeRandomCacheEntry(
            byte[] data, boolean isExpired, boolean needsRefresh) {
        Random random = new Random();
        Cache.Entry entry = new Cache.Entry();
        if (data != null) {
            entry.data = data;
        } else {
            entry.data = new byte[random.nextInt(1024)];
        }
        entry.etag = String.valueOf(random.nextLong());
        entry.lastModified = random.nextLong();
        entry.ttl = isExpired ? 0 : Long.MAX_VALUE;
        entry.softTtl = needsRefresh ? 0 : Long.MAX_VALUE;
        return entry;
    }

    /**
     * Like {@link #makeRandomCacheEntry(byte[], boolean, boolean)} but
     * defaults to an unexpired entry.
     */
    public static Cache.Entry makeRandomCacheEntry(byte[] data) {
        return makeRandomCacheEntry(data, false, false);
    }
}

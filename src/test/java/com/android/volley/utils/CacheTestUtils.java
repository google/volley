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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import com.android.volley.Cache;
import java.util.Random;

public class CacheTestUtils {

    /**
     * Makes a random cache entry.
     *
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
     * Like {@link #makeRandomCacheEntry(byte[], boolean, boolean)} but defaults to an unexpired
     * entry.
     */
    public static Cache.Entry makeRandomCacheEntry(byte[] data) {
        return makeRandomCacheEntry(data, false, false);
    }

    public static void assertThatEntriesAreEqual(Cache.Entry actual, Cache.Entry expected) {
        assertNotNull(actual);
        assertThat(actual.data, is(equalTo(expected.data)));
        assertThat(actual.etag, is(equalTo(expected.etag)));
        assertThat(actual.lastModified, is(equalTo(expected.lastModified)));
        assertThat(actual.responseHeaders, is(equalTo(expected.responseHeaders)));
        assertThat(actual.serverDate, is(equalTo(expected.serverDate)));
        assertThat(actual.softTtl, is(equalTo(expected.softTtl)));
        assertThat(actual.ttl, is(equalTo(expected.ttl)));
    }

    public static Cache.Entry randomData(int length) {
        Cache.Entry entry = new Cache.Entry();
        byte[] data = new byte[length];
        new Random(42).nextBytes(data); // explicit seed for reproducible results
        entry.data = data;
        return entry;
    }

    public static int getEntrySizeOnDisk(String key) {
        // Header size is:
        // 4 bytes for magic int
        // 8 + len(key) bytes for key (long length)
        // 8 bytes for etag (long length + 0 characters)
        // 32 bytes for serverDate, lastModified, ttl, and softTtl longs
        // 4 bytes for length of header list int
        // == 56 + len(key) bytes total.
        return 56 + key.length();
    }
}

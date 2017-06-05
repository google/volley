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

package com.android.volley.toolbox;

import android.os.SystemClock;

import com.android.volley.Cache;
import com.android.volley.VolleyLog;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cache implementation that caches files directly onto the hard disk in the specified
 * directory. The default disk usage size is 5MB, but is configurable.
 */
@SuppressWarnings("WeakerAccess")
public class DiskBasedCache implements Cache {

    /** Map of the Key, CacheHeader pairs */
    private final Map<String, CacheHeader> mCacheHeaders = new LinkedHashMap<>(16, .75f, true);

    /** Total amount of space currently used by the cache in bytes. */
    private long mTotalSize;

    /** The root directory to use for the cache. */
    private final File mRootDirectory;

    /** The maximum size of the cache in bytes. */
    private final int mMaxCacheSizeInBytes;

    /** Default maximum disk usage in bytes. */
    private static final int DEFAULT_DISK_USAGE_BYTES = 5 * 1024 * 1024;

    /** High water mark percentage for the cache */
    private static final float HYSTERESIS_FACTOR = 0.9f;

    /** Magic number for current version of cache file format. */
    private static final int CACHE_MAGIC = 0x20170214;

    /**
     * Constructs an instance of the DiskBasedCache at the specified directory.
     * @param rootDirectory the root directory of the cache
     * @param maxCacheSizeInBytes the maximum size of the cache in bytes
     */
    public DiskBasedCache(File rootDirectory, int maxCacheSizeInBytes) {
        mRootDirectory = rootDirectory;
        mMaxCacheSizeInBytes = maxCacheSizeInBytes;
    }

    /**
     * Constructs an instance of the DiskBasedCache at the specified directory using
     * the default maximum cache size of 5MB.
     * @param rootDirectory the root directory of the cache
     */
    public DiskBasedCache(File rootDirectory) {
        this(rootDirectory, DEFAULT_DISK_USAGE_BYTES);
    }

    @Override
    public synchronized void clear() {
        File[] files = mRootDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
        mCacheHeaders.clear();
        mTotalSize = 0;
        VolleyLog.d("Cache cleared.");
    }

    @SuppressWarnings("TryFinallyCanBeTryWithResources")
    @Override
    public synchronized void initialize() {
        if (!mRootDirectory.exists()) {
            if (!mRootDirectory.mkdirs()) {
                VolleyLog.e("Unable to create cache dir %s", mRootDirectory.getAbsolutePath());
            }
            return;
        }
        File[] files = mRootDirectory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                DataInputStream is =
                        new DataInputStream(new BufferedInputStream(createInputStream(file)));
                try {
                    CacheHeader cacheHeader = CacheHeader.readHeader(is);
                    putCacheHeader(cacheHeader.key, cacheHeader);
                } finally {
                    //noinspection ThrowFromFinallyBlock
                    is.close();
                }
            } catch (IOException | RuntimeException e) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    @SuppressWarnings("TryFinallyCanBeTryWithResources")
    @Override
    public synchronized Entry get(String key) {
        CacheHeader cacheHeader = mCacheHeaders.get(key);
        // If the entry does not exist, return.
        if (cacheHeader == null) {
            return null;
        }
        File file = getFileForKey(key);
        try {
            CountingInputStream countingInputStream =
                    new CountingInputStream(new BufferedInputStream(createInputStream(file)));
            DataInputStream dataInputStream = new DataInputStream(countingInputStream);
            try {
                CacheHeader found = CacheHeader.readHeader(dataInputStream);
                if (!key.equals(found.key)) {
                    // File was shared by two keys and now holds data for a different entry!
                    VolleyLog.d("%s: key=%s, found=%s", file.getAbsolutePath(), key, found.key);
                    removeCacheHeader(key);
                    return null;
                }
                int bytesRead = countingInputStream.byteCount();
                int remaining = (int) (file.length() - bytesRead);
                if (remaining != cacheHeader.dataLength) {
                    throw new IOException("remain bytes=" + remaining + " bytesRead=" + bytesRead
                            + " dataLength=" + cacheHeader.dataLength);
                }
                byte[] data = new byte[cacheHeader.dataLength];
                dataInputStream.readFully(data);
                return cacheHeader.toCacheEntry(data);
            } finally {
                //noinspection ThrowFromFinallyBlock
                dataInputStream.close();
            }
        } catch (IOException | RuntimeException e) {
            VolleyLog.d("%s: %s", file.getAbsolutePath(), e.toString());
            remove(key);
            return null;
        }
    }

    @SuppressWarnings("TryFinallyCanBeTryWithResources")
    @Override
    public synchronized void put(String key, Entry entry) {
        CacheHeader cacheHeader = new CacheHeader(key, entry);
        pruneIfNeeded(cacheHeader.dataLength);
        File file = getFileForKey(key);
        try {
            DataOutputStream os =
                    new DataOutputStream(new BufferedOutputStream(createOutputStream(file)));
            try {
                if (!cacheHeader.writeHeader(os)) {
                    VolleyLog.d("Failed to write header for %s", file.getAbsolutePath());
                    throw new IOException();
                }
                os.write(entry.data);
            } finally {
                //noinspection ThrowFromFinallyBlock
                os.close();
            }
            putCacheHeader(key, cacheHeader);
        } catch (IOException ignored) {
            if (!file.delete()) {
                VolleyLog.d("Could not clean up file %s", file.getAbsolutePath());
            }
        }
    }

    @Override
    public synchronized void invalidate(String key, boolean fullExpire) {
        Entry entry = get(key);
        if (entry != null) {
            entry.softTtl = 0;
            if (fullExpire) {
                entry.ttl = 0;
            }
            put(key, entry);
        }
    }

    @Override
    public synchronized void remove(String key) {
        removeCacheHeader(key);
        File file = getFileForKey(key);
        if (!file.delete()) {
            VolleyLog.d("Could not delete cache entry for key=%s, filename=%s",
                    key, file.getName());
        }
    }

    /**
     * Returns a file object for the given cache key. Generates a pseudo-unique file name from the
     * specified cache key.
     */
    public File getFileForKey(String key) {
        int firstHalfLength = key.length() / 2;
        String localFilename = String.valueOf(key.substring(0, firstHalfLength).hashCode()) +
                String.valueOf(key.substring(firstHalfLength).hashCode());
        return new File(mRootDirectory, localFilename);
    }

    /**
     * Prunes the cache to fit the amount of bytes specified.
     * @param neededSpace the number of bytes we are trying to fit into the cache
     */
    private void pruneIfNeeded(int neededSpace) {
        if ((mTotalSize + neededSpace) < mMaxCacheSizeInBytes) {
            return;
        }
        if (VolleyLog.DEBUG) {
            VolleyLog.v("Pruning old cache entries.");
        }

        long before = mTotalSize;
        int prunedFiles = 0;
        long startTime = SystemClock.elapsedRealtime();

        for (Iterator<Map.Entry<String, CacheHeader>> iter = mCacheHeaders.entrySet().iterator();
             iter.hasNext(); ) {
            Map.Entry<String, CacheHeader> entry = iter.next();
            CacheHeader cacheHeader = entry.getValue();
            File file = getFileForKey(cacheHeader.key);
            if (file.delete()) {
                mTotalSize -= cacheHeader.dataLength;
            } else {
                VolleyLog.d("Could not delete cache entry for key=%s, filename=%s",
                        cacheHeader.key, file.getName());
            }
            iter.remove();
            prunedFiles++;

            if ((mTotalSize + neededSpace) < mMaxCacheSizeInBytes * HYSTERESIS_FACTOR) {
                break;
            }
        }

        if (VolleyLog.DEBUG) {
            VolleyLog.v("pruned %d files, %d bytes, %d ms",
                    prunedFiles, (mTotalSize - before), SystemClock.elapsedRealtime() - startTime);
        }
    }

    /**
     * Puts the item with the specified key into the map.
     * @param key the key to identify the item by
     * @param cacheHeader the item to cache
     */
    private void putCacheHeader(String key, CacheHeader cacheHeader) {
        CacheHeader removed = mCacheHeaders.put(key, cacheHeader);
        if (removed != null) {
            mTotalSize -= removed.dataLength;
        }
        mTotalSize += cacheHeader.dataLength;
    }

    /**
     * Removes the item identified by 'key' from the map.
     */
    private void removeCacheHeader(String key) {
        CacheHeader removed = mCacheHeaders.remove(key);
        if (removed != null) {
            mTotalSize -= removed.dataLength;
        }
    }

    //VisibleForTesting
    InputStream createInputStream(File file) throws FileNotFoundException {
        return new FileInputStream(file);
    }

    //VisibleForTesting
    OutputStream createOutputStream(File file) throws FileNotFoundException {
        return new FileOutputStream(file);
    }

    /**
     * Header for each cached item.
     */
    //VisibleForTesting
    static class CacheHeader {
        /** The key that identifies the cache entry. */
        final String key;

        /** ETag for cache coherence. */
        final String etag;

        /** Date of this response as reported by the server. */
        final long serverDate;

        /** The last modified date for the requested object. */
        final long lastModified;

        /** TTL for this record. */
        final long ttl;

        /** Soft TTL for this record. */
        final long softTtl;

        /** Headers from the response resulting in this cache entry. */
        final Map<String, String> responseHeaders;

        /** The length of the data identified by this CacheHeader. */
        final int dataLength;

        /**
         * Instantiates a new CacheHeader object
         * @param key the key that identifies the cache entry
         * @param entry the cache entry
         */
        CacheHeader(String key, Entry entry) {
            this(key, entry.etag, entry.serverDate, entry.lastModified, entry.ttl, entry.softTtl,
                    entry.responseHeaders, entry.data.length);
        }

        CacheHeader(String key, String etag, long serverDate, long lastModified,
                    long ttl, long softTtl, Map<String, String> responseHeaders,
                    int dataLength) {
            if (responseHeaders != null && responseHeaders.size() > 0xFFFF) {
                throw new IllegalArgumentException(
                        "responseHeaders size:" + responseHeaders.size());
            }
            this.key = key;
            this.etag = ("".equals(etag)) ? null : etag;
            this.serverDate = serverDate;
            this.lastModified = lastModified;
            this.ttl = ttl;
            this.softTtl = softTtl;
            this.responseHeaders = responseHeaders;
            this.dataLength = dataLength;
        }

        /**
         * Reads the header from a DataInput source and returns a CacheHeader object.
         * @param in the DataInput to read from
         * @throws IOException
         */
        static CacheHeader readHeader(DataInput in) throws IOException {
            int magic = in.readInt();
            if (magic != CACHE_MAGIC) {
                // entry will be deleted
                throw new IOException();
            }
            String key = in.readUTF();
            String etag = in.readUTF();
            long serverDate = in.readLong();
            long lastModified = in.readLong();
            long ttl = in.readLong();
            long softTtl = in.readLong();
            Map<String, String> responseHeaders = readResponseHeaders(in);
            int dataLength = in.readInt();
            return new CacheHeader(
                    key, etag, serverDate, lastModified, ttl, softTtl, responseHeaders, dataLength);
        }

        /**
         * Creates a cache entry for the specified data.
         */
        Entry toCacheEntry(byte[] data) {
            Entry e = new Entry();
            e.data = data;
            e.etag = etag;
            e.serverDate = serverDate;
            e.lastModified = lastModified;
            e.ttl = ttl;
            e.softTtl = softTtl;
            e.responseHeaders = responseHeaders;
            return e;
        }

        /**
         * Writes the contents of this CacheHeader to the specified DataOutputStream.
         */
        boolean writeHeader(DataOutputStream out) {
            try {
                writeFields(out);
                out.flush();
                return true;
            } catch (IOException e) {
                VolleyLog.d(e.toString());
                return false;
            }
        }

        /**
         * Writes the fields of this CacheHeader to the specified DataOutput.
         */
        //VisibleForTesting
        void writeFields(DataOutput out) throws IOException {
            out.writeInt(CACHE_MAGIC);
            out.writeUTF(key);
            out.writeUTF((etag == null) ? "" : etag);
            out.writeLong(serverDate);
            out.writeLong(lastModified);
            out.writeLong(ttl);
            out.writeLong(softTtl);
            writeResponseHeaders(out);
            out.writeInt(dataLength);
        }

        private static Map<String, String> readResponseHeaders(DataInput in)
                throws IOException {
            int size = in.readUnsignedShort();
            Map<String, String> result = (size == 0)
                    ? Collections.<String, String>emptyMap()
                    : new HashMap<String, String>(size);
            for (int i = 0; i < size; i++) {
                String key = in.readUTF().intern();
                String value = in.readUTF().intern();
                result.put(key, value);
            }
            return result;
        }

        private void writeResponseHeaders(DataOutput out)
                throws IOException {
            int size = (responseHeaders != null) ? responseHeaders.size() : 0;
            out.writeShort(size);
            if (size > 0) {
                for (Map.Entry<String, String> entry : responseHeaders.entrySet()) {
                    out.writeUTF(entry.getKey());
                    out.writeUTF(entry.getValue());
                }
            }
        }
    }

    /** Counts the number of bytes read from this stream. */
    //VisibleForTesting
    static class CountingInputStream extends FilterInputStream {
        private int bytesRead;

        CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int result = super.read();
            if (result != -1) {
                bytesRead++;
            }
            return result;
        }

        @Override
        public int read(/*NonNull*/ byte[] buffer, int offset, int count) throws IOException {
            int result = super.read(buffer, offset, count);
            if (result != -1) {
                bytesRead += result;
            }
            return result;
        }

        int byteCount() {
            return bytesRead;
        }
    }
}

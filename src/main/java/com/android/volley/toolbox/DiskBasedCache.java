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
import android.text.TextUtils;
import androidx.annotation.VisibleForTesting;
import com.android.volley.Cache;
import com.android.volley.VolleyLog;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cache implementation that caches files directly onto the hard disk in the specified directory.
 * The default disk usage size is 5MB, but is configurable.
 *
 * <p>This cache supports the {@link Entry#allResponseHeaders} headers field.
 */
public class DiskBasedCache implements Cache {

    /** Map of the Key, CacheHeader pairs */
    private final Map<String, CacheHeader> mEntries = new LinkedHashMap<>(16, .75f, true);

    /** Total amount of space currently used by the cache in bytes. */
    private long mTotalSize = 0;

    /** The supplier for the root directory to use for the cache. */
    private final FileSupplier mRootDirectorySupplier;

    /** The maximum size of the cache in bytes. */
    private final int mMaxCacheSizeInBytes;

    /**
     * Constructs an instance of the DiskBasedCache at the specified directory.
     *
     * @param rootDirectory The root directory of the cache.
     * @param maxCacheSizeInBytes The maximum size of the cache in bytes. Note that the cache may
     *     briefly exceed this size on disk when writing a new entry that pushes it over the limit
     *     until the ensuing pruning completes.
     */
    public DiskBasedCache(final File rootDirectory, int maxCacheSizeInBytes) {
        mRootDirectorySupplier =
                new FileSupplier() {
                    @Override
                    public File get() {
                        return rootDirectory;
                    }
                };
        mMaxCacheSizeInBytes = maxCacheSizeInBytes;
    }

    /**
     * Constructs an instance of the DiskBasedCache at the specified directory.
     *
     * @param rootDirectorySupplier The supplier for the root directory of the cache.
     * @param maxCacheSizeInBytes The maximum size of the cache in bytes. Note that the cache may
     *     briefly exceed this size on disk when writing a new entry that pushes it over the limit
     *     until the ensuing pruning completes.
     */
    public DiskBasedCache(FileSupplier rootDirectorySupplier, int maxCacheSizeInBytes) {
        mRootDirectorySupplier = rootDirectorySupplier;
        mMaxCacheSizeInBytes = maxCacheSizeInBytes;
    }

    /**
     * Constructs an instance of the DiskBasedCache at the specified directory using the default
     * maximum cache size of 5MB.
     *
     * @param rootDirectory The root directory of the cache.
     */
    public DiskBasedCache(File rootDirectory) {
        this(rootDirectory, DiskBasedCacheUtility.DEFAULT_DISK_USAGE_BYTES);
    }

    /**
     * Constructs an instance of the DiskBasedCache at the specified directory using the default
     * maximum cache size of 5MB.
     *
     * @param rootDirectorySupplier The supplier for the root directory of the cache.
     */
    public DiskBasedCache(FileSupplier rootDirectorySupplier) {
        this(rootDirectorySupplier, DiskBasedCacheUtility.DEFAULT_DISK_USAGE_BYTES);
    }

    /** Clears the cache. Deletes all cached files from disk. */
    @Override
    public synchronized void clear() {
        File[] files = mRootDirectorySupplier.get().listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        mEntries.clear();
        mTotalSize = 0;
        VolleyLog.d("Cache cleared.");
    }

    /** Returns the cache entry with the specified key if it exists, null otherwise. */
    @Override
    public synchronized Entry get(String key) {
        CacheHeader entry = mEntries.get(key);
        // if the entry does not exist, return.
        if (entry == null) {
            return null;
        }
        File file = getFileForKey(key);
        try {
            CountingInputStream cis =
                    new CountingInputStream(
                            new BufferedInputStream(createInputStream(file)), file.length());
            try {
                CacheHeader entryOnDisk = CacheHeader.readHeader(cis);
                if (!TextUtils.equals(key, entryOnDisk.key)) {
                    // File was shared by two keys and now holds data for a different entry!
                    VolleyLog.d(
                            "%s: key=%s, found=%s", file.getAbsolutePath(), key, entryOnDisk.key);
                    // Remove key whose contents on disk have been replaced.
                    removeEntry(key);
                    return null;
                }
                byte[] data = streamToBytes(cis, cis.bytesRemaining());
                return entry.toCacheEntry(data);
            } finally {
                // Any IOException thrown here is handled by the below catch block by design.
                //noinspection ThrowFromFinallyBlock
                cis.close();
            }
        } catch (IOException e) {
            VolleyLog.d("%s: %s", file.getAbsolutePath(), e.toString());
            remove(key);
            return null;
        }
    }

    /**
     * Initializes the DiskBasedCache by scanning for all files currently in the specified root
     * directory. Creates the root directory if necessary.
     */
    @Override
    public synchronized void initialize() {
        File rootDirectory = mRootDirectorySupplier.get();
        if (!rootDirectory.exists()) {
            if (!rootDirectory.mkdirs()) {
                VolleyLog.e("Unable to create cache dir %s", rootDirectory.getAbsolutePath());
            }
            return;
        }
        File[] files = rootDirectory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                long entrySize = file.length();
                CountingInputStream cis =
                        new CountingInputStream(
                                new BufferedInputStream(createInputStream(file)), entrySize);
                try {
                    CacheHeader entry = CacheHeader.readHeader(cis);
                    entry.size = entrySize;
                    putEntry(entry.key, entry);
                } finally {
                    // Any IOException thrown here is handled by the below catch block by design.
                    //noinspection ThrowFromFinallyBlock
                    cis.close();
                }
            } catch (IOException e) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    /**
     * Invalidates an entry in the cache.
     *
     * @param key Cache key
     * @param fullExpire True to fully expire the entry, false to soft expire
     */
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

    /** Puts the entry with the specified key into the cache. */
    @Override
    public synchronized void put(String key, Entry entry) {
        // If adding this entry would trigger a prune, but pruning would cause the new entry to be
        // deleted, then skip writing the entry in the first place, as this is just churn.
        // Note that we don't include the cache header overhead in this calculation for simplicity,
        // so putting entries which are just below the threshold may still cause this churn.
        if (mTotalSize + entry.data.length > mMaxCacheSizeInBytes
                && entry.data.length
                        > mMaxCacheSizeInBytes * DiskBasedCacheUtility.HYSTERESIS_FACTOR) {
            return;
        }
        File file = getFileForKey(key);
        try {
            BufferedOutputStream fos = new BufferedOutputStream(createOutputStream(file));
            CacheHeader e = new CacheHeader(key, entry);
            boolean success = e.writeHeader(fos);
            if (!success) {
                fos.close();
                VolleyLog.d("Failed to write header for %s", file.getAbsolutePath());
                throw new IOException();
            }
            fos.write(entry.data);
            fos.close();
            e.size = file.length();
            putEntry(key, e);
            pruneIfNeeded();
        } catch (IOException e) {
            boolean deleted = file.delete();
            if (!deleted) {
                VolleyLog.d("Could not clean up file %s", file.getAbsolutePath());
            }
            initializeIfRootDirectoryDeleted();
        }
    }

    /** Removes the specified key from the cache if it exists. */
    @Override
    public synchronized void remove(String key) {
        boolean deleted = getFileForKey(key).delete();
        removeEntry(key);
        if (!deleted) {
            VolleyLog.d(
                    "Could not delete cache entry for key=%s, filename=%s",
                    key, getFilenameForKey(key));
        }
    }

    /**
     * Creates a pseudo-unique filename for the specified cache key.
     *
     * @param key The key to generate a file name for.
     * @return A pseudo-unique filename.
     */
    private String getFilenameForKey(String key) {
        int firstHalfLength = key.length() / 2;
        String localFilename = String.valueOf(key.substring(0, firstHalfLength).hashCode());
        localFilename += String.valueOf(key.substring(firstHalfLength).hashCode());
        return localFilename;
    }

    /** Returns a file object for the given cache key. */
    public File getFileForKey(String key) {
        return new File(mRootDirectorySupplier.get(), getFilenameForKey(key));
    }

    /** Re-initialize the cache if the directory was deleted. */
    private void initializeIfRootDirectoryDeleted() {
        if (!mRootDirectorySupplier.get().exists()) {
            VolleyLog.d("Re-initializing cache after external clearing.");
            mEntries.clear();
            mTotalSize = 0;
            initialize();
        }
    }

    /** Represents a supplier for {@link File}s. */
    public interface FileSupplier {
        File get();
    }

    /** Prunes the cache to fit the maximum size. */
    private void pruneIfNeeded() {
        if (mTotalSize < mMaxCacheSizeInBytes) {
            return;
        }
        if (VolleyLog.DEBUG) {
            VolleyLog.v("Pruning old cache entries.");
        }

        long before = mTotalSize;
        int prunedFiles = 0;
        long startTime = SystemClock.elapsedRealtime();

        Iterator<Map.Entry<String, CacheHeader>> iterator = mEntries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CacheHeader> entry = iterator.next();
            CacheHeader e = entry.getValue();
            boolean deleted = getFileForKey(e.key).delete();
            if (deleted) {
                mTotalSize -= e.size;
            } else {
                VolleyLog.d(
                        "Could not delete cache entry for key=%s, filename=%s",
                        e.key, getFilenameForKey(e.key));
            }
            iterator.remove();
            prunedFiles++;

            if (mTotalSize < mMaxCacheSizeInBytes * DiskBasedCacheUtility.HYSTERESIS_FACTOR) {
                break;
            }
        }

        if (VolleyLog.DEBUG) {
            VolleyLog.v(
                    "pruned %d files, %d bytes, %d ms",
                    prunedFiles, (mTotalSize - before), SystemClock.elapsedRealtime() - startTime);
        }
    }

    /**
     * Puts the entry with the specified key into the cache.
     *
     * @param key The key to identify the entry by.
     * @param entry The entry to cache.
     */
    private void putEntry(String key, CacheHeader entry) {
        if (!mEntries.containsKey(key)) {
            mTotalSize += entry.size;
        } else {
            CacheHeader oldEntry = mEntries.get(key);
            mTotalSize += (entry.size - oldEntry.size);
        }
        mEntries.put(key, entry);
    }

    /** Removes the entry identified by 'key' from the cache. */
    private void removeEntry(String key) {
        CacheHeader removed = mEntries.remove(key);
        if (removed != null) {
            mTotalSize -= removed.size;
        }
    }

    /**
     * Reads length bytes from CountingInputStream into byte array.
     *
     * @param cis input stream
     * @param length number of bytes to read
     * @throws IOException if fails to read all bytes
     */
    static byte[] streamToBytes(CountingInputStream cis, long length) throws IOException {
        long maxLength = cis.bytesRemaining();
        // Length cannot be negative or greater than bytes remaining, and must not overflow int.
        if (length < 0 || length > maxLength || (int) length != length) {
            throw new IOException("streamToBytes length=" + length + ", maxLength=" + maxLength);
        }
        byte[] bytes = new byte[(int) length];
        new DataInputStream(cis).readFully(bytes);
        return bytes;
    }

    @VisibleForTesting
    InputStream createInputStream(File file) throws FileNotFoundException {
        return new FileInputStream(file);
    }

    @VisibleForTesting
    OutputStream createOutputStream(File file) throws FileNotFoundException {
        return new FileOutputStream(file);
    }

    @VisibleForTesting
    static class CountingInputStream extends FilterInputStream {
        private final long length;
        private long bytesRead;

        CountingInputStream(InputStream in, long length) {
            super(in);
            this.length = length;
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
        public int read(byte[] buffer, int offset, int count) throws IOException {
            int result = super.read(buffer, offset, count);
            if (result != -1) {
                bytesRead += result;
            }
            return result;
        }

        @VisibleForTesting
        long bytesRead() {
            return bytesRead;
        }

        long bytesRemaining() {
            return length - bytesRead;
        }
    }
}

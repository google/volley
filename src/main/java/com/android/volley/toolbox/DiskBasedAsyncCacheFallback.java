package com.android.volley.toolbox;

import android.text.TextUtils;
import androidx.annotation.VisibleForTesting;
import com.android.volley.AsyncCache;
import com.android.volley.VolleyLog;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AsyncCache implementation that uses Java's standard IO libraries to perform disc reads and writes
 * This should be used as a fallback for devices with an API level below 26, as the operations are
 * synchronous.
 */
public class DiskBasedAsyncCacheFallback extends AsyncCache {

    /** Map of the Key, CacheHeader pairs */
    private final Map<String, CacheHeader> mEntries = new LinkedHashMap<>(16, .75f, true);

    /** Total amount of space currently used by the cache in bytes. */
    private long mTotalSize = 0;

    /** The supplier for the root directory to use for the cache. */
    private final DiskBasedCacheUtility.FileSupplier mRootDirectorySupplier;

    /** The maximum size of the cache in bytes. */
    private final int mMaxCacheSizeInBytes;

    /** Default maximum disk usage in bytes. */
    private static final int DEFAULT_DISK_USAGE_BYTES = 5 * 1024 * 1024;

    /** High water mark percentage for the cache */
    @VisibleForTesting static final float HYSTERESIS_FACTOR = 0.9f;

    /**
     * Constructs an instance of the DiskBasedAsyncCacheFallback at the specified directory.
     *
     * @param rootDirectory The root directory of the cache.
     * @param maxCacheSizeInBytes The maximum size of the cache in bytes. Note that the cache may
     *     briefly exceed this size on disk when writing a new entry that pushes it over the limit
     *     until the ensuing pruning completes.
     */
    public DiskBasedAsyncCacheFallback(final File rootDirectory, int maxCacheSizeInBytes) {
        mRootDirectorySupplier =
                new DiskBasedCacheUtility.FileSupplier() {
                    @Override
                    public File get() {
                        return rootDirectory;
                    }
                };
        mMaxCacheSizeInBytes = maxCacheSizeInBytes;
    }

    /** Clears the cache. Deletes all cached files from disk. */
    @Override
    public synchronized void clear() {
        // TODO (sphill99): Implement
    }

    /** Returns the cache entry with the specified key if it exists, null otherwise. */
    @Override
    public synchronized void get(String key, OnGetCompleteCallback callback) {
        CacheHeader entry = mEntries.get(key);
        // if the entry does not exist, return.
        if (entry == null) {
            callback.onGetComplete(null);
        }
        File file = getFileForKey(key);

        try {
            DiskBasedCacheUtility.CountingInputStream cis =
                    new DiskBasedCacheUtility.CountingInputStream(
                            new BufferedInputStream(createInputStream(file)), file.length());
            try {
                CacheHeader entryOnDisk = CacheHeader.readHeader(cis);
                if (!TextUtils.equals(key, entryOnDisk.key)) {
                    // File was shared by two keys and now holds data for a different entry!
                    VolleyLog.d(
                            "%s: key=%s, found=%s", file.getAbsolutePath(), key, entryOnDisk.key);
                    // Remove key whose contents on disk have been replaced.
                    removeEntry(key);
                    callback.onGetComplete(null);
                }
                byte[] data = DiskBasedCacheUtility.streamToBytes(cis, cis.bytesRemaining());
                callback.onGetComplete(entry.toCacheEntry(data));
            } finally {
                // Any IOException thrown here is handled by the below catch block by design.
                //noinspection ThrowFromFinallyBlock
                cis.close();
            }
        } catch (IOException e) {
            VolleyLog.d("%s: %s", file.getAbsolutePath(), e.toString());
            remove(key);
            callback.onGetComplete(null);
        }
    }

    @Override
    public void put(String key, Entry entry, OnPutCompleteCallback callback) {
        // TODO (sphill99): Implement
    }

    /**
     * Initializes the DiskBasedAsyncCacheFallback by scanning for all files currently in the
     * specified root directory. Creates the root directory if necessary.
     */
    @Override
    public synchronized void initialize() {
        // TODO (sphill99): Implement
    }

    /**
     * Invalidates an entry in the cache.
     *
     * @param key Cache key
     * @param fullExpire True to fully expire the entry, false to soft expire
     */
    @Override
    public synchronized void invalidate(String key, boolean fullExpire) {
        // TODO (sphill99): Implement
    }

    /** Removes the specified key from the cache if it exists. */
    @Override
    public synchronized void remove(String key) {
        // TODO (sphill99): Implement
    }

    /** Returns a file object for the given cache key. */
    public File getFileForKey(String key) {
        return new File(mRootDirectorySupplier.get(), DiskBasedCacheUtility.getFilenameForKey(key));
    }

    /** Removes the entry identified by 'key' from the cache. */
    private void removeEntry(String key) {
        CacheHeader removed = mEntries.remove(key);
        if (removed != null) {
            mTotalSize -= removed.size;
        }
    }

    @VisibleForTesting
    InputStream createInputStream(File file) throws FileNotFoundException {
        return new FileInputStream(file);
    }
}

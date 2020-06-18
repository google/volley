package com.android.volley.toolbox;

import com.android.volley.AsyncCache;
import java.io.File;

/**
 * AsyncCache implementation that uses Java's standard IO libraries to perform disc reads and writes
 * This should be used as a fallback for devices with an API level below 26, as the operations are
 * synchronous.
 */
public class DiskBasedAsyncCacheFallback extends AsyncCache {

    private DiskBasedCache cache;

    /**
     * Constructs an instance of the DiskBasedCache at the specified directory.
     *
     * @param rootDirectory The root directory of the cache.
     * @param maxCacheSizeInBytes The maximum size of the cache in bytes. Note that the cache may
     *     briefly exceed this size on disk when writing a new entry that pushes it over the limit
     *     until the ensuing pruning completes.
     */
    public DiskBasedAsyncCacheFallback(final File rootDirectory, int maxCacheSizeInBytes) {
        cache = new DiskBasedCache(rootDirectory, maxCacheSizeInBytes);
    }

    /**
     * Constructs an instance of the DiskBasedCache at the specified directory.
     *
     * @param rootDirectorySupplier The supplier for the root directory of the cache.
     * @param maxCacheSizeInBytes The maximum size of the cache in bytes. Note that the cache may
     *     briefly exceed this size on disk when writing a new entry that pushes it over the limit
     *     until the ensuing pruning completes.
     */
    public DiskBasedAsyncCacheFallback(
            DiskBasedCache.FileSupplier rootDirectorySupplier, int maxCacheSizeInBytes) {
        cache = new DiskBasedCache(rootDirectorySupplier, maxCacheSizeInBytes);
    }

    /**
     * Constructs an instance of the DiskBasedCache at the specified directory using the default
     * maximum cache size of 5MB.
     *
     * @param rootDirectory The root directory of the cache.
     */
    public DiskBasedAsyncCacheFallback(File rootDirectory) {
        this(rootDirectory, DiskBasedCacheUtility.DEFAULT_DISK_USAGE_BYTES);
    }

    /**
     * Constructs an instance of the DiskBasedCache at the specified directory using the default
     * maximum cache size of 5MB.
     *
     * @param rootDirectorySupplier The supplier for the root directory of the cache.
     */
    public DiskBasedAsyncCacheFallback(DiskBasedCache.FileSupplier rootDirectorySupplier) {
        this(rootDirectorySupplier, DiskBasedCacheUtility.DEFAULT_DISK_USAGE_BYTES);
    }

    /** Clears the cache. Deletes all cached files from disk. */
    @Override
    public synchronized void clear() {
        // TODO (sphill99): Implement
    }

    /** Returns the cache entry with the specified key if it exists, null otherwise. */
    @Override
    public synchronized void get(String key, OnGetCompleteCallback callback) {
        Entry entry = cache.get(key);
        callback.onGetComplete(entry);
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
}

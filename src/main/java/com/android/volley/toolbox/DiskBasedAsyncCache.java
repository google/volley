package com.android.volley.toolbox;

import android.annotation.SuppressLint;
import androidx.annotation.VisibleForTesting;
import com.android.volley.AsyncCache;
import com.android.volley.Header;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressLint("NewApi")
public class DiskBasedAsyncCache extends AsyncCache {

    /** Map of the Key, CacheHeader pairs */
    private final Map<String, DiskBasedAsyncCache.CacheHeader> mEntries =
            new LinkedHashMap<>(16, .75f, true);

    /** Total amount of space currently used by the cache in bytes. */
    private long mTotalSize = 0;

    /** The supplier for the root directory to use for the cache. */
    private final DiskBasedAsyncCache.FileSupplier mRootDirectorySupplier;

    /** The maximum size of the cache in bytes. */
    private final int mMaxCacheSizeInBytes;

    /** Default maximum disk usage in bytes. */
    private static final int DEFAULT_DISK_USAGE_BYTES = 5 * 1024 * 1024;

    /** High water mark percentage for the cache */
    @VisibleForTesting static final float HYSTERESIS_FACTOR = 0.9f;

    /** Magic number for current version of cache file format. */
    private static final int CACHE_MAGIC = 0x20150306;

    /**
     * Constructs an instance of the DiskBasedAsyncCache at the specified directory.
     *
     * @param rootDirectory The root directory of the cache.
     * @param maxCacheSizeInBytes The maximum size of the cache in bytes. Note that the cache may
     *     briefly exceed this size on disk when writing a new entry that pushes it over the limit
     *     until the ensuing pruning completes.
     */
    public DiskBasedAsyncCache(final File rootDirectory, int maxCacheSizeInBytes) {
        mRootDirectorySupplier =
                new DiskBasedAsyncCache.FileSupplier() {
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
    public void get(String key, OnGetCompleteCallback callback) {
        final OnGetCompleteCallback cb = callback;
        final DiskBasedAsyncCache.CacheHeader entry = mEntries.get(key);
        // if the entry does not exist, return.
        if (entry == null) {
            cb.onGetComplete(null);
            return;
        }
        File file = getFileForKey(key);
        Path path = Paths.get(file.getPath());
        try {
            AsynchronousFileChannel afc =
                    AsynchronousFileChannel.open(path, StandardOpenOption.READ);
            ByteBuffer buffer = ByteBuffer.allocate((int) file.length());
            afc.read(
                    /* destination */ buffer,
                    /* position */ 0,
                    /* attachment */ buffer,
                    new CompletionHandler<Integer, ByteBuffer>() {
                        @Override
                        public void completed(Integer result, ByteBuffer attachment) {
                            if (attachment.hasArray()) {
                                int offset = attachment.arrayOffset();
                                byte[] data = attachment.array();
                                cb.onGetComplete(entry.toCacheEntry(data));
                            }
                        }

                        @Override
                        public void failed(Throwable exc, ByteBuffer attachment) {
                            cb.onGetComplete(null);
                        }
                    });
        } catch (IOException e) {
            cb.onGetComplete(null);
        }
    }

    @Override
    public void put(String key, Entry entry, OnPutCompleteCallback callback) {
        // TODO (sphill99): Implement
    }

    /**
     * Initializes the DiskBasedAsyncCache by scanning for all files currently in the specified root
     * directory. Creates the root directory if necessary.
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

    /** Represents a supplier for {@link File}s. */
    public interface FileSupplier {
        File get();
    }

    /** Handles holding onto the cache headers for an entry. */
    @VisibleForTesting
    static class CacheHeader {
        /**
         * The size of the data identified by this CacheHeader on disk (both header and data).
         *
         * <p>Must be set by the caller after it has been calculated.
         *
         * <p>This is not serialized to disk.
         */
        long size;

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
        final List<Header> allResponseHeaders;

        private CacheHeader(
                String key,
                String etag,
                long serverDate,
                long lastModified,
                long ttl,
                long softTtl,
                List<Header> allResponseHeaders) {
            this.key = key;
            this.etag = "".equals(etag) ? null : etag;
            this.serverDate = serverDate;
            this.lastModified = lastModified;
            this.ttl = ttl;
            this.softTtl = softTtl;
            this.allResponseHeaders = allResponseHeaders;
        }

        /**
         * Instantiates a new CacheHeader object.
         *
         * @param key The key that identifies the cache entry
         * @param entry The cache entry.
         */
        CacheHeader(String key, Entry entry) {
            this(
                    key,
                    entry.etag,
                    entry.serverDate,
                    entry.lastModified,
                    entry.ttl,
                    entry.softTtl,
                    getAllResponseHeaders(entry));
        }

        private static List<Header> getAllResponseHeaders(Entry entry) {
            // If the entry contains all the response headers, use that field directly.
            if (entry.allResponseHeaders != null) {
                return entry.allResponseHeaders;
            }

            // Legacy fallback - copy headers from the map.
            return HttpHeaderParser.toAllHeaderList(entry.responseHeaders);
        }

        /** Creates a cache entry for the specified data. */
        Entry toCacheEntry(byte[] data) {
            Entry e = new Entry();
            e.data = data;
            e.etag = etag;
            e.serverDate = serverDate;
            e.lastModified = lastModified;
            e.ttl = ttl;
            e.softTtl = softTtl;
            e.responseHeaders = HttpHeaderParser.toHeaderMap(allResponseHeaders);
            e.allResponseHeaders = Collections.unmodifiableList(allResponseHeaders);
            return e;
        }
    }
}

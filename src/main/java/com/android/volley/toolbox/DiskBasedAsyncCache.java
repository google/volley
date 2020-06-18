package com.android.volley.toolbox;

import android.annotation.SuppressLint;
import com.android.volley.AsyncCache;
import com.android.volley.VolleyLog;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AsyncCache implementation that uses Java NIO's AsynchronousFileChannel to perform asynchronous
 * disk reads and writes. This should only be used by devices with an API level of 26 or above.
 */
@SuppressLint("NewApi")
public class DiskBasedAsyncCache extends AsyncCache {

    /** Map of the Key, CacheHeader pairs */
    private final Map<String, CacheHeader> mEntries = new LinkedHashMap<>(16, .75f, true);

    /** The supplier for the root directory to use for the cache. */
    private final DiskBasedCacheUtility.FileSupplier mRootDirectorySupplier;

    /**
     * Constructs an instance of the DiskBasedAsyncCache at the specified directory.
     *
     * @param rootDirectory The root directory of the cache.
     */
    public DiskBasedAsyncCache(final File rootDirectory) {
        mRootDirectorySupplier =
                new DiskBasedCacheUtility.FileSupplier() {
                    @Override
                    public File get() {
                        return rootDirectory;
                    }
                };
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
        final CacheHeader entry = mEntries.get(key);
        // if the entry does not exist, return.
        if (entry == null) {
            cb.onGetComplete(null);
            return;
        }
        final File file = getFileForKey(key);
        Path path = Paths.get(file.getPath());
        try {
            AsynchronousFileChannel afc =
                    AsynchronousFileChannel.open(path, StandardOpenOption.READ);
            ByteBuffer buffer = ByteBuffer.allocate((int) file.length());
            afc.read(
                    /* destination= */ buffer,
                    /* position= */ 0,
                    /* attachment= */ buffer,
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
                            VolleyLog.d("%s: %s", file.getAbsolutePath(), exc.toString());
                            cb.onGetComplete(null);
                        }
                    });
        } catch (IOException e) {
            VolleyLog.d("%s: %s", file.getAbsolutePath(), e.toString());
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

    /** Returns a file object for the given cache key. */
    public File getFileForKey(String key) {
        return new File(mRootDirectorySupplier.get(), DiskBasedCacheUtility.getFilenameForKey(key));
    }
}

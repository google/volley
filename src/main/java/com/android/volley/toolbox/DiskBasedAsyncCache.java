package com.android.volley.toolbox;

import android.os.Build;
import androidx.annotation.RequiresApi;
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
 * disk reads and writes.
 */
@RequiresApi(Build.VERSION_CODES.O)
public class DiskBasedAsyncCache extends AsyncCache {

    /** Map of the Key, CacheHeader pairs */
    private final Map<String, CacheHeader> mEntries = new LinkedHashMap<>(16, .75f, true);

    /** The supplier for the root directory to use for the cache. */
    private final DiskBasedCacheUtility.FileSupplier mRootDirectorySupplier;

    /** Total amount of space currently used by the cache in bytes. */
    private long mTotalSize = 0;

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

    /** Returns the cache entry with the specified key if it exists, null otherwise. */
    @Override
    public void get(String key, final OnGetCompleteCallback callback) {
        final CacheHeader entry = mEntries.get(key);
        // if the entry does not exist, return null.
        if (entry == null) {
            callback.onGetComplete(null);
            return;
        }
        final File file = getFileForKey(key);
        final int size = (int) file.length();
        Path path = Paths.get(file.getPath());
        try (AsynchronousFileChannel afc =
                AsynchronousFileChannel.open(path, StandardOpenOption.READ)) {
            final ByteBuffer buffer = ByteBuffer.allocate(size);
            afc.read(
                    /* destination= */ buffer,
                    /* position= */ 0,
                    /* attachment= */ null,
                    new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result, Void v) {
                            // if the file size changes, return null
                            if (size != result) {
                                VolleyLog.e(
                                        "File changed while reading: %s", file.getAbsolutePath());
                                callback.onGetComplete(null);
                                return;
                            }
                            byte[] data = buffer.array();
                            callback.onGetComplete(entry.toCacheEntry(data));
                        }

                        @Override
                        public void failed(Throwable exc, Void v) {
                            VolleyLog.e(exc, "Failed to read file %s", file.getAbsolutePath());
                            callback.onGetComplete(null);
                        }
                    });
        } catch (IOException e) {
            VolleyLog.e(e, "Failed to read file %s", file.getAbsolutePath());
            callback.onGetComplete(null);
        }
    }

    /** Returns a file object for the given cache key. */
    File getFileForKey(String key) {
        return new File(mRootDirectorySupplier.get(), DiskBasedCacheUtility.getFilenameForKey(key));
    }
}

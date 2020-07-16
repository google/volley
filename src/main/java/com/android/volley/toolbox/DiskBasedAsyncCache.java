package com.android.volley.toolbox;

import android.os.Build;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.android.volley.AsyncCache;
import com.android.volley.Cache;
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
    private final FileSupplier mRootDirectorySupplier;

    /** Total amount of space currently used by the cache in bytes. */
    private long mTotalSize = 0;

    /** The maximum size of the cache in bytes. */
    private final int mMaxCacheSizeInBytes;

    /**
     * Constructs an instance of the DiskBasedAsyncCache at the specified directory.
     *
     * @param rootDirectory The root directory of the cache.
     */
    public DiskBasedAsyncCache(final File rootDirectory, int maxCacheSizeInBytes) {
        mRootDirectorySupplier =
                new FileSupplier() {
                    @Override
                    public File get() {
                        return rootDirectory;
                    }
                };
        mMaxCacheSizeInBytes = maxCacheSizeInBytes;
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
        final File file = DiskBasedCacheUtility.getFileForKey(key, mRootDirectorySupplier);
        Path path = Paths.get(file.getPath());

        // channel we can close after IOException
        AsynchronousFileChannel channel = null;
        try {
            final AsynchronousFileChannel afc =
                    AsynchronousFileChannel.open(path, StandardOpenOption.READ);
            channel = afc;
            int headerSize = entry.getHeaderSize();
            final int size = (int) file.length() - headerSize;
            final ByteBuffer buffer = ByteBuffer.allocate(size);
            afc.read(
                    /* destination= */ buffer,
                    /* position= */ headerSize,
                    /* attachment= */ null,
                    new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result, Void v) {
                            closeChannel(afc, "completed read");
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
                        public void failed(Throwable exc, Void ignore) {
                            VolleyLog.e(exc, "Failed to read file %s", file.getAbsolutePath());
                            closeChannel(afc, "failed read");
                            callback.onGetComplete(null);
                        }
                    });
        } catch (IOException e) {
            VolleyLog.e(e, "Failed to read file %s", file.getAbsolutePath());
            closeChannel(channel, "IOException");
            callback.onGetComplete(null);
        }
    }

    /** Puts the cache entry with a specified key into the cache. */
    @Override
    public void put(final String key, Cache.Entry entry, final OnPutCompleteCallback callback) {
        if (DiskBasedCacheUtility.wouldBePruned(
                mTotalSize, entry.data.length, mMaxCacheSizeInBytes)) {
            return;
        }

        final File file = DiskBasedCacheUtility.getFileForKey(key, mRootDirectorySupplier);
        Path path = Paths.get(file.getPath());

        // channel we can close after IOException
        AsynchronousFileChannel channel = null;
        try {
            final AsynchronousFileChannel afc =
                    AsynchronousFileChannel.open(
                            path, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
            channel = afc;
            final CacheHeader header = new CacheHeader(key, entry);
            int headerSize = header.getHeaderSize();
            final int size = entry.data.length + headerSize;
            ByteBuffer buffer = ByteBuffer.allocate(size);
            header.writeHeader(buffer);
            buffer.put(entry.data);
            buffer.flip();
            afc.write(
                    /* source= */ buffer,
                    /* position= */ 0,
                    /* attachment= */ null,
                    new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer resultLen, Void ignore) {
                            if (closeChannel(afc, "completed write")) {
                                if (resultLen != size) {
                                    VolleyLog.e(
                                            "File changed while writing: %s",
                                            file.getAbsolutePath());
                                    deleteFile(file);
                                    callback.onPutComplete();
                                    return;
                                }
                                header.size = resultLen;
                                mTotalSize =
                                        DiskBasedCacheUtility.putEntry(
                                                key, header, mTotalSize, mEntries);
                                mTotalSize =
                                        DiskBasedCacheUtility.pruneIfNeeded(
                                                mTotalSize,
                                                mMaxCacheSizeInBytes,
                                                mEntries,
                                                mRootDirectorySupplier);
                            } else {
                                deleteFile(file);
                            }

                            callback.onPutComplete();
                        }

                        @Override
                        public void failed(Throwable throwable, Void ignore) {
                            VolleyLog.e(
                                    throwable, "Failed to read file %s", file.getAbsolutePath());
                            deleteFile(file);
                            callback.onPutComplete();
                            closeChannel(afc, "failed read");
                        }
                    });
        } catch (IOException e) {
            if (closeChannel(channel, "IOException")) {
                deleteFile(file);
            }
            callback.onPutComplete();
        }
    }

    public void initialize() {
        // TODO (sphill99): #181
    }

    /** Re-initialize the cache if the directory was deleted. */
    private void initializeIfRootDirectoryDeleted() {
        if (mRootDirectorySupplier.get().exists()) {
            return;
        }
        VolleyLog.d("Re-initializing cache after external clearing.");
        mEntries.clear();
        mTotalSize = 0;
        initialize();
    }

    /**
     * Closes the asynchronous file channel.
     *
     * @param afc Channel that is being closed.
     * @param endOfMessage End of error message that logs where the close is happening.
     * @return Returns true if the channel is successfully closed, false if channel is null, or
     *     close results in an IOException.
     */
    private boolean closeChannel(@Nullable AsynchronousFileChannel afc, String endOfMessage) {
        if (afc == null) {
            return false;
        }
        try {
            afc.close();
            return true;
        } catch (IOException e) {
            VolleyLog.e(e, "failed to close file after %s", endOfMessage);
            return false;
        }
    }

    /** Deletes the specified file, and reinitializes the root if it was deleted. */
    private void deleteFile(File file) {
        boolean deleted = file.delete();
        if (!deleted) {
            VolleyLog.d("Could not clean up file %s", file.getAbsolutePath());
        }
        initializeIfRootDirectoryDeleted();
    }
}

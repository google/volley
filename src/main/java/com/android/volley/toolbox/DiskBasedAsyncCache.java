package com.android.volley.toolbox;

import android.os.Build;
import android.text.TextUtils;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
    public void get(final String key, final OnGetCompleteCallback callback) {
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
            final int size = (int) file.length();
            final ByteBuffer buffer = ByteBuffer.allocate(size);
            afc.read(
                    /* destination= */ buffer,
                    /* position= */ 0,
                    /* attachment= */ null,
                    new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result, Void v) {
                            closeChannel(afc, "completed read");
                            CacheHeader entryOnDisk = CacheHeader.readHeader(buffer);
                            if (size != result) {
                                VolleyLog.e(
                                        "File changed while reading: %s", file.getAbsolutePath());
                                callback.onGetComplete(null);
                            }
                            if (!readHeaderError(key, entryOnDisk, callback, file)) {
                                int pos = buffer.position();
                                byte[] data = Arrays.copyOfRange(buffer.array(), pos, size);
                                callback.onGetComplete(entry.toCacheEntry(data));
                            }
                        }

                        @Override
                        public void failed(Throwable exc, Void ignore) {
                            closeChannel(afc, "completed read");
                            VolleyLog.e(exc, "Failed to read file %s", file.getAbsolutePath());
                            closeAndCall(key, callback);
                        }
                    });
        } catch (IOException e) {
            VolleyLog.e(e, "Failed to read file %s", file.getAbsolutePath());
            closeChannel(channel, "IOException");
            remove(
                    key,
                    new OnCompleteCallback() {
                        @Override
                        public void onComplete() {
                            callback.onGetComplete(null);
                        }
                    });
        }
    }

    /** Puts the cache entry with a specified key into the cache. */
    @Override
    public void put(final String key, Cache.Entry entry, final OnCompleteCallback callback) {
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
                                    callback.onComplete();
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

                            callback.onComplete();
                        }

                        @Override
                        public void failed(Throwable throwable, Void ignore) {
                            VolleyLog.e(
                                    throwable, "Failed to read file %s", file.getAbsolutePath());
                            deleteFile(file);
                            callback.onComplete();
                            closeChannel(afc, "failed read");
                        }
                    });
        } catch (IOException e) {
            if (closeChannel(channel, "IOException")) {
                deleteFile(file);
                initializeIfRootDirectoryDeleted();
            }
            callback.onComplete();
        }
    }

    /** Clears the cache. Deletes all cached files from disk. */
    @Override
    public void clear(OnCompleteCallback callback) {
        File[] files = mRootDirectorySupplier.get().listFiles();
        if (files != null) {
            for (File file : files) {
                deleteFile(file);
            }
        }
        mEntries.clear();
        mTotalSize = 0;
        VolleyLog.d("Cache cleared.");
        callback.onComplete();
    }

    /**
     * Initializes the cache. We are suppressing warnings, since we create the futures above and
     * there is no chance this fails.
     */
    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void initialize(final OnCompleteCallback callback) {
        File rootDirectory = mRootDirectorySupplier.get();
        if (!rootDirectory.exists()) {
            createCacheDirectory(rootDirectory);
            callback.onComplete();
            return;
        }
        File[] files = rootDirectory.listFiles();
        if (files == null) {
            callback.onComplete();
            return;
        }
        List<CompletableFuture<Void>> reads = new ArrayList<>();
        for (File file : files) {
            Path path = file.toPath();
            AsynchronousFileChannel channel = null;
            final int entrySize = (int) file.length();
            final ByteBuffer buffer = ByteBuffer.allocate(entrySize);
            final CompletableFuture<Void> fileRead = new CompletableFuture<>();
            try {
                channel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);
                final AsynchronousFileChannel afc = channel;
                afc.read(
                        buffer,
                        0,
                        file,
                        new CompletionHandler<Integer, File>() {
                            @Override
                            public void completed(Integer integer, File ignore) {
                                CacheHeader entry = CacheHeader.readHeader(buffer);
                                if (entry != null) {
                                    closeChannel(afc, "after successful read");
                                    entry.size = entrySize;
                                    mTotalSize =
                                            DiskBasedCacheUtility.putEntry(
                                                    entry.key, entry, mTotalSize, mEntries);
                                } else {
                                    closeChannel(afc, "after failed read");
                                }
                                fileRead.complete(null);
                            }

                            @Override
                            public void failed(Throwable throwable, File file) {
                                closeChannel(afc, "after failed read");
                                deleteFile(file);
                                fileRead.complete(null);
                            }
                        });
            } catch (IOException e) {
                closeChannel(channel, "IOException in initialize");
                deleteFile(file);
            }
            reads.add(fileRead);
        }
        CompletableFuture<Void> voidCompletableFuture =
                CompletableFuture.allOf(reads.toArray(new CompletableFuture[0]));

        voidCompletableFuture.thenRun(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onComplete();
                    }
                });
    }

    /** Invalidates an entry in the cache. */
    @Override
    public void invalidate(
            final String key, final boolean fullExpire, final OnCompleteCallback callback) {
        Cache.Entry entry = null;
        get(
                key,
                new OnGetCompleteCallback() {
                    @Override
                    public void onGetComplete(@Nullable Cache.Entry entry) {
                        if (entry == null) {
                            callback.onComplete();
                        } else {
                            entry.softTtl = 0;
                            if (fullExpire) {
                                entry.ttl = 0;
                            }
                            put(
                                    key,
                                    entry,
                                    new OnCompleteCallback() {
                                        @Override
                                        public void onComplete() {
                                            callback.onComplete();
                                        }
                                    });
                        }
                    }
                });
    }

    /** Removes an entry from the cache. */
    @Override
    public void remove(String key, OnCompleteCallback callback) {
        deleteFile(DiskBasedCacheUtility.getFileForKey(key, mRootDirectorySupplier));
        mTotalSize = DiskBasedCacheUtility.removeEntry(key, mTotalSize, mEntries);
        callback.onComplete();
    }

    /** Re-initialize the cache if the directory was deleted. */
    private void initializeIfRootDirectoryDeleted() {
        if (mRootDirectorySupplier.get().exists()) {
            return;
        }
        VolleyLog.d("Re-initializing cache after external clearing.");
        mEntries.clear();
        mTotalSize = 0;
        createCacheDirectory(mRootDirectorySupplier.get());
    }

    /**
     * Closes the asynchronous file channel.
     *
     * @param afc Channel that is being closed.
     * @param endOfMessage End of error message that logs where the close is happening.
     * @return Returns true if the channel is successfully closed or the channel was null, fails if
     *     close results in an IOException.
     */
    private boolean closeChannel(@Nullable AsynchronousFileChannel afc, String endOfMessage) {
        if (afc == null) {
            return true;
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
    }

    /** Attempts to create the root directory, logging if it was unable to. */
    private void createCacheDirectory(File rootDirectory) {
        if (!rootDirectory.mkdirs()) {
            VolleyLog.e("Unable to create cache dir %s", rootDirectory.getAbsolutePath());
        }
    }

    /** Closes the file channel, removes the key, calls the callback. */
    private void closeAndCall(String key, OnGetCompleteCallback callback) {
        mTotalSize = DiskBasedCacheUtility.removeEntry(key, mTotalSize, mEntries);
        callback.onGetComplete(null);
    }

    private boolean readHeaderError(
            String key, CacheHeader entryOnDisk, OnGetCompleteCallback callback, File file) {
        if (entryOnDisk == null) {
            // IOException was thrown while reading header!
            deleteFile(file);
            closeAndCall(key, callback);
            return true;
        } else if (!TextUtils.equals(key, entryOnDisk.key)) {
            // File was shared by two keys and now holds data for a different entry!
            VolleyLog.d("%s: key=%s, found=%s", file.getAbsolutePath(), key, entryOnDisk.key);
            closeAndCall(key, callback);
            return true;
        }
        return false;
    }
}

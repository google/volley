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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

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

    /** The maximum size of the cache in bytes. */
    private final int mMaxCacheSizeInBytes;

    /**
     * Constructs an instance of the DiskBasedAsyncCache at the specified directory.
     *
     * @param rootDirectory The root directory of the cache.
     */
    public DiskBasedAsyncCache(final File rootDirectory, int maxCacheSizeInBytes) {
        mRootDirectorySupplier =
                new DiskBasedCacheUtility.FileSupplier() {
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

    /** Puts the cache entry with a specified key into the cache. */
    @Override
    public void put(final String key, Cache.Entry entry, final OnCompleteCallback callback) {
        // If adding this entry would trigger a prune, but pruning would cause the new entry to be
        // deleted, then skip writing the entry in the first place, as this is just churn.
        // Note that we don't include the cache header overhead in this calculation for simplicity,
        // so putting entries which are just below the threshold may still cause this churn.
        if (DiskBasedCacheUtility.wouldExceedCacheSize(
                        mTotalSize + entry.data.length, mMaxCacheSizeInBytes)
                && DiskBasedCacheUtility.isDataTooLarge(entry.data.length, mMaxCacheSizeInBytes)) {
            return;
        }

        final File file = DiskBasedCacheUtility.getFileForKey(key, mRootDirectorySupplier);
        Path path = Paths.get(file.getPath());

        try (AsynchronousFileChannel afc =
                AsynchronousFileChannel.open(path, StandardOpenOption.WRITE)) {
            final CacheHeader header = new CacheHeader(key, entry);
            int headerSize = header.getHeaderSize();
            int size = entry.data.length + headerSize;
            ByteBuffer buffer = ByteBuffer.allocate(size);
            header.writeHeader(buffer);
            buffer.put(entry.data);
            afc.write(
                    /* source= */ buffer,
                    /* position= */ 0,
                    /* attachment= */ null,
                    new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer resultLen, Void ignore) {
                            header.size = file.length();
                            mTotalSize =
                                    DiskBasedCacheUtility.putEntry(
                                            key, header, mTotalSize, mEntries);
                            mTotalSize =
                                    DiskBasedCacheUtility.pruneIfNeeded(
                                            mTotalSize,
                                            mMaxCacheSizeInBytes,
                                            mEntries,
                                            mRootDirectorySupplier);
                            callback.onComplete();
                        }

                        @Override
                        public void failed(Throwable throwable, Void aVoid) {
                            VolleyLog.e(
                                    throwable, "Failed to read file %s", file.getAbsolutePath());
                            callback.onComplete();
                        }
                    });
        } catch (IOException e) {
            boolean deleted = file.delete();
            if (!deleted) {
                VolleyLog.d("Could not clean up file %s", file.getAbsolutePath());
            }
            initializeIfRootDirectoryDeleted();
            callback.onComplete();
        }
    }

    /**
     * Initializes the DiskBasedAsyncCache by scanning for all files currently in the specified root
     * directory. Creates the root directory if necessary.
     */
    @Override
    public void initialize(OnCompleteCallback callback) {
        File rootDirectory = mRootDirectorySupplier.get();
        if (!rootDirectory.exists()) {
            if (!rootDirectory.mkdirs()) {
                VolleyLog.e("Unable to create cache dir %s", rootDirectory.getAbsolutePath());
            }
            callback.onComplete();
            return;
        }
        File[] files = rootDirectory.listFiles();
        if (files == null) {
            callback.onComplete();
            return;
        }
        Arrays.asList(files).parallelStream().forEach(new Consumer<File>() {
              @Override
              public void accept(File file) {
                  try (AsynchronousFileChannel afc =
                               AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.READ)) {
                      long entrySize = file.length();
                      CacheHeader entry = CacheHeader.readHeader(afc);
                      entry.size = entrySize;
                      DiskBasedCacheUtility.putEntry(entry.key, entry, mTotalSize, mEntries);
                  } catch (IOException e) {
                      //noinspection ResultOfMethodCallIgnored
                      file.delete();
                  }
              }
          }
        );
        for (File file : files) {
            try (AsynchronousFileChannel afc =
                    AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.READ)) {
                long entrySize = file.length();
                CacheHeader entry = CacheHeader.readHeader(afc);
                entry.size = entrySize;
                DiskBasedCacheUtility.putEntry(entry.key, entry, mTotalSize, mEntries);
            } catch (IOException e) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
        callback.onComplete();
    }

    /** Invalidates an entry in the cache. */
    @Override
    public void invalidate(
            final String key, final boolean fullExpire, final OnCompleteCallback callback) {
        get(
                key,
                new OnGetCompleteCallback() {
                    @Override
                    public void onGetComplete(@Nullable Cache.Entry entry) {
                        if (entry != null) {
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

    /** Removes a specified key from the cache, if it exists. */
    @Override
    public void remove(String key, OnCompleteCallback callback) {
        boolean deleted = DiskBasedCacheUtility.getFileForKey(key, mRootDirectorySupplier).delete();
        mTotalSize = DiskBasedCacheUtility.removeEntry(key, mTotalSize, mEntries);
        if (!deleted) {
            VolleyLog.d(
                    "Could not delete cache entry for key=%s, filename=%s",
                    key, DiskBasedCacheUtility.getFilenameForKey(key));
        }
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
        initialize(
                new OnCompleteCallback() {
                    @Override
                    public void onComplete() {
                        // do nothing
                    }
                });
    }
}

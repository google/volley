package com.android.volley.toolbox;

import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.android.volley.Header;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.DiskBasedCache.CountingInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class DiskBasedCacheUtility {

    /** Default maximum disk usage in bytes. */
    static final int DEFAULT_DISK_USAGE_BYTES = 5 * 1024 * 1024;

    /** High water mark percentage for the cache */
    static final float HYSTERESIS_FACTOR = 0.9f;

    /**
     * Creates a pseudo-unique filename for the specified cache key.
     *
     * @param key The key to generate a file name for.
     * @return A pseudo-unique filename.
     */
    static String getFilenameForKey(String key) {
        int firstHalfLength = key.length() / 2;
        String localFilename = String.valueOf(key.substring(0, firstHalfLength).hashCode());
        localFilename += String.valueOf(key.substring(firstHalfLength).hashCode());
        return localFilename;
    }

    /** Returns a file object for the given cache key. */
    public static File getFileForKey(String key, FileSupplier rootDirectorySupplier) {
        return new File(rootDirectorySupplier.get(), getFilenameForKey(key));
    }

    static boolean wouldExceedCacheSize(long newTotalSize, long maxCacheSize) {
        return newTotalSize > maxCacheSize;
    }

    static boolean doesDataExceedHighWaterMark(long dataLength, long maxCacheSize) {
        return dataLength > maxCacheSize * HYSTERESIS_FACTOR;
    }

    /**
     * If adding this entry would trigger a prune, but pruning would cause the new entry to be
     * deleted, then skip writing the entry in the first place, as this is just churn. Note that we
     * don't include the cache header overhead in this calculation for simplicity, so putting
     * entries which are just below the threshold may still cause this churn.
     *
     * @param totalSize totalSize of the cache
     * @param entryLength length of entry being put into cache
     * @param maxCacheSize max size of the cache
     * @return true if adding the entry would trigger a prune.
     */
    static boolean wouldBePruned(long totalSize, int entryLength, int maxCacheSize) {
        return wouldExceedCacheSize(totalSize + entryLength, maxCacheSize)
                && doesDataExceedHighWaterMark(entryLength, maxCacheSize);
    }

    /**
     * Prunes the cache if needed. This method modifies the entries map by removing the pruned
     * entries.
     *
     * @param totalSize The total size of the cache.
     * @param maxCacheSizeInBytes Maximum size of the cache.
     * @param entries Map of the entries in the cache.
     * @param rootDirectorySupplier The supplier for the root directory to use for the cache.
     * @return The updated totalSize.
     */
    static long pruneIfNeeded(
            long totalSize,
            int maxCacheSizeInBytes,
            Map<String, CacheHeader> entries,
            FileSupplier rootDirectorySupplier) {
        if (!wouldExceedCacheSize(totalSize, maxCacheSizeInBytes)) {
            return totalSize;
        }
        if (VolleyLog.DEBUG) {
            VolleyLog.v("Pruning old cache entries.");
        }

        long before = totalSize;
        int prunedFiles = 0;
        long startTime = SystemClock.elapsedRealtime();

        Iterator<Map.Entry<String, CacheHeader>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CacheHeader> entry = iterator.next();
            CacheHeader e = entry.getValue();
            boolean deleted = getFileForKey(e.key, rootDirectorySupplier).delete();
            if (deleted) {
                totalSize -= e.size;
            } else {
                VolleyLog.d(
                        "Could not delete cache entry for key=%s, filename=%s",
                        e.key, getFilenameForKey(e.key));
            }
            iterator.remove();
            prunedFiles++;

            if (!doesDataExceedHighWaterMark(totalSize, maxCacheSizeInBytes)) {
                break;
            }
        }

        if (VolleyLog.DEBUG) {
            VolleyLog.v(
                    "pruned %d files, %d bytes, %d ms",
                    prunedFiles, (totalSize - before), SystemClock.elapsedRealtime() - startTime);
        }
        return totalSize;
    }

    /**
     * Puts the entry with the specified key into the cache. This method updates the entries map
     * with the key, entry pair.
     *
     * @param key The key to identify the entry by.
     * @param entry The entry to cache.
     * @param totalSize The total size of the cache.
     * @param entries Map of the entries in the cache.
     * @return The updated totalSize.
     */
    static long putEntry(
            String key, CacheHeader entry, long totalSize, Map<String, CacheHeader> entries) {
        if (!entries.containsKey(key)) {
            totalSize += entry.size;
        } else {
            CacheHeader oldEntry = entries.get(key);
            totalSize += (entry.size - oldEntry.size);
        }
        entries.put(key, entry);
        return totalSize;
    }

    /** Removes the entry identified by 'key' from the cache. */
    static long removeEntry(String key, long totalSize, Map<String, CacheHeader> entries) {
        CacheHeader removed = entries.remove(key);
        if (removed != null) {
            totalSize -= removed.size;
        }
        return totalSize;
    }

    /*
     * Homebrewed simple serialization system used for reading and writing cache
     * headers on disk. Once upon a time, this used the standard Java
     * Object{Input,Output}Stream, but the default implementation relies heavily
     * on reflection (even for standard types) and generates a ton of garbage.
     *
     * TODO: Replace by standard DataInput and DataOutput in next cache version.
     */

    /**
     * Simple wrapper around {@link InputStream#read()} that throws EOFException instead of
     * returning -1.
     */
    private static int read(InputStream is) throws IOException {
        int b = is.read();
        if (b == -1) {
            throw new EOFException();
        }
        return b;
    }

    static void writeInt(OutputStream os, int n) throws IOException {
        os.write(n & 0xff);
        os.write((n >> 8) & 0xff);
        os.write((n >> 16) & 0xff);
        os.write((n >> 24) & 0xff);
    }

    static int readInt(InputStream is) throws IOException {
        int n = 0;
        n |= read(is);
        n |= (read(is) << 8);
        n |= (read(is) << 16);
        n |= (read(is) << 24);
        return n;
    }

    static void writeLong(OutputStream os, long n) throws IOException {
        os.write((byte) n);
        os.write((byte) (n >>> 8));
        os.write((byte) (n >>> 16));
        os.write((byte) (n >>> 24));
        os.write((byte) (n >>> 32));
        os.write((byte) (n >>> 40));
        os.write((byte) (n >>> 48));
        os.write((byte) (n >>> 56));
    }

    static long readLong(InputStream is) throws IOException {
        long n = 0;
        n |= (read(is) & 0xFFL);
        n |= ((read(is) & 0xFFL) << 8);
        n |= ((read(is) & 0xFFL) << 16);
        n |= ((read(is) & 0xFFL) << 24);
        n |= ((read(is) & 0xFFL) << 32);
        n |= ((read(is) & 0xFFL) << 40);
        n |= ((read(is) & 0xFFL) << 48);
        n |= ((read(is) & 0xFFL) << 56);
        return n;
    }

    static void writeString(OutputStream os, String s) throws IOException {
        byte[] b = s.getBytes("UTF-8");
        writeLong(os, b.length);
        os.write(b, 0, b.length);
    }

    static void writeString(ByteBuffer buffer, @Nullable String s) throws IOException {
        // if the string is null, put the length as 0.
        if (s == null) {
            buffer.putLong(0);
            return;
        }
        byte[] b = s.getBytes("UTF-8");
        buffer.putLong(b.length);
        buffer.put(b);
    }

    static String readString(CountingInputStream cis) throws IOException {
        long n = readLong(cis);
        byte[] b = DiskBasedCache.streamToBytes(cis, n);
        return new String(b, "UTF-8");
    }

    static void writeHeaderList(@Nullable List<Header> headers, OutputStream os)
            throws IOException {
        if (headers != null) {
            writeInt(os, headers.size());
            for (Header header : headers) {
                writeString(os, header.getName());
                writeString(os, header.getValue());
            }
        } else {
            writeInt(os, 0);
        }
    }

    static void writeHeaderList(@Nullable List<Header> headers, ByteBuffer buffer)
            throws IOException {
        if (headers != null) {
            buffer.putInt(headers.size());
            for (Header header : headers) {
                writeString(buffer, header.getName());
                writeString(buffer, header.getValue());
            }
        } else {
            buffer.putInt(0);
        }
    }

    static List<Header> readHeaderList(CountingInputStream cis) throws IOException {
        int size = readInt(cis);
        if (size < 0) {
            throw new IOException("readHeaderList size=" + size);
        }
        List<Header> result =
                (size == 0) ? Collections.<Header>emptyList() : new ArrayList<Header>();
        for (int i = 0; i < size; i++) {
            String name = readString(cis).intern();
            String value = readString(cis).intern();
            result.add(new Header(name, value));
        }
        return result;
    }

    static int headerListSize(@Nullable List<Header> headers) throws IOException {
        if (headers == null) {
            return 4;
        }
        int bytes = 4;

        for (Header header : headers) {
            bytes += header.getName().getBytes("UTF-8").length;
            bytes += header.getValue().getBytes("UTF-8").length;
        }

        return bytes;
    }
}

package com.android.volley.toolbox;

import android.annotation.SuppressLint;
import android.os.SystemClock;
import androidx.annotation.VisibleForTesting;
import com.android.volley.AsyncCache;
import com.android.volley.Header;
import com.android.volley.VolleyLog;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
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

    /**
     * Constructs an instance of the DiskBasedAsyncCache at the specified directory.
     *
     * @param rootDirectorySupplier The supplier for the root directory of the cache.
     * @param maxCacheSizeInBytes The maximum size of the cache in bytes. Note that the cache may
     *     briefly exceed this size on disk when writing a new entry that pushes it over the limit
     *     until the ensuing pruning completes.
     */
    public DiskBasedAsyncCache(
            DiskBasedAsyncCache.FileSupplier rootDirectorySupplier, int maxCacheSizeInBytes) {
        mRootDirectorySupplier = rootDirectorySupplier;
        mMaxCacheSizeInBytes = maxCacheSizeInBytes;
    }

    /**
     * Constructs an instance of the DiskBasedAsyncCache at the specified directory using the
     * default maximum cache size of 5MB.
     *
     * @param rootDirectory The root directory of the cache.
     */
    public DiskBasedAsyncCache(File rootDirectory) {
        this(rootDirectory, DEFAULT_DISK_USAGE_BYTES);
    }

    /**
     * Constructs an instance of the DiskBasedAsyncCache at the specified directory using the
     * default maximum cache size of 5MB.
     *
     * @param rootDirectorySupplier The supplier for the root directory of the cache.
     */
    public DiskBasedAsyncCache(DiskBasedAsyncCache.FileSupplier rootDirectorySupplier) {
        this(rootDirectorySupplier, DEFAULT_DISK_USAGE_BYTES);
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
        File file = getFileForKey(key);
        Path path = Paths.get(file.getPath());
        try {
            AsynchronousFileChannel afc =
                    AsynchronousFileChannel.open(path, StandardOpenOption.READ);
            ByteBuffer buffer = ByteBuffer.allocate((int) file.length());
            afc.read(
                    buffer,
                    0,
                    buffer,
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
        } catch (Exception e) {
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

    /**
     * Reads length bytes from CountingInputStream into byte array.
     *
     * @param cis input stream
     * @param length number of bytes to read
     * @throws IOException if fails to read all bytes
     */
    @VisibleForTesting
    static byte[] streamToBytes(DiskBasedAsyncCache.CountingInputStream cis, long length)
            throws IOException {
        long maxLength = cis.bytesRemaining();
        // Length cannot be negative or greater than bytes remaining, and must not overflow int.
        if (length < 0 || length > maxLength || (int) length != length) {
            throw new IOException("streamToBytes length=" + length + ", maxLength=" + maxLength);
        }
        byte[] bytes = new byte[(int) length];
        new DataInputStream(cis).readFully(bytes);
        return bytes;
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

        /**
         * Reads the header from a CountingInputStream and returns a CacheHeader object.
         *
         * @param is The InputStream to read from.
         * @throws IOException if fails to read header
         */
        static DiskBasedAsyncCache.CacheHeader readHeader(
                DiskBasedAsyncCache.CountingInputStream is) throws IOException {
            int magic = readInt(is);
            if (magic != CACHE_MAGIC) {
                // don't bother deleting, it'll get pruned eventually
                throw new IOException();
            }
            String key = readString(is);
            String etag = readString(is);
            long serverDate = readLong(is);
            long lastModified = readLong(is);
            long ttl = readLong(is);
            long softTtl = readLong(is);
            List<Header> allResponseHeaders = readHeaderList(is);
            return new DiskBasedAsyncCache.CacheHeader(
                    key, etag, serverDate, lastModified, ttl, softTtl, allResponseHeaders);
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

        /** Writes the contents of this CacheHeader to the specified OutputStream. */
        boolean writeHeader(OutputStream os) {
            try {
                writeInt(os, CACHE_MAGIC);
                writeString(os, key);
                writeString(os, etag == null ? "" : etag);
                writeLong(os, serverDate);
                writeLong(os, lastModified);
                writeLong(os, ttl);
                writeLong(os, softTtl);
                writeHeaderList(allResponseHeaders, os);
                os.flush();
                return true;
            } catch (IOException e) {
                VolleyLog.d("%s", e.toString());
                return false;
            }
        }
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
        os.write((n >> 0) & 0xff);
        os.write((n >> 8) & 0xff);
        os.write((n >> 16) & 0xff);
        os.write((n >> 24) & 0xff);
    }

    static int readInt(InputStream is) throws IOException {
        int n = 0;
        n |= (read(is) << 0);
        n |= (read(is) << 8);
        n |= (read(is) << 16);
        n |= (read(is) << 24);
        return n;
    }

    static void writeLong(OutputStream os, long n) throws IOException {
        os.write((byte) (n >>> 0));
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
        n |= ((read(is) & 0xFFL) << 0);
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

    static String readString(DiskBasedAsyncCache.CountingInputStream cis) throws IOException {
        long n = readLong(cis);
        byte[] b = streamToBytes(cis, n);
        return new String(b, "UTF-8");
    }

    static void writeHeaderList(List<Header> headers, OutputStream os) throws IOException {
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

    static List<Header> readHeaderList(DiskBasedAsyncCache.CountingInputStream cis)
            throws IOException {
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
}

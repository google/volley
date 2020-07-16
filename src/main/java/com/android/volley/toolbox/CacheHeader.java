package com.android.volley.toolbox;

import androidx.annotation.Nullable;
import com.android.volley.Cache;
import com.android.volley.Header;
import com.android.volley.VolleyLog;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

/** Handles holding onto the cache headers for an entry. */
class CacheHeader {
    /** Magic number for current version of cache file format. */
    private static final int CACHE_MAGIC = 0x20150306;

    /** Bits required to write 6 longs and 1 int. */
    private static final int HEADER_SIZE = 52;

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
    @Nullable final String etag;

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
    CacheHeader(String key, Cache.Entry entry) {
        this(
                key,
                entry.etag,
                entry.serverDate,
                entry.lastModified,
                entry.ttl,
                entry.softTtl,
                getAllResponseHeaders(entry));
    }

    private static List<Header> getAllResponseHeaders(Cache.Entry entry) {
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
    static CacheHeader readHeader(DiskBasedCache.CountingInputStream is) throws IOException {
        int magic = DiskBasedCacheUtility.readInt(is);
        if (magic != CACHE_MAGIC) {
            // don't bother deleting, it'll get pruned eventually
            throw new IOException();
        }
        String key = DiskBasedCacheUtility.readString(is);
        String etag = DiskBasedCacheUtility.readString(is);
        long serverDate = DiskBasedCacheUtility.readLong(is);
        long lastModified = DiskBasedCacheUtility.readLong(is);
        long ttl = DiskBasedCacheUtility.readLong(is);
        long softTtl = DiskBasedCacheUtility.readLong(is);
        List<Header> allResponseHeaders = DiskBasedCacheUtility.readHeaderList(is);
        return new CacheHeader(
                key, etag, serverDate, lastModified, ttl, softTtl, allResponseHeaders);
    }

    /** Creates a cache entry for the specified data. */
    Cache.Entry toCacheEntry(byte[] data) {
        Cache.Entry e = new Cache.Entry();
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
            DiskBasedCacheUtility.writeInt(os, CACHE_MAGIC);
            DiskBasedCacheUtility.writeString(os, key);
            DiskBasedCacheUtility.writeString(os, etag == null ? "" : etag);
            DiskBasedCacheUtility.writeLong(os, serverDate);
            DiskBasedCacheUtility.writeLong(os, lastModified);
            DiskBasedCacheUtility.writeLong(os, ttl);
            DiskBasedCacheUtility.writeLong(os, softTtl);
            DiskBasedCacheUtility.writeHeaderList(allResponseHeaders, os);
            os.flush();
            return true;
        } catch (IOException e) {
            VolleyLog.d("%s", e.toString());
            return false;
        }
    }

    /** Writes the contents of this CacheHeader to the specified ByteBuffer. */
    void writeHeader(ByteBuffer buffer) throws IOException {
        buffer.putInt(CACHE_MAGIC);
        DiskBasedCacheUtility.writeString(buffer, key);
        DiskBasedCacheUtility.writeString(buffer, etag);
        buffer.putLong(serverDate);
        buffer.putLong(lastModified);
        buffer.putLong(ttl);
        buffer.putLong(softTtl);
        DiskBasedCacheUtility.writeHeaderList(allResponseHeaders, buffer);
    }

    /** Gets the size of the header in bytes. */
    int getHeaderSize() throws IOException {
        int size = 0;
        size += key.getBytes("UTF-8").length;
        if (etag != null) {
            size += etag.getBytes("UTF-8").length;
        }
        size += DiskBasedCacheUtility.headerListSize(allResponseHeaders);
        return size + HEADER_SIZE;
    }
}

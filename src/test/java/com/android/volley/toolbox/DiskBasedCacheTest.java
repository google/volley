/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.volley.toolbox;

import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.android.volley.Cache;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Random;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 16)
public class DiskBasedCacheTest {

    private static final int MAX_SIZE = 1024 * 1024;

    private Cache cache;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule public ExpectedException exception = ExpectedException.none();

    @Before
    public void setup() throws IOException {
        // Initialize empty cache
        cache = new DiskBasedCache(temporaryFolder.getRoot(), MAX_SIZE);
        cache.initialize();
    }

    @After
    public void teardown() {
        cache = null;
    }

    @Test
    public void testEmptyInitialize() {
        assertThat(cache.get("key"), is(nullValue()));
    }

    @Test
    public void testPutGetZeroBytes() {
        Cache.Entry entry = new Cache.Entry();
        entry.data = new byte[0];
        entry.serverDate = 1234567L;
        entry.lastModified = 13572468L;
        entry.ttl = 9876543L;
        entry.softTtl = 8765432L;
        entry.etag = "etag";
        entry.responseHeaders = new HashMap<>();
        entry.responseHeaders.put("fruit", "banana");
        entry.responseHeaders.put("color", "yellow");
        cache.put("my-magical-key", entry);

        assertThatEntriesAreEqual(cache.get("my-magical-key"), entry);
        assertThat(cache.get("unknown-key"), is(nullValue()));
    }

    @Test
    public void testPutRemoveGet() {
        Cache.Entry entry = randomData(511);
        cache.put("key", entry);

        assertThatEntriesAreEqual(cache.get("key"), entry);

        cache.remove("key");
        assertThat(cache.get("key"), is(nullValue()));
        assertThat(listCachedFiles(), is(emptyArray()));
    }

    @Test
    public void testPutClearGet() {
        Cache.Entry entry = randomData(511);
        cache.put("key", entry);

        assertThatEntriesAreEqual(cache.get("key"), entry);

        cache.clear();
        assertThat(cache.get("key"), is(nullValue()));
        assertThat(listCachedFiles(), is(emptyArray()));
    }

    @Test
    public void testReinitialize() {
        Cache.Entry entry = randomData(1023);
        cache.put("key", entry);

        Cache copy = new DiskBasedCache(temporaryFolder.getRoot(), MAX_SIZE);
        copy.initialize();

        assertThatEntriesAreEqual(copy.get("key"), entry);
    }

    @Test
    public void testInvalidate() {
        Cache.Entry entry = randomData(32);
        entry.softTtl = 8765432L;
        entry.ttl = 9876543L;
        cache.put("key", entry);

        cache.invalidate("key", false);
        entry.softTtl = 0; // expired
        assertThatEntriesAreEqual(cache.get("key"), entry);
    }

    @Test
    public void testInvalidateFullExpire() {
        Cache.Entry entry = randomData(32);
        entry.softTtl = 8765432L;
        entry.ttl = 9876543L;
        cache.put("key", entry);

        cache.invalidate("key", true);
        entry.softTtl = 0; // expired
        entry.ttl = 0; // expired
        assertThatEntriesAreEqual(cache.get("key"), entry);
    }

    @Test
    public void testTooLargeEntry() {
        Cache.Entry entry = randomData(MAX_SIZE - getEntrySizeOnDisk("oversize") + 1);
        cache.put("oversize", entry);

        assertThat(cache.get("oversize"), is(nullValue()));
    }

    @Test
    public void testMaxSizeEntry() {
        Cache.Entry entry = randomData(MAX_SIZE - getEntrySizeOnDisk("maxsize") - 1);
        cache.put("maxsize", entry);

        assertThatEntriesAreEqual(cache.get("maxsize"), entry);
    }

    @Test
    public void testTrimAtThreshold() {
        // Start with the largest possible entry.
        Cache.Entry entry = randomData(MAX_SIZE - getEntrySizeOnDisk("maxsize") - 1);
        cache.put("maxsize", entry);

        assertThatEntriesAreEqual(cache.get("maxsize"), entry);

        // Now any new entry should cause the first one to be cleared.
        entry = randomData(0);
        cache.put("bit", entry);

        assertThat(cache.get("goodsize"), is(nullValue()));
        assertThatEntriesAreEqual(cache.get("bit"), entry);
    }

    @Test
    public void testTrimWithMultipleEvictions_underHysteresisThreshold() {
        Cache.Entry entry1 = randomData(MAX_SIZE / 3 - getEntrySizeOnDisk("entry1") - 1);
        cache.put("entry1", entry1);
        Cache.Entry entry2 = randomData(MAX_SIZE / 3 - getEntrySizeOnDisk("entry2") - 1);
        cache.put("entry2", entry2);
        Cache.Entry entry3 = randomData(MAX_SIZE / 3 - getEntrySizeOnDisk("entry3") - 1);
        cache.put("entry3", entry3);

        assertThatEntriesAreEqual(cache.get("entry1"), entry1);
        assertThatEntriesAreEqual(cache.get("entry2"), entry2);
        assertThatEntriesAreEqual(cache.get("entry3"), entry3);

        Cache.Entry entry =
                randomData(
                        (int) (DiskBasedCacheUtility.HYSTERESIS_FACTOR * MAX_SIZE)
                                - getEntrySizeOnDisk("max"));
        cache.put("max", entry);

        assertThat(cache.get("entry1"), is(nullValue()));
        assertThat(cache.get("entry2"), is(nullValue()));
        assertThat(cache.get("entry3"), is(nullValue()));
        assertThatEntriesAreEqual(cache.get("max"), entry);
    }

    @Test
    public void testTrimWithMultipleEvictions_atHysteresisThreshold() {
        Cache.Entry entry1 = randomData(MAX_SIZE / 3 - getEntrySizeOnDisk("entry1") - 1);
        cache.put("entry1", entry1);
        Cache.Entry entry2 = randomData(MAX_SIZE / 3 - getEntrySizeOnDisk("entry2") - 1);
        cache.put("entry2", entry2);
        Cache.Entry entry3 = randomData(MAX_SIZE / 3 - getEntrySizeOnDisk("entry3") - 1);
        cache.put("entry3", entry3);

        assertThatEntriesAreEqual(cache.get("entry1"), entry1);
        assertThatEntriesAreEqual(cache.get("entry2"), entry2);
        assertThatEntriesAreEqual(cache.get("entry3"), entry3);

        Cache.Entry entry =
                randomData(
                        (int) (DiskBasedCacheUtility.HYSTERESIS_FACTOR * MAX_SIZE)
                                - getEntrySizeOnDisk("max")
                                + 1);
        cache.put("max", entry);

        assertThat(cache.get("entry1"), is(nullValue()));
        assertThat(cache.get("entry2"), is(nullValue()));
        assertThat(cache.get("entry3"), is(nullValue()));
        assertThat(cache.get("max"), is(nullValue()));
    }

    @Test
    public void testTrimWithPartialEvictions() {
        Cache.Entry entry1 = randomData(MAX_SIZE / 3 - getEntrySizeOnDisk("entry1") - 1);
        cache.put("entry1", entry1);
        Cache.Entry entry2 = randomData(MAX_SIZE / 3 - getEntrySizeOnDisk("entry2") - 1);
        cache.put("entry2", entry2);
        Cache.Entry entry3 = randomData(MAX_SIZE / 3 - getEntrySizeOnDisk("entry3") - 1);
        cache.put("entry3", entry3);

        assertThatEntriesAreEqual(cache.get("entry1"), entry1);
        assertThatEntriesAreEqual(cache.get("entry2"), entry2);
        assertThatEntriesAreEqual(cache.get("entry3"), entry3);

        Cache.Entry entry4 = randomData((MAX_SIZE - getEntrySizeOnDisk("entry4") - 1) / 2);
        cache.put("entry4", entry4);

        assertThat(cache.get("entry1"), is(nullValue()));
        assertThat(cache.get("entry2"), is(nullValue()));
        assertThatEntriesAreEqual(cache.get("entry3"), entry3);
        assertThatEntriesAreEqual(cache.get("entry4"), entry4);
    }

    @Test
    public void testLargeEntryDoesntClearCache() {
        // Writing a large entry to an empty cache should succeed
        Cache.Entry largeEntry = randomData(MAX_SIZE - getEntrySizeOnDisk("largeEntry") - 1);
        cache.put("largeEntry", largeEntry);

        assertThatEntriesAreEqual(cache.get("largeEntry"), largeEntry);

        // Reset and fill up ~half the cache.
        cache.clear();
        Cache.Entry entry = randomData(MAX_SIZE / 2 - getEntrySizeOnDisk("entry") - 1);
        cache.put("entry", entry);

        assertThatEntriesAreEqual(cache.get("entry"), entry);

        // Writing the large entry should no-op, because otherwise the pruning algorithm would clear
        // the whole cache, since the large entry is above the hysteresis threshold.
        cache.put("largeEntry", largeEntry);

        assertThat(cache.get("largeEntry"), is(nullValue()));
        assertThatEntriesAreEqual(cache.get("entry"), entry);
    }

    @Test
    @SuppressWarnings("TryFinallyCanBeTryWithResources")
    public void testGetBadMagic() throws IOException {
        // Cache something
        Cache.Entry entry = randomData(1023);
        cache.put("key", entry);
        assertThatEntriesAreEqual(cache.get("key"), entry);

        // Overwrite the magic header
        File cacheFolder = temporaryFolder.getRoot();
        File file = cacheFolder.listFiles()[0];
        FileOutputStream fos = new FileOutputStream(file);
        try {
            DiskBasedCacheUtility.writeInt(fos, 0); // overwrite magic
        } finally {
            //noinspection ThrowFromFinallyBlock
            fos.close();
        }

        assertThat(cache.get("key"), is(nullValue()));
        assertThat(listCachedFiles(), is(emptyArray()));
    }

    @Test
    @SuppressWarnings("TryFinallyCanBeTryWithResources")
    public void testGetWrongKey() throws IOException {
        // Cache something
        Cache.Entry entry = randomData(1023);
        cache.put("key", entry);
        assertThatEntriesAreEqual(cache.get("key"), entry);

        // Access the cached file
        File cacheFolder = temporaryFolder.getRoot();
        File file = cacheFolder.listFiles()[0];
        FileOutputStream fos = new FileOutputStream(file);
        try {
            // Overwrite with a different key
            CacheHeader wrongHeader = new CacheHeader("bad", entry);
            wrongHeader.writeHeader(fos);
        } finally {
            //noinspection ThrowFromFinallyBlock
            fos.close();
        }

        // key is gone, but file is still there
        assertThat(cache.get("key"), is(nullValue()));
        assertThat(listCachedFiles(), is(arrayWithSize(1)));

        // Note: file is now a zombie because its key does not map to its name
    }

    @Test
    public void testStreamToBytesNegativeLength() throws IOException {
        byte[] data = new byte[1];
        DiskBasedCache.CountingInputStream cis =
                new DiskBasedCache.CountingInputStream(new ByteArrayInputStream(data), data.length);
        exception.expect(IOException.class);
        DiskBasedCache.streamToBytes(cis, -1);
    }

    @Test
    public void testStreamToBytesExcessiveLength() throws IOException {
        byte[] data = new byte[1];
        DiskBasedCache.CountingInputStream cis =
                new DiskBasedCache.CountingInputStream(new ByteArrayInputStream(data), data.length);
        exception.expect(IOException.class);
        DiskBasedCache.streamToBytes(cis, 2);
    }

    @Test
    public void testStreamToBytesOverflow() throws IOException {
        byte[] data = new byte[0];
        DiskBasedCache.CountingInputStream cis =
                new DiskBasedCache.CountingInputStream(
                        new ByteArrayInputStream(data), 0x100000000L);
        exception.expect(IOException.class);
        DiskBasedCache.streamToBytes(cis, 0x100000000L); // int value is 0
    }

    @Test
    public void testFileIsDeletedWhenWriteHeaderFails() throws IOException {
        // Create DataOutputStream that throws IOException
        OutputStream mockedOutputStream = spy(OutputStream.class);
        doThrow(IOException.class).when(mockedOutputStream).write(anyInt());

        // Create read-only copy that fails to write anything
        DiskBasedCache readonly = spy((DiskBasedCache) cache);
        doReturn(mockedOutputStream).when(readonly).createOutputStream(any(File.class));

        // Attempt to write
        readonly.put("key", randomData(1111));

        // write is called at least once because each linked stream flushes when closed
        verify(mockedOutputStream, atLeastOnce()).write(anyInt());
        assertThat(readonly.get("key"), is(nullValue()));
        assertThat(listCachedFiles(), is(emptyArray()));

        // Note: original cache will try (without success) to read from file
        assertThat(cache.get("key"), is(nullValue()));
    }

    @Test
    public void testIOExceptionInInitialize() throws IOException {
        // Cache a few kilobytes
        cache.put("kilobyte", randomData(1024));
        cache.put("kilobyte2", randomData(1024));
        cache.put("kilobyte3", randomData(1024));

        // Create DataInputStream that throws IOException
        InputStream mockedInputStream = spy(InputStream.class);
        //noinspection ResultOfMethodCallIgnored
        doThrow(IOException.class).when(mockedInputStream).read();

        // Create broken cache that fails to read anything
        DiskBasedCache broken = spy(new DiskBasedCache(temporaryFolder.getRoot()));
        doReturn(mockedInputStream).when(broken).createInputStream(any(File.class));

        // Attempt to initialize
        broken.initialize();

        // Everything is gone
        assertThat(broken.get("kilobyte"), is(nullValue()));
        assertThat(broken.get("kilobyte2"), is(nullValue()));
        assertThat(broken.get("kilobyte3"), is(nullValue()));
        assertThat(listCachedFiles(), is(emptyArray()));

        // Verify that original cache can cope with missing files
        assertThat(cache.get("kilobyte"), is(nullValue()));
        assertThat(cache.get("kilobyte2"), is(nullValue()));
        assertThat(cache.get("kilobyte3"), is(nullValue()));
    }

    @Test
    public void testManyResponseHeaders() {
        Cache.Entry entry = new Cache.Entry();
        entry.data = new byte[0];
        entry.responseHeaders = new HashMap<>();
        for (int i = 0; i < 0xFFFF; i++) {
            entry.responseHeaders.put(Integer.toString(i), "");
        }
        cache.put("key", entry);
    }

    @Test
    @SuppressWarnings("TryFinallyCanBeTryWithResources")
    public void testCountingInputStreamByteCount() throws IOException {
        // Write some bytes
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        //noinspection ThrowFromFinallyBlock
        try {
            DiskBasedCacheUtility.writeInt(out, 1);
            DiskBasedCacheUtility.writeLong(out, -1L);
            DiskBasedCacheUtility.writeString(out, "hamburger");
        } finally {
            //noinspection ThrowFromFinallyBlock
            out.close();
        }
        long bytesWritten = out.size();

        // Read the bytes and compare the counts
        DiskBasedCache.CountingInputStream cis =
                new DiskBasedCache.CountingInputStream(
                        new ByteArrayInputStream(out.toByteArray()), bytesWritten);
        try {
            assertThat(cis.bytesRemaining(), is(bytesWritten));
            assertThat(cis.bytesRead(), is(0L));
            assertThat(DiskBasedCacheUtility.readInt(cis), is(1));
            assertThat(DiskBasedCacheUtility.readLong(cis), is(-1L));
            assertThat(DiskBasedCacheUtility.readString(cis), is("hamburger"));
            assertThat(cis.bytesRead(), is(bytesWritten));
            assertThat(cis.bytesRemaining(), is(0L));
        } finally {
            //noinspection ThrowFromFinallyBlock
            cis.close();
        }
    }

    /* Serialization tests */

    @Test
    public void testEmptyReadThrowsEOF() throws IOException {
        ByteArrayInputStream empty = new ByteArrayInputStream(new byte[] {});
        exception.expect(EOFException.class);
        DiskBasedCacheUtility.readInt(empty);
    }

    @Test
    public void publicMethods() throws Exception {
        // Catch-all test to find API-breaking changes.
        assertNotNull(DiskBasedCache.class.getConstructor(File.class, int.class));
        assertNotNull(
                DiskBasedCache.class.getConstructor(DiskBasedCache.FileSupplier.class, int.class));
        assertNotNull(DiskBasedCache.class.getConstructor(File.class));
        assertNotNull(DiskBasedCache.class.getConstructor(DiskBasedCache.FileSupplier.class));
        assertNotNull(DiskBasedCache.class.getMethod("getFileForKey", String.class));
    }

    @Test
    public void initializeIfRootDirectoryDeleted() {
        temporaryFolder.delete();

        Cache.Entry entry = randomData(101);
        cache.put("key1", entry);

        assertThat(cache.get("key1"), is(nullValue()));

        // confirm that we can now store entries
        cache.put("key2", entry);
        assertThatEntriesAreEqual(cache.get("key2"), entry);
    }

    /* Test helpers */

    private void assertThatEntriesAreEqual(Cache.Entry actual, Cache.Entry expected) {
        assertThat(actual.data, is(equalTo(expected.data)));
        assertThat(actual.etag, is(equalTo(expected.etag)));
        assertThat(actual.lastModified, is(equalTo(expected.lastModified)));
        assertThat(actual.responseHeaders, is(equalTo(expected.responseHeaders)));
        assertThat(actual.serverDate, is(equalTo(expected.serverDate)));
        assertThat(actual.softTtl, is(equalTo(expected.softTtl)));
        assertThat(actual.ttl, is(equalTo(expected.ttl)));
    }

    private Cache.Entry randomData(int length) {
        Cache.Entry entry = new Cache.Entry();
        byte[] data = new byte[length];
        new Random(42).nextBytes(data); // explicit seed for reproducible results
        entry.data = data;
        return entry;
    }

    private File[] listCachedFiles() {
        return temporaryFolder.getRoot().listFiles();
    }

    private int getEntrySizeOnDisk(String key) {
        // Header size is:
        // 4 bytes for magic int
        // 8 + len(key) bytes for key (long length)
        // 8 bytes for etag (long length + 0 characters)
        // 32 bytes for serverDate, lastModified, ttl, and softTtl longs
        // 4 bytes for length of header list int
        // == 56 + len(key) bytes total.
        return 56 + key.length();
    }
}

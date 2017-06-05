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

import com.android.volley.Cache;
import com.android.volley.toolbox.DiskBasedCache.CacheHeader;
import com.android.volley.toolbox.DiskBasedCache.CountingInputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class DiskBasedCacheTest {

    private static final String CACHE_FOLDER = "test";
    private static final int MAX_SIZE = 1024 * 1024;

    private Cache cache;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setup() throws IOException {
        // Initialize empty cache
        File rootDirectory = new File(temporaryFolder.getRoot(), CACHE_FOLDER);
        cache = new DiskBasedCache(rootDirectory, MAX_SIZE);
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
        // Random entry from Volley's DiskBasedCacheTest
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

        Cache copy = new DiskBasedCache(new File(temporaryFolder.getRoot(), CACHE_FOLDER));
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
    public void testTrim() {
        Cache.Entry entry = randomData(2 * MAX_SIZE);
        cache.put("oversize", entry);

        assertThatEntriesAreEqual(cache.get("oversize"), entry);

        entry = randomData(1024);
        cache.put("kilobyte", entry);

        assertThat(cache.get("oversize"), is(nullValue()));
        assertThatEntriesAreEqual(cache.get("kilobyte"), entry);

        Cache.Entry entry2 = randomData(1024);
        cache.put("kilobyte2", entry2);
        Cache.Entry entry3 = randomData(1024);
        cache.put("kilobyte3", entry3);

        assertThatEntriesAreEqual(cache.get("kilobyte"), entry);
        assertThatEntriesAreEqual(cache.get("kilobyte2"), entry2);
        assertThatEntriesAreEqual(cache.get("kilobyte3"), entry3);

        entry = randomData(MAX_SIZE);
        cache.put("max", entry);

        assertThat(cache.get("kilobyte"), is(nullValue()));
        assertThat(cache.get("kilobyte2"), is(nullValue()));
        assertThat(cache.get("kilobyte3"), is(nullValue()));
        assertThatEntriesAreEqual(cache.get("max"), entry);
    }

    @Test
    @SuppressWarnings("TryFinallyCanBeTryWithResources")
    public void testGetBadMagic() throws IOException {
        // Cache something
        Cache.Entry entry = randomData(1023);
        cache.put("key", entry);
        assertThatEntriesAreEqual(cache.get("key"), entry);

        // Overwrite the magic header
        File cacheFolder = new File(temporaryFolder.getRoot(), CACHE_FOLDER);
        File file = cacheFolder.listFiles()[0];
        RandomAccessFile rwFile = new RandomAccessFile(file, "rw");
        try {
            rwFile.writeInt(0); // overwrite magic
        } finally {
            //noinspection ThrowFromFinallyBlock
            rwFile.close();
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
        File cacheFolder = new File(temporaryFolder.getRoot(), CACHE_FOLDER);
        File file = cacheFolder.listFiles()[0];
        RandomAccessFile rwFile = new RandomAccessFile(file, "rw");

        // Overwrite with a different key
        CacheHeader wrongHeader = new CacheHeader("bad", entry);
        try {
            wrongHeader.writeFields(rwFile);
        } finally {
            //noinspection ThrowFromFinallyBlock
            rwFile.close();
        }

        // key is gone, but file is still there
        assertThat(cache.get("key"), is(nullValue()));
        assertThat(listCachedFiles(), is(arrayWithSize(1)));

        // Note: file is now a zombie because its key does not map to its name
    }

    @Test
    @SuppressWarnings("TryFinallyCanBeTryWithResources")
    public void testGetWrongLength() {
        // Cache something
        Cache.Entry entry = randomData(1023);
        cache.put("key", entry);
        assertThatEntriesAreEqual(cache.get("key"), entry);

        // Sneakily overwrite with a different length
        Cache copy = new DiskBasedCache(new File(temporaryFolder.getRoot(), CACHE_FOLDER));
        copy.initialize();
        copy.put("key", randomData(127));

        // Item is removed when we try to get it
        assertThat(cache.get("key"), is(nullValue()));
        assertThat(listCachedFiles(), is(emptyArray()));
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
    public void testFailuresInInitialize() throws IOException {
        // Cache a few kilobytes
        cache.put("kilobyte", randomData(1024));
        cache.put("kilobyte2", randomData(1024));
        cache.put("kilobyte3", randomData(1024));

        // Create DataInputStream that throws IOException
        InputStream mockedInputStream = spy(InputStream.class);
        //noinspection ResultOfMethodCallIgnored
        doThrow(IOException.class).when(mockedInputStream).read();

        // Create broken cache that fails to read anything
        DiskBasedCache broken =
                spy(new DiskBasedCache(new File(temporaryFolder.getRoot(), CACHE_FOLDER)));
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

    /* DiskBasedCache.CacheHeader tests */

    @Test
    public void testAlmostTooManyResponseHeaders() {
        Cache.Entry entry = new Cache.Entry();
        entry.data = new byte[0];
        entry.responseHeaders = new HashMap<>();
        for (int i = 0; i < 0xFFFF; i++) {
            entry.responseHeaders.put(Integer.toString(i), "");
        }
        cache.put("key", entry);
    }

    @Test
    public void testTooManyResponseHeaders() {
        Cache.Entry entry = new Cache.Entry();
        entry.data = new byte[0];
        entry.responseHeaders = new HashMap<>();
        for (int i = 0; i < 0x10000; i++) {
            entry.responseHeaders.put(Integer.toString(i), "");
        }
        exception.expect(IllegalArgumentException.class);
        cache.put("key", entry);
    }

    /* DiskBasedCache.CountingInputStream tests */

    @Test
    @SuppressWarnings("TryFinallyCanBeTryWithResources")
    public void testCountingInputStreamByteCount() throws IOException {
        // Write some bytes
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(out);
        //noinspection ThrowFromFinallyBlock
        try {
            dos.writeInt(1);
            dos.writeBoolean(true);
            dos.writeShort(12321);
            dos.writeLong(-1);
            dos.writeUTF("hamburger");
        } finally {
            //noinspection ThrowFromFinallyBlock
            dos.close();
        }
        int bytesWritten = dos.size();

        // Read the bytes and compare the counts
        CountingInputStream countingInputStream =
                new CountingInputStream(new ByteArrayInputStream(out.toByteArray()));
        DataInputStream dis = new DataInputStream(countingInputStream);
        try {
            assertThat(countingInputStream.byteCount(), is(0));
            assertThat(dis.readInt(), is(1));
            assertThat(dis.readBoolean(), is(true));
            assertThat(dis.readUnsignedShort(), is(12321));
            assertThat(dis.readLong(), is(-1L));
            assertThat(dis.readUTF(), is("hamburger"));
            assertThat(countingInputStream.byteCount(), is(bytesWritten));
        } finally {
            //noinspection ThrowFromFinallyBlock
            dis.close();
        }
    }

    @Test
    public void publicMethods() throws Exception {
        // Catch-all test to find API-breaking changes.
        assertThat(DiskBasedCache.class.getConstructor(File.class, int.class), is(notNullValue()));
        assertThat(DiskBasedCache.class.getConstructor(File.class), is(notNullValue()));
        assertThat(
                DiskBasedCache.class.getMethod("getFileForKey", String.class), is(notNullValue()));
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
        new Random().nextBytes(data);
        entry.data = data;
        return entry;
    }

    private File[] listCachedFiles() {
        return new File(temporaryFolder.getRoot(), CACHE_FOLDER).listFiles();
    }
}

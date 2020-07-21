package com.android.volley.toolbox;

import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import androidx.annotation.Nullable;
import com.android.volley.AsyncCache;
import com.android.volley.Cache;
import com.android.volley.utils.CacheTestUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26)
public class DiskBasedAsyncCacheTest {

    private static final int MAX_SIZE = 1024 * 1024;

    private DiskBasedAsyncCache cache;

    private AsyncCache.OnCompleteCallback emptyCallback;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule public ExpectedException exception = ExpectedException.none();

    @Before
    public void setup() throws IOException, ExecutionException, InterruptedException {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        emptyCallback =
                new AsyncCache.OnCompleteCallback() {
                    @Override
                    public void onComplete() {
                        future.complete(null);
                    }
                };
        // Initialize empty cache
        cache = new DiskBasedAsyncCache(temporaryFolder.getRoot(), MAX_SIZE);
        cache.initialize(emptyCallback);
        future.get();
    }

    @Test
    public void testEmptyInitialize() throws ExecutionException, InterruptedException {
        assertNull(getEntry("key").get());
    }

    @Test
    public void testPutGetZeroBytes() throws ExecutionException, InterruptedException {
        final Cache.Entry entry = new Cache.Entry();

        entry.data = new byte[0];
        entry.serverDate = 1234567L;
        entry.lastModified = 13572468L;
        entry.ttl = 9876543L;
        entry.softTtl = 8765432L;
        entry.etag = "etag";
        entry.responseHeaders = new HashMap<>();
        entry.responseHeaders.put("fruit", "banana");
        entry.responseHeaders.put("color", "yellow");

        putEntry("my-magical-key", entry).get();

        CacheTestUtils.assertThatEntriesAreEqual(getEntry("my-magical-key").get(), entry);
        assertThat(getEntry("unknown-key").get(), is(nullValue()));
    }

    @Test
    public void testTooLargeEntry() throws ExecutionException, InterruptedException {
        Cache.Entry entry =
                CacheTestUtils.randomData(
                        MAX_SIZE - CacheTestUtils.getEntrySizeOnDisk("oversize") + 1);
        putEntry("oversize", entry).get();

        assertThat(getEntry("oversize").get(), is(nullValue()));
    }

    @Test
    public void testMaxSizeEntry() throws ExecutionException, InterruptedException {
        Cache.Entry entry =
                CacheTestUtils.randomData(
                        MAX_SIZE - CacheTestUtils.getEntrySizeOnDisk("maxsize") - 1);
        putEntry("maxsize", entry).get();

        CacheTestUtils.assertThatEntriesAreEqual(getEntry("maxsize").get(), entry);
    }

    @Test
    public void testTrimAtThreshold() throws ExecutionException, InterruptedException {
        // Start with the largest possible entry.
        Cache.Entry entry =
                CacheTestUtils.randomData(MAX_SIZE - CacheTestUtils.getEntrySizeOnDisk("maxsize"));
        putEntry("maxsize", entry).get();

        CacheTestUtils.assertThatEntriesAreEqual(getEntry("maxsize").get(), entry);

        // Now any new entry should cause the first one to be cleared.
        entry = CacheTestUtils.randomData(0);
        putEntry("bit", entry).get();

        assertThat(getEntry("maxsize").get(), is(nullValue()));
        CacheTestUtils.assertThatEntriesAreEqual(getEntry("bit").get(), entry);
    }

    @Test
    public void testTrimWithMultipleEvictions_underHysteresisThreshold()
            throws ExecutionException, InterruptedException {
        final Cache.Entry entry1 =
                CacheTestUtils.randomData(
                        MAX_SIZE / 3 - CacheTestUtils.getEntrySizeOnDisk("entry1") - 1);
        final Cache.Entry entry2 =
                CacheTestUtils.randomData(
                        MAX_SIZE / 3 - CacheTestUtils.getEntrySizeOnDisk("entry2") - 1);
        final Cache.Entry entry3 =
                CacheTestUtils.randomData(
                        MAX_SIZE / 3 - CacheTestUtils.getEntrySizeOnDisk("entry3") - 1);

        putEntry("entry1", entry1).get();
        putEntry("entry2", entry2).get();
        putEntry("entry3", entry3).get();

        CacheTestUtils.assertThatEntriesAreEqual(getEntry("entry1").get(), entry1);
        CacheTestUtils.assertThatEntriesAreEqual(getEntry("entry2").get(), entry2);
        CacheTestUtils.assertThatEntriesAreEqual(getEntry("entry3").get(), entry3);

        final Cache.Entry entry =
                CacheTestUtils.randomData(
                        (int) (DiskBasedCacheUtility.HYSTERESIS_FACTOR * MAX_SIZE)
                                - CacheTestUtils.getEntrySizeOnDisk("max"));

        putEntry("max", entry).get();

        assertThat(getEntry("entry1").get(), is(nullValue()));
        assertThat(getEntry("entry2").get(), is(nullValue()));
        assertThat(getEntry("entry3").get(), is(nullValue()));
        CacheTestUtils.assertThatEntriesAreEqual(getEntry("max").get(), entry);
    }

    @Test
    public void testTrimWithMultipleEvictions_atHysteresisThreshold()
            throws ExecutionException, InterruptedException {
        final Cache.Entry entry1 =
                CacheTestUtils.randomData(
                        MAX_SIZE / 3 - CacheTestUtils.getEntrySizeOnDisk("entry1") - 1);
        final Cache.Entry entry2 =
                CacheTestUtils.randomData(
                        MAX_SIZE / 3 - CacheTestUtils.getEntrySizeOnDisk("entry2") - 1);
        final Cache.Entry entry3 =
                CacheTestUtils.randomData(
                        MAX_SIZE / 3 - CacheTestUtils.getEntrySizeOnDisk("entry3") - 1);

        putEntry("entry1", entry1).get();
        putEntry("entry2", entry2).get();
        putEntry("entry3", entry3).get();

        CacheTestUtils.assertThatEntriesAreEqual(getEntry("entry1").get(), entry1);
        CacheTestUtils.assertThatEntriesAreEqual(getEntry("entry2").get(), entry2);
        CacheTestUtils.assertThatEntriesAreEqual(getEntry("entry3").get(), entry3);

        final Cache.Entry entry =
                CacheTestUtils.randomData(
                        (int) (DiskBasedCacheUtility.HYSTERESIS_FACTOR * MAX_SIZE)
                                - CacheTestUtils.getEntrySizeOnDisk("max")
                                + 1);

        putEntry("max", entry).get();

        assertThat(getEntry("entry1").get(), is(nullValue()));
        assertThat(getEntry("entry2").get(), is(nullValue()));
        assertThat(getEntry("entry3").get(), is(nullValue()));
        assertThat(getEntry("max").get(), is(nullValue()));
    }

    @Test
    public void testTrimWithPartialEvictions() throws ExecutionException, InterruptedException {
        final Cache.Entry entry1 =
                CacheTestUtils.randomData(
                        MAX_SIZE / 3 - CacheTestUtils.getEntrySizeOnDisk("entry1") - 1);
        final Cache.Entry entry2 =
                CacheTestUtils.randomData(
                        MAX_SIZE / 3 - CacheTestUtils.getEntrySizeOnDisk("entry2") - 1);
        final Cache.Entry entry3 =
                CacheTestUtils.randomData(
                        MAX_SIZE / 3 - CacheTestUtils.getEntrySizeOnDisk("entry3") - 1);
        final Cache.Entry entry4 =
                CacheTestUtils.randomData(
                        (MAX_SIZE - CacheTestUtils.getEntrySizeOnDisk("entry4") - 1) / 2);

        putEntry("entry1", entry1).get();
        putEntry("entry2", entry2).get();
        putEntry("entry3", entry3).get();

        CacheTestUtils.assertThatEntriesAreEqual(getEntry("entry1").get(), entry1);
        CacheTestUtils.assertThatEntriesAreEqual(getEntry("entry2").get(), entry2);
        CacheTestUtils.assertThatEntriesAreEqual(getEntry("entry3").get(), entry3);

        putEntry("entry4", entry4).get();

        assertThat(getEntry("entry1").get(), is(nullValue()));
        assertThat(getEntry("entry2").get(), is(nullValue()));
        CacheTestUtils.assertThatEntriesAreEqual(getEntry("entry3").get(), entry3);
        CacheTestUtils.assertThatEntriesAreEqual(getEntry("entry4").get(), entry4);
    }

    @Test
    public void testGetBadMagic() throws IOException, ExecutionException, InterruptedException {
        // Cache something
        Cache.Entry entry = CacheTestUtils.randomData(1023);
        putEntry("key", entry).get();
        CacheTestUtils.assertThatEntriesAreEqual(getEntry("key").get(), entry);

        // Overwrite the magic header
        File cacheFolder = temporaryFolder.getRoot();
        File file = cacheFolder.listFiles()[0];
        FileOutputStream fos = new FileOutputStream(file);
        DiskBasedCacheUtility.writeInt(fos, 0);
        fos.close();
        assertThat(getEntry("key").get(), is(nullValue()));
        assertThat(listCachedFiles(), is(emptyArray())); // should this be??
    }

    @Test
    public void testGetWrongKey() throws IOException, ExecutionException, InterruptedException {
        // Cache something
        Cache.Entry entry = CacheTestUtils.randomData(1023);
        putEntry("key", entry).get();
        CacheTestUtils.assertThatEntriesAreEqual(getEntry("key").get(), entry);

        // Access the cached file
        File cacheFolder = temporaryFolder.getRoot();
        File file = cacheFolder.listFiles()[0];

        // Write a new header to file associated with key
        AsynchronousFileChannel afc =
                AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.WRITE);
        CacheHeader wrongHeader = new CacheHeader("bad", entry);
        ByteBuffer buffer = ByteBuffer.allocate(59);
        wrongHeader.writeHeader(buffer);
        buffer.flip();
        Future<Integer> operation = afc.write(buffer, 0);
        operation.get();
        afc.close();

        // key is gone, but file is still there
        assertThat(getEntry("key").get(), is(nullValue()));
        assertThat(listCachedFiles(), is(arrayWithSize(1)));

        // Note: file is now a zombie because its key does not map to its name
    }

    @Test
    public void testPutRemoveGet() throws ExecutionException, InterruptedException {
        Cache.Entry entry = CacheTestUtils.randomData(511);
        putEntry("key", entry).get();

        CacheTestUtils.assertThatEntriesAreEqual(getEntry("key").get(), entry);

        removeEntry("key").get();
        assertThat(getEntry("key").get(), is(nullValue()));
        assertThat(listCachedFiles(), is(emptyArray()));
    }

    @Test
    public void testPutClearGet() throws ExecutionException, InterruptedException {
        Cache.Entry entry = CacheTestUtils.randomData(511);
        putEntry("key", entry).get();

        CacheTestUtils.assertThatEntriesAreEqual(getEntry("key").get(), entry);

        clearEntries().get();
        assertThat(getEntry("key").get(), is(nullValue()));
        assertThat(listCachedFiles(), is(emptyArray()));
    }

    @Test
    public void testReinitialize() throws ExecutionException, InterruptedException {
        Cache.Entry entry = CacheTestUtils.randomData(1023);
        putEntry("key", entry).get();

        AsyncCache copy = new DiskBasedAsyncCache(temporaryFolder.getRoot(), MAX_SIZE);
        copy.initialize(emptyCallback);

        final CompletableFuture<Cache.Entry> getEntry = new CompletableFuture<>();
        copy.get(
                "key",
                new AsyncCache.OnGetCompleteCallback() {
                    @Override
                    public void onGetComplete(@Nullable Cache.Entry entry) {
                        getEntry.complete(entry);
                    }
                });

        CacheTestUtils.assertThatEntriesAreEqual(getEntry.get(), entry);
    }

    @Test
    public void testInvalidate() throws ExecutionException, InterruptedException {
        Cache.Entry entry = CacheTestUtils.randomData(32);
        entry.softTtl = 8765432L;
        entry.ttl = 9876543L;
        putEntry("key", entry).get();

        invalidateEntry("key", /* fullExpire= */ false).get();
        entry.softTtl = 0; // expired
        CacheTestUtils.assertThatEntriesAreEqual(getEntry("key").get(), entry);
    }

    @Test
    public void testInvalidateFullExpire() throws ExecutionException, InterruptedException {
        Cache.Entry entry = CacheTestUtils.randomData(32);
        entry.softTtl = 8765432L;
        entry.ttl = 9876543L;
        putEntry("key", entry).get();

        invalidateEntry("key", /* fullExpire= */ true).get();
        entry.softTtl = 0; // expired
        entry.ttl = 0; // expired
        CacheTestUtils.assertThatEntriesAreEqual(getEntry("key").get(), entry);
    }

    @Test
    public void testManyResponseHeaders() throws ExecutionException, InterruptedException {
        Cache.Entry entry = new Cache.Entry();
        entry.data = new byte[0];
        entry.responseHeaders = new HashMap<>();
        for (int i = 0; i < 0xFFFF; i++) {
            entry.responseHeaders.put(Integer.toString(i), "");
        }
        putEntry("key", entry).get();
    }

    @Test
    public void initializeIfRootDirectoryDeleted() throws ExecutionException, InterruptedException {
        temporaryFolder.delete();

        Cache.Entry entry = CacheTestUtils.randomData(101);
        putEntry("key1", entry).get();

        assertThat(getEntry("key1").get(), is(nullValue()));

        // confirm that we can now store entries
        putEntry("key2", entry).get();
        CacheTestUtils.assertThatEntriesAreEqual(getEntry("key2").get(), entry);
    }

    /* Test helpers */

    /** Puts entry into the cache, and returns a CompletableFuture after putting the entry. */
    private CompletableFuture<Void> putEntry(final String key, Cache.Entry entry) {
        final CompletableFuture<Void> put = new CompletableFuture<>();
        cache.put(
                key,
                entry,
                new AsyncCache.OnCompleteCallback() {
                    @Override
                    public void onComplete() {
                        put.complete(null);
                    }
                });
        return put;
    }

    /** Gets an entry from the cache, and returns a CompletableFuture containing the entry. */
    private CompletableFuture<Cache.Entry> getEntry(final String key) {
        final CompletableFuture<Cache.Entry> get = new CompletableFuture<>();
        cache.get(
                key,
                new AsyncCache.OnGetCompleteCallback() {
                    @Override
                    public void onGetComplete(@Nullable Cache.Entry entry) {
                        get.complete(entry);
                    }
                });
        return get;
    }

    private CompletableFuture<Void> removeEntry(final String key) {
        final CompletableFuture<Void> remove = new CompletableFuture<>();
        cache.remove(
                key,
                new AsyncCache.OnCompleteCallback() {
                    @Override
                    public void onComplete() {
                        remove.complete(null);
                    }
                });
        return remove;
    }

    private CompletableFuture<Void> invalidateEntry(final String key, final boolean fullExpire) {
        final CompletableFuture<Void> remove = new CompletableFuture<>();
        cache.invalidate(
                key,
                fullExpire,
                new AsyncCache.OnCompleteCallback() {
                    @Override
                    public void onComplete() {
                        remove.complete(null);
                    }
                });
        return remove;
    }

    private CompletableFuture<Void> clearEntries() {
        final CompletableFuture<Void> clear = new CompletableFuture<>();
        cache.clear(
                new AsyncCache.OnCompleteCallback() {
                    @Override
                    public void onComplete() {
                        clear.complete(null);
                    }
                });
        return clear;
    }

    private File[] listCachedFiles() {
        return temporaryFolder.getRoot().listFiles();
    }
}

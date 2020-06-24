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

import static org.junit.Assert.assertEquals;

import com.android.volley.Header;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 16)
public class DiskBasedCacheUtilityTest {

    @Rule public ExpectedException exception = ExpectedException.none();

    @Test
    public void testReadHeaderListWithNegativeSize() throws IOException {
        // If a cached header list is corrupted and begins with a negative size,
        // verify that readHeaderList will throw an IOException.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DiskBasedCacheUtility.writeInt(baos, -1); // negative size
        DiskBasedCache.CountingInputStream cis =
                new DiskBasedCache.CountingInputStream(
                        new ByteArrayInputStream(baos.toByteArray()), Integer.MAX_VALUE);
        // Expect IOException due to negative size
        exception.expect(IOException.class);
        DiskBasedCacheUtility.readHeaderList(cis);
    }

    @Test
    public void testReadHeaderListWithGinormousSize() throws IOException {
        // If a cached header list is corrupted and begins with 2GB size, verify
        // that readHeaderList will throw EOFException rather than OutOfMemoryError.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DiskBasedCacheUtility.writeInt(baos, Integer.MAX_VALUE); // 2GB size
        DiskBasedCache.CountingInputStream cis =
                new DiskBasedCache.CountingInputStream(
                        new ByteArrayInputStream(baos.toByteArray()), baos.size());
        // Expect EOFException when end of stream is reached
        exception.expect(EOFException.class);
        DiskBasedCacheUtility.readHeaderList(cis);
    }

    @Test
    public void serializeInt() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DiskBasedCacheUtility.writeInt(baos, 0);
        DiskBasedCacheUtility.writeInt(baos, 19791214);
        DiskBasedCacheUtility.writeInt(baos, -20050711);
        DiskBasedCacheUtility.writeInt(baos, Integer.MIN_VALUE);
        DiskBasedCacheUtility.writeInt(baos, Integer.MAX_VALUE);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        assertEquals(DiskBasedCacheUtility.readInt(bais), 0);
        assertEquals(DiskBasedCacheUtility.readInt(bais), 19791214);
        assertEquals(DiskBasedCacheUtility.readInt(bais), -20050711);
        assertEquals(DiskBasedCacheUtility.readInt(bais), Integer.MIN_VALUE);
        assertEquals(DiskBasedCacheUtility.readInt(bais), Integer.MAX_VALUE);
    }

    @Test
    public void serializeLong() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DiskBasedCacheUtility.writeLong(baos, 0);
        DiskBasedCacheUtility.writeLong(baos, 31337);
        DiskBasedCacheUtility.writeLong(baos, -4160);
        DiskBasedCacheUtility.writeLong(baos, 4295032832L);
        DiskBasedCacheUtility.writeLong(baos, -4314824046L);
        DiskBasedCacheUtility.writeLong(baos, Long.MIN_VALUE);
        DiskBasedCacheUtility.writeLong(baos, Long.MAX_VALUE);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        assertEquals(DiskBasedCacheUtility.readLong(bais), 0);
        assertEquals(DiskBasedCacheUtility.readLong(bais), 31337);
        assertEquals(DiskBasedCacheUtility.readLong(bais), -4160);
        assertEquals(DiskBasedCacheUtility.readLong(bais), 4295032832L);
        assertEquals(DiskBasedCacheUtility.readLong(bais), -4314824046L);
        assertEquals(DiskBasedCacheUtility.readLong(bais), Long.MIN_VALUE);
        assertEquals(DiskBasedCacheUtility.readLong(bais), Long.MAX_VALUE);
    }

    @Test
    public void serializeString() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DiskBasedCacheUtility.writeString(baos, "");
        DiskBasedCacheUtility.writeString(baos, "This is a string.");
        DiskBasedCacheUtility.writeString(baos, "ファイカス");
        DiskBasedCache.CountingInputStream cis =
                new DiskBasedCache.CountingInputStream(
                        new ByteArrayInputStream(baos.toByteArray()), baos.size());
        assertEquals(DiskBasedCacheUtility.readString(cis), "");
        assertEquals(DiskBasedCacheUtility.readString(cis), "This is a string.");
        assertEquals(DiskBasedCacheUtility.readString(cis), "ファイカス");
    }

    @Test
    public void serializeHeaders() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        List<Header> empty = new ArrayList<>();
        DiskBasedCacheUtility.writeHeaderList(empty, baos);
        DiskBasedCacheUtility.writeHeaderList(null, baos);
        List<Header> twoThings = new ArrayList<>();
        twoThings.add(new Header("first", "thing"));
        twoThings.add(new Header("second", "item"));
        DiskBasedCacheUtility.writeHeaderList(twoThings, baos);
        List<Header> emptyKey = new ArrayList<>();
        emptyKey.add(new Header("", "value"));
        DiskBasedCacheUtility.writeHeaderList(emptyKey, baos);
        List<Header> emptyValue = new ArrayList<>();
        emptyValue.add(new Header("key", ""));
        DiskBasedCacheUtility.writeHeaderList(emptyValue, baos);
        List<Header> sameKeys = new ArrayList<>();
        sameKeys.add(new Header("key", "value"));
        sameKeys.add(new Header("key", "value2"));
        DiskBasedCacheUtility.writeHeaderList(sameKeys, baos);
        DiskBasedCache.CountingInputStream cis =
                new DiskBasedCache.CountingInputStream(
                        new ByteArrayInputStream(baos.toByteArray()), baos.size());
        assertEquals(DiskBasedCacheUtility.readHeaderList(cis), empty);
        assertEquals(DiskBasedCacheUtility.readHeaderList(cis), empty); // null reads back empty
        assertEquals(DiskBasedCacheUtility.readHeaderList(cis), twoThings);
        assertEquals(DiskBasedCacheUtility.readHeaderList(cis), emptyKey);
        assertEquals(DiskBasedCacheUtility.readHeaderList(cis), emptyValue);
        assertEquals(DiskBasedCacheUtility.readHeaderList(cis), sameKeys);
    }
}

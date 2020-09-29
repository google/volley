/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.volley.cronet;

import static org.junit.Assert.assertEquals;

import com.android.volley.Header;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class CronetHttpStackTest {
    @Test
    public void getHeadersEmptyTest() {
        List<Map.Entry<String, String>> list = new ArrayList<>();
        List<Header> actual = CronetHttpStack.getHeaders(list);
        List<Header> expected = new ArrayList<>();
        assertEquals(expected, actual);
    }

    @Test
    public void getHeadersNonEmptyTest() {
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < 5; i++) {
            headers.put("key" + i, "value" + i);
        }
        List<Map.Entry<String, String>> list = new ArrayList<>(headers.entrySet());
        List<Header> actual = CronetHttpStack.getHeaders(list);
        List<Header> expected = new ArrayList<>();
        for (int i = 1; i < 5; i++) {
            expected.add(new Header("key" + i, "value" + i));
        }
        assertHeaderListsEqual(expected, actual);
    }

    private void assertHeaderListsEqual(List<Header> expected, List<Header> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).getName(), actual.get(i).getName());
            assertEquals(expected.get(i).getValue(), actual.get(i).getValue());
        }
    }
}

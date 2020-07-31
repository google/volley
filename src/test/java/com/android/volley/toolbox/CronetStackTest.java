package com.android.volley.toolbox;

import static org.junit.Assert.assertEquals;

import com.android.volley.Header;
import com.android.volley.cronet.*;
import com.android.volley.cronet.CronetHttpStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class CronetStackTest {
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

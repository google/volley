package com.android.volley;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class NetworkResponseTest {

    @SuppressWarnings("deprecation")
    @Test
    public void mapToList() {
        Map<String, String> headers = new HashMap<>();
        headers.put("key1", "value1");
        headers.put("key2", "value2");

        NetworkResponse resp = new NetworkResponse(200, null, headers, false);

        List<Header> expectedHeaders = new ArrayList<>();
        expectedHeaders.add(new Header("key1", "value1"));
        expectedHeaders.add(new Header("key2", "value2"));

        assertThat(expectedHeaders, containsInAnyOrder(resp.allHeaders.toArray(new Header[0])));
    }

    @Test
    public void listToMap() {
        List<Header> headers = new ArrayList<>();
        headers.add(new Header("key1", "value1"));
        // Later values should be preferred.
        headers.add(new Header("key2", "ignoredvalue"));
        headers.add(new Header("key2", "value2"));

        NetworkResponse resp = new NetworkResponse(200, null, false, 0L, headers);

        Map<String, String> expectedHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        expectedHeaders.put("key1", "value1");
        expectedHeaders.put("key2", "value2");

        assertEquals(expectedHeaders, resp.headers);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void nullValuesDontCrash() {
        new NetworkResponse(null);
        new NetworkResponse(null, null);
        new NetworkResponse(200, null, null, false);
        new NetworkResponse(200, null, null, false, 0L);
        new NetworkResponse(200, null, false, 0L, null);
    }
}

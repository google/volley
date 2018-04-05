package com.android.volley.internal;

import com.android.volley.Header;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Utilities to convert between different header containers.
 *
 * <p>This class is internal to Volley and should not be used externally.
 */
public final class HeaderConversions {
    private HeaderConversions() {}

    public static Map<String, String> toHeaderMap(List<Header> allHeaders) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        // Later elements in the list take precedence.
        for (Header header : allHeaders) {
            headers.put(header.getName(), header.getValue());
        }
        return headers;
    }

    public static List<Header> toAllHeaderList(Map<String, String> headers) {
        List<Header> allHeaders = new ArrayList<>(headers.size());
        for (Map.Entry<String, String> header : headers.entrySet()) {
            allHeaders.add(new Header(header.getKey(), header.getValue()));
        }
        return allHeaders;
    }
}

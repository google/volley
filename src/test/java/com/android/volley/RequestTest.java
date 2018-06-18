/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.volley;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.volley.Request.Method;
import com.android.volley.Request.Priority;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class RequestTest {

    @Test
    public void compareTo() {
        int sequence = 0;
        TestRequest low = new TestRequest(Priority.LOW);
        low.setSequence(sequence++);
        TestRequest low2 = new TestRequest(Priority.LOW);
        low2.setSequence(sequence++);
        TestRequest high = new TestRequest(Priority.HIGH);
        high.setSequence(sequence++);
        TestRequest immediate = new TestRequest(Priority.IMMEDIATE);
        immediate.setSequence(sequence++);

        // "Low" should sort higher because it's really processing order.
        assertTrue(low.compareTo(high) > 0);
        assertTrue(high.compareTo(low) < 0);
        assertTrue(low.compareTo(low2) < 0);
        assertTrue(low.compareTo(immediate) > 0);
        assertTrue(immediate.compareTo(high) < 0);
    }

    private static class TestRequest extends Request<Object> {
        private Priority mPriority = Priority.NORMAL;

        public TestRequest(Priority priority) {
            super(Request.Method.GET, "", null);
            mPriority = priority;
        }

        @Override
        public Priority getPriority() {
            return mPriority;
        }

        @Override
        protected void deliverResponse(Object response) {}

        @Override
        protected Response<Object> parseNetworkResponse(NetworkResponse response) {
            return null;
        }
    }

    @Test
    public void urlParsing() {
        UrlParseRequest nullUrl = new UrlParseRequest(null);
        assertEquals(0, nullUrl.getTrafficStatsTag());
        UrlParseRequest emptyUrl = new UrlParseRequest("");
        assertEquals(0, emptyUrl.getTrafficStatsTag());
        UrlParseRequest noHost = new UrlParseRequest("http:///");
        assertEquals(0, noHost.getTrafficStatsTag());
        UrlParseRequest badProtocol = new UrlParseRequest("bad:http://foo");
        assertEquals(0, badProtocol.getTrafficStatsTag());
        UrlParseRequest goodProtocol = new UrlParseRequest("http://foo");
        assertFalse(0 == goodProtocol.getTrafficStatsTag());
    }

    @Test
    public void getCacheKey() {
        assertEquals(
                "http://example.com",
                new UrlParseRequest(Method.GET, "http://example.com").getCacheKey());
        assertEquals(
                "http://example.com",
                new UrlParseRequest(Method.DEPRECATED_GET_OR_POST, "http://example.com")
                        .getCacheKey());
        assertEquals(
                "1-http://example.com",
                new UrlParseRequest(Method.POST, "http://example.com").getCacheKey());
        assertEquals(
                "2-http://example.com",
                new UrlParseRequest(Method.PUT, "http://example.com").getCacheKey());
    }

    private static class UrlParseRequest extends Request<Object> {
        UrlParseRequest(String url) {
            this(Method.GET, url);
        }

        UrlParseRequest(int method, String url) {
            super(method, url, null);
        }

        @Override
        protected void deliverResponse(Object response) {}

        @Override
        protected Response<Object> parseNetworkResponse(NetworkResponse response) {
            return null;
        }
    }

    @Test
    public void nullKeyInPostParams() throws Exception {
        Request<Object> request =
                new Request<Object>(Method.POST, "url", null) {
                    @Override
                    protected void deliverResponse(Object response) {}

                    @Override
                    protected Response<Object> parseNetworkResponse(NetworkResponse response) {
                        return null;
                    }

                    @Override
                    protected Map<String, String> getParams() {
                        return Collections.singletonMap(null, "value");
                    }

                    @Override
                    protected Map<String, String> getPostParams() {
                        return Collections.singletonMap(null, "value");
                    }
                };
        try {
            request.getBody();
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            request.getPostBody();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void nullValueInPostParams() throws Exception {
        Request<Object> request =
                new Request<Object>(Method.POST, "url", null) {
                    @Override
                    protected void deliverResponse(Object response) {}

                    @Override
                    protected Response<Object> parseNetworkResponse(NetworkResponse response) {
                        return null;
                    }

                    @Override
                    protected Map<String, String> getParams() {
                        return Collections.singletonMap("key", null);
                    }

                    @Override
                    protected Map<String, String> getPostParams() {
                        return Collections.singletonMap("key", null);
                    }
                };
        try {
            request.getBody();
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            request.getPostBody();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}

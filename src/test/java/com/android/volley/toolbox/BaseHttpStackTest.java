package com.android.volley.toolbox;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.mock.TestRequest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
public class BaseHttpStackTest {
    private static final Request<?> REQUEST = new TestRequest.Get();
    private static final Map<String, String> ADDITIONAL_HEADERS = Collections.emptyMap();

    @Mock
    private InputStream mContent;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void legacyRequestWithoutBody() throws Exception {
        BaseHttpStack stack = new BaseHttpStack() {
            @Override
            public HttpResponse executeRequest(
                    Request<?> request, Map<String, String> additionalHeaders)
                    throws IOException, AuthFailureError {
                assertSame(REQUEST, request);
                assertSame(ADDITIONAL_HEADERS, additionalHeaders);
                return new HttpResponse(12345, Collections.<String, List<String>>emptyMap());
            }
        };
        org.apache.http.HttpResponse resp = stack.performRequest(REQUEST, ADDITIONAL_HEADERS);
        assertEquals(12345, resp.getStatusLine().getStatusCode());
        assertEquals(0, resp.getAllHeaders().length);
        assertNull(resp.getEntity());
    }

    @Test
    public void legacyResponseWithBody() throws Exception {
        BaseHttpStack stack = new BaseHttpStack() {
            @Override
            public HttpResponse executeRequest(
                    Request<?> request, Map<String, String> additionalHeaders)
                    throws IOException, AuthFailureError {
                assertSame(REQUEST, request);
                assertSame(ADDITIONAL_HEADERS, additionalHeaders);
                return new HttpResponse(
                        12345,
                        Collections.<String, List<String>>emptyMap(),
                        555,
                        mContent);
            }
        };
        org.apache.http.HttpResponse resp = stack.performRequest(REQUEST, ADDITIONAL_HEADERS);
        assertEquals(12345, resp.getStatusLine().getStatusCode());
        assertEquals(0, resp.getAllHeaders().length);
        assertEquals(555L, resp.getEntity().getContentLength());
        assertSame(mContent, resp.getEntity().getContent());
    }

    @Test
    public void legacyResponseHeaders() throws Exception {
        BaseHttpStack stack = new BaseHttpStack() {
            @Override
            public HttpResponse executeRequest(
                    Request<?> request, Map<String, String> additionalHeaders)
                    throws IOException, AuthFailureError {
                assertSame(REQUEST, request);
                assertSame(ADDITIONAL_HEADERS, additionalHeaders);
                Map<String, List<String>> headers = new TreeMap<>();
                headers.put("HeaderA", Collections.singletonList("ValueA"));
                List<String> values = new ArrayList<>();
                values.add("ValueB_1");
                values.add("ValueB_2");
                headers.put("HeaderB", values);
                return new HttpResponse(12345, headers);
            }
        };
        org.apache.http.HttpResponse resp = stack.performRequest(REQUEST, ADDITIONAL_HEADERS);
        assertEquals(12345, resp.getStatusLine().getStatusCode());
        assertEquals(3, resp.getAllHeaders().length);
        assertEquals("HeaderA", resp.getAllHeaders()[0].getName());
        assertEquals("ValueA", resp.getAllHeaders()[0].getValue());
        assertEquals("HeaderB", resp.getAllHeaders()[1].getName());
        assertEquals("ValueB_1", resp.getAllHeaders()[1].getValue());
        assertEquals("HeaderB", resp.getAllHeaders()[2].getName());
        assertEquals("ValueB_2", resp.getAllHeaders()[2].getValue());
        assertNull(resp.getEntity());
    }
}

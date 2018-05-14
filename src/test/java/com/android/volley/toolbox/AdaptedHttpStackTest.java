package com.android.volley.toolbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;

import com.android.volley.Header;
import com.android.volley.Request;
import com.android.volley.mock.TestRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class AdaptedHttpStackTest {
    private static final Request<?> REQUEST = new TestRequest.Get();
    private static final Map<String, String> ADDITIONAL_HEADERS = Collections.emptyMap();

    @Mock private HttpStack mHttpStack;
    @Mock private HttpResponse mHttpResponse;
    @Mock private StatusLine mStatusLine;
    @Mock private HttpEntity mHttpEntity;
    @Mock private InputStream mContent;

    private AdaptedHttpStack mAdaptedHttpStack;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mAdaptedHttpStack = new AdaptedHttpStack(mHttpStack);
        when(mHttpResponse.getStatusLine()).thenReturn(mStatusLine);
    }

    @Test(expected = SocketTimeoutException.class)
    public void requestTimeout() throws Exception {
        when(mHttpStack.performRequest(REQUEST, ADDITIONAL_HEADERS))
                .thenThrow(new ConnectTimeoutException());

        mAdaptedHttpStack.executeRequest(REQUEST, ADDITIONAL_HEADERS);
    }

    @Test
    public void emptyResponse() throws Exception {
        when(mHttpStack.performRequest(REQUEST, ADDITIONAL_HEADERS)).thenReturn(mHttpResponse);
        when(mStatusLine.getStatusCode()).thenReturn(12345);
        when(mHttpResponse.getAllHeaders()).thenReturn(new org.apache.http.Header[0]);

        com.android.volley.toolbox.HttpResponse response =
                mAdaptedHttpStack.executeRequest(REQUEST, ADDITIONAL_HEADERS);

        assertEquals(12345, response.getStatusCode());
        assertEquals(Collections.emptyList(), response.getHeaders());
        assertNull(response.getContent());
    }

    @Test
    public void nonEmptyResponse() throws Exception {
        when(mHttpStack.performRequest(REQUEST, ADDITIONAL_HEADERS)).thenReturn(mHttpResponse);
        when(mStatusLine.getStatusCode()).thenReturn(12345);
        when(mHttpResponse.getAllHeaders()).thenReturn(new org.apache.http.Header[0]);
        when(mHttpResponse.getEntity()).thenReturn(mHttpEntity);
        when(mHttpEntity.getContentLength()).thenReturn((long) Integer.MAX_VALUE);
        when(mHttpEntity.getContent()).thenReturn(mContent);

        com.android.volley.toolbox.HttpResponse response =
                mAdaptedHttpStack.executeRequest(REQUEST, ADDITIONAL_HEADERS);

        assertEquals(12345, response.getStatusCode());
        assertEquals(Collections.emptyList(), response.getHeaders());
        assertEquals(Integer.MAX_VALUE, response.getContentLength());
        assertSame(mContent, response.getContent());
    }

    @Test(expected = IOException.class)
    public void responseTooBig() throws Exception {
        when(mHttpStack.performRequest(REQUEST, ADDITIONAL_HEADERS)).thenReturn(mHttpResponse);
        when(mStatusLine.getStatusCode()).thenReturn(12345);
        when(mHttpResponse.getAllHeaders()).thenReturn(new org.apache.http.Header[0]);
        when(mHttpResponse.getEntity()).thenReturn(mHttpEntity);
        when(mHttpEntity.getContentLength()).thenReturn(Integer.MAX_VALUE + 1L);
        when(mHttpEntity.getContent()).thenReturn(mContent);

        mAdaptedHttpStack.executeRequest(REQUEST, ADDITIONAL_HEADERS);
    }

    @Test
    public void responseWithHeaders() throws Exception {
        when(mHttpStack.performRequest(REQUEST, ADDITIONAL_HEADERS)).thenReturn(mHttpResponse);
        when(mStatusLine.getStatusCode()).thenReturn(12345);
        when(mHttpResponse.getAllHeaders())
                .thenReturn(
                        new org.apache.http.Header[] {
                            new BasicHeader("header1", "value1_B"),
                            new BasicHeader("header3", "value3"),
                            new BasicHeader("HEADER2", "value2"),
                            new BasicHeader("header1", "value1_A")
                        });

        com.android.volley.toolbox.HttpResponse response =
                mAdaptedHttpStack.executeRequest(REQUEST, ADDITIONAL_HEADERS);

        assertEquals(12345, response.getStatusCode());
        assertNull(response.getContent());

        List<Header> expectedHeaders = new ArrayList<>();
        expectedHeaders.add(new Header("header1", "value1_B"));
        expectedHeaders.add(new Header("header3", "value3"));
        expectedHeaders.add(new Header("HEADER2", "value2"));
        expectedHeaders.add(new Header("header1", "value1_A"));
        assertEquals(expectedHeaders, response.getHeaders());
    }
}

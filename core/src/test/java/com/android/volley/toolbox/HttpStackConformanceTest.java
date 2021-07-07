package com.android.volley.toolbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.android.volley.Request;
import com.android.volley.RetryPolicy;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

/** Tests to validate that HttpStack implementations conform with expected behavior. */
@RunWith(RobolectricTestRunner.class)
public class HttpStackConformanceTest {
    @Mock private RetryPolicy mMockRetryPolicy;
    @Mock private Request mMockRequest;

    @Mock private HttpURLConnection mMockConnection;
    @Mock private OutputStream mMockOutputStream;
    @Spy private HurlStack mHurlStack = new HurlStack();

    @Mock private HttpClient mMockHttpClient;
    private HttpClientStack mHttpClientStack;

    private final TestCase[] mTestCases =
            new TestCase[] {
                // TestCase for HurlStack.
                new TestCase() {
                    @Override
                    public HttpStack getStack() {
                        return mHurlStack;
                    }

                    @Override
                    public void setOutputHeaderMap(final Map<String, String> outputHeaderMap) {
                        doAnswer(
                                        new Answer<Void>() {
                                            @Override
                                            public Void answer(InvocationOnMock invocation) {
                                                outputHeaderMap.put(
                                                        invocation.<String>getArgument(0),
                                                        invocation.<String>getArgument(1));
                                                return null;
                                            }
                                        })
                                .when(mMockConnection)
                                .setRequestProperty(anyString(), anyString());
                        doAnswer(
                                        new Answer<Map<String, List<String>>>() {
                                            @Override
                                            public Map<String, List<String>> answer(
                                                    InvocationOnMock invocation) {
                                                Map<String, List<String>> result = new HashMap<>();
                                                for (Map.Entry<String, String> entry :
                                                        outputHeaderMap.entrySet()) {
                                                    result.put(
                                                            entry.getKey(),
                                                            Collections.singletonList(
                                                                    entry.getValue()));
                                                }
                                                return result;
                                            }
                                        })
                                .when(mMockConnection)
                                .getRequestProperties();
                    }
                },

                // TestCase for HttpClientStack.
                new TestCase() {
                    @Override
                    public HttpStack getStack() {
                        return mHttpClientStack;
                    }

                    @Override
                    public void setOutputHeaderMap(final Map<String, String> outputHeaderMap) {
                        try {
                            doAnswer(
                                            new Answer<Void>() {
                                                @Override
                                                public Void answer(InvocationOnMock invocation)
                                                        throws Throwable {
                                                    HttpRequest request = invocation.getArgument(0);
                                                    for (Header header : request.getAllHeaders()) {
                                                        if (outputHeaderMap.containsKey(
                                                                header.getName())) {
                                                            fail(
                                                                    "Multiple values for header "
                                                                            + header.getName());
                                                        }
                                                        outputHeaderMap.put(
                                                                header.getName(),
                                                                header.getValue());
                                                    }
                                                    return null;
                                                }
                                            })
                                    .when(mMockHttpClient)
                                    .execute(any(HttpUriRequest.class));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            };

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mHttpClientStack = spy(new HttpClientStack(mMockHttpClient));

        doReturn(mMockConnection).when(mHurlStack).createConnection(any(URL.class));
        doReturn(mMockOutputStream).when(mMockConnection).getOutputStream();
        when(mMockRequest.getUrl()).thenReturn("http://127.0.0.1");
        when(mMockRequest.getRetryPolicy()).thenReturn(mMockRetryPolicy);
    }

    @Test
    public void headerPrecedence() throws Exception {
        Map<String, String> additionalHeaders = new HashMap<>();
        additionalHeaders.put("A", "AddlA");
        additionalHeaders.put("B", "AddlB");

        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("A", "RequestA");
        requestHeaders.put("C", "RequestC");
        when(mMockRequest.getHeaders()).thenReturn(requestHeaders);

        when(mMockRequest.getMethod()).thenReturn(Request.Method.POST);
        when(mMockRequest.getBody()).thenReturn(new byte[0]);
        when(mMockRequest.getBodyContentType()).thenReturn("BodyContentType");

        for (TestCase testCase : mTestCases) {
            // Test once without a Content-Type header in getHeaders().
            Map<String, String> combinedHeaders = new HashMap<>();
            testCase.setOutputHeaderMap(combinedHeaders);

            testCase.getStack().performRequest(mMockRequest, additionalHeaders);

            Map<String, String> expectedHeaders = new HashMap<>();
            expectedHeaders.put("A", "RequestA");
            expectedHeaders.put("B", "AddlB");
            expectedHeaders.put("C", "RequestC");
            expectedHeaders.put(HttpHeaderParser.HEADER_CONTENT_TYPE, "BodyContentType");

            assertEquals(expectedHeaders, combinedHeaders);

            // Reset and test again with a Content-Type header in getHeaders().
            combinedHeaders.clear();

            requestHeaders.put(HttpHeaderParser.HEADER_CONTENT_TYPE, "RequestContentType");
            expectedHeaders.put(HttpHeaderParser.HEADER_CONTENT_TYPE, "RequestContentType");

            testCase.getStack().performRequest(mMockRequest, additionalHeaders);
            assertEquals(expectedHeaders, combinedHeaders);

            // Clear the Content-Type header for the next TestCase.
            requestHeaders.remove(HttpHeaderParser.HEADER_CONTENT_TYPE);
        }
    }

    private interface TestCase {
        HttpStack getStack();

        void setOutputHeaderMap(Map<String, String> outputHeaderMap);
    }
}

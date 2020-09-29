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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.volley.Header;
import com.android.volley.cronet.CronetHttpStack.CurlCommandLogger;
import com.android.volley.mock.TestRequest;
import com.android.volley.toolbox.AsyncHttpStack.OnRequestComplete;
import com.android.volley.toolbox.UrlRewriter;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.chromium.net.CronetEngine;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class CronetHttpStackTest {
    @Mock private CurlCommandLogger mMockCurlCommandLogger;
    @Mock private OnRequestComplete mMockOnRequestComplete;
    @Mock private UrlRewriter mMockUrlRewriter;

    // A fake would be ideal here, but Cronet doesn't (yet) provide one, and at the moment we aren't
    // exercising the full response flow.
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private CronetEngine mMockCronetEngine;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void curlLogging_disabled() {
        CronetHttpStack stack =
                createStack(
                        new Consumer<CronetHttpStack.Builder>() {
                            @Override
                            public void accept(CronetHttpStack.Builder builder) {
                                // Default parameters should not enable cURL logging.
                            }
                        });

        stack.executeRequest(
                new TestRequest.Get(), ImmutableMap.<String, String>of(), mMockOnRequestComplete);

        verify(mMockCurlCommandLogger, never()).logCurlCommand(anyString());
    }

    @Test
    public void curlLogging_simpleTextRequest() {
        CronetHttpStack stack =
                createStack(
                        new Consumer<CronetHttpStack.Builder>() {
                            @Override
                            public void accept(CronetHttpStack.Builder builder) {
                                builder.setCurlLoggingEnabled(true);
                            }
                        });

        stack.executeRequest(
                new TestRequest.Get(), ImmutableMap.<String, String>of(), mMockOnRequestComplete);

        ArgumentCaptor<String> curlCommandCaptor = ArgumentCaptor.forClass(String.class);
        verify(mMockCurlCommandLogger).logCurlCommand(curlCommandCaptor.capture());
        assertEquals("curl -X GET \"http://foo.com\"", curlCommandCaptor.getValue());
    }

    @Test
    public void curlLogging_rewrittenUrl() {
        CronetHttpStack stack =
                createStack(
                        new Consumer<CronetHttpStack.Builder>() {
                            @Override
                            public void accept(CronetHttpStack.Builder builder) {
                                builder.setCurlLoggingEnabled(true)
                                        .setUrlRewriter(mMockUrlRewriter);
                            }
                        });
        when(mMockUrlRewriter.rewriteUrl("http://foo.com")).thenReturn("http://bar.com");

        stack.executeRequest(
                new TestRequest.Get(), ImmutableMap.<String, String>of(), mMockOnRequestComplete);

        ArgumentCaptor<String> curlCommandCaptor = ArgumentCaptor.forClass(String.class);
        verify(mMockCurlCommandLogger).logCurlCommand(curlCommandCaptor.capture());
        assertEquals("curl -X GET \"http://bar.com\"", curlCommandCaptor.getValue());
    }

    @Test
    public void curlLogging_headers_withoutTokens() {
        CronetHttpStack stack =
                createStack(
                        new Consumer<CronetHttpStack.Builder>() {
                            @Override
                            public void accept(CronetHttpStack.Builder builder) {
                                builder.setCurlLoggingEnabled(true);
                            }
                        });

        stack.executeRequest(
                new TestRequest.Delete() {
                    @Override
                    public Map<String, String> getHeaders() {
                        return ImmutableMap.of(
                                "SomeHeader", "SomeValue",
                                "Authorization", "SecretToken");
                    }
                },
                ImmutableMap.of("SomeOtherHeader", "SomeValue"),
                mMockOnRequestComplete);

        ArgumentCaptor<String> curlCommandCaptor = ArgumentCaptor.forClass(String.class);
        verify(mMockCurlCommandLogger).logCurlCommand(curlCommandCaptor.capture());
        // NOTE: Header order is stable because the implementation uses a TreeMap.
        assertEquals(
                "curl -X DELETE --header \"Authorization: [REDACTED]\" "
                        + "--header \"SomeHeader: SomeValue\" "
                        + "--header \"SomeOtherHeader: SomeValue\" \"http://foo.com\"",
                curlCommandCaptor.getValue());
    }

    @Test
    public void curlLogging_headers_withTokens() {
        CronetHttpStack stack =
                createStack(
                        new Consumer<CronetHttpStack.Builder>() {
                            @Override
                            public void accept(CronetHttpStack.Builder builder) {
                                builder.setCurlLoggingEnabled(true)
                                        .setLogAuthTokensInCurlCommands(true);
                            }
                        });

        stack.executeRequest(
                new TestRequest.Delete() {
                    @Override
                    public Map<String, String> getHeaders() {
                        return ImmutableMap.of(
                                "SomeHeader", "SomeValue",
                                "Authorization", "SecretToken");
                    }
                },
                ImmutableMap.of("SomeOtherHeader", "SomeValue"),
                mMockOnRequestComplete);

        ArgumentCaptor<String> curlCommandCaptor = ArgumentCaptor.forClass(String.class);
        verify(mMockCurlCommandLogger).logCurlCommand(curlCommandCaptor.capture());
        // NOTE: Header order is stable because the implementation uses a TreeMap.
        assertEquals(
                "curl -X DELETE --header \"Authorization: SecretToken\" "
                        + "--header \"SomeHeader: SomeValue\" "
                        + "--header \"SomeOtherHeader: SomeValue\" \"http://foo.com\"",
                curlCommandCaptor.getValue());
    }

    @Test
    public void curlLogging_textRequest() {
        CronetHttpStack stack =
                createStack(
                        new Consumer<CronetHttpStack.Builder>() {
                            @Override
                            public void accept(CronetHttpStack.Builder builder) {
                                builder.setCurlLoggingEnabled(true);
                            }
                        });

        stack.executeRequest(
                new TestRequest.PostWithBody() {
                    @Override
                    public byte[] getBody() {
                        try {
                            return "hello".getBytes("UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public String getBodyContentType() {
                        return "text/plain; charset=UTF-8";
                    }
                },
                ImmutableMap.<String, String>of(),
                mMockOnRequestComplete);

        ArgumentCaptor<String> curlCommandCaptor = ArgumentCaptor.forClass(String.class);
        verify(mMockCurlCommandLogger).logCurlCommand(curlCommandCaptor.capture());
        assertEquals(
                "curl -X POST "
                        + "--header \"Content-Type: text/plain; charset=UTF-8\" \"http://foo.com\" "
                        + "--data-ascii \"hello\"",
                curlCommandCaptor.getValue());
    }

    @Test
    public void curlLogging_gzipTextRequest() {
        CronetHttpStack stack =
                createStack(
                        new Consumer<CronetHttpStack.Builder>() {
                            @Override
                            public void accept(CronetHttpStack.Builder builder) {
                                builder.setCurlLoggingEnabled(true);
                            }
                        });

        stack.executeRequest(
                new TestRequest.PostWithBody() {
                    @Override
                    public byte[] getBody() {
                        return new byte[] {1, 2, 3, 4, 5};
                    }

                    @Override
                    public String getBodyContentType() {
                        return "text/plain";
                    }

                    @Override
                    public Map<String, String> getHeaders() {
                        return ImmutableMap.of("Content-Encoding", "gzip, identity");
                    }
                },
                ImmutableMap.<String, String>of(),
                mMockOnRequestComplete);

        ArgumentCaptor<String> curlCommandCaptor = ArgumentCaptor.forClass(String.class);
        verify(mMockCurlCommandLogger).logCurlCommand(curlCommandCaptor.capture());
        assertEquals(
                "echo 'AQIDBAU=' | base64 -d > /tmp/$$.bin; curl -X POST "
                        + "--header \"Content-Encoding: gzip, identity\" "
                        + "--header \"Content-Type: text/plain\" \"http://foo.com\" "
                        + "--data-binary @/tmp/$$.bin",
                curlCommandCaptor.getValue());
    }

    @Test
    public void curlLogging_binaryRequest() {
        CronetHttpStack stack =
                createStack(
                        new Consumer<CronetHttpStack.Builder>() {
                            @Override
                            public void accept(CronetHttpStack.Builder builder) {
                                builder.setCurlLoggingEnabled(true);
                            }
                        });

        stack.executeRequest(
                new TestRequest.PostWithBody() {
                    @Override
                    public byte[] getBody() {
                        return new byte[] {1, 2, 3, 4, 5};
                    }

                    @Override
                    public String getBodyContentType() {
                        return "application/octet-stream";
                    }
                },
                ImmutableMap.<String, String>of(),
                mMockOnRequestComplete);

        ArgumentCaptor<String> curlCommandCaptor = ArgumentCaptor.forClass(String.class);
        verify(mMockCurlCommandLogger).logCurlCommand(curlCommandCaptor.capture());
        assertEquals(
                "echo 'AQIDBAU=' | base64 -d > /tmp/$$.bin; curl -X POST "
                        + "--header \"Content-Type: application/octet-stream\" \"http://foo.com\" "
                        + "--data-binary @/tmp/$$.bin",
                curlCommandCaptor.getValue());
    }

    @Test
    public void curlLogging_largeRequest() {
        CronetHttpStack stack =
                createStack(
                        new Consumer<CronetHttpStack.Builder>() {
                            @Override
                            public void accept(CronetHttpStack.Builder builder) {
                                builder.setCurlLoggingEnabled(true);
                            }
                        });

        stack.executeRequest(
                new TestRequest.PostWithBody() {
                    @Override
                    public byte[] getBody() {
                        return new byte[2048];
                    }

                    @Override
                    public String getBodyContentType() {
                        return "application/octet-stream";
                    }
                },
                ImmutableMap.<String, String>of(),
                mMockOnRequestComplete);

        ArgumentCaptor<String> curlCommandCaptor = ArgumentCaptor.forClass(String.class);
        verify(mMockCurlCommandLogger).logCurlCommand(curlCommandCaptor.capture());
        assertEquals(
                "curl -X POST "
                        + "--header \"Content-Type: application/octet-stream\" \"http://foo.com\" "
                        + "[REQUEST BODY TOO LARGE TO INCLUDE]",
                curlCommandCaptor.getValue());
    }

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

    private CronetHttpStack createStack(Consumer<CronetHttpStack.Builder> stackEditor) {
        CronetHttpStack.Builder builder =
                new CronetHttpStack.Builder(RuntimeEnvironment.application)
                        .setCronetEngine(mMockCronetEngine)
                        .setCurlCommandLogger(mMockCurlCommandLogger);
        stackEditor.accept(builder);
        CronetHttpStack stack = builder.build();
        stack.setBlockingExecutor(MoreExecutors.newDirectExecutorService());
        stack.setNonBlockingExecutor(MoreExecutors.newDirectExecutorService());
        return stack;
    }
}

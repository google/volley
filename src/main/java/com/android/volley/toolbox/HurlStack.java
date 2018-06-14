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

package com.android.volley.toolbox;

import android.support.annotation.VisibleForTesting;
import com.android.volley.AuthFailureError;
import com.android.volley.Header;
import com.android.volley.Request;
import com.android.volley.Request.Method;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/** A {@link BaseHttpStack} based on {@link HttpURLConnection}. */
public class HurlStack extends BaseHttpStack {

    private static final int HTTP_CONTINUE = 100;

    /** An interface for transforming URLs before use. */
    public interface UrlRewriter {
        /**
         * Returns a URL to use instead of the provided one, or null to indicate this URL should not
         * be used at all.
         */
        String rewriteUrl(String originalUrl);
    }

    private final UrlRewriter mUrlRewriter;
    private final SSLSocketFactory mSslSocketFactory;

    public HurlStack() {
        this(/* urlRewriter = */ null);
    }

    /** @param urlRewriter Rewriter to use for request URLs */
    public HurlStack(UrlRewriter urlRewriter) {
        this(urlRewriter, /* sslSocketFactory = */ null);
    }

    /**
     * @param urlRewriter Rewriter to use for request URLs
     * @param sslSocketFactory SSL factory to use for HTTPS connections
     */
    public HurlStack(UrlRewriter urlRewriter, SSLSocketFactory sslSocketFactory) {
        mUrlRewriter = urlRewriter;
        mSslSocketFactory = sslSocketFactory;
    }

    @Override
    public HttpResponse executeRequest(Request<?> request, Map<String, String> additionalHeaders)
            throws IOException, AuthFailureError {
        String url = request.getUrl();
        HashMap<String, String> map = new HashMap<>();
        map.putAll(additionalHeaders);
        // Request.getHeaders() takes precedence over the given additional (cache) headers).
        map.putAll(request.getHeaders());
        if (mUrlRewriter != null) {
            String rewritten = mUrlRewriter.rewriteUrl(url);
            if (rewritten == null) {
                throw new IOException("URL blocked by rewriter: " + url);
            }
            url = rewritten;
        }
        URL parsedUrl = new URL(url);
        HttpURLConnection connection = openConnection(parsedUrl, request);
        boolean keepConnectionOpen = false;
        try {
            for (String headerName : map.keySet()) {
                connection.setRequestProperty(headerName, map.get(headerName));
            }
            setConnectionParametersForRequest(connection, request);
            // Initialize HttpResponse with data from the HttpURLConnection.
            int responseCode = connection.getResponseCode();
            if (responseCode == -1) {
                // -1 is returned by getResponseCode() if the response code could not be retrieved.
                // Signal to the caller that something was wrong with the connection.
                throw new IOException("Could not retrieve response code from HttpUrlConnection.");
            }

            if (!hasResponseBody(request.getMethod(), responseCode)) {
                return new HttpResponse(responseCode, convertHeaders(connection.getHeaderFields()));
            }

            // Need to keep the connection open until the stream is consumed by the caller. Wrap the
            // stream such that close() will disconnect the connection.
            keepConnectionOpen = true;
            return new HttpResponse(
                    responseCode,
                    convertHeaders(connection.getHeaderFields()),
                    connection.getContentLength(),
                    new UrlConnectionInputStream(connection));
        } finally {
            if (!keepConnectionOpen) {
                connection.disconnect();
            }
        }
    }

    @VisibleForTesting
    static List<Header> convertHeaders(Map<String, List<String>> responseHeaders) {
        List<Header> headerList = new ArrayList<>(responseHeaders.size());
        for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
            // HttpUrlConnection includes the status line as a header with a null key; omit it here
            // since it's not really a header and the rest of Volley assumes non-null keys.
            if (entry.getKey() != null) {
                for (String value : entry.getValue()) {
                    headerList.add(new Header(entry.getKey(), value));
                }
            }
        }
        return headerList;
    }

    /**
     * Checks if a response message contains a body.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7230#section-3.3">RFC 7230 section 3.3</a>
     * @param requestMethod request method
     * @param responseCode response status code
     * @return whether the response has a body
     */
    private static boolean hasResponseBody(int requestMethod, int responseCode) {
        return requestMethod != Request.Method.HEAD
                && !(HTTP_CONTINUE <= responseCode && responseCode < HttpURLConnection.HTTP_OK)
                && responseCode != HttpURLConnection.HTTP_NO_CONTENT
                && responseCode != HttpURLConnection.HTTP_NOT_MODIFIED;
    }

    /**
     * Wrapper for a {@link HttpURLConnection}'s InputStream which disconnects the connection on
     * stream close.
     */
    static class UrlConnectionInputStream extends FilterInputStream {
        private final HttpURLConnection mConnection;

        UrlConnectionInputStream(HttpURLConnection connection) {
            super(inputStreamFromConnection(connection));
            mConnection = connection;
        }

        @Override
        public void close() throws IOException {
            super.close();
            mConnection.disconnect();
        }
    }

    /**
     * Initializes an {@link InputStream} from the given {@link HttpURLConnection}.
     *
     * @param connection
     * @return an HttpEntity populated with data from <code>connection</code>.
     */
    private static InputStream inputStreamFromConnection(HttpURLConnection connection) {
        InputStream inputStream;
        try {
            inputStream = connection.getInputStream();
        } catch (IOException ioe) {
            inputStream = connection.getErrorStream();
        }
        return inputStream;
    }

    /** Create an {@link HttpURLConnection} for the specified {@code url}. */
    protected HttpURLConnection createConnection(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // Workaround for the M release HttpURLConnection not observing the
        // HttpURLConnection.setFollowRedirects() property.
        // https://code.google.com/p/android/issues/detail?id=194495
        connection.setInstanceFollowRedirects(HttpURLConnection.getFollowRedirects());

        return connection;
    }

    /**
     * Opens an {@link HttpURLConnection} with parameters.
     *
     * @param url
     * @return an open connection
     * @throws IOException
     */
    private HttpURLConnection openConnection(URL url, Request<?> request) throws IOException {
        HttpURLConnection connection = createConnection(url);

        int timeoutMs = request.getTimeoutMs();
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setUseCaches(false);
        connection.setDoInput(true);

        // use caller-provided custom SslSocketFactory, if any, for HTTPS
        if ("https".equals(url.getProtocol()) && mSslSocketFactory != null) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(mSslSocketFactory);
        }

        return connection;
    }

    // NOTE: Any request headers added here (via setRequestProperty or addRequestProperty) should be
    // checked against the existing properties in the connection and not overridden if already set.
    @SuppressWarnings("deprecation")
    /* package */ static void setConnectionParametersForRequest(
            HttpURLConnection connection, Request<?> request) throws IOException, AuthFailureError {
        switch (request.getMethod()) {
            case Method.DEPRECATED_GET_OR_POST:
                // This is the deprecated way that needs to be handled for backwards compatibility.
                // If the request's post body is null, then the assumption is that the request is
                // GET.  Otherwise, it is assumed that the request is a POST.
                byte[] postBody = request.getPostBody();
                if (postBody != null) {
                    connection.setRequestMethod("POST");
                    addBody(connection, request, postBody);
                }
                break;
            case Method.GET:
                // Not necessary to set the request method because connection defaults to GET but
                // being explicit here.
                connection.setRequestMethod("GET");
                break;
            case Method.DELETE:
                connection.setRequestMethod("DELETE");
                break;
            case Method.POST:
                connection.setRequestMethod("POST");
                addBodyIfExists(connection, request);
                break;
            case Method.PUT:
                connection.setRequestMethod("PUT");
                addBodyIfExists(connection, request);
                break;
            case Method.HEAD:
                connection.setRequestMethod("HEAD");
                break;
            case Method.OPTIONS:
                connection.setRequestMethod("OPTIONS");
                break;
            case Method.TRACE:
                connection.setRequestMethod("TRACE");
                break;
            case Method.PATCH:
                connection.setRequestMethod("PATCH");
                addBodyIfExists(connection, request);
                break;
            default:
                throw new IllegalStateException("Unknown method type.");
        }
    }

    private static void addBodyIfExists(HttpURLConnection connection, Request<?> request)
            throws IOException, AuthFailureError {
        byte[] body = request.getBody();
        if (body != null) {
            addBody(connection, request, body);
        }
    }

    private static void addBody(HttpURLConnection connection, Request<?> request, byte[] body)
            throws IOException {
        // Prepare output. There is no need to set Content-Length explicitly,
        // since this is handled by HttpURLConnection using the size of the prepared
        // output stream.
        connection.setDoOutput(true);
        // Set the content-type unless it was already set (by Request#getHeaders).
        if (!connection.getRequestProperties().containsKey(HttpHeaderParser.HEADER_CONTENT_TYPE)) {
            connection.setRequestProperty(
                    HttpHeaderParser.HEADER_CONTENT_TYPE, request.getBodyContentType());
        }
        DataOutputStream out = new DataOutputStream(connection.getOutputStream());
        out.write(body);
        out.close();
    }
}

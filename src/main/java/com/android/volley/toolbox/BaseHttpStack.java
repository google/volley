/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.volley.AuthFailureError;
import com.android.volley.Header;
import com.android.volley.Request;

import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** An HTTP stack abstraction. */
@SuppressWarnings("deprecation") // for HttpStack
public abstract class BaseHttpStack implements HttpStack {

    /**
     * Performs an HTTP request with the given parameters.
     *
     * <p>A GET request is sent if request.getPostBody() == null. A POST request is sent otherwise,
     * and the Content-Type header is set to request.getPostBodyContentType().
     *
     * @param request the request to perform
     * @param additionalHeaders additional headers to be sent together with
     *         {@link Request#getHeaders()}
     * @return the {@link HttpResponse}
     * @throws SocketTimeoutException if the request times out
     * @throws IOException if another I/O error occurs during the request
     * @throws AuthFailureError if an authentication failure occurs during the request
     */
    public abstract HttpResponse executeRequest(
            Request<?> request, Map<String, String> additionalHeaders)
            throws IOException, AuthFailureError;

    /**
     * @deprecated use {@link #executeRequest} instead to avoid a dependency on the deprecated
     * Apache HTTP library. Nothing in Volley's own source calls this method. However, since
     * {@link BasicNetwork#mHttpStack} is exposed to subclasses, we provide this implementation in
     * case legacy client apps are dependent on that field. This method may be removed in a future
     * release of Volley.
     */
    @Deprecated
    @Override
    public final org.apache.http.HttpResponse performRequest(
            Request<?> request, Map<String, String> additionalHeaders)
            throws IOException, AuthFailureError {
        HttpResponse response = executeRequest(request, additionalHeaders);

        ProtocolVersion protocolVersion = new ProtocolVersion("HTTP", 1, 1);
        StatusLine statusLine = new BasicStatusLine(
                protocolVersion, response.getStatusCode(), "" /* reasonPhrase */);
        BasicHttpResponse apacheResponse = new BasicHttpResponse(statusLine);

        List<org.apache.http.Header> headers = new ArrayList<>();
        for (Header header : response.getHeaders()) {
            headers.add(new BasicHeader(header.getName(), header.getValue()));
        }
        apacheResponse.setHeaders(headers.toArray(new org.apache.http.Header[headers.size()]));

        InputStream responseStream = response.getContent();
        if (responseStream != null) {
            BasicHttpEntity entity = new BasicHttpEntity();
            entity.setContent(responseStream);
            entity.setContentLength(response.getContentLength());
            apacheResponse.setEntity(entity);
        }

        return apacheResponse;
    }
}

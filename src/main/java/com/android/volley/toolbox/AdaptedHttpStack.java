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
import com.android.volley.Request;

import org.apache.http.Header;
import org.apache.http.conn.ConnectTimeoutException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@link BaseHttpStack} implementation wrapping a {@link HttpStack}.
 *
 * <p>{@link BasicNetwork} uses this if it is provided a {@link HttpStack} at construction time,
 * allowing it to have one implementation based atop {@link BaseHttpStack}.
 */
@SuppressWarnings("deprecation")
class AdaptedHttpStack extends BaseHttpStack {

    private final HttpStack mHttpStack;

    AdaptedHttpStack(HttpStack httpStack) {
        mHttpStack = httpStack;
    }

    @Override
    public HttpResponse executeRequest(
            Request<?> request, Map<String, String> additionalHeaders)
            throws IOException, AuthFailureError {
        org.apache.http.HttpResponse apacheResp;
        try {
             apacheResp = mHttpStack.performRequest(request, additionalHeaders);
        } catch (ConnectTimeoutException e) {
            // BasicNetwork won't know that this exception should be retried like a timeout, since
            // it's an Apache-specific error, so wrap it in a standard timeout exception.
            throw new SocketTimeoutException(e.getMessage());
        }

        int statusCode = apacheResp.getStatusLine().getStatusCode();

        Header[] headers = apacheResp.getAllHeaders();
        Map<String, List<String>> headerMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Header header : headers) {
            List<String> valueList = headerMap.get(header.getName());
            if (valueList == null) {
                valueList = new ArrayList<>();
                headerMap.put(header.getName(), valueList);
            }
            valueList.add(header.getValue());
        }

        if (apacheResp.getEntity() == null) {
            return new HttpResponse(statusCode, headerMap);
        }

        return new HttpResponse(
                statusCode,
                headerMap,
                (int) apacheResp.getEntity().getContentLength(),
                apacheResp.getEntity().getContent());
    }
}

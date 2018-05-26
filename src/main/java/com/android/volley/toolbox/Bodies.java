package com.android.volley.toolbox;

import com.android.volley.Request;
import com.android.volley.VolleyLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import static com.android.volley.toolbox.Utils.requireNonNull;

/** Convenience factory methods for creating a {@link Body}. */
public class Bodies {

    static final String DEFAULT_CONTENT_TYPE =
            Request.DEFAULT_BODY_CONTENT_TYPE_BASE + Request.DEFAULT_PARAMS_ENCODING;

    /** Body with no data. */
    public static final Body STUB = new StubBody();

    /**
     * Convenience method for creating a raw body.
     *
     * @param bytes See {@link Body#bytes()}.
     */
    public static Body forBytes(final byte[] bytes) {
        return forBytes(bytes, DEFAULT_CONTENT_TYPE);
    }

    /**
     * Convenience method for creating a raw body.
     *
     * @param bytes See {@link Body#bytes()}.
     * @param contentType See {@link Request#getBodyContentType()}.
     */
    public static Body forBytes(final byte[] bytes, final String contentType) {
        return new Body() {
            @Override
            public byte[] bytes() {
                return bytes;
            }

            @Override
            public <T> void configureDefaults(RequestBuilder<T> requestBuilder) {
                if (requestBuilder.getBodyContentType() == null) {
                    requestBuilder.bodyContentType(contentType);
                }
            }
        };
    }

    /** Convenience method for creating a {@link JSONObject} body. */
    public static Body forJSONObject(JSONObject jsonObject) {
        return new JsonBody(jsonObject.toString());
    }

    /** Convenience method for creating a {@link JSONArray} body. */
    public static Body forJSONArray(JSONArray jsonArray) {
        return new JsonBody(jsonArray.toString());
    }

    public static Body forParam(String key, String value) {
        Map<String, String> params = new HashMap<>(1);
        params.put(requireNonNull(key), requireNonNull(value));
        return forParams(params);
    }

    /**
     * Convenience method for putting query parameters in a {@link Body}. Prefer using {@link
     * RequestBuilder#params(Map)} over this.
     */
    public static Body forParams(final Map<String, String> params) {
        return new Body() {
            @Override
            public byte[] bytes() {
                if (params.isEmpty()) {
                    return null;
                }

                return _encodeParameters(params, Request.DEFAULT_PARAMS_ENCODING);
            }

            @Override
            public <T> void configureDefaults(RequestBuilder<T> requestBuilder) {
                // Do nothing
            }
        };
    }

    /**
     * Visible for internal use only! Do not use outside Volley source code! Converts <code>params
     * </code> into an application/x-www-form-urlencoded encoded string.
     */
    public static byte[] _encodeParameters(Map<String, String> params, String paramsEncoding) {
        StringBuilder encodedParams = new StringBuilder();
        try {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                encodedParams.append(URLEncoder.encode(entry.getKey(), paramsEncoding));
                encodedParams.append('=');
                encodedParams.append(URLEncoder.encode(entry.getValue(), paramsEncoding));
                encodedParams.append('&');
            }
            return encodedParams.toString().getBytes(paramsEncoding);
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException("Encoding not supported: " + paramsEncoding, uee);
        }
    }

    private static class StubBody implements Body {
        @Override
        public byte[] bytes() {
            return null;
        }

        @Override
        public <T> void configureDefaults(RequestBuilder<T> requestBuilder) {
            // Do nothing
        }
    }

    private static class JsonBody implements Body {

        private final String jsonString;

        public JsonBody(String jsonString) {
            this.jsonString = jsonString;
        }

        @Override
        public byte[] bytes() {
            try {
                return jsonString.getBytes(JsonRequest.PROTOCOL_CHARSET);
            } catch (UnsupportedEncodingException e) {
                VolleyLog.wtf(
                        "Unsupported Encoding while trying to get the bytes of %s using %s",
                        jsonString, JsonRequest.PROTOCOL_CHARSET);
                return null;
            }
        }

        @Override
        public <T> void configureDefaults(RequestBuilder<T> requestBuilder) {
            if (requestBuilder.getBodyContentType() == null) {
                requestBuilder.bodyContentType(JsonRequest.PROTOCOL_CONTENT_TYPE);
            }
        }
    }
}

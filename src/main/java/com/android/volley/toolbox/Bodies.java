package com.android.volley.toolbox;

import com.android.volley.Request;
import com.android.volley.VolleyLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * TODO make methods for string later?
 * TODO desc
 * TODO move
 */
public class Bodies {

    static final String DEFAULT_CONTENT_TYPE =
            Request.DEFAULT_BODY_CONTENT_TYPE_BASE + Request.DEFAULT_PARAMS_ENCODING;

    public static final Body STUB = new StubBody();

    public static Body forBytes(final byte[] bytes, final String contentType) {
        return new Body() {
            @Override
            public byte[] bytes() {
                return bytes;
            }

            @Override
            public String contentType() {
                return contentType;
            }
        };
    }

    // TODO rename?
    public static Body forJSONObject(JSONObject jsonObject) {
        return new JsonBody(jsonObject.toString());
    }

    public static Body forJSONArray(JSONArray jsonArray) {
        return new JsonBody(jsonArray.toString());
    }

    public static Body forParam(String key, String value) {
        Map<String, String> params = new HashMap<>(1);
        params.put(requireNonNull(key), requireNonNull(value));
        return forParams(params);
    }

    public static Body forParams(final Map<String, String> params) {
        return new Body() {
            @Override
            public byte[] bytes() {
                if (params.isEmpty()) {
                    return null;
                }

                return _encodeParameters(params, contentType());
            }

            @Override
            public String contentType() {
                return Request.DEFAULT_PARAMS_ENCODING;
            }
        };
    }

    /**
     * Converts <code>params</code> into an application/x-www-form-urlencoded encoded string.
     *
     * Visible for internal use only! Do not use outside Volley source code!
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
        public String contentType() {
            return DEFAULT_CONTENT_TYPE;
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
                        jsonString,
                        JsonRequest.PROTOCOL_CHARSET
                );
                return null;
            }
        }

        @Override
        public String contentType() {
            return JsonRequest.PROTOCOL_CONTENT_TYPE;
        }
    }
}

package com.android.volley.toolbox;

import com.android.volley.Request;
import com.android.volley.VolleyLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

/**
 * TODO make methods for all of the other kinds of stuff
 * TODO desc
 * TODO move
 */
public class Bodies {

    static final String DEFAULT_CONTENT_TYPE =
            Request.DEFAULT_BODY_CONTENT_TYPE_BASE + Request.DEFAULT_PARAMS_ENCODING;

    public static final Body STUB = new Body() {
        @Override
        public byte[] bytes() {
            return null;
        }

        @Override
        public String contentType() {
            return DEFAULT_CONTENT_TYPE;
        }
    };

    // TODO rename?
    public static Body forJSONObject(final JSONObject jsonObject) {
        return new JsonBody(jsonObject.toString());
    }

    public static Body forJSONArray(final JSONArray jsonArray) {
        return new JsonBody(jsonArray.toString());
    }

    // TODO string, image??, bytes in general

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

// TODO move, doc
interface Body {

    byte[] bytes();

    String contentType();
}

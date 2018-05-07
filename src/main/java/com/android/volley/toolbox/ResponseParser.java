package com.android.volley.toolbox;

import com.android.volley.NetworkResponse;
import com.android.volley.Response;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

/**
 * TODO
 */
public interface ResponseParser<T> {
    /**
     * TODO
     */
    Response<T> parseNetworkResponse(NetworkResponse response);
}

/**
 * TODO
 */
class ResponseParsers {

    public static <T> ResponseParser<T> stub() {
        return new ResponseParser<T>() {
            @Override
            public Response<T> parseNetworkResponse(NetworkResponse response) {
                return null;
            }
        };
    }

    public static ResponseParser<String> forString() {
        return new ResponseParser<String>() {
            @Override
            @SuppressWarnings("DefaultCharset")
            public Response<String> parseNetworkResponse(NetworkResponse response) {
                String parsed;
                try {
                    parsed = new String(
                            response.data,
                            HttpHeaderParser.parseCharset(response.headers)
                    );
                } catch (UnsupportedEncodingException e) {
                    // Since minSdkVersion = 8, we can't call
                    // new String(response.data, Charset.defaultCharset())
                    // So suppress the warning instead.
                    parsed = new String(response.data);
                }
                return Response.success(parsed, HttpHeaderParser.parseCacheHeaders(response));
            }
        };
    }

    public static ResponseParser<JSONObject> forJSONObject() {
        return new ResponseParser<JSONObject>() {
            @Override
            public Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
                // TODO ocpy code here
                return null;
            }
        };
    }
}

/**
 * TODO make methods for all of the other kinds of stuff
 * TODO desc
 * TODO move
 */
//class Bodies {
//    public static Body forJSONObject(final JSONObject jsonObject) {
//        return new Body() {
//            public byte[] getBytes() {
//                try {
//                    return jsonObject.toString().getBytes(JsonRequest.PROTOCOL_CHARSET);
//                } catch (UnsupportedEncodingException e) {
//                    throw new Error();
//                    // TODO
//                }
//            }
//        };
//    }
//}

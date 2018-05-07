package com.android.volley.toolbox;

import com.android.volley.NetworkResponse;
import com.android.volley.Response;

import org.json.JSONObject;

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

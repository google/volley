package com.android.volley.toolbox;

import com.android.volley.NetworkResponse;
import com.android.volley.Response;

/**
 * TODO
 */
public interface ResponseParser<T> {
    /**
     * TODO docs, rename?
     */
    Response<T> parseNetworkResponse(NetworkResponse response);
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

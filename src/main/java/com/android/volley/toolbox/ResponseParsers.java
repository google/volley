package com.android.volley.toolbox;

import android.graphics.Bitmap;
import android.widget.ImageView;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

import static com.android.volley.toolbox.JsonRequest.PROTOCOL_CHARSET;

/**
 * TODO docs
 */
public class ResponseParsers {

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

    public static ResponseParser<Bitmap> forImage(
            Bitmap.Config mDecodeConfig,
            int mMaxWidth,
            int mMaxHeight,
            ImageView.ScaleType mScaleType
    ) {
        return new ImageResponseParser(mDecodeConfig, mMaxWidth, mMaxHeight, mScaleType);

    }

    public static ResponseParser<JSONObject> forJSONObject() {
        return new JsonParserBase<JSONObject>() {
            @Override
            protected JSONObject stringToResponseType(String string) throws JSONException {
                return new JSONObject(string);
            }
        };
    }

    public static ResponseParser<JSONArray> forJSONArray() {
        return new JsonParserBase<JSONArray>() {
            @Override
            protected JSONArray stringToResponseType(String string) throws JSONException {
                return new JSONArray(string);
            }
        };
    }

    private static abstract class JsonParserBase<T> implements ResponseParser<T> {

        @Override
        public Response<T> parseNetworkResponse(NetworkResponse response) {
            try {
                String jsonString = new String(
                        response.data,
                        HttpHeaderParser.parseCharset(response.headers, PROTOCOL_CHARSET)
                );
                return Response.success(
                        stringToResponseType(jsonString),
                        HttpHeaderParser.parseCacheHeaders(response)
                );
            } catch (UnsupportedEncodingException e) {
                return Response.error(new ParseError(e));
            } catch (JSONException je) {
                return Response.error(new ParseError(je));
            }
        }

        protected abstract T stringToResponseType(String string) throws JSONException;
    }
}

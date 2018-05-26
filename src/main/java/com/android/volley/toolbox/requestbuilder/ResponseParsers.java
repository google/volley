package com.android.volley.toolbox.requestbuilder;

import android.graphics.Bitmap;
import android.widget.ImageView;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

import static com.android.volley.toolbox.JsonRequest.PROTOCOL_CHARSET;

/** Convenience methods for creating {@link ResponseParser} for various data types. */
public class ResponseParsers {

    /** Ignores the {@link Response} data, always returns {@code null}. */
    public static ResponseParser<Object> ignoreResponse() {
        return new ResponseParser<Object>() {
            @Override
            public Response<Object> parseNetworkResponse(NetworkResponse response) {
                return Response.success(null, HttpHeaderParser.parseCacheHeaders(response));
            }

            @Override
            public void configureDefaults(RequestBuilder<Object> requestBuilder) {
                // Do nothing
            }
        };
    }

    /** Converts {@link Response} data into a {@link String}. */
    public static ResponseParser<String> forString() {
        return new ResponseParser<String>() {
            @Override
            @SuppressWarnings("DefaultCharset")
            public Response<String> parseNetworkResponse(NetworkResponse response) {
                String parsed;
                try {
                    parsed =
                            new String(
                                    response.data, HttpHeaderParser.parseCharset(response.headers));
                } catch (UnsupportedEncodingException e) {
                    // Since minSdkVersion = 8, we can't call
                    // new String(response.data, Charset.defaultCharset())
                    // So suppress the warning instead.
                    parsed = new String(response.data);
                }
                return Response.success(parsed, HttpHeaderParser.parseCacheHeaders(response));
            }

            @Override
            public void configureDefaults(RequestBuilder<String> requestBuilder) {
                // Do nothing
            }
        };
    }

    /**
     * Converts image {@link Response} data into a {@link Bitmap}.
     *
     * <p>Decodes an image to a maximum specified width and height. If both width and height are
     * zero, the image will be decoded to its natural size. If one of the two is nonzero, that
     * dimension will be clamped and the other one will be set to preserve the image's aspect ratio.
     * If both width and height are nonzero, the image will be decoded to be fit in the rectangle of
     * dimensions width x height while keeping its aspect ratio.
     *
     * @param maxWidth Maximum width to decode this bitmap to, or zero for none
     * @param maxHeight Maximum height to decode this bitmap to, or zero for none
     * @param scaleType The ImageViews ScaleType used to calculate the needed image size.
     * @param decodeConfig Format to decode the bitmap to
     */
    public static ResponseParser<Bitmap> forImage(
            Bitmap.Config decodeConfig,
            int maxWidth,
            int maxHeight,
            ImageView.ScaleType scaleType) {
        return new ImageResponseParser(decodeConfig, maxWidth, maxHeight, scaleType);
    }

    /** Converts {@link Response} data into a {@link JSONObject}. */
    public static ResponseParser<JSONObject> forJSONObject() {
        return new JsonParserBase<JSONObject>() {
            @Override
            protected JSONObject stringToResponseType(String string) throws JSONException {
                return new JSONObject(string);
            }
        };
    }

    /** Converts {@link Response} data into a {@link JSONArray}. */
    public static ResponseParser<JSONArray> forJSONArray() {
        return new JsonParserBase<JSONArray>() {
            @Override
            protected JSONArray stringToResponseType(String string) throws JSONException {
                return new JSONArray(string);
            }
        };
    }

    private abstract static class JsonParserBase<T> implements ResponseParser<T> {

        @Override
        public Response<T> parseNetworkResponse(NetworkResponse response) {
            try {
                String jsonString =
                        new String(
                                response.data,
                                HttpHeaderParser.parseCharset(response.headers, PROTOCOL_CHARSET));
                return Response.success(
                        stringToResponseType(jsonString),
                        HttpHeaderParser.parseCacheHeaders(response));
            } catch (UnsupportedEncodingException e) {
                return Response.error(new ParseError(e));
            } catch (JSONException je) {
                return Response.error(new ParseError(je));
            }
        }

        @Override
        public void configureDefaults(RequestBuilder<T> requestBuilder) {
            // Do nothing
        }

        protected abstract T stringToResponseType(String string) throws JSONException;
    }
}

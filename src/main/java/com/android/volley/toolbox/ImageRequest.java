/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.widget.ImageView.ScaleType;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyLog;

/** A canned request for getting an image at a given URL and calling back with a decoded Bitmap. */
public class ImageRequest extends Request<Bitmap> {
    /** Socket timeout in milliseconds for image requests */
    public static final int DEFAULT_IMAGE_TIMEOUT_MS = 1000;

    /** Default number of retries for image requests */
    public static final int DEFAULT_IMAGE_MAX_RETRIES = 2;

    /** Default backoff multiplier for image requests */
    public static final float DEFAULT_IMAGE_BACKOFF_MULT = 2f;

    /** Low priority to prevent blocking much smaller {@link Request}s. */
    public static final Priority DEFAULT_IMAGE_PRIORITY = Priority.LOW;

    /** Lock to guard mListener as it is cleared on cancel() and read on delivery. */
    private final Object mLock = new Object();

    private final ResponseParser<Bitmap> mParser;

    // @GuardedBy("mLock")
    private Response.Listener<Bitmap> mListener;

    /**
     * Creates a new image request, decoding to a maximum specified width and height. If both width
     * and height are zero, the image will be decoded to its natural size. If one of the two is
     * nonzero, that dimension will be clamped and the other one will be set to preserve the image's
     * aspect ratio. If both width and height are nonzero, the image will be decoded to be fit in
     * the rectangle of dimensions width x height while keeping its aspect ratio.
     *
     * @param url URL of the image
     * @param listener Listener to receive the decoded bitmap
     * @param maxWidth Maximum width to decode this bitmap to, or zero for none
     * @param maxHeight Maximum height to decode this bitmap to, or zero for none
     * @param scaleType The ImageViews ScaleType used to calculate the needed image size.
     * @param decodeConfig Format to decode the bitmap to
     * @param errorListener Error listener, or null to ignore errors
     * @deprecated Prefer using {@link RequestBuilder}, with {@link ResponseParsers#forImage(Config,
     *     int, int, ScaleType)}
     */
    @Deprecated
    public ImageRequest(
            String url,
            Response.Listener<Bitmap> listener,
            int maxWidth,
            int maxHeight,
            ScaleType scaleType,
            Config decodeConfig,
            Response.ErrorListener errorListener) {
        super(Method.GET, url, errorListener);
        setRetryPolicy(
                new DefaultRetryPolicy(
                        DEFAULT_IMAGE_TIMEOUT_MS,
                        DEFAULT_IMAGE_MAX_RETRIES,
                        DEFAULT_IMAGE_BACKOFF_MULT));
        mListener = listener;
        mParser = ResponseParsers.forImage(decodeConfig, maxWidth, maxHeight, scaleType);
    }

    /**
     * For API compatibility with the pre-ScaleType variant of the constructor. Equivalent to the
     * normal constructor with {@code ScaleType.CENTER_INSIDE}.
     */
    @Deprecated
    public ImageRequest(
            String url,
            Response.Listener<Bitmap> listener,
            int maxWidth,
            int maxHeight,
            Config decodeConfig,
            Response.ErrorListener errorListener) {
        this(
                url,
                listener,
                maxWidth,
                maxHeight,
                ScaleType.CENTER_INSIDE,
                decodeConfig,
                errorListener);
    }

    @Override
    public Priority getPriority() {
        return DEFAULT_IMAGE_PRIORITY;
    }

    @Override
    protected Response<Bitmap> parseNetworkResponse(NetworkResponse response) {
        try {
            return mParser.parseNetworkResponse(response);
        } catch (OutOfMemoryError e) {
            VolleyLog.e("Caught OOM for %d byte image, url=%s", response.data.length, getUrl());
            return Response.error(new ParseError(e));
        }
    }

    @Override
    public void cancel() {
        super.cancel();
        synchronized (mLock) {
            mListener = null;
        }
    }

    @Override
    protected void deliverResponse(Bitmap response) {
        Response.Listener<Bitmap> listener;
        synchronized (mLock) {
            listener = mListener;
        }
        if (listener != null) {
            listener.onResponse(response);
        }
    }
}

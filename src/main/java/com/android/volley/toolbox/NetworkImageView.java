/**
 * Copyright (C) 2013 The Android Open Source Project
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.volley.toolbox;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader.ImageContainer;
import com.android.volley.toolbox.ImageLoader.ImageListener;

/**
 * Handles fetching an image from a URL as well as the life-cycle of the
 * associated request.
 */
public class NetworkImageView extends ImageView {
    /**
     * The URL of the network image to load
     */
    private String mUrl;

    /**
     * Resource ID of the image to be used as a placeholder until the network image is loaded.
     */
    private int mDefaultImageId;

    /**
     * Resource ID of the image to be used if the network response fails.
     */
    private int mErrorImageId;

    /**
     * Local copy of the ImageLoader.
     */
    private ImageLoader mImageLoader;

    /**
     * Current ImageContainer. (either in-flight or finished)
     */
    private ImageContainer mImageContainer;

    /**
     * Rounding value for corners
     */
    private int mCornerRadius;

    public NetworkImageView(Context context) {
        this(context, null);
    }

    public NetworkImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NetworkImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Sets URL of the image that should be loaded into this view. Note that calling this will
     * immediately either set the cached image (if available) or the default image specified by
     * {@link NetworkImageView#setDefaultImageResId(int)} on the view.
     * <p>
     * NOTE: If applicable, {@link NetworkImageView#setDefaultImageResId(int)} and
     * {@link NetworkImageView#setErrorImageResId(int)} should be called prior to calling
     * this function.
     *
     * @param url         The URL that should be loaded into this ImageView.
     * @param imageLoader ImageLoader that will be used to make the request.
     */
    public void setImageUrl(String url, ImageLoader imageLoader) {
        mUrl = url;
        mImageLoader = imageLoader;
        // The URL has potentially changed. See if we need to load it.
        loadImageIfNecessary(false);
    }

    @SuppressWarnings("unused")
    public void setCornerRadius(int radius) {
        mCornerRadius = radius;
    }

    /**
     * Sets the default image resource ID to be used for this view until the attempt to load it
     * completes.
     */
    public void setDefaultImageResId(int defaultImage) {
        mDefaultImageId = defaultImage;
    }

    /**
     * Sets the error image resource ID to be used for this view in the event that the image
     * requested fails to load.
     */
    public void setErrorImageResId(int errorImage) {
        mErrorImageId = errorImage;
    }

    /**
     * Loads the image for the view if it isn't already loaded.
     *
     * @param isInLayoutPass True if this was invoked from a layout pass, false otherwise.
     */
    void loadImageIfNecessary(final boolean isInLayoutPass) {
        int width = getWidth();
        int height = getHeight();
        ScaleType scaleType = getScaleType();

        boolean wrapWidth = false, wrapHeight = false;
        if (getLayoutParams() != null) {
            wrapWidth = getLayoutParams().width == LayoutParams.WRAP_CONTENT;
            wrapHeight = getLayoutParams().height == LayoutParams.WRAP_CONTENT;
        }

        // if the view's bounds aren't known yet, and this is not a wrap-content/wrap-content
        // view, hold off on loading the image.
        boolean isFullyWrapContent = wrapWidth && wrapHeight;
        if (width == 0 && height == 0 && !isFullyWrapContent) {
            return;
        }

        // if the URL to be loaded in this view is empty, cancel any old requests and clear the
        // currently loaded image.
        if (TextUtils.isEmpty(mUrl)) {
            if (mImageContainer != null) {
                mImageContainer.cancelRequest();
                mImageContainer = null;
            }
            setDefaultImageOrNull();
            return;
        }

        // if there was an old request in this view, check if it needs to be canceled.
        if (mImageContainer != null && mImageContainer.getRequestUrl() != null) {
            if (mImageContainer.getRequestUrl().equals(mUrl)) {
                // if the request is from the same URL, return.
                return;
            } else {
                // if there is a pre-existing request, cancel it if it's fetching a different URL.
                mImageContainer.cancelRequest();
                setDefaultImageOrNull();
            }
        }

        // Calculate the max image width / height to use while ignoring WRAP_CONTENT dimens.
        int maxWidth = wrapWidth ? 0 : width;
        int maxHeight = wrapHeight ? 0 : height;

        // The pre-existing content of this view didn't match the current URL. Load the new image
        // from the network.

        // update the ImageContainer to be the new bitmap container.
        mImageContainer = mImageLoader.get(mUrl,
            new ImageListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    if (mErrorImageId != 0) {
                        setImageResource(mErrorImageId);
                    }
                }

                @Override
                public void onResponse(final ImageContainer response, boolean isImmediate) {
                    // If this was an immediate response that was delivered inside of a layout
                    // pass do not set the image immediately as it will trigger a requestLayout
                    // inside of a layout. Instead, defer setting the image by posting back to
                    // the main thread.
                    if (isImmediate && isInLayoutPass) {
                        post(new Runnable() {
                            @Override
                            public void run() {
                                onResponse(response, false);
                            }
                        });
                        return;
                    }
                    if (response.getBitmap() != null) {
                        Bitmap b =
                            mCornerRadius > 0 ?
                                roundCorners(response.getBitmap(), mCornerRadius) :
                                response.getBitmap();
                        setImageBitmap(b);
                    } else if (mDefaultImageId != 0) {
                        setImageResource(mDefaultImageId);
                    }
                }
            }, maxWidth, maxHeight, scaleType);
    }

    private void setDefaultImageOrNull() {
        if (mDefaultImageId != 0) {
            setImageResource(mDefaultImageId);
        } else {
            setImageBitmap(null);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        loadImageIfNecessary(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mImageContainer != null) {
            // If the view was bound to an image request, cancel it and clear
            // out the image from the view.
            mImageContainer.cancelRequest();
            setImageBitmap(null);
            // also clear out the container so we can reload the image if necessary.
            mImageContainer = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    /**
     * Process incoming {@linkplain Bitmap} to make rounded corners according to target
     * This method <b>doesn't display</b> result bitmap in {@link ImageView}
     *
     * @param bitmap      Incoming Bitmap to process
     * @param roundPixels Rounded pixels of corner
     * @return Result bitmap with rounded corners
     */
    public Bitmap roundCorners(Bitmap bitmap, int roundPixels) {
        Bitmap roundBitmap;
        int bw = bitmap.getWidth();
        int bh = bitmap.getHeight();
        int vw = this.getWidth();
        int vh = this.getHeight();
        if (vw <= 0) vw = bw;
        if (vh <= 0) vh = bh;

        final ImageView.ScaleType scaleType = this.getScaleType();
        if (scaleType == null) {
            return bitmap;
        }

        int width, height;
        Rect srcRect;
        Rect destRect;
        switch (scaleType) {
            case CENTER_INSIDE:
                float vRation = (float) vw / vh;
                float bRation = (float) bw / bh;
                int destWidth;
                int destHeight;
                if (vRation > bRation) {
                    destHeight = Math.min(vh, bh);
                    destWidth = (int) (bw / ((float) bh / destHeight));
                } else {
                    destWidth = Math.min(vw, bw);
                    destHeight = (int) (bh / ((float) bw / destWidth));
                }
                int x = (vw - destWidth) / 2;
                int y = (vh - destHeight) / 2;
                srcRect = new Rect(0, 0, bw, bh);
                destRect = new Rect(x, y, x + destWidth, y + destHeight);
                width = vw;
                height = vh;
                break;
            case FIT_CENTER:
            case FIT_START:
            case FIT_END:
            default:
                vRation = (float) vw / vh;
                bRation = (float) bw / bh;
                if (vRation > bRation) {
                    width = (int) (bw / ((float) bh / vh));
                    height = vh;
                } else {
                    width = vw;
                    height = (int) (bh / ((float) bw / vw));
                }
                srcRect = new Rect(0, 0, bw, bh);
                destRect = new Rect(0, 0, width, height);
                break;
            case CENTER_CROP:
                vRation = (float) vw / vh;
                bRation = (float) bw / bh;
                int srcWidth;
                int srcHeight;
                if (vRation > bRation) {
                    srcWidth = bw;
                    srcHeight = (int) (vh * ((float) bw / vw));
                    x = 0;
                    y = (bh - srcHeight) / 2;
                } else {
                    srcWidth = (int) (vw * ((float) bh / vh));
                    srcHeight = bh;
                    x = (bw - srcWidth) / 2;
                    y = 0;
                }
                width = srcWidth;// Math.min(vw, bw);
                height = srcHeight;//Math.min(vh, bh);
                srcRect = new Rect(x, y, x + srcWidth, y + srcHeight);
                destRect = new Rect(0, 0, width, height);
                break;
            case FIT_XY:
                width = vw;
                height = vh;
                srcRect = new Rect(0, 0, bw, bh);
                destRect = new Rect(0, 0, width, height);
                break;
            case CENTER:
            case MATRIX:
                width = Math.min(vw, bw);
                height = Math.min(vh, bh);
                x = (bw - width) / 2;
                y = (bh - height) / 2;
                srcRect = new Rect(x, y, x + width, y + height);
                destRect = new Rect(0, 0, width, height);
                break;
        }

        try {
            roundBitmap = getRoundedCornerBitmap(bitmap, roundPixels, srcRect, destRect, width, height);
        } catch (OutOfMemoryError e) {
            roundBitmap = bitmap;
        }

        return roundBitmap;
    }

    private Bitmap getRoundedCornerBitmap(Bitmap bitmap, int roundPixels, Rect srcRect, Rect destRect, int width,
                                          int height) {
        Bitmap output = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Paint paint = new Paint();
        final RectF destRectF = new RectF(destRect);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(0xFF000000);
        canvas.drawRoundRect(destRectF, roundPixels, roundPixels, paint);

        paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
        canvas.drawBitmap(bitmap, srcRect, destRectF, paint);

        return output;
    }

}

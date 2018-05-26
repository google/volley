package com.android.volley.toolbox.requestbuilder;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView.ScaleType;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.ImageRequest;

/**
 * Prefer using {@link ResponseParsers#forImage} instead of creating this directly. Also see that
 * method for documentation.
 *
 * <p>A {@link ResponseParser} for converting response data into a {@link Bitmap}.
 */
public class ImageResponseParser implements ResponseParser<Bitmap> {

    /** Decoding lock so that we don't decode more than one image at a time (to avoid OOM's) */
    private static final Object sDecodeLock = new Object();

    /**
     * Returns the largest power-of-two divisor for use in downscaling a bitmap that will not result
     * in the scaling past the desired dimensions.
     *
     * @param actualWidth Actual width of the bitmap
     * @param actualHeight Actual height of the bitmap
     * @param desiredWidth Desired width of the bitmap
     * @param desiredHeight Desired height of the bitmap
     */
    // Visible for testing.
    public static int findBestSampleSize(
            int actualWidth, int actualHeight, int desiredWidth, int desiredHeight) {
        double wr = (double) actualWidth / desiredWidth;
        double hr = (double) actualHeight / desiredHeight;
        double ratio = Math.min(wr, hr);
        float n = 1.0f;
        while ((n * 2) <= ratio) {
            n *= 2;
        }

        return (int) n;
    }

    /**
     * Scales one side of a rectangle to fit aspect ratio.
     *
     * @param maxPrimary Maximum size of the primary dimension (i.e. width for max width), or zero
     *     to maintain aspect ratio with secondary dimension
     * @param maxSecondary Maximum size of the secondary dimension, or zero to maintain aspect ratio
     *     with primary dimension
     * @param actualPrimary Actual size of the primary dimension
     * @param actualSecondary Actual size of the secondary dimension
     * @param scaleType The ScaleType used to calculate the needed image size.
     */
    private static int getResizedDimension(
            int maxPrimary,
            int maxSecondary,
            int actualPrimary,
            int actualSecondary,
            ScaleType scaleType) {

        // If no dominant value at all, just return the actual.
        if ((maxPrimary == 0) && (maxSecondary == 0)) {
            return actualPrimary;
        }

        // If ScaleType.FIT_XY fill the whole rectangle, ignore ratio.
        if (scaleType == ScaleType.FIT_XY) {
            if (maxPrimary == 0) {
                return actualPrimary;
            }
            return maxPrimary;
        }

        // If primary is unspecified, scale primary to match secondary's scaling ratio.
        if (maxPrimary == 0) {
            double ratio = (double) maxSecondary / (double) actualSecondary;
            return (int) (actualPrimary * ratio);
        }

        if (maxSecondary == 0) {
            return maxPrimary;
        }

        double ratio = (double) actualSecondary / (double) actualPrimary;
        int resized = maxPrimary;

        // If ScaleType.CENTER_CROP fill the whole rectangle, preserve aspect ratio.
        if (scaleType == ScaleType.CENTER_CROP) {
            if ((resized * ratio) < maxSecondary) {
                resized = (int) (maxSecondary / ratio);
            }
            return resized;
        }

        if ((resized * ratio) > maxSecondary) {
            resized = (int) (maxSecondary / ratio);
        }
        return resized;
    }

    private final Bitmap.Config mDecodeConfig;
    private final int mMaxWidth;
    private final int mMaxHeight;
    private final ScaleType mScaleType;

    /** See {@link ResponseParsers#forImage(Bitmap.Config, int, int, ScaleType)} for docs. */
    public ImageResponseParser(
            Bitmap.Config mDecodeConfig, int mMaxWidth, int mMaxHeight, ScaleType mScaleType) {
        this.mDecodeConfig = mDecodeConfig;
        this.mMaxWidth = mMaxWidth;
        this.mMaxHeight = mMaxHeight;
        this.mScaleType = mScaleType;
    }

    @Override
    public Response<Bitmap> parseNetworkResponse(NetworkResponse response) {
        // Serialize all decode on a global lock to reduce concurrent heap usage.
        synchronized (sDecodeLock) {
            return doParse(response);
        }
    }

    @Override
    public void configureDefaults(RequestBuilder<Bitmap> requestBuilder) {
        if (requestBuilder.getPriority() == null) {
            requestBuilder.retryPolicy(
                    new DefaultRetryPolicy(
                            ImageRequest.DEFAULT_IMAGE_TIMEOUT_MS,
                            ImageRequest.DEFAULT_IMAGE_MAX_RETRIES,
                            ImageRequest.DEFAULT_IMAGE_BACKOFF_MULT));
        }

        if (requestBuilder.getPriority() == null) {
            requestBuilder.priority(ImageRequest.DEFAULT_IMAGE_PRIORITY);
        }
    }

    /** The real guts of parseNetworkResponse. Broken out for readability. */
    private Response<Bitmap> doParse(NetworkResponse response) {
        byte[] data = response.data;
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        Bitmap bitmap = null;
        if (mMaxWidth == 0 && mMaxHeight == 0) {
            decodeOptions.inPreferredConfig = mDecodeConfig;
            bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, decodeOptions);
        } else {
            // If we have to resize this image, first get the natural bounds.
            decodeOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, decodeOptions);
            int actualWidth = decodeOptions.outWidth;
            int actualHeight = decodeOptions.outHeight;

            // Then compute the dimensions we would ideally like to decode to.
            int desiredWidth =
                    getResizedDimension(
                            mMaxWidth, mMaxHeight, actualWidth, actualHeight, mScaleType);
            int desiredHeight =
                    getResizedDimension(
                            mMaxHeight, mMaxWidth, actualHeight, actualWidth, mScaleType);

            // Decode to the nearest power of two scaling factor.
            decodeOptions.inJustDecodeBounds = false;
            // TODO(ficus): Do we need this or is it okay since API 8 doesn't support it?
            // decodeOptions.inPreferQualityOverSpeed = PREFER_QUALITY_OVER_SPEED;
            decodeOptions.inSampleSize =
                    findBestSampleSize(actualWidth, actualHeight, desiredWidth, desiredHeight);
            Bitmap tempBitmap = BitmapFactory.decodeByteArray(data, 0, data.length, decodeOptions);

            // If necessary, scale down to the maximal acceptable size.
            if (tempBitmap != null
                    && (tempBitmap.getWidth() > desiredWidth
                            || tempBitmap.getHeight() > desiredHeight)) {
                bitmap = Bitmap.createScaledBitmap(tempBitmap, desiredWidth, desiredHeight, true);
                tempBitmap.recycle();
            } else {
                bitmap = tempBitmap;
            }
        }

        if (bitmap == null) {
            return Response.error(new ParseError(response));
        } else {
            return Response.success(bitmap, HttpHeaderParser.parseCacheHeaders(response));
        }
    }
}

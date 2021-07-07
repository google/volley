/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView.ScaleType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class NetworkImageViewTest {
    private NetworkImageView mNIV;
    private MockImageLoader mMockImageLoader;

    @Before
    public void setUp() throws Exception {
        mMockImageLoader = new MockImageLoader();
        mNIV = new NetworkImageView(RuntimeEnvironment.application);
    }

    @Test
    public void setImageUrl_requestsImage() {
        mNIV.setLayoutParams(
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        mNIV.setImageUrl("http://foo", mMockImageLoader);
        assertEquals("http://foo", mMockImageLoader.lastRequestUrl);
        assertEquals(0, mMockImageLoader.lastMaxWidth);
        assertEquals(0, mMockImageLoader.lastMaxHeight);
    }

    // public void testSetImageUrl_setsMaxSize() {
    // // TODO: Not sure how to make getWidth() return something from an
    // // instrumentation test. Write this test once it's figured out.
    // }

    private static class MockImageLoader extends ImageLoader {
        public MockImageLoader() {
            super(null, null);
        }

        public String lastRequestUrl;
        public int lastMaxWidth;
        public int lastMaxHeight;

        @Override
        public ImageContainer get(
                String requestUrl,
                ImageListener imageListener,
                int maxWidth,
                int maxHeight,
                ScaleType scaleType) {
            lastRequestUrl = requestUrl;
            lastMaxWidth = maxWidth;
            lastMaxHeight = maxHeight;
            return null;
        }
    }

    @Test
    public void publicMethods() throws Exception {
        // Catch-all test to find API-breaking changes.
        assertNotNull(NetworkImageView.class.getConstructor(Context.class));
        assertNotNull(NetworkImageView.class.getConstructor(Context.class, AttributeSet.class));
        assertNotNull(
                NetworkImageView.class.getConstructor(
                        Context.class, AttributeSet.class, int.class));

        assertNotNull(
                NetworkImageView.class.getMethod("setImageUrl", String.class, ImageLoader.class));
        assertNotNull(NetworkImageView.class.getMethod("setDefaultImageDrawable", Drawable.class));
        assertNotNull(NetworkImageView.class.getMethod("setDefaultImageBitmap", Bitmap.class));
        assertNotNull(NetworkImageView.class.getMethod("setDefaultImageResId", int.class));
        assertNotNull(NetworkImageView.class.getMethod("setErrorImageDrawable", Drawable.class));
        assertNotNull(NetworkImageView.class.getMethod("setErrorImageBitmap", Bitmap.class));
        assertNotNull(NetworkImageView.class.getMethod("setErrorImageResId", int.class));
    }
}

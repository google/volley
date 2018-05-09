package com.android.volley.utils;

import android.annotation.SuppressLint;

import java.nio.charset.Charset;

/**
 * Created by Dylan on 8/05/18.
 */

public class TestUtils {

    @SuppressLint("NewApi")
    public static byte[] stringBytes(String s) {
        return s.getBytes(Charset.forName("UTF-8"));
    }
}

package com.android.volley.utils;

import android.annotation.SuppressLint;

import java.nio.charset.Charset;

public class TestUtils {

    @SuppressLint("NewApi")
    public static byte[] stringBytes(String s) {
        return s.getBytes(Charset.forName("UTF-8"));
    }
}

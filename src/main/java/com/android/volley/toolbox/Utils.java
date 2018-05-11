package com.android.volley.toolbox;

class Utils {

    public static <T> T requireNonNull(T obj) {
        if (obj == null) throw new NullPointerException();
        return obj;
    }

    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null) throw new NullPointerException(message);
        return obj;
    }

    public static <T> T or(T valueOrNull, T defaultValue) {
        if (valueOrNull == null) {
            return requireNonNull(defaultValue);
        }

        return valueOrNull;
    }
}

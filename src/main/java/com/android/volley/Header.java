/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.volley;

import android.text.TextUtils;

/** An HTTP header. */
public final class Header {
    private final String mName;
    private final String mValue;

    public Header(String name, String value) {
        mName = name;
        mValue = value;
    }

    public final String getName() {
        return mName;
    }

    public final String getValue() {
        return mValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Header header = (Header) o;

        return TextUtils.equals(mName, header.mName)
                && TextUtils.equals(mValue, header.mValue);
    }

    @Override
    public int hashCode() {
        int result = mName.hashCode();
        result = 31 * result + mValue.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Header[name=" + mName + ",value=" + mValue + "]";
    }
}

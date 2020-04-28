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

package com.android.volley;

import android.content.Intent;
import androidx.annotation.Nullable;

/** Error indicating that there was an authentication failure when performing a Request. */
@SuppressWarnings("serial")
public class AuthFailureError extends VolleyError {
    /** An intent that can be used to resolve this exception. (Brings up the password dialog.) */
    @Nullable private Intent mResolutionIntent;

    public AuthFailureError() {}

    public AuthFailureError(@Nullable Intent intent) {
        mResolutionIntent = intent;
    }

    public AuthFailureError(@Nullable NetworkResponse response) {
        super(response);
    }

    public AuthFailureError(@Nullable String message) {
        super(message);
    }

    public AuthFailureError(@Nullable String message, @Nullable Exception reason) {
        super(message, reason);
    }

    @Nullable
    public Intent getResolutionIntent() {
        return mResolutionIntent;
    }

    @Override
    @Nullable
    public String getMessage() {
        if (mResolutionIntent != null) {
            return "User needs to (re)enter credentials.";
        }
        return super.getMessage();
    }
}

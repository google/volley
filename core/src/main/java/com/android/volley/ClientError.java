/*
 * Copyright (C) 2015 The Android Open Source Project
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

/**
 * Indicates that the server responded with an error response indicating that the client has erred.
 *
 * <p>For backwards compatibility, extends ServerError which used to be thrown for all server
 * errors, including 4xx error codes indicating a client error.
 */
@SuppressWarnings("serial")
public class ClientError extends ServerError {
    public ClientError(NetworkResponse networkResponse) {
        super(networkResponse);
    }

    public ClientError() {
        super();
    }
}

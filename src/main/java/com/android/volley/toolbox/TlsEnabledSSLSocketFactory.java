/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.os.Build;

import com.android.volley.VolleyLog;

import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * {@link SSLSocketFactory} which ensures TLSv1.1 and 1.2 are enabled where supported.
 *
 * <p>These protocols are supported on API 16+ devices but only enabled by default on API 20+
 * devices. {@link #newTlsEnabledSSLSocketFactoryIfNeeded} will return a factory which attempts to
 * enable these protocols on devices that fall in this range.
 */
class TlsEnabledSSLSocketFactory extends InterceptingSSLSocketFactory {

    // VisibleForTesting
    static final String TLS_1_1 = "TLSv1.1";
    // VisibleForTesting
    static final String TLS_1_2 = "TLSv1.2";

    // Nullable
    static SSLSocketFactory newTlsEnabledSSLSocketFactoryIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH) {
            // No special factory needed - TLSv1.1 and 1.2 are unsupported prior to Jelly Bean and
            // enabled by default on KitKat Watch+ devices per:
            // https://developer.android.com/reference/javax/net/ssl/SSLSocket.html
            return null;
        }
        SocketFactory socketFactory = SSLSocketFactory.getDefault();
        if (!(socketFactory instanceof SSLSocketFactory)) {
            VolleyLog.wtf(new IllegalStateException(),
                    "SSLSocketFactory.getDefault() didn't return an SSLSocketFactory");
            return null;
        }
        return new TlsEnabledSSLSocketFactory((SSLSocketFactory) socketFactory);
    }

    // VisibleForTesting
    TlsEnabledSSLSocketFactory(SSLSocketFactory delegate) {
        super(delegate);
    }

    @Override
    protected void onSocketCreated(Socket socket) {
        if (!(socket instanceof SSLSocket)) {
            return;
        }
        SSLSocket sslSocket = (SSLSocket) socket;
        boolean shouldEnableTls11 = shouldEnableProtocol(sslSocket, TLS_1_1);
        boolean shouldEnableTls12 = shouldEnableProtocol(sslSocket, TLS_1_2);
        if (!shouldEnableTls11 && !shouldEnableTls12) {
            // Already enabled - unexpected, but no action is needed.
            return;
        }
        // Add missing protocols to the list of enabled protocols.
        List<String> augmentedProtocols =
                new ArrayList<>(Arrays.asList(sslSocket.getEnabledProtocols()));
        if (shouldEnableTls11) {
            augmentedProtocols.add(TLS_1_1);
        }
        if (shouldEnableTls12) {
            augmentedProtocols.add(TLS_1_2);
        }
        sslSocket.setEnabledProtocols(
                augmentedProtocols.toArray(new String[augmentedProtocols.size()]));
    }

    private static boolean shouldEnableProtocol(SSLSocket sslSocket, String protocol) {
        return !linearSearch(sslSocket.getEnabledProtocols(), protocol)
                && linearSearch(sslSocket.getSupportedProtocols(), protocol);
    }

    private static <T> boolean linearSearch(T[] array, T item) {
        for (T arrayItem : array) {
            if (item.equals(arrayItem)) {
                return true;
            }
        }
        return false;
    }
}

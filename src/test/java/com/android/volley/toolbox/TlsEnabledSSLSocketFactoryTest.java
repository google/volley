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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class TlsEnabledSSLSocketFactoryTest {
    private static final String TLS_1 = "TLSv1";
    private static final String DUMMY_PROTOCOL = "Dummy";

    @Mock
    private SSLSocketFactory mMockSystemSocketFactory;
    @Mock
    private SSLSocket mMockSocket;

    private TlsEnabledSSLSocketFactory mTlsSocketFactory;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        mTlsSocketFactory = new TlsEnabledSSLSocketFactory(mMockSystemSocketFactory);
        when(mMockSystemSocketFactory.createSocket(anyString(), anyInt())).thenReturn(mMockSocket);
    }

    @Test
    public void bothDisabled() throws IOException {
        when(mMockSocket.getSupportedProtocols()).thenReturn(
                new String[] {
                        TLS_1,
                        TlsEnabledSSLSocketFactory.TLS_1_1,
                        TlsEnabledSSLSocketFactory.TLS_1_2,
                        DUMMY_PROTOCOL });
        when(mMockSocket.getEnabledProtocols()).thenReturn(new String[] { });

        mTlsSocketFactory.createSocket("dummy", 12345);

        verify(mMockSocket).setEnabledProtocols(aryEq(new String[] {
                TlsEnabledSSLSocketFactory.TLS_1_1,
                TlsEnabledSSLSocketFactory.TLS_1_2 }));
    }

    @Test
    public void bothDisabled_hasOtherEnabledProtocols() throws IOException {
        when(mMockSocket.getSupportedProtocols()).thenReturn(
                new String[] {
                        TLS_1,
                        TlsEnabledSSLSocketFactory.TLS_1_1,
                        TlsEnabledSSLSocketFactory.TLS_1_2,
                        DUMMY_PROTOCOL });
        when(mMockSocket.getEnabledProtocols()).thenReturn(new String[] { TLS_1 });

        mTlsSocketFactory.createSocket("dummy", 12345);

        verify(mMockSocket).setEnabledProtocols(aryEq(new String[] {
                TLS_1,
                TlsEnabledSSLSocketFactory.TLS_1_1,
                TlsEnabledSSLSocketFactory.TLS_1_2 }));
    }

    @Test
    public void needsTls11() throws IOException {
        when(mMockSocket.getSupportedProtocols()).thenReturn(
                new String[] {
                        TlsEnabledSSLSocketFactory.TLS_1_1,
                        TlsEnabledSSLSocketFactory.TLS_1_2 });
        when(mMockSocket.getEnabledProtocols()).thenReturn(
                new String[] { TlsEnabledSSLSocketFactory.TLS_1_2 });

        mTlsSocketFactory.createSocket("dummy", 12345);

        verify(mMockSocket).setEnabledProtocols(aryEq(new String[] {
                TlsEnabledSSLSocketFactory.TLS_1_2,
                TlsEnabledSSLSocketFactory.TLS_1_1 }));
    }

    @Test
    public void needsTls12() throws IOException {
        when(mMockSocket.getSupportedProtocols()).thenReturn(
                new String[] {
                        TlsEnabledSSLSocketFactory.TLS_1_1,
                        TlsEnabledSSLSocketFactory.TLS_1_2 });
        when(mMockSocket.getEnabledProtocols()).thenReturn(
                new String[] { TlsEnabledSSLSocketFactory.TLS_1_1 });

        mTlsSocketFactory.createSocket("dummy", 12345);

        verify(mMockSocket).setEnabledProtocols(aryEq(new String[] {
                TlsEnabledSSLSocketFactory.TLS_1_1,
                TlsEnabledSSLSocketFactory.TLS_1_2 }));
    }

    @Test
    public void alreadyHasBothEnabled() throws IOException {
        when(mMockSocket.getSupportedProtocols()).thenReturn(
                new String[] {
                        TlsEnabledSSLSocketFactory.TLS_1_1,
                        TlsEnabledSSLSocketFactory.TLS_1_2 });
        when(mMockSocket.getEnabledProtocols()).thenReturn(
                new String[] {
                        TlsEnabledSSLSocketFactory.TLS_1_1,
                        TlsEnabledSSLSocketFactory.TLS_1_2 });

        mTlsSocketFactory.createSocket("dummy", 12345);

        verify(mMockSocket, never()).setEnabledProtocols(ArgumentMatchers.<String[]>any());
    }
}

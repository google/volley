package com.android.volley.toolbox;

import com.android.volley.Request;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class RequestBuilderTest {

    @Test
    public void setsAndAppendsUrl() {
        String url = "http://www.example.com/";
        String append = "subdomain/";

        Request<Void> request = RequestBuilder.create()
                .url(url)
                .appendUrl(append)
                .build();

        assertEquals(url + append, request.getUrl());
    }

    @Test(expected = NullPointerException.class)
    public void nullUrlIsThrownOnBuild() {
        RequestBuilder.create().build();
    }


    @Test(expected = NullPointerException.class)
    public void nullUrlIsThrownWhenPassedIn() {
        RequestBuilder.create().url(null);
    }
}

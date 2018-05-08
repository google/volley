package com.android.volley.toolbox;

import com.android.volley.NetworkResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.android.volley.utils.Utils.stringBytes;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class ResponseParsersTest {

    @Test
    public void correctParsingForString() throws Exception {
        String string = "The quick brown fox jumped over the lazy dog.";
        byte[] data = stringBytes(string);

        String result = ResponseParsers
                .forString()
                .parseNetworkResponse(new NetworkResponse(data))
                .result;

        assertEquals(string, result);
    }

    @Test
    public void correctParsingForJSONObject() throws Exception {
        String jsonString = new JSONObject()
                .put("first-key", "first-value")
                .put("second key", 3)
                .toString();
        byte[] data = stringBytes(jsonString);

        JSONObject result = ResponseParsers
                .forJSONObject()
                .parseNetworkResponse(new NetworkResponse(data))
                .result;

        assertEquals(jsonString, result.toString());
    }

    @Test
    public void correctParsingForJSONArray() throws Exception {
        String jsonString = new JSONArray()
                .put("first-key")
                .put(3)
                .toString();
        byte[] data = stringBytes(jsonString);

        JSONArray result = ResponseParsers
                .forJSONArray()
                .parseNetworkResponse(new NetworkResponse(data))
                .result;

        assertEquals(jsonString, result.toString());
    }
}

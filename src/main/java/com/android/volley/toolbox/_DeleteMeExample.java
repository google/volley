package com.android.volley.toolbox;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.Response;

import org.json.JSONObject;

import java.util.HashMap;

/**
 * Created by Dylan on 7/05/18.
 */

public class _DeleteMeExample {

    /**
     * TODO remove this (this is just for planning)
     */
    public static void example() {
        Response.Listener<Object> listener = null;
        Response.ErrorListener errorListener = null;
        //noinspection ConstantConditions
        RequestBuilder.create()
                .url("url") // TODO null check
                .appendUrl("url") // TODO Throw exception if no url yet
                .method(Request.Method.GET) // TODO null check, or default to get?
                .header("Key", "Val")
                .header("Key2", "Val2")
                .headers(new HashMap<String, String>())
                .range("name", 0, 99)
                .rangeForPage("name", 0, 100)
                .param("Key", "Val")
                .params(new HashMap<String, String>())
                .paramsEncoding("")
                .body(Bodies.forJSONObject(new JSONObject())) // todo also Bodies.forParams(map)
                .bodyContentType(BodyContents.forJSON()) // todo include this in the body,
                .parseResponse(ResponseParsers.forJSONObject()) // todo force generic, don't allow re changing
                .onSuccess(listener) // TODO parsing cannot be done after adding listeners
                .onError(errorListener) // TODO null check, don't accept a list
                .priority(Request.Priority.NORMAL)
                .tag("tag")
                .marker("debugMarker")
                .retryPolicy(new DefaultRetryPolicy())
                .retryOnServerErrors(true)
                .shouldCache(true)
                .build() // TODO don't build twice
                .addTo(Volley.newRequestQueue(null));
    }
}

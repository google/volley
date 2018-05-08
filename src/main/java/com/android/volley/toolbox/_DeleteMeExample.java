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
        Response.Listener<JSONObject> listener = null;
        Response.ErrorListener errorListener = null;
        //noinspection ConstantConditions
        RequestBuilder.<JSONObject>create()
                .url("url")
                .appendUrl("url")
                .method(Request.Method.GET)
                .header("Key", "Val")
                .header("Key2", "Val2")
                .headers(new HashMap<String, String>())
//                .range("name", 0, 99)
//                .rangeForPage("name", 0, 100)
//                .param("Key", "Val")
//                .params(new HashMap<String, String>())
//                .paramsEncoding("")
//                .body(Bodies.forJSONObject(new JSONObject())) // todo also Bodies.forParams(map)
//                .bodyContentType(BodyContents.forJSON()) // todo include this in the body,
                .parseResponse(ResponseParsers.forJSONObject()) // todo force generic, don't allow re changing, default
                .onSuccess(listener)
                .onError(errorListener)
                .priority(Request.Priority.NORMAL)
                .tag("tag")
                .retryPolicy(new DefaultRetryPolicy())
                .retryOnServerErrors(true)
                .shouldCache(true)
                .build()
//                .addTo(Volley.newRequestQueue(null))
        ;
    }
}

/**
 * TODO Add this as a documentation example somewhere
 *
 * @param <ResponseT>
 * @param <ThisT>
 */
class ExampleCustomRequestBuilder
        <ResponseT, ThisT extends ExampleCustomRequestBuilder<ResponseT, ThisT>>
        extends RequestBuilder<ResponseT, ThisT> {

    public static <T> ExampleCustomRequestBuilder<T, ? extends ExampleCustomRequestBuilder> create() {
        return new ExampleCustomRequestBuilder<>();
    }

    public static <T> ExampleCustomRequestBuilder<T, ? extends ExampleCustomRequestBuilder>
    createWithMyBusinessHeaders(/* your params here */) {
        return ExampleCustomRequestBuilder.<T>create()
                .customSetSomething("")
                .url("a")
                .customSetSomething("")
                // TODO Make this a test example
                ;
    }

    public ThisT customSetSomething(String something) {
        // this.something = something
        return getThis();
    }
}

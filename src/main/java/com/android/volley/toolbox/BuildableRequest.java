package com.android.volley.toolbox;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TODO
 */
public class BuildableRequest<T> extends Request<T> {

    /**
     * TODO remove this (this is just for planning)
     */
    public static void example() {
        Response.Listener<Object> listener = null;
        Response.ErrorListener errorListener = null;
        //noinspection ConstantConditions
        RequestBuilder.start()
                .url("url")
                .method(Method.GET)
                .header("Key", "Val")
                .header("Key2", "Val2")
                .headers(new HashMap<String, String>())
                .range("name", 0, 100)
                .param("Key", "Val")
                .params(new HashMap<String, String>())
                .body(Bodies.forJSONObject(new JSONObject()))
                .bodyContentsType("application/json")
                .parseResponse(ResponseParsers.forJSONObject()) // todo force generic, don't allow re changing
                .onSuccess(listener)
                .onSuccess(listener)
                .onError(errorListener)
                .priority(Priority.NORMAL)
                .buildAndSend(Volley.newRequestQueue(null));
    }

    private final ResponseParser<T> parser;
    private final String bodyContentType;
    private final byte[] body;
    private final List<Response.Listener<T>> mListeners;

    public BuildableRequest(
            int method,
            String url,
            List<Response.Listener<T>> listeners,
            final List<Response.ErrorListener> errorListeners,
            ResponseParser<T> parser,
            String bodyContentType,
            byte[] body
    ) {
        super(method, url, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                for (Response.ErrorListener errorListener : errorListeners) {
                    errorListener.onErrorResponse(error);
                }
            }
        });
        // TODO Null checks for listeners
        this.mListeners = new CopyOnWriteArrayList<>(listeners);
        this.parser = parser;
        this.bodyContentType = bodyContentType;
        this.body = body;
    }

    @Override
    public void cancel() {
        super.cancel();
        mListeners.clear();
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        return parser.parseNetworkResponse(response);
    }

    @Override
    protected void deliverResponse(T response) {
        for (Response.Listener<T> listener : mListeners) {
            listener.onResponse(response);
        }
    }

    @Override
    public String getBodyContentType() {
        return bodyContentType;
    }

    @Override
    public byte[] getBody() throws AuthFailureError {
        return body;
    }
}

class ExampleCustomRequestBuilder<T> extends RequestBuilder<T> {
    public static ExampleCustomRequestBuilder<Void> start() {
        return new ExampleCustomRequestBuilder<>();
    }

    public static ExampleCustomRequestBuilder<Void> startCustom() {
        return start()
                .header("", "");
    }
}

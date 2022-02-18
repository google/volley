# Make a standard request

This lesson describes how to use the common request types that Volley supports:

- `StringRequest`. Specify a URL and receive a raw string in response. See
  [Set up a RequestQueue](requestqueue.md) for an example.
- `JsonObjectRequest` and `JsonArrayRequest` (both subclasses of
  `JsonRequest`). Specify a URL and get a JSON object or array (respectively) in
  response.

If your expected response is one of these types, you probably don't have to implement a
custom request. This lesson describes how to use these standard request types. For
information on how to implement your own custom request, see
[Implement a custom request](./request-custom.md).

## Request JSON

Volley provides the following classes for JSON requests:

- `JsonArrayRequest`: A request for retrieving a
  [`JSONArray`](https://developer.android.com/reference/org/json/JSONArray)
  response body at a given URL.
- `JsonObjectRequest`: A request for retrieving a
  [`JSONObject`](https://developer.android.com/reference/org/json/JSONObject)
  response body at a given URL, allowing for an optional
  [`JSONObject`](https://developer.android.com/reference/org/json/JSONObject)
  to be passed in as part of the request body.

Both classes are based on the common base class `JsonRequest`. You use them
following the same basic pattern you use for other types of requests. For example, this
snippet fetches a JSON feed and displays it as text in the UI:

*Kotlin*

```kotlin
val url = "http://my-json-feed"

val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
        Response.Listener { response ->
            textView.text = "Response: %s".format(response.toString())
        },
        Response.ErrorListener { error ->
            // TODO: Handle error
        }
)

// Access the RequestQueue through your singleton class.
MySingleton.getInstance(this).addToRequestQueue(jsonObjectRequest)
```

*Java*

```java
String url = "http://my-json-feed";

JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
        (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {

    @Override
    public void onResponse(JSONObject response) {
        textView.setText("Response: " + response.toString());
    }
}, new Response.ErrorListener() {

    @Override
    public void onErrorResponse(VolleyError error) {
        // TODO: Handle error

    }
});

// Access the RequestQueue through your singleton class.
MySingleton.getInstance(this).addToRequestQueue(jsonObjectRequest);
```

For an example of implementing a custom JSON request based on
[Gson](https://github.com/google/gson), see the next lesson,
[Implement a custom request](request-custom.md).

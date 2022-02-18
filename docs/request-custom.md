# Implement a custom request

This lesson describes how to implement your own custom request types, for types that
don't have out-of-the-box Volley support.

## Write a custom request

Most requests have ready-to-use implementations in the toolbox; if your response is a string,
image, or JSON, you probably won't need to implement a custom `Request`.

For cases where you do need to implement a custom request, this is all you need
to do:

- Extend the `Request<T>` class, where `<T>` represents the type of parsed response
  the request expects. So if your parsed response is a string, for example,
  create your custom request by extending `Request<String>`. See the Volley
  toolbox classes `StringRequest` and `ImageRequest` for examples of
  extending `Request<T>`.
- Implement the abstract methods `parseNetworkResponse()`
  and `deliverResponse()`, described in more detail below.

### parseNetworkResponse

A `Response` encapsulates a parsed response for delivery, for a given type
(such as string, image, or JSON). Here is a sample implementation of
`parseNetworkResponse()`:

*Kotlin*

```kotlin
override fun parseNetworkResponse(response: NetworkResponse?): Response<T> {
    return try {
        val json = String(
                response?.data ?: ByteArray(0),
                Charset.forName(HttpHeaderParser.parseCharset(response?.headers)))
        Response.success(
                gson.fromJson(json, clazz),
                HttpHeaderParser.parseCacheHeaders(response))
    }
    // handle errors
}
```

*Java*

```java
@Override
protected Response<T> parseNetworkResponse(NetworkResponse response) {
    try {
        String json = new String(response.data,
                HttpHeaderParser.parseCharset(response.headers));
        return Response.success(gson.fromJson(json, clazz),
                HttpHeaderParser.parseCacheHeaders(response));
    }
    // handle errors
}
```

Note the following:

- `parseNetworkResponse()` takes as its parameter a `NetworkResponse`, which
  contains the response payload as a byte[], HTTP status code, and response headers.
- Your implementation must return a `Response<T>`, which contains your typed
  response object and cache metadata or an error, such as in the case of a parse failure.

If your protocol has non-standard cache semantics, you can build a `Cache.Entry`
yourself, but most requests are fine with something like this:

*Kotlin*

```kotlin
return Response.success(myDecodedObject,
        HttpHeaderParser.parseCacheHeaders(response))
```

*Java*

```java
return Response.success(myDecodedObject,
        HttpHeaderParser.parseCacheHeaders(response));
```

Volley calls `parseNetworkResponse()` from a worker thread. This ensures that
expensive parsing operations, such as decoding a JPEG into a Bitmap, don't block the UI
thread.

### deliverResponse

Volley calls you back on the main thread with the object you returned in
`parseNetworkResponse()`. Most requests invoke a callback interface here,
for example:

*Kotlin*

```kotlin
override fun deliverResponse(response: T) = listener.onResponse(response)
```

*Java*

```java
protected void deliverResponse(T response) {
    listener.onResponse(response);
}
```

## Example: GsonRequest

[Gson](https://github.com/google/gson) is a library for converting
Java objects to and from JSON using reflection. You can define Java objects that have the
same names as their corresponding JSON keys, pass Gson the class object, and Gson will fill
in the fields for you. Here's a complete implementation of a Volley request that uses
Gson for parsing:

*Kotlin*

```kotlin
/**
 * Make a GET request and return a parsed object from JSON.
 *
 * @param url URL of the request to make
 * @param clazz Relevant class object, for Gson's reflection
 * @param headers Map of request headers
 */
class GsonRequest<T>(
        url: String,
        private val clazz: Class<T>,
        private val headers: MutableMap<String, String>?,
        private val listener: Response.Listener<T>,
        errorListener: Response.ErrorListener
) : Request<T>(Method.GET, url, errorListener) {
    private val gson = Gson()

    override fun getHeaders(): MutableMap<String, String> = headers ?: super.getHeaders()

    override fun deliverResponse(response: T) = listener.onResponse(response)

    override fun parseNetworkResponse(response: NetworkResponse?): Response<T> {
        return try {
            val json = String(
                    response?.data ?: ByteArray(0),
                    Charset.forName(HttpHeaderParser.parseCharset(response?.headers)))
            Response.success(
                    gson.fromJson(json, clazz),
                    HttpHeaderParser.parseCacheHeaders(response))
        } catch (e: UnsupportedEncodingException) {
            Response.error(ParseError(e))
        } catch (e: JsonSyntaxException) {
            Response.error(ParseError(e))
        }
    }
}
```

*Java*

```java
public class GsonRequest<T> extends Request<T> {
    private final Gson gson = new Gson();
    private final Class<T> clazz;
    private final Map<String, String> headers;
    private final Listener<T> listener;

    /**
     * Make a GET request and return a parsed object from JSON.
     *
     * @param url URL of the request to make
     * @param clazz Relevant class object, for Gson's reflection
     * @param headers Map of request headers
     */
    public GsonRequest(String url, Class<T> clazz, Map<String, String> headers,
            Listener<T> listener, ErrorListener errorListener) {
        super(Method.GET, url, errorListener);
        this.clazz = clazz;
        this.headers = headers;
        this.listener = listener;
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return headers != null ? headers : super.getHeaders();
    }

    @Override
    protected void deliverResponse(T response) {
        listener.onResponse(response);
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        try {
            String json = new String(
                    response.data,
                    HttpHeaderParser.parseCharset(response.headers));
            return Response.success(
                    gson.fromJson(json, clazz),
                    HttpHeaderParser.parseCacheHeaders(response));
        } catch (UnsupportedEncodingException e) {
            return Response.error(new ParseError(e));
        } catch (JsonSyntaxException e) {
            return Response.error(new ParseError(e));
        }
    }
}
```

Volley provides ready-to-use `JsonArrayRequest` and `JsonArrayObject` classes
if you prefer to take that approach. See [Make a standard request](request.md) for more information.

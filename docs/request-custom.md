# Implement a custom request
This lesson describes how to implement your own custom request types for data types, parsers, and other functionality which Volley does not support out-of-the-box.

Implementing custom request types will allow you to implement the following common use cases:
- Use your preferred JSON formatter/parser such as [Gson](https://github.com/google/gson), [Moshi](https://github.com/square/moshi), or [Jackson](https://github.com/FasterXML/jackson).
- Send and parse XML data.
- Send and parse binary data.
- Add request headers such as [Content-Type](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Type), [Authorization](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Authorization), [Accept](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept), etc.
- Send POST request bodies such as `multipart/form-data`, JSON, XML, plain text, etc.

## Extend the Request class
To implement a custom request, this is what you need to do:

- Extend the `Request<T>` class, where `<T>` represents the type of parsed response that you expect.
- Implement the abstract methods `parseNetworkResponse()` and `deliverResponse()`, described in more detail below.

*See the `StringRequest`, `JsonObjectRequest`, `JsonRequest`, and `ImageRequest` toolbox classes for more examples of extending `Request<T>`.*

## Implement parseNetworkResponse()
A `Response` encapsulates a parsed response for delivery, for a given type (such as string, image, or JSON). Here is a sample implementation of `parseNetworkResponse()`:

### Kotlin
```
override fun parseNetworkResponse(response: NetworkResponse): Response<User> {
  try {
    val charset = HttpHeaderParser.parseCharset(response.headers, "utf-8");
    val json = String(response.data, charset)
    val obj = gson.fromJson(json, User.class);

    val cacheEntry = HttpHeaderParser.parseCacheHeaders(response);
    return Response.success(obj, cacheEntry);
  } catch (ex: Exception) {
    return Response.error(new ParseError(ex));
  }
}
```
### Java
```
@Override
protected Response<User> parseNetworkResponse(NetworkResponse response) {
  try {
    String charset = HttpHeaderParser.parseCharset(response.headers, "utf-8");
    String json = new String(response.data, charset);
    User obj = gson.fromJson(json, User.class);

    Cache.Entry cacheEntry = HttpHeaderParser.parseCacheHeaders(response);
    return Response.success(obj, cacheEntry);
  } catch (Exception ex) {
    return Response.error(new ParseError(ex));
  }
}
```

Note the following:
- `parseNetworkResponse()` takes as its parameter a `NetworkResponse`, which contains the response payload as a byte[], the HTTP status code, and the response headers.
- Your implementation must return a `Response<T>`, which contains your typed response object and cache metadata or an error, such as in the case of a parse failure.
- If your protocol has non-standard cache semantics, you can build a `Cache.Entry` yourself, but most requests will be fine with the `HttpHeaderParser.parseCacheHeaders()` utility method shown above.
- Volley calls `parseNetworkResponse()` from a worker thread. This ensures that expensive decoding operations, such as parsing JSON/XML, or decoding a JPEG into a Bitmap, don't block the UI thread.

## Implement deliverResponse()
Volley calls you back on the main thread with the parsed object you returned in `parseNetworkResponse()`. You should invoke a callback function here, for example:

### Kotlin
```
override fun deliverResponse(response: User) = listener?.onResponse(response)
```
### Java
```
@Override
protected void deliverResponse(User response) {
  if (listener != null) { 
    listener.onResponse(response);
  } 
}
```

## Example: GsonRequest
[Gson](https://github.com/google/gson) is a library for converting Java objects to and from JSON using reflection. You can define Java objects that have the same names as their corresponding JSON keys, pass Gson the class object, and Gson will fill in the fields for you. Here's a complete implementation of a Volley request that uses Gson for parsing:

### Kotlin
```
class GsonRequest<T>(
    int method,
    url: String,
    private val clazz: Class<T>,
    private val headers: MutableMap<String, String>?,
    private val requestBody: Object?,
    private val listener: Response.Listener<T>?,
    errorListener: Response.ErrorListener?
) : Request<T>(method, url, errorListener) {

    private val gson = Gson()

    override fun getHeaders(): MutableMap<String, String> = headers ?: super.getHeaders()

    override fun getBodyContentType(): String = "application/json; charset=utf-8"

    override fun getBody(): byte[] {
        try {
            if (requestBody != null) {
                return gson.toJson(requestBody).getBytes("utf-8")
            } else {
                return null;
            }
        } catch (ex: Exception) {
            // handle error ...
        }
    }

    override fun parseNetworkResponse(response: NetworkResponse?): Response<T> {
        try {
            val charset = HttpHeaderParser.parseCharset(response.headers, "utf-8");
            val json = String(response.data, charset)
            val obj = gson.fromJson(json, clazz);

            val cacheEntry = HttpHeaderParser.parseCacheHeaders(response);
            return Response.success(obj, cacheEntry);
        } catch (ex: Exception) {
            return Response.error(new ParseError(ex));
        }
    }

    override fun deliverResponse(response: T) = listener?.onResponse(response)
}
```
### Java
```
public class GsonRequest<T> extends Request<T> {
    private final Gson gson = new Gson();
    private final Class<T> clazz;
    private final Map<String, String> headers;
    private final Object requestBody;
    private final Listener<T> listener;

    /**
     * Make a GET request and return a parsed object from JSON.
     *
     * @param method the HTTP method to use
     * @param url URL to fetch the JSON from
     * @param clazz Relevant class object, for Gson's reflection
     * @param headers Map of request headers
     * @param requestBody JSON data to be posted as the body of the request.
     *                    Or null to skip the request body.
     * @param listener Listener to receive the parsed response
     * @param errorListener Error listener, or null to ignore errors
     */
    public GsonRequest(
        int method,
        String url, 
        Class<T> clazz, 
        @Nullable Map<String, String> headers, 
        @Nullable Object requestBody,
        @Nullable Listener<T> listener, 
        @Nullable ErrorListener errorListener
    ) {    
        super(method, url, errorListener);
        this.clazz = clazz;
        this.headers = headers;
        this.listener = listener;
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers != null ? headers : super.getHeaders();
    }

    @Override
    public String getBodyContentType() {
        return "application/json; charset=utf-8";
    }

    @Override
    public byte[] getBody() {
        try {
            if (requestBody != null) {
                return gson.toJson(requestBody).getBytes("utf-8");
            } else {
                return null;
            }
        } catch (Exception ex) {
            // handle error ...
        }
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        try {
            String charset = HttpHeaderParser.parseCharset(response.headers, "utf-8");
            String json = new String(response.data, charset);
            T obj = gson.fromJson(json, clazz);

            Cache.Entry cacheEntry = HttpHeaderParser.parseCacheHeaders(response);
            return Response.success(obj, cacheEntry);
        } catch (Exception ex) {
            return Response.error(new ParseError(ex));
        }
    }

    @Override
    protected void deliverResponse(T response) {
        if (listener != null) {
            listener.onResponse(response);
        }
    }
}
```

## Previous Lesson
[Make a standard request](request-standard.md)

## Next Lesson
[Setting up a RequestQueue](request-queue.md)

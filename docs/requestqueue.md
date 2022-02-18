# Set up a RequestQueue

The previous lesson showed you how to use the convenience method
`Volley.newRequestQueue` to set up a `RequestQueue`, taking advantage of
Volley's default behaviors. This lesson walks you through the explicit steps of creating a
`RequestQueue`, to allow you to supply your own custom behavior.

This lesson also describes the recommended practice of creating a `RequestQueue`
as a singleton, which makes the `RequestQueue` last the lifetime of your app.

## Set up a network and cache

A `RequestQueue` needs two things to do its job: a network to perform transport
of the requests, and a cache to handle caching. There are standard implementations of these
available in the Volley toolbox: `DiskBasedCache` provides a one-file-per-response
cache with an in-memory index, and `BasicNetwork` provides a network transport based
on your preferred HTTP client.

`BasicNetwork` is Volley's default network implementation. A `BasicNetwork`
must be initialized with the HTTP client your app is using to connect to the network.
Typically this is an
[`HttpURLConnection`](https://developer.android.com/reference/java/net/HttpURLConnection).

This snippet shows you the steps involved in setting up a `RequestQueue`:

*Kotlin*

```kotlin
// Instantiate the cache
val cache = DiskBasedCache(cacheDir, 1024 * 1024) // 1MB cap

// Set up the network to use HttpURLConnection as the HTTP client.
val network = BasicNetwork(HurlStack())

// Instantiate the RequestQueue with the cache and network. Start the queue.
val requestQueue = RequestQueue(cache, network).apply {
    start()
}

val url = "http://www.example.com"

// Formulate the request and handle the response.
val stringRequest = StringRequest(Request.Method.GET, url,
         Response.Listener<String> { response ->
            // Do something with the response
        },
        Response.ErrorListener { error ->
            // Handle error
            textView.text = "ERROR: %s".format(error.toString())
        })

// Add the request to the RequestQueue.
requestQueue.add(stringRequest)

// ...
```

*Java*

```java
RequestQueue requestQueue;

// Instantiate the cache
Cache cache = new DiskBasedCache(getCacheDir(), 1024 * 1024); // 1MB cap

// Set up the network to use HttpURLConnection as the HTTP client.
Network network = new BasicNetwork(new HurlStack());

// Instantiate the RequestQueue with the cache and network.
requestQueue = new RequestQueue(cache, network);

// Start the queue
requestQueue.start();

String url = "http://www.example.com";

// Formulate the request and handle the response.
StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
        new Response.Listener<String>() {
    @Override
    public void onResponse(String response) {
        // Do something with the response
    }
},
    new Response.ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {
            // Handle error
    }
});

// Add the request to the RequestQueue.
requestQueue.add(stringRequest);

// ...
```

If you just need to make a one-time request and don't want to leave the thread pool
around, you can create the `RequestQueue` wherever you need it and call `stop()` on the
`RequestQueue` once your response or error has come back, using the
`Volley.newRequestQueue()` method described in [Sending a Simple Request](./simple.md).
But the more common use case is to create the `RequestQueue` as a
singleton to keep it running for the lifetime of your app, as described in the next section.

## Use a singleton pattern

If your application makes constant use of the network, it's probably most efficient to
set up a single instance of `RequestQueue` that will last the lifetime of your app.
You can achieve this in various ways. The recommended approach is to implement a singleton
class that encapsulates `RequestQueue` and other Volley functionality. Another approach is to
subclass [`Application`](https://developer.android.com/reference/android/app/Application) and 
set up the `RequestQueue` in
[`Application.onCreate()`](https://developer.android.com/reference/android/app/Application#onCreate()).
But this approach is discouraged; a static singleton can provide the same functionality in a 
more modular way.

A key concept is that the `RequestQueue` must be instantiated with the
[`Application`](https://developer.android.com/reference/android/app/Application) context, not an
[`Activity`](https://developer.android.com/reference/android/app/Activity) context. This
ensures that the `RequestQueue` will last for the lifetime of your app, instead of
being recreated every time the activity is recreated (for example, when the user
rotates the device).

Here is an example of a singleton class that provides `RequestQueue` and
`ImageLoader` functionality:

*Kotlin*

```kotlin
class MySingleton constructor(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: MySingleton? = null
        fun getInstance(context: Context) =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MySingleton(context).also {
                    INSTANCE = it
                }
            }
    }
    val imageLoader: ImageLoader by lazy {
        ImageLoader(requestQueue,
                object : ImageLoader.ImageCache {
                    private val cache = LruCache<String, Bitmap>(20)
                    override fun getBitmap(url: String): Bitmap {
                        return cache.get(url)
                    }
                    override fun putBitmap(url: String, bitmap: Bitmap) {
                        cache.put(url, bitmap)
                    }
                })
    }
    val requestQueue: RequestQueue by lazy {
        // applicationContext is key, it keeps you from leaking the
        // Activity or BroadcastReceiver if someone passes one in.
        Volley.newRequestQueue(context.applicationContext)
    }
    fun <T> addToRequestQueue(req: Request<T>) {
        requestQueue.add(req)
    }
}
```

*Java*

```java
public class MySingleton {
    private static MySingleton instance;
    private RequestQueue requestQueue;
    private ImageLoader imageLoader;
    private static Context ctx;

    private MySingleton(Context context) {
        ctx = context;
        requestQueue = getRequestQueue();

        imageLoader = new ImageLoader(requestQueue,
                new ImageLoader.ImageCache() {
            private final LruCache<String, Bitmap>
                    cache = new LruCache<String, Bitmap>(20);

            @Override
            public Bitmap getBitmap(String url) {
                return cache.get(url);
            }

            @Override
            public void putBitmap(String url, Bitmap bitmap) {
                cache.put(url, bitmap);
            }
        });
    }

    public static synchronized MySingleton getInstance(Context context) {
        if (instance == null) {
            instance = new MySingleton(context);
        }
        return instance;
    }

    public RequestQueue getRequestQueue() {
        if (requestQueue == null) {
            // getApplicationContext() is key, it keeps you from leaking the
            // Activity or BroadcastReceiver if someone passes one in.
            requestQueue = Volley.newRequestQueue(ctx.getApplicationContext());
        }
        return requestQueue;
    }

    public <T> void addToRequestQueue(Request<T> req) {
        getRequestQueue().add(req);
    }

    public ImageLoader getImageLoader() {
        return imageLoader;
    }
}
```

Here are some examples of performing `RequestQueue` operations using the singleton
class:

*Kotlin*

```kotlin
// Get a RequestQueue
val queue = MySingleton.getInstance(this.applicationContext).requestQueue

// ...

// Add a request (in this example, called stringRequest) to your RequestQueue.
MySingleton.getInstance(this).addToRequestQueue(stringRequest)
```

*Java*

```java
// Get a RequestQueue
RequestQueue queue = MySingleton.getInstance(this.getApplicationContext()).
    getRequestQueue();

// ...

// Add a request (in this example, called stringRequest) to your RequestQueue.
MySingleton.getInstance(this).addToRequestQueue(stringRequest);
```

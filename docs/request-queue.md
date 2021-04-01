# Setting up a RequestQueue

This lesson walks you through the process of configuring the `RequestQueue` to supply your own custom behavior, and discusses options for managing/sharing your `RequestQueue` instances.

## Setup a network and cache
A `RequestQueue` needs two things to do its job: a network to perform transport of the requests, and a cache to handle caching. There are standard implementations of these available in the Volley toolbox: [DiskBasedCache](https://github.com/google/volley/blob/master/src/main/java/com/android/volley/toolbox/DiskBasedCache.java) provides a one-file-per-response cache with an in-memory index, and [BasicNetwork](https://github.com/google/volley/blob/master/src/main/java/com/android/volley/toolbox/BasicNetwork.java) provides a network transport based on your preferred HTTP client.

`BasicNetwork` is Volley's default network implementation. A BasicNetwork must be initialized with the HTTP client your app is using to connect to the network. Typically this is an [HttpURLConnection](https://developer.android.com/reference/java/net/HttpURLConnection), but other HTTP clients are supported (such as [OkHttpStack](https://gist.github.com/JakeWharton/5616899).)

This snippet shows you the steps involved in configuring a `RequestQueue`:

### Kotlin
```
// Instantiate the cache
val cache = DiskBasedCache(context.getCacheDir(), 1024 * 1024) // 1MB cap

// Use HttpURLConnection as the HTTP client
val network = BasicNetwork(HurlStack())

// Instantiate the RequestQueue
val requestQueue = RequestQueue(cache, network)

// Start the queue
requestQueue.start()
```
### Java
```
// Instantiate the cache
Cache cache = new DiskBasedCache(context.getCacheDir(), 1024 * 1024); // 1MB cap

// Use HttpURLConnection as the HTTP client
Network network = new BasicNetwork(new HurlStack());

// Instantiate the RequestQueue
RequestQueue requestQueue = new RequestQueue(cache, network);

// Start the queue
requestQueue.start();
```

## Managing RequestQueue Instances
There are three common patterns for managing the instances of `RequestQueue`:

- Each `Activity`/`Service`/`Worker` manages their own `RequestQueue`. The `RequestQueue` is created in `onCreate()` and disposed of in `onDestroy()`. This is the easiest to implement and works fine for small projects. It does not prevent multiple `Requests` from executing simultaneously, but is generally sufficient if you do not need to make network requests while the app is asleep. 
- The `Application` manages the shared `RequestQueue`. This ensures that there is only one `RequestQueue` instance and prevents multiple `Requests` from being in-flight at the same time. 
- If you are using the [Android Architecture Components](https://developer.android.com/topic/libraries/architecture), then the [Repositories](https://developer.android.com/jetpack/guide#recommended-app-arch)/[DataSources](https://developer.android.com/topic/libraries/architecture/paging/data)/[ViewModels](https://developer.android.com/topic/libraries/architecture/viewmodel) can manage the `RequestQueue` instances.
- Using Singletons is **strongly discouraged.**
  - [So Singletons are bad, then what?](https://softwareengineering.stackexchange.com/questions/40373/so-singletons-are-bad-then-what)


## RequestQueue managed by Activity
See the code sample below for an `Activity` which manages its own `RequestQueue`:

### Kotlin
```
class MyActivity: AppCompatActivity {
    ...
    private val requestQueue: RequestQueue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ...
        requestQueue = Volley.newRequestQueue(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        requestQueue?.stop()
    }
}
```
### Java
```
public class MyActivity extends AppCompatActivity {
    ...
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ...
        requestQueue = Volley.newRequestQueue(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (requestQueue != null) {
            requestQueue.stop();
        }
    }
}
```

## RequestQueue managed by Application
See the code sample below for an `Application` which creates and holds a `RequestQueue` that can be shared between all application components:

### Kotlin
```
class MyApplication: Application {
    ...
    private val requestQueue: RequestQueue

    override fun onCreate() {
        super.onCreate()
        requestQueue = Volley.newRequestQueue(this)
    }

    fun getRequestQueue(): RequestQueue = requestQueue
}
```
### Java
```
public class MyApplication extends Application {
    private RequestQueue requestQueue;

    @Override
    protected void onCreate() {
        super.onCreate();
        requestQueue = Volley.newRequestQueue(this);
    }

    public RequestQueue getRequestQueue() {
        return requestQueue;
    }
}
```

And below is a sample `Activity` which uses it:

### Kotlin
```
class MyActivity: AppCompatActivity {
    ...
    private val app: MyApplication
    private val requestQueue: RequestQueue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        ...
        app = getApplication() as MyApplication
        requestQueue = Volley.newRequestQueue(this)
    }
}
```
### Java
```
public class MyActivity extends AppCompatActivity {
    ...
    private MyApplication app;
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ...
        app = (MyApplication) getApplication();
        requestQueue = Volley.newRequestQueue(this);
    }
}
```

## RequestQueue managed by Repository
See the code sample below for a `Repository` which manages its own `RequestQueue`:

### Kotlin
```
class UserRepository(context: Context) {
    private val requestQueue: RequestQueue;

    init {
        requestQueue = Volley.newRequestQueue(context.getApplicationContext());
    }

    fun getUsers(listener: Response.Listener<List<User>>, errorListener: Response.ErrorListener) {
        val request = ...
        requestQueue.add(request);
    }

    fun getUserById(userId: String, listener: Response.Listener<User>, errorListener: Response.ErrorListener) {
        val request = ...
        requestQueue.add(request);
    }

    fun stop() {
        requestQueue.stop()
    }
}
```
### Java
```
public class UserRepository {
    private RequestQueue requestQueue;

    public UserRepository(Context context) {
        requestQueue = Volley.newRequestQueue(context.getApplicationContext());
    }

    public void getUsers(Response.Listener<List<User>> listener, Response.ErrorListener errorListener) {
        Request request = ...
        requestQueue.add(request);
    }

    public void getUserById(String userId, Response.Listener<User> listener, Response.ErrorListener errorListener) {
        Request request = ...
        requestQueue.add(request);
    }

    public void stop() {
        requestQueue.stop();
    }
}
```


## Previous Lesson
[Implement a custom request](request-custom.md)

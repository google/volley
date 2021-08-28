# Send a simple request

This lesson describes how to send a simple request using Volley, and how to cancel a request.

At a high level, you use Volley by creating a `RequestQueue` and enqueuing `Request` objects as needed. The `RequestQueue` manages worker threads for running the network operations, reading from and writing to the cache, and parsing responses. Requests do the parsing of raw responses and Volley takes care of dispatching the parsed response back to the main thread for delivery.

## Add the gradle dependency
The easiest way to add Volley to your project is to add the following dependency to your app's build.gradle file:

```
dependencies {
  ...
  implementation 'com.android.volley:volley:1.2.0'
}
```

## Add the INTERNET permission
To use Volley, you must add the [android.permission.INTERNET](https://developer.android.com/reference/android/Manifest.permission#INTERNET) permission to your app's manifest. Without this, your app won't be able to connect to the network.

```
<uses-permission android:name="android.permission.INTERNET" />
```

## Send a request

### Kotlin
```
// Instantiate the RequestQueue.
val requestQueue = Volley.newRequestQueue(this);

// Request the contents of the provided URL as a raw string.
val request: Request = StringRequest(
  Request.Method.GET, 
  "https://www.example.com",
  (response: String) -> {
    Log.i("Volley", "Response: " + response);
  },
  (error: VolleyError) -> {
    Log.e("Volley", "Error: " + error.getMessage(), error);
  }
);

// Add the request to the RequestQueue.
requestQueue.add(request);
```

### Java
```
// Instantiate the RequestQueue.
RequestQueue requestQueue = Volley.newRequestQueue(this);

// Request the contents of the provided URL as a raw string.
Request request = new StringRequest(
  Request.Method.GET, 
  "https://www.example.com",
  (String response) -> {
    Log.i("Volley", "Response: " + response);
  },
  (VolleyError error) -> {
    Log.e("Volley", "Error: " + error.getMessage(), error);
  }
);

// Add the request to the RequestQueue.
requestQueue.add(request);
```

To send a request, you simply construct a `Request` and enqueue it to the `RequestQueue` with `add()`. Once you enqueue the request it moves through the pipeline, gets serviced, has its raw response parsed, and is delivered back to your callback.

Volley always delivers parsed responses on the main thread. Running on the main thread is convenient for populating UI controls with received data, as you can freely modify UI controls directly from your response handler, but it's especially critical to many of the important semantics provided by the library, particularly related to canceling requests.

Note that expensive operations like blocking I/O and parsing/decoding are done on worker threads. You can enqueue a request from any thread, but responses are always delivered on the main thread.

## Cancel a request

To cancel a request, call `cancel()` on your `Request` object. Once cancelled, Volley guarantees that your response handler will not be called. This allows you to cancel a pending request when your activity/service/worker is stopped.

### Kotlin
```
// Add a field to hold a reference to the pending request.
private val request: Request = null

// Create and enqueue a request.
request = ...
requestQueue.add(request)

// Cancel the request when the activity is stopped.
override fun onStop() {
  super.onStop()
  request?.cancel()
}
```
### Java
```
// Add a field to hold a reference to the pending request.
private Request request = null;

// Create and enqueue a request.
request = ...
requestQueue.add(request);

// Cancel the request when the activity is stopped.
@Override
protected void onStop() {
  super.onStop();
  if (request != null) {
    request.cancel();
  }
}
```

However, to use this approach, you would need to track all in-flight requests in order to cancel all of them at the appropriate time. Which can easily lead to memory leaks and race conditions. But there is an easier way: you can associate a tag with each request. You can then use this tag to provide a scope of requests to cancel.

Here is an example that uses a constant string value for the tag:

### Kotlin
```
// Define your tag.
val VOLLEY_TAG = "MainActivity"

// Create and enqueue a request.
val request = ...
request.tag = VOLLEY_TAG
requestQueue.add(request)

// Cancel all requests that have this tag
requestQueue?.cancelAll(VOLLEY_TAG)
```
### Java
```
// Define your tag.
public static final String VOLLEY_TAG = "MainActivity";

// Create and enqueue a request.
Request request = ...
request.setTag(VOLLEY_TAG);
requestQueue.add(request);

// Cancel all requests that have this tag
if (requestQueue != null) {
  requestQueue.cancelAll(TAG);
}
```

For example, you can tag all of your requests with the activity/service/worker they are being made on behalf of, and cancel them when the component becomes stopped. Similarly, you could tag all the requests in a `ViewPager` with their respective tab ids, and cancel on swipe to make sure that the new tab isn't being held up by requests from another one.

Take care when canceling requests. If you are depending on your response handler to advance a state or kick off another process, you need to account for this. Again, the response handler will not be called.

## Setting up a RequestQueue

Volley provides a convenience method `Volley.newRequestQueue` that sets up a `RequestQueue` for you, using default values, as shown above. See [Setting up a RequestQueue](request-queue.md), for information on how to set up and configure a `RequestQueue`.

## Next Lesson
[Make a standard request](request-standard.md)

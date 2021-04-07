# Volley Overview

Volley is an HTTP library that makes networking for Android apps easier and most importantly, faster. 

Volley offers the following benefits:
- Automatic scheduling of network requests.
- Multiple concurrent network connections.
- Transparent disk and memory response caching with standard [HTTP Cache Coherence.](https://en.wikipedia.org/wiki/Cache_coherence)
- Support for request prioritization.
- Cancellation request API. You can cancel a single request, or you can set blocks or scopes of requests to cancel.
- Ease of customization, for example, for retry and backoff.
- Strong ordering that makes it easy to correctly populate your UI with data fetched asynchronously from the network.
- Debugging and tracing tools.

Volley excels at RPC-type operations used to populate a UI, such as fetching a page of search results as structured data. It integrates easily with any protocol and comes out of the box with support for raw strings, images, and JSON. By providing built-in support for the features you need, Volley frees you from writing boilerplate code and allows you to concentrate on the logic that is specific to your app.

Volley is not suitable for large download or streaming operations, since Volley holds all responses in memory during parsing. For large download operations, consider using an alternative like [DownloadManager.](https://developer.android.com/reference/android/app/DownloadManager)

The core Volley library is developed on [GitHub](https://github.com/google/volley) and contains the main request dispatch pipeline as well as a set of commonly applicable utilities, available in the Volley "toolbox." The easiest way to add Volley to your project is to add the following dependency to your app's `build.gradle` file:

```
dependencies {
  ...
  implementation 'com.android.volley:volley:1.2.0'
}
```

## Lessons

- [Send a simple request](request-simple.md)

  Learn how to send a simple request using Volley, and how to cancel a request.

- [Make a standard request](request-standard.md)

  Learn how to send a request using one of Volley's out-of-the-box request types (raw strings, images, and org.json).

- [Implement a custom request](request-custom.md)

  Learn how to implement a custom request.

- [Setting up a RequestQueue](request-queue.md)

  Learn how to customize your `RequestQueue`.

- [Using Volley with WorkManager](request-workmanager.md)

  How to implement a `ListenableWorker` which makes asynchronous requests using volley.

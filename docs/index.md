# Volley overview

Volley is an HTTP library that makes networking for Android apps easier and most importantly,
faster. Volley is available on [GitHub](https://github.com/google/volley).

Volley offers the following benefits:

- Automatic scheduling of network requests.
- Multiple concurrent network connections.
- Transparent disk and memory response caching with standard HTTP 
  [cache coherence](https://en.wikipedia.org/wiki/Cache_coherence).
- Support for request prioritization.
- Cancellation request API. You can cancel a single request, or you can set blocks or scopes of 
  requests to cancel.
- Ease of customization, for example, for retry and backoff.
- Strong ordering that makes it easy to correctly populate your UI with data fetched asynchronously 
  from the network.
- Debugging and tracing tools.

Volley excels at RPC-type operations used to populate a UI, such as fetching a page of
search results as structured data. It integrates easily with any protocol and comes out of
the box with support for raw strings, images, and JSON. By providing built-in support for
the features you need, Volley frees you from writing boilerplate code and allows you to
concentrate on the logic that is specific to your app.
Volley is not suitable for large download or streaming operations, since Volley holds
all responses in memory during parsing. For large download operations, consider using an
alternative like
[`DownloadManager`](https://developer.android.com/reference/android/app/DownloadManager).

The core Volley library is developed on [GitHub](https://github.com/google/volley) and
contains the main request dispatch pipeline as well as a set of commonly applicable utilities,
available in the Volley "toolbox." The easiest way to add Volley to your project is to add the
following dependency to your app's build.gradle file:

*Groovy*

```groovy
dependencies {
    implementation 'com.android.volley:volley:1.2.1'
}
```

*Kotlin*

```kotlin
dependencies {
    implementation("com.android.volley:volley:1.2.1")
}
```

You can also clone the Volley repository and set it as a library project:

1. Git clone the repository by typing the following at the command line:

    ```console
    git clone https://github.com/google/volley
    ```

2. Import the downloaded source into your app project as an Android library module as described
   in [Create an Android Library](https://developer.android.com/studio/projects/android-library).

## Lessons

[**Send a simple request**](./simple.md)

Learn how to send a simple request using the default behaviors of Volley, and how
to cancel a request.

[**Set up RequestQueue**](./requestqueue.md)

Learn how to set up a `RequestQueue`, and how to implement a singleton
pattern to create a `RequestQueue` that lasts the lifetime of your app.

[**Make a standard request**](./request.md)

Learn how to send a request using one of Volley's out-of-the-box request types
(raw strings, images, and JSON).

[**Implement a custom request**](./request-custom.md)

Learn how to implement a custom request.

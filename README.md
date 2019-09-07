### Volley

Volley is an HTTP library that makes networking for Android apps easier and, most
importantly, faster.

For more information about Volley and how to use it, visit the [Android developer training
page](https://developer.android.com/training/volley/index.html).

About synchronous mode or asynchronous mode

jpd236 said:
The app-facing API for making requests is asynchronous. You add requests to the queue and the provided response listener will be invoked when the request completes.

Some of Volley's internals are synchronous when it comes to dispatching the requests, but for most applications this would not be a major concern. If you are dispatching many parallel requests or are severely memory constrained then Volley's design may not be ideal because it spawns a few threads for cache and network dispatching that never stop themselves. Fixing that is tracked by #181, but from the app's perspective this shouldn't result in any changes to the API surface unless you're making custom HTTP stacks or cache implementations.


Similar project

Cronet(https://github.com/GoogleChromeLabs/cronet-sample)


# Using Volley with WorkManager

Using *WorkManager* and *Volley* together requires some special considerations:
- Volley executes requests asynchronously on a background thread.
- A [Worker](https://developer.android.com/reference/androidx/work/Worker) is required to perform its synchronously on the provided background thread.
- When you need to call asynchronous APIs, you should use [ListenableWorker](https://developer.android.com/reference/androidx/work/ListenableWorker) instead.

As such, this lesson will cover how to create a `ListenableWorker` that executes an asynchronous request with volley.

## Required Dependencies
The code below requires the following additional dependencies:

```
implementation 'androidx.work:work-runtime:2.5.0'
implementation 'androidx.concurrent:concurrent-futures:1.1.0'
```

## 
An example `ListenableWorker` which retrieves a single user object from a REST API is shown below.

### Java

```
public class GetUserWorker extends ListenableWorker {
  private static final String LOG_TAG = MyWorker.class.getSimpleName();

  private MyApp app;
  private RequestQueue requestQueue;

  public GetUserWorker(@NonNull Context context, @NonNull WorkerParameters params) {
    super(context, params);
    app = (MyApp) context;
    requestQueue = app.getRequestQueue();
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    return CallbackToFutureAdapter.getFuture(resolver -> {
      Request request = new JsonObjectRequest(
        Request.Method.GET,
        "https://reqres.in/api/users/2",
        null,
        (JSONObject response) -> {
          // process response ...
          resolver.set(Result.success());

        },
        (VolleyError error) -> {
          // handle error ...
          resolver.set(Result.retry());
        }
      );

      requestQueue.add(request);
      return request;
    });
  }
}
```

## Further Reading
- [Schedule tasks with WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
- [WorkManager basics](https://medium.com/androiddevelopers/workmanager-basics-beba51e94048)
- [Worker](https://developer.android.com/reference/androidx/work/Worker)
- [ListenableWorker](https://developer.android.com/reference/androidx/work/ListenableWorker)
- [Threading in ListenableWorker](https://developer.android.com/topic/libraries/architecture/workmanager/advanced/listenableworker)
- [androidx.concurrent](https://developer.android.com/jetpack/androidx/releases/concurrent)
- [CallbackToFutureAdapter](https://developer.android.com/reference/androidx/concurrent/futures/CallbackToFutureAdapter)

## Previous Lesson
[Setting up a RequestQueue](request-queue.md)

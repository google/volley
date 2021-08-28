# Make a standard request
This lesson describes how to use Volley's built-in request types:

- `StringRequest` - Parse the response as a raw string.
- `JsonObjectRequest` - Parse the response as an [org.json.JSONObject](https://developer.android.com/reference/org/json/JSONObject)
- `JsonArrayRequest` - Parse the response as an [org.json.JSONArray](https://developer.android.com/reference/org/json/JSONArray)

Extending volley with more request types will be covered in the next lesson [Implementing a Custom Request](request-custom.md)


## StringRequest
`StringRequest` allows you to specify a URL and retrieve the contents as a raw string. This is suitable for simple testing, but moves the parsing of the response onto the main/ui thread, so is generally not optimal.

### Kotlin
```
val request = StringRequest(
  Request.Method.GET, 
  "https://www.example.com",
  (response: String) -> {
    Log.i(LOG_TAG, "Response: " + response);
  },
  (error: VolleyError) -> {
    Log.e(LOG_TAG, "Error: " + error.getMessage(), error);
  }
);
requestQueue.add(request);
```

### Java
```
Request request = new StringRequest(
  Request.Method.GET, 
  "https://www.example.com",
  (String response) -> {
    Log.i(LOG_TAG, "Response: " + response);
  },
  (VolleyError error) -> {
    Log.e(LOG_TAG, "Error: " + error.getMessage(), error);
  }
);
requestQueue.add(request);
```

### Posting data with StringRequest

*`StringRequest` does not currently provide a mechanism to POST data to the URL.*


## JsonObjectRequest
`JsonObjectRequest` allows you to specify a URL and parse the contents as an [org.json.JSONObject](https://developer.android.com/reference/org/json/JSONObject). This will send the request and parse the response on the background thread, which is preferable over `StringRequest`.

### Kotlin
```
val request = JsonObjectRequest(
  Request.Method.GET, 
  "http://time.jsontest.com",
  null,  // indicates no data will posted as request body
  (response: JSONObject) -> {
    Log.i(LOG_TAG, "Response: " + response);
  },
  (error: VolleyError) -> {
    Log.e(LOG_TAG, "Error: " + error.getMessage(), error);
  }
);
requestQueue.add(request);
```

### Java
```
Request request = new JsonObjectRequest(
  Request.Method.GET, 
  "http://time.jsontest.com",
  null, // indicates no data will posted as request body
  (JSONObject response) -> {
    Log.i(LOG_TAG, "Response: " + response);
  },
  (VolleyError error) -> {
    Log.e(LOG_TAG, "Error: " + error.getMessage(), error);
  }
);
requestQueue.add(request);
```

### Posting data with JsonObjectRequest
`JsonObjectRequest` also allows to send an [org.json.JSONObject](https://developer.android.com/reference/org/json/JSONObject) as the request body.

### Kotlin
```
val requestData = JSONObject()
requestData.put("id", 123)
requestData.put("name", "example")

val request = JsonObjectRequest(
  Request.Method.POST, 
  "https://reqres.in/api/users",
  requestData,
  (response: JSONObject) -> {
    Log.i(LOG_TAG, "Response: " + response);
  },
  (error: VolleyError) -> {
    Log.e(LOG_TAG, "Error: " + error.getMessage(), error);
  }
);
requestQueue.add(request);
```

### Java
```
JSONObject requestData = new JSONObject();
requestData.put("id", 123);
requestData.put("name", "example");

Request request = new JsonObjectRequest(
  Request.Method.POST, 
  "https://reqres.in/api/users",
  requestData,
  (JSONObject response) -> {
    Log.i(LOG_TAG, "Response: " + response);
  },
  (VolleyError error) -> {
    Log.e(LOG_TAG, "Error: " + error.getMessage(), error);
  }
);
requestQueue.add(request);
```


## JsonArrayRequest
`JsonArrayRequest` allows you to specify a URL and parse the contents as an [org.json.JSONArray](https://developer.android.com/reference/org/json/JSONArray). This will send the request and parse the response on the background thread, which is preferable over `StringRequest`.

### Kotlin
```
val request = JsonArrayRequest(
  "https://jsonplaceholder.typicode.com/users",
  (response: JSONArray) -> {
    Log.i(LOG_TAG, "Response: " + response);
  },
  (error: VolleyError) -> {
    Log.e(LOG_TAG, "Error: " + error.getMessage(), error);
  }
);
requestQueue.add(request);
```

### Java
```
Request request = new JsonArrayRequest(
  "https://jsonplaceholder.typicode.com/users",
  (JSONArray response) -> {
    Log.i(LOG_TAG, "Response: " + response);
  },
  (VolleyError error) -> {
    Log.e(LOG_TAG, "Error: " + error.getMessage(), error);
  }
);
requestQueue.add(request);
```

### Posting data with JsonArrayRequest
*`JsonArrayRequest` does not currently allow you to POST a [org.json.JSONObject](https://developer.android.com/reference/org/json/JSONObject) to the URL. But you can create a custom `Request` type to implement this.*



### Using other JSON libraries
See [Implementing a Custom Request](request-custom.md) for how to use other JSON libraries with Volley. 


## Previous Lesson
[Send a simple request](request-simple.md)

## Next Lesson
[Implement a custom request](request-custom.md)
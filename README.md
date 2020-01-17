### Volley

Volley is an HTTP library that makes networking for Android apps easier and, most
importantly, faster.

For more information about Volley and how to use it, visit the [Android developer training
page](https://developer.android.com/training/volley/index.html).

### How to use MultipartRequest

- MutipartRequest: A mulipart request for multiple files uploading, included text parameters
- A request example:
```java

String url = "https://examples.com/multipart_feed";
MutipartRequest volleyMultipartRequest = new MutipartRequest(Request.Method.POST, url, new Response.Listener<NetworkResponse>() {
            @Override
            public void onResponse(NetworkResponse response) {
                //TODO: handle respone
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                //TODO: handle error
            }
        }) {
            @Override
            protected Map<String, MultipartData> getByteData() throws AuthFailureError {
                //add files with filenames
                Map<String, MultipartData> params = new HashMap<>();
                try {
                  byte[] audioData = FileUtils.readFileToByteArray(file);
                  params.put(fileName, new MultipartData(fileName, audioData, "audio/mpeg"));
                }
                catch (Exception e) {
                  e.printStackTrace();
                }
               
                return params;
            }
            
            {
            @Override
            protected Map<String, ArrayList<MultipartData>> getArrayByteData() throws AuthFailureError {
                Map<String, ArrayList<MultipartData>> params = new HashMap<>();
                ArrayList<MultipartData> arrData = new ArrayList<>();
                for (String filePath : listFiles) {
                    Uri uri = Uri.parse(filePath);
                    try {
                        byte[] documentData = getByteArrayFromUri(uri);
                        arrData.add(new MultipartData(fileName, documentData, "application/octet-stream"));
                     } catch (Exception e) {
                        e.printStackTrace();
                     }
                }
                    
                params.put("uploadFiles[]", arrData);
                return params;
            }

            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                //add text paramaters
                Map<String, String> params = new HashMap<>();
                params.put("first_name", "John");
                params.put("last_name", "Doe");
                
                return params;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<String, String>();
                params.put("Authorization", "usertoken");
                return params;
            }
        };

```
  

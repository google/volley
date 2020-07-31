package com.android.volley.toolbox;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import java.io.IOException;
import java.util.Map;

public abstract class AsyncHttpStack extends BaseHttpStack {

    public interface OnRequestComplete {
        void onSuccess(HttpResponse httpResponse);

        void onAuthError(AuthFailureError authFailureError);

        void onError(IOException ioException);
    }

    public abstract void executeRequest(
            Request<?> request, Map<String, String> additionalHeaders, OnRequestComplete callback);

    @Override
    public final HttpResponse executeRequest(
            Request<?> request, Map<String, String> additionalHeaders)
            throws IOException, AuthFailureError {
        return null;
    }
}

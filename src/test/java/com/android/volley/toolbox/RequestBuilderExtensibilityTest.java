package com.android.volley.toolbox;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * 'Tests' to ensure that the {@link RequestBuilder} API is extendable. The problem with builders in
 * java is that one of the base builder methods will return the type of the base builder - even when
 * someone extends the builder class to make their own. It is not exactly trivial to workaround the
 * above problem, but it is possible. This test is to ensure that the extensibility is possible, by
 * making sure than a realistic example compiles.
 *
 * TODO delete this class???
 */
@Deprecated
@RunWith(RobolectricTestRunner.class)
public class RequestBuilderExtensibilityTest {

    /**
     * A somewhat realistic of example of an extended {@link RequestBuilder} for an imaginary
     * company called ABCD. You can even make these methods non-static non  static
     *
     * This is copied into the JavaDoc for {@link RequestBuilder} for documentation purposes.
     */
    private static class ABCDRequestBuilder {

        private ABCDRequestBuilder() {}

        /** Creates builder with headers required to send to the ABCD API server. */
        public static RequestBuilder<String> startNew() {
            return ABCDRequestBuilder.baseStartNew()
                    .header("Authentication", "key")
                    .url("http://my.base.url.for.requests.to.abcd.server/"); // we can then call
            // {@link
            // #appendUrl(String)}
        }

        /** Creates a normal builder, with extra loggers. */
        public static RequestBuilder<String> baseStartNew() {// Todo rename
            return RequestBuilder.startNew()
                    .onSuccess(
                            new Response.Listener<String>() { // todo fix logger
                                @Override
                                public void onResponse(String response) {
                                    // Some logging here
                                }
                            })
                    .onError(
                            new Response.ErrorListener() {
                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    // Some logging here
                                }
                            });
        }
    }
}

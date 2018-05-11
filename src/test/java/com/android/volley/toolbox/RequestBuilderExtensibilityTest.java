package com.android.volley.toolbox;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * 'Tests' to ensure that the {@link RequestBuilder} API is extendable. The problem with builders in
 * java is that one of the base builder methods will return the type of the base builder - even when
 * someone extends the builder class to make their own. It is not exactly trivial to workaround the
 * above problem, but it is possible. This test is to ensure that the extensibility is possible, by
 * making sure than a realistic example compiles.
 */
@RunWith(RobolectricTestRunner.class)
public class RequestBuilderExtensibilityTest {

    @Test
    public void callingSubclassMethodsBeforeAndAfterCallingBaseMethodsCompiles() {
        ABCDRequestBuilder.baseStartNew()
                .url("http://example.com") // base
                .addABCDAuthHeaders() // subclass
                .param("key", "value") // base
                .onError(
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {}
                        })
                .build();
    }

    /**
     * A somewhat realistic of example of an extended {@link RequestBuilder} for an imaginary
     * company called ABCD. This is copied into the JavaDoc for {@link RequestBuilder} for
     * documentation purposes.
     */
    private static class ABCDRequestBuilder<
                    ResponseT, ThisT extends ABCDRequestBuilder<ResponseT, ThisT>>
            extends RequestBuilder<ResponseT, ThisT> {

        protected ABCDRequestBuilder() {}

        /** Creates builder with headers required to send to the ABCD API server. */
        public static <T> ABCDRequestBuilder<T, ? extends ABCDRequestBuilder> startNew() {
            return ABCDRequestBuilder.<T>baseStartNew()
                    .addABCDAuthHeaders()
                    .url("http://my.base.url.for.requests.to.abcd.server/"); // we can then call
            // {@link
            // #appendUrl(String)}
        }

        /** Creates a normal builder, with extra loggers. */
        public static <T> ABCDRequestBuilder<T, ? extends ABCDRequestBuilder> baseStartNew() {
            return ABCDRequestBuilder.<T>baseStartNewNoLogging().addABCDLoggers();
        }

        /** Creates a normal builder; */
        public static <T>
                ABCDRequestBuilder<T, ? extends ABCDRequestBuilder> baseStartNewNoLogging() {
            return new ABCDRequestBuilder<>();
        }

        public ThisT addABCDAuthHeaders() {
            header("Authentication", "key");
            return endSetter();
        }

        public ThisT addABCDLoggers() {
            onSuccess(
                    new Response.Listener<ResponseT>() {
                        @Override
                        public void onResponse(ResponseT response) {
                            // Some logging here
                        }
                    });
            onError(
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // Some logging here
                        }
                    });
            return endSetter();
        }
    }
}

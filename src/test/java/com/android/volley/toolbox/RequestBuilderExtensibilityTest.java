package com.android.volley.toolbox;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.junit.Test;

/**
 * 'Tests' to ensure that the {@link RequestBuilder} API is extendable. The problem with builders in
 * java is that one of the base builder methods will return the type of the base builder - even when
 * someone extends the builder class to make their own. It is not exactly trivial to workaround the
 * above problem, but it is possible. This test is to ensure that the extensibility is possible, by
 * making sure than a realistic example compiles.
 */
public class RequestBuilderExtensibilityTest {

    @Test
    public void callingSubclassMethodsBeforeAndAfterCallingBaseMethodsCompiles() {
        ABCDRequestBuilder.createBase()
                .url("http://example.com") // base
                .addABCDAuthHeaders()
                .param("k", "v")
                .onError(new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                    }
                })
                .build();
    }

    /**
     * A somewhat realistic of example of an extended {@link RequestBuilder} for an imaginary
     * company called ABCD.
     */
    private static class ABCDRequestBuilder
            <ResponseT, ThisT extends ABCDRequestBuilder<ResponseT, ThisT>>
            extends RequestBuilder<ResponseT, ThisT> {

        protected ABCDRequestBuilder() {
        }

        /**
         * Creates builder with headers required to send to the ABCD API server.
         */
        public static <T> ABCDRequestBuilder<T, ? extends ABCDRequestBuilder> create() {
            return ABCDRequestBuilder.<T>createBase()
                    .addABCDAuthHeaders();
        }

        /**
         * Creates a normal builder, with extra loggers.
         */
        public static <T> ABCDRequestBuilder<T, ? extends ABCDRequestBuilder> createBase() {
            return ABCDRequestBuilder.<T>createBaseNoLogging().addABCDLoggers();
        }

        /**
         * Creates a normal builder;
         */
        public static <T> ABCDRequestBuilder<T, ? extends ABCDRequestBuilder> createBaseNoLogging() {
            return new ABCDRequestBuilder<>();
        }

        public ThisT addABCDAuthHeaders() {
            header("Authentication", "key");
            return getThis();
        }

        public ThisT addABCDLoggers() {
            onSuccess(new Response.Listener<ResponseT>() {
                @Override
                public void onResponse(ResponseT response) {
                    // Some logging here
                }
            });
            onError(new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    // Some logging here
                }
            });
            return getThis();
        }
    }
}

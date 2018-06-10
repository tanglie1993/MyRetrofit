package test;

import main.retrofit.*;
import main.retrofit.okhttp.*;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import org.junit.Test;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static okhttp3.mockwebserver.SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public final class CallTest {
//    @Rule
    public final MockWebServer server = new MockWebServer();

    interface Service {
        @GET("/")
        Call<String> getString();

        @GET("/")
        Call<ResponseBody> getBody();

        @GET("/")
        @Streaming
        Call<ResponseBody> getStreamingBody();

        @POST("/")
        Call<String> postString(@Body String body);

        @POST("/{a}")
        Call<String> postRequestBody(@Path("a") Object a);
    }

    @Test
    public void http200Sync() throws IOException {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(new ToStringConverterFactory())
                .build();
        Service example = retrofit.create(Service.class);

        server.enqueue(new MockResponse().setBody("Hi"));

        Response<String> response = example.getString().execute();
        org.assertj.core.api.Assertions.assertThat(response.isSuccessful()).isTrue();
        org.assertj.core.api.Assertions.assertThat(response.body()).isEqualTo("Hi");
    }

    @Test
    public void http200Async() throws InterruptedException {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(new ToStringConverterFactory())
                .build();
        Service example = retrofit.create(Service.class);

        server.enqueue(new MockResponse().setBody("Hi"));

        final AtomicReference<Response<String>> responseRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        Call<String> call = example.getString();
        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                t.printStackTrace();
            }
        });
        assertTrue(latch.await(10, SECONDS));

        Response<String> response = responseRef.get();
        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.body()).isEqualTo("Hi");
    }


    @Test
    public void http404Sync() throws IOException {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(new ToStringConverterFactory())
                .build();
        Service example = retrofit.create(Service.class);

        server.enqueue(new MockResponse().setResponseCode(404).setBody("Hi"));

        Response<String> response = example.getString().execute();
        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.code()).isEqualTo(404);
        assertThat(response.errorBody().string()).isEqualTo("Hi");
    }

    @Test
    public void http404Async() throws InterruptedException, IOException {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(new ToStringConverterFactory())
                .build();
        Service example = retrofit.create(Service.class);

        server.enqueue(new MockResponse().setResponseCode(404).setBody("Hi"));

        final AtomicReference<Response<String>> responseRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        example.getString().enqueue(new Callback<String>() {
            @Override public void onResponse(Call<String> call, Response<String> response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override public void onFailure(Call<String> call, Throwable t) {
                t.printStackTrace();
            }
        });
        assertTrue(latch.await(10, SECONDS));

        Response<String> response = responseRef.get();
        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.code()).isEqualTo(404);
        assertThat(response.errorBody().string()).isEqualTo("Hi");
    }

    @Test
    public void transportProblemSync() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(new ToStringConverterFactory())
                .build();
        Service example = retrofit.create(Service.class);

        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        Call<String> call = example.getString();
        try {
            call.execute();
            fail();
        } catch (IOException ignored) {
        }
    }

    @Test
    public void transportProblemAsync() throws InterruptedException {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(new ToStringConverterFactory())
                .build();
        Service example = retrofit.create(Service.class);

        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        final AtomicReference<Throwable> failureRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        example.getString().enqueue(new Callback<String>() {
            @Override public void onResponse(Call<String> call, Response<String> response) {
                throw new AssertionError();
            }

            @Override public void onFailure(Call<String> call, Throwable t) {
                failureRef.set(t);
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, SECONDS));

        Throwable failure = failureRef.get();
        assertThat(failure).isInstanceOf(IOException.class);
    }

    @Test
    public void conversionProblemOutgoingSync() throws IOException {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(new ToStringConverterFactory() {
                    @Override
                    public Converter<?, RequestBody> requestBodyConverter(Type type,
                                                                          Annotation[] parameterAnnotations, Annotation[] methodAnnotations,
                                                                          Retrofit retrofit) {
                        return new Converter<String, RequestBody>() {
                            @Override public RequestBody convert(String value) throws IOException {
                                throw new UnsupportedOperationException("I am broken!");
                            }
                        };
                    }
                })
                .build();
        Service example = retrofit.create(Service.class);

        Call<String> call = example.postString("Hi");
        try {
            call.execute();
            fail();
        } catch (UnsupportedOperationException e) {
            assertThat(e).hasMessage("I am broken!");
        }
    }

    @Test
    public void conversionProblemOutgoingAsync() throws InterruptedException {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(new ToStringConverterFactory() {
                    @Override
                    public Converter<?, RequestBody> requestBodyConverter(Type type,
                                                                          Annotation[] parameterAnnotations, Annotation[] methodAnnotations,
                                                                          Retrofit retrofit) {
                        return new Converter<String, RequestBody>() {
                            @Override public RequestBody convert(String value) throws IOException {
                                throw new UnsupportedOperationException("I am broken!");
                            }
                        };
                    }
                })
                .build();
        Service example = retrofit.create(Service.class);

        final AtomicReference<Throwable> failureRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        example.postString("Hi").enqueue(new Callback<String>() {
            @Override public void onResponse(Call<String> call, Response<String> response) {
                throw new AssertionError();
            }

            @Override public void onFailure(Call<String> call, Throwable t) {
                failureRef.set(t);
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, SECONDS));

        assertThat(failureRef.get()).isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("I am broken!");
    }

    @Test
    public void conversionProblemIncomingSync() throws IOException {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(new ToStringConverterFactory() {
                    @Override
                    public Converter<ResponseBody, ?> responseBodyConverter(Type type,
                                                                            Annotation[] annotations, Retrofit retrofit) {
                        return new Converter<ResponseBody, String>() {
                            @Override public String convert(ResponseBody value) throws IOException {
                                throw new UnsupportedOperationException("I am broken!");
                            }
                        };
                    }
                })
                .build();
        Service example = retrofit.create(Service.class);

        server.enqueue(new MockResponse().setBody("Hi"));

        Call<String> call = example.postString("Hi");
        try {
            call.execute();
            fail();
        } catch (UnsupportedOperationException e) {
            assertThat(e).hasMessage("I am broken!");
        }
    }

    @Test
    public void conversionProblemIncomingMaskedByConverterIsUnwrapped() throws IOException {
        // MWS has no way to trigger IOExceptions during the response body so use an interceptor.
        OkHttpClient client = new OkHttpClient.Builder() //
                .addInterceptor(new Interceptor() {
                    @Override public okhttp3.Response intercept(Chain chain) throws IOException {
                        okhttp3.Response response = chain.proceed(chain.request());
                        ResponseBody body = response.body();
                        BufferedSource source = Okio.buffer(new ForwardingSource(body.source()) {
                            @Override
                            public long read(Buffer sink, long byteCount) throws IOException {
                                throw new IOException("cause");
                            }
                        });
                        body = ResponseBody.create(body.contentType(), body.contentLength(), source);
                        return response.newBuilder().body(body).build();
                    }
                }).build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(client)
                .addConverterFactory(new ToStringConverterFactory() {
                    @Override
                    public Converter<ResponseBody, ?> responseBodyConverter(Type type,
                                                                            Annotation[] annotations, Retrofit retrofit) {
                        return new Converter<ResponseBody, String>() {
                            @Override public String convert(ResponseBody value) throws IOException {
                                try {
                                    return value.string();
                                } catch (IOException e) {
                                    // Some serialization libraries mask transport problems in runtime exceptions. Bad!
                                    throw new RuntimeException("wrapper", e);
                                }
                            }
                        };
                    }
                })
                .build();
        Service example = retrofit.create(Service.class);

        server.enqueue(new MockResponse().setBody("Hi"));

        Call<String> call = example.getString();
        try {
            call.execute();
            fail();
        } catch (IOException e) {
            assertThat(e).hasMessage("cause");
        }
    }

    @Test
    public void conversionProblemIncomingAsync() throws InterruptedException {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(new ToStringConverterFactory() {
                    @Override
                    public Converter<ResponseBody, ?> responseBodyConverter(Type type,
                                                                            Annotation[] annotations, Retrofit retrofit) {
                        return new Converter<ResponseBody, String>() {
                            @Override public String convert(ResponseBody value) throws IOException {
                                throw new UnsupportedOperationException("I am broken!");
                            }
                        };
                    }
                })
                .build();
        Service example = retrofit.create(Service.class);

        server.enqueue(new MockResponse().setBody("Hi"));

        final AtomicReference<Throwable> failureRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        example.postString("Hi").enqueue(new Callback<String>() {
            @Override public void onResponse(Call<String> call, Response<String> response) {
                throw new AssertionError();
            }

            @Override public void onFailure(Call<String> call, Throwable t) {
                failureRef.set(t);
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, SECONDS));

        assertThat(failureRef.get()).isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("I am broken!");
    }

    @Test
    public void http204SkipsConverter() throws IOException {
        final Converter<ResponseBody, String> converter = spy(new Converter<ResponseBody, String>() {
            @Override public String convert(ResponseBody value) throws IOException {
                return value.string();
            }
        });
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(new ToStringConverterFactory() {
                    @Override
                    public Converter<ResponseBody, ?> responseBodyConverter(Type type,
                                                                            Annotation[] annotations, Retrofit retrofit) {
                        return converter;
                    }
                })
                .build();
        Service example = retrofit.create(Service.class);

        server.enqueue(new MockResponse().setStatus("HTTP/1.1 204 Nothin"));

        Response<String> response = example.getString().execute();
        assertThat(response.code()).isEqualTo(204);
        assertThat(response.body()).isNull();
        verifyNoMoreInteractions(converter);
    }

    @Test
    public void http205SkipsConverter() throws IOException {
        final Converter<ResponseBody, String> converter = spy(new Converter<ResponseBody, String>() {
            @Override public String convert(ResponseBody value) throws IOException {
                return value.string();
            }
        });
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(new ToStringConverterFactory() {
                    @Override
                    public Converter<ResponseBody, ?> responseBodyConverter(Type type,
                                                                            Annotation[] annotations, Retrofit retrofit) {
                        return converter;
                    }
                })
                .build();
        Service example = retrofit.create(Service.class);

        server.enqueue(new MockResponse().setStatus("HTTP/1.1 205 Nothin"));

        Response<String> response = example.getString().execute();
        assertThat(response.code()).isEqualTo(205);
        assertThat(response.body()).isNull();
        verifyNoMoreInteractions(converter);
    }

    @Test
    public void executeCallOnce() throws IOException {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(new ToStringConverterFactory())
                .build();
        Service example = retrofit.create(Service.class);
        server.enqueue(new MockResponse());
        Call<String> call = example.getString();
        call.execute();
        try {
            call.execute();
            fail();
        } catch (IllegalStateException e) {
            assertThat(e).hasMessage("Already executed.");
        }
    }

    @Test
    public void successfulRequestResponseWhenMimeTypeMissing() throws Exception {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(new ToStringConverterFactory())
                .build();
        Service example = retrofit.create(Service.class);

        server.enqueue(new MockResponse().setBody("Hi").removeHeader("Content-Type"));

        Response<String> response = example.getString().execute();
        assertThat(response.body()).isEqualTo("Hi");
    }

    @Test
    public void responseBody() throws IOException {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(new ToStringConverterFactory())
                .build();
        Service example = retrofit.create(Service.class);

        server.enqueue(new MockResponse().setBody("1234"));

        Response<ResponseBody> response = example.getBody().execute();
        String str = response.body().string();
        assertThat(str).isEqualTo("1234");
    }

    @Test
    public void responseBodyBuffers() throws IOException {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(new ToStringConverterFactory())
                .build();
        Service example = retrofit.create(Service.class);

        server.enqueue(new MockResponse()
                .setBody("1234")
                .setSocketPolicy(DISCONNECT_DURING_RESPONSE_BODY));

        Call<ResponseBody> buffered = example.getBody();
        // When buffering we will detect all socket problems before returning the Response.
        try {
            buffered.execute();
            fail();
        } catch (IOException e) {
            assertThat(e).hasMessage("unexpected end of stream");
        }
    }

    @Test
    public void responseBodyStreams() throws IOException {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(new ToStringConverterFactory())
                .build();
        Service example = retrofit.create(Service.class);

        server.enqueue(new MockResponse()
                .setBody("1234")
                .setSocketPolicy(DISCONNECT_DURING_RESPONSE_BODY));

        Response<ResponseBody> response = example.getStreamingBody().execute();

        ResponseBody streamedBody = response.body();
        // When streaming we only detect socket problems as the ResponseBody is read.
        try {
            streamedBody.string();
            fail();
        } catch (IOException e) {
            assertThat(e).hasMessage("unexpected end of stream");
        }
    }

    @Test
    public void rawResponseContentTypeAndLengthButNoSource() throws IOException {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(new ToStringConverterFactory())
                .build();
        Service example = retrofit.create(Service.class);

        server.enqueue(new MockResponse().setBody("Hi").addHeader("Content-Type", "text/greeting"));

        Response<String> response = example.getString().execute();
        assertThat(response.body()).isEqualTo("Hi");
        ResponseBody rawBody = response.raw().body();
        assertThat(rawBody.contentLength()).isEqualTo(2);
        assertThat(rawBody.contentType().toString()).isEqualTo("text/greeting");
        try {
            rawBody.source();
            fail();
        } catch (IllegalStateException e) {
            assertThat(e).hasMessage("Cannot read raw response body of a converted body.");
        }
    }
}

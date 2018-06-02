package test;

import main.retrofit.*;
import main.retrofit.okhttp.*;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


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
}

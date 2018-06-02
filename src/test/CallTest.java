package test;

import main.retrofit.Call;
import main.retrofit.Response;
import main.retrofit.Retrofit;
import main.retrofit.ToStringConverterFactory;
import main.retrofit.okhttp.*;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;

import java.io.IOException;


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
}

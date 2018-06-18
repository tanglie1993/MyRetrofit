package main.retrofit;


import okhttp3.Headers;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;

import static org.mockito.internal.util.Checks.checkNotNull;


/** An HTTP response. */
public class Response<T> {

    private final okhttp3.Response rawResponse;
    private final T body;
    private final ResponseBody errorBody;

    public Response(okhttp3.Response rawResponse, T body, ResponseBody errorBody) {
        this.rawResponse = rawResponse;
        this.body = body;
        this.errorBody = errorBody;
    }

    public boolean isSuccessful() {
        return rawResponse.isSuccessful();
    }

    public T body() {
        return body;
    }

    public int code() {
        return rawResponse.code();
    }

    public ResponseBody errorBody() {
        return errorBody;
    }

    public okhttp3.Response raw() {
        return rawResponse;
    }

    public static <T> Response<T> success(T body) {
        return success(body, new okhttp3.Response.Builder() //
                .code(200)
                .message("OK")
                .protocol(Protocol.HTTP_1_1)
                .request(new Request.Builder().url("http://localhost/").build())
                .build());
    }

    public static <T> Response<T> success(T body, okhttp3.Response rawResponse) {
        checkNotNull(rawResponse, "rawResponse == null");
        if (!rawResponse.isSuccessful()) {
            throw new IllegalArgumentException("rawResponse must be successful response");
        }
        return new Response<>(rawResponse, body, null);
    }

    public String message() {
        return rawResponse.message();
    }

    public Headers headers() {
        return rawResponse.headers();
    }
}

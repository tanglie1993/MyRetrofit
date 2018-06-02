package main.retrofit;


import com.sun.istack.internal.Nullable;
import okhttp3.ResponseBody;


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
}

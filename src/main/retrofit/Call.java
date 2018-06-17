package main.retrofit;

import main.retrofit.Response;
import okhttp3.Request;

import java.io.IOException;

/**
 * Created by pc on 2018/5/29.
 */
public interface Call<T> extends Cloneable {

    Response<T> execute() throws IOException;

    void enqueue(Callback<T> callback);

    boolean isExecuted();

    void cancel();

    boolean isCanceled();

    Call<T> clone();

    Request request();
}

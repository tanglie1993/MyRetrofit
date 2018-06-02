package main.retrofit;

import main.retrofit.okhttp.GET;
import main.retrofit.okhttp.Streaming;
import okhttp3.*;
import okio.BufferedSource;
import test.CallTest;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;

/**
 * Created by pc on 2018/5/29.
 */
public class Retrofit {

    OkHttpClient client = new OkHttpClient();

    private HttpUrl baseUrl;
    private Converter.Factory factory;

    public Retrofit(HttpUrl baseUrl, Converter.Factory factory) {
        this.baseUrl = baseUrl;
        this.factory = factory;
    }

    public <T> T create(final Class<T> service) {
        return (T) Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[]{service},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if(method.getDeclaredAnnotations().length > 0){
                            if(method.getDeclaredAnnotations()[0] instanceof GET){
                                GET get = (GET) method.getDeclaredAnnotations()[0];
                                String path = get.value();
                                if(method.getGenericReturnType() != null){
                                    Type genericReturnType = method.getGenericReturnType();
                                }
                                Request request = new Request.Builder()
                                        .url(baseUrl + path)
                                        .build();
                                return new Call<String>() {
                                    Call<String> thisCall = this;
                                    @Override
                                    public Response<String> execute() throws IOException {
                                        okhttp3.Response rawResponse = client.newCall(request).execute();
                                        if(rawResponse.isSuccessful()){
                                            return new Response<String>(rawResponse, rawResponse.body().string(), null);
                                        }else{
                                            return new Response<String>(rawResponse, null, rawResponse.body());
                                        }
                                    }

                                    @Override
                                    public void enqueue(Callback<String> callback) {
                                        okhttp3.Call okhttpCall = client.newCall(request);
                                        okhttpCall.enqueue(new okhttp3.Callback() {
                                            @Override
                                            public void onFailure(okhttp3.Call call, IOException e) {
                                                callback.onFailure(thisCall, e);
                                            }

                                            @Override
                                            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                                                callback.onResponse(thisCall, new Response<String>(response, response.body().string(), new ResponseBody() {
                                                    @Override
                                                    public MediaType contentType() {
                                                        return null;
                                                    }

                                                    @Override
                                                    public long contentLength() {
                                                        return 0;
                                                    }

                                                    @Override
                                                    public BufferedSource source() {
                                                        return null;
                                                    }
                                                }));
                                            }
                                        });
                                    }
                                };
                            }
                        }
                        return new Object();
                    }
                });
    }

    public static class Builder {

        private HttpUrl baseUrl;
        private Converter.Factory factory;


        public Builder baseUrl(HttpUrl url) {
            this.baseUrl = url;
            return this;
        }

        public Builder addConverterFactory(Converter.Factory factory){
            this.factory = factory;
            return this;
        }

        public Retrofit build() {
            return new Retrofit(baseUrl, factory);
        }
    }
}

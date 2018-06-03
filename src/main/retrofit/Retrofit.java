package main.retrofit;

import main.retrofit.okhttp.Body;
import main.retrofit.okhttp.GET;
import main.retrofit.okhttp.POST;
import main.retrofit.okhttp.Streaming;
import okhttp3.*;
import okio.BufferedSink;
import okio.BufferedSource;
import test.CallTest;

import java.io.IOException;
import java.lang.annotation.Annotation;
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

    public Retrofit(HttpUrl baseUrl, Converter.Factory factory, OkHttpClient client) {
        this.baseUrl = baseUrl;
        this.factory = factory;
        this.client = client;
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
                                Request request = new Request.Builder()
                                        .url(baseUrl + path)
                                        .build();
                                return createCall(request, "GET", null, null, null);
                            }else if(method.getDeclaredAnnotations()[0] instanceof POST){
                                POST post = (POST) method.getDeclaredAnnotations()[0];
                                String path = post.value();
                                int paramIndex = 0;
                                String body = "";
                                outer:  for(Annotation[] parameterAnnotation : method.getParameterAnnotations()){
                                    if(parameterAnnotation.length > 0){
                                        for(Annotation annotation : parameterAnnotation){
                                            if(annotation instanceof Body){
                                                body = (String) args[paramIndex];
                                                break outer;
                                            }
                                        }
                                    }
                                    paramIndex++;
                                }
                                Converter<String, RequestBody> requestBodyConverter = (Converter<String, RequestBody>) factory.requestBodyConverter(String.class,
                                        method.getParameterAnnotations()[paramIndex],
                                        method.getDeclaredAnnotations(), Retrofit.this);
                                Converter<ResponseBody, String> responseBodyConverter = (Converter<ResponseBody, String>) factory.responseBodyConverter(String.class,
                                        method.getDeclaredAnnotations(), Retrofit.this);
                                Request request = new Request.Builder()
                                        .url(baseUrl + path)
                                        .build();
                                return createCall(request, "POST",  body, requestBodyConverter, responseBodyConverter);
                            }
                        }
                        return new Object();
                    }

                    private Object createCall(final Request request, final String method,
                                              final String body, final Converter<String, RequestBody> requestBodyConverter,
                                              final Converter<ResponseBody, String> responseBodyConverter) {
                        return new Call<String>() {

                            Call<String> thisCall = this;
                            Converter.Factory factory = Retrofit.this.factory;

                            @Override
                            public Response<String> execute() throws IOException {
                                if(method!= null && method.equals("POST")){
                                    request.newBuilder().method("POST", requestBodyConverter.convert(body));
                                }
                                okhttp3.Call rawCall = client.newCall(request);
                                okhttp3.Response rawResponse = rawCall.execute();
                                if(rawResponse.isSuccessful()){
                                    if(responseBodyConverter == null){
                                        if(rawResponse.body().contentLength() == 0){
                                            return new Response<>(rawResponse, null, null);
                                        }
                                        return new Response<>(rawResponse, rawResponse.body().string(), null);
                                    }
                                    return new Response<>(rawResponse, responseBodyConverter.convert(rawResponse.body()), null);
                                }else{
                                    return new Response<>(rawResponse, null, rawResponse.body());
                                }
                            }

                            @Override
                            public void enqueue(Callback<String> callback) {
                                if(method!= null && method.equals("POST")){
                                    try {
                                        request.newBuilder().method("POST", requestBodyConverter.convert(body));
                                    } catch (Exception e) {
                                        callback.onFailure(thisCall, e);
                                    }
                                }
                                okhttp3.Call okhttpCall = client.newCall(request);
                                okhttpCall.enqueue(new okhttp3.Callback() {
                                    @Override
                                    public void onFailure(okhttp3.Call call, IOException e) {
                                        callback.onFailure(thisCall, e);
                                    }

                                    @Override
                                    public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                                        try{
                                            if(response.isSuccessful()){
                                                if(responseBodyConverter == null){
                                                    callback.onResponse(thisCall, new Response<>(response, response.body().string(),null));
                                                }
                                                callback.onResponse(thisCall, new Response<>(response, responseBodyConverter.convert(response.body()),null));
                                            }else{
                                                callback.onResponse(thisCall, new Response<>(response, null, response.body()));
                                            }
                                        } catch (Exception e){
                                            callback.onFailure(thisCall, e);
                                        }
                                    }
                                });
                            }
                        };
                    }
                });
    }

    public static class Builder {

        private HttpUrl baseUrl;
        private Converter.Factory factory;
        private OkHttpClient client = new OkHttpClient();


        public Builder baseUrl(HttpUrl url) {
            this.baseUrl = url;
            return this;
        }

        public Builder addConverterFactory(Converter.Factory factory){
            this.factory = factory;
            return this;
        }

        public Retrofit build() {
            return new Retrofit(baseUrl, factory, client);
        }

        public Builder client(OkHttpClient client) {
            this.client = client;
            return this;
        }
    }
}

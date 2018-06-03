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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created by pc on 2018/5/29.
 */
public class Retrofit {

    OkHttpClient client = new OkHttpClient();

    private HttpUrl baseUrl;
    private List<Converter.Factory> factoryList;
    private volatile boolean isExecuted = false;

    public Retrofit(HttpUrl baseUrl, List<Converter.Factory> factoryList, OkHttpClient client) {
        this.baseUrl = baseUrl;
        this.factoryList = factoryList;
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
                                Converter<?, RequestBody> requestBodyConverter = searchForRequestConverter(method.getGenericReturnType(),
                                        null, method.getDeclaredAnnotations(), Retrofit.this);
                                Converter<ResponseBody, ?> responseBodyConverter = searchForResponseConverter(method.getGenericReturnType(),
                                        method.getDeclaredAnnotations(), Retrofit.this);
                                Request request = new Request.Builder()
                                        .url(baseUrl + path)
                                        .build();
                                return createCall(request, "GET", null, requestBodyConverter, responseBodyConverter);
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
                                Converter<?, RequestBody> requestBodyConverter = searchForRequestConverter(method.getGenericReturnType(),
                                        method.getParameterAnnotations()[paramIndex],
                                        method.getDeclaredAnnotations(), Retrofit.this);
                                Converter<ResponseBody, ?> responseBodyConverter = searchForResponseConverter(method.getGenericReturnType(),
                                        method.getDeclaredAnnotations(), Retrofit.this);
                                Request request = new Request.Builder()
                                        .url(baseUrl + path)
                                        .build();
                                return createCall(request, "POST",  body, requestBodyConverter, responseBodyConverter);
                            }
                        }
                        return new Object();
                    }

                    private Call createCall(final Request request, final String method,
                                              final String requestBody, final Converter<?, RequestBody> requestBodyConverter,
                                              final Converter<ResponseBody, ?> responseBodyConverter) {
                        return new Call<Object>() {

                            Call<Object> thisCall = this;

                            @Override
                            public Response<Object> execute() throws IOException {
                                if(isExecuted){
                                    throw new IllegalStateException("Already executed.");
                                }
                                isExecuted = true;
                                if(method!= null && method.equals("POST")){
                                    request.newBuilder().method("POST", ((Converter<String, RequestBody>) requestBodyConverter).convert(requestBody));
                                }
                                okhttp3.Call rawCall = client.newCall(request);
                                okhttp3.Response rawResponse = rawCall.execute();
                                if(rawResponse.isSuccessful()){
                                    return new Response<>(rawResponse, responseBodyConverter.convert(rawResponse.body()), null);
                                }else{
                                    return new Response<>(rawResponse, null, rawResponse.body());
                                }
                            }

                            @Override
                            public void enqueue(Callback<Object> callback) {
                                if(method!= null && method.equals("POST")){
                                    try {
                                        request.newBuilder().method("POST", ((Converter<String, RequestBody>) requestBodyConverter).convert(requestBody));
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

    private Converter<ResponseBody, ?> searchForResponseConverter(Type genericReturnType, Annotation[] declaredAnnotations, Retrofit retrofit) {
        for(Converter.Factory factory : factoryList){
            if(factory.responseBodyConverter(genericReturnType, declaredAnnotations, retrofit) != null){
                return factory.responseBodyConverter(genericReturnType, declaredAnnotations, retrofit);
            }
        }
        return BuiltInConverter.INSTANCE.responseBodyConverter(genericReturnType, declaredAnnotations, retrofit);
    }

    private Converter<?, RequestBody> searchForRequestConverter(Type genericReturnType, Annotation[] parameterAnnotations,
                                                                Annotation[] declaredAnnotations, Retrofit retrofit) {

        for(Converter.Factory factory : factoryList){
            if(factory.requestBodyConverter(genericReturnType, parameterAnnotations, declaredAnnotations, retrofit) != null){
                return factory.requestBodyConverter(genericReturnType, parameterAnnotations, declaredAnnotations, retrofit);
            }
        }
        return BuiltInConverter.INSTANCE.requestBodyConverter(genericReturnType, parameterAnnotations, declaredAnnotations, retrofit);
    }

    public static class Builder {

        private HttpUrl baseUrl;
        private List<Converter.Factory> factoryList = new ArrayList<>();
        private OkHttpClient client = new OkHttpClient();

        public Builder() {
            factoryList.add(BuiltInConverter.INSTANCE);
        }

        public Builder baseUrl(HttpUrl url) {
            this.baseUrl = url;
            return this;
        }

        public Builder addConverterFactory(Converter.Factory factory){
            this.factoryList.add(factory);
            return this;
        }

        public Retrofit build() {
            return new Retrofit(baseUrl, factoryList, client);
        }

        public Builder client(OkHttpClient client) {
            this.client = client;
            return this;
        }
    }
}

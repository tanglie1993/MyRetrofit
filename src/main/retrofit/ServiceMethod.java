package main.retrofit;

import main.retrofit.okhttp.Body;
import main.retrofit.okhttp.GET;
import main.retrofit.okhttp.POST;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Created by pc on 2018/6/9.
 */
public class ServiceMethod {

    private volatile boolean isExecuted = false;
    Request request;
    String method;
    String requestBody;
    Converter<?, RequestBody> requestBodyConverter;
    Converter<ResponseBody, ?> responseBodyConverter;
    OkHttpClient client;

    public static Object generateServiceMethod(Retrofit retrofit, Method method, Object[] args) {
        if(method.getDeclaredAnnotations().length > 0){
            if(method.getDeclaredAnnotations()[0] instanceof GET){
                return generateGet(retrofit, method);
            }else if(method.getDeclaredAnnotations()[0] instanceof POST){
                return generatePost(retrofit, method, args);
            }
        }
        return new Object();
    }

    private static Object generateGet(Retrofit retrofit, Method method) {
        ServiceMethod serviceMethod = new ServiceMethod();
        serviceMethod.client = retrofit.client;
        GET get = (GET) method.getDeclaredAnnotations()[0];
        String path = get.value();
        serviceMethod.requestBodyConverter = retrofit.searchForRequestConverter(method.getGenericReturnType(),
                null, method.getDeclaredAnnotations());
        serviceMethod.responseBodyConverter = retrofit.searchForResponseConverter(method.getGenericReturnType(),
                method.getDeclaredAnnotations());
        serviceMethod.request = new Request.Builder()
                .url(retrofit.baseUrl + path)
                .build();
        serviceMethod.method ="GET";
        return serviceMethod.createCall();
    }

    private static Object generatePost(Retrofit retrofit, Method method, Object[] args) {
        ServiceMethod serviceMethod = new ServiceMethod();
        serviceMethod.client = retrofit.client;
        POST post = (POST) method.getDeclaredAnnotations()[0];
        String path = post.value();
        int paramIndex = 0;
        serviceMethod.requestBody = "";
        outer:  for(Annotation[] parameterAnnotation : method.getParameterAnnotations()){
            if(parameterAnnotation.length > 0){
                for(Annotation annotation : parameterAnnotation){
                    if(annotation instanceof Body){
                        serviceMethod.requestBody = (String) args[paramIndex];
                        break outer;
                    }
                }
            }
            paramIndex++;
        }
        serviceMethod.requestBodyConverter = retrofit.searchForRequestConverter(method.getGenericReturnType(),
                method.getParameterAnnotations()[paramIndex],
                method.getDeclaredAnnotations());
        serviceMethod.responseBodyConverter = retrofit.searchForResponseConverter(method.getGenericReturnType(),
                method.getDeclaredAnnotations());
        serviceMethod.request = new Request.Builder()
                .url(retrofit.baseUrl + path)
                .build();
        serviceMethod.method = "POST";
        return serviceMethod.createCall();
    }

    private Call createCall() {
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
                    if(rawResponse.body().contentLength() == 0){
                        return new Response<>(rawResponse, null, null);
                    }
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
}

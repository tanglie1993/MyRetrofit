package main.retrofit;

import okhttp3.RequestBody;

import java.io.IOException;

/**
 * Created by pc on 2018/6/10.
 */
public class OkHttpCall implements Call {

    private volatile boolean isExecuted = false;
    private ServiceMethod serviceMethod;

    public OkHttpCall(ServiceMethod serviceMethod) {
        this.serviceMethod =  serviceMethod;
    }

    @Override
    public Response<Object> execute() throws IOException {
        if(isExecuted){
            throw new IllegalStateException("Already executed.");
        }
        isExecuted = true;
        if(serviceMethod.method!= null && serviceMethod.method.equals("POST")){
            serviceMethod.request.newBuilder().method("POST", ((Converter<String, RequestBody>) serviceMethod.requestBodyConverter)
                    .convert(serviceMethod.requestBody));
        }
        okhttp3.Call rawCall = serviceMethod.client.newCall(serviceMethod.request);
        okhttp3.Response rawResponse = rawCall.execute();
        if(rawResponse.isSuccessful()){
            if(rawResponse.body().contentLength() == 0){
                return new Response<>(rawResponse, null, null);
            }
            return new Response<>(rawResponse, serviceMethod.responseBodyConverter.convert(rawResponse.body()), null);
        }else{
            return new Response<>(rawResponse, null, rawResponse.body());
        }
    }

    @Override
    public void enqueue(Callback callback) {
        if(serviceMethod.method!= null && serviceMethod.method.equals("POST")){
            try {
                serviceMethod.request.newBuilder().method("POST",
                        ((Converter<String, RequestBody>) serviceMethod.requestBodyConverter).convert(serviceMethod.requestBody));
            } catch (Exception e) {
                callback.onFailure(OkHttpCall.this, e);
            }
        }
        okhttp3.Call okhttpCall = serviceMethod.client.newCall(serviceMethod.request);
        okhttpCall.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                callback.onFailure(OkHttpCall.this, e);
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                try{
                    if(response.isSuccessful()){
                        callback.onResponse(OkHttpCall.this, new Response<>(response, serviceMethod.responseBodyConverter.convert(response.body()),null));
                    }else{
                        callback.onResponse(OkHttpCall.this, new Response<>(response, null, response.body()));
                    }
                } catch (Exception e){
                    callback.onFailure(OkHttpCall.this, e);
                }
            }
        });
    }
}

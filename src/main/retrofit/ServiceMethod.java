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

    Request request;
    String method;
    String requestBody;
    Converter<?, RequestBody> requestBodyConverter;
    Converter<ResponseBody, ?> responseBodyConverter;
    OkHttpClient client;

    public static Call generateOkHttpCall(Retrofit retrofit, Method method, Object[] args) {
        if(method.getDeclaredAnnotations().length > 0){
            if(method.getDeclaredAnnotations()[0] instanceof GET){
                return generateGet(retrofit, method);
            }else if(method.getDeclaredAnnotations()[0] instanceof POST){
                return generatePost(retrofit, method, args);
            }
        }
        return null;
    }

    private static Call generateGet(Retrofit retrofit, Method method) {
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
        return new OkHttpCall(serviceMethod);
    }

    private static Call generatePost(Retrofit retrofit, Method method, Object[] args) {
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
        return new OkHttpCall(serviceMethod);
    }

}

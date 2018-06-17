package main.retrofit;

import main.retrofit.okhttp.Body;
import main.retrofit.okhttp.GET;
import main.retrofit.okhttp.POST;
import main.retrofit.okhttp.Path;
import okhttp3.*;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by pc on 2018/6/9.
 */
public class ServiceMethod {

    String method;
    String requestBody;
    String relativeUrl;
    String baseUrl;
    Converter<String, RequestBody> requestBodyConverter;
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
        serviceMethod.baseUrl = retrofit.baseUrl.toString();
        GET get = (GET) method.getDeclaredAnnotations()[0];
        serviceMethod.relativeUrl = get.value();
        serviceMethod.requestBodyConverter = (Converter<String, RequestBody>) retrofit.searchForRequestConverter(method.getGenericReturnType(),
                null, method.getDeclaredAnnotations());
        serviceMethod.responseBodyConverter = retrofit.searchForResponseConverter(method.getGenericReturnType(),
                method.getDeclaredAnnotations());
        serviceMethod.method ="GET";
        return new OkHttpCall(serviceMethod);
    }

    private static Call generatePost(Retrofit retrofit, Method method, Object[] args) {
        ServiceMethod serviceMethod = new ServiceMethod();
        serviceMethod.client = retrofit.client;
        serviceMethod.baseUrl = retrofit.baseUrl.toString();
        POST post = (POST) method.getDeclaredAnnotations()[0];
        serviceMethod.relativeUrl = post.value();
        int paramIndex = 0;
        serviceMethod.requestBody = "";
        outer:  for(Annotation[] parameterAnnotation : method.getParameterAnnotations()){
            if(parameterAnnotation.length > 0){
                for(Annotation annotation : parameterAnnotation){
                    if(annotation instanceof Body){
                        serviceMethod.requestBody = (String) args[paramIndex];
                        break outer;
                    }
                    if(annotation instanceof Path){
                        String pattern = "\\{" + ((Path) annotation).value() + "\\}";
                        Pattern r = Pattern.compile(pattern);
                        Matcher m = r.matcher(serviceMethod.relativeUrl);
                        serviceMethod.relativeUrl = m.replaceAll(args[paramIndex].toString());

                        break outer;
                    }
                }
            }
            paramIndex++;
        }
        serviceMethod.requestBodyConverter = (Converter<String, RequestBody>) retrofit.searchForRequestConverter(method.getGenericReturnType(),
                method.getParameterAnnotations()[paramIndex],
                method.getDeclaredAnnotations());
        serviceMethod.responseBodyConverter = retrofit.searchForResponseConverter(method.getGenericReturnType(),
                method.getDeclaredAnnotations());
        serviceMethod.method = "POST";
        return new OkHttpCall(serviceMethod);
    }

    okhttp3.Call toCall() throws IOException {
        RequestBuilder requestBuilder = new RequestBuilder(method, baseUrl, relativeUrl);
        if(requestBody != null){
            requestBuilder.setBody(requestBodyConverter.convert(requestBody));
        }
        return client.newCall(requestBuilder.build());
    }

}

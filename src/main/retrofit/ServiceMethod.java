package main.retrofit;

import com.google.caliper.Param;
import com.google.inject.internal.MoreTypes;
import main.retrofit.okhttp.Body;
import main.retrofit.okhttp.GET;
import main.retrofit.okhttp.POST;
import main.retrofit.okhttp.Path;
import okhttp3.*;
import okio.BufferedSink;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by pc on 2018/6/9.
 */
public class ServiceMethod<R, T> {

    private static Map<Method, ServiceMethod> cache = new HashMap<>();

    String method;
    String baseUrl;
    Converter<String, RequestBody> requestBodyConverter;
    Converter<ResponseBody, ?> responseBodyConverter;
    Annotation[][] parameterAnnotations;
    Annotation[] declaredAnnotations;
    CallAdapter<R, T> callAdapter;
    OkHttpClient client;
    String relativeUrl;

    public static OkHttpCall generateOkHttpCall(Retrofit retrofit, Method method, Object[] args) {
        Type returnType = method.getGenericReturnType();
        if(returnType instanceof Class && ((Class) returnType).getName().equals("void")){
            throw new IllegalArgumentException("Service methods cannot return void.\n    for method "
                    + method.getDeclaringClass().getSimpleName() + "."+ method.getName());
        }
        Type responseType;
        if(returnType instanceof ParameterizedTypeImpl){
            Type[] actualTypeArguments = ((ParameterizedTypeImpl) returnType).getActualTypeArguments();
            responseType = actualTypeArguments[0];
        }else{
            responseType = returnType;
        }
        if (responseType == Response.class || responseType == okhttp3.Response.class) {
            throw new IllegalArgumentException("'"
                    + responseType.getTypeName()
                    + "' is not a valid response body type. Did you mean ResponseBody?"
                    + "\n    for method "
                    + method.getDeclaringClass().getSimpleName()
                    + "."
                    + method.getName());
        }
        if(cache.containsKey(method)){
            return new OkHttpCall(cache.get(method), args);
        }
        ServiceMethod serviceMethod = new ServiceMethod();
        serviceMethod.parameterAnnotations = method.getParameterAnnotations();
        serviceMethod.declaredAnnotations = method.getDeclaredAnnotations();
        serviceMethod.baseUrl = retrofit.baseUrl.toString();
        serviceMethod.callAdapter = retrofit.getCallAdapter(method.getReturnType(), method.getDeclaredAnnotations(), retrofit);
        if(method.getParameterAnnotations().length == 0){
            serviceMethod.requestBodyConverter = new ToStringConverterFactory().requestBodyConverter(method.getReturnType(),
                    null, method.getDeclaredAnnotations(), retrofit);
        }else{
            serviceMethod.requestBodyConverter = retrofit.searchForRequestConverter(method.getGenericReturnType(),
                    method.getParameterAnnotations()[0], method.getDeclaredAnnotations());
        }
        serviceMethod.responseBodyConverter = retrofit.searchForResponseConverter(method.getGenericReturnType(),
        method.getDeclaredAnnotations());

        if(method.getDeclaredAnnotations()[0] instanceof GET){
            serviceMethod.method ="GET";
            serviceMethod.relativeUrl = ((GET) method.getDeclaredAnnotations()[0]).value();
        }else if(method.getDeclaredAnnotations()[0] instanceof POST){
            serviceMethod.method ="POST";
            serviceMethod.relativeUrl = ((POST) method.getDeclaredAnnotations()[0]).value();
        }
        serviceMethod.client = retrofit.client;
        OkHttpCall result = new OkHttpCall(serviceMethod, args);
//        cache.put(method, serviceMethod);
        return result;
    }

    okhttp3.Call toCall(Object[] args) throws IOException {
        RequestBuilder requestBuilder = new RequestBuilder(method, baseUrl, relativeUrl);
        String requestBody = null;
        int paramIndex = 0;
        outer:  for(Annotation[] parameterAnnotation : parameterAnnotations){
            if(parameterAnnotation.length > 0){
                for(Annotation annotation : parameterAnnotation){
                    if(annotation instanceof Body){
                        requestBody = (String) args[paramIndex];
                    }
                    if(annotation instanceof Path){
                        String pattern = "\\{" + ((Path) annotation).value() + "\\}";
                        Pattern r = Pattern.compile(pattern);
                        Matcher m = r.matcher(relativeUrl);
                        relativeUrl = m.replaceAll(args[paramIndex].toString());
                    }
                }
            }
            paramIndex++;
        }
        if(requestBody != null){
            requestBuilder.setBody(requestBodyConverter.convert(requestBody));
        }else if(method.equals("POST")){
            requestBuilder.setBody(new RequestBody() {
                @Override
                public MediaType contentType() {
                    return null;
                }

                @Override
                public void writeTo(BufferedSink bufferedSink) throws IOException {

                }
            });
        }
        return client.newCall(requestBuilder.build());
    }

    T adapt(Call<R> call) {
        if(callAdapter == null){
            throw new IllegalArgumentException("Unable to create call adapter");
        }
        return callAdapter.adapt(call);
    }
}

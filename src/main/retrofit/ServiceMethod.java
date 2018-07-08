package main.retrofit;

import com.google.inject.internal.MoreTypes;
import main.retrofit.okhttp.Body;
import main.retrofit.okhttp.GET;
import main.retrofit.okhttp.POST;
import main.retrofit.okhttp.Path;
import okhttp3.*;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
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
    String requestBody;
    String relativeUrl;
    String baseUrl;
    Converter<String, RequestBody> requestBodyConverter;
    Converter<ResponseBody, ?> responseBodyConverter;
    Annotation[][] parameterAnnotations;
    Annotation[] declaredAnnotations;
    CallAdapter<R, T> callAdapter;
    OkHttpClient client;

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
        if(method.getDeclaredAnnotations().length > 0){
            if(method.getDeclaredAnnotations()[0] instanceof GET){
                return generateGet(retrofit, method, args);
            }else if(method.getDeclaredAnnotations()[0] instanceof POST){
                return generatePost(retrofit, method, args);
            }
        }
        return null;
    }

    private static OkHttpCall generateGet(Retrofit retrofit, Method method, Object[] args) {
        ServiceMethod serviceMethod = new ServiceMethod();
        serviceMethod.parameterAnnotations = method.getParameterAnnotations();
        serviceMethod.declaredAnnotations = method.getDeclaredAnnotations();
        serviceMethod.baseUrl = retrofit.baseUrl.toString();
        serviceMethod.callAdapter = retrofit.getCallAdapter(method.getReturnType(), method.getDeclaredAnnotations(), retrofit);
        GET get = (GET) method.getDeclaredAnnotations()[0];
        serviceMethod.relativeUrl = get.value();
        serviceMethod.requestBodyConverter = retrofit.searchForRequestConverter(method.getGenericReturnType(),
                null, method.getDeclaredAnnotations());
        serviceMethod.responseBodyConverter = retrofit.searchForResponseConverter(method.getGenericReturnType(),
                method.getDeclaredAnnotations());
        serviceMethod.method ="GET";
        serviceMethod.client = retrofit.client;
        OkHttpCall result = new OkHttpCall(serviceMethod, args);
//        cache.put(method, serviceMethod);
        return result;
    }

    private static OkHttpCall generatePost(Retrofit retrofit, Method method, Object[] args) {
        ServiceMethod serviceMethod = new ServiceMethod();
        serviceMethod.baseUrl = retrofit.baseUrl.toString();
        serviceMethod.parameterAnnotations = method.getParameterAnnotations();
        serviceMethod.declaredAnnotations = method.getDeclaredAnnotations();
        POST post = (POST) method.getDeclaredAnnotations()[0];
        serviceMethod.relativeUrl = post.value();
        serviceMethod.callAdapter = retrofit.getCallAdapter(method.getReturnType(), method.getDeclaredAnnotations(), retrofit);
        serviceMethod.requestBody = "";
        serviceMethod.requestBodyConverter = retrofit.searchForRequestConverter(method.getGenericReturnType(),
                method.getParameterAnnotations()[0],
                method.getDeclaredAnnotations());
        serviceMethod.responseBodyConverter = retrofit.searchForResponseConverter(method.getGenericReturnType(),
                method.getDeclaredAnnotations());
        serviceMethod.method = "POST";
        serviceMethod.client = retrofit.client;
        OkHttpCall result = new OkHttpCall(serviceMethod, args);
//        cache.put(method, serviceMethod);
        return result;
    }

    okhttp3.Call toCall(Object[] args) throws IOException {
        RequestBuilder requestBuilder = new RequestBuilder(method, baseUrl, relativeUrl);
        if(requestBody != null){
            requestBuilder.setBody(requestBodyConverter.convert(requestBody));
        }

        int paramIndex = 0;
        outer:  for(Annotation[] parameterAnnotation : parameterAnnotations){
            if(parameterAnnotation.length > 0){
                for(Annotation annotation : parameterAnnotation){
                    if(annotation instanceof Body){
                        requestBody = (String) args[paramIndex];
                        break outer;
                    }
                    if(annotation instanceof Path){
                        String pattern = "\\{" + ((Path) annotation).value() + "\\}";
                        Pattern r = Pattern.compile(pattern);
                        Matcher m = r.matcher(relativeUrl);
                        relativeUrl = m.replaceAll(args[paramIndex].toString());
                        break outer;
                    }
                }
            }
            paramIndex++;
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

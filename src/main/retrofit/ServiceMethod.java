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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by pc on 2018/6/9.
 */
public class ServiceMethod<R, T> {

    String method;
    String requestBody;
    String relativeUrl;
    String baseUrl;
    Converter<String, RequestBody> requestBodyConverter;
    Converter<ResponseBody, ?> responseBodyConverter;
    OkHttpClient client;
    Annotation[][] parameterAnnotations;
    Annotation[] declaredAnnotations;
    Object[] args;
    CallAdapter<R, T> callAdapter;

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
        serviceMethod.client = retrofit.client;
        serviceMethod.baseUrl = retrofit.baseUrl.toString();
        serviceMethod.args = args;
        serviceMethod.callAdapter = retrofit.callAdapterFactory.get(method.getReturnType(), method.getDeclaredAnnotations(), retrofit);
        GET get = (GET) method.getDeclaredAnnotations()[0];
        serviceMethod.relativeUrl = get.value();
        serviceMethod.requestBodyConverter = (Converter<String, RequestBody>) retrofit.searchForRequestConverter(method.getGenericReturnType(),
                null, method.getDeclaredAnnotations());
        serviceMethod.responseBodyConverter = retrofit.searchForResponseConverter(method.getGenericReturnType(),
                method.getDeclaredAnnotations());
        serviceMethod.method ="GET";
        return new OkHttpCall(serviceMethod);
    }

    private static OkHttpCall generatePost(Retrofit retrofit, Method method, Object[] args) {
        ServiceMethod serviceMethod = new ServiceMethod();
        serviceMethod.client = retrofit.client;
        serviceMethod.baseUrl = retrofit.baseUrl.toString();
        serviceMethod.parameterAnnotations = method.getParameterAnnotations();
        serviceMethod.declaredAnnotations = method.getDeclaredAnnotations();
        POST post = (POST) method.getDeclaredAnnotations()[0];
        serviceMethod.relativeUrl = post.value();
        serviceMethod.args = args;
        serviceMethod.callAdapter = retrofit.callAdapterFactory.get(method.getReturnType(), method.getDeclaredAnnotations(), retrofit);
        serviceMethod.requestBody = "";
        serviceMethod.requestBodyConverter = retrofit.searchForRequestConverter(method.getGenericReturnType(),
                method.getParameterAnnotations()[0],
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

package main.retrofit;

import okhttp3.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pc on 2018/5/29.
 */
public class Retrofit {

    OkHttpClient client = new OkHttpClient();

     HttpUrl baseUrl;
    private List<Converter.Factory> factoryList;

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
                        if (method.getDeclaringClass() == Object.class) {
                            return method.invoke(this, args);
                        }
                        return ServiceMethod.generateOkHttpCall(Retrofit.this, method, args);
                    }
                });
    }

    Converter<ResponseBody, ?> searchForResponseConverter(Type genericReturnType, Annotation[] declaredAnnotations) {
        for(Converter.Factory factory : factoryList){
            if(factory.responseBodyConverter(genericReturnType, declaredAnnotations, this) != null){
                return factory.responseBodyConverter(genericReturnType, declaredAnnotations, this);
            }
        }
        return BuiltInConverter.INSTANCE.responseBodyConverter(genericReturnType, declaredAnnotations, this);
    }

    Converter<?, RequestBody> searchForRequestConverter(Type genericReturnType, Annotation[] parameterAnnotations, Annotation[] declaredAnnotations) {

        for(Converter.Factory factory : factoryList){
            if(factory.requestBodyConverter(genericReturnType, parameterAnnotations, declaredAnnotations, this) != null){
                return factory.requestBodyConverter(genericReturnType, parameterAnnotations, declaredAnnotations, this);
            }
        }
        return BuiltInConverter.INSTANCE.requestBodyConverter(genericReturnType, parameterAnnotations, declaredAnnotations, this);
    }

    public static class Builder {

        private HttpUrl baseUrl;
        private List<Converter.Factory> factoryList = new ArrayList<>();
        private OkHttpClient client = new OkHttpClient();

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

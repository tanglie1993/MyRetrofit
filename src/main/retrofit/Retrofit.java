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

    List<CallAdapter.Factory> callAdapterFactoryList;
    OkHttpClient client = new OkHttpClient();

     HttpUrl baseUrl;
    private List<Converter.Factory> factoryList;
    private boolean validateEagerly;

    public Retrofit(HttpUrl baseUrl, List<Converter.Factory> factoryList, OkHttpClient client, boolean validateEagerly, List<CallAdapter.Factory> callAdapterFactoryList) {
        this.baseUrl = baseUrl;
        this.factoryList = factoryList;
        this.client = client;
        this.validateEagerly = validateEagerly;
        this.callAdapterFactoryList = callAdapterFactoryList;
    }

    public <T> T create(final Class<T> service) {
        if (!service.isInterface()) {
            throw new IllegalArgumentException("API declarations must be interfaces.");
        }
        if (service.getInterfaces().length > 0) {
            throw new IllegalArgumentException("API interfaces must not extend other interfaces.");
        }
        if(validateEagerly){
            for(Method method : service.getDeclaredMethods()){
                if(method.getReturnType().getName().equals("void")){
                    throw new IllegalArgumentException("Service methods cannot return void.\n    for method "
                            + method.getDeclaringClass().getSimpleName() + "."+ method.getName());
                }
            }
        }
        return (T) Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[]{service},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getDeclaringClass() == Object.class) {
                            return method.invoke(this, args);
                        }
                        OkHttpCall okHttpCall = ServiceMethod.generateOkHttpCall(Retrofit.this, method, args);
                        return okHttpCall.adapt();
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

    public Builder newBuilder() {
        return new Builder(this);
    }

    public CallAdapter getCallAdapter(Class<?> returnType, Annotation[] declaredAnnotations, Retrofit retrofit) {
        for(CallAdapter.Factory factory : callAdapterFactoryList){
            CallAdapter adapter = factory.get(returnType, declaredAnnotations, retrofit);
            if(adapter != null){
                return adapter;
            }
        }
        return null;
    }

    public HttpUrl baseUrl() {
        return baseUrl;
    }

    public static class Builder {

        private HttpUrl baseUrl;
        private List<Converter.Factory> factoryList = new ArrayList<>();
        private OkHttpClient client = new OkHttpClient();
        private boolean validateEagerly;
        private List<CallAdapter.Factory> callAdapterFactoryList;

        public Builder() {
            this.callAdapterFactoryList = new ArrayList<>();
            callAdapterFactoryList.add(CallAdapter.FACTORY_INSTANCE);
        }

        public Builder(Retrofit retrofit) {
            this.client = retrofit.client;
            this.baseUrl = retrofit.baseUrl;
            this.factoryList = retrofit.factoryList;
            this.callAdapterFactoryList = retrofit.callAdapterFactoryList;
            callAdapterFactoryList.add(CallAdapter.FACTORY_INSTANCE);
        }

        public Builder baseUrl(HttpUrl url) {
            if(url == null){
                throw new NullPointerException("baseUrl == null");
            }
            this.baseUrl = url;
            List<String> pathSegments = baseUrl.pathSegments();
            if (!"".equals(pathSegments.get(pathSegments.size() - 1))) {
                throw new IllegalArgumentException("baseUrl must end in /: " + baseUrl);
            }
            return this;
        }

        public Builder baseUrl(String url) {
            if(url == null){
                throw new NullPointerException("baseUrl == null");
            }
            HttpUrl parsed = HttpUrl.parse(url);
            if (parsed == null) {
                throw new IllegalArgumentException("Illegal URL: " + url);
            }
            return baseUrl(HttpUrl.parse(url));
        }

        public Builder addConverterFactory(Converter.Factory factory){
            this.factoryList.add(factory);
            return this;
        }

        public Retrofit build() {
            if(baseUrl == null){
                throw new IllegalStateException("Base URL required.");
            }
            return new Retrofit(baseUrl, factoryList, client, validateEagerly, callAdapterFactoryList);
        }

        public Builder client(OkHttpClient client) {
            this.client = client;
            return this;
        }

        public List<Converter.Factory> converterFactories() {
            return factoryList;
        }

        public Builder validateEagerly(boolean validateEagerly) {
            this.validateEagerly = validateEagerly;
            return this;
        }

        public Builder addCallAdapterFactory(CallAdapter.Factory callAdapterFactory) {
            this.callAdapterFactoryList.add(callAdapterFactory);
            return this;
        }


    }
}

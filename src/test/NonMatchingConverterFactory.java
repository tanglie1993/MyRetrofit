package test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import main.retrofit.Converter;
import main.retrofit.Retrofit;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public final class NonMatchingConverterFactory extends Converter.Factory {
    public boolean called;

    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations,
                                                            Retrofit retrofit) {
        called = true;
        return null;
    }

    @Override public Converter<?, RequestBody> requestBodyConverter(Type type,
                                                                    Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Retrofit retrofit) {
        called = true;
        return null;
    }

    @Override public Converter<?, String> stringConverter(Type type, Annotation[] annotations,
                                                          Retrofit retrofit) {
        called = true;
        return null;
    }
}
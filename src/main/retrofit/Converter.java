package main.retrofit;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Created by pc on 2018/5/29.
 */

public interface Converter<F, T> {
    T convert(F value) throws IOException;

    abstract class Factory {

        public abstract Converter<ResponseBody, ?> responseBodyConverter(Type type,
                                                                         Annotation[] annotations, Retrofit retrofit);

        public abstract Converter<?, RequestBody> requestBodyConverter(Type type,
                                                                       Annotation[] parameterAnnotations, Annotation[] methodAnnotations,
                                                                       Retrofit retrofit);

    }
}


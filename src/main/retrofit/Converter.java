package main.retrofit;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import java.io.IOException;

/**
 * Created by pc on 2018/5/29.
 */

public interface Converter<F, T> {
    T convert(F value) throws IOException;

    abstract class Factory {

        public abstract Converter<ResponseBody, ?> responseBodyConverter();

        public abstract Converter<?, RequestBody> requestBodyConverter();

    }
}


package main.retrofit;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.BufferedSink;

import java.io.IOException;

/**
 * Created by pc on 2018/5/29.
 */
public class ToStringConverterFactory extends Converter.Factory {
    @Override
    public Converter<ResponseBody, ?> responseBodyConverter() {
        return (Converter<ResponseBody, String>) value -> {
            if(value == null){
                return "";
            }
            return value.string();
        };
    }

    @Override
    public Converter<?, RequestBody> requestBodyConverter() {
        return (Converter<String, RequestBody>) value -> new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("text/plain");
            }

            @Override
            public void writeTo(BufferedSink bufferedSink) throws IOException {
                if(value == null){
                    return;
                }
                bufferedSink.writeUtf8(value);
            }
        };
    }
}

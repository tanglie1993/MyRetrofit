package main.retrofit;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;

import java.io.IOException;

/**
 * Created by pc on 2018/6/10.
 */
public class OkHttpCall implements Call {

    private volatile boolean isExecuted = false;
    private ServiceMethod serviceMethod;

    public OkHttpCall(ServiceMethod serviceMethod) {
        this.serviceMethod = serviceMethod;
    }

    @Override
    public Response<Object> execute() throws IOException {
        if(isExecuted){
            throw new IllegalStateException("Already executed.");
        }
        isExecuted = true;
        if(serviceMethod.method!= null && serviceMethod.method.equals("POST")){
            serviceMethod.request.newBuilder().method("POST", ((Converter<String, RequestBody>) serviceMethod.requestBodyConverter)
                    .convert(serviceMethod.requestBody));
        }
        okhttp3.Call rawCall = serviceMethod.client.newCall(serviceMethod.request);
        okhttp3.Response rawResponse = rawCall.execute();
        return parseResponse(rawResponse);
    }

    @Override
    public void enqueue(Callback callback) {
        if(isExecuted){
            throw new IllegalStateException("Already executed.");
        }
        isExecuted = true;
        if(serviceMethod.method!= null && serviceMethod.method.equals("POST")){
            try {
                serviceMethod.request.newBuilder().method("POST",
                        ((Converter<String, RequestBody>) serviceMethod.requestBodyConverter).convert(serviceMethod.requestBody));
            } catch (Exception e) {
                callback.onFailure(OkHttpCall.this, e);
            }
        }
        okhttp3.Call okhttpCall = serviceMethod.client.newCall(serviceMethod.request);
        okhttpCall.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                callback.onFailure(OkHttpCall.this, e);
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                try{
                    callback.onResponse(OkHttpCall.this, parseResponse(response));
                } catch (Exception e){
                    callback.onFailure(OkHttpCall.this, e);
                }
            }
        });
    }

    @Override
    public boolean isExecuted() {
        return isExecuted;
    }

    private Response parseResponse(okhttp3.Response response) throws IOException {
        int code = response.code();

        if (code == 204 || code == 205) {
            response.close();
            return new Response<>(response, null, null);
        }
        ExceptionCatchingResponseBody exceptionCatchingResponseBody = new ExceptionCatchingResponseBody(response.body());
        response = response.newBuilder()
                .body(new NoContentResponseBody(response.body().contentType(), response.body().contentLength()))
                .build();
        if(response.isSuccessful()){
            try {
                return new Response<>(response, serviceMethod.responseBodyConverter.convert(exceptionCatchingResponseBody),null);
            } catch (Exception e) {
                e.printStackTrace();
                if(exceptionCatchingResponseBody.thrownException !=  null){
                    throw exceptionCatchingResponseBody.thrownException;
                }else{
                    throw e;
                }
            }
        }else{
            Buffer buffer = new Buffer();
            exceptionCatchingResponseBody.source().readAll(buffer);
            return new Response<>(response, null,  ResponseBody.create(response.body().contentType(), response.body().contentLength(), buffer));
        }
    }

    static final class ExceptionCatchingResponseBody extends ResponseBody {
        private final ResponseBody delegate;
        IOException thrownException;

        ExceptionCatchingResponseBody(ResponseBody delegate) {
            this.delegate = delegate;
        }

        @Override public MediaType contentType() {
            return delegate.contentType();
        }

        @Override public long contentLength() {
            return delegate.contentLength();
        }

        @Override public BufferedSource source() {
            return Okio.buffer(new ForwardingSource(delegate.source()) {
                @Override public long read(Buffer sink, long byteCount) throws IOException {
                    try {
                        return super.read(sink, byteCount);
                    } catch (IOException e) {
                        thrownException = e;
                        throw e;
                    }
                }
            });
        }

        @Override public void close() {
            delegate.close();
        }

        void throwIfCaught() throws IOException {
            if (thrownException != null) {
                throw thrownException;
            }
        }
    }

    static final class NoContentResponseBody extends ResponseBody {
        private final MediaType contentType;
        private final long contentLength;

        NoContentResponseBody(MediaType contentType, long contentLength) {
            this.contentType = contentType;
            this.contentLength = contentLength;
        }

        @Override public MediaType contentType() {
            return contentType;
        }

        @Override public long contentLength() {
            return contentLength;
        }

        @Override public BufferedSource source() {
            throw new IllegalStateException("Cannot read raw response body of a converted body.");
        }
    }
}

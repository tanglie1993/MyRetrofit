package main.retrofit;

import com.google.caliper.model.Run;
import okhttp3.*;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;

import java.io.IOException;

/**
 * Created by pc on 2018/6/10.
 */
public class OkHttpCall implements Call<Object> {

    private Object[] args;
    private volatile boolean isExecuted = false;
    private volatile boolean isCancelled = false;
    private ServiceMethod<Object, Call> serviceMethod;
    private volatile okhttp3.Call rawCall;
    private Throwable creationFailure;

    public OkHttpCall(OkHttpCall okHttpCall) {
        this.serviceMethod = okHttpCall.serviceMethod;
        this.args = okHttpCall.args;
    }

    public OkHttpCall(ServiceMethod serviceMethod, Object[] args) {
        this.serviceMethod = serviceMethod;
        this.args = args;
    }

    @Override
    public Response<Object> execute() throws IOException {
        if(isExecuted){
            throw new IllegalStateException("Already executed.");
        }
        if(isCancelled){
            throw new IOException("Canceled");
        }
        if (creationFailure != null) {
            if(creationFailure instanceof IOException){
                throw (IOException) creationFailure;
            }
            if(creationFailure instanceof RuntimeException){
                throw (RuntimeException) creationFailure;
            }
            if(creationFailure instanceof Error){
                throw (Error) creationFailure;
            }
        }
        isExecuted = true;
        try{
            if(rawCall == null){
                rawCall = serviceMethod.toCall(args);
            }
        }catch (Throwable e){
            throwIfFatal(e);
            creationFailure = e;
            if(e instanceof RuntimeException || e instanceof Error){
                throw e;
            }
        }
        if(isCancelled){
            rawCall.cancel();
        }
        okhttp3.Response rawResponse = rawCall.execute();
        return parseResponse(rawResponse);
    }

    private void throwIfFatal(Throwable e) {
        if (e instanceof VirtualMachineError) {
            throw (VirtualMachineError) e;
        } else if (e instanceof ThreadDeath) {
            throw (ThreadDeath) e;
        } else if (e instanceof LinkageError) {
            throw (LinkageError) e;
        }
    }

    @Override
    public void enqueue(Callback callback) {
        if(isExecuted){
            throw new IllegalStateException("Already executed.");
        }
        isExecuted = true;
        if (creationFailure != null) {
            callback.onFailure(OkHttpCall.this, creationFailure);
            return;
        }
        try {
            if(rawCall == null && creationFailure == null){
                rawCall = serviceMethod.toCall(args);
            }
        } catch (Throwable e) {
            throwIfFatal(e);
            creationFailure = e;
            callback.onFailure(OkHttpCall.this, e);
            return;
        }
        if(isCancelled){
            rawCall.cancel();
        }
        rawCall.enqueue(new okhttp3.Callback() {
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

    @Override
    public void cancel() {
        if(isCancelled){
            return;
        }
        if(rawCall != null){
            rawCall.cancel();
        }
        isCancelled = true;
    }

    @Override
    public boolean isCanceled() {
        if (isCancelled) {
            return true;
        }
        synchronized (this) {
            return rawCall != null && rawCall.isCanceled();
        }
    }

    @Override
    public Call clone() {
        return new OkHttpCall(this);
    }

    @Override
    public Request request() {
        if (creationFailure != null) {
            if(creationFailure instanceof RuntimeException){
                throw (RuntimeException) creationFailure;
            }
            if(creationFailure instanceof Error){
                throw (Error) creationFailure;
            }
        }
        if(rawCall == null){
            try {
                if(rawCall == null && creationFailure == null){
                    rawCall = serviceMethod.toCall(args);
                }
            } catch (Throwable e) {
                throwIfFatal(e);
                creationFailure = e;
                if(e instanceof RuntimeException){
                    throw (RuntimeException) e;
                } else if(e instanceof Exception){
                    throw new RuntimeException(e);
                } else if(e instanceof Error){
                    throw (Error) e;
                }
            }
        }
        okhttp3.Call call = rawCall;
        return call.request();
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

    public Object adapt() {
        return serviceMethod.adapt(OkHttpCall.this);
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

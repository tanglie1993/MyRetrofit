/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package main.retrofit;

import main.retrofit.okhttp.Streaming;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.Buffer;


import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public final class BuiltInConverters extends Converter.Factory {

  static final Converter.Factory INSTANCE = new BuiltInConverters();

  @Override
  public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations,
                                                          Retrofit retrofit) {
    for(Annotation annotation : annotations){
      if(annotation instanceof Streaming){
        return StreamingResponseBodyConverter.INSTANCE;
      }
    }
    if (type instanceof ParameterizedType && Utils.getParameterUpperBound(0, (ParameterizedType) type) == Void.class) {
      return VoidResponseBodyConverter.INSTANCE;
    }
    return ResponseBodyConverter.INSTANCE;
  }

  @Override
  public Converter<?, RequestBody> requestBodyConverter(Type type,
                                                        Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Retrofit retrofit) {
    return RequestBodyConverter.INSTANCE;
  }

  static final class RequestBodyConverter implements Converter<RequestBody, RequestBody> {
    static final RequestBodyConverter INSTANCE = new RequestBodyConverter();

    @Override public RequestBody convert(RequestBody value) {
      return value;
    }
  }

  static final class ResponseBodyConverter implements Converter<ResponseBody, ResponseBody> {
    static final ResponseBodyConverter INSTANCE = new ResponseBodyConverter();

    @Override
    public ResponseBody convert(ResponseBody value) throws IOException {
      Buffer buffer = new Buffer();
      value.source().readAll(buffer);
      return ResponseBody.create(value.contentType(), value.contentLength(), buffer);
    }
  }

  static final class StreamingResponseBodyConverter implements Converter<ResponseBody, ResponseBody> {
    static final StreamingResponseBodyConverter INSTANCE = new StreamingResponseBodyConverter();

    @Override
    public ResponseBody convert(ResponseBody value) throws IOException {
      return value;
    }
  }

  static final class VoidResponseBodyConverter implements Converter<ResponseBody, Void> {
    static final VoidResponseBodyConverter INSTANCE = new VoidResponseBodyConverter();

    @Override public Void convert(ResponseBody value) {
      value.close();
      return null;
    }
  }
}

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

import okhttp3.RequestBody;
import okhttp3.ResponseBody;


import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

final class BuiltInConverter extends Converter.Factory {

  static final Converter.Factory INSTANCE = new BuiltInConverter();

  @Override
  public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations,
                                                          Retrofit retrofit) {
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
      return value;
    }
  }
}

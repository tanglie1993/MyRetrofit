/*
 * Copyright (C) 2012 Square, Inc.
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

import okhttp3.*;

final class RequestBuilder {

  private String method;

  private String baseUrl;
  private String relativeUrl;

  private final Request.Builder requestBuilder;
  private MediaType contentType;

  private RequestBody body;

  RequestBuilder(String method, String baseUrl,  String relativeUrl) {
    this.method = method;
    this.baseUrl = baseUrl;
    this.relativeUrl = relativeUrl;
    this.requestBuilder = new Request.Builder();
  }

  void setRelativeUrl(Object relativeUrl) {
    this.relativeUrl = relativeUrl.toString();
  }

  void addHeader(String name, String value) {
    if ("Content-Type".equalsIgnoreCase(name)) {
      MediaType type = MediaType.parse(value);
      if (type == null) {
        throw new IllegalArgumentException("Malformed content type: " + value);
      }
      contentType = type;
    } else {
      requestBuilder.addHeader(name, value);
    }
  }

  void setBody(RequestBody body) {
    this.body = body;
  }

  Request build() {
    MediaType contentType = this.contentType;
    if (contentType != null) {
      requestBuilder.addHeader("Content-Type", contentType.toString());
    }

    return requestBuilder
            .url(baseUrl + relativeUrl)
            .method(method, body)
            .build();
  }

}

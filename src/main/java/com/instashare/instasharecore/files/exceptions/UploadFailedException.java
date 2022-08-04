package com.instashare.instasharecore.files.exceptions;

import lombok.AllArgsConstructor;
import lombok.val;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.SdkResponse;

import java.util.Optional;

@AllArgsConstructor
public class UploadFailedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int statusCode;
  private final Optional<String> statusText;

  public UploadFailedException(SdkResponse response) {
    val httpResponse = response.sdkHttpResponse();
    if (httpResponse != null) {
      this.statusCode = httpResponse.statusCode();
      this.statusText = httpResponse.statusText();
    } else {
      this.statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
      this.statusText = Optional.of("Unknown");
    }
  }
}

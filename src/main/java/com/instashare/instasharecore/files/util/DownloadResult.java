package com.instashare.instasharecore.files.util;

import lombok.Builder;
import lombok.Getter;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;

@Getter
@Builder
public class DownloadResult {
  private final String contentType;
  private final Long contentLength;
  private final String contentDisposition;
  private final Flux<ByteBuffer> response;
}

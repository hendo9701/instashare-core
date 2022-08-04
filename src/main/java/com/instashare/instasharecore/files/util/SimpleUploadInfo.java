package com.instashare.instasharecore.files.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;
import java.util.UUID;

@Builder
@Data
@AllArgsConstructor
public class SimpleUploadInfo {
  String fileKey;
  String fileName;
  String owner;
  String mediaType;
  long contentLength;
  Flux<ByteBuffer> content;

  public SimpleUploadInfo(
      MediaType mediaType,
      String fileName,
      String owner,
      long contentLength,
      Flux<ByteBuffer> content) {
    this(
        UUID.randomUUID().toString(),
        fileName,
        owner,
        mediaType.toString(),
        contentLength,
        content);
  }
}

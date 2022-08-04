package com.instashare.instasharecore.files;

import reactor.core.publisher.Flux;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class FluxResponseProvider
    implements AsyncResponseTransformer<GetObjectResponse, FluxResponse> {

  private FluxResponse response;

  @Override
  public CompletableFuture<FluxResponse> prepare() {
    response = new FluxResponse();
    return response.completableFuture;
  }

  @Override
  public void onResponse(GetObjectResponse getObjectResponse) {
    this.response.sdkResponse = getObjectResponse;
  }

  @Override
  public void onStream(SdkPublisher<ByteBuffer> sdkPublisher) {
    response.flux = Flux.from(sdkPublisher);
    response.completableFuture.complete(response);
  }

  @Override
  public void exceptionOccurred(Throwable throwable) {
    response.completableFuture.completeExceptionally(throwable);
  }
}

class FluxResponse {
  final CompletableFuture<FluxResponse> completableFuture = new CompletableFuture<>();
  GetObjectResponse sdkResponse;
  Flux<ByteBuffer> flux;
}

package com.instashare.instasharecore.files;

import com.instashare.instasharecore.config.S3ClientConfigurationProperties;
import com.instashare.instasharecore.events.DestinationInfo;
import com.instashare.instasharecore.files.exceptions.DownloadFailedException;
import com.instashare.instasharecore.files.exceptions.UploadFailedException;
import com.instashare.instasharecore.files.util.DownloadResult;
import com.instashare.instasharecore.files.util.SimpleUploadInfo;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3BackedFileService implements FileService {

  private final S3AsyncClient s3Client;

  private final S3ClientConfigurationProperties s3Config;

  private final FileRepository fileRepository;

  private final AmqpTemplate amqpTemplate;

  private final DestinationInfo destinationInfo;

  private static ByteBuffer concatBuffers(List<DataBuffer> buffers) {
    log.debug("Creating ByteBuffer from {} chunks", buffers.size());

    val partSize = buffers.stream().map(DataBuffer::readableByteCount).reduce(0, Integer::sum);
    val partData = ByteBuffer.allocate(partSize);
    buffers.forEach((buffer) -> partData.put(buffer.asByteBuffer()));
    // Reset read pointer to first byte
    partData.rewind();

    log.debug("PartData: size={}", partData.capacity());
    return partData;
  }

  @Override
  public Mono<File> uploadSimple(@NonNull SimpleUploadInfo simpleUploadInfo) {
    val uploadFuture =
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(s3Config.getBucket())
                .contentLength(simpleUploadInfo.getContentLength())
                .contentType(simpleUploadInfo.getMediaType())
                .key(simpleUploadInfo.getFileKey())
                .build(),
            AsyncRequestBody.fromPublisher(simpleUploadInfo.getContent()));

    return Mono.fromFuture(uploadFuture)
        .flatMap(
            response -> {
              if (transmissionFailed(response.sdkHttpResponse())) {
                log.error("Unable to upload file with key: {}.", simpleUploadInfo.getFileKey());
                return Mono.error(new UploadFailedException(response));
              }
              val file =
                  new File(
                      simpleUploadInfo.getFileKey(),
                      simpleUploadInfo.getFileName(),
                      simpleUploadInfo.getOwner(),
                      FileStatus.RAW,
                      simpleUploadInfo.getContentLength(),
                      simpleUploadInfo.getMediaType());
              log.info(
                  "File with key: {} was successfully uploaded.", simpleUploadInfo.getFileKey());
              return fileRepository.save(file);
            })
        .doOnSuccess(
            file -> {
              // Publish file uploaded event
              amqpTemplate.convertAndSend(
                  destinationInfo.exchange(), destinationInfo.routingKey(), file.getId());
            });
  }

  // This method does not require any temporary storage
  public Mono<File> uploadPart(FilePart filePart, String owner) {

    val fileKey = UUID.randomUUID().toString();
    val filename = of(filePart.filename()).orElse(fileKey);
    val metadata = Map.of("filename", filename);
    val mediaType =
        ofNullable(filePart.headers().getContentType()).orElse(MediaType.APPLICATION_OCTET_STREAM);

    // Create multipart upload request
    val uploadRequest =
        s3Client.createMultipartUpload(
            CreateMultipartUploadRequest.builder()
                .contentType(mediaType.toString())
                .key(fileKey)
                .metadata(metadata)
                .bucket(s3Config.getBucket())
                .build());

    // Will hold the upload state
    val uploadState = new UploadState(s3Config.getBucket(), fileKey);

    return Mono.fromFuture(uploadRequest)
        .flatMapMany(
            response -> {
              if (transmissionFailed(response.sdkHttpResponse())) {
                return Mono.error(new UploadFailedException(response));
              }
              uploadState.uploadId = response.uploadId();
              log.debug("UploadId: {}", response.uploadId());
              return filePart.content();
            })
        .bufferUntil(
            buffer -> {
              uploadState.buffered += buffer.readableByteCount();
              if (uploadState.buffered >= s3Config.getMultipartMinPartSize()) {
                uploadState.buffered = 0;
                return true;
              }
              return false;
            })
        .map(S3BackedFileService::concatBuffers)
        .flatMap(buffer -> uploadPart(uploadState, buffer))
        .onBackpressureBuffer()
        .reduce(
            uploadState,
            (state, completedPart) -> {
              state.completedParts.put(completedPart.partNumber(), completedPart);
              return state;
            })
        .flatMap(this::completeUpload)
        .flatMap(
            response -> {
              if (transmissionFailed(response.sdkHttpResponse())) {
                return Mono.error(new UploadFailedException(response));
              }
              val sizeRequest =
                  s3Client.headObject(
                      HeadObjectRequest.builder()
                          .bucket(s3Config.getBucket())
                          .key(fileKey)
                          .build());
              return Mono.fromFuture(sizeRequest);
            })
        .flatMap(
            response -> {
              if (transmissionFailed(response.sdkHttpResponse())) {
                return Mono.error(new UploadFailedException(response));
              }
              val file =
                  new File(
                      fileKey,
                      filename,
                      owner,
                      FileStatus.RAW,
                      response.contentLength(),
                      mediaType.toString());
              log.info("File with key: {} was successfully uploaded.", fileKey);
              return fileRepository.save(file);
            })
        .doOnSuccess(
            file -> {
              // Publish file uploaded event
              amqpTemplate.convertAndSend(
                  destinationInfo.exchange(), destinationInfo.routingKey(), file.getId());
            });
  }

  @Override
  public Mono<Long> countByOwner(String name) {
    return fileRepository.countByOwner(name);
  }

  private boolean transmissionFailed(SdkHttpResponse httpResponse) {
    return httpResponse == null || !httpResponse.isSuccessful();
  }

  private Mono<CompletedPart> uploadPart(UploadState uploadState, ByteBuffer buffer) {
    val partNumber = ++uploadState.partCounter;
    val request =
        s3Client.uploadPart(
            UploadPartRequest.builder()
                .bucket(uploadState.bucket)
                .key(uploadState.getFileKey())
                .partNumber(partNumber)
                .uploadId(uploadState.uploadId)
                .contentLength((long) buffer.capacity())
                .build(),
            AsyncRequestBody.fromPublisher(Mono.just(buffer)));

    return Mono.fromFuture(request)
        .map(
            (response) -> {
              val sdkHttpResponse = response.sdkHttpResponse();
              if (sdkHttpResponse == null || !sdkHttpResponse.isSuccessful()) {
                log.error("Unable to upload file with key: {}.", uploadState.fileKey);
                throw new UploadFailedException(response);
              }
              log.info("[I230] uploadPart complete: part={}, etag={}", partNumber, response.eTag());
              return CompletedPart.builder().eTag(response.eTag()).partNumber(partNumber).build();
            });
  }

  private Mono<CompleteMultipartUploadResponse> completeUpload(UploadState state) {
    log.debug(
        "Upload completed: bucket={}, fileKey={}, completedParts.size={}",
        state.bucket,
        state.getFileKey(),
        state.completedParts.size());

    val multipartUpload =
        CompletedMultipartUpload.builder().parts(state.completedParts.values()).build();

    return Mono.fromFuture(
        s3Client.completeMultipartUpload(
            CompleteMultipartUploadRequest.builder()
                .bucket(state.bucket)
                .uploadId(state.uploadId)
                .multipartUpload(multipartUpload)
                .key(state.getFileKey())
                .build()));
  }

  @Override
  public Mono<Boolean> existsByFileName(@NonNull String fileName) {
    return fileRepository.existsByFileName(fileName);
  }

  @Override
  public Flux<File> getAll(@NonNull String owner) {
    return fileRepository.findAllByOwner(owner);
  }

  @Override
  public Mono<DownloadResult> download(@NonNull File file) {
    val request = GetObjectRequest.builder().bucket(s3Config.getBucket()).key(file.getId()).build();
    return Mono.fromFuture(s3Client.getObject(request, new FluxResponseProvider()))
        .map(
            response -> {
              val httpResponse = response.sdkResponse.sdkHttpResponse();
              if (httpResponse == null || !httpResponse.isSuccessful()) {
                throw new DownloadFailedException(response.sdkResponse);
              }
              return DownloadResult.builder()
                  .contentType(response.sdkResponse.contentType())
                  .contentLength(response.sdkResponse.contentLength())
                  .contentDisposition("attachment; filename=\"" + file.getFileName() + "\"")
                  .response(response.flux)
                  .build();
            });
  }

  @Override
  public Mono<File> rename(File file, String newName) {
    file.setFileName(newName);
    return fileRepository.save(file);
  }

  @Override
  public Mono<File> getByIdAndOwner(String fileKey, String owner) {
    return fileRepository.findByIdAndOwner(fileKey, owner);
  }

  @Data
  @RequiredArgsConstructor
  private static class UploadState {
    final String bucket;
    final String fileKey;
    String uploadId;
    int partCounter;
    Map<Integer, CompletedPart> completedParts = new HashMap<>();
    int buffered = 0;
  }
}

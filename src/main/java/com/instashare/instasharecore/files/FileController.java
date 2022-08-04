package com.instashare.instasharecore.files;

import com.instashare.instasharecore.files.dtos.*;
import com.instashare.instasharecore.files.util.SimpleUploadInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@RestController
@RequestMapping("/v1/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

  private final FileService fileService;

  @GetMapping
  public Mono<List<File>> list(
      Principal principal,
      @RequestParam(defaultValue = "10", required = false) int size,
      @RequestParam(defaultValue = "0", required = false) long skip) {
    val userEmail = principal.getName();
    return Mono.from(fileService.getAll(userEmail).buffer(size).skip(skip));
  }

  @GetMapping("/{fileKey}")
  public Mono<ResponseEntity<Flux<ByteBuffer>>> download(
      Principal principal, @PathVariable String fileKey) {
    val userEmail = principal.getName();
    return fileService
        .getByIdAndOwner(fileKey, userEmail)
        .flatMap(
            file -> {
              if (file.getFileStatus().equals(FileStatus.RAW))
                return Mono.just(ResponseEntity.status(FORBIDDEN).body(Flux.<ByteBuffer>empty()));
              return fileService
                  .download(file)
                  .map(
                      downloadResult ->
                          ResponseEntity.ok()
                              .header(HttpHeaders.CONTENT_TYPE, downloadResult.getContentType())
                              .header(
                                  HttpHeaders.CONTENT_LENGTH,
                                  Long.toString(downloadResult.getContentLength()))
                              .header(
                                  HttpHeaders.CONTENT_DISPOSITION,
                                  downloadResult.getContentDisposition())
                              .body(downloadResult.getResponse()));
            })
        .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
  }

  @PatchMapping("/{fileKey}")
  public Mono<ResponseEntity<File>> rename(
      Principal principal,
      @PathVariable String fileKey,
      @Valid @RequestBody RenameFileRequest renameFileRequest) {
    val userEmail = principal.getName();
    return fileService
        .getByIdAndOwner(fileKey, userEmail)
        .flatMap(file -> fileService.rename(file, renameFileRequest.newName()))
        .map(renamedFile -> ResponseEntity.ok().body(renamedFile))
        .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
  }

  @PostMapping
  public Mono<ResponseEntity<UploadResult>> upload(
      Principal principal,
      @RequestHeader HttpHeaders headers,
      @RequestBody Flux<ByteBuffer> body,
      @RequestParam String filename) {

    val userEmail = principal.getName();
    val length = headers.getContentLength();
    if (length <= 0) {
      return Mono.just(
          ResponseEntity.badRequest()
              .body(
                  new UploadResult(
                      UploadStatus.FAILED,
                      new UploadResultError("Required header: [Content-Length] is missing."))));
    }

    return fileService
        .existsByFileName(filename)
        .flatMap(
            exists -> {
              if (exists) {
                return Mono.just(
                    ResponseEntity.badRequest()
                        .body(
                            new UploadResult(
                                UploadStatus.FAILED, new UploadResultError("File name in use."))));
              }
              val mediaType =
                  ofNullable(headers.getContentType()).orElse(MediaType.APPLICATION_OCTET_STREAM);
              val uploadInfo = new SimpleUploadInfo(mediaType, filename, userEmail, length, body);
              log.info(
                  "Upload attempt. Info: MediaType {}, Length: {}",
                  uploadInfo.getMediaType(),
                  length);
              val uploadFuture = fileService.uploadSimple(uploadInfo);
              return uploadFuture.map(
                  file ->
                      ResponseEntity.ok()
                          .body(
                              new UploadResult(
                                  UploadStatus.COMPLETED,
                                  new UploadResultCompleted(List.of(file.getId())))));
            });
  }

  @RequestMapping(
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      method = {RequestMethod.POST, RequestMethod.PUT})
  public Mono<ResponseEntity<UploadResult>> uploadMultipart(
      Principal principal, @RequestBody Flux<Part> parts) {
    return parts
        .ofType(FilePart.class)
        .flatMap(part -> fileService.uploadPart(part, principal.getName()))
        .collect(Collectors.toList())
        .map(
            files -> {
              val fileKeys = files.stream().map(File::getId).collect(Collectors.toList());
              return ResponseEntity.status(HttpStatus.CREATED)
                  .body(
                      new UploadResult(
                          UploadStatus.COMPLETED, new UploadResultCompleted(fileKeys)));
            });
  }
}

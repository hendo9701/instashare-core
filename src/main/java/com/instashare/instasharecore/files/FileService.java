package com.instashare.instasharecore.files;

import com.instashare.instasharecore.files.util.DownloadResult;
import com.instashare.instasharecore.files.util.SimpleUploadInfo;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FileService {
  Mono<File> uploadSimple(SimpleUploadInfo simpleUploadInfo);

  Mono<Boolean> existsByFileName(String fileName);

  Flux<File> getAll(String owner);

  Mono<DownloadResult> download(File file);

  Mono<File> getByIdAndOwner(String fileKey, String owner);

  Mono<File> rename(File file, String newName);

  Mono<File> uploadPart(FilePart filePart, String owner);

  Mono<Long> countByOwner(String name);
}
